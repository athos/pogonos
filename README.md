# Pogonos
[![Clojars Project](https://img.shields.io/clojars/v/pogonos.svg)](https://clojars.org/pogonos)
![build](https://github.com/athos/pogonos/workflows/build/badge.svg)
[![codecov](https://codecov.io/gh/athos/pogonos/branch/master/graph/badge.svg)](https://codecov.io/gh/athos/pogonos)

Pogonos is another Clojure implementation of the [Mustache](http://mustache.github.io/) templating language.

## Features

- Completely compliant to the [Mustache spec](https://github.com/mustache/spec), including lambdas
- Fast, but clean implementation
- User-friendly error messages for parsing errors
- Supporting all of Clojure, ClojureScript and self-hosted ClojureScript

## Table of Contents

- [Installation](#installation)
- [Usage](#usage)
  - [Fundamentals](#fundamentals)
  - [Outputs](#outputs)
  - [Partials](#partials)
  - [Errors](#errors)

## Installation

Add the following to your project's `:dependencies`:

[![Clojars Project](https://clojars.org/pogonos/latest-version.svg)](https://clojars.org/pogonos)

## Usage

### Fundamentals

```clojure
(require '[pogonos.core :as pg])

(pg/render-string "Hello, {{name}}!" {:name "Rich"})
;=> "Hello, Rich!"
```

```clojure
;; load a template from a file
(pg/render-file "sample.mustache" {:name "Rich"})

;; load a template from a resource file on the classpath
(pg/render-resource "sample.mustache" {:name "Rich"})
```

```clojure
(def template (pg/parse-string "Hello, {{name}}!"))
(pg/render template {:name "Rich"})
;=> "Hello, Rich!"
(pg/render template {:name "Alex"})
;=> "Hello, Alex!"
```

### Outputs

```clojure
(require '[pogonos.output :as output])

;; Prints the rendered result to stdout
(pg/render-string "Hello, {{name}}" {:name "Rich"}
                  {:output (output/to-stdout)})

;; Writes the rendered result to a file
(pg/render-string "Hello, {{name}}" {:name "Rich"}
                  {:output (output/to-file "result.txt")})
```

### Partials

```clojure
(pg/render-string "{{>node}}" {:content "X" :nodes [{:content "Y" :nodes []}]}
                  {:partials {:node "{{content}}<{{#nodes}}{{>node}}{{/nodes}}>"}})
;=> "X<Y<>>"
```

### Errors

(TODO)

## License

Copyright © 2020 Shogo Ohta

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
