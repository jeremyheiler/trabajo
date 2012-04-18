(ns trabajo.core
  (:require [redis.core :as redis]))

; todo
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
      (redis/hgetall (str (name queue) ":" id)))))

(defn apply-job
  "Applies the function from the job with its arguments."
  [{:strs [func args] :as job}]
  (apply (ns-resolve *ns* (symbol func)) (read-string args)))

(def workers (ref {}))

(defn ^:private process [queue]
  (loop []
    (when-not (Thread/interrupted)
      (if-let [job (dequeue queue)]
        (apply-job job)
        (Thread/sleep 5000))
      (recur))))

(defn work-on
  "Returns a thread that polls the given queue until interrupted."
  [queue]
  (dosync
    (let [t (Thread. #(process queue))]
      (alter workers update-in [queue] #(conj (if (nil? %) [] %) t))
      (.start t))))

(defn test-work [x]
  (with-open [out (java.io.FileWriter. "/home/jeremy/job.out" true)]
    (.write out (str x "\n"))))

