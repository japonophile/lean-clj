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
