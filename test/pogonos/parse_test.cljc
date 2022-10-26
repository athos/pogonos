(ns pogonos.parse-test
  (:require [clojure.test :refer [deftest is are testing]]
            [pogonos.error :as error]
            [pogonos.nodes :as nodes]
            [pogonos.parse :as parse]
            [pogonos.reader :as reader]))

(deftest node-buffer-test
  (let [buf (parse/make-node-buffer)]
    (buf "foo\n")
    (buf "bar\n")
    (buf (nodes/->Variable [:x] false))
    (buf "baz")
    (buf (nodes/->Variable [:y] false))
    (buf "qux\n")
    (buf "quux")
    (is (= ["foo\nbar\n"
            (nodes/->Variable [:x] false)
            "baz"
            (nodes/->Variable [:y] false)
            "qux\nquux"]
           (buf)))))

(defn- parse
  ([s] (parse s {}))
  ([s opts]
   (let [out (parse/make-node-buffer)]
     (parse/parse (reader/make-string-reader s) out opts)
     (out))))

(defn- meta-for-whitespaces [x]
  (not-empty (select-keys (meta x) [:pre :post])))

(deftest parse-test
  (testing "text"
    (are [input expected] (= expected (parse input))
      "" []
      "foo" ["foo"]
      "\n" ["\n"]
      "foo\nbar" ["foo\nbar"]))
  (testing "variables"
    (are [input expected] (= expected (parse input))
      "{{.}}" [(nodes/->Variable [] false)]
      "{{x}}" [(nodes/->Variable [:x] false)]
      "{{x.y.z}}" [(nodes/->Variable [:x :y :z] false)]
      "{{&.}}" [(nodes/->Variable [] true)]
      "{{&x}}" [(nodes/->Variable [:x] true)]
      "{{&x.y.z}}" [(nodes/->Variable [:x :y :z] true)]
      "{{{.}}}" [(nodes/->UnescapedVariable [])]
      "{{{x}}}" [(nodes/->UnescapedVariable [:x])]
      "{{{x.y.z}}}" [(nodes/->UnescapedVariable [:x :y :z])]
      "  {{x}}  " ["  " (nodes/->Variable [:x] false) "  "]
      "  {{x}}\n" ["  " (nodes/->Variable [:x] false) "\n"]
      "foo {{x}} bar" ["foo " (nodes/->Variable [:x] false) " bar"]
      "  {{{x}}}  " ["  " (nodes/->UnescapedVariable [:x]) "  "]
      "  {{{x}}}\n" ["  " (nodes/->UnescapedVariable [:x]) "\n"]
      "foo {{{x}}} bar" ["foo " (nodes/->UnescapedVariable [:x]) " bar"])
    (are [input error-type] (= error-type
                               (try
                                 (parse input)
                                 nil
                                 (catch #?(:clj Exception :cljs :default) e
                                   (::error/type (ex-data e)))))
      "{{" :incomplete-tag
      "{{foo" :missing-closing-delimiter
      "{{foo{{}}" :missing-closing-delimiter
      "{{}}" :invalid-variable-name
      "{{{}}" :missing-closing-delimiter))
  (testing "sections"
    (are [input expected] (= expected (parse input))
      "abc {{#foo}} lmn {{/foo}} xyz"
      ["abc "
       (nodes/->Section
        [:foo]
        [" lmn " (nodes/->SectionEnd [:foo])])
       " xyz"]

      "abc {{^foo}} lmn {{/foo}} xyz"
      ["abc "
       (nodes/->Inverted
        [:foo]
        [" lmn " (nodes/->SectionEnd [:foo])])
       " xyz"]

      "abc {{#foo}}{{bar}}\n{{baz}}{{/foo}} xyz"
      ["abc "
       (nodes/->Section
        [:foo]
        [(nodes/->Variable [:bar] false) "\n"
         (nodes/->Variable [:baz] false)
         (nodes/->SectionEnd [:foo])])
       " xyz"]

      "abc {{^foo}}{{bar}}\n{{baz}}{{/foo}} xyz"
      ["abc "
       (nodes/->Inverted
        [:foo]
        [(nodes/->Variable [:bar] false) "\n"
         (nodes/->Variable [:baz] false)
         (nodes/->SectionEnd [:foo])])
       " xyz"]

      "abc{{#foo.bar}}lmn{{/foo.bar}}xyz"
      ["abc"
       (nodes/->Section
        [:foo :bar]
        ["lmn" (nodes/->SectionEnd [:foo :bar])])
       "xyz"]

      "abc{{^foo.bar}}lmn{{/foo.bar}}xyz"
      ["abc"
       (nodes/->Inverted
        [:foo :bar]
        ["lmn" (nodes/->SectionEnd [:foo :bar])])
       "xyz"]

      "abc{{#.}}lmn{{/.}}xyz"
      ["abc"
       (nodes/->Section
        []
        ["lmn" (nodes/->SectionEnd [])])
       "xyz"]

      "abc{{^.}}lmn{{/.}}xyz"
      ["abc"
       (nodes/->Inverted
        []
        ["lmn" (nodes/->SectionEnd [])])
       "xyz"]

      "abc{{#foo}}{{#bar}}lmn{{/bar}}{{/foo}}xyz"
      ["abc"
       (nodes/->Section
        [:foo]
        [(nodes/->Section
          [:bar]
          ["lmn" (nodes/->SectionEnd [:bar])])
         (nodes/->SectionEnd [:foo])])
       "xyz"]

      "abc{{^foo}}{{^bar}}lmn{{/bar}}{{/foo}}xyz"
      ["abc"
       (nodes/->Inverted
        [:foo]
        [(nodes/->Inverted
          [:bar]
          ["lmn" (nodes/->SectionEnd [:bar])])
         (nodes/->SectionEnd [:foo])])
       "xyz"])

    (let [[_ section :as result] (parse "abc \n  {{#foo}} \n lmn \n {{/foo}}  \n xyz")]
      (is (= ["abc \n"
              (nodes/->Section
               [:foo]
               [" lmn \n" (nodes/->SectionEnd [:foo])])
              " xyz"]
             result))
      (is (= {:pre "  " :post " \n"} (meta-for-whitespaces section)))
      (is (= {:pre " " :post "  \n"}
             (meta-for-whitespaces (second (:nodes section))))))
    (let [[_ section :as result] (parse "abc \n  {{^foo}} \n lmn \n {{/foo}}  \n xyz")]
      (is (= ["abc \n"
              (nodes/->Inverted
               [:foo]
               [" lmn \n" (nodes/->SectionEnd [:foo])])
              " xyz"]
             result))
      (is (= {:pre "  " :post " \n"} (meta-for-whitespaces section)))
      (is (= {:pre " " :post "  \n"}
             (meta-for-whitespaces (second (:nodes section))))))
    (are [input error-type] (= error-type
                               (try
                                 (parse input)
                                 nil
                                 (catch #?(:clj Exception :cljs :default) e
                                   (::error/type (ex-data e)))))
      "{{#foo" :missing-closing-delimiter
      "{{#foo}" :missing-closing-delimiter
      "{{#foo}}" :missing-section-end
      "{{#foo}}{{/foo}" :missing-closing-delimiter
      "{{#foo}}{{/bar}}" :mismatched-section-end))
  (testing "comments"
    (are [input expected] (= expected (parse input))
      "{{!}}" [(nodes/->Comment [""])]
      "{{!foo}}" [(nodes/->Comment ["foo"])]
      "{{!  foo  }}" [(nodes/->Comment ["  foo  "])]
      "{{! foo\nbar\nbaz }}" [(nodes/->Comment [" foo\n" "bar\n" "baz "])])
    (let [[comment :as result] (parse "abc {{! foo }} xyz")]
      (is (= ["abc " (nodes/->Comment [" foo "]) " xyz"] result))
      (is (nil? (meta-for-whitespaces comment))))
    (let [[comment :as result] (parse "abc {{! foo\nbar\nbaz }} xyz")]
      (is (= ["abc " (nodes/->Comment [" foo\n" "bar\n" "baz "]) " xyz"] result))
      (is (nil? (meta-for-whitespaces comment))))
    (let [[comment :as result] (parse "  {{! foo }}   ")]
      (is (= [(nodes/->Comment [" foo "])] result))
      (is (= {:pre "  " :post "   "} (meta-for-whitespaces comment))))
    (let [[comment :as result] (parse "  {{! foo\nbar\nbaz }}  \n")]
      (is (= [(nodes/->Comment [" foo\n" "bar\n" "baz "])] result))
      (is (= {:pre "  " :post "  \n"} (meta-for-whitespaces comment))))
    (let [[_ comment :as result] (parse "abc \n  {{! foo\nbar\nbaz }}  \nxyz\n")]
      (is (= ["abc \n" (nodes/->Comment [" foo\n" "bar\n" "baz "]) "xyz\n"] result))
      (is (= {:pre "  " :post "  \n"} (meta-for-whitespaces comment))))
    (are [input error-type] (= error-type
                               (try
                                 (parse input)
                                 nil
                                 (catch #?(:clj Exception :cljs :default) e
                                   (::error/type (ex-data e)))))
      "{{!" :missing-closing-delimiter
      "{{!\n" :missing-closing-delimiter
      "{{! comment" :missing-closing-delimiter
      "{{! comment\n" :missing-closing-delimiter))
  (testing "partials"
    (are [input expected] (= expected (parse input))
      "{{>foo}}" [(nodes/->Partial :foo nil)]
      "{{> foo }}" [(nodes/->Partial :foo nil)])
    (let [[_ partial :as result] (parse "abc  {{>foo}}  xyz")]
      (is (= ["abc  " (nodes/->Partial :foo nil) "  xyz"] result))
      (is (nil? (meta-for-whitespaces partial))))
    (let [[_ partial :as result] (parse "   {{>foo}}  ")]
      (is (= ["   " (nodes/->Partial :foo "   ")] result))
      (is (= {:post "  "} (meta-for-whitespaces partial))))
    (let [[_ partial :as result] (parse "   {{>foo}}  " {:indent "    "})]
      (is (= ["   " (nodes/->Partial :foo "       ")] result))
      (is (= {:post "  "} (meta-for-whitespaces partial))))
    (let [[_ partial :as result] (parse "abc  {{>foo}}  xyz" {:indent "    "})]
      (is (= ["abc  " (nodes/->Partial :foo nil) "  xyz"] result))
      (is (nil? (meta-for-whitespaces partial))))
    (are [input error-type] (= error-type
                               (try
                                 (parse input)
                                 nil
                                 (catch #?(:clj Exception :cljs :default) e
                                   (::error/type (ex-data e)))))
      "{{>partial" :missing-closing-delimiter
      "{{>}}" :invalid-partial-name))
  (testing "dynamic partials"
    (are [input expected] (= expected (parse input))
      "{{>*foo}}" [(nodes/->DynamicPartial [:foo] nil)]
      "{{>* foo }}" [(nodes/->DynamicPartial [:foo] nil)]
      "{{> * foo }}" [(nodes/->DynamicPartial [:foo] nil)])
    (let [[_ partial :as result] (parse "abc  {{>*foo}}  xyz")]
      (is (= ["abc  " (nodes/->DynamicPartial [:foo] nil) "  xyz"] result))
      (is (nil? (meta-for-whitespaces partial))))
    (let [[_ partial :as result] (parse "   {{>*foo}}  ")]
      (is (= ["   " (nodes/->DynamicPartial [:foo] "   ")] result))
      (is (= {:post "  "} (meta-for-whitespaces partial))))
    (let [[_ partial :as result] (parse "   {{>*foo}}  " {:indent "    "})]
      (is (= ["   " (nodes/->DynamicPartial [:foo] "       ")] result))
      (is (= {:post "  "} (meta-for-whitespaces partial))))
    (let [[_ partial :as result] (parse "abc  {{>*foo}}  xyz" {:indent "    "})]
      (is (= ["abc  " (nodes/->DynamicPartial [:foo] nil) "  xyz"] result))
      (is (nil? (meta-for-whitespaces partial))))
    (are [input error-type] (= error-type
                               (try
                                 (parse input)
                                 nil
                                 (catch #?(:clj Exception :cljs :default) e
                                   (::error/type (ex-data e)))))
      "{{>*partial" :missing-closing-delimiter
      "{{>*}}" :invalid-variable-name))
  (testing "set delimiters"
    (let [[_ node :as result] (parse "abc {{=<% %>=}} <%foo%> xyz")]
      (is (= ["abc " (nodes/->SetDelimiter "<%" "%>")
              " " (nodes/->Variable [:foo] false) " xyz"]
             result))
      (is (nil? (meta-for-whitespaces node))))
    (let [[_ node :as result] (parse "abc \n  {{= <% %> =}}  \n <% foo %> xyz")]
      (is (= ["abc \n" (nodes/->SetDelimiter "<%" "%>")
              " " (nodes/->Variable [:foo] false) " xyz"]
             result))
      (is (= {:pre "  " :post "  \n"} (meta-for-whitespaces node))))
    (are [input expected] (= expected (parse input))
      "{{=<% %>=}}<%=<< >>=%><<foo>>"
      [(nodes/->SetDelimiter "<%" "%>")
       (nodes/->SetDelimiter "<<" ">>")
       (nodes/->Variable [:foo] false)]

      "{{#foo}}{{=<< >>=}}<</foo>><<bar>>"
      [(nodes/->Section
        [:foo]
        [(nodes/->SetDelimiter "<<" ">>")
         (nodes/->SectionEnd [:foo])])
       (nodes/->Variable [:bar] false)])
    (are [input error-type] (= error-type
                               (try
                                 (parse input)
                                 nil
                                 (catch #?(:clj Exception :cljs :default) e
                                   (::error/type (ex-data e)))))
      "{{=" :missing-closing-delimiter
      "{{=}}" :missing-closing-delimiter
      "{{==}}" :invalid-set-delimiters
      "{{= <% =}}" :invalid-set-delimiters
      "{{= <% %> }}" :missing-closing-delimiter)))
