(ns lean-clj.core-test
  (:require
    [lean-clj.core :refer :all]
    [clojure.test :refer [deftest testing is]]))

(deftest test-strip-comments
  (testing "strip comments"
    (is (= "abc  def" (strip-comments "abc $( comment $) def")))
    (is (= "abc  def  hij" (strip-comments "abc $( first comment $) def $( second comment $) hij")))
    (is (= "abc  def" (strip-comments "abc $( multiline \ncomment $) def")))
    (is (thrown? Exception (strip-comments "abc $( unfinished comment")))
    (is (thrown? Exception (strip-comments "$) abc $( finished comment $)")))))

(deftest test-load-includes
  (with-redefs [slurp (fn [filename]
                        (case filename
                          "abc.mm" "ABC"
                          "xyz.mm" "XYZ"
                          "xyz-comment.mm" "XYZ $( comment $) ZYX"
                          "xyz-include.mm" "XYZ $[ abc.mm $] ZYX"
                          "xyz-include2.mm" "XYZ $[ abc.mm $] $[ root.mm $] ZYX"
                          "root.mm" "this is meta"))]
    (testing "load includes"
      (is (= "abc XYZ def"          (first (load-includes "abc $[ xyz.mm $] def" ["root.mm"]))))
      (is (= "abc XYZ  ZYX def"     (first (load-includes "abc $[ xyz-comment.mm $] def" ["root.mm"])))))
    (testing "nested inclusion"
      (is (= "abc XYZ ABC ZYX def"  (first (load-includes "abc $[ xyz-include.mm $] def" ["root.mm"]))))
      (is (= "abc XYZ ABC  ZYX def" (first (load-includes "abc $[ xyz-include2.mm $] def" ["root.mm"])))))
    (testing "no multiple inclusion"
      (is (= "abc  def"             (first (load-includes "abc $[ root.mm $] def" ["root.mm"]))))
      (is (= "abc XYZ ABC ZYX def " (first (load-includes "abc $[ xyz-include.mm $] def $[ abc.mm $]" ["root.mm"])))))))
