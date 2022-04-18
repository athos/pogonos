# Pogonos
[![Clojars Project](https://img.shields.io/clojars/v/pogonos.svg)](https://clojars.org/pogonos)
![build](https://github.com/athos/pogonos/workflows/build/badge.svg)
[![codecov](https://codecov.io/gh/athos/pogonos/branch/master/graph/badge.svg)](https://codecov.io/gh/athos/pogonos)

Pogonos is another Clojure(Script) implementation of the [Mustache](http://mustache.github.io/) templating language.

## Features

- Completely compliant to the [Mustache spec](https://github.com/mustache/spec), including lambdas
- Fast but clean implementation
- User-friendly error messages for parsing errors
- Handy API for use from the CLI
- Supports Clojure, ClojureScript and self-hosted ClojureScript

## Project status

Pogonos is still in beta. The public API provided in the `pogonos.core`
namespace is almost fixed while other interfaces, including various
extension points (such as readers, outputs, AST nodes), are subject to change.

## Table of Contents

- [Installation](#installation)
- [Usage](#usage)
  - [Fundamentals](#fundamentals)
  - [Outputs](#outputs)
  - [Partials](#partials)
  - [Error messages](#error-messages)
  - [CLI usage](#cli-usage)

## Installation

Add the following to your project's `:dependencies`:

[![Clojars Project](https://clojars.org/pogonos/latest-version.svg)](https://clojars.org/pogonos)

## Usage

In this section, you'll see how to use Pogonos, but if you're not so familiar with the Mustache language itself, you might want to read its [documentation](http://mustache.github.io/) first.

### Fundamentals

#### `render-string`

The easiest way to use the library is to just call `render-string`:

```clojure
(require '[pogonos.core :as pg])

(pg/render-string "Hello, {{name}}!" {:name "Rich"})
;=> "Hello, Rich!"
```

`render-string` takes two arguments; a string that represents a Mustache template,
and a map of the values passed to the template.
The keys of the map must be keywords.

Then, the function will render the template and return the resulting string.
If you'd rather write out the rendering result to somewhere, instead of
generating a string, you can use *outputs* to specify where to output the result.
See [Outputs](#outputs) for details.

#### `render-file` / `render-resource`

`render-string` has two look-alike cousins named `render-file` and `render-resource`.
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

#### `parse-string` / `parse-file` / `parse-resource` / `render`

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
parsed results of previously rendered templates for better rendering performance.
So, if you want to use Pogonos in situations where the rendering performance matters,
you may have to cache parsed templates yourself.

#### `check-string` / `check-file` / `check-resource` \[`0.2.0+`\]

Since `0.2.0`, Pogonos also provides another set of functions: `check-string`, `check-file` and `check-resource`.

These functions try to parse the input template and check if the template
contains any Mustache syntax error. If any, they will report it as an exception.
Otherwise, they will return `nil` silently:

```clojure
(pg/check-string "Hello, {{name")
;; Execution error (ExceptionInfo) at pogonos.error/error (error.cljc:52).
;; Missing closing delimiter "}}" (1:14):
;;
;;   1| Hello, {{name
;;                   ^^

(pg/check-string "Hello, {{name}}!")
;=> nil
```

The verbosity of error messages can be controlled by an option.
See [Error messages](#error-messages) for details.

What the `check-*` functions do is semantically equivalent to "parsing a template
and discarding the parsed result". However, the `check-*` functions are generally
more efficient than `parse-*` in this regard because the former functions do not
actually build a syntax tree.

### Outputs

An output is the way to specify where to output the rendering result.
By default, Pogonos's render functions output the result as a string.
You can emulate this behavior by specifying `(pogonos.output/to-string)`
as the output like the following:

```clojure
(require '[pogonos.output :as output])

(pg/render-string "Hello, {{name}}!" {:name "Clojure"}
                  {:output (output/to-string)})
;=> "Hello, Clojure!"
```

You can also write out the rendering result to a file or to stdout via output:

```clojure
;; writes the rendering result to a file
(pg/render-string "Hello, {{name}}!" {:name "Clojure"}
                  {:output (output/to-file "hello.txt")})

;; writes the rendering result to the stdout
(pg/render-string "Hello, {{name}}!" {:name "Clojure"}
                  {:output (output/to-stdout)})
```

In general, it's more efficient to write out the rendering result
directly to a file than to generate a resulting string and then write it
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

To disable partials, specify `nil` for `:partials`:

```clojure
(pg/render-string "<h2>Users</h2>{{>partial}}" {} {:partials nil})
;=> "<h2>Users</h2>"
```

### Error messages

Pogonos aims to provide user-friendly error messages for parse errors
as one of its features, so that users can easily find where and why
the error occurred.

For example, if you miss the closing delimiter of a Mustache tag, you'll
see a detailed error message, like the following:

```clojure
(pg/render-string "Hello, {{name" {:name "Clojure"})

;; Execution error (ExceptionInfo) at pogonos.error/error (error.cljc:52).
;; Missing closing delimiter "}}" (1:14):
;;
;;   1| Hello, {{name
;;                   ^^
```

You can suppress these somewhat "verbose" error messages if you want,
by specifying the option `{:suppress-verbose-errors true}`:

```clojure
(pg/render-string "Hello, {{name" {:name "Clojure"}
                  {:suppress-verbose-errors true})

;; Execution error (ExceptionInfo) at pogonos.error/error (error.cljc:52).
;; Missing closing delimiter "}}" (1:14)
```

Even while disabling verbose error messages, you can get them back by calling
`perr` explicitly:

```clojure
(pg/perr *e)
;; Missing closing delimiter "}}" (1:14):
;;
;;   1| Hello, {{name
;;                   ^^
```

### CLI usage

Pogonos 0.2.0+ provides a new API for calling functions via the `-X`/`-T` option of the Clojure CLI.

To use it as a `-X` program, add settings like the following to your `deps.edn`:

```clojure
{:aliases
 {...
  template {:extra-deps {pogonos/pogonos {:mvn/version "<version>"}}
            :ns-default pogonos.api}
  ...}}
```

To use it as a `-T` tool, install Pogonos with the following command:

```sh
clojure -Ttools install io.github.athos/pogonos '{:git/tag <version>}' :as template
```

Then, you can call the API from the CLI like:

```sh
# as -X program
$ clojure -X:template <function name> ...

# as -T tool
$ clojure -Ttemplate <function name> ...
```

The functions available from the CLI are as follows:

- [`render`](#render)
- [`check`](#check)

You will see the usage of each function in the next sections.

#### `render`

The `render` function renders the specified Mustache template.

The example below renders a template file named `hello.mustache` with the data `{:name "Clojurian"}`:

```sh
$ cat hello.mustache
Hello, {{name}}!
$ clojure -X:template render :file '"hello.mustache"' :data '{:name "Clojurian"}'
Hello, Clojurian!
$
```

The `:file` option specifies the path to the template file to be rendered. The `:data` option specifies a map of values passed to the template.

If no template is specified, Pogonos will try to read the template from stdin:

```sh
$ echo 'Hello, {{name}}!' | clojure -X:template render :data '{:name "Clojurian"}'
Hello, Clojurian!
$
```

The following table shows the available options for `render`:

| Option | Description |
| :----- | :---------- |
| `:string` | Renders the given template string |
| `:file` | Renders the specified template file |
| `:resource` | Renders the specified template resource on the classpath |
| `:output` | The path to the output file. If not specified, the rendering result will be emitted to stdout by default. |
| `:data` | A map of values passed to the template |
| `:data-file` | If specified, reads an EDN map from the file specified by that path and pass it to the template |

#### `check`

The `check` function performs a syntax check on a given Mustache template, reporting any syntax errors that the Mustache template contains.

The example below checks a template file named `broken.mustache` that contains a syntax error:

```sh
$ cat broken.mustache
This is a broken {{template
$ clojure -X:template check :file '"broken.mustache"'
Checking template broken.mustache
[ERROR] Missing closing delimiter "}}" (broken.mustache:1:28):

  1| This is a broken {{template
                                ^^
$
```

If no template is specified, Pogonos will try to read the template to be checked from stdin:

```sh
$ echo '{{#foo}}' | clojure -X:template check
[ERROR] Missing section-end tag {{/foo}} (1:9):

  1| {{#foo}}
             ^^
$
```

The following table shows the available options for `check`:

| Option | Description |
| :----- | :---------- |
| `:string` | Checks the given template string |
| `:file` | Checks the specified template file |
| `:dir` | Checks the template files in the specified directory |
| `:resource` | Checks the specified template resource on the classpath |
| `:include-regex` | Includes only the templates that match the given pattern |
| `:exclude-regex` | Excludes the templates that match the given pattern |
| `:only-show-errors` | Hides progress messages |
| `:suppress-verbose-errors` | Suppresses verbose error messages |

Note that the `:file`, `:dir` and `:resource` options allow multiple items to be specified separated by the file path separator (`:` (colon) on Linux/macOS and `;` (semicolon) on Windows).

For example, the following command will check three template files named `foo.mustache`, `bar.mustache` and `baz.mustache` (Here, we assume that the file path separator is `:`):

```sh
$ clojure -X:template check :file '"foo.mustache:bar.mustache:baz.mustache"'
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
