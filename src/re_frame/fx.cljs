(ns re-frame.fx
  (:require [reagent.ratom  :refer [IReactiveAtom]]
            [re-frame.router :refer [dispatch]]
            [re-frame.db :refer [app-db]]
            [re-frame.events]
            [re-frame.loggers    :refer [console]]))


;; -- Registration ------------------------------------------------------------

(def ^:private id->handler-fn  (atom {}))

(defn lookup-handler
  [effect-id]
  (get @id->handler-fn effect-id))


(defn clear-all-handlers!
  []
  (reset! id->handler-fn {}))


(defn clear-handler!
  [effect-id]
  (if (lookup-handler effect-id)
    (swap! id->handler-fn dissoc effect-id)
    (console :warn "re-frame: unable to clear effect handler for  " effect-id ". Not defined.")))


(defn register
  "register a handler fn for an effect."
  [effect-id handler-fn]
  (when (lookup-handler effect-id)
    (console :warn "re-frame: overwriting an effects handler for: " effect-id))   ;; allow it, but warn.
  (swap! id->handler-fn assoc effect-id handler-fn))


;; -- Standard Builtin Effects Handlers  --------------------------------------

(defn dispatch-helper
  [effect]
  (cond
    (list? effect)   (map dispatch effect)
    (vector? effect) (dispatch effect)
    :else (console :error "re-frame: expected :dispatch effect to be a list or vector, but got: " effect)))

;; Example:
;; {:dispatch-later {200  [:event-id "param"]    ;;  in 200ms do this: (dispatch [:event-id "param"])
;;                   100  [:also :this :in :100ms]
;;                   250  (list [:do ] [:all ] [:three ])}
;;
(register
  :dispatch-later
  (fn [effect]
    (doseq  [[ms events] effect]
        (js/setTimeout #(dispatch-helper events) ms))))


;; Supply either a vector or a list of vectors. For example:
;;
;;   {:dispatch [:event-id "param"] }
;;
;;   {:dispatch (list [:do :all] [:three :of] [:these]) }
;;
(register
  :dispatch
  (fn [val]
    (dispatch-helper val)))


;; Provides a way to "forward" events. To put it another way, it provides
;; a way to "sniff" events.
;;
;; Normally, when `(dispatch [:a 42])` happens the event will be routed to
;; the registered handler for `:a`, and that is the end of the matter.
;;
;; BUT with this effect, you can ask that an event is ALSO
;; forwarded to another handler for further processing. This allows this
;; 2nd handler to further process events.
;;
;; You provide a set of events to which you'd like to subscribe.
;; "listen" for a set of events and, when they occur,
;; to "forward" the events to another handler.
;;
;; In effect, if you registered a "listener" for event `:a`  and asked for them
;; to be forewared to `[:another "hello"]`, then:
;;   - when some did (dispatch [:a 42])
;;   - the event would be handler by the normal handler for event `:a`
;;   - but then a further dispatch would be made (dispatch [:naother "hello"  [:a 42]])
;;
;; As you can see the enter event is "forwarded' to enother handler for further
;; processing.
;;
;; {:forward-events  {:register    :an-id-for-this-listner
;;                    :events      #{:event1  :event2}
;;                    :dispatch-to [:eid "eg. param"]}     ;; the forwared event will be conj to the end of the dispatch.
;;
;; {:forward-events  {:unregister :the-id-supplied-when-registering}}
;;
#_(register
  :forward-events
  (let [id->listen-fn (atom {})
        process-one-entry (fn [{:as m :keys [unlisten listen events dispatch-to]}]
                            (let [_  (assert (map? m) (str "re-frame: effects handler for :forward-events expected a map or a list of maps. Got: " m))
                                  _  (assert (or (= #{:unlisten} (-> m keys set))
                                                 (= #{:listen :events :dispatch-to} (-> m keys set))) "re-frame: effects handler for :forward-events given wrong map keys")]
                              (if unlisten
                                (do
                                  (re-frame.core/remove-post-event-callback (@id->listen-fn unlisten))
                                  (swap! id->listen-fn dissoc unlisten))
                                (let [post-event-callback-fn  (fn [event-v _]
                                                                (when (events (first event-v))
                                                                  (dispatch (conj dispatch-to event-v))))]
                                  (re-frame.core/add-post-event-callback  post-event-callback-fn)
                                  (swap! id->listen-fn assoc listen post-event-callback-fn)))))]
    (fn [val]
      (cond
        (map? val) (process-one-entry val)
        (list? val) (doall (map process-one-entry val)))     ;; XXX add else
      )))


(register
  :deregister-event-handler
  (fn [val]
    (if (list? val)
      (doall (map re-frame.events/clear-handler! val))
      (re-frame.events/clear-handler! val))))


(register
  :db
  (fn [val]
    (reset! app-db val)))

;; -- Middleware --------------------------------------------------------------

;; XXX a coeffect for jsDate ?
;; XXX add metadata saying it is fx.
;; XXX add config
;; XXX world or branch ??  Return world?
;; XXX ordering
;; XXX review other standard middleware
;; XXX think about an undo effect


(defn fx
  [handler]
  (fn fx-handler
    [app-db event-vec]
    (if-not (satisfies? IReactiveAtom app-db)
        (if (map? app-db)
          (console :warn "re-frame: Did you use \"fx\" middleware with \"def-event\"?  Use \"def-event-fx\" instead (and don't directly use \"fx\")")
          (console :warn "re-frame: \"fx\" middleware not given a Ratom.  Got: " app-db)))
    (let [run-effect (fn [[key val]]
                       (if-let [effect-fn  (lookup-handler key)]
                         (effect-fn val)
                         (console :error "re-frame: no effects handler registered for: " key ". Ignoring")))
          world   {:db @app-db}]
      (->> (handler world event-vec)   ;; is expected to return a map of effects
           (mapv run-effect)))))       ;; use mapv to process the returned effects (because it isn't lazy)
