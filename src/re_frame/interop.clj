(ns re-frame.interop
  (:import [java.util.concurrent Executor Executors]))


;; The purpose of this file is to provide JVM-runnable implementations of the
;; CLJS equivalents in interop.cljs.
;;
;; These implementations are to enable you to bring up a re-frame app on the JVM
;; in order to run tests, or to develop at a JVM REPL instead of a CLJS one.
;;
;; Please note, though, that the purpose here *isn't* to fully replicate all of
;; re-frame's behaviour in a real CLJS environment.  We don't have Reagent or
;; React on the JVM, and we don't try to mimic the stateful lifecycles that they
;; embody.
;;
;; In particular, if you're performing side effects in any code that's triggered
;; by a change to a Ratom's value, and not via a call to `dispatch`, then you're
;; going to have a hard time getting any accurate tests with this code.
;; However, if your subscriptions and Reagent render functions are pure, and
;; your side-effects are all managed by effect handlers, then hopefully this will
;; allow you to write some useful tests that can run on the JVM.


(defonce ^:private executor (Executors/newSingleThreadExecutor))

(defn next-tick [f]
  (let [bound-f (bound-fn [& args] (apply f args))]
    (.execute ^Executor executor bound-f))
  nil)

(def empty-queue clojure.lang.PersistentQueue/EMPTY)

(def after-render next-tick)

(def debug-enabled? true)

(defn ratom [x]
  (atom x))

(defn ratom? [x]
  (instance? clojure.lang.IAtom x))

(defn deref? [x]
  (instance? clojure.lang.IDeref x))

(defn make-reaction
  "On JVM Clojure, return a `deref`-able thing which invokes the given function
  on every `deref`. That is, `make-reaction` here provides precisely none of the
  benefits of `reagent.ratom/make-reaction` (which only invokes its function if
  the reactions that the function derefs have changed value). But so long as `f`
  only depends on other reactions (which also behave themselves), the only
  difference is one of efficiency. That is, your tests should see no difference
  other than that they do redundant work."
  [f]
  (reify clojure.lang.IDeref
    (deref [_] (f))))

(defn add-on-dispose!
  "No-op in JVM Clojure, since for testing purposes, we don't care about
  releasing resources for efficiency purposes."
  [a-ratom f]
  nil)

(defn set-timeout!
  "Note that we ignore the `ms` value and just invoke the function, because
  there isn't often much point firing a timed event in a test."
  [f ms]
  (next-tick f))
