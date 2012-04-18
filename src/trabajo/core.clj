(ns trabajo.core
  (:require [redis.core :as redis]))

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
      (redis/hgetall (str (name queue) ":" id)))))

(defn apply-job
  "Applies the function from the job with its arguments."
  [{:strs [func args] :as job}]
  (apply (ns-resolve *ns* (symbol func)) (read-string args)))

(def workers (ref {}))

(defn ^:private init-future [queue]
  (future
    (loop []
      (when-not (Thread/interrupted)
        (if-let [job (dequeue queue)]
          (apply-job job)
          (Thread/sleep 5000))
        (recur)))))

(defn work-on
  "Returns a future that polls the given queue until cancelled."
  [queue]
  (dosync
    (let [f (init-future queue)]
      (alter workers update-in [queue] #(conj (if (nil? %) [] %) f)))))

(defn test-work [x]
  (with-open [out (java.io.FileWriter. "/home/jeremy/job.out" true)]
    (.write out (str x "\n"))))

;(defn inc-workers [queue-key incr]
;  (dosync
;    (alter queues update-in [queue-key :workers] (fn [old]
;                                                   (reduce (fn [v n] (conj v (init-worker))) old (range incr))))))

;(defn ^:private poll-redis [queue-key worker]
;  (with-redis
;    (let [id (-> queue-key queue-name redis/lpop)]
;      (when-not (nil? id)
;        (let [{:strs [func args]} (redis/hgetall id)]
;          (assoc worker :future (future (apply (load-fn func) args))))))))

;(defn ^:private init-workers [worker-count]
;  (reduce (fn [v n] (conj v (init-worker))) [] (range worker-count)))

;(defn ^:private init-manager [queue-key]
;  (future
;    (loop []
;      (when-not (Thread/interrupted)
;        (when (nil? (dosync 
;                      (let [worker (first (filter #(let [f (:future %)] (and (future? f) (future-done? f))) (get-in @queues [queue-key :workers])))]
;                        (when-not (nil? worker)
;                          (poll-redis queue-key worker)))))
;          (Thread/sleep 5000))
;        (recur)))))

;(defn ^:private init-queue [queue-key worker-count]
;  (alter queues assoc queue-key {:manager (init-manager queue-key)

;(defn listen
;  "Returns a future that polls the given Redis queue every five seconds.
;  If one already exists, that one is returned, otherwise a new one is created."
;  ([queue-key] (listen queue-key 1))
;  ([queue-key worker-count]
;   (if (keyword? queue-key)
;     (dosync (:future (-> @queues queue-key #(if (nil? %) (init-queue queue-key worker-count) %))))
;     (throw (IllegalArgumentException. "Argument must be a keyword")))))

