# Pogonos
[![Clojars Project](https://img.shields.io/clojars/v/pogonos.svg)](https://clojars.org/pogonos)
![build](https://github.com/athos/pogonos/workflows/build/badge.svg)
[![codecov](https://codecov.io/gh/athos/pogonos/branch/master/graph/badge.svg)](https://codecov.io/gh/athos/pogonos)

Pogonos is another Clojure(Script) implementation of the [Mustache](http://mustache.github.io/) templating language.

## Features

- Completely compliant to the [Mustache spec](https://github.com/mustache/spec), including lambdas
- Fast, but clean implementation
- User-friendly error messages for parsing errors
- Supports all of Clojure, ClojureScript and self-hosted ClojureScript

## Table of Contents

- [Installation](#installation)
- [Usage](#usage)
  - [Fundamentals](#fundamentals)
  - [Outputs](#outputs)
  - [Partials](#partials)
  - [Error messages](#error-messages)

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
and a map of the values passed to the template.
The keys of the map must be keywords.

The function, then, will render the template and return the resulting string.
If you'd rather write out the rendering result to somewhere, instead of
generating a string, you can use *outputs* to specify where to output the result.
See [Outputs](#outputs) for the details.

`render-string` has look-alike cousins named `render-file` and `render-resource`.
The only difference between `render-string` and those functions is that `render-string`
directly takes a template string as an argument whereas `render-file` and
`render-resource` load a template stored in a text file on the file system
or a resource file placed somewhere on the classpath.

Let's say you have a template file located at `resources/sample.mustache`
whose content looks like the following:

```sh
$ cat resources/sample.mustache
Hello, {{name}}!
```

Then, you can render the template using `render-file`:

```clojure
;; loads a template from a text file on the file system
(pg/render-file "resources/sample.mustache" {:name "Rich"})
```

Or if you have the template file on your classpath, you can also render it
with `render-resource` (Here we assume the `resources` directory is
included in the classpath):

```clojure
;; loads a template from a resource file on the classpath
(pg/render-resource "sample.mustache" {:name "Rich"})
```

All the render functions mentioned above are more suitable for one-shot
rendering. But if you want to render the same template with different contexts
over and over again, it would be more efficient to prepare a *parsed template*
prior to rendering.

To prepare a parsed template, use `parse-string` (or `parse-file` / `parse-resource`
accordingly):

```clojure
(def template (pg/parse-string "Hello, {{name}}!"))

template
;=> #pogonos.nodes.Root{:body ["Hello, " #pogonos.nodes.Variable{:keys (:name), :unescaped? false} "!"]}
```

And then, you can render the parsed template using the `render` function:

```clojure
(pg/render template {:name "Rich"})
;=> "Hello, Rich!"

(pg/render template {:name "Alex"})
;=> "Hello, Alex!"
```

At the time, Pogonos does NOT have an internal mechanism to implicitly cache
parsing results for templates you've ever rendered, for better performance
of rendering. So, if you're trying to use Pogonos where the rendering
performance matters much, you may have to cache parsed templates on your own.

### Outputs

An output is the way to specify where to output the rendering result.
By default, Pogonos' render functions output the result as a string.
You can emulate this behavior by specifying `(pogonos.output/to-string)`
to them as the output like the following:

```clojure
(require '[pogonos.output :as output])

(pg/render-string "Hello, {{name}}!" {:name "Clojure"}
                  {:output (output/to-string)})
;=> "Hello, Clojure!"
```

You can also write out the rendering result to a file or the stdout via output:

```clojure
;; writes the rendering result to a file
(pg/render-string "Hello, {{name}}!" {:name "Clojure"}
                  {:output (output/to-file "hello.txt")})

;; writes the rendering result to the stdout
(pg/render-string "Hello, {{name}}!" {:name "Clojure"}
                  {:output (output/to-stdout)})
```

In general, it's more efficient to write out the rendering result
directly to a file than to generate a resulting string, and then write it
out to the file.

### Partials

The Mustache spec provides a feature named [partials](http://mustache.github.io/mustache.5.html#Partials). Partials can be used to include contents from other templates.

Let's say you have a partial `resources/user.mustache` that looks like:

```sh
$ cat resources/user.mustache
<strong>{{name}}</strong>
```

You can render a template that has a partial in it using the render
functions out of the box:

```clojure
(pg/render-string "<h2>Users</h2>{{#users}}{{>user}}{{/users}}"
                  {:users [{:name "Rich"} {:name "Alex"}]})
;=> "<h2>Users</h2><strong>Rich</strong><strong>Alex</strong>"
```

By default, `render-string` and `render-resource` try to find
partials on the classpath, and `render-file` on the file system.

To specify where to find partials explicitly, use the `:partials` option:

```clojure
(require '[pogonos.partials :as partials])

(pg/render-string "<h2>Users</h2>{{#users}}{{>user}}{{/users}}"
                  {:users [{:name "Rich"} {:name "Alex"}]}
                  {:partials (partials/file-partials "resources")})

(pg/render-string "<h2>Users</h2>{{#users}}{{>user}}{{/users}}"
                  {:users [{:name "Rich"} {:name "Alex"}]}
                  {:partials (partials/resource-partials)})
```

You can even specify a map to utilize *inline* partials:

```clojure
(pg/render-string "<h2>Users</h2>{{#users}}{{>user}}{{/users}}"
                  {:users [{:name "Rich"} {:name "Alex"}]}
                  {:partials {:user "<strong>{{name}}</strong>"}}
```

### Error messages

Pogonos aims to provide user-friendly error messages for parse errors
as one of its features, so that users can easily find where and why
the error occurred.

For example, if you miss the closing delimiter of a Mustache tag, you'll
see an error message like the following:

```clojure
(pg/render-string "Hello, {{name" {:name "Clojure"})

;; Execution error (ExceptionInfo) at pogonos.error/error (error.cljc:52).
;; Missing closing delimiter "}}" (1:14):
;;
;;   1| Hello, {{name
;;                   ^^
```

You can opt out this somewhat "verbose" error message if you want,
by specifying the option `{:show-error-details false}`:

```clojure
(pg/render-string "Hello, {{name" {:name "Clojure"}
                  {:show-error-details false})

;; Execution error (ExceptionInfo) at pogonos.error/error (error.cljc:52).
;; Missing closing delimiter "}}" (1:14)
```

Even while disabling verbose error messages, you can get back the detailed
message by calling `perr` explicitly:

```clojure
(pg/perr *e)
;; Missing closing delimiter "}}" (1:14):
;;
;;   1| Hello, {{name
;;                   ^^
```

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
