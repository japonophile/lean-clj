(ns mm-clj.core-test
  (:require
    [mm-clj.core :refer :all]
    [clojure.test :refer :all])
  (:import
    [mm-clj ParseException]))

(deftest test-strip-comments
  (testing "The token $( begins a comment and $) ends a comment.
           Comments are ignored (treated like white space) for the purpose of parsing."
    (is (= "$c wff $.\n\n$v x $.\n"    (strip-comments "$c wff $.\n$( comment $)\n$v x $.\n")))
    (is (= "$c wff $.\n\n$v x $.\n\nax1 $a x $.\n"
           (strip-comments "$c wff $.\n$( first comment $)\n$v x $.\n$( second comment $)\nax1 $a x $.\n")))
    (is (= "$c wff $.\n\n$v x $.\n"    (strip-comments "$c wff $.\n$( multiline \ncomment $)\n$v x $.\n")))
    (is (thrown? ParseException (strip-comments "$c wff $.\n$( unfinished comment")))
    (is (thrown? ParseException (strip-comments "$c wff $.\n$) $v x $.\n$( finished comment $)\n"))))
  (testing "$( $[ $) is a comment"
    (is (= "$c wff $.\n\n$v x $.\n"    (strip-comments "$c wff $.\n$( $[ $)\n$v x $.\n"))))
  (testing "they may not contain the 2-character sequences $( or $) (comments do not nest)"
    (is (thrown? ParseException (strip-comments "$c wff $.\n$( comment $( nested comment, illegal $) $)\n$v x $.\n")))))

(deftest test-load-includes
  (let [slurp-original slurp
        slurp-mocked   (fn [filename]
                         (case filename
                           ; filename       ; file content
                           "abc.mm"           "$c a b c $.\n"
                           "xyz.mm"           "$v x y z $.\n"
                           "xyz-comment.mm"   "$c wff $.\n$( comment $)\n$v x y z $.\n"
                           "xyz-include.mm"   "$c wff $.\n$[ abc.mm $]\n$v x y z $.\n"
                           "xyz-include2.mm"  "$c wff $.\n$[ abc.mm $]\n$[ root.mm $]\n$v x y z $.\n"
                           "wrong-include.mm" "$c a $.\n${ $[ xyz.mm $] $}\n$v n $.\n"
                           "root.mm"          "this file should not be read"
                           (slurp-original filename)))]
    (with-redefs [slurp slurp-mocked]
      (testing "A file inclusion command consists of $[ followed by a file name followed by $]."
        (is (= "$c a $.\n$v x y z $.\n\n$v n $.\n"
               (first (load-includes "$c a $.\n$[ xyz.mm $]\n$v n $.\n" ["root.mm"]))))
        (is (= "$c a $.\n$c wff $.\n\n$v x y z $.\n\n$v n $.\n"
               (first (load-includes "$c a $.\n$[ xyz-comment.mm $]\n$v n $.\n" ["root.mm"])))))
      (testing "It is only allowed in the outermost scope (i.e., not between ${ and $})"
        (is (thrown? ParseException (load-includes "$[ wrong-include.mm $]\n" ["root.mm"]))))
      (testing "nested inclusion"
        (is (= "$c a $.\n$c wff $.\n$c a b c $.\n\n$v x y z $.\n\n$v n $.\n"
               (first (load-includes "$c a $.\n$[ xyz-include.mm $]\n$v n $.\n" ["root.mm"]))))
        (is (= "$c a $.\n$c wff $.\n$c a b c $.\n\n\n$v x y z $.\n\n$v n $.\n"
               (first (load-includes "$c a $.\n$[ xyz-include2.mm $]\n$v n $.\n" ["root.mm"])))))
      (testing "no multiple inclusion"
        (is (= "$c a $.\n\n$v n $.\n"
               (first (load-includes "$c a $.\n$[ root.mm $]\n$v n $.\n" ["root.mm"]))))
        (is (= "$c a $.\n$c wff $.\n$c a b c $.\n\n$v x y z $.\n\n$v n $.\n\n"
               (first (load-includes "$c a $.\n$[ xyz-include.mm $]\n$v n $.\n$[ abc.mm $]\n" ["root.mm"]))))))))

(deftest variables-and-constants
  (testing "The same math symbol may not occur twice in a given $v or $c statement"
    (is (thrown? ParseException (parse-mm-program "$c c c $.\n")))
    (is (thrown? ParseException (parse-mm-program "$v x y x $.\n"))))
  (testing "A math symbol becomes active when declared and stays active until the end of the block in which it is declared."
    (is (true? (:active (get (:variables (parse-mm-program "$v x y $.\n")) "x")))))
  (testing "A constant must be declared in the outermost block"
    (is (record? (parse-mm-program "$c a b c $.\n${\n  $v x y $.\n$}\n$c d e f $.\n")))
    (is (thrown? ParseException (parse-mm-program "$c a b c $.\n${\n  $c d e f $.\n$}\n"))))
  (testing "A constant ... may not be declared a second time.")
    (is (thrown? ParseException (parse-mm-program "$c a b c $.\n${\n  $v x y $.\n$}\n$c b $.\n")))
  (testing "A variable may not be declared a second time while it is active"
    (is (thrown? ParseException (parse-mm-program "${\n  $v x y $.\n  $v z x $. $}\n"))))
  (testing "[a variable] may be declared again (as a variable, but not as a constant) after it becomes inactive."
    (is (record? (parse-mm-program "${\n  $v x y $.\n$}\n$v z x $.\n")))
    (is (thrown? ParseException (parse-mm-program "${\n  $v x y $.\n$}\n$c z x $.\n")))))

(deftest hypotheses
  (testing "A $f statement consists of a label, followed by $f, followed by its typecode (an active constant), followed by an active variable, followed by the $. token."
    (is (thrown? ParseException (parse-mm-program "$c var c $.\n$v x $.\nvarx $f var y $.\n")))
    (is (thrown? ParseException (parse-mm-program "$c var c $.\n$v x $.\nvarx $f bar x $.\n")))
    (is (thrown? ParseException (parse-mm-program "$c var c $.\n$v x $.\nvarx $f var c $.\n")))
    (is (record? (parse-mm-program "$c var c $.\n$v x $.\nvarx $f var x $.\n")))))

