(defproject pogonos "0.2.0-SNAPSHOT"
  :description "Another Clojure(Script) implementation of the Mustache templating language"
  :url "https://github.com/athos/pogonos"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :profiles {:provided
             {:dependencies [[org.clojure/clojure "1.11.1"]
                             [org.clojure/clojurescript "1.11.4"]]}
             :dev
             ;; these are necessary only for tests, so ideally they should go
             ;; under :test profile, but our IDE requires them to be here
             ;; to load test code fine
             {:dependencies [[org.clojure/data.json "2.4.0"]]
              :resource-paths ["test-resources"]}}
  :repl-options {:init-ns pogonos.core})
