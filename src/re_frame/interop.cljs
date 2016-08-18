(ns re-frame.interop
  (:require [goog.async.nextTick]
            [reagent.core]
            [reagent.ratom]))

(def next-tick goog.async.nextTick)

(def empty-queue #queue [])

(def after-render reagent.core/after-render)

;; make sure the Google Closure compiler see this as a boolean constatnt
;; https://developers.google.com/closure/compiler/docs/js-for-compiler
(def ^:boolean debug-enabled? "@const  @define {boolean}" js/goog.DEBUG)

(defn ratom [x]
  (reagent.core/atom x))

(defn ratom? [x]
  (satisfies? reagent.ratom/IReactiveAtom x))

(defn deref? [x]
  (satisfies? IDeref x))


(defn make-reaction [f]
  (reagent.ratom/make-reaction f))

(defn add-on-dispose! [a-ratom f]
  (reagent.ratom/add-on-dispose! a-ratom f))

(defn set-timeout! [f ms]
  (js/setTimeout f ms))
