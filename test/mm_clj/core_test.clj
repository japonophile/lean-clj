(ns mm-clj.core-test
  (:require
    [mm-clj.core :refer [strip-comments load-includes parse-mm-program 
                         mandatory-variables mandatory-hypotheses mandatory-disjoints
                         verify-proofs]]
    [clojure.test :refer [deftest is testing]])
  (:import
    [mm-clj ParseException]))

(deftest test-strip-comments
  (testing "The token $( begins a comment and $) ends a comment.
           Comments are ignored (treated like white space) for the purpose of parsing."
    (is (= "$c wff $.\n\n$v x $.\n"    (strip-comments "$c wff $.\n$( comment $)\n$v x $.\n")))
    (is (= "$c wff $.\n\n$v x $.\n\nax1 $a x $.\n"
           (strip-comments "$c wff $.\n$( first comment $)\n$v x $.\n$( second comment $)\nax1 $a x $.\n")))
    (is (= "$c wff $.\n\n$v x $.\n"    (strip-comments "$c wff $.\n$( multiline \ncomment $)\n$v x $.\n")))
    (is (thrown-with-msg? ParseException #"Malformed comment"
                          (strip-comments "$c wff $.\n$( unfinished comment")))
    (is (thrown-with-msg? ParseException #"Malformed comment"
                          (strip-comments "$c wff $.\n$) $v x $.\n$( finished comment $)\n"))))
  (testing "$( $[ $) is a comment"
    (is (= "$c wff $.\n\n$v x $.\n"    (strip-comments "$c wff $.\n$( $[ $)\n$v x $.\n"))))
  (testing "they may not contain the 2-character sequences $( or $) (comments do not nest)"
    (is (thrown-with-msg? ParseException #"Comments may not be nested"
                          (strip-comments "$c wff $.\n$( comment $( nested comment, illegal $) $)\n$v x $.\n")))))

(deftest test-load-includes
  (let [slurp-original slurp
        slurp-mocked   (fn [filename]
                         (case filename
                           ; filename       ; file content
                           "./abc.mm"           "$c a b c $.\n"
                           "./xyz.mm"           "$v x y z $.\n"
                           "./xyz-comment.mm"   "$c wff $.\n$( comment $)\n$v x y z $.\n"
                           "./xyz-include.mm"   "$c wff $.\n$[ abc.mm $]\n$v x y z $.\n"
                           "./xyz-include2.mm"  "$c wff $.\n$[ abc.mm $]\n$[ root.mm $]\n$v x y z $.\n"
                           "./wrong-include.mm" "$c a $.\n${ $[ xyz.mm $] $}\n$v n $.\n"
                           "./root.mm"          ""
                           (slurp-original filename)))]
    (with-redefs [slurp slurp-mocked]
      (testing "A file inclusion command consists of $[ followed by a file name followed by $]."
        (is (= "$c a $.\n$v x y z $.\n\n$v n $.\n"
               (first (load-includes "$c a $.\n$[ xyz.mm $]\n$v n $.\n" ["root.mm"] "."))))
        (is (= "$c a $.\n$c wff $.\n\n$v x y z $.\n\n$v n $.\n"
               (first (load-includes "$c a $.\n$[ xyz-comment.mm $]\n$v n $.\n" ["root.mm"] ".")))))
      (testing "It is only allowed in the outermost scope (i.e., not between ${ and $})"
        (is (thrown-with-msg? ParseException #".*:expecting \"\$}\".*"
                              (load-includes "$[ wrong-include.mm $]\n" ["root.mm"] "."))))
      (testing "nested inclusion"
        (is (= "$c a $.\n$c wff $.\n$c a b c $.\n\n$v x y z $.\n\n$v n $.\n"
               (first (load-includes "$c a $.\n$[ xyz-include.mm $]\n$v n $.\n" ["root.mm"] "."))))
        (is (= "$c a $.\n$c wff $.\n$c a b c $.\n\n\n$v x y z $.\n\n$v n $.\n"
               (first (load-includes "$c a $.\n$[ xyz-include2.mm $]\n$v n $.\n" ["root.mm"] ".")))))
      (testing "no multiple inclusion"
        (is (= "$c a $.\n\n$v n $.\n"
               (first (load-includes "$c a $.\n$[ root.mm $]\n$v n $.\n" ["root.mm"] "."))))
        (is (= "$c a $.\n$c wff $.\n$c a b c $.\n\n$v x y z $.\n\n$v n $.\n\n"
               (first (load-includes "$c a $.\n$[ xyz-include.mm $]\n$v n $.\n$[ abc.mm $]\n" ["root.mm"] "."))))))))

(deftest variables-and-constants
  (testing "The same math symbol may not occur twice in a given $v or $c statement"
    (is (thrown-with-msg? ParseException #"Constant c was already defined before"
                          (parse-mm-program "$c c c $.\n")))
    (is (thrown-with-msg? ParseException #"Variable x was already defined before"
                          (parse-mm-program "$v x y x $.\n"))))
  (testing "A math symbol becomes active when declared and stays active until the end of the block in which it is declared."
    (is (get (-> (parse-mm-program "$v x y $.\n") :scope :variables) "x")))
  (testing "A constant must be declared in the outermost block"
    (is (record? (parse-mm-program "$c a b c $.\n${\n  $v x y $.\n$}\n$c d e f $.\n")))
    (is (thrown-with-msg? ParseException #".*:expecting \"\$}\".*"
                          (parse-mm-program "$c a b c $.\n${\n  $c d e f $.\n$}\n"))))
  (testing "A constant ... may not be declared a second time.")
    (is (thrown-with-msg? ParseException #"Constant b was already defined before"
                          (parse-mm-program "$c a b c $.\n${\n  $v x y $.\n$}\n$c b $.\n")))
  (testing "A variable may not be declared a second time while it is active"
    (is (thrown-with-msg? ParseException #"Variable x was already defined before"
                          (parse-mm-program "${\n  $v x y $.\n  $v z x $. $}\n"))))
  (testing "[a variable] may be declared again (as a variable, but not as a constant) after it becomes inactive."
    (is (record? (parse-mm-program "${\n  $v x y $.\n$}\n$v z x $.\n")))
    (is (thrown-with-msg? ParseException #"Constant x was previously defined as a variable before"
                          (parse-mm-program "${\n  $v x y $.\n$}\n$c z x $.\n"))))
  (testing "A variable must not match an existing constant (follows from other rules)"
    (is (thrown-with-msg? ParseException #"Variable x matches an existing constant"
                          (parse-mm-program "$c x $.\n$v x $.\n")))))

(deftest hypotheses
  (testing "A $f statement consists of a label, followed by $f, followed by its typecode (an active constant), followed by an active variable, followed by the $. token."
    (is (record? (parse-mm-program "$c var c $.\n$v x $.\nvarx $f var x $.\n")))
    (is (thrown-with-msg? ParseException #"Type bar not found in constants"
                          (parse-mm-program "$c var c $.\n$v x $.\nvarx $f bar x $.\n")))
    (is (thrown-with-msg? ParseException #"Variable y not defined"
                          (parse-mm-program "$c var c $.\n$v x $.\nvarx $f var y $.\n")))
    (is (thrown-with-msg? ParseException #"Variable c not defined"
                          (parse-mm-program "$c var c $.\n$v x $.\nvarx $f var c $.\n"))))
  (testing "A $e statement consists of a label, followed by $e, followed by its typecode (an active constant), followed by zero or more active math symbols, followed by the $. token."
    (is (record? (parse-mm-program "$c var a b $.\n$v x $.\nvarx $f var x $.\ness1 $e var x a a $.\n")))
    (is (record? (parse-mm-program "$c var $.\ness1 $e var $.\n")))
    (is (thrown-with-msg? ParseException #"Type bar not found in constants"
                          (parse-mm-program "$c var a b $.\n$v x $.\nvarx $f var x $.\ness1 $e bar x a a $.\n")))
    (is (thrown-with-msg? ParseException #"Variable or constant y not defined"
                          (parse-mm-program "$c var a b $.\n$v x $.\ness1 $e var y a a $.\n")))
    (is (thrown-with-msg? ParseException #"Variable or constant c not defined"
                          (parse-mm-program "$c var a b $.\n$v x $.\nvarx $f var x $.\ness1 $e var x a b a x c $.\n")))
    (is (thrown-with-msg? ParseException #"Variable or constant x not defined"
                          (parse-mm-program "$c var a b $.\n${ $v x $. $}\ness1 $e var x a a $.\n"))))
  (testing "The type declared by a $f statement for a given label is global even if the variable is not (e.g., a database may not have wff P in one local scope and class P in another)."
    (is (thrown-with-msg? ParseException #"Variable P was previously assigned type wff"
                          (parse-mm-program "$c wff class $.\n${ $v P $.\nwff_P $f wff P $. $}\n${ $v P $.\nclass_P $f class P $. $}\n"))))
  (testing "There may not be two active $f statements containing the same variable."
    (is (thrown-with-msg? ParseException #"Variable x was previously assigned type var"
                          (parse-mm-program "$c var int $.\n$v x $.\nvarx $f var x $.\nintx $f int x $.\n")))))

(deftest disjoints
  (testing "A simple $d statement consists of $d, followed by two different active variables, followed by the $. token."
    (is (record? (parse-mm-program "$v x y $.\n$d x y $.\n")))
    (is (thrown-with-msg? ParseException #"Variable x appears more than once in a disjoint statement"
                          (parse-mm-program "$v x y $.\n$d x x $.\n")))
    (is (thrown-with-msg? ParseException #"Variable x not active"
                          (parse-mm-program "$v y $.\n$d x y $.\n")))
    (is (thrown-with-msg? ParseException #"Variable x not active"
                          (parse-mm-program "$v y $.\n${ $v x $. $}\n$d x y $.\n"))))
  (testing "A compound $d statement consists of $d, followed by three or more variables (all different), followed by the $. token."
    (is (record? (parse-mm-program "$v x y z $.\n$d x z y $.\n")))
    (is (thrown-with-msg? ParseException #"Variable z appears more than once in a disjoint statement"
                          (parse-mm-program "$v x y z $.\n$d z x z y $.\n"))))
  (testing "The order of the variables in a $d statement is unimportant."
    (is (= (parse-mm-program "$v x y z $.\n$d x y z $.\n")
           (parse-mm-program "$v x y z $.\n$d x z y $.\n")))))

(deftest assertions
  (testing "A $a statement consists of a label, followed by $a, followed by its typecode (an active constant), followed by zero or more active math symbols, followed by the $. token."
    (is (record? (parse-mm-program "$c var wff $.\n$v x $.\nvarx $f var x $.\nax1 $a wff x $.\n")))
    (is (record? (parse-mm-program "$c var wff = $.\n$v x $.\nvarx $f var x $.\nax1 $a wff = x x $.\n")))
    (is (thrown-with-msg? ParseException #"Type woof not found in constants"
                          (parse-mm-program "$c var wff $.\n$v x $.\nvarx $f var x $.\nax1 $a woof x $.\n")))
    (is (thrown-with-msg? ParseException #"Variable or constant y not defined"
                          (parse-mm-program "$c var wff $.\n$v x $.\nvarx $f var x $.\nax1 $a wff y $.\n"))))
  (testing "A $p statement consists of a label, followed by $p, followed by its typecode (an active constant), followed by zero or more active math symbols, followed by $=, followed by a sequence of labels, followed by the $. token."
    (is (record? (parse-mm-program "$c var wff $.\n$v x $.\nvarx $f var x $.\ndum $a var x $.\np1 $p wff x $= dum dum $.\n")))
    (is (record? (parse-mm-program "$c var wff = $.\n$v x $.\nvarx $f var x $.\nding $a var x $.\ndong $a wff x $.\np1 $p wff = x x $= ding dong $.\n")))
    (is (thrown-with-msg? ParseException #"Type woof not found in constants"
                          (parse-mm-program "$c var wff $.\n$v x $.\nvarx $f var x $.\ndum $a var x $.\np1 $p woof x $= dum dum $.\n"))))
  (testing "Each variable in a $e, $a, or $p statement must exist in an active $f statement."
    (is (thrown-with-msg? ParseException #"Variable x must be assigned a type"
                          (parse-mm-program "$c var wff $.\n$v x $.\nmin $e wff x $.\n")))
    (is (thrown-with-msg? ParseException #"Variable x must be assigned a type"
                          (parse-mm-program "$c var wff $.\n$v x $.\nax1 $a wff x $.\n")))
    (is (thrown-with-msg? ParseException #"Variable x must be assigned a type"
                          (parse-mm-program "$c var wff $.\n$v x $.\nho $a wff $.\np1 $p wff x $= ho ho $.\n")))
    (is (thrown-with-msg? ParseException #"Variable y must be assigned a type"
                          (parse-mm-program "$c var wff $.\n$v x y $.\nvarx $f var x $.\nax1 $a wff x y $.\n"))))
  (testing "Each label token must be unique"
    (is (thrown-with-msg? ParseException #"Label ax1 was already defined before"
                          (parse-mm-program "$c var wff $.\n$v x $.\nvarx $f var x $.\nax1 $a wff x $.\nax1 $a wff $.\n")))
    (is (thrown-with-msg? ParseException #"Label ax1 was already defined before"
                          (parse-mm-program "$c var wff $.\n$v x $.\n${ varx $f var x $.\nax1 $a wff x $. $}\nvarxx $f var x $.\nax1 $a wff $.\n"))))
  (testing "no label token may match any math symbol token."
    (is (thrown-with-msg? ParseException #"Label ax1 matches a constant"
                          (parse-mm-program "$c var wff ax1 $.\n$v x $.\nvarx $f var x $.\nax1 $a wff x $.\n")))
    (is (thrown-with-msg? ParseException #"Label ax1 matches a variable"
                          (parse-mm-program "$c var wff $.\n$v x ax1 $.\nvarx $f var x $.\nax1 $a wff x $.\n")))
    (is (thrown-with-msg? ParseException #"Constant c matches an existing label"
                          (parse-mm-program "$c var wff $.\n$v x $.\nvarx $f var x $.\nc $a wff x $.\n$c c $.\n")))
    (is (thrown-with-msg? ParseException #"Variable y matches an existing label"
                          (parse-mm-program "$c var wff $.\n$v x $.\nvarx $f var x $.\ny $a wff x $.\n$v y $.\n")))))

(deftest mandatory-elements
  (testing "The set of mandatory variables associated with an assertion is the set of (zero or more) variables in the assertion and in any active $e statements."
    (let [state (parse-mm-program "$c var wff = $.\n$v x y z $.\nvarx $f var x $.\nvarz $f var z $.\nax1 $a wff = x z $.\n")]
      (is (= #{"x" "z"} (mandatory-variables (get (:axioms state) "ax1")))))
    (let [state (parse-mm-program "$c var wff = $.\n$v n x y z $.\nvarx $f var x $.\nvary $f var y $.\nvarz $f var z $.\nmin $e wff = x y $.\nax1 $a wff = x z $.\n")]
      (is (= #{"x" "y" "z"} (mandatory-variables (get (:axioms state) "ax1"))))))
  (testing "The (possibly empty) set of mandatory hypotheses is the set of all active $f statements containing mandatory variables, together with all active $e statements."
    (let [state (parse-mm-program "$c var wff = $.\n$v x y z $.\nvarx $f var x $.\nvarz $f var z $.\nax1 $a wff = x z $.\n")]
      (is (= ["varx" "varz"] (mandatory-hypotheses (get (:axioms state) "ax1") state))))
    (let [state (parse-mm-program "$c var wff = $.\n$v n x y z $.\nvarx $f var x $.\nvary $f var y $.\nvarz $f var z $.\nmin $e wff = x y $.\nax1 $a wff = x z $.\n")]
      (is (= ["varx" "vary" "varz" "min"] (mandatory-hypotheses (get (:axioms state) "ax1") state))))
    (let [state (parse-mm-program "$c var wff = $.\n$v n x y z $.\nvary $f var y $.\nvarx $f var x $.\nmin $e wff = x y $.\nvarz $f var z $.\nax1 $a wff = x z $.\n")]
      (is (= ["vary" "varx" "min" "varz"] (mandatory-hypotheses (get (:axioms state) "ax1") state)))))
  (testing "The set of mandatory $d statements associated with an assertion are those active $d statements whose variables are both among the assertion’s mandatory variables."
    (let [state (parse-mm-program "$c var wff = $.\n$v x y z $.\nvarx $f var x $.\nvarz $f var z $.\n$d x y $.\n$d y z $.\n$d x z $.\nax1 $a wff = x z $.\n")]
      (is (= #{["x" "z"]} (mandatory-disjoints (get (:axioms state) "ax1")))))
    (let [state (parse-mm-program "$c var wff = $.\n$v x y z $.\nvarx $f var x $.\nvarz $f var z $.\n$d x y $.\n$d y z $.\nmin $e wff = x z $.\nax1 $a wff = x z $.\n")]
      (is (= #{} (mandatory-disjoints (get (:axioms state) "ax1")))))
    (let [state (parse-mm-program "$c var wff = $.\n$v x y z $.\nvarx $f var x $.\nvary $f var y $.\nvarz $f var z $.\n$d x y $.\n$d y z $.\n$d x z $.\nmin $e = y y $.\nax1 $a wff = x z $.\n")]
      (is (= #{["x" "y"] ["x" "z"] ["y" "z"]} (mandatory-disjoints (get (:axioms state) "ax1")))))))

(deftest proof-verification
  (testing "Sample of 'The anatomy of a proof'"
    (let [program "$c ( ) -> wff $.
$v p q r s $.
wp $f wff p $.
wq $f wff q $.
wr $f wff r $.
ws $f wff s $.
w2 $a wff ( p -> q ) $.
wnew $p wff ( s -> ( r -> p ) ) $= ws wr wp w2 w2 $. "
          state (parse-mm-program program)]
      (is (record? (verify-proofs state))))))
