# zakon

> But whoso looketh into the perfect law of liberty, and continueth therein, he being not a forgetful hearer, but a doer of the work, this man shall be blessed in his deed.
> (James 1:25)

zakon (/zakon/ rus. *закон - law*) is declarative authorization library inspired by https://github.com/ryanb/cancan

It has no dependencies (despite clojure itself) and uses clojure multimethods under the hood.

Everything is highly experimental.

## Usage

**Not released to clojars yet**

```clojure
(use '[zakon.core])
```

### Rules

The core concept of `zakon` is rule - a combination of actor(who performs action), action(what is performed), subject(on which action is performed) and result.
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
In some cases we need to specify kind of wildcard in rule, for example "admin can do anything with content".
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
Rules have a priority, in case of conflict last applied rule always wins:
```clojure
(can! :user :delete :content)
(can? :user :delete :content) => true
(cant! :user :delete :content)
(can? :user :delete :content) => false
(can! :user :delete :content)
(can? :user :delete :content) => true
```
Sometimes rules are ambigious by design, e.g. "user can do anything with content, but user cannot delete anything".
Can user delete content? Probably not, as statement about deletion restriction is last and works as clarification.
```clojure
(can! :user any :content)
(cant! :user :delete any)
(can? :user :delete :content) => false
```
Let's rephrase last sentence - "user cannot delete anything, but can do anything with content".
Can user delete content in such case? Probably yes.
```clojure
(cant! :user :delete any)
(can! :user any :content)
(can? :user :delete :content) => true
```
`find-rule` can be used to find out which rule was used:
```clojure
(find-rule :user :delete :content) => {:line 4, :column 1, :ns "user"}
(find-rule any :delete :content) => :zakon.core/default-rule
```
`can!` and `cant!` allow for only simple boolean result returned. For more complex cases, `defrule` should be used.
In the following example atom is used to store result:
```clojure
(def content-deletion-allowed (atom true))
(defrule [:user :delete :content] content-deletion-allowed)
(can? :user :delete :content) => true
(swap! content-deletion-allowed not)
(can? :user :delete :content) => false
```
In addition to atom, 1-arity function can be also used as result in `defrule`. A map with keys `:actor`, `:action` and `:subject` will be passed to function:
```clojure
(def user-types #{:content :comment})
(defrule [:user :create any] (fn [{:keys [subject]}] (user-types subject)))
(can? :user :create :content) => true
(can? :user :create :comment) => true
(can? :user :create :share) => false
```
`cleanup!` cleans up all defined rules and prints cleaned rules count to stdout

### Entities
### Dispatchers
### Policies

## License

Copyright © 2018 DAWCS

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
