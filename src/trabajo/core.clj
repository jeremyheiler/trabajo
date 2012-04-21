(ns trabajo.core
  (:require [redis.core :as redis]))

; todo
; - namespace redis keys with "trabajo"
; - fn to kill and remove workers
; - fn to replace dead workers
; - job timeouts

(def redis-conf (atom {:host "localhost" :port 6379}))

(defmacro with-redis
  "Executes the body with the default redis instance."
  [& body]
  `(redis/with-server ~(deref redis-conf) ~@body))

(defn enqueue
  "Puts a job onto the queue. Returns the job ID."
  [queue func args & {:keys [timeout] :as opts}] ;todo timeouts
  (with-redis
    (let [qn (name queue) id (redis/incr (str qn ":sequencer"))]
      (redis/hmset (str qn ":" id) "func" func "args" (pr-str args))
      (redis/rpush qn id)
      id)))

(defn dequeue
  "Pulls the next job from the queue. Returns the next job or nil."
  [queue]
  (with-redis
    (when-let [id (redis/lpop (name queue))]
      (-> (redis/hgetall (str (name queue) ":" id))
        (assoc :id id)
        (assoc :queue queue)))))

(defn ^:private store-error
  "Stores the given error for the given job into Redis."
  [job message]
  (with-redis
    (redis/rpush (str (name (:queue job) ) ":failed") (:id job))
    (redis/set (str (name (:queue job)) ":" (:id job) ":error") message)))

(defn apply-job
  "Applies the function from the job with its arguments."
  [{:strs [func args] :as job}]
  (apply (ns-resolve *ns* (symbol func)) (read-string args)))

;(defn process-job
;  "Processes the job and handles any errors."
;  [job]
;  (try
;    (apply-job job)
;    (catch Exception e
;      (store-error job (.toString e)))))

;(def workers (ref {}))

;(defn ^:private process [queue]
;  (loop []
;    (when-not (Thread/interrupted)
;      (if-let [job (dequeue queue)]
;        (process-job job)
;        (Thread/sleep 5000))
;      (recur))))

;(defn work-on
;  "Returns a thread that polls the given queue until interrupted."
;  [queue]
;  (dosync
;    (let [t (Thread. #(process queue))]
;      (alter workers update-in [queue] #(conj (if (nil? %) [] %) t))
;      (.start t))))

(defn test-work [x]
  (with-open [out (java.io.FileWriter. "/home/jeremy/job.out" true)]
    (.write out (str x "\n"))))


(def work-manager (ref {}))

;(def poll-manager (ref {}))

(defn ^:private request-promise-from-work-manager
  ""
  []
  (dosync
    (peek (:promises (alter work-manager update-in [:promises] conj (promise))))))

(defn ^:private process-job
  ""
  [job]
  (try
    (apply-job job)
    (catch Exception e (store-error job (.toString e)))
    (finally (dosync
               (let [p (peek (:promises @work-manager))]
                 ;(alter work-manager update-in [:promises] #(-> % (pop) (conj (promise))))
                 (alter work-manager update-in [:promises] pop)
                 (deliver p true))))))

(defn ^:private process
  ""
  [queues]
  (loop [[q & qs :as q+qs] queues p (atom true)]
    (when-not (Thread/interrupted)
      (if (or
            (< (count (:promises @work-manager)) (:max-workers @work-manager))
            @p) ; blocks until a worker is available
        (if-let [job (dequeue q)]
          (let [f (future-call #(process-job job))]
            ;(dosync (alter work-manager update-in [:futures] conj f))
            (recur queues (request-promise-from-work-manager)))
          (do
            (Thread/sleep 5000)
            (recur (if (nil? qs) queues qs) p)))
        (recur q+qs (request-promise-from-work-manager))))))

(defn start
  "Starts a work manager that polls jobs on the given queue."
  [n & queues]
  (dosync
    (if (nil? (:thread @work-manager))
      (let [t (Thread. #(process queues))]
        (alter work-manager assoc :thread t :max-workers n)
        (.start t))
      (throw (IllegalStateException. "A work manager has already been started.")))))

(defn stop
  "Stops the work manager if one has been started."
  []
  (dosync
    (when-not (nil? (:thread @work-manager))
      (.interrupt @work-manager))))

