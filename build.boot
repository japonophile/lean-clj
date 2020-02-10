(set-env!
  :resource-paths #{"src"}
  :source-paths #{"test"}
  :dependencies '[[org.clojure/clojure "1.10.1"]
                  [instaparse "1.4.10"]
                  [adzerk/boot-test "1.2.0" :scope "test"]])

(require '[adzerk.boot-test :refer :all])

(task-options!
  pom {:project 'lein-clj
       :version "0.1.0"})

(deftask build
  "Build my project."
  []
  (comp (pom) (jar) (install)))

(require 'lean-clj.core)
(deftask run []
  (with-pass-thru _
    (lean-clj.core/-main)))
