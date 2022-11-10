(ns pogonos.main
  ;;; This namespace is only available for clj/bb, and so all the following
  ;;; reader conditionals are effectively unnecessary and are only used
  ;;; to suppress linter warnings.
  #?(:clj
     (:require [babashka.cli :as cli]
               pogonos.api)))

#?(:clj
   (do
     (defn- print-usage []
       (println
        (str #?@(:bb
                 ["Usage: pgns <command> <options>\n"
                  "       bb -m pogonos.main <command> <options>\n"]
                 :clj
                 ["Usage: clojure -M -m pogonos.main <command> <options>\n"])
             "\n"
             "Commands:\n"
             "  render  Renders the given Mustache template\n"
             "  check   Checks if the given Mustache template contains any syntax error\n"
             "  help    Prints this help message\n"
             "\n"
             #?(:bb
                "Run `pgns help <command>` or `bb -m pogonos.main help <command>` to see more details for each command."
                :clj
                "Run `clojure -M -m pogonos.main help <command>` to see more details for each command."))))

     (defn- resolve-command [cmd]
       (when-let [v (some->> cmd
                             symbol
                             (ns-resolve 'pogonos.api))]
         {:cmd v, :opts (:org.babashka/cli (meta v))}))

     (defn- help [args]
       (if (empty? args)
         (print-usage)
         (let [cmd (first args)]
           (if-let [{cli-opts :opts} (resolve-command cmd)]
             (println
              (str #?@(:bb
                       ["Usage: pgns" cmd " <options>\n"
                        "       bb -m pogonos.main " cmd " <options>\n"]
                       :clj
                       ["Usage: clojure -M -m pogonos.main " cmd " <options>\n"])
                   "\n"
                   "Options:\n"
                   (cli/format-opts cli-opts)))
             (do (print-usage)
                 (System/exit 1))))))

     (defn -main [& [cmd & args]]
       (if (= cmd "help")
         (help args)
         (if-let [{f :cmd cli-opts :opts} (resolve-command cmd)]
           (let [opts (cli/parse-opts args cli-opts)
                 cli-exec-args (:exec-args opts)
                 opts (cli/merge-opts cli-exec-args opts)]
             (f opts))
           (do (print-usage)
               (System/exit 1)))))
     ))
