# Trabajo

Trabajo is a Redis-backed library for creating background jobs with Clojure.

## Usage

The two main functions you need to know are `enqueue` and `work-on`.

    (trabajo/enqueue :my-queue 'clojure.core/inc [41])
    (trabajo/work-on :my-queue)

## License

Copyright © 2012 Jeremy Heiler

Distributed under the Eclipse Public License, the same as Clojure.

