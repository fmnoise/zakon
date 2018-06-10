(ns zakon.core)

(def any ::any)
(def global-policy ::policy)
(def relations (atom (make-hierarchy)))

(def ^:dynamic *default-result* false)
(def ^:dynamic *policy* global-policy)
(def ^:dynamic *actor-dispatcher* identity)
(def ^:dynamic *action-dispatcher* identity)
(def ^:dynamic *subject-dispatcher* identity)

(defmacro with
  "Executes body with the given options. Options is a map with following keys
  :policy - specifies policy for dispatching rules
  :default-result - specifies default result when no matching rules found
  :actor-dispatcher - specifies dispatcher for actor
  :action-dispatcher - specifies dispatcher for action
  :subject-dispatcher - specifies dispatcher for subject"
  [options & body]
  `(binding [*policy* (get ~options :policy *policy*)
             *default-result* (get ~options :default-result *default-result*)
             *actor-dispatcher* (get ~options :actor-dispather *actor-dispatcher*)
             *action-dispatcher* (get ~options :action-dispatcher *action-dispatcher*)
             *subject-dispatcher* (get ~options :subject-dispatcher *subject-dispatcher*)]
     ~@body))

(defmacro with-dispatchers
  "Executes body with given dispatchers. Dispatchers map may contain :actor, :action and :subject"
  [dispatchers-map & body]
  `(binding [*actor-dispatcher* (get ~dispatchers-map :actor *actor-dispatcher*)
             *action-dispatcher* (get ~dispatchers-map :action *action-dispatcher*)
             *subject-dispatcher* (get ~dispatchers-map :subject *subject-dispatcher*)]
     ~@body))

(defmacro with-policy
  "Executes body against given policy"
  [policy & body]
  `(binding [*policy* ~policy] ~@body))

(defmacro with-default-result
  "Executes body returning given default result if no matching rules found"
  [result & body]
  `(binding [*default-result* ~result] ~@body))

(defmulti dispatch
  "Base method for rules dispatching. Should not be used directly until you know what you're doing"
  (fn [policy actor action subject] [policy actor action subject])
  :hierarchy relations)

(defmethod dispatch
  [global-policy any any any]
  [_ _ _ _] {:result *default-result*
             :source ::default-rule})

(defn- known-entity? [entity]
  (or (= entity any)
      (contains? (descendants @relations any) entity)))

(defn- known-policy? [policy]
  (or (= policy global-policy)
      (contains? (descendants @relations global-policy) policy)))

(defn- register-entity! [entity]
  (let [ns (namespace entity)
        str-any (name any)
        entity-any (keyword ns str-any)]
    (when-not (known-entity? entity-any)
      (swap! relations derive entity-any any))
    (swap! relations derive entity entity-any)))

(defn- register-policy! [policy]
  (swap! relations derive policy global-policy))

(defn- entity-name [v]
  (if (keyword? v)
    (name v)
    (-> v str (clojure.string/replace #"\s" "-"))))

(defn build-entity
  "Service method used to build entity from given value. Should not be used directly"
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
        kw-parent (build-entity parent)
        _ (when-not (known-entity? kw-parent) (register-entity! kw-parent))]
    (swap! relations derive kw-child kw-parent)))

(defn inherited?
  "Checks if given values are in child-parent relation"
  [child parent]
  (let [kw-child (build-entity child)
        kw-parent (build-entity parent)]
    (-> @relations
        (descendants kw-parent)
        (contains? kw-child))))

(defn- extract [result actor action subject]
  ;; find workaround to dispatch multimethods
  (cond
    (fn? result) (-> {:actor actor :action action :subject subject}
                     result
                     (extract actor action subject))
    (instance? clojure.lang.Atom result) (extract @result actor action subject)
    :else (boolean result)))

(defn can?
  "Checks if actor can do action on subject"
  ([actor action subject]
   (can? *policy* actor action subject))
  ([policy actor action subject]
   (let [kw-actor (-> actor *actor-dispatcher* build-entity)
         kw-action (-> action *action-dispatcher* build-entity)
         kw-subject (-> subject *subject-dispatcher* build-entity)
         kw-policy (build-entity policy)
         _ (when-not (known-entity? kw-actor) (register-entity! kw-actor))
         _ (when-not (known-entity? kw-action) (register-entity! kw-action))
         _ (when-not (known-entity? kw-subject) (register-entity! kw-subject))
         _ (when-not (known-policy? kw-policy) (register-policy! kw-policy))
         {:keys [result]} (dispatch kw-policy kw-actor kw-action kw-subject)]
     (extract result actor action subject))))

(def cant?
  "Checks if actor can't do action on subject"
  (complement can?))

(defn find-rule
  "Looks up for rule with given action, action and subject and return coordinates where it's declared.
  Can accept policy as first agrument, otherwise performs rule lookup against default policy"
  ([actor action subject]
   (find-rule *policy* actor action subject))
  ([policy actor action subject]
   (let [kw-actor (-> actor *actor-dispatcher* build-entity)
         kw-action (-> action *action-dispatcher* build-entity)
         kw-subject (-> subject *subject-dispatcher* build-entity)
         kw-policy (build-entity policy)
         _ (when-not (known-entity? kw-actor) (register-entity! kw-actor))
         _ (when-not (known-entity? kw-action) (register-entity! kw-action))
         _ (when-not (known-entity? kw-subject) (register-entity! kw-subject))
         _ (when-not (known-policy? kw-policy) (register-policy! kw-policy))
         {:keys [source]} (dispatch kw-policy kw-actor kw-action kw-subject)]
     source)))

(defn list-rules []
  ;; add policy param
  (-> dispatch
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
    `(let [kw-actor# (build-entity ~actor)
           kw-action# (build-entity ~action)
           kw-subject# (build-entity ~subject)
           kw-policy# (build-entity ~policy)
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
  (let [rules (-> (list-rules) count)]
    (reset! relations (make-hierarchy))
    (def dispatch nil)
    (defmulti dispatch
      (fn [policy actor action subject] [policy actor action subject])
      :hierarchy relations)
    (defmethod dispatch [global-policy any any any]
      [_ _ _ _] {:result *default-result*
                 :source ::default-rule})
    (println rules " rules cleaned")))