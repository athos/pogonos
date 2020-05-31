(ns pogonos.stringify-test
  (:require [clojure.test :refer [deftest are testing]]
            [pogonos.nodes :as nodes]
            [pogonos.output :as output]
            [pogonos.stringify :as stringify]))

(deftest stringify-keys-test
  (are [input expected]
      (= expected
         (let [out (output/string-output)]
           (stringify/stringify-keys input out)
           (out)))
    [] "."
    [:x] "x"
    [:x :y :z] "x.y.z"))

(defn- stringify
  ([x] (stringify x nil nil))
  ([x open-delim close-delim]
   (stringify/stringify x open-delim close-delim)))

(deftest stringify-test
  (testing "text"
    (are [input] (= input (stringify input))
      ""
      "\n"
      "foo"
      "<>"))
  (testing "variables"
    (are [input expected] (= expected (stringify input))
      (nodes/->Variable [] false) "{{.}}"
      (nodes/->Variable [:x] false) "{{x}}"
      (nodes/->Variable [:x :y :z] false) "{{x.y.z}}"
      (nodes/->Variable [] true) "{{&.}}"
      (nodes/->Variable [:x] true) "{{&x}}"
      (nodes/->Variable [:x :y :z] true) "{{&x.y.z}}"
      (nodes/->UnescapedVariable []) "{{{.}}}"
      (nodes/->UnescapedVariable [:x]) "{{{x}}}"
      (nodes/->UnescapedVariable [:x :y :z]) "{{{x.y.z}}}")
    (are [input expected] (= expected (stringify input "<%" "%>"))
      (nodes/->Variable [] false) "<%.%>"
      (nodes/->Variable [:x] false) "<%x%>"
      (nodes/->Variable [:x :y :z] false) "<%x.y.z%>"
      (nodes/->Variable [] true) "<%&.%>"
      (nodes/->Variable [:x] true) "<%&x%>"
      (nodes/->Variable [:x :y :z] true) "<%&x.y.z%>"))
  (testing "partials"
    (are [input expected] (= expected (stringify input))
      (nodes/->Partial :x nil) "{{>x}}"
      (nodes/->Partial :x "") "{{>x}}"
      (nodes/->Partial :x " ") "{{>x}}"
      (with-meta
        (nodes/->Partial :x nil)
        {:post "  \n"})
      "{{>x}}  \n"

      (with-meta
        (nodes/->Partial :x "  ")
        {:post "  \n"})
      "{{>x}}  \n")
    (are [input expected] (= expected (stringify input "<%" "%>"))
      (nodes/->Partial :x nil) "<%>x%>"
      (nodes/->Partial :x "") "<%>x%>"
      (nodes/->Partial :x " ") "<%>x%>"
      (with-meta
        (nodes/->Partial :x nil)
        {:post "  \n"})
      "<%>x%>  \n"

      (with-meta
        (nodes/->Partial :x "  ")
        {:post "  \n"})
      "<%>x%>  \n"))
  (testing "comments"
    (are [input expected] (= expected (stringify input))
      (nodes/->Comment []) "{{!}}"
      (nodes/->Comment [" "]) "{{! }}"
      (nodes/->Comment ["foo"]) "{{!foo}}"
      (nodes/->Comment [" foo\n" "bar\n" "baz "]) "{{! foo\nbar\nbaz }}"
      (with-meta
        (nodes/->Comment [" foo "])
        {:pre "  " :post "  \n"})
      "  {{! foo }}  \n")
    (are [input expected] (= expected (stringify input "<%" "%>"))
      (nodes/->Comment []) "<%!%>"
      (nodes/->Comment [" "]) "<%! %>"
      (nodes/->Comment ["foo"]) "<%!foo%>"
      (nodes/->Comment [" foo\n" "bar\n" "baz "]) "<%! foo\nbar\nbaz %>"
      (with-meta
        (nodes/->Comment [" foo "])
        {:pre "  " :post "  \n"})
      "  <%! foo %>  \n"))
  (testing "set delimiters"
    (are [input expected] (= expected (stringify input))
      (nodes/->SetDelimiter "<%" "%>") "{{=<% %>=}}"
      (with-meta
        (nodes/->SetDelimiter "<%" "%>")
        {:pre "  " :post "  \n"})
      "  {{=<% %>=}}  \n")
    (are [input expected] (= expected (stringify input "<%" "%>"))
      (nodes/->SetDelimiter "{{" "}}") "<%={{ }}=%>"
      (with-meta
        (nodes/->SetDelimiter "{{" "}}")
        {:pre "  " :post "  \n"})
      "  <%={{ }}=%>  \n"))
  (testing "section"
    (are [input expected] (= expected (stringify input))
      (nodes/->Section [] [(nodes/->SectionEnd [])])
      "{{#.}}{{/.}}"

      (nodes/->Section [:x] [(nodes/->SectionEnd [:x])])
      "{{#x}}{{/x}}"

      (nodes/->Section [:x :y :z] [(nodes/->SectionEnd [:x :y :z])])
      "{{#x.y.z}}{{/x.y.z}}"

      (nodes/->Inverted [] [(nodes/->SectionEnd [])])
      "{{^.}}{{/.}}"

      (nodes/->Inverted [:x] [(nodes/->SectionEnd [:x])])
      "{{^x}}{{/x}}"

      (nodes/->Inverted [:x :y :z] [(nodes/->SectionEnd [:x :y :z])])
      "{{^x.y.z}}{{/x.y.z}}"

      (nodes/->Section [:x] ["foo" (nodes/->SectionEnd [:x])])
      "{{#x}}foo{{/x}}"

      (nodes/->Section [:x] ["  foo  " (nodes/->SectionEnd [:x])])
      "{{#x}}  foo  {{/x}}"

      (nodes/->Section [:x] ["foo\nbar" (nodes/->SectionEnd [:x])])
      "{{#x}}foo\nbar{{/x}}"

      (nodes/->Section
       [:x]
       [(nodes/->Variable [:y] false) (nodes/->SectionEnd [:x])])
      "{{#x}}{{y}}{{/x}}"

      (nodes/->Section
       [:x]
       ["  " (nodes/->Variable [:y] false) "  " (nodes/->SectionEnd [:x])])
      "{{#x}}  {{y}}  {{/x}}"

      (nodes/->Section
       [:x]
       [(nodes/->Variable [:y] false) "\n" (nodes/->Variable [:z] false)
        (nodes/->SectionEnd [:x])])
      "{{#x}}{{y}}\n{{z}}{{/x}}"

      (nodes/->Section
       [:x]
       [(nodes/->Section
         [:y]
         [(nodes/->Variable [:z] false) (nodes/->SectionEnd [:y])])
        (nodes/->SectionEnd [:x])])
      "{{#x}}{{#y}}{{z}}{{/y}}{{/x}}"

      (with-meta
        (nodes/->Section
         [:x]
         [(with-meta
            (nodes/->Section
             [:y]
             [(nodes/->Variable [:z] false)
              "\n"
              (with-meta
                (nodes/->SectionEnd [:y])
                {:pre "   " :post "    \n"})])
            {:pre " " :post "  \n"})
          (nodes/->SectionEnd [:x])])
        {:pre nil :post "\n"})
      "{{#x}}\n {{#y}}  \n{{z}}\n   {{/y}}    \n{{/x}}"

      (nodes/->Section
       [:x]
       [(nodes/->Inverted
         [:y]
         [(nodes/->Variable [:z] false) (nodes/->SectionEnd [:y])])
        (nodes/->SectionEnd [:x])])
      "{{#x}}{{^y}}{{z}}{{/y}}{{/x}}"

      (with-meta
        (nodes/->Section
         [:x]
         [(with-meta
            (nodes/->Inverted
             [:y]
             [(nodes/->Variable [:z] false)
              "\n"
              (with-meta
                (nodes/->SectionEnd [:y])
                {:pre "   " :post "    \n"})])
            {:pre " " :post "  \n"})
          (nodes/->SectionEnd [:x])])
        {:pre nil :post "\n"})
      "{{#x}}\n {{^y}}  \n{{z}}\n   {{/y}}    \n{{/x}}"

      (nodes/->Section
       [:x]
       [(nodes/->Partial :y nil)
        (nodes/->SectionEnd [:x])])
      "{{#x}}{{>y}}{{/x}}"

      (nodes/->Section
       [:x]
       ["  "
        (nodes/->Partial :y nil)
        "  "
        (nodes/->SectionEnd [:x])])
      "{{#x}}  {{>y}}  {{/x}}"

      (with-meta
        (nodes/->Section
         [:x]
         ["  "
          (with-meta
            (nodes/->Partial :y "  ")
            {:post "  \n"})
          (nodes/->SectionEnd [:x])])
        {:pre nil :post "\n"})
      "{{#x}}\n  {{>y}}  \n{{/x}}"

      (nodes/->Section
       [:x]
       [(nodes/->Comment ["foo"])
        (nodes/->SectionEnd [:x])])
      "{{#x}}{{!foo}}{{/x}}"

      (nodes/->Section
       [:x]
       ["  "
        (nodes/->Comment ["foo"])
        "  "
        (nodes/->SectionEnd [:x])])
      "{{#x}}  {{!foo}}  {{/x}}"

      (with-meta
        (nodes/->Section
         [:x]
         [(with-meta
            (nodes/->Comment ["foo\n" "bar\n" "baz"])
            {:pre "  " :post "  \n"})
          (nodes/->SectionEnd [:x])])
        {:pre nil :post "\n"})
      "{{#x}}\n  {{!foo\nbar\nbaz}}  \n{{/x}}"

      (nodes/->Section
       [:x]
       [(nodes/->SetDelimiter "<%" "%>")
        (nodes/->SectionEnd [:x])])
      "{{#x}}{{=<% %>=}}<%/x%>"

      (nodes/->Section
       [:x]
       ["  "
        (nodes/->SetDelimiter "<%" "%>")
        "  "
        (nodes/->SectionEnd [:x])])
      "{{#x}}  {{=<% %>=}}  <%/x%>"

      (with-meta
        (nodes/->Section
         [:x]
         [(with-meta
            (nodes/->SetDelimiter "<%" "%>")
            {:pre "  " :post "  \n"})
          (nodes/->SectionEnd [:x])])
        {:pre nil :post "\n"})
      "{{#x}}\n  {{=<% %>=}}  \n<%/x%>")
    (are [input expected] (= expected (stringify input "<%" "%>"))
      (nodes/->Section [] [(nodes/->SectionEnd [])])
      "<%#.%><%/.%>"

      (nodes/->Section [:x] [(nodes/->SectionEnd [:x])])
      "<%#x%><%/x%>"

      (nodes/->Section [:x :y :z] [(nodes/->SectionEnd [:x :y :z])])
      "<%#x.y.z%><%/x.y.z%>"

      (nodes/->Inverted [] [(nodes/->SectionEnd [])])
      "<%^.%><%/.%>"

      (nodes/->Inverted [:x] [(nodes/->SectionEnd [:x])])
      "<%^x%><%/x%>"

      (nodes/->Inverted [:x :y :z] [(nodes/->SectionEnd [:x :y :z])])
      "<%^x.y.z%><%/x.y.z%>"

      (nodes/->Section [:x] ["foo" (nodes/->SectionEnd [:x])])
      "<%#x%>foo<%/x%>"

      (nodes/->Section [:x] ["  foo  " (nodes/->SectionEnd [:x])])
      "<%#x%>  foo  <%/x%>"

      (nodes/->Section [:x] ["foo\nbar" (nodes/->SectionEnd [:x])])
      "<%#x%>foo\nbar<%/x%>"

      (nodes/->Section
       [:x]
       [(nodes/->Variable [:y] false) (nodes/->SectionEnd [:x])])
      "<%#x%><%y%><%/x%>"

      (nodes/->Section
       [:x]
       ["  " (nodes/->Variable [:y] false) "  " (nodes/->SectionEnd [:x])])
      "<%#x%>  <%y%>  <%/x%>"

      (nodes/->Section
       [:x]
       [(nodes/->Variable [:y] false) "\n" (nodes/->Variable [:z] false)
        (nodes/->SectionEnd [:x])])
      "<%#x%><%y%>\n<%z%><%/x%>"

      (nodes/->Section
       [:x]
       [(nodes/->Section
         [:y]
         [(nodes/->Variable [:z] false) (nodes/->SectionEnd [:y])])
        (nodes/->SectionEnd [:x])])
      "<%#x%><%#y%><%z%><%/y%><%/x%>"

      (with-meta
        (nodes/->Section
         [:x]
         [(with-meta
            (nodes/->Section
             [:y]
             [(nodes/->Variable [:z] false)
              "\n"
              (with-meta
                (nodes/->SectionEnd [:y])
                {:pre "   " :post "    \n"})])
            {:pre " " :post "  \n"})
          (nodes/->SectionEnd [:x])])
        {:pre nil :post "\n"})
      "<%#x%>\n <%#y%>  \n<%z%>\n   <%/y%>    \n<%/x%>"

      (nodes/->Section
       [:x]
       [(nodes/->Inverted
         [:y]
         [(nodes/->Variable [:z] false) (nodes/->SectionEnd [:y])])
        (nodes/->SectionEnd [:x])])
      "<%#x%><%^y%><%z%><%/y%><%/x%>"

      (with-meta
        (nodes/->Section
         [:x]
         [(with-meta
            (nodes/->Inverted
             [:y]
             [(nodes/->Variable [:z] false)
              "\n"
              (with-meta
                (nodes/->SectionEnd [:y])
                {:pre "   " :post "    \n"})])
            {:pre " " :post "  \n"})
          (nodes/->SectionEnd [:x])])
        {:pre nil :post "\n"})
      "<%#x%>\n <%^y%>  \n<%z%>\n   <%/y%>    \n<%/x%>"

      (nodes/->Section
       [:x]
       [(nodes/->Partial :y nil)
        (nodes/->SectionEnd [:x])])
      "<%#x%><%>y%><%/x%>"

      (nodes/->Section
       [:x]
       ["  "
        (nodes/->Partial :y nil)
        "  "
        (nodes/->SectionEnd [:x])])
      "<%#x%>  <%>y%>  <%/x%>"

      (with-meta
        (nodes/->Section
         [:x]
         ["  "
          (with-meta
            (nodes/->Partial :y "  ")
            {:post "  \n"})
          (nodes/->SectionEnd [:x])])
        {:pre nil :post "\n"})
      "<%#x%>\n  <%>y%>  \n<%/x%>"

      (nodes/->Section
       [:x]
       [(nodes/->Comment ["foo"])
        (nodes/->SectionEnd [:x])])
      "<%#x%><%!foo%><%/x%>"

      (nodes/->Section
       [:x]
       ["  "
        (nodes/->Comment ["foo"])
        "  "
        (nodes/->SectionEnd [:x])])
      "<%#x%>  <%!foo%>  <%/x%>"

      (with-meta
        (nodes/->Section
         [:x]
         [(with-meta
            (nodes/->Comment ["foo\n" "bar\n" "baz"])
            {:pre "  " :post "  \n"})
          (nodes/->SectionEnd [:x])])
        {:pre nil :post "\n"})
      "<%#x%>\n  <%!foo\nbar\nbaz%>  \n<%/x%>"

      (nodes/->Section
       [:x]
       [(nodes/->SetDelimiter "{{" "}}")
        (nodes/->SectionEnd [:x])])
      "<%#x%><%={{ }}=%>{{/x}}"

      (nodes/->Section
       [:x]
       ["  "
        (nodes/->SetDelimiter "{{" "}}")
        "  "
        (nodes/->SectionEnd [:x])])
      "<%#x%>  <%={{ }}=%>  {{/x}}"

      (with-meta
        (nodes/->Section
         [:x]
         [(with-meta
            (nodes/->SetDelimiter "{{" "}}")
            {:pre "  " :post "  \n"})
          (nodes/->SectionEnd [:x])])
        {:pre nil :post "\n"})
      "<%#x%>\n  <%={{ }}=%>  \n{{/x}}")))
