(ns zakon.todo)

;; XXX with-dispatchers {:actor (fn []) :subject (fn [])}
;; XXX load-policy for loading from plain values (or db)
`(load-policy {:policy-name [[:user :do :stuff true] [:user :sell :goods false]]})
;; ??? how to serialize functions and keywords properly to db

;; ??? separate :actor/any :action/any :subject/any
;; ??? (defrule->> res [a b c] [d e f]
;; ??? (can->> [a b c] [d e f] [g h j])

;; not sure we need all that stuff
;; ??? (can-> {:actor :user} [:play :games] [:watch :tv])
;; ??? (can-> {:action :play} [:user :games] [:child :toys])
;; ??? (can-> {:subject :games} [:user :play] [:child :watch])
;; ??? (can-> {:actor :user :subject :games} :play :watch)
;; ??? (can-some? {:actor (User. 1)} [:go :home] [:play :football])
;; ??? readers
;; #policy [[:user :do :stuff true] [:user :do :other false]]
;; #rule [:user :do :stuff false]

;; ??? atom with vector of applied rules
;; ??? remove creating any for each ns

;; ??? how to dispatch on set or vector
'(with-dispatchers {:actor :admin-group :subject :type}
  (can! :file-admin :delete :file)
  (can? {:role :admin :admin-groups [:file-admin :site-admin]}
        :delete
        {:name "Test" :type :file})) ;; => false :(

;; of course we can
'(with-dispatchers {:actor :role :subject :type}
   (defrule [:admin :delete :file]
     (fn [{:keys [actor]}] (contains? (-> actor :admin-groups set) :file-admin))))

;; ??? what about setting dispatchers as maps
`(can! {:role :admin} :delete {:type :file})
