# mm-clj

A [metamath](http:us.metamath.org) parser written in Clojure.

... inspired by [twitchcoq](https://github.com/geohot/twitchcoq) from [@geohot](https://github.com/geohot)

## Usage

    lein run <filename>

E.g.

    lein run mm/miu2.mm
    lein run mm/simple.mm
    lein run mm/simple2.mm
    lein run mm/nothing2zero.mm
    lein run mm/twoplustwo.mm
    lein run mm/lib/peano.mm
    lein run mm/lib/set.mm

## Run tests

    lein test

## Notes

- [Performance was greatly improved](PerformanceNotes.md) compared to the initial implementation:
  - we can now parse the whole `set.mm` file (no more `OutOfMemory` exception)
  - all proofs can be verified in a reasonable amount of time
  - used Peter Taoussanis' [`tufte`](https://github.com/ptaoussanis/tufte) library for profiling and it helped a lot
  - we are still nowhere compared to the Rust implementation ( https://github.com/japonophile/minimast )
  - parsing: `4.14 min`, proof verification: `1.67 min`


## License

Copyright © 2020 Antoine Choppin ( Eclipse Public License 2.0 http://www.eclipse.org/legal/epl-2.0 )
