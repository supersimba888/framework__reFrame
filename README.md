## re-frame

re-frame is a lightweight [reagent] framework for writing  [SPAs] using ClojureScript.

It proposes a pattern for structuring an app, and provides a small library implementing one version of this pattern. 

In another context, re-frame might be called an MVC framework, except it is instead a functional RACES framework - Reactive-Atom Component Event Subscription  (I love the smell of acronym in the morning).  

## Claims

Nothing about re-frame is the slightest bit original or clever. 
You'll find no ingenious use of functional zippers, transducers or core.async. 
This is a good thing (although, for the record, one day I'd love to develop something original and clever).

Using re-frame, you can break your application code into distinct pieces. 
Each of these pieces can be easily described, understood and tested independently. 
These pieces will be pure functions.

At small scale, any framework seems like pesky overhead. The 
explanatory examples in here are small scale, so you'll need to squint a little to 
see the benefit.

## Shaping Beliefs

Above all we believe in the one true [Dan Holmsand] (creator of reagent), 
and his divine instrument the `ratom`.  We genuflect towards Sweden once a day. 

Second, because paradigm is worth 80 points of IQ, we think you'll only 
really "get" Reagent once you view it as an [FRP] library, and not simply a 
ReactJS wrapper. To put that another way, we think that Reagent is closer in 
nature to [Hoplon] or [Elm] than it is [OM]. This wasn't obvious to us initially -   
we knew we liked reagent, but it took a while for the penny to drop as to why. 

Finally, we believe in one way data flow.  We don't like `cursors` which 
allow for the two way flow of data. 

re-frame implements the two way flow of data into and 
out of views by using two, one-way flows. 

## The Parts

To explain re-frame, we'll now incrementally 
develop a diagram.  We'll explain parts as they are progressively 
added. 

##### The Big Ratom 

<blockquote class="twitter-tweet" lang="en"><p>Well-formed Data at rest is as close to perfection in programming as it gets. All the crap that had to happen to put it there however...</p>&mdash; Fogus (@fogus) <a href="https://twitter.com/fogus/status/454582953067438080">April 11, 2014</a></blockquote>
<script async src="//platform.twitter.com/widgets.js" charset="utf-8"></script>

Our re-frame diagram starts with the "well formed data at rest" bit: 
```
ratom
```

So, re-frame says that you should put your data into one, dirty great big atom. Structure the  data in that  atom, of course, but put it all in the one place. 

Now, its not the slightest bit controversial to use databases, right? And they enourage you to put your well formed data all in one place.  But if you have background in OO, this data-in-one-place is a hard one to swallow.  You've 
 spent your life breaking systems into pieces, organised around behaviour and trying 
 to hide the data.  I still wake up in a sweat some nights thinking about all 
 that clojure data lying around exposed and passive.

But, as @Fogus says, data is the easy bit. 

From here on, we'll assume that this part of the framework looks like this:
```
(def ratom  (reagent/atom {}))    ;; a reagent atom, containing a map
```

It is useful to actively imagine our ratom as an (in memory) database. 
It will contain structured data (perhaps with a formal [Prismatic Schema] definition). 
You will need to query that data. You will perform CRUD 
and other transformations on it.  
You'll often want to transact on this database atomically, etc.  So "in-memory database" 
seems a more useful paradigm than plain old atom. In our code, we actually call ratom `db`.

Finally, `db` doesn't actually have to be a ratom containing a map. re-frame 
imposes no requirement here.  It could be a [datascript] database.  Its just a datastore.

##### Magic Ratoms

ratoms have a key feature: they act like normal clojurescript atoms, PLUS they allow
you to create reactive functions similar to `lift` in [Elm] or `defc=` in [hoplon].
You create these reactive functions via the macro `reaction` (or `run!`)

```clojure
(ns example1
  (:require-macros [reagent.ratom :refer [run!]])
  (:require
    [reagent.core   :as    r]))
    
(def ratom1  (r/atom {:a 1}))

(def ratom2  (reaction {:b (:a @ratom1)}))           ;; notice use of "reaction"
(def ratom3  (reaction (cond = (:a @ratom1)          ;; notice use of "reaction"
                             0 "World"
                             1 "Hello")))         

(println @ratom2)    ;; ==>  {:b 1}
(println @ratom3)    ;; ==> "Hello"
(reset!  ratom1  {:a 0})
(println @ratom2)    ;; ==>  {:b 0}    ;; ratom2 is automatically updated.
(println @ratom3)    ;; ==> "World"    ;; ratom3 is automatically updated.
    
;; cleanup 
(dispose ratom2)
(dispose ratom3)
```

`reaction` wraps a function body, and it will cause that function to be rerun each
time one of the ratoms it references changes, and it will swap! the function result
into the ratom it returns.  Over time, ratom2 and ratom3 are auto-updated each
time ratom1 changes. This enables [FRP] (its the equivalent of `lift` in Elm).

### The Components

Extending the diagram a bit, we introduce the beginnings of one way data flow:
```
ratom -->  components --> hiccup
```
When using reagent, you write one or more `components`.   The `[hiccup]` they produce is DOM represented as ClojureScript data structures.

Think about `components` as `pure functions` - data in, hiccup out. 

These components are a bit like the templates you'd find in frameworks 
like Django or Rails or Mustache, except for two massive differences: 

- you have available the full power of ClojureScript.
   (The tradeoff is that these are not "designer friendly" HTML templates)
- these components are reactive.  When their inputs change, they are automatically rerun, producing new hiccup. reagent hides the details from you, but `components` (functions you write) are wrapped by `reaction` in such a way that they re-run when the ratoms they dereference change. This FRP. This is why reagent feels slightly magic.

So `components` are pure, **reactive** functions.  Change the input ratom, and automatically, new hiccup is produced.

This is not a tutorial on how to write reagent components, but let's talk briefly about the "data in" bit. Turns out there are two ways data conponents get data in: 
1. the data is supplied as component parameters, typically from a parent component.  There tends to be a 
   hierarchy of components and data often flows from parent to child via parameters. 

2. a component can 'subscribe' to some aspect of the data.  As a result, it becomes an 
   observer of that state, and it gets an stream of data updates as that part of the ratom changes.

Either way, when the "data in" changes, the component function is rerun, and it produces new hiccup, which is then stictched into the DOM. 

Subscriptions are a significant part of re-frame ... more on them soon. 

### Reactjs

So let's complete the data flow from data to DOM: 

```
ratom  -->  components --> Hiccup --> Reagent --> VDOM  -->  ReactJS  --> DOM
```

This is strictly one way. 

Best to imagine this process as a pipeline of 3 functions.  Each fucntion takes data from the 
previous step, and produces data for the next step.  In the next diagram, the three fucntions is marked. Unmarked nodes are data, produced by one step, becoming the input to the next step.  hiccup, VDOM and DOM are all various forms of HTML markup (in our world that's data).

```
ratom  -->  components --> hiccup --> Reagent --> VDOM  -->  ReactJS  --> DOM
               f1                      f2                     f3                                    
```

The combined three-function pipeline `P` should be seen as a pure function which takes an ratom as input, and produces DOM. One way data flow:
```
ratom  -->  P  --> DOM
```
We only concern ourselves with `ratom` and `components`, and reagent, which wraps reactjs, manages the rest. 

Reactive fucntion:  If the atom changes the DOM changes.  The same data will trigger 
the same DOM to be rendered again. 

We only have to worry ourselves with the `atom` and `components` bit. 

### Events

Once we've displayed a GUi to a user, and they start to interact with it. 

[SPAs]:http://en.wikipedia.org/wiki/Single-page_application
[reagent]:http://reagent-project.github.io/
[Dan Holmsand]:https://github.com/holmsand
[Hiccup]:https://github.com/weavejester/hiccup
[FRP]:https://gist.github.com/staltz/868e7e9bc2a7b8c1f754
[Elm]:http://elm-lang.org/
[OM]:https://github.com/swannodette/om
[Prismatic Schema]:https://github.com/Prismatic/schema
[InterViews]:http://www.softwarepreservation.org/projects/c_plus_plus/library/index.html#InterViews
[datascript]:https://github.com/tonsky/datascript
[Hoplon]:http://hoplon.io/
