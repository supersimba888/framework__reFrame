(ns re-frame.middleware
  (:require
    [reagent.ratom  :refer [IReactiveAtom]]
    [re-frame.undo  :refer [store-now!]]
    [re-frame.utils :refer [warn log dbg group groupEnd]]
    [clojure.data   :as data]))


;; See docs in the Wiki: https://github.com/Day8/re-frame/wiki

(defn noop
  "Middleware which does nothing"
  [handler]
  handler)

(defn pure
  "Acts as an adaptor, allowing handlers to be writen as pure functions.
  The re-frame router will pass in an atom as the first parameter. This middleware
  adapts that to the value within the atom.
  If you strip away the error/efficiency checks, this middleware is just:
     (reset! app-db (handler @app-db event-vec))"
  [handler]
  (fn new-handler
    [app-db event-vec]
    (assert (satisfies? IReactiveAtom app-db)
            (str "re-frame: pure not given a Ratom."
                 (if (map? app-db)
                   " Looks like \"pure\" is in the middleware pipeline twice."
                   (str " Got: " app-db))))
    (let [orig-db @app-db
          new-db  (handler orig-db event-vec)]
      (if (nil? new-db)
        (warn "re-frame: your pure handler returned nil. It should return the new db.")
        (if-not (identical? orig-db new-db)
          (reset! app-db new-db))))))


(defn debug
  "Middleware which logs debug information to js/console for each event.
  Includes a clojure.data/diff of the db, before vs after, showing changes."
  [handler]
  (fn new-handler
    [db v]
    (if (satisfies? IReactiveAtom db)
      (str "re-frame: \"debug\" middleware used without prior \"pure\"."))
    (group "re-frame event: " v)
    (let [new-db  (handler db v)
          diff    (data/diff db new-db)]
      (log "only before: " (first diff))
      (log " only after: " (second diff))
      (groupEnd)
      new-db)))


(defn undoable
  "Middleware which stores an undo checkpoint."
  [handler]
  (fn new-handler
    [app-db event-vec]
    (store-now!)
    (handler app-db event-vec)))


(defn trim-v
  "Middleware which removes the first element of v. Its use means you can write
  more asthetically pleasing handlers.
  Your handlers will look like this:
      (defn my-handler
        [db [x y z]]    ;; <-- instead of [_ x y z]
        ....)
  "
  [handler]
  (fn new-handler
    [db v]
    (handler db (vec (rest v)))))


(defn path
  "Supplies a sub-tree of `db` to the handler. A narrowed view.
  Assumes \"pure\" is in the middleware pipeline prior.
  Grafts the result back into db.
  If a get-in of the path results in a nil, then \"default-fn\" will be called to supply a value."
  ([p]
    (path p hash-map))
  ([p default-fn]
    (fn middleware
      [handler]
      (fn new-handler
        [db v]
        (if (satisfies? IReactiveAtom db)
          (str "re-frame: \"path\" used in middleware, without prior \"pure\"."))
        (if-not (vector? p)
          (warn  "re-frame: \"path\" expected a vector, got: " p))
        (let [val (get-in db p)
              val (if (nil? val) (default-fn) val)]
          (assoc-in db p (handler val v)))))))


(defn validate
  "Middleware factory which applies a given validation function \"f\" to db
  after the handler is finished.
  This validation function f, might further change db, by perhaps
   assoc-ing warnings and errors into db. "
  [f]
  (fn middleware
    [handler]
    (fn new-handler
      [db v]
      (f (handler db v)))))


;; warning: untested
#_(defn log-events
  "Middleware that logs events (vec) using to the given logger fucntion"
  [logger]
  (fn middleware
    [handler]
    (fn new-handler
      [db v]
      (logger v)
      (handler db v))))


;; warning: untested
;; check the state of db AFTER the handler has run, using a prismatic Schema.
#_(defn check-schema
"Middleware for checking that a handlers mutations leave the state in a schema-matching way"
[a-prismatic-schema]
(fn middleware
  [next-handler]
  (fn handler
    [db v]
    (let [val    (next-handler db v)
          valid? true]   ;; XXXXX  replace true by code which checks the schema using original parameter
      (if (not valid?)
        (warn "re-frame: schema not valid after:" v))
      val))))