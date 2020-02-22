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
    lein run mm/lib/set2.mm

## Run tests

    lein test

## License

Copyright © 2020 Antoine Choppin

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
