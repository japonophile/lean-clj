(ns mm-clj.handmade
  (:require
    [clojure.java.io :as io]
    [taoensso.tufte :as tufte :refer [defnp profiled format-pstats]]))


(set! *warn-on-reflection* true)

;;; Program parsing stuff

(defn file->bytes [filename]
  (with-open [xin (io/input-stream filename)
              xout (java.io.ByteArrayOutputStream.)]
    (io/copy xin xout)
    (.toByteArray xout)))

(defnp parse-comment
  [program start state]
  (loop [i start]
    (if (= (char (aget #^bytes program i)) \$)
      (case (char (aget #^bytes program (inc i)))
        \( (throw "Comments may not be nested")
        \) (do (swap! state update-in [:comments] inc)
               (+ i 2))
        (recur (inc i)))
      (recur (inc i)))))

(defnp parse-mm-program
  "Parse a metamath program"
  [program]
  (let [state (atom {:comments 0})
        n (count program)]
    (loop [i 0]
      (if (< i n)
        (if (= (char (aget #^bytes program i)) \$)
          (case (char (aget #^bytes program (inc i)))
            \( (recur (long (parse-comment program (+ i 2) state)))
            (recur (inc i)))
          (recur (inc i)))
        @state))))

(defn parse-mm
  "Parse a metamath file"
  [filename]
  (let [[program pstats]
        (profiled {}
                  (let [_ (print "Reading program from file... ")
                        _ (flush)
                        bs (file->bytes filename)
                        _ (println "OK!")
                        _ (print "Parsing program... ")
                        _ (flush)
                        program (parse-mm-program bs)
                        _ (println "OK!")]
                    program))]
    (println (:comments program))
    (println (format-pstats pstats))))
