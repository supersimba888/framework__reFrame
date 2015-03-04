(ns re-frame.middleware
  (:require
    [reagent.ratom  :refer [IReactiveAtom]]
    [re-frame.undo  :refer [store-now!]]
    [re-frame.utils :refer [warn log dbg group groupEnd]]
    [clojure.data   :as data]))


;; See docs in the Wiki: https://github.com/Day8/re-frame/wiki


(defn pure
  "Acts as an adaptor, allowing handlers to be writen as pure functions.
  The re-frame router will pass in an atom as the first parameter. This middleware
  adapts that to the value within the atom.
  If you strip away the error/efficiency checks, this middleware is just:
     (reset! app-db (handler @app-db event-vec))"
  [handler]
  (fn pure-handler
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
  Includes a clojure.data/diff of the db, before vs after, showing the changes
  caused by the event."
  [handler]
  (fn debug-handler
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
  (fn undoable-handler
    [app-db event-vec]
    (store-now!)
    (handler app-db event-vec)))


(defn trim-v
  "Middleware which removes the first element of v which allows you to write
  more asthetically pleasing handlers. No leading underscore on the event-v!
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
  If a get-in of the path results in a nil, then \"default-fn\" will be called to supply a value.
  XXX very like update-in. Should the name be more indicative of that closeness? "
  ([p]
    (path p hash-map))
  ([p default-fn]
    (fn middleware
      [handler]
      (fn path-handler
        [db v]
        (if (satisfies? IReactiveAtom db)
          (str "re-frame: \"path\" used in middleware, without prior \"pure\"."))
        (if-not (vector? p)
          (warn  "re-frame: \"path\" expected a vector, got: " p))
        (let [val (get-in db p)
              val (if (nil? val) (default-fn) val)]
          (assoc-in db p (handler val v)))))))


(defn derive
  "Middleware factory which runs a given function \"f\" in the after position.
  \"f\" is (db) -> db
  Different from \"after\" because f is expecteed to produce new derived data.
  re-frame is about derived data flowing,and this middleware allows you to
  derive new data each time a handler is called.
  A usecase: f can perform certain kinds of \"overall\" validation checks
  on the newly minted state, adding or removing error and warning flags.
  This  is such a common pattern!!
  There are invariably a category of checks that require \"overall knowledge\".
  For example, imagine that todomvc had to do duplicate detection - if any
  two todos had the same text, highlight them, and put a warning down the
  bottom.
  Almost any action (edit text, add new todo, remove a todo) requires that
  that new error/warning data be derived from the new state.
  And to perform this derivation, requires access to all todos, plus the ability to:
     - set (or remove) duplicate flags on individual todos (so they are
       rendered with a pink background?)
     - add (or remove) warnings at a more global level
  And that's just one kind of check, there may be a few that are need to run
  on every change.
  \"f\" would need to be both adding and removing the duplicate warnings.
  We could add a call to f in each handler but it is convienient to use
  middleware instead. "
  [f]
  (fn middleware
    [handler]
    (fn validate-handler
      [db v]
      (f (handler db v)))))



(defn after
  "Middleware factory which runs a function \"f\" in the \"after handler\"
  position presumably for side effects.
  \"f\" is given the value of \"db\". It's return value is ignored.
  Examples: \"f\" can run schema validation. Or write current state to localstorage. etc.
  In effect, \"f\" is meant to sideeffect. It gets no chance to change db. See \"derive\" (if you need that.)"
  [f]
  (fn middleware
    [handler]
    (fn after-handler
      [db v]
      (let [new-db (handler db v)]
        (f new-db)   ;; call f for side effects
        new-db))))


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

