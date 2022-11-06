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
    ["/todos" {:post (di/ref `create-todo-handler)}]
    ["/todos/:id" {:delete (di/ref `destroy-todo-handler)}]]))

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

(def header-tmpl
  (wt/compile
   [header.header#header
    [h1 "todos"]
    [form {action "/todos" method post}
     [input.new-todo {placeholder "What needs to be done?"
                      autofocus   true
                      name        title}]]]))

(def todo-tmpl
  (wt/compile
   [li {class {completed (:completed)}
        id    (:id)}
    [.view
     [input.toggle {type checkbox, checked (:completed)}]
     [label (:title)]
     [form {method delete, action (:delete-url)}
      [button.destroy]]]
    [input.edit {value "Create a TodoMVC template"}]]))

(def footer-tmpl
  (wt/compile
   [footer.footer#footer
    [span.todo-count
     [strong (:todo-count)]
     "item left"]]))

(def root-tmpl
  (-> [section.todoapp
       #'header-tmpl
       [section.main
        [input#toggle-all.toggle-all {type checkbox}]
        [label {for toggle-all} "Mark all as complete"]
        [ul.todo-list#todo-list
         (:todos #'todo-tmpl)
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
       (:footer #'footer-tmpl)]
      wt/compile
      layout))

;; todo: bidirectional routing
(defn todos-presenter [todos]
  (for [t todos]
    (assoc t :delete-url (str "/todos/" (:id t)))))

(defn root-handler [{db `db} req]
  (let [db   @db
        data {:todos  (todos-presenter db)
              :footer {:todo-count (count db)}}]
    (-> (r.resp/ok
         (wt/render-to-string root-tmpl data))
        (r.resp/content-type "text/html"))))

(def new-todo-stream
  (wt/compile
   [<>
    [turbo-stream {action append, target todo-list}
     [template
      (:new-todo #'todo-tmpl)]]

    [turbo-stream {action replace, target header}
     [template
      #'header-tmpl]]

    [turbo-stream {action replace, target footer}
     [template
      (:footer #'footer-tmpl)]]]))

(defn create-todo-handler [{db `db} req]
  (let [title    (get-in req [:form-params "title"])
        new-todo {:id        (random-uuid)
                  :completed false
                  :title     title}
        new-db   (swap! db conj new-todo)
        data     {:new-todo new-todo
                  :footer   {:todo-count (count new-db)}}]
    (-> (wt/render-to-string new-todo-stream data)
        (r.resp/ok)
        (r.resp/content-type "text/vnd.turbo-stream.html"))))

(def destroy-todo-stream
  (wt/compile
   [<>
    (:todo
     [turbo-stream {action remove, target (:id)}])

    [turbo-stream {action replace, target footer}
     [template
      (:footer #'footer-tmpl)]]]))

(defn destroy-todo-handler [{db `db} req]
  (let [id     (-> req :path-params :id parse-uuid)
        new-db (swap! db (partial remove #(= id (:id %))))
        data   {:todo   {:id id}
                :footer {:todo-count (count new-db)}}]
    (-> (wt/render-to-string destroy-todo-stream data)
        (r.resp/ok)
        (r.resp/content-type "text/vnd.turbo-stream.html"))))

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


;; вообще в этой задаче гораздо проще использовать turbo-frame
;; и просто обновлять его целиком, чем использовать turbo-stream,
;; но хочется попробовать ;)
