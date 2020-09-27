# mm-clj

A [metamath](http:us.metamath.org) parser written in Clojure.

... inspired by [twitchcoq](https://github.com/geohot/twitchcoq) from [@geohot](https://github.com/geohot)

## Usage

1. To just parse a single Metamath file

    lein run <filename>

E.g.

    lein run mm/miu2.mm
    lein run mm/simple.mm
    lein run mm/simple2.mm
    lein run mm/nothing2zero.mm
    lein run mm/twoplustwo.mm
    lein run mm/peano.mm
    lein run mm/set.mm

1. To start a web server and interactively display the content of Metamath files

```
lein do clean, run
```

The application will now be available at [http://localhost:3000](http://localhost:3000).

## Run tests

    lein test

## Notes

- [Performance was greatly improved](PerformanceNotes.md) compared to the initial implementation:
  - we can now parse the whole `set.mm` file (no more `OutOfMemory` exception)
  - all proofs can be verified in a reasonable amount of time
  - used Peter Taoussanis' [`tufte`](https://github.com/ptaoussanis/tufte) library for profiling and it helped a lot
  - we are still nowhere compared to the Rust implementation ( https://github.com/japonophile/minimast )
  - parsing: `4.14 min`, proof verification: `1.67 min`

## Development mode

To start the Figwheel compiler, navigate to the project folder and run the following command in the terminal:

```
lein figwheel
```

Figwheel will automatically push cljs changes to the browser. The server will be available at [http://localhost:3449](http://localhost:3449) once Figwheel starts up. 

Figwheel also starts `nREPL` using the value of the `:nrepl-port` in the `:figwheel`
config found in `project.clj`. By default the port is set to `7002`.

The figwheel server can have unexpected behaviors in some situations such as when using
websockets. In this case it's recommended to run a standalone instance of a web server as follows:

```
lein do clean, run
```

The application will now be available at [http://localhost:3000](http://localhost:3000).

### Optional development tools

Start the browser REPL:

```
$ lein repl
```
The Jetty server can be started by running:

```clojure
(start-server)
```
and stopped by running:
```clojure
(stop-server)
```

## Building for release

```
lein do clean, uberjar
```

## License

Copyright © 2020 Antoine Choppin ( Eclipse Public License 2.0 http://www.eclipse.org/legal/epl-2.0 )

