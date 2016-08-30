## Bootstrapping Application State

To bootstrap a re-frame application, you need to:
  1. register handlers
   - subscription  (via `reg-sub`)
   - events (via `reg-event-db` or `reg-event-fx`)
   - effects (via `reg-fx`)
   - coeffects (via `reg-cofx`)
  2. kickstart reagent (views)
  3. Load the right initial data into `app-db` which might be a `merge` of:
   - Some default values
   - Values stored in LocalStorage
   - Values obtained via service calls to server
   - etc, etc

Point 3 is the interesting bit and will be the main focus of this page, but let's work our way through them ...

## 1. Register Handlers 

re-frame's multifarious handlers all work in the same way.  You declare 
and registered your handlers in the one step, like this "event handler" example: 
```clj
(re-frame/reg-event-db       ;; event handler will be registered automatically
  :some-id
  (fn [db [_ value]]
    ...  do some state change based on db and value ))
```

As a result, there's nothing further you need to do because 
handler registration happens as a direct result of loading the code  
code (presumably via a `<script>`). 


## 2. Kick Start Reagent 

Create a function `main` which does a `reagent/render` of your root reagent component `main-panel`:

```clj
(defn main-panel       ;; my top level reagent component
  []
  [:div "Hello DDATWD"])

(defn ^:export main     ;; call this to bootstrap your app
  []
  (reagent/render [main-panel]
                  (js/document.getElementById "app")))
```

Mounting the top level component `main-panel` will trigger a cascade of child 
component creation.  The full DOM tree will be rendered.

## 3. Loading Initial Data 

Let's rewrite our `main-panel` component to use a subscription. In effect, 
we want it to source and render some data held in `app-db`.  

First, we'll create the subscription handler:
```Clojure
(re-frame/reg-sub     ;; a new subscription handler
  :name               ;; usage (subscribe [:name])
  (fn [db _]
    (:display-name db)))  ;; extracts `:display-name` from app-db
```

And now we use that subscription:
```clj
(defn main-panel 
  []
  (let [name  (re-frame/subscribe [:name])]  ;; <--- a subscription  <---
    (fn []
      [:div "Hello " @name]))))   ;; <--- use the result of the subscription
```

The user of our app will see funny things 
if that `(subscribe [:name])` doesn't deliver good data. But how do we ensure "good data"?

That will require: 
  1. getting data into `app-db`; and
  2. not get into trouble if that data isn't yet in `app-db`.  For example, 
  the data may have to come from a server and there's latency.

**Note: `app-db` initially contains `{}`**

### Getting Data Into `app-db`

Only event handlers can change `app-db`. Those are the rules!! Indeed, even initial 
values must be put in `app-db` via an event handler. 

Here's an event handler for that purpose:
```Clojure
(re-frame/reg-event-db
  :initialise-db				 ;; usage: (dispatch [:initialise-db])
  (fn [_ _]						 ;; Ignore both params (db and event)
	 {:display-name "DDATWD"	 ;; return a new value for app-db
	  :items [1 2 3 4]}))
```

You'll notice that this handler does nothing other than to return a ` map`. That map 
will become the new value within `app-db`.

We'll need to dispatch an `:initialise-db` event to get it to execute. `main` seems like the natural place: 
```Clojure
(defn ^:export main
  []
  (re-frame/dispatch [:initialise-db])   ;;  <--- this is new 
  (reagent/render [main-panel]
                  (js/document.getElementById "app")))
```

But remember, event handlers execute async. So although there's 
a `dispatch` within `main`, the event is simply queued, and the 
handler for `:initialise-db` 
will not be run until sometime after `main` has finished.

But how long after?  And is there a race condition?  The 
component `main-panel` (which assumes good data) might be 
rendered before the `:initialise-db` event handler has 
put good data into `app-db`. 

We don't want any rendering (of `main-panel`) until after `app-db` 
has been correctly initialised. 

Okay, so that's enough of teasing-out the issues. Let's see a 
quick sketch of the entire pattern. It is very straight-forward.

## The Pattern

```Clojure
(re-frame/reg-sub   ;; the means by which main-panel gets data
  :name             ;; usage (subscribe [:name])
  (fn  [db _]
	(:display-name db)))
	   
(re-frame/reg-sub        ;; we can check if there is data
  :initialised?          ;; usage (subscribe [:initialised?])
  (fn  [db _]
	(not (empty? db))))  ;; do we have data
	
(re-frame/reg-event-db
   :initialise-db
   (fn [db _]
       (assoc db :display-name "Jane Doe")))

(defn main-panel    ;; the top level of our app 
  []
  (let [name  (re-frame/subscribe :name)]   ;; we need there to be good data
    (fn []
      [:div "Hello " @name]))))

(defn top-panel    ;; this is new
  []
  (let [ready?  (re-frame/subscribe [:initialised?])]
    (fn []
      (if-not @ready?         ;; do we have good data?
        [:div "Initialising ..."]   ;; tell them we are working on it
        [main-panel]))))      ;; all good, render this component

(defn ^:export main     ;; call this to bootstrap your app
  []
  (re-frame/dispatch [:initialise-db])
  (reagent/render [top-panel]
                  (js/document.getElementById "app")))
```

## Scales Up

This pattern scales up easily.

For example, imagine a more complicated scenario in which your app 
is not fully initialised until 2 backend services supply data.

Your `main` might look like this:
```Clojure
(defn ^:export main     ;; call this to bootstrap your app
  []
  (re-frame/dispatch [:initialise-db])           ;; basics
  (re-frame/dispatch [:load-from-service-1])     ;; ask for data from service-1
  (re-frame/dispatch [:load-from-service-2])     ;; ask for data from service-2
  (reagent/render [top-panel]
                  (js/document.getElementById "app")))
```

Your `:initialised?` test then becomes more like this sketch: 

```Clojure
(reg-sub
  :initialised?          ;; usage (subscribe [:initialised?])
  (fn  [db _]
    (and  (not (empty? db))
          (:service1-answered? db)
          (:service2-answered? db)))))
```

This assumes boolean flags are set in `app-db` when data was loaded from these services.

## Cheating - Synchronous Dispatch

In simple cases, you can simplify matters by using `(dispatch-sync [:initialise-db])` in 
the main entry point function.  The 
[Simple Example](https://github.com/Day8/re-frame/blob/8cf42f57f50f3ee41e74de1754fdb75f80b31775/examples/simple/src/simpleexample/core.cljs#L110) 
and [TodoMVC Example](https://github.com/Day8/re-frame/blob/8cf42f57f50f3ee41e74de1754fdb75f80b31775/examples/todomvc/src/todomvc/core.cljs#L35) 
both use `dispatch-sync` to initialise the app-db.

`dispatch` queues an event for later processing, but `dispatch-sync` acts 
like a function call and handles an event immediately. That's useful for initial data 
load we are considering, particularly for simple apps. Using `dispatch-sync` guarantees 
that initial state will be in place before any views are mounted, so we know they'll 
subscribe to sensible values.  We don't need a guard like `top-panel` (introduced above). 

But don't get into the habit of using `dispatch-sync` everywhere. It is the right 
tool in this context and, sometimes, when writing tests, but 
`dispatch` is the staple you should use everywhere else.

## Loading Initial Data From Services 

Above,  in our example `main`, we imagined using `(re-frame/dispatch [:load-from-service-1])`  to request data
from a backend services.  How would we write the handler for this event?
 
The next Tutorial will show you how. 



---
Previous:  [Namespaced Keywords](Namespaced-Keywords.md)&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
Up:  [Index](README.md)&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
Next:  [Talking To Servers](Talking-To-Servers.md)  

 
