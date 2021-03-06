(ns teamwall.db
  "Communicate with the Mongo database"
  (:require [clojurewerkz.scrypt.core :as scrypt]
            [monger.collection :as mc]
            [monger.core :as mg]
            [monger.json]
            [monger.operators :refer :all]
            [monger.query :as mq])
  (:use [slingshot.slingshot :only [throw+ try+]])
  (:import [com.mongodb MongoOptions ServerAddress]
           [org.bson.types ObjectId]))


;;    /==================\
;;    |                  |
;;    |       VARS       |
;;    |                  |
;;    \==================/


(def ^:private db-name
  "Name of the mongo db to use"
  "teamwall")
(def ^:private db-users
  "Name of the user mongo collection"
  "teamwallUsers")
(def ^:private db-photos
  "Name of the photo mongo collection"
  "teamwallPhotos")
(def ^:private db-settings
  "Private: name of the server settings mongo collection"
  "teamwallServerSettings")


;;    /==================\
;;    |                  |
;;    |      PRIVATE     |
;;    |                  |
;;    \==================/


(defn- connect-to-mongo
  "Connect to the mongoDB instance"
  [options db-name]
  (let [conn (mg/connect {:host (:db-host options)
                          :port (:db-port options)})
        db   (mg/get-db conn db-name)]
    (when (and (:db-username options)
               (:db-password options))
      (when-not (mg/authenticate db
                                 (:db-username options)
                                 (.toCharArray (:db-password options)))
        (throw+ {:type     ::connection-failed
                 :host     (:db-host options)
                 :port     (:db-port options)
                 :username (:db-username options)})))
    [conn db]))

(defn- hashed-password
  "Generate a hashed password using Scrypt
  (https://www.tarsnap.com/scrypt/scrypt.pdf)"
  [password salt]
  (scrypt/encrypt (str password salt)
                  16384
                  8
                  1))

(defn- valid-password?
  "Check if the provided password matches the provided hash"
  [password salt pw-hash]
  (if (nil? pw-hash)
    false
    (scrypt/verify (str password salt)
                   pw-hash)))


;;    /==================\
;;    |                  |
;;    |      PUBLIC      |
;;    |                  |
;;    \==================/


(defn register-user
  "Add a new user to the user database"
  [username password email salt settings]
  (let [[conn db] (connect-to-mongo settings
                                    db-name)
        user      {:_id      email
                   :username username
                   :email    email
                   :status   :offline
                   :hash     (hashed-password password salt)}]
    (mc/insert db
               db-users
               user)
    (mg/disconnect conn)
    user))

(defn user-exists
  "Return true if a user with the provided EMAIL exists"
  [email settings]
  (let [[conn db] (connect-to-mongo settings
                                    db-name)
        user      (mc/find-one-as-map db
                                      db-users
                                      {:email email})]
    (mg/disconnect conn)
    (some? user)))

(defn retrieve-user
  "Retrieve a user from the database using its email and password"
  [email password salt settings]
  (let [[conn db]       (connect-to-mongo settings
                                          db-name)
        user            (mc/find-one-as-map db
                                            db-users
                                            {:email email})
        valid-password? (valid-password? password
                                         salt
                                         (:hash user))
        valid-user?     (and
                         (not (nil? user))
                         valid-password?)]
    (mg/disconnect conn)
    (if valid-user?
      user
      (throw+ {:type            ::login-failed
               :email           email
               :valid-password? valid-password?}))))

(defn update-status
  "Update the status of the provided USER to the new VALUE"
  [user value settings]
  (let [[conn db] (connect-to-mongo settings
                                    db-name)]
    (mc/update db
               db-users
               {:_id (:email user)}
               {$set {:status value}})
    (mg/disconnect conn)))

(defn get-users-for-email
  "Retrieve all the users whose email match the pattern provided"
  [pattern settings]
  (let [[conn db]  (connect-to-mongo settings
                                     db-name)
        users (mc/find-maps db
                            db-users
                            {:email (re-pattern pattern)})]
    users))

(defn add-photo!
  "Store a new photo for the provided user.
  If `timelaps` option is false, erase first all the other photos"
  [user filename size content settings]
  (let [[conn db] (connect-to-mongo settings
                                    db-name)
        timelaps  (-> user :settings :timelaps)]
    (when-not timelaps
      (mc/remove db
                 db-photos
                 {:user-id (:email user)}))
    (mc/insert db
               db-photos
               {:_id      (ObjectId.)
                :user-id  (:email user)
                :filename filename
                :size     size
                :content  content})
    (mg/disconnect conn)))

(defn get-last-photo
  "Return the last photo of the user provided as argument"
  [email settings]
  (let [[conn db] (connect-to-mongo settings
                                    db-name)
        photo     (mq/with-collection db db-photos
                    (mq/find  {:user-id email})
                    (mq/sort  {:_id -1})
                    (mq/limit 1))
        result (first photo)]
    (mg/disconnect conn)
    result))

(defn load-settings
  "Return the server settings, or nil if none is found"
  [database-settings]
  (let [[conn db] (connect-to-mongo database-settings
                                    db-name)
        settings  (mc/find-one-as-map db
                                      db-settings
                                      {:_id "server_settings"})]
    (mg/disconnect conn)
    settings))

(defn store-settings
  "Store new server side settings using the OPTIONS provided as argument"
  [options settings]
  (let [[conn db] (connect-to-mongo settings
                                    db-name)]
    (mc/update db
               db-settings
               {:_id "server_settings"}
               {$set options}
               {:upsert true})
    (mg/disconnect conn)
    options))

(defn update-settings!
  "Update the provided user info in db"
  [user settings]
  (let [[conn db] (connect-to-mongo settings
                                    db-name)]
    (mc/update db
               db-users
               {:_id (:email user)}
               {$set {:settings (:settings user)}}
               {:upsert true})
    (mg/disconnect conn)))


;;    /==================\
;;    |                  |
;;    |       TEST       |
;;    |                  |
;;    \==================/


(comment
  (try+
   (register-user "Bob"
                  "password"
                  "email@email.com"
                  "salt")
   (println (retrieve-user "email@email.com"
                           "password"
                           "salt"))
   (println (retrieve-user "email2@email.com"
                           "password"
                           "salt"))
   (catch [:type :teamwall.db/login-failed] {:keys [email]}
     (println (str "Email \""
                   email
                   "\" not found or password incorrect"))))
  )
