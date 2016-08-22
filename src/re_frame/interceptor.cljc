(ns re-frame.interceptor
  (:require
    [re-frame.interop :refer [ratom?]]
    [re-frame.loggers :refer [console]]
    [re-frame.interop :refer [empty-queue debug-enabled?]]))


;; XXX use defrecord ??

(def mandatory-interceptor-keys #{:id :after :before})


(defn interceptor?
  [m]
  (and (map? m)
       (= mandatory-interceptor-keys (-> m keys set))))


(defn ->interceptor
  "Create an interceptor from named arguements"
  [& {:as m :keys [name id before after]}]      ;; XXX remove `name` in due course - only in there as a backwards compat thing
  (when debug-enabled?
    (if name                                    ;; XXX remove in due course
      (console :warn  "re-frame.core/->interceptor no longer takes `:name` - has been renamed to `:id`. Please change for " name))
    (if-let [unknown-keys  (seq (clojure.set/difference
                             (-> (dissoc m :name) keys set)         ;; XXX take out name in due course
                             mandatory-interceptor-keys))]
      (console :error "re-frame: ->interceptor " m " has unknown keys:" unknown-keys)))
  {:id     (or id name :unnamed)     ;; XXX remove `name` in due course
   :before before
   :after  after })

;; -- Effect Helpers  -----------------------------------------------------------------------------

(defn get-effect
  ([context]
   (:effects context))
  ([context key]
   (get-in context [:effects key]))
  ([context key not-found]
   (get-in context [:effects key] not-found)))


(defn assoc-effect
  [context key value]
  (assoc-in context [:effects key] value))

;; -- CoEffect Helpers  ---------------------------------------------------------------------------

(defn get-coeffect
  ([context]
   (:coeffects context))
  ([context key]
   (get-in context [:coeffects key]))
  ([context key not-fount]
   (get-in context [:coeffects key] not-fount)))

(defn assoc-coeffect
  [context key value]
  (assoc-in context [:coeffects key] value))

;; -- Execute Interceptor Chain  ------------------------------------------------------------------


(defn- invoke-interceptor-fn
  [context interceptor direction]
  (if-let [f (get interceptor direction)]
    (f context)
    context))


(defn- invoke-interceptors
  "Loop over all interceptors, calling `direction` function on each,
  threading the value of `context` through every call.

  `direction` is one of `:before` or `:after`.

  Each iteration, the next interceptor to process is obtained from
  context's `:queue`. After they are processed, interceptors are popped
  from `:queue` and added to `:stack`.

  After sufficient iteration, `:queue` will be empty, and `:stack` will
  contain all interceptors processed.

  Returns updated `context`. Ie. the `context` which has been threaded
  through all interceptor functions.

  Generally speaking, an interceptor's `:before` fucntion will (if present)
  add to a `context's` `:coeffect`, while it's `:after` function
  will modify the `context`'s `:effect`.  Very approximately.

  But because all interceptor functions are given `context`, and can
  return a modified version of it, the way is clear for an interceptor
  to introspect the stack or queue, or even modify the queue
  (add new interceptors via `enqueue`?). This is a very fluid arrangement."
  ([context direction]
   (loop [context context]
     (let [queue (:queue context)]        ;; future interceptors
       (if (empty? queue)
         context
         (let [interceptor (peek queue)   ;; next interceptor to call
               stack (:stack context)]    ;; already completed interceptors
           (recur (-> context
                      (assoc :queue (pop queue))
                      (assoc :stack (conj stack interceptor))
                      (invoke-interceptor-fn interceptor direction)))))))))


(defn enqueue
  "Add a collection of `interceptors` to the end of `context's` execution `:queue`.
  Returns the updated `context`.

  In an advanced case, this function would allow an interceptor could add new
  interceptors to the `:queue` of a context."
  [context interceptors]
  (update context :queue
          (fnil into empty-queue)
          interceptors))


(defn- context
  "Create a fresh context"
  ([event interceptors]
  (-> {}
      (assoc-coeffect :event event)
      (enqueue interceptors)))
  ([event interceptors db]      ;; only used in tests, probably a hack, remove ?  XXX
   (-> (context event interceptors)
       (assoc-coeffect :db db))))


(defn- change-direction
  "Called on completion of `:before` processing, this function prepares/modifies
   `context` for the backwards sweep of processing in which an interceptor
   chain's `:after` fns are called.

  At this point in processing, the `:queue` is empty and `:stack` holds all
  the previously run interceptors. So this function enables the backwards walk
  by priming `:queue` with what's currently in `:stack`"
  [context]
  (-> context
      (dissoc :queue)
      (enqueue (:stack context))))


(defn execute
  "Executes the given chain (coll) of interceptors.

   Each interceptor has this form:
       {:before  (fn [context] ...)     ;; returns possibly modified context
        :after   (fn [context] ...)}    ;; `identity` would be a noop

   Walks the queue of iterceptors from beginning to end, calling the
   `:before` fn on each, then reverse direction and walk backwards,
   calling the `:after` fn on each.

   The last interceptor in the chain presumably wraps an event
   handler fn. So the overall goal of the process is to \"handle
   the given event\".

   Thread a `context` through all calls. `context` has this form:

     {:coeffects {:event [:a-query-id :some-param]
                  :db    <original contents of app-db>}
      :effects   {:db    <new value for app-db>
                  :dispatch  [:an-event-id :param1]}
      :queue     <a collection of further interceptors>
      :stack     <a collection of interceptors already walked>}

   `context` has `:coeffects` and `:effects` which, if this was a web
   server, would be somewhat anologous to `request` and `response`
   respectively.

   `coeffects` will contain data like `event` and the initial
   state of `db` -  the inputs required by the event handler
   (sitting presumably on the end of the chain), while handler-returned
   side effects are put into `:effects` including, but not limited to,
   new values for `db`.

   The first few interceptors in a chain will likely have `:before`
   functions which \"prime\" the `context` by adding the event, and
   the current state of app-db into `:coeffects`. But interceptors can
   add whatever they want to `:coeffect` - perhaps the event handler needs
   some information from localstore, or a random number, or access to
   a DataScript connection.

   Equally, some interceptors in the chain will have `:after` fn
   which can process the side effects accumulated into `:effects`
   including but, not limited to, updates to app-db.

   Through both stages (before and after), `context` contains a `:queue`
   of interceptors yet to be processed, and a `:stack` of interceptors
   already done.  In advanced cases, these values can be modified by the
   functions through which the context is threaded."
  [event-v interceptors]
  (-> (context event-v interceptors)
      (invoke-interceptors :before)
      change-direction
      (invoke-interceptors :after)))

