# zakon [![CircleCI](https://circleci.com/gh/fmnoise/zakon/tree/master.svg?style=svg)](https://circleci.com/gh/fmnoise/zakon/tree/master) [![cljdoc badge](https://cljdoc.xyz/badge/zakon/zakon)](https://cljdoc.xyz/d/zakon/zakon/CURRENT)

> But whoso looketh into the perfect law of liberty, and continueth therein, he being not a forgetful hearer, but a doer of the work, this man shall be blessed in his deed.
> (James 1:25)

zakon (/zakon/ rus. *закон - law*) is declarative authorization library inspired by https://github.com/ryanb/cancan and built on top of Clojure multimethods

## Usage

[![Current Version](https://clojars.org/zakon/latest-version.svg)](https://clojars.org/zakon)

```clojure
(require '[zakon.core :refer :all])
```

### Rules

The core concept of `zakon` is **rule** - a combination of **actor**(who performs action), **action**(what is performed), **subject**(on which action is performed) and associated result, which represents the answer to question "can this actor do this action on this subject?".
So textual rule "user can create content", can be written as:
```clojure
(can! :user :create :content)
```
In the example above actor is `:user`, action is `:create` and subject is `:content`. Result is simply `true`.
The opposite effect can be achieved with `cant!`:
```clojure
(cant! :user :delete :content)
```
`can?` and `cant?` can be used to test rules:
```clojure
(can? :user :create :content) => true
(can? :user :delete :content) => false
(cant? :user :create :content) => false
(cant? :user :delete :content) => true
```
Rules have a priority, in case of conflict last applied rule always wins:
```clojure
(can! :user :delete :content)
(can? :user :delete :content) => true
(cant! :user :delete :content)
(can? :user :delete :content) => false
(can! :user :delete :content)
(can? :user :delete :content) => true
```
List of registered rules can be obtained using `rules` method:
```clojure
(rules)
=> ([:zakon.core/policy :zakon.core/any :zakon.core/any :zakon.core/any] [:zakon.core/policy :zakon.core/user :zakon.core/delete :zakon.core/content])
```

`cleanup!` cleans up all defined rules and prints cleaned rules count to stdout.

### Wildcards

In some cases kind of wildcard should be specified in the rule, for example "admin can do anything with content" (action is a wildcard - anything).
`any` can be used to wildcard actor, action or subject in any combinations:
```clojure
(can! :admin any :content)
(can? :admin :create :content) => true
(can? :admin :delete :content) => true

(can! :admin any any)
(can? :admin :create :profile) => true
(can? :admin :upload :file) => true
```
`any` can be also used in rules test:
```clojure
(can? :admin any any) => true
(can? any any any) => false
```
Rules with wildcards may sound ambigious, e.g. "user can do anything with content, but user cannot delete anything".
Can user delete content? From zakon's point of view it can't, as statement about deletion restriction is last and works as clarification.
```clojure
(can! :user any :content)
(cant! :user :delete any)
(can? :user :delete :content) => false
```
The same logic works if we swap sentence parts - "user cannot delete anything, but can do anything with content".
```clojure
(cant! :user :delete any)
(can! :user any :content)
(can? :user :delete :content) => true
```

### Default rule

In the example above, restricting part is redundant, because of zakon is restrictive by default (everything which is not specified as allowed, is restricted).
So initially there's only **default rule** defined using wildcards and equivalent to:
```clojure
(cant! any any any)
```
If we dispatch rule that was not yet defined, the default one will be used instead.
If target system should be not restrictive by default(everything which is not specified as restricted, is allowed), that can be either redefined globally:
```clojure
(can! any any any)
```
or locally using `with-default`:
```clojure
(with-default true
 (can? any any any)) => true
```

### Debugging rules

With all that wildcards and defaults ambiguity, it can be hard to understand which rule was dispatched for given arguments.
`find-rule` can be used to debug rule:
```clojure
(find-rule :user :delete :content) => {:line 4, :column 1, :ns "user"}
(find-rule any :delete :content) => :zakon.core/default-rule
```

### Resolvers

`can!` and `cant!` are suitable for specifying only simple boolean result, which is enough for simple cases.
For more complex ones, `defrule` should be used. `defrule` supports specifying both simple values and **resolvers** - atoms and fns to dispatch rule.
In the following example atom is used to store result:
```clojure
(def content-deletion-allowed (atom true))
(defrule [:user :delete :content] content-deletion-allowed)
(can? :user :delete :content) => true
(swap! content-deletion-allowed not)
(can? :user :delete :content) => false
```
Function or multimethod which is used as resolver should accept single argument - map with keys `:actor`, `:action` and `:subject`:
```clojure
(def user-types #{:content :comment})
(defrule [:user :create any] (fn [{:keys [subject]}] (user-types subject)))
(can? :user :create :content) => true
(can? :user :create :comment) => true
(can? :user :create :share) => false
```

In order to keep things predictable `can?` and `cant?` always return boolean result, so there's no need to do conversion manually in `defrule` or resolver:
```clojure
(defrule [:admin :delete :profile] 1)
(defrule [:user :delete :profile] nil)
(can? :admin :delete :profile) => true
(can? :user :delete :profile) => false
```

### Entities

Actor, action and subject values are **entities**. In the examples above we used keywords, but any other value can also be an entity.
```clojure
(can! 1 + 2)
(can? 1 + 2) => true
```
Values which are not keywords are converted into keywords automatically, e.g. in the example above `1` becomes `:java.lang.Long\1` and `+` becomes `:clojure.core$_PLUS_/clojure.core$_PLUS_@5d3882cf`.
Each entity belongs to domain, which is keyword's namespace(or current namespace if keyword is non-qualified).
Each domain have special value `any` which represents domain root object. All domain entities are inherited from domain root:
```clojure
(can! :java.lang.Long/any + :java.lang.Long/any)
(can? 3 + 4) => true
```
As shown above `any` can be used as wildcard for defining rules, so it's root object for all other entities and all domain roots are inherited from `any`.
`inherit!` can be used to make child-parent relation for any other object:
```clojure
(inherit! :role/admin :role/user)
(can! :role/user  :http/get :routes/home)
(can! :role/admin :http/any :routes/admin)

(can? :role/admin :http/get :routes/home)  => true
(can? :role/admin :http/get :routes/admin) => true
(can? :role/user  :http/get :routes/admin) => false
```
Entities hierarchy is stored in atom called `relations`:
```clojure
@relations
=> {:parents {:role/any #{:zakon.core/any}, :role/user #{:role/any}, :role/admin #{:role/user}, :http/any #{:zakon.core/any}, :http/get #{:http/any}, :routes/any #{:zakon.core/any}, :routes/home #{:routes/any}, :routes/admin #{:routes/any}}, :ancestors {:role/any #{:zakon.core/any}, :role/user #{:zakon.core/any :role/any}, :role/admin #{:zakon.core/any :role/user :role/any}, :http/any #{:zakon.core/any}, :http/get #{:zakon.core/any :http/any}, :routes/any #{:zakon.core/any}, :routes/home #{:routes/any :zakon.core/any}, :routes/admin #{:routes/any :zakon.core/any}}, :descendants {:zakon.core/any #{:routes/any :http/any :routes/home :role/user :role/admin :role/any :http/get :routes/admin}, :role/any #{:role/user :role/admin}, :role/user #{:role/admin}, :http/any #{:http/get}, :routes/any #{:routes/home :routes/admin}}}
```
`inherited?` can be used to check if 2 enities are in child-parent relations:
```clojure
(inherited? :role/admin :role/user) => true
(inherited? :http/get :http/any) => true
(inherited? :http/any any) => true
```

### Context

Let's say we want to define a rule which allows to create content with type `:acticle` for any user, and allows doing anything to user with role `:admin`. User and content are respresented as records.
```clojure
(defrecord User [role])
(defrecord Content [type])

(def admin (->User :admin))
(def topic (->Content :topic))
```
Despite any value can be an entity, that's impractical to define rule like this:
```clojure
(defrule [any any any]
  (fn [{:keys [actor action subject]}]
    (or (and (= action :create)
             (= (:type subject) :article))
        (= (:role actor) :admin))))

(can? admin :create topic) => true
```
Such rules are very generic and resolver function quickly becomes cumbersome.
**Context** allows to separate rule declaration and getting data required for rule checking from application objects. Context is a map with functions for turning application objects into rule entities.
So rule from example above can be rewritten using context:
```clojure
(can! :user :create :article)
(can! :admin any any)
(def context {:actor :role :subject :type})

(can? admin :create topic {:context context}) => true

;; context can be also specified using with-context
(with-context context
  (can? admin :create topic)) => true
```

### Policies

Rule sets can be kept isolated from each other in scope of **policy**.
Policies can contain the same rules with different values, for example:
```clojure
;; policy is passed as first argument when defining rule
(cant! :restrictive-policy :user any any)
(can! :permissive-policy :user any any)

;; when checking rule, policy is passed as options similarly to context
(can? :user :say :hello {:policy :restrictive-policy}) => false
(can? :user :say :hello {:policy :permissive-policy}) => true

;; policy can be also specified using with-policy
(with-policy :restrictive-policy
  (can? :user :say :hello)) => false
```
All policies are inherited from `:zakon.core/policy` which acts as **global policy**. If specified policy can't dispatch rule, `:zakon.core/policy` will be used.

## License

Copyright © 2018 fmnoise

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
