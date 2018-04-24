(ns police.core)

;; XXX add docstrings

(def any ::any) ;; ??? :actor/any :action/any :subject/any

(def entities (atom (make-hierarchy)))

(def ^:dynamic *default-result* false)

(defn ^:dynamic *dispatcher*
  [actor action subject]
  [actor action subject])

(defn set-default-result! [res]
  (alter-var-root #'*default-result* (constantly res)))

(defn set-dispatcher! [f]
  (alter-var-root #'*dispatcher* (constantly f)))

(defmulti dispatch
  (fn [actor action subject] [actor action subject])
  :hierarchy entities) ;; #'dispatcher

(defmethod dispatch [any any any] [_ _ _] {:result *default-result*
                                           :source ::default})

(defn- known? [s]
  (or (= s any)
      (contains? (descendants @entities any) s)))

(defn- register! [kw]
  (let [ns (namespace kw)
        str-any (name any)
        kw-any (keyword ns str-any)]
    (when-not (known? kw-any)
      (swap! entities derive kw-any any))
    (swap! entities derive kw kw-any)))

(defn make-kw [v]
  (let [kw (cond
             (keyword? v) v
             (string? v) (keyword v)
             true (-> v str keyword))
        nsp (or (namespace kw) (str *ns*))]
    (keyword nsp (name kw))))

(defn inherit! [child parent]
  (let [kw-child (make-kw child)
        kw-parent (make-kw parent)
        _ (when-not (known? kw-parent) (register! kw-parent))]
    (swap! entities derive kw-child kw-parent)))

(defn inherited? [child parent]
  (let [kw-child (make-kw child)
        kw-parent (make-kw parent)]
    (-> @entities
        (descendants kw-parent)
        (contains? kw-child))))

(defn can? [actor action subject]
  (let [[actor' action' subject'] (*dispatcher* actor action subject)
        kw-actor (make-kw actor')
        kw-action (make-kw action')
        kw-subject (make-kw subject')
        _ (when-not (known? kw-actor) (register! kw-actor))
        _ (when-not (known? kw-action) (register! kw-action))
        _ (when-not (known? kw-subject) (register! kw-subject))
        {:keys [result]} (dispatch kw-actor kw-action kw-subject)]
    (if (ifn? result)
      (result [actor action subject])
      result)))

(defn describe [actor action subject]
  (let [[actor' action' subject'] (*dispatcher* actor action subject)
        kw-actor (make-kw actor')
        kw-action (make-kw action')
        kw-subject (make-kw subject')
        _ (when-not (known? kw-actor) (register! kw-actor))
        _ (when-not (known? kw-action) (register! kw-action))
        _ (when-not (known? kw-subject) (register! kw-subject))
        {:keys [source]} (dispatch kw-actor kw-action kw-subject)]
    source))

(def cant? (complement can?))

(defmacro defrule
  [[actor action subject] res]
  (let [m (meta &form)
        ns (str *ns*)]
   `(let [kw-actor# (make-kw ~actor)
          kw-action# (make-kw ~action)
          kw-subject# (make-kw ~subject)
          prev-rules# (-> dispatch prefers keys)]
      (defmethod dispatch [kw-actor# kw-action# kw-subject#]
        [~'_ ~'_ ~'_]
        {:result ~res
         :source (assoc ~m :ns ~ns)})
      (if (empty? prev-rules#)
        (prefer-method dispatch [kw-actor# kw-action# kw-subject#] any)
        (doseq [rule# prev-rules#]
          (when-not (= rule# [kw-actor# kw-action# kw-subject#])
            (prefer-method dispatch [kw-actor# kw-action# kw-subject#] rule#))))
      nil)))

(defmacro can! [actor action subject]
  (let [m (meta &form)]
    `~(with-meta
       `(defrule [~actor ~action ~subject] true)
        m)))

(defmacro cant! [actor action subject]
  (let [m (meta &form)]
    `~(with-meta
        `(defrule [~actor ~action ~subject] false)
        m)))
