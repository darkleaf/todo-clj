(ns todo.core
  (:require
   [darkleaf.di.core :as di]
   [darkleaf.di.protocols :as dip]
   [darkleaf.web-template.core :as wt]
   [reitit.ring :as r]
   [ring.adapter.jetty :as jetty]
   [ring.util.http-response :as r.resp]
   [ring.middleware.params :as r.params])
  (:import
   [org.eclipse.jetty.server Server]))

(defn db []
  (atom [{:id        (random-uuid)
          :completed true
          :title     "Taste JavaScript"}
         {:id        (random-uuid)
          :completed false
          :title     "Buy a unicorn"}]))

(defn handler [{route-data `route-data}]
  (-> route-data
      (r/router)
      (r/ring-handler
       (fn [_] (r.resp/not-found))
       {:middleware [r.params/wrap-params]})))

(defn jetty [{handler `handler}]
  (jetty/run-jetty handler {:join? false :port 8585}))

(extend-type Server
  dip/Stoppable
  (stop [this]
    (.stop this)))

(def route-data
  (di/template
   [["/" {:get (di/ref `root-handler)}]
    ["/todos" {:post (di/ref `new-todo-handler)}]]))

;; https://github.com/tastejs/todomvc-app-template
(defn layout [body]
  (wt/compile
   [<>
    "<!doctype html>"
    [html
     [head
      [title "Todo Clj"]
      [script {src "https://unpkg.com/@hotwired/turbo@7.2.4/dist/turbo.es2017-umd.js"}]
      [link {rel stylesheet href "https://unpkg.com/todomvc-app-css@2.4.2/index.css"}]]
     [body ~body]]]))

(def root-tmpl
  (-> [section.todoapp
       [header.header
        [h1 "todos"]
        [form {action "/todos" method post}
         [input.new-todo {placeholder "What needs to be done?"
                          autofocus   true
                          name        title}]]]
       [section.main
        [input#toggle-all.toggle-all {type checkbox}]
        [label {for toggle-all} "Mark all as complete"]
        [ul.todo-list
         (:todos
          [li {class {completed (:completed)}
               id    (:id)}
           [.view
            [input.toggle {type checkbox checked (:completed)}]
            [label (:title)]
            [button.destroy]]
           [input.edit {value "Create a TodoMVC template"}]])

         #_#_
         [li.completed
          [.view
           [input.toggle {type checkbox checked true}]
           [label "Taste JavaScript"]
           [button.destroy]]
          [input.edit {value "Create a TodoMVC template"}]]
         [li #_.editing
          [.view
           [input.toggle {type checkbox}]
           [label "Buy a unicorn"]
           [button.destroy]]
          [input.edit {value "Rule the web"}]]]]
       [footer.footer
        [span.todo-count
         [strong 0]
         "item left"]
        #_...]]
      wt/compile
      layout))

(defn root-handler [{db `db} req]
  (-> (r.resp/ok
       (wt/render-to-string root-tmpl {:todos @db}))
      (r.resp/content-type "text/html")))

(defn new-todo-handler [{db `db} req]
  (let [title (get-in req [:form-params "title"])]
    (swap! db conj {:id        (random-uuid)
                    :completed false
                    :title     title}))
  (r.resp/see-other "/"))


(defonce root (atom nil))

(defn start []
  (reset! root (di/start `jetty)))

(defn stop []
  (di/stop @root))

(comment
  (start)
  (stop)
  (do
    (stop)
    (start))
  nil)
