(ns main
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.body-params :as bd]
            [io.pedestal.http.route :as route]
            [io.pedestal.test :as test]
            [io.pedestal.interceptor :refer [interceptor]]
            [hiccup.core :as hc]))


;;-----------function-----------


(def database (atom {}))
(def post-numbering (atom 1))

(defn post-list [post]
  (for [key (keys post)]
    [:div {:class "col-sm-4"}
     [:p (str "Post #" (:number (key post)))]
     [:a {:href (str "/post/" (:number (key post)))} [:h3 (str (:title (key post)))]]
     [:p (str (:content (key post)))[:hr][:br][:br]]]))

(defn keymaker [num]
  (cond
   (= (count (str num)) 1) (keyword (str "000" num))
   (= (count (str num)) 2) (keyword (str "00" num))
   (= (count (str num)) 3) (keyword (str "0" num))
   (= (count (str num)) 4) (keyword (str num))))

(defn bootstrap []
  (for [cnt (range 4)]
    ([[:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:link {:rel "stylesheet" :href "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css"}]
      [:script {:src "https://ajax.googleapis.com/ajax/libs/jquery/3.1.1/jquery.min.js"}]
      [:script {:src "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js"}]] cnt)))


;;-----------html-----------


(defn home-html [post]
  (hc/html [:html
            [:head
             [:title "Home"]
             (bootstrap)]
            [:body
             [:nav {:class "navbar navbar-inverse"}
              [:div {:class "container-fluid"}
               [:div {:class "navbar-header"}
                [:a {:class "navbar-brand" :href "/"} "BlogWeb"]]
               [:ul {:class "nav navbar-nav"}
                [:li {:class "active"} [:a {:href "/"} "Home"]]
                [:li [:a {:href "/"} "About"]]]
               [:ul {:class "nav navbar-nav navbar-right"}
                [:li [:a {:href "#"} [:span {:class "glyphicon glyphicon-user"}] " Sign Up"]]
                [:li [:a {:href "#"} [:span {:class "glyphicon glyphicon-log-in"}] " Login"]]]]]
             [:div {:class "jumbotron text-center"}
              [:h1 "Home Page"]
              [:p "Welcome to blog test page"]]
             [:div {:class "container"}
              (if (empty? post)
                [:div {:class "text-center"} "No Post Yet!" [:br][:br]]
                [:div {:class "row"}
                 (post-list (into (sorted-map) post))])
              [:div {:class "text-center"}
               [:a {:href "/new"}
               [:button {:class "btn btn-primary" :type "button"} "Create New Post"]]]]]]))

(def new-post-html
  (hc/html [:html
            [:head
             [:title "Create New Post"]]
            [:body
             [:div {:align "center"}
              [:h1 "Create New Post"]
              [:form {:action "/ok" :method "post" :id "input-form"}
               "Title"[:br]
               [:input {:type "text" :name "title" :size "100%" :required ""}][:br][:br]
               "Content"[:br]
               [:textarea {:name "content" :size "100%" :form "input-form" :rows "20" :cols "100" :required ""}][:br][:br]
               [:a {:href "/"} [:button {:type "button"} "Cancel"]] "    "
               [:input {:type "reset" :value "Reset"}] "    "
               [:input {:type "submit" :value "Create"}]]]]]))

(defn edit-post-html [number title content]
  (hc/html [:html
            [:head
             [:title "Edit Post"]]
            [:body
             [:div {:align "center"}
              [:h1 "Edit Post"]
              [:form {:action (str "/okedit/" number) :method "post" :id "input-form"}
               "Title"[:br]
               [:input {:type "text" :name "title" :size "100%" :value (str title) :required ""}][:br][:br]
               "Content"[:br]
               [:textarea {:name "content" :size "100%" :form "input-form" :rows "20" :cols "100" :required ""} (str content)][:br][:br]
               [:a {:href "/"} [:button {:type "button"} "Cancel"]] "    "
               [:input {:type "reset" :value "Reset"}] "    "
               [:input {:type "submit" :value "Edit"}]]]]]))

(def post-ok-html
  (hc/html [:html
            [:head
             [:title "Create New Post"]]
            [:body
             [:div {:align "center"}
              [:h1 "Congratulations"]
              "Your post successfully created!"[:br][:br]
              [:a {:href "/new"} [:button {:type "button"} "Create New Post"]]"   "
              [:a {:href "/"} [:button {:type "button"} "Go to Home"]]]]]))

(def edit-ok-html
  (hc/html [:html
            [:head
             [:title "Edit Post"]]
            [:body
             [:div {:align "center"}
              [:h1 "Congratulations"]
              "Your post successfully edited!"[:br][:br]
              [:a {:href "/"} [:button {:type "button"} "Go to Home"]]]]]))

(defn post-view-html [post postid postnum]
  (hc/html [:html
            [:head
             [:title (str "Post Page :: " (:title (postid post)))]]
            [:body
             [:div {:align "center"}
              [:h1 (str (:title (postid post)))]
              (str (:content (postid post)))[:br][:br]
              [:a {:href "/"} [:button {:type "button"} "Go to Home"]]"   "
              [:a {:href (str "/delete/" postnum)} [:button {:type "button"} "Delete"]] "   "
              [:a {:href (str "/edit/" postnum)} [:button {:type "button"} "Edit"]]]]]))

(def no-page-html
  (hc/html [:html
            [:head
             [:title "Page Not Found"]]
            [:body
             [:div {:align "center"}
              [:h1 "Page Not Found"]
              "The page you requested cannot be found"]]]))

(defn delete-confirm-html [postnum]
  (hc/html [:html
            [:head
             [:title "Delete Post Confirmation"]]
            [:body
             [:div {:align "center"}
              (str "Are you sure you want to delete Post #" postnum "?")[:br][:br]
              [:form {:action (str "/deleteok/" postnum) :method "post"}
               [:a {:href (str "/post/" postnum)} [:button {:type "button"} "No"]] "   "
               [:input {:type "submit" :value "Yes"}]]]]]))

(def delete-ok-html
  (hc/html [:html
            [:head
             [:title "Delete Success"]]
            [:body
             [:div {:align "center"}
              [:h1 "Congratulations"]
              "Your post successfully deleted!"[:br][:br]
              [:a {:href "/"} [:button {:type "button"} "Go to Home"]]]]]))


;;-----------interceptor-----------


(def home-main
  (interceptor
   {:name :home-main
    :enter
    (fn [context]
      (let [request (:request context)
            response {:status 200 :body (home-html @database)}]
        (assoc context :response response)))}))

(def new-post
  (interceptor
   {:name :new-post
    :enter
    (fn [context]
      (let [request (:request context)
            response {:status 200 :body new-post-html}]
        (assoc context :response response)))}))

(def view-post
  (interceptor
   {:name :view-post
    :enter
    (fn [context]
      (let [postnum (get-in context [:request :path-params :postid])
            postid (keymaker postnum)
            response {:status 200 :body (post-view-html @database postid postnum)}]
        (if (= (postid @database) nil)
          (assoc context :response {:status 404 :body no-page-html})
          (assoc context :response response))))}))

(def create-post
  (interceptor
   {:name :create-post
    :enter
    (fn [context]
      (let [title (:title (:form-params (:request context)))
            content (:content (:form-params (:request context)))
            post-num @post-numbering
            post-num-key (keymaker post-num)]
        (swap! database assoc post-num-key {:number post-num :title title :content content})
        (swap! post-numbering inc)
        (assoc context :response {:status 200 :body post-ok-html})))}))

(def edit-post-ok
  (interceptor
   {:name :edit-post-ok
    :enter
    (fn [context]
      (let [title (:title (:form-params (:request context)))
            content (:content (:form-params (:request context)))
            post-num (get-in context [:request :path-params :postid])
            post-num-key (keymaker post-num)]
        (swap! database assoc post-num-key {:number post-num :title title :content content})
        (assoc context :response {:status 200 :body edit-ok-html})))}))

(def edit-post
  (interceptor
   {:name :edit-post
    :enter
    (fn [context]
      (let [postid (keymaker (get-in context [:request :path-params :postid]))
            title (get-in @database [postid :title])
            content (get-in @database [postid :content])
            number (get-in @database [postid :number])]
        (if (= (postid @database) nil)
          (assoc context :response {:status 404 :body no-page-html})
          (assoc context :response {:status 200 :body (edit-post-html number title content)}))))}))

(def delete-post
  (interceptor
   {:name :delete-post
    :enter
    (fn [context]
      (let [postnum (get-in context [:request :path-params :postid])
            response {:status 200 :body (delete-confirm-html postnum)}]
        (assoc context :response response)))}))

(def delete-post-ok
  (interceptor
   {:name :delete-post-ok
    :enter
    (fn [context]
      (let [postid (keymaker (get-in context [:request :path-params :postid]))
            response {:status 200 :body delete-ok-html}]
        (swap! database dissoc postid)
        (assoc context :response response)))}))


;;-----------routes&server-----------


(def routes
  (route/expand-routes
   #{["/" :get [(bd/body-params) http/html-body home-main]]
     ["/new" :get [(bd/body-params) http/html-body new-post]]
     ["/ok" :post [(bd/body-params) http/html-body create-post]]
     ["/okedit/:postid" :post [(bd/body-params) http/html-body edit-post-ok]]
     ["/post/:postid" :get [(bd/body-params) http/html-body view-post]]
     ["/edit/:postid" :get [(bd/body-params) http/html-body edit-post]]
     ["/delete/:postid" :get [(bd/body-params) http/html-body delete-post]]
     ["/deleteok/:postid" :post [(bd/body-params) http/html-body delete-post-ok]]}))

(def service-map
  {::http/routes routes
   ::http/type   :jetty
   ::http/port   8890})


(defn start []
  (http/start (http/create-server service-map)))

;; For interactive development
(defonce server (atom nil))

(defn start-dev []
  (reset! server
          (http/start (http/create-server
                       (assoc service-map
                              ::http/join? false)))))

(defn stop-dev []
  (http/stop @server))

(defn restart []
  (stop-dev)
  (start-dev))

(defn test-request [verb url]
  (io.pedestal.test/response-for (::http/service-fn @server) verb url))
