(defproject re-frame "see :git-version below https://github.com/arrdem/lein-git-version"
  :description  "A Clojurescript MVC-like Framework For Writing SPAs Using Reagent."
  :url          "https://github.com/day8/re-frame.git"
  :license      {:name "MIT"}

  :git-version
  {:status-to-version
   (fn [{:keys [tag version branch ahead ahead? dirty?] :as git-status}]
     (if-not (string? tag)
       ;; If git-status is nil (i.e. IntelliJ reading project.clj) then return an empty version.
       "_"
       (if (and (not ahead?) (not dirty?))
         tag
         (let [[_ major minor patch suffix] (re-find #"v?(\d+)\.(\d+)\.(\d+)(-.+)?" tag)]
           (if (nil? major)
             ;; If tag is poorly formatted then return GIT-TAG-INVALID
             "GIT-TAG-INVALID"
             (let [patch' (try (Long/parseLong patch) (catch Throwable _ 0))
                   patch+ (inc patch')]
               (str major "." minor "." patch+ suffix "-" ahead "-SNAPSHOT")))))))}

  :dependencies [[org.clojure/clojure       "1.10.1"   :scope "provided"]
                 [org.clojure/clojurescript "1.10.520" :scope "provided"
                  :exclusions [com.google.javascript/closure-compiler-unshaded
                               org.clojure/google-closure-library]]
                 [thheller/shadow-cljs      "2.8.69"   :scope "provided"]
                 [reagent                   "0.9.0-rc2"]
                 [net.cgrand/macrovich      "0.2.1"]
                 [org.clojure/tools.logging "0.4.1"]]

  :plugins [[me.arrdem/lein-git-version "2.0.3"]
            [lein-shadow                "0.1.6"]]

  :profiles {:debug {:debug true}
             :dev   {:dependencies [[binaryage/devtools "0.9.10"]]
                     :plugins      [[lein-ancient "0.6.15"]
                                    [lein-shell   "0.5.0"]]}}

  :clean-targets  [:target-path "run/compiled"]

  :resource-paths ["run/resources"]
  :jvm-opts       ["-Xmx1g"]
  :source-paths   ["src"]
  :test-paths     ["test"]

  :shell          {:commands {"open" {:windows ["cmd" "/c" "start"]
                                      :macosx  "open"
                                      :linux   "xdg-open"}}}

  :deploy-repositories [["clojars" {:sign-releases false
                                    :url "https://clojars.org/repo"
                                    :username :env/CLOJARS_USERNAME
                                    :password :env/CLOJARS_PASSWORD}]]

  :release-tasks [["deploy" "clojars"]]

  :shadow-cljs {:nrepl  {:port 8777}

                :builds {:browser-test
                         {:target           :browser-test
                          :ns-regexp        "re-frame\\..*-test$"
                          :test-dir         "run/compiled/browser/test"
                          :compiler-options {:pretty-print                       true
                                             :external-config                    {:devtools/config {:features-to-install [:formatters :hints]}}}
                          :devtools         {:http-port 3449
                                             :http-root "run/compiled/browser/test"
                                             :preloads  [devtools.preload]}}

                         :karma-test
                         {:target           :karma
                          :ns-regexp        "re-frame\\..*-test$"
                          :output-to        "run/compiled/karma/test/test.js"
                          :compiler-options {:pretty-print                       true
                                             :closure-defines                    {re-frame.trace.trace-enabled? true}}}}}

  :aliases {"test-once"   ["do" "clean," "shadow" "compile" "browser-test," "shell" "open" "run/compiled/browser/test/index.html"]
            "test-auto"   ["do" "clean," "shadow" "watch" "browser-test,"]
            "karma-once"  ["do"
                           ["clean"]
                           ["shadow" "compile" "karma-test"]
                           ["shell" "karma" "start" "--single-run" "--reporters" "junit,dots"]]
            "karma-auto"  ["do" "clean," "shadow" "watch" "karma-test,"]
            ;; NOTE: In order to compile docs you would need to install
            ;; gitbook-cli(2.3.2) utility globaly using npm or yarn
            "docs-serve" ^{:doc "Runs the development server of docs with live reloading"} ["shell" "gitbook" "serve" "./" "./build/re-frame/"]
            "docs-build" ^{:doc "Builds the HTML version of docs"} ["shell" "gitbook" "build" "./" "./build/re-frame/"]
            ;; NOTE: Calibre and svgexport(0.3.2) are needed to build below
            ;; formats of docs. Install svgexpor3t using npm or yarn.
            "docs-pdf"  ^{:doc "Builds the PDF version of docs"}
            ["do"
             ["shell" "mkdir" "-p" "./build/"]
             ["shell" "gitbook" "pdf" "./" "./build/re-frame.pdf"]]

            "docs-mobi" ^{:doc "Builds the MOBI version of docs"}
            ["do"
             ["shell" "mkdir" "-p" "./build/"]
             ["shell" "gitbook" "mobi" "./" "./build/re-frame.mobi"]]

            "docs-epub" ^{:doc "Builds the EPUB version of docs"}
            ["do"
             ["shell" "mkdir" "-p" "./build/"]
             ["shell" "gitbook" "epub" "./" "./build/re-frame.epub"]]})
