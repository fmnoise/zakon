(ns zakon.core)

(def any
  "Wildcard for anything"
  ::any)

(def global-policy
  "Global policy"
  ::policy)

(def ^:no-doc relations
  (atom (make-hierarchy)))

(def ^:dynamic *default-result* false)
(def ^:dynamic *policy* global-policy)
(def ^:dynamic *actor-dispatcher* identity)
(def ^:dynamic *action-dispatcher* identity)
(def ^:dynamic *subject-dispatcher* identity)

(defmacro with
  "Executes body with the given options. Options is a map with following keys
  :policy - specifies policy for dispatching rules
  :default - specifies default result when no matching rules found
  :context - map which specifies dispatchers for :actor, :action and :subject"
  {:style/indent 1}
  [options & body]
  `(binding [*policy* (get ~options :policy *policy*)
             *default-result* (get ~options :default-result *default-result*)
             *actor-dispatcher* (get-in ~options [:context :actor] *actor-dispatcher*)
             *action-dispatcher* (get-in ~options [:context :action] *action-dispatcher*)
             *subject-dispatcher* (get-in ~options [:context :subject] *subject-dispatcher*)]
     ~@body))

(defmacro with-context
  "Executes body with given context. Context map may contain :actor, :action and :subject"
  {:style/indent 1}
  [context & body]
  `(binding [*actor-dispatcher* (get ~context :actor *actor-dispatcher*)
             *action-dispatcher* (get ~context :action *action-dispatcher*)
             *subject-dispatcher* (get ~context :subject *subject-dispatcher*)]
     ~@body))

(defmacro with-policy
  "Executes body against given policy"
  {:style/indent 1}
  [policy & body]
  `(binding [*policy* ~policy] ~@body))

(defmacro with-default
  "Executes body returning given default result if no matching rules found"
  {:style/indent 1}
  [result & body]
  `(binding [*default-result* ~result] ~@body))

(defmulti ^:no-doc dispatch
  (fn [policy actor action subject] [policy actor action subject])
  :hierarchy relations)

(defmethod dispatch
  [global-policy any any any]
  [_ _ _ _] {:result *default-result*
             :source ::default-rule})

(defn- known-entity? [entity]
  (isa? @relations entity any))

(defn- known-policy? [policy]
  (isa? @relations policy global-policy))

(defn ^:no-doc register-entity!
  [entity]
  (when-not (known-entity? entity)
    (let [ns (namespace entity)
          str-any (name any)
          entity-any (keyword ns str-any)]
      (when-not (known-entity? entity-any)
        (swap! relations derive entity-any any))
      (when-not (= entity entity-any)
        (swap! relations derive entity entity-any))))
  entity)

(defn ^:no-doc register-policy!
  [policy]
  (when-not (known-policy? policy)
    (swap! relations derive policy global-policy))
  policy)

(defn- entity-name [v]
  (if (keyword? v)
    (name v)
    (-> v str (clojure.string/replace #"\s" "-"))))

(defn ^:no-doc build-entity
  ([value]
   (let [[kw ns]
         (if (keyword? value)
           [(name value) (or (namespace value) (str *ns*))]
           [(-> value str (clojure.string/replace #"\s" "-"))
            (-> value class str (clojure.string/split #"\s") last)])]
     (keyword ns kw)))
  ([domain value]
   (keyword (entity-name domain) (entity-name value))))

(defn inherit!
  "Build child-parent relation"
  [child parent]
  (let [kw-child (build-entity child)
        kw-parent (build-entity parent)]
    (when-not (or (known-entity? kw-parent) (known-policy? kw-parent))
      (register-entity! kw-parent))
    (swap! relations derive kw-child kw-parent)))

(defn inherited?
  "Checks if given values are in child-parent relation"
  [child parent]
  (let [kw-child (build-entity child)
        kw-parent (build-entity parent)]
    (isa? @relations kw-child kw-parent)))

(defn- -resolve [result actor action subject]
  (cond
    (or (fn? result) (instance? clojure.lang.MultiFn result))
    (-> {:actor actor :action action :subject subject}
        result
        (recur actor action subject))

    (instance? clojure.lang.Atom result)
    (recur @result actor action subject)

    :else (boolean result)))

(defn can?
  "Checks if actor can do action on subject with given options: context and policy, where context is a map of 3 keys :actor, :action and :subject, each one containing function for getting rule entity from application object. `options` can omited, global policy and default context will be used instead"
  ([actor action subject] (can? actor action subject nil))
  ([actor action subject {:keys [context policy]
                          :or {policy *policy*}}]
   (let [actor-dispatcher (:actor context *actor-dispatcher*)
         action-dispatcher (:action context *action-dispatcher*)
         subject-dispatcher (:subject context *subject-dispatcher*)]
     (let [kw-actor (-> actor actor-dispatcher build-entity register-entity!)
           kw-action (-> action action-dispatcher build-entity register-entity!)
           kw-subject (-> subject subject-dispatcher build-entity register-entity!)
           kw-policy (-> policy build-entity register-policy!)
           {:keys [result]} (dispatch kw-policy kw-actor kw-action kw-subject)]
       (-resolve result actor action subject)))))

(def cant?
  "Checks if actor can't do action on subject. For more info check `can?` documentation"
  (complement can?))

(defn find-rule
  "Looks up rule for given actor, action and subject with given context and policy, where context is a map of 3 keys :actor, :action and :subject, each one containing function for getting rule entity from application object. `context` and `policy` are optional arguments and can omited, global policy and default context will be used instead"
  ([actor action subject] (find-rule actor action subject nil))
  ([actor action subject {:keys [context policy]
                          :or {policy *policy*}}]
   (let [actor-dispatcher (:actor context *actor-dispatcher*)
         action-dispatcher (:action context *action-dispatcher*)
         subject-dispatcher (:subject context *subject-dispatcher*)]
     (let [kw-actor (-> actor actor-dispatcher build-entity register-entity!)
           kw-action (-> action action-dispatcher build-entity register-entity!)
           kw-subject (-> subject subject-dispatcher build-entity register-entity!)
           kw-policy (-> policy build-entity register-policy!)
           {:keys [source]} (dispatch kw-policy kw-actor kw-action kw-subject)]
       source))))

(defn rules []
  "Returns list of all registered rules"
  ;; add policy param
  (->> dispatch
       methods
       keys))

(defmacro defrule
  "Defines rule for given vector of [actor action subject] and result.
  Can accept policy as first param, otherwise uses default policy.
  Result can be arbitrary value, function or atom.
  Takes the precedence of any previously defined rules in case of conflict."
  ([rule res]
   (let [m (meta &form)]
     `~(with-meta
         `(defrule *policy* ~rule ~res)
         m)))
  ([policy [actor action subject] res]
   (let [m (meta &form)
         ns (str *ns*)]
     `(let [kw-actor# (-> ~actor build-entity register-entity!)
            kw-action# (-> ~action build-entity register-entity!)
            kw-subject# (-> ~subject build-entity register-entity!)
            kw-policy# (-> ~policy build-entity register-policy!)
            new-rule# [kw-policy# kw-actor# kw-action# kw-subject#]
            prev-rules# (-> dispatch prefers keys set (conj any))]
        (defmethod dispatch [kw-policy# kw-actor# kw-action# kw-subject#]
          ~'[_ _ _ _]
          {:result ~res
           :source (assoc ~m :ns ~ns)})
        (when-not (contains? prev-rules# new-rule#)
          (doseq [rule# prev-rules#]
            (prefer-method dispatch new-rule# rule#)))
        {::policy kw-policy#
         ::actor kw-actor#
         ::action kw-action#
         ::subject kw-subject#}))))

(defmacro can!
  "Defines allowing rule for given actor, action and subject. Can accept policy as first param, otherwise uses default policy."
  ([actor action subject]
   (let [m (meta &form)]
     `~(with-meta
         `(can! *policy* ~actor ~action ~subject)
         m)))
  ([policy actor action subject]
   (let [m (meta &form)]
     `~(with-meta
         `(defrule ~policy [~actor ~action ~subject] true)
         m))))

(defmacro cant!
  "Defines declining rule for given actor, action and subject. Can accept policy as first param, otherwise uses default policy."
  ([actor action subject]
   (let [m (meta &form)]
     `~(with-meta
         `(cant! *policy* ~actor ~action ~subject)
         m)))
  ([policy actor action subject]
   (let [m (meta &form)]
     `~(with-meta
         `(defrule ~policy [~actor ~action ~subject] false)
         m))))

(defn cleanup!
  "Cleanup all rules and relations. Reset everything to initial state."
  []
  (let [rules (-> (rules) count)]
    (reset! relations (make-hierarchy))
    (def dispatch nil)
    (defmulti dispatch
      (fn [policy actor action subject] [policy actor action subject])
      :hierarchy relations)
    (defmethod dispatch [global-policy any any any]
      [_ _ _ _] {:result *default-result*
                 :source ::default-rule})
    (println rules " rules cleaned")))