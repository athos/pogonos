(defproject pogonos "0.1.0-SNAPSHOT"
  :description "another Clojure implementation of Mustache template engine"
  :url "https://github.com/athos/pogonos"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :profiles {:provided
             {:dependencies [[org.clojure/clojure "1.10.1"]
                             [org.clojure/clojurescript "1.10.597"]]}
             :test
             {:dependencies [[org.clojure/data.json "0.2.7"]]
              :resource-paths ["test-resources"]}}
  :repl-options {:init-ns pogonos.core})
