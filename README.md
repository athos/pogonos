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

We will show you how to use Pogonos in this section, but if you're not too familiar with the Mustache language itself, you might want to read its [documentation](http://mustache.github.io/) first.

### Fundamentals

The easiest way to use the library is to just call `render-string`:

```clojure
(require '[pogonos.core :as pg])

(pg/render-string "Hello, {{name}}!" {:name "Rich"})
;=> "Hello, Rich!"
```

`render-string` takes two arguments; a string that represents a Mustache template,
and a map of the values that are referenced in the template.
The keys of the map must be keywords.

The function, then, will render the template and return the resulting string.
If you'd rather write out the resulting content somewhere instead of generating a string,
you can use *outputs* to specify where to output the result.
See [Outputs](#outputs) for the details.

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
