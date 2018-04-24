# police

> Big car  
> Movie star  
> Hot tip  
> Go far  
> Blind date  
> Too late  
> Take a bus  
> Don't wait  

Police is declarative authorization library inspired by https://github.com/ryanb/cancan
It uses clojure multimethods under the hood and is highly experimental

## Usage

```clojure
(:require '[police.core :refer [can! cant! can? any]])

(can? :i :go :home) ;; => false
(can! :i :go :home)
(can? :i :go :home) ;; => true
(cant! any :go :home)
(can? :boss :go :home) ;; => false
(can? :i :go :home) ;; => true
```

## License

Copyright © 2018 DAWCS

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
