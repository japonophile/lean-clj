(ns mm-clj.model)


(defrecord Program [constants variables
                    symbols symbolmap labels labelmap
                    vartypes axioms provables
                    structure
                    comments formatting])
(defrecord Essential [label typ syms description])
(defrecord Assertion [label typ syms proof scope
                      category title description])
(defrecord Scope [variables vartypes
                  floatings essentials disjoints
                  mvars mhypos mdisjs])
