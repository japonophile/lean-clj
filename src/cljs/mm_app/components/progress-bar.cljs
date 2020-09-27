(ns mm-app.components.progress-bar
  (:require
    [goog.string :as gstring]
    [goog.string.format]))

(defn progress-bar
  [valuenow valuemax]
  (let [percent (gstring/format "%.1f" (* 100.0 (/ valuenow valuemax)))]
    [:div {:class "progress"}
     [:div {:class "progress-bar progress-bar-striped active" :role "progressbar"
            :aria-valuenow valuenow :aria-valuemin 0 :aria-valuemax valuemax
            :style {:width (str percent "%")}}
      (str percent "%")]]))
