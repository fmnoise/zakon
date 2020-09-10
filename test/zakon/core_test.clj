(ns zakon.core-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [zakon.core :as z :refer [can! cant! can? cant? defrule find-rule any]]))

(use-fixtures :each (fn [f] (f) (z/cleanup!)))

(deftest default-rule-test
  (is (cant? :user :create :content))
  (can! :user :create :content)
  (is (can? :user :create :content)))

(deftest restriction-last-test
  (can! :user :create any)
  (is (can? :user :create :profile))
  (cant! :user :create :profile )
  (is (cant? :user :create :profile)))

(deftest allowance-last-test
  (cant! :user :create any)
  (is (cant? :user :create :profile))
  (can! :user :create :profile )
  (is (can? :user :create :profile)))

(deftest domain-wildcards-test
  (is (not (z/inherited? :role/user :role/any))
      "system doesn't know anything about entities relations until rules are defined or checked")
  (can! :role/any :create :content/any)
  (is (not (z/inherited? :role/user :role/any)))
  (is (can? :role/user :create :content/profile))
  (is (z/inherited? :role/user :role/any)))

(deftest overriding-rules-test
  (can! :user :create :content)
  (is (can? :user :create :content))
  (cant! :user :create :content)
  (is (cant? :user :create :content))
  (can! :user :create :content)
  (is (can? :user :create :content)))

(deftest finding-rules-test
  (can! :user :create :content)
  (is (= {:line 41, :column 3, :ns "zakon.core-test"}
         (find-rule :user :create :content)))
  (is (= ::z/default-rule
         (find-rule :user :create any))))

(deftest cleanup-test
  (can! :user :create :content)
  (is (z/inherited? :user any))
  (is (can? :user :create :content))
  (z/cleanup!)
  (is (not (z/inherited? :user any)))
  (is (cant? :user :create :content)))

(deftest defrule-test
  (defrule [:user :create :content] true)
  (is (can? :user :create :content)))

(deftest defrule--with-var-test
  (def result true)
  (defrule [:user :create :content] result)
  (is (can? :user :create :content))
  (alter-var-root #'result not)
  (is (cant? :user :create :content)))

(deftest defrule--with-fn-test
  (defn result-fn
    [{:keys [actor action subject]}]
    (and (= actor :user) (= action :create) (= subject :content)))
  (defrule [:user any :content] result-fn)
  (is (can? :user :create :content))
  (is (cant? :user :delete :content)))

(deftest defrule--with-multimethod-test
  (defmulti mresult (fn [{:keys [actor]}] (:role actor)))
  (defmethod mresult :default [_] false)
  (defmethod mresult :user [{:keys [action]}] (= action :view))
  (defmethod mresult :admin [_] true)
  (defrule [any any :content] mresult)
  (is (can? {:role :user} :view :content))
  (is (cant? {:role :guest} :view :content))
  (is (cant? {:role :user} :create :content))
  (is (can? {:role :admin} :delete :content)))

(deftest defrule--with-atom-test
  (def result-atom (atom true))
  (defrule [:user :create :content] result-atom)
  (is (can? :user :create :content))
  (swap! result-atom not)
  (is (cant? :user :create :content)))

(deftest defrule--with-atom-inside-function-test
  (def result-atom (atom true))
  (defrule [:user :create :content] result-atom)
  (is (can? :user :create :content))
  (swap! result-atom not)
  (is (cant? :user :create :content)))

(deftest defrule--with-atom-inside-function-test
  (defn fn-with-atom
    [{:keys [actor action subject]}]
    (when (and (= actor :user) (= action :create) (= subject :content))
      (atom true)))
  (defrule [:user any :content] fn-with-atom)
  (is (can? :user :create :content))
  (is (cant? :user :delete :content)))

(deftest defrule--with-function-inside-atom-test
  (def atom-with-fn (atom (fn [{:keys [actor action subject]}]
                            (and (= actor :user)
                                 (= action :create)
                                 (= subject :content)))))
  (defrule [:user any :content] atom-with-fn)
  (is (can? :user :create :content))
  (is (cant? :user :delete :content))
  (swap! atom-with-fn (constantly (fn [_] true)))
  (is (can? :user :create :content))
  (is (can? :user :delete :content)))

(deftest inheritance-test
  (can! :user :create any)
  (is (can? :user :create :content))
  (is (cant? :admin :create :content))
  (z/inherit! :admin :user)
  (is (z/inherited? :admin :user))
  (is (can? :admin :create :content))
  (can! :admin :delete any)
  (is (can? :admin :delete :content))
  (is (cant? :user :delete :content)))

(deftest policies-test
  (can! :user :create :content)
  (is (can? :user :create :content {:policy :my-policy})
      "all policies inherit from default one")

  (can! :my-policy :user :delete :content)
  (is (cant? :user :delete :content)
      "change in policy doesn't affect default one")
  (is (can? :user :delete :content {:policy :my-policy}))

  (cant! :my-policy :user :delete :profile)
  (can! :user :delete :profile)
  (is (cant? :user :delete :profile {:policy :my-policy})
      "policy restrictions override default policy rules")
  (is (can? :user :delete :profile)))

(deftest entity-test
  (can! :user :create :topic)

  (extend-protocol z/Entity
    clojure.lang.PersistentArrayMap
    (z/as-actor [{:keys [role]}] role)
    (z/as-subject [{:keys [type]}] type))

  (let [user {:role :user}
        content {:type :topic}]
    (is (can? user :create content))
    (is (= {:line 148, :column 3, :ns "zakon.core-test"}
           (find-rule user :create content)))
    (is (cant? user :delete content))))
