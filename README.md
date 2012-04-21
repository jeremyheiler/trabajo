# Trabajo

Trabajo is a Redis-backed library for creating background jobs with Clojure.

## Usage

The two main functions you need to know are `enqueue` and `work-on`.

    (trabajo.core/enqueue :my-queue 'clojure.core/inc [41])
    (trabajo.core/start :my-queue)

### Redis

Update the `trabajo.core/redis-conf` atom to point to your desired Redis instance. There also is a `with-redis` macro that takes advantage of the information in `redis-conf` when creating a client connection.

### Rudimentary Testing

There is a `trabajo.core/test-work` function that takes a single argument and appends text to a file. After updating the file location of `job.out`, you can use this function as, or part of, your jobs. Use `tail -f job.out` to follow the output as it is written.

Here is an example with two worker threads, a quick job, and a time consuming job:

    user=> (defn long-job [] (Thread/sleep 30000) (trabajo.core/test-work "Finished!"))
    #'user/long-job
    user=> (trabajo.core/enqueue :b 'user/long-job [])
    1
    user=> (trabajo.core/enqueue :b 'trabajo.core/test-work ["Quicky."])
    2
    user=> (trabajo.core/start :b)
    nil

The contents of `job.out` will be:

    Quickly.
    Finished!

## License

Copyright © 2012 Jeremy Heiler

Distributed under the Eclipse Public License, the same as Clojure.

