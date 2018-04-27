(ns zakon.idea)

(comment
  ;; ??? action level authorization rules(automatically check on gate) or application level rules(manual check before doing something))
  ;; >>> we  can use both

  ;; ??? try doing that with clara rules -> maybe high-level macro
  ;; as we need to have some way to see which rules applied and why

  ;; mutates global policy, implemented with swap! and Authorization methods
  (defn permit! [& _])
  (defn forbid! [& _]) ;;

  ;; name variations ???
  (defn allow! [& _])
  (defn deny! [& _])
  (defn can! [& _])
  (defn cant! [& _])

  ;; aliases
  (def x! forbid!)
  (def v! permit!)

  ;; usage
  (forbid! :users/delete :*)
  (permit! :staff/delete {:groups [:hr]})
  (permit! :* :admin)

  ;; AER access entity record as a base for all security checks and operations
  ;; an abstraction on user access

  {:id 123 ;; :user {:id 123} or :user-id 123
   :admin true
   :groups []}

  (defprotocol Authorization
    (permits? [this action issuer & targets]) ;; multiple targets!!!
    (forbids? [this action issuer & targets])
    (permit [this action & conditions]) ;; immutable, returns policy, good for ->
    (forbid [this action & conditions])) ;; immutable, returns policy, good for ->

  ;; later
  (defprotocol Composable
    (merge-policy [this policy strategy])) ;; immutable, returns new policy, good for ->

  ;; later
  (defprotocol Loadable
    (load-rules [this rules]) ;; immutable, returns policy, good for ->
    (load-from [this policy-map])) ;; immutable, returns policy, good for ->

  (defprotocol Serializable
    (raw [this])
    (serialize [this format])) ;; defmulti here

  (defrecord AccessPolicy [strategy rules]) ;; storage atom with all rules

  :strategy ;; permit/forbid
  ;; permit - all not forbidden is permitted, forbid - all not permitted is forbidden
  ;; if somethening is both permitted and forbidden, more specific than :* classifiers win
  ;; ??? maybe :default here and :strategy for merge-policies

  (def ^:dynamic *global-policy* (atom (->AccessPolicy :forbid [])))

  ;; ??? maybe we should also have :scope key there to have ability for separate scopes per policy
  ;; for example we may have topics policy with scope = :topics and rules = [:delete :create :update]
  ;; if we'll merge such policies it will add scope to actions so :delete becomes :topics/delete
  ;; if no scope is assigned to policy then actions are passed as is
  ;; add scopes to rules on the fly (if action is not namespaced)
  ;; show warning when merging policies without scope
  (defmacro defpolicy [name & {:keys [rules strategy scope]}] `(def ~name (->AccessPolicy ~strategy ~rules ~scope)))

  (defpolicy topic
    :scope :topics
    :rules []
    :strategy :permit)

  (defpolicy content
    :rules []
    :strategy :permit)

  (defn merge-policy! [& _]) ;; mutates global policy

  (merge-policy! topic :forbid) ;; forbid wins

  (defn permitted? [& _] ;; use protocol methods
    '[policy action user target] ;; given policy used - all objects checked
    '[action user target]) ;; default policy used

  (def v? permitted?)

  (defn forbidden? [& _])
  (def x? forbidden?)

  (defn check [& _]) ;; throws exception if forbidden

  (def user {})
  (def topic {})

  ;; ??? probably object should be always given as there's no sense to check if someone can create abstract topic, but rather some certain topic
  (v? topic :delete user) ;; object is nil, so all topics will be checked ??? ^
  (v? topic :delete user topic) ;; certain topic will be checked
  (x? :topic/create user topic)

  ;; ??? the same here - probably concrete topic should be given as user may be able to delete only his own creations (or anything if he's admin)
  (check :topics/delete user) ;; throws exception if forbidden

  ;; ??? maybe use spec?
  ;; ??? what if we build everything using spec?
  ;; predicate fn needs array with 1 or 2 keys - user and target even if we check only user for composability
  (defn admin? [[user] (= (:role user) :admin)])
  (defn member? [[user community]] (-> user :communities set (contains? (:id community))))
  (s/def :roles/admin admin?)
  (s/def :community/member member?)
  (s/def :community/admin (s/and admin? member?))
  (s/valid? :roles/admin {:user {:role :admin}})
  (s/valid? :roles/admin {:community {:id 1}
                          :user {:role :admin :communities [2]}})

  (permit! :community/delete :community/admin)
  (permit! :community/read :community/member)

  (permitted? :community/delete [current-user community])


  (permit! :topics/delete :admin) ;; kw attbitute to get from user/eac
  (permit! :topics/delete {:groups [:managers]}) ;; or map with attributes (or whatever)
  (permit! :topics/delete
           (fn [user topic]
             (and (= "Ivan" (:name user))
                  (= (:author-id topic) (:id user))))) ;; or fn
  ;; ??? how to access system context if we need to perform query for fetching some user-related data
  ;; >>> first enrich user with this data then pass to checker

  (defmacro with-policy [policy &body]) ;; binding *authorization-policy*

  (with-policy topic (permit! :delete :admin))
  ;; ??? do we really need policies, global one seems to be nice
  ;; >>> nope. we need it for flexibility
  ;; >>> XXX prefer with-policy macro instead of policy as 1st optional param - I like that!

  '(permit|restrict action condition pre-condition post-actions)
  'condition = [fn 1/2 args or kw] or [vector of 2(fns maps vectors)] or [map]

  ;; XXX SIDE EFFECTS ARE REALLY REALLY BAD HERE!!!
  'pre-condition - fn/2 that return vector/2 with [user target] enriched with req data
  ;; we may add some spec/schema of which things should be present to perform check
  'pre-condition - [spec user, spec target]
  ;; for example -> key :students should exist and eq nil/[] for non-coach or :is-coach should be Bool
  ;; exception can be raised if requirements are not fullfilled (optionally) or just decline action

  ;; XXX SIDE EFFECTS ARE ALSO REALLY REALLY BAD HERE!!!
  'post-actions - vector of fns - example (fn [permitted? user target] (when-not permitted? (log-failure user target)))
  ;; implement with agents ^^^

  ;; EXAMPLES

  (defn enrich-with-students [http-client user _]
    ;; ... fetch students
    [(assoc user :students [1 2 3]) _]) ;; other object is not changed

  (defn detect-coaching [db user learner]
    ;; ... fetch learned-coach pairs from db
    [(assoc user :is-coach true)
     (assoc learner :is-learner true)])

  (defn enrich-with-groups [db user learner]
    ;; ... fetch groups from db
    [(assoc user :groups [:coaches])
     (assoc learner :groups [:learners])])

  (defpolicy learning)

  ;; (after-system-start (define-rules system))
  (defn define-rules [{:keys [http-client db]}]
    (with-policy learning
      (permit! :coach
               (fn [user learner]
                 (-> user :students set (contains? (:id learner))))
               (partial enrich-with-students http-client))
      ;; last fn is called before check, accepts 2 args - user & object, returns array [user object] enriched with data
      ;; this results are then passed into check-fn with apply
      ;; XXX SIDE EFFECTS ARE BAD HERE!!!

      (permit! :coach
               [:is-coach? :is-learner?] ;;first kw is called on user, second on object
               (partial detect-coaching db))

      (permit! :coach
               [[:is-coach? :know-math?] [:is-learner? :learns-math]] ;; each kw groups works as AND
               (partial detect-coaching db))

      (permit! [:coach :one-to-one] ;; actions can be vector too
               [[:is-coach? :know-math?] [:is-learner? :learns-math]] ;; each kw groups works as AND
               (partial detect-coaching db))

      (permit! :coach
               [{:groups [:coaches]} ;; dispatched on user,
                {:groups [:learners]}] ;; dispatched on target
               (partial enrich-with-groups db))

      (permit! :coach-math
               [[{:groups [:coaches]} :know-math?] ;; AND
                {:groups [:learners]}]
               (partial enrich-with-groups db)
               (fn [v? user _] (when-not v? (log user " tried to teach math!!!"))))))

  ;; ??? do we need that hierarchy magic?
  ;; (derive :topic/* :topic/delete)
  ;; (derive :*/delete :topic/delete)
  ;; (derive :*/* :topic/*)
  ;; (derive :*/* :*/delete)
  ;; (derive topic :*/* :*/delete)

  ;; maybe no multimethods magic but map lookup and special symbols
  :*/* ;; anything
  :*   ;; anyone

  '(permit! :*/* :admin? (enrich-with-admin db))

  (def a #:user{:admin true :groups [:managers]})

  ;; ??? read from db/config?
  ;; >>> yes, load-rules! load-policy! and fn which will compose policy structure

  ;; ??? how to update if changed in db?
  ;; >>> easy - subscribers and notifications, or kafka

  (defn search-actions [action]
    (let [ns (namespace action)]
      #{action (keyword ns "*") :*}))

  (qualified-keyword? :*)

  (name :a/*)

  (if (= :strategy :forbid)
    (or (v? :* user) (v? :scope/* user) (v? :scope/action user))
    (not (or (x? :* user) (x? :scope/* user) (x? :scope/action user))))

  ;; STRATEGY RAW DATA - no scope here as actions are already scoped
  {:strategy :forbid
   :rules [{:permit :community/delete                               ;; permission
            :if [{:groups [:managers]} {:type [:public :open]}] ;; condition
            :pre check-groups-enrichment ;; pre
            :post log-community-deletion-attempt} ;; post
           {:v :user/delete
            :? {:groups #{:admin :staff}} ;; groups has :admin AND :staff - note set usage
            :! enrich-with-groups
            :> do-some-post-action}
           {:v :user/delete
            :? {:groups [:admin :staff]} ;; groups has :admin OR :staff
            :! enrich-with-groups}
           {:v :topic/create
            :? [:* {:type [:public :open]}]
            :! enrich-with-groups}
           {:x :community/*                   ;; all actions with community
            :? {:organization {:type :flat}}} ;; nested lookup (get-in user [:organization :type])
           {:x :*                             ;; all actions
            :? :blocked?}]}

  ;; (defn load-policy! ([from] [to from])) ;; reads map (from) to some policy(to) or globally
  ;; (defn load-rules! ([rules] [policy rules])) ;; reads array with rules

  ;; TODO let's TDD on that

  ;; ??? how to compose this with service scopes
  ;; A->
  [:widgets/dashboard :publish] '=> :widgets.dashboard/publish
  ;; so if scope is namespaced it's converted to :ns.keyword
  (defn action-kw [action-str]) ;; XXX helper for converting "scope.subscope.action" into :scope.subscope/action

  ;; convenient helpers to work in code
  '(if-permitted [action user target] (do-smthn) (do-smthn-else))
  '(if-restricted [action user target] (do-smthn) (do-smthn-else))
  '(when-permitted [action user target] (do-smthn))
  '(when-restricted [action user target] (do-smthn)))
