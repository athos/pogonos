(ns pogonos.render-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest are testing]]
            [pogonos.context :as context]
            [pogonos.render :as render]
            [pogonos.nodes :as nodes]
            [pogonos.output :as output]))

(deftest lookup-test
  (are [ctx keys expected]
       (= expected (render/lookup (context/->NonCheckingContext ctx) keys))
    '({:x 42})
    '(:x)
    42

    '({:x {:y 42}})
    '(:x :y)
    42

    '({:y 42} {:x {:y 42} :y 43})
    '(:y)
    42

    '({:x 42} {:x {:y 42} :y 43})
    '(:y)
    43

    '({:x 42} {:x {:y 42} :y 43})
    '(:z)
    nil

    '(42)
    '()
    42))

(defn- render
  ([template data]
   (render template data nil))
  ([template data partials]
   (let [out ((output/to-string))]
     (render/render (context/make-context data)
                    out
                    (nodes/->Root template)
                    {:partials partials})
     (out))))

(deftest render-test
  (testing "variables"
    (are [template ctx expected] (= expected (render template ctx))
      ["Hello, " (nodes/->Variable '(:name) false) "!"]
      {:name "Rich"}
      "Hello, Rich!"

      ["I'm " (nodes/->Variable '(:age) false) " years old."]
      {:age 42}
      "I'm 42 years old."

      ["Hello, " (nodes/->Variable '(:user :name) false) "!"]
      {:user {:name "Alex"}}
      "Hello, Alex!"

      ["The value is " (nodes/->Variable () false) "."]
      42
      "The value is 42."

      ["<<<" (nodes/->Variable '(:content) false) ">>>"]
      {:content "<&>"}
      "<<<&lt;&amp;&gt;>>>"

      ["<<<" (nodes/->Variable '(:content) true) ">>>"]
      {:content "<&>"}
      "<<<<&>>>>"

      ["<<<" (nodes/->UnescapedVariable '(:content)) ">>>"]
      {:content "<&>"}
      "<<<<&>>>>"))
  (testing "sections"
    (are [template data expected] (= expected (render template data))
      ["abc "
       (nodes/->Section
        '(:foo)
        ["foo " (nodes/->SectionEnd '(:foo))])
       "xyz"]
      {:foo true}
      "abc foo xyz"

      ["abc "
       (nodes/->Section
        '(:foo)
        ["foo " (nodes/->SectionEnd '(:foo))])
       "xyz"]
      {:foo false}
      "abc xyz"

      [(nodes/->Section
        '(:user)
        ["name: "
         (nodes/->Variable '(:name) false)
         (nodes/->SectionEnd '(:user))])]
      {:user {:name "david"}}
      "name: david"

      [(nodes/->Section
        '(:members)
        ["name: " (nodes/->Variable '(:name) false)
         "\n" (nodes/->SectionEnd '(:members))])]
      {:members [{:name "rich"} {:name "stu"} {:name "alex"}]}
      "name: rich\nname: stu\nname: alex\n"

      [(nodes/->Section
        '(:foo)
        ["Hello, "
         (nodes/->Variable '(:name) false)
         "!"
         (nodes/->SectionEnd '(:foo))])]
      {:foo #(str/replace % #"Hello" "Howdy")
       :name "World"}
      "Howdy, World!"

      [(nodes/->Section
        '(:x)
        [(nodes/->Section
          '(:y)
          ["z: "(nodes/->Variable '(:z) false) "\n"
           (nodes/->SectionEnd '(:y))])
         "z: " (nodes/->Variable '(:z) false) "\n"
         (nodes/->SectionEnd '(:y))])
       "z: " (nodes/->Variable '(:z) false)]
      {:x {:y {:z 1} :z 2} :z 3}
      "z: 1\nz: 2\nz: 3"

      ["abc\n"
       (nodes/->Inverted
        '(:foo)
        ["lmn\n" (nodes/->SectionEnd '(:foo))])
       "xyz"]
      {:foo "hi"}
      "abc\nxyz"

      ["abc\n"
       (nodes/->Inverted
        '(:foo)
        ["lmn\n" (nodes/->SectionEnd '(:foo))])
       "xyz"]
      {:foo false}
      "abc\nlmn\nxyz"))
  (testing "partials"
    (are [template ctx partials expected]
        (= expected (render template ctx partials))
      ["[" (nodes/->Partial :p nil) "]"]
      {:x 42}
      {:p "{{x}}"}
      "[42]"

      ["\\\n " (nodes/->Partial :p " ") "\n/"]
      {}
      {:p "|\n  {{>q}}\n\n|"
       :q "]"}
      "\\\n |\n   ]\n |\n/"

      [(nodes/->Partial :p nil)]
      {:name "foo"
       :children
       [{:name "bar1"
         :children [{:name "baz" :children []}]}
        {:name "bar2"
         :children []}]}
      {:p "- name: {{name}}\n  children:{{#children}}\n  {{>p}}\n{{/children}}"}
      "- name: foo
  children:
  - name: bar1
    children:
    - name: baz
      children:
  - name: bar2
    children:"))
  (testing "dynamic partials"
    (are [template ctx partials expected]
        (= expected (render template ctx partials))
      ["[" (nodes/->DynamicPartial [:partial] nil) "]"]
      {:partial "foo" :x 42}
      {:foo "{{x}}"}
      "[42]"

      ["\\\n " (nodes/->DynamicPartial [:partial] " ") "\n/"]
      {:partial :p}
      {:p "|\n  {{>q}}\n\n|"
       :q "]"}
      "\\\n |\n   ]\n |\n/"

      [(nodes/->DynamicPartial [:partial] nil)]
      {:partial 'p
       :name "foo"
       :children
       [{:name "bar1"
         :children [{:name "baz" :children []}]}
        {:name "bar2"
         :children []}]}
      {:p "- name: {{name}}\n  children:{{#children}}\n  {{>*partial}}\n{{/children}}"}
      "- name: foo
  children:
  - name: bar1
    children:
    - name: baz
      children:
  - name: bar2
    children:")))
