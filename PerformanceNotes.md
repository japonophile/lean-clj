# Performance notes

## Summary

### set2.mm

Started performance optimization with `set2.mm`, a reduced version of `set.mm`

- pulled mandatory hypotheses into assertion scope (`set2.mm`, `6.78m` -> `5.78m`)
- pulled mandatory variables into assertion scope (`set2.mm`, `5.78m` -> `4.92m`)
- pulled mandatory disjoints into assertion scope (`set2.mm`, `4.92m` -> `4.52m`)
- replaced `(some #{x} coll)` by `(contains? coll x)` (`set2.mm`, `4.52m` -> `3.94m`)
- used a hashmap for labels rather than a vector (`set2.mm`, `3.94m` -> `3.49m`)

### set.mm

Continued on with `set.mm`

- used a hashmap for labels rather than a vector (`set.mm`, parsing `` -> `68.84m`, proof verification `` -> `61.08m`)
- replaced `(some #{x} coll)` by `(contains? coll x)` (`set.mm`, parsing `68.84m` -> `60.84m`, proof verification `61.08m` -> `3.31m`)
- used hashmap for labels instead of vector (`set.mm`, parsing `60.84m` -> `4.16m`, proof verification `3.31m` -> `1.66m`)
- refactored `parse-mm-program-by-blocks` (`set.mm`, parsing: `4.16m` -> `4.15m`)


## Details

(latest result first)

### set.mm

#### after refactoring parse-mm-program-by-blocks a bit

- parsing:

```
pId                                              nCalls        Min      50% ≤      90% ≤      95% ≤      99% ≤        Max       Mean   MAD      Clock  Total

:mm-clj.core/defn_parse-mm-program                    1     4.13m      4.13m      4.13m      4.13m      4.13m      4.13m      4.13m    ±0%     4.13m     99%
:mm-clj.core/defn_parse-mm-program-by-blocks          1     3.61m      3.61m      3.61m      3.61m      3.61m      3.61m      3.61m    ±0%     3.61m     87%
:mm-clj.core/defn_check-program                 147,429     1.43μs    56.93μs   731.75μs     1.42ms     4.07ms    31.15s    663.27μs ±148%     1.63m     39%
:mm-clj.core/defn_check-block-stmt               17,187    14.48μs   648.75μs     3.33ms     5.39ms    15.97ms     1.14s      2.12ms ±113%    36.47s     15%
:mm-clj.core/defn_check-provable-stmt            33,601    17.61μs   296.18μs     1.24ms     1.82ms     3.43ms     1.13s    713.60μs ±100%    23.98s     10%
:mm-clj.core/defn_check-assertion-stmt           35,926     8.51μs   164.33μs   588.64μs     1.13ms     2.81ms   721.50ms   360.93μs  ±98%    12.97s      5%
:mm-clj.core/defn_check-proof                    33,601     4.50μs    85.95μs   500.94μs   814.22μs     1.79ms     1.13s    332.42μs ±120%    11.17s      4%
:mm-clj.core/defn_check-compressed-proof         33,601     2.95μs    83.77μs   497.86μs   811.41μs     1.78ms     1.13s    326.03μs ±120%    10.95s      4%
:mm-clj.core/defn_decompress-proof               33,601     2.19μs    80.64μs   470.45μs   776.14μs     1.72ms     1.13s    314.75μs ±122%    10.58s      4%
:mm-clj.core/defn_check-variables-have-type      78,947   381.00ns    46.42μs   205.21μs   331.35μs     1.16ms   721.27ms   127.38μs ±104%    10.06s      4%
:mm-clj.core/defn_decode-proof-chars_1           33,601   833.00ns    39.33μs   252.43μs   438.59μs     1.01ms     1.13s    162.39μs ±123%     5.46s      2%
:mm-clj.core/defn_check-essential-stmt           43,021     4.70μs    41.51μs   182.43μs   241.55μs   567.20μs   138.57ms    91.85μs  ±88%     3.95s      2%
:mm-clj.core/defn_mandatory-hypotheses           35,926     1.03μs    45.80μs   194.76μs   460.21μs   962.93μs   104.90ms   106.14μs ±100%     3.81s      2%
:tufte/compaction                                    10   134.11ms   208.05ms   238.03ms   347.04ms   347.04ms   347.04ms   198.40ms  ±23%     1.98s      1%
:mm-clj.core/defn_check-disjoint-stmt            49,561     6.39μs    19.55μs    68.92μs    96.56μs   201.82μs   104.96ms    34.77μs  ±77%     1.72s      1%
:mm-clj.core/defn_num-to-label                7,394,429    57.00ns   219.00ns   250.00ns   261.00ns   338.00ns    97.11ms   226.23ns  ±31%     1.67s      1%
:mm-clj.core/defn_read-file_1                         1     1.43s      1.43s      1.43s      1.43s      1.43s      1.43s      1.43s    ±0%     1.43s      1%
:mm-clj.core/defn_read-file_2                         1     1.43s      1.43s      1.43s      1.43s      1.43s      1.43s      1.43s    ±0%     1.43s      1%
:mm-clj.core/defn_check-symbols                  78,947   580.00ns     4.00μs    12.61μs    17.20μs    31.86μs   642.02ms    14.84μs ±125%     1.17s      0%
:mm-clj.core/defn_mandatory-variables            35,926     1.22μs    10.74μs    34.98μs    49.51μs    89.65μs    75.45ms    18.75μs  ±77%   673.72ms     0%
:mm-clj.core/defn_mandatory-disjoints            35,926   556.00ns     4.65μs    41.28μs    71.20μs   179.90μs     2.88ms    16.55μs ±113%   594.51ms     0%
:mm-clj.core/defn_strip-comments_1                    1   589.10ms   589.10ms   589.10ms   589.10ms   589.10ms   589.10ms   589.10ms   ±0%   589.10ms     0%
:mm-clj.core/defn_add-disjoint                  326,775   978.00ns     1.40μs     2.18μs     2.51μs     3.90μs   222.17μs     1.56μs  ±27%   508.31ms     0%
:mm-clj.core/defn_check-axiom-stmt                2,325     8.80μs    43.78μs   384.69μs   587.41μs     1.32ms    64.66ms   191.75μs ±114%   445.81ms     0%
:mm-clj.core/defn_load-includes                       1   361.07ms   361.07ms   361.07ms   361.07ms   361.07ms   361.07ms   361.07ms   ±0%   361.07ms     0%
:mm-clj.core/defn_add-label                      79,299     1.12μs     2.19μs     3.02μs     3.67μs     6.58μs    42.27ms     2.97μs  ±50%   235.80ms     0%
:mm-clj.core/defn_check-variables-unique         49,561     2.03μs     3.78μs     7.63μs    10.16μs    18.24μs   542.16μs     4.74μs  ±43%   235.12ms     0%
:mm-clj.core/defn_find-block                     14,714   498.00ns     3.22μs    10.02μs    16.92μs    46.83μs   483.49μs     5.60μs  ±78%    82.40ms     0%
:mm-clj.core/defn_check-variable-active         177,147   196.00ns   242.00ns   390.00ns   453.00ns   713.00ns    33.46μs   297.65ns  ±28%    52.73ms     0%
:mm-clj.core/defn_add-constant                    1,110   933.00ns     2.02μs     4.81μs     5.77μs    14.76μs   247.14μs     2.92μs  ±56%     3.24ms     0%
:mm-clj.core/defn_check-floating-stmt               352     5.38μs     6.25μs     8.17μs    14.68μs    27.21μs   426.69μs     8.54μs  ±48%     3.01ms     0%
:mm-clj.core/defn_add-variable                      352     2.15μs     2.74μs     3.89μs     5.67μs    16.21μs   194.83μs     3.80μs  ±48%     1.34ms     0%
:mm-clj.core/defn_set-active-variable-type          352     2.44μs     2.77μs     3.37μs     5.03μs    17.72μs   173.00μs     3.65μs  ±44%     1.29ms     0%
:mm-clj.core/defn_get-active-variable-type          352   892.00ns     1.04μs     1.40μs     1.96μs     2.82μs    83.99μs     1.44μs  ±49%   505.87μs     0%

Accounted                                                                                                                                     11.74m    283%
Clock                                                                                                                                          4.15m    100%
```

#### after using hashmap for labels instead of vector

- parsing:

```
pId                                              nCalls        Min      50% ≤      90% ≤      95% ≤      99% ≤        Max       Mean   MAD      Clock  Total

:mm-clj.core/defn_parse-mm-program                    1     4.14m      4.14m      4.14m      4.14m      4.14m      4.14m      4.14m    ±0%     4.14m    100%
:mm-clj.core/defn_parse-mm-program-by-blocks          1     3.65m      3.65m      3.65m      3.65m      3.65m      3.65m      3.65m    ±0%     3.65m     88%
:mm-clj.core/defn_check-program                 147,429     1.45μs    55.58μs   701.01μs     1.37ms     3.96ms    29.79s    614.87μs ±146%     1.51m     36%
:mm-clj.core/defn_check-block-stmt               17,187    13.81μs   624.52μs     3.23ms     5.22ms    15.67ms   966.57ms     1.86ms ±109%    31.98s     13%
:mm-clj.core/defn_check-provable-stmt            33,601    17.35μs   286.38μs     1.21ms     1.77ms     3.41ms     1.01s    682.55μs  ±99%    22.93s      9%
:mm-clj.core/defn_check-assertion-stmt           35,926     8.69μs   160.09μs   573.63μs     1.12ms     2.86ms   107.24ms   325.96μs  ±93%    11.71s      5%
:mm-clj.core/defn_check-proof                    33,601     4.35μs    81.67μs   472.94μs   764.54μs     1.66ms     1.01s    339.17μs ±123%    11.40s      5%
:mm-clj.core/defn_check-compressed-proof         33,601     2.79μs    79.46μs   469.38μs   761.41μs     1.65ms     1.01s    336.46μs ±124%    11.31s      5%
:mm-clj.core/defn_decompress-proof               33,601     2.05μs    76.51μs   442.44μs   731.80μs     1.59ms     1.01s    326.05μs ±126%    10.96s      4%
:mm-clj.core/defn_check-variables-have-type      78,947   397.00ns    45.47μs   201.61μs   327.12μs     1.16ms   102.60ms   117.55μs ±101%     9.28s      4%
:mm-clj.core/defn_decode-proof-chars_1           33,601   795.00ns    35.87μs   230.56μs   406.66μs   921.86μs     1.01s    135.24μs ±119%     4.54s      2%
:mm-clj.core/defn_mandatory-hypotheses           35,926     1.09μs    45.12μs   193.37μs   458.73μs   976.48μs   107.03ms   111.56μs ±105%     4.01s      2%
:mm-clj.core/defn_check-essential-stmt           43,021     4.67μs    40.48μs   180.18μs   235.79μs   563.20μs    91.44ms    89.00μs  ±87%     3.83s      2%
:tufte/compaction                                    10   131.49ms   208.18ms   250.13ms   882.81ms   882.81ms   882.81ms   254.52ms  ±49%     2.55s      1%
:mm-clj.core/defn_check-disjoint-stmt            49,561     6.39μs    18.93μs    65.83μs    93.56μs   196.26μs    82.57ms    34.60μs  ±79%     1.71s      1%
:mm-clj.core/defn_num-to-label                7,394,429    61.00ns   217.00ns   246.00ns   254.00ns   326.00ns     1.08ms   207.90ns  ±22%     1.54s      1%
:mm-clj.core/defn_read-file_1                         1     1.18s      1.18s      1.18s      1.18s      1.18s      1.18s      1.18s    ±0%     1.18s      0%
:mm-clj.core/defn_read-file_2                         1     1.18s      1.18s      1.18s      1.18s      1.18s      1.18s      1.18s    ±0%     1.18s      0%
:mm-clj.core/defn_mandatory-disjoints            35,926   566.00ns     4.47μs    40.05μs    70.34μs   177.20μs    70.79ms    17.99μs ±118%   646.47ms     0%
:mm-clj.core/defn_mandatory-variables            35,926     1.16μs    10.35μs    33.50μs    47.56μs    85.96μs     1.93ms    16.03μs  ±69%   575.95ms     0%
:mm-clj.core/defn_add-disjoint                  326,775   963.00ns     1.37μs     2.14μs     2.45μs     3.15μs   791.28μs     1.50μs  ±26%   489.97ms     0%
:mm-clj.core/defn_check-symbols                  78,947   603.00ns     3.89μs    12.02μs    16.29μs    31.00μs     2.81ms     6.01μs  ±63%   474.14ms     0%
:mm-clj.core/defn_check-axiom-stmt                2,325     9.00μs    42.71μs   375.43μs   582.95μs     1.29ms     2.94ms   145.64μs ±105%   338.62ms     0%
:mm-clj.core/defn_load-includes                       1   332.66ms   332.66ms   332.66ms   332.66ms   332.66ms   332.66ms   332.66ms   ±0%   332.66ms     0%
:mm-clj.core/defn_strip-comments_1                    1   312.22ms   312.22ms   312.22ms   312.22ms   312.22ms   312.22ms   312.22ms   ±0%   312.22ms     0%
:mm-clj.core/defn_check-variables-unique         49,561     1.91μs     3.60μs     7.33μs     9.18μs    16.19μs   452.52μs     4.47μs  ±42%   221.57ms     0%
:mm-clj.core/defn_find-block                     14,714   429.00ns     3.36μs    18.77μs    27.12μs    69.23μs    18.96ms    14.33μs ±125%   210.89ms     0%
:mm-clj.core/defn_add-label                      79,299     1.08μs     2.07μs     2.87μs     3.46μs     6.51μs   199.18μs     2.32μs  ±26%   183.97ms     0%
:mm-clj.core/defn_check-variable-active         177,147   200.00ns   243.00ns   375.00ns   419.00ns   584.00ns   209.90μs   287.03ns  ±25%    50.85ms     0%
:mm-clj.core/defn_add-constant                    1,110   922.00ns     1.91μs     4.26μs     5.03μs     9.34μs   257.45μs     2.67μs  ±54%     2.97ms     0%
:mm-clj.core/defn_check-floating-stmt               352     5.24μs     6.18μs     7.99μs    13.66μs    21.01μs   429.43μs     8.25μs  ±45%     2.91ms     0%
:mm-clj.core/defn_add-variable                      352     2.13μs     2.66μs     3.71μs     5.52μs    13.86μs   195.28μs     3.66μs  ±46%     1.29ms     0%
:mm-clj.core/defn_set-active-variable-type          352     2.47μs     2.72μs     3.34μs     5.08μs     8.04μs   171.56μs     3.53μs  ±40%     1.24ms     0%
:mm-clj.core/defn_get-active-variable-type          352   930.00ns     1.04μs     1.38μs     1.92μs     2.72μs    82.32μs     1.38μs  ±42%   484.20μs     0%

Accounted                                                                                                                                     11.53m    277%
Clock                                                                                                                                          4.16m    100%
```

- proof verification:

```
pId                                               nCalls        Min      50% ≤      90% ≤      95% ≤      99% ≤        Max       Mean   MAD      Clock  Total

:mm-clj.core/defn_verify-proofs                        1     1.66m      1.66m      1.66m      1.66m      1.66m      1.66m      1.66m    ±0%     1.66m    100%
:mm-clj.core/defn_verify-proof_2                  33,601     8.29μs     1.05ms     6.47ms    10.32ms    26.18ms     1.18s      2.96ms ±106%     1.66m    100%
:mm-clj.core/defn_verify-proof_5                  33,601     1.37μs     1.02ms     6.43ms    10.28ms    25.98ms     1.18s      2.93ms ±107%     1.64m     99%
:mm-clj.core/defn_apply-axiom                  2,503,272     2.88μs    18.73μs    60.17μs    82.23μs   197.48μs     1.16s     35.47μs  ±85%     1.48m     89%
:mm-clj.core/defn_check-disjoint-restrictions  2,503,272   462.00ns     4.22μs    27.47μs    39.80μs   121.99μs   351.22ms    14.03μs ±105%    35.11s     35%
:mm-clj.core/find-substitutions                2,503,272   371.00ns     2.55μs    18.48μs    25.91μs    55.88μs   716.73ms     9.00μs ±112%    22.52s     23%
:mm-clj.core/defn_apply-substitutions          3,766,958   725.00ns     3.92μs     9.33μs    13.03μs    25.05μs   716.70ms     5.75μs  ±67%    21.66s     22%
:mm-clj.core/defn_check-disjoint-restriction   7,303,758   445.00ns   546.00ns   960.00ns     6.14μs    15.12μs   146.63ms     1.33μs  ±99%     9.72s     10%
:tufte/compaction                                     23   120.54ms   150.59ms   350.01ms   367.93ms   436.17ms   436.17ms   208.64ms  ±35%     4.80s      5%
:mm-clj.core/defn_check-expressions-disjoint     445,204   864.00ns     4.47μs    14.79μs    22.63μs    51.60μs    48.83ms     7.86μs  ±70%     3.50s      4%

Accounted                                                                                                                                       8.06m    485%
Clock                                                                                                                                           1.66m    100%
```

#### after replacing `(some #{x} coll)` by `(contains? coll x)`

- parsing:

```
pId                                              nCalls        Min      50% ≤      90% ≤      95% ≤      99% ≤        Max       Mean   MAD      Clock  Total

:mm-clj.core/defn_check-program                 147,429     1.27μs   525.47μs    79.98ms   235.17ms   975.75ms    57.06m     79.32ms ±170%   194.90m    320%
:mm-clj.core/defn_check-block-stmt               17,187    48.64μs    33.93ms   546.77ms     1.06s      4.02s     55.32s    282.35ms ±133%    80.88m    133%
:mm-clj.core/defn_parse-mm-program                    1    60.81m     60.81m     60.81m     60.81m     60.81m     60.81m     60.81m    ±0%    60.81m    100%
:mm-clj.core/defn_check-provable-stmt            33,601    26.82μs    10.71ms   263.52ms   531.26ms     1.20s      3.82s     99.42ms ±131%    55.68m     92%
:mm-clj.core/defn_check-assertion-stmt           35,926    12.57μs     8.68ms   246.10ms   500.03ms     1.15s      3.82s     92.88ms ±133%    55.61m     91%
:mm-clj.core/defn_mandatory-hypotheses           35,926     1.07μs     5.99ms   239.57ms   490.40ms     1.13s      3.82s     89.65ms ±136%    53.68m     88%
:mm-clj.core/defn_parse-mm-program-by-blocks          1     3.75m      3.75m      3.75m      3.75m      3.75m      3.75m      3.75m    ±0%     3.75m      6%
:mm-clj.core/defn_check-symbols                  78,947     1.38μs   683.95μs     3.47ms     5.52ms    16.63ms     2.11s      1.81ms ±106%     2.38m      4%
:mm-clj.core/defn_check-essential-stmt           43,021     6.88μs   666.80μs     2.33ms     3.46ms     7.38ms     4.84s      1.48ms  ±97%     1.06m      2%
:mm-clj.core/defn_check-variables-have-type      78,947   457.00ns    52.97μs   256.95μs   441.71μs     1.62ms     4.84s    212.04μs ±123%    16.74s      0%
:mm-clj.core/defn_check-proof                    33,601     5.11μs   101.95μs   574.20μs   925.88μs     2.07ms   834.54ms   350.55μs ±113%    11.78s      0%
:mm-clj.core/defn_check-compressed-proof         33,601     3.70μs    98.21μs   567.70μs   919.20μs     2.05ms   834.53ms   346.48μs ±114%    11.64s      0%
:mm-clj.core/defn_decompress-proof               33,601     2.80μs    90.10μs   487.76μs   804.87μs     1.78ms   834.47ms   304.12μs ±116%    10.22s      0%
:mm-clj.core/defn_check-axiom-stmt                2,325    15.12μs   838.91μs    10.11ms    14.65ms    30.06ms   233.03ms     3.58ms ±116%     8.31s      0%
:mm-clj.core/defn_check-disjoint-stmt            49,561     8.85μs    60.68μs   167.08μs   225.24μs   422.39μs   144.55ms    97.93μs  ±72%     4.85s      0%
:mm-clj.core/defn_decode-proof-chars_1           33,601     1.07μs    41.76μs   253.60μs   439.49μs     1.00ms   130.73ms   112.22μs ±104%     3.77s      0%
:tufte/compaction                                    10   132.44ms   244.50ms   432.95ms   834.06ms   834.06ms   834.06ms   287.21ms  ±55%     2.87s      0%
:mm-clj.core/defn_check-variable-active         177,147   484.00ns     9.23μs    23.70μs    40.65μs    77.47μs   144.39ms    14.56μs  ±81%     2.58s      0%
:mm-clj.core/defn_read-file_1                         1     1.64s      1.64s      1.64s      1.64s      1.64s      1.64s      1.64s    ±0%     1.64s      0%
:mm-clj.core/defn_read-file_2                         1     1.64s      1.64s      1.64s      1.64s      1.64s      1.64s      1.64s    ±0%     1.64s      0%
:mm-clj.core/defn_num-to-label                7,394,429    61.00ns   219.00ns   254.00ns   269.00ns   477.00ns     1.36ms   221.00ns  ±25%     1.63s      0%
:mm-clj.core/defn_mandatory-variables            35,926     1.30μs    18.11μs    49.95μs    65.97μs   117.47μs    14.07ms    25.84μs  ±68%   928.34ms     0%
:mm-clj.core/defn_mandatory-disjoints            35,926   531.00ns     6.15μs    49.95μs    84.70μs   215.98μs     3.17ms    20.41μs ±111%   733.10ms     0%
:mm-clj.core/defn_add-disjoint                  326,775   969.00ns     1.46μs     2.43μs     3.54μs     8.39μs    43.92ms     1.99μs  ±53%   651.12ms     0%
:mm-clj.core/defn_strip-comments_1                    1   561.18ms   561.18ms   561.18ms   561.18ms   561.18ms   561.18ms   561.18ms   ±0%   561.18ms     0%
:mm-clj.core/defn_check-variables-unique         49,561     2.08μs     4.64μs    12.89μs    15.34μs    25.44μs     9.87ms     8.10μs  ±71%   401.29ms     0%
:mm-clj.core/defn_find-block                     14,714   178.00ns     3.56μs    24.87μs    38.20μs   131.19μs    14.15ms    26.15μs ±137%   384.72ms     0%
:mm-clj.core/defn_load-includes                       1   350.18ms   350.18ms   350.18ms   350.18ms   350.18ms   350.18ms   350.18ms   ±0%   350.18ms     0%
:mm-clj.core/defn_add-label                      79,299   521.00ns     1.17μs     3.25μs     3.78μs     6.63μs     3.70ms     1.87μs  ±61%   148.37ms     0%
:mm-clj.core/defn_check-floating-stmt               352     5.48μs    22.19μs    47.81μs    59.96μs   109.74μs   615.34μs    28.74μs  ±58%    10.12ms     0%
:mm-clj.core/defn_set-active-variable-type          352     3.16μs    17.47μs    43.63μs    53.15μs    78.39μs   189.16μs    22.83μs  ±59%     8.04ms     0%
:mm-clj.core/defn_add-constant                    1,110   736.00ns     2.09μs     6.20μs     7.18μs    16.64μs     2.67ms     6.50μs ±111%     7.21ms     0%
:mm-clj.core/defn_get-active-variable-type          352     1.48μs    15.26μs    41.39μs    49.42μs    75.59μs   100.97μs    20.16μs  ±64%     7.10ms     0%
:mm-clj.core/defn_add-variable                      352     2.09μs     2.50μs     4.55μs     8.41μs    20.44μs     3.48ms    16.02μs ±158%     5.64ms     0%

Accounted                                                                                                                                    510.11m    838%
Clock                                                                                                                                         60.84m    100%
```

- proof verification:

```
pId                                               nCalls        Min      50% ≤      90% ≤      95% ≤      99% ≤        Max       Mean   MAD      Clock  Total

:mm-clj.core/defn_verify-proofs                        1     3.31m      3.31m      3.31m      3.31m      3.31m      3.31m      3.31m    ±0%     3.31m    100%
:mm-clj.core/defn_verify-proof_2                  33,601     8.63μs     1.40ms    10.33ms    17.82ms    53.15ms     7.08s      5.89ms ±122%     3.30m    100%
:mm-clj.core/defn_verify-proof_5                  33,601     1.56μs     1.35ms    10.20ms    17.61ms    52.32ms     7.08s      5.78ms ±122%     3.24m     98%
:mm-clj.core/defn_apply-axiom                  2,503,272     2.88μs    23.44μs    84.66μs   132.41μs   448.91μs     7.07s     68.69μs ±112%     2.87m     87%
:mm-clj.core/defn_check-disjoint-restrictions  2,503,272   442.00ns     4.91μs    31.74μs    52.28μs   244.52μs     1.68s     27.18μs ±128%     1.13m     34%
:mm-clj.core/defn_apply-substitutions          3,766,958   738.00ns     4.58μs    13.77μs    19.65μs    43.21μs     1.94s      9.62μs  ±93%    36.22s     18%
:mm-clj.core/find-substitutions                2,503,272   336.00ns     3.12μs    24.03μs    36.03μs    96.91μs     1.22s     13.43μs ±117%    33.62s     17%
:mm-clj.core/defn_check-disjoint-restriction   7,303,758   455.00ns   575.00ns     1.41μs     9.69μs    40.04μs   913.41ms     3.43μs ±148%    25.03s     13%
:mm-clj.core/defn_check-expressions-disjoint     445,204   904.00ns    12.36μs    50.16μs    82.22μs   300.79μs   913.24ms    34.99μs ±108%    15.58s      8%
:tufte/compaction                                     23   145.57ms   205.02ms   802.90ms   810.99ms     4.30s      4.30s    488.47ms  ±82%    11.23s      6%

Accounted                                                                                                                                      15.87m    479%
Clock                                                                                                                                           3.31m    100%
```

#### original

- parsing:

```
pId                                              nCalls        Min      50% ≤      90% ≤      95% ≤      99% ≤        Max       Mean   MAD      Clock  Total

:mm-clj.core/defn_check-program                 147,429     6.45μs     4.71ms    92.74ms   252.38ms     1.02s     64.82m     88.96ms ±163%   218.59m    318%
:mm-clj.core/defn_check-block-stmt               17,187    75.45μs    50.54ms   614.25ms     1.16s      4.13s      1.11m    310.95ms ±128%    89.07m    129%
:mm-clj.core/defn_parse-mm-program                    1    68.82m     68.82m     68.82m     68.82m     68.82m     68.82m     68.82m    ±0%    68.82m    100%
:mm-clj.core/defn_check-assertion-stmt           35,926    84.47μs    15.96ms   249.21ms   501.39ms     1.15s      4.32s     97.70ms ±127%    58.50m     85%
:mm-clj.core/defn_check-provable-stmt            33,601   118.07μs    18.01ms   265.55ms   530.47ms     1.18s      4.32s    104.17ms ±125%    58.34m     85%
:mm-clj.core/defn_mandatory-hypotheses           35,926     1.48μs     6.30ms   230.86ms   477.07ms     1.10s      4.28s     87.65ms ±135%    52.48m     76%
:mm-clj.core/defn_add-label                      79,299     4.85μs     5.63ms    10.90ms    11.92ms    16.01ms     1.91s      6.03ms  ±59%     7.97m     12%
:mm-clj.core/defn_check-essential-stmt           43,021    27.20μs     7.49ms    13.48ms    15.05ms    22.34ms   419.52ms     7.88ms  ±54%     5.65m      8%
:mm-clj.core/defn_parse-mm-program-by-blocks          1     4.01m      4.01m      4.01m      4.01m      4.01m      4.01m      4.01m    ±0%     4.01m      6%
:mm-clj.core/defn_check-symbols                  78,947     2.74μs   655.60μs     3.16ms     4.88ms    13.04ms     2.31s      1.49ms  ±99%     1.96m      3%
:mm-clj.core/defn_mandatory-variables            35,926     4.68μs   474.98μs     2.52ms     4.81ms    13.55ms   272.28ms     1.24ms ±105%    44.73s      1%
:mm-clj.core/defn_check-variables-have-type      78,947     1.98μs   255.63μs   926.24μs     1.50ms     4.72ms   259.72ms   508.49μs  ±87%    40.14s      1%
:mm-clj.core/defn_check-axiom-stmt                2,325    95.61μs     7.38ms    19.32ms    26.06ms    50.48ms     2.34s     10.89ms  ±72%    25.32s      1%
:mm-clj.core/defn_check-proof                    33,601     5.73μs   105.15μs   587.64μs   949.02μs     2.21ms     2.72s    437.11μs ±122%    14.69s      0%
:mm-clj.core/defn_check-compressed-proof         33,601     4.20μs   100.90μs   580.93μs   939.86μs     2.18ms     2.72s    432.03μs ±123%    14.52s      0%
:mm-clj.core/defn_decompress-proof               33,601     3.17μs    91.70μs   493.52μs   818.87μs     1.81ms     2.72s    381.04μs ±126%    12.80s      0%
:mm-clj.core/defn_check-disjoint-stmt            49,561    10.23μs    69.72μs   221.74μs   349.22μs   985.70μs    48.36ms   126.73μs  ±81%     6.28s      0%
:mm-clj.core/defn_add-constant                    1,110     3.70μs     4.82ms     9.89ms    11.05ms    14.31ms    87.72ms     5.60ms  ±55%     6.22s      0%
:tufte/compaction                                    10   131.54ms   229.49ms   450.16ms     2.72s      2.72s      2.72s    478.01ms  ±94%     4.78s      0%
:mm-clj.core/defn_decode-proof-chars_1           33,601     1.25μs    42.34μs   253.74μs   445.03μs     1.03ms   402.41ms   135.01μs ±112%     4.54s      0%
:mm-clj.core/defn_add-disjoint                  326,775     1.32μs     4.63μs    14.82μs    20.83μs    39.21μs     9.45ms     7.48μs  ±69%     2.45s      0%
:mm-clj.core/defn_mandatory-disjoints            35,926   657.00ns    11.50μs   128.52μs   247.28μs   840.61μs    66.39ms    67.20μs ±130%     2.41s      0%
:mm-clj.core/defn_add-variable                      352     9.86μs     8.45ms     9.62ms    10.03ms    10.93ms    18.32ms     6.57ms  ±52%     2.31s      0%
:mm-clj.core/defn_check-floating-stmt               352    15.82μs     8.36ms     9.52ms     9.85ms    10.97ms    21.35ms     6.49ms  ±52%     2.28s      0%
:mm-clj.core/defn_check-variable-active         177,147   476.00ns     9.08μs    21.96μs    38.47μs    55.28μs    48.23ms    11.93μs  ±66%     2.11s      0%
:mm-clj.core/defn_num-to-label                7,394,429    61.00ns   218.00ns   252.00ns   265.00ns   487.00ns    27.64ms   228.51ns  ±29%     1.69s      0%
:mm-clj.core/defn_read-file_1                         1   998.19ms   998.19ms   998.19ms   998.19ms   998.19ms   998.19ms   998.19ms   ±0%   998.19ms     0%
:mm-clj.core/defn_read-file_2                         1   998.16ms   998.16ms   998.16ms   998.16ms   998.16ms   998.16ms   998.16ms   ±0%   998.16ms     0%
:mm-clj.core/defn_check-variables-unique         49,561     2.07μs     4.49μs    12.99μs    15.84μs    29.41μs    10.79ms     9.73μs  ±85%   482.34ms     0%
:mm-clj.core/defn_load-includes                       1   361.23ms   361.23ms   361.23ms   361.23ms   361.23ms   361.23ms   361.23ms   ±0%   361.23ms     0%
:mm-clj.core/defn_find-block                     14,714   210.00ns     3.66μs    21.27μs    31.84μs    90.68μs    18.47ms    17.37μs ±124%   255.64ms     0%
:mm-clj.core/defn_strip-comments_1                    1   226.60ms   226.60ms   226.60ms   226.60ms   226.60ms   226.60ms   226.60ms   ±0%   226.60ms     0%
:mm-clj.core/defn_set-active-variable-type          352     4.72μs    26.77μs    56.56μs    72.55μs    85.87μs     2.34ms    37.13μs  ±66%    13.07ms     0%
:mm-clj.core/defn_get-active-variable-type          352     2.07μs    21.03μs    51.14μs    64.53μs    78.54μs    98.15μs    25.26μs  ±60%     8.89ms     0%

Accounted                                                                                                                                    568.57m    826%
Clock                                                                                                                                         68.84m    100%
```

- proof verification:

```
pId                                               nCalls        Min      50% ≤      90% ≤      95% ≤      99% ≤        Max       Mean   MAD      Clock  Total

:mm-clj.core/defn_verify-proofs                        1    61.08m     61.08m     61.08m     61.08m     61.08m     61.08m     61.08m    ±0%    61.08m    100%
:mm-clj.core/defn_verify-proof_2                  33,601    10.11μs    47.36ms   269.05ms   418.28ms   875.85ms     5.57s    108.97ms  ±96%    61.02m    100%
:mm-clj.core/defn_verify-proof_5                  33,601     1.92μs    47.16ms   268.72ms   417.81ms   875.79ms     5.57s    108.70ms  ±96%    60.87m    100%
:mm-clj.core/defn_apply-axiom                  2,503,272     5.56μs   689.93μs     3.28ms     4.35ms     9.47ms     5.09s      1.44ms  ±90%    60.00m     98%
:mm-clj.core/defn_apply-substitutions          3,766,958     1.44μs   678.07μs     1.75ms     2.35ms     4.68ms     5.09s    923.07μs  ±61%    57.95m     95%
:mm-clj.core/find-substitutions                2,503,272   401.00ns     3.26μs     1.68ms     2.33ms     5.45ms     2.45s    499.03μs ±149%    20.82m     34%
:mm-clj.core/defn_check-disjoint-restrictions  2,503,272   458.00ns     7.43μs    40.93μs    61.45μs   219.38μs   739.63ms    26.47μs ±112%     1.10m      2%
:mm-clj.core/defn_check-disjoint-restriction   7,303,758   804.00ns     1.07μs     6.31μs    12.17μs    36.57μs   310.70ms     3.93μs ±115%    28.70s      1%
:mm-clj.core/defn_check-expressions-disjoint     445,204     1.06μs    10.85μs    37.46μs    58.11μs   180.93μs   310.66ms    25.36μs  ±96%    11.29s      0%
:tufte/compaction                                     23   135.97ms   201.37ms   574.03ms   735.42ms   754.90ms   754.90ms   294.41ms  ±52%     6.77s      0%

Accounted                                                                                                                                     323.64m    530%
Clock                                                                                                                                          61.08m    100%
```

### set2.mm

#### after using a hashmap for labels rather than a vector

- parsing:

```
pId                                              nCalls        Min      50% ≤      90% ≤      95% ≤      99% ≤        Max       Mean   MAD      Clock  Total

:mm-clj.core/defn_parse-mm-program                    1     3.47s      3.47s      3.47s      3.47s      3.47s      3.47s      3.47s    ±0%     3.47s     99%
:mm-clj.core/defn_parse-mm-program-by-blocks          1     3.33s      3.33s      3.33s      3.33s      3.33s      3.33s      3.33s    ±0%     3.33s     96%
:mm-clj.core/defn_check-program                   3,094     2.75μs    58.92μs   129.15μs   172.37μs   302.64μs   136.25ms   114.10μs ±100%   353.02ms    10%
:mm-clj.core/defn_check-provable-stmt             1,317    33.14μs    65.66μs   114.95μs   141.90μs   227.47μs     2.50ms    77.44μs  ±32%   101.99ms     3%
:mm-clj.core/defn_check-block-stmt                  625    48.83μs   105.66μs   223.05μs   275.33μs   622.80μs     3.74ms   146.95μs  ±51%    91.85ms     3%
:mm-clj.core/defn_check-assertion-stmt            1,333    18.49μs    44.14μs    78.58μs    93.85μs   144.87μs     1.58ms    51.84μs  ±31%    69.10ms     2%
:mm-clj.core/defn_check-proof                     1,317     6.32μs    16.44μs    32.66μs    41.54μs    83.05μs   739.53μs    21.02μs  ±43%    27.69ms     1%
:mm-clj.core/defn_check-compressed-proof          1,317     4.86μs    13.98μs    29.20μs    37.41μs    75.98μs   543.15μs    18.32μs  ±44%    24.13ms     1%
:mm-clj.core/defn_decompress-proof                1,317     3.99μs    12.19μs    26.45μs    33.46μs    72.84μs   441.46μs    16.27μs  ±46%    21.43ms     1%
:mm-clj.core/defn_mandatory-hypotheses            1,333     3.50μs    12.93μs    25.62μs    32.83μs    54.42μs   849.15μs    16.04μs  ±42%    21.38ms     1%
:mm-clj.core/defn_check-variables-have-type       2,433   925.00ns     6.95μs    13.52μs    17.67μs    31.45μs   308.10μs     8.46μs  ±47%    20.59ms     1%
:mm-clj.core/defn_read-file_1                         1    18.06ms    18.06ms    18.06ms    18.06ms    18.06ms    18.06ms    18.06ms   ±0%    18.06ms     1%
:mm-clj.core/defn_read-file_2                         1    18.00ms    18.00ms    18.00ms    18.00ms    18.00ms    18.00ms    18.00ms   ±0%    18.00ms     1%
:mm-clj.core/defn_check-essential-stmt            1,100     4.94μs    13.29μs    24.15μs    31.36μs    58.46μs   690.98μs    16.22μs  ±40%    17.84ms     1%
:mm-clj.core/defn_mandatory-variables             1,333     3.17μs     8.32μs    16.11μs    20.60μs    33.98μs   340.88μs    10.15μs  ±41%    13.53ms     0%
:mm-clj.core/defn_check-symbols                   2,433   727.00ns     3.66μs     6.30μs     7.23μs    16.02μs   198.14μs     4.19μs  ±42%    10.19ms     0%
:mm-clj.core/defn_decode-proof-chars_1            1,317     1.68μs     5.18μs    11.86μs    14.61μs    30.69μs   242.03μs     7.28μs  ±49%     9.59ms     0%
:mm-clj.core/defn_add-label                       2,445     1.20μs     1.66μs     3.89μs     4.63μs    12.98μs   116.62μs     2.33μs  ±48%     5.69ms     0%
:mm-clj.core/defn_num-to-label                   22,428    63.00ns   169.00ns   292.00ns   342.00ns     1.05μs    35.10μs   245.03ns  ±43%     5.50ms     0%
:mm-clj.core/defn_strip-comments_1                    1     3.71ms     3.71ms     3.71ms     3.71ms     3.71ms     3.71ms     3.71ms   ±0%     3.71ms     0%
:mm-clj.core/defn_load-includes                       1     2.90ms     2.90ms     2.90ms     2.90ms     2.90ms     2.90ms     2.90ms   ±0%     2.90ms     0%
:mm-clj.core/defn_find-block                        589   804.00ns     2.88μs     6.09μs     7.04μs    27.47μs   233.81μs     4.36μs  ±64%     2.57ms     0%
:mm-clj.core/defn_mandatory-disjoints             1,333   610.00ns   845.00ns     2.01μs     2.66μs    10.13μs   141.11μs     1.50μs  ±59%     2.00ms     0%
:mm-clj.core/defn_check-axiom-stmt                   16    31.58μs    47.40μs   127.28μs   127.28μs   153.51μs   153.51μs    71.74μs  ±51%     1.15ms     0%
:mm-clj.core/defn_check-floating-stmt                12    12.53μs    13.85μs    21.20μs    21.20μs   398.75μs   398.75μs    46.69μs ±126%   560.33μs     0%
:mm-clj.core/defn_add-constant                       11     1.48μs     2.76μs     7.29μs   245.50μs   245.50μs   245.50μs    25.06μs ±160%   275.64μs     0%
:mm-clj.core/defn_add-variable                       12     5.73μs     7.01μs     9.27μs     9.27μs   191.37μs   191.37μs    22.21μs ±127%   266.46μs     0%
:mm-clj.core/defn_set-active-variable-type           12     4.67μs     5.22μs     6.41μs     6.41μs   159.08μs   159.08μs    18.01μs ±131%   216.16μs     0%
:mm-clj.core/defn_get-active-variable-type           12     1.79μs     2.06μs     2.44μs     2.44μs    77.35μs    77.35μs     8.33μs ±138%    99.92μs     0%
:mm-clj.core/defn_check-variable-active              12   623.00ns   681.00ns   902.00ns   902.00ns    18.98μs    18.98μs     2.22μs ±126%    26.65μs     0%

Accounted                                                                                                                                      7.65s    219%
Clock                                                                                                                                          3.49s    100%
```

#### after replacing `(some #{x} coll)` by `(contains? coll x)`

```
pId                                              nCalls        Min      50% ≤      90% ≤      95% ≤      99% ≤        Max       Mean   MAD      Clock  Total

:mm-clj.core/defn_parse-mm-program                    1     3.91s      3.91s      3.91s      3.91s      3.91s      3.91s      3.91s    ±0%     3.91s     99%
:mm-clj.core/defn_parse-mm-program-by-blocks          1     3.21s      3.21s      3.21s      3.21s      3.21s      3.21s      3.21s    ±0%     3.21s     81%
:mm-clj.core/defn_check-program                   3,094     2.71μs   114.52μs   947.95μs     1.61ms     5.75ms   705.02ms   722.08μs ±134%     2.23s     57%
:mm-clj.core/defn_check-block-stmt                  625    95.29μs   473.54μs     2.23ms     3.95ms     7.46ms   203.81ms     1.35ms ±102%   840.78ms    21%
:mm-clj.core/defn_check-provable-stmt             1,317    44.85μs   236.39μs   890.32μs     1.85ms     5.75ms    11.51ms   492.03μs  ±94%   648.00ms    16%
:mm-clj.core/defn_check-assertion-stmt            1,333    30.28μs   198.68μs   872.82μs     1.59ms     5.73ms    11.47ms   462.14μs  ±99%   616.03ms    16%
:mm-clj.core/defn_mandatory-hypotheses            1,333     3.32μs   143.55μs   830.88μs     1.56ms     5.68ms    11.40ms   404.27μs ±114%   538.90ms    14%
:mm-clj.core/defn_check-symbols                   2,433     2.97μs    18.78μs    39.99μs    45.58μs    80.12μs     3.17ms    23.82μs  ±51%    57.97ms     1%
:mm-clj.core/defn_check-essential-stmt            1,100     6.66μs    25.34μs    45.44μs    58.37μs    84.04μs     3.35ms    31.99μs  ±49%    35.19ms     1%
:mm-clj.core/defn_check-proof                     1,317     6.35μs    16.62μs    33.47μs    42.97μs    74.73μs   569.64μs    20.89μs  ±41%    27.51ms     1%
:mm-clj.core/defn_read-file_1                         1    25.40ms    25.40ms    25.40ms    25.40ms    25.40ms    25.40ms    25.40ms   ±0%    25.40ms     1%
:mm-clj.core/defn_read-file_2                         1    25.37ms    25.37ms    25.37ms    25.37ms    25.37ms    25.37ms    25.37ms   ±0%    25.37ms     1%
:mm-clj.core/defn_check-compressed-proof          1,317     4.79μs    14.27μs    29.76μs    38.39μs    67.86μs   429.73μs    18.21μs  ±42%    23.98ms     1%
:mm-clj.core/defn_decompress-proof                1,317     3.96μs    12.35μs    26.25μs    34.54μs    64.43μs   358.40μs    16.06μs  ±44%    21.16ms     1%
:mm-clj.core/defn_check-variables-have-type       2,433   988.00ns     6.90μs    13.21μs    17.50μs    27.39μs   297.18μs     8.28μs  ±46%    20.15ms     1%
:mm-clj.core/defn_mandatory-variables             1,333     3.12μs     8.57μs    15.70μs    21.09μs    33.76μs   322.78μs    10.30μs  ±41%    13.72ms     0%
:mm-clj.core/defn_decode-proof-chars_1            1,317     1.57μs     5.17μs    12.13μs    14.47μs    30.55μs   198.39μs     7.19μs  ±49%     9.47ms     0%
:mm-clj.core/defn_strip-comments_1                    1     9.18ms     9.18ms     9.18ms     9.18ms     9.18ms     9.18ms     9.18ms   ±0%     9.18ms     0%
:mm-clj.core/defn_num-to-label                   22,428    62.00ns   169.00ns   304.00ns   337.00ns   737.00ns    28.19μs   230.92ns  ±38%     5.18ms     0%
:mm-clj.core/defn_add-label                       2,445   511.00ns   731.00ns     1.81μs     2.46μs     4.18μs    65.18μs     1.07μs  ±54%     2.61ms     0%
:mm-clj.core/defn_load-includes                       1     2.60ms     2.60ms     2.60ms     2.60ms     2.60ms     2.60ms     2.60ms   ±0%     2.60ms     0%
:mm-clj.core/defn_find-block                        589   780.00ns     2.58μs     5.58μs     6.63μs    22.18μs   169.09μs     3.85μs  ±63%     2.27ms     0%
:mm-clj.core/defn_mandatory-disjoints             1,333   654.00ns   854.00ns     1.95μs     2.53μs     5.74μs   136.50μs     1.44μs  ±57%     1.92ms     0%
:mm-clj.core/defn_check-axiom-stmt                   16    38.48μs    63.13μs   192.73μs   192.73μs   251.47μs   251.47μs    91.38μs  ±49%     1.46ms     0%
:mm-clj.core/defn_check-floating-stmt                12    12.97μs    15.92μs    33.83μs    33.83μs   323.08μs   323.08μs    43.22μs ±108%   518.65μs     0%
:mm-clj.core/defn_add-constant                       11     1.33μs     3.36μs     8.98μs   240.80μs   240.80μs   240.80μs    25.15μs ±156%   276.69μs     0%
:mm-clj.core/defn_add-variable                       12     5.87μs     7.87μs     9.50μs     9.50μs   185.66μs   185.66μs    22.42μs ±121%   269.03μs     0%
:mm-clj.core/defn_set-active-variable-type           12     5.88μs     8.30μs    11.28μs    11.28μs   171.02μs   171.02μs    21.59μs ±115%   259.14μs     0%
:mm-clj.core/defn_get-active-variable-type           12     3.03μs     5.20μs     7.21μs     7.21μs    90.56μs    90.56μs    11.96μs ±110%   143.55μs     0%
:mm-clj.core/defn_check-variable-active              12     1.78μs     3.80μs     5.42μs     5.42μs    32.91μs    32.91μs     5.92μs  ±76%    71.07μs     0%

Accounted                                                                                                                                     12.29s    312%
Clock                                                                                                                                          3.94s    100%
```

#### after pulling mandatory disjoints into assertion scope

```
pId                                               nCalls        Min      50% ≤      90% ≤      95% ≤      99% ≤        Max       Mean   MAD      Clock  Total

:mm-clj.core/defn_parse-mm-program                     1     4.12s      4.12s      4.12s      4.12s      4.12s      4.12s      4.12s    ±0%     4.12s     91%
:mm-clj.core/defn_check-program                    3,094     5.54μs   304.02μs     1.28ms     2.21ms     6.28ms     1.05s      1.03ms ±121%     3.20s     71%
:mm-clj.core/defn_parse-mm-program-by-blocks           1     3.07s      3.07s      3.07s      3.07s      3.07s      3.07s      3.07s    ±0%     3.07s     68%
:mm-clj.core/defn_check-block-stmt                   625   152.56μs   804.93μs     3.18ms     5.07ms     8.78ms   223.16ms     1.77ms  ±89%     1.11s     25%
:mm-clj.core/defn_check-provable-stmt              1,317   109.82μs   367.44μs     1.09ms     1.98ms     6.08ms    10.75ms   661.43μs  ±72%   871.11ms    19%
:mm-clj.core/defn_check-assertion-stmt             1,333    76.90μs   340.74μs     1.06ms     1.87ms     6.06ms    10.72ms   631.18μs  ±75%   841.36ms    19%
:mm-clj.core/defn_mandatory-hypotheses             1,333     3.57μs   141.53μs   819.63μs     1.58ms     5.60ms    10.33ms   398.96μs ±114%   531.82ms    12%
:mm-clj.core/defn_verify-proofs                        1   375.36ms   375.36ms   375.36ms   375.36ms   375.36ms   375.36ms   375.36ms   ±0%   375.36ms     8%
:mm-clj.core/defn_verify-proof_2                   1,317    10.25μs   214.19μs   457.45μs   598.70μs     1.02ms    12.46ms   281.86μs  ±50%   371.21ms     8%
:mm-clj.core/defn_verify-proof_5                   1,317     2.06μs   194.78μs   426.81μs   569.68μs   977.37μs    12.30ms   261.26μs  ±52%   344.08ms     8%
:mm-clj.core/defn_apply-axiom                      6,478     8.35μs    34.25μs    88.53μs   109.52μs   184.89μs    11.35ms    49.65μs  ±62%   321.64ms     7%
:mm-clj.core/defn_add-label                        2,445     4.55μs   106.29μs   196.88μs   208.38μs   244.34μs   376.80μs   109.58μs  ±49%   267.91ms     6%
:mm-clj.core/defn_apply-substitutions             10,164     2.60μs    11.97μs    28.38μs    34.83μs    56.62μs    11.33ms    17.35μs  ±50%   176.38ms     4%
:mm-clj.core/defn_check-essential-stmt             1,100    22.51μs   129.08μs   248.94μs   275.37μs   346.80μs   662.97μs   145.31μs  ±44%   159.85ms     4%
:mm-clj.core/defn_check-disjoint-restrictions      6,478   879.00ns     6.91μs    29.11μs    38.25μs    77.46μs    10.09ms    15.39μs  ±80%    99.71ms     2%
:mm-clj.core/find-substitutions                    6,478     1.00μs     2.51μs    34.97μs    41.73μs    74.64μs   253.41μs    12.46μs ±103%    80.74ms     2%
:mm-clj.core/defn_check-variables-have-type        2,433     1.85μs    22.32μs    48.24μs    53.25μs    82.92μs   307.96μs    26.91μs  ±47%    65.47ms     1%
:mm-clj.core/defn_mandatory-variables              1,333    11.06μs    42.34μs    74.83μs    89.03μs   130.66μs   368.69μs    48.17μs  ±34%    64.22ms     1%
:mm-clj.core/defn_check-symbols                    2,433     2.93μs    18.18μs    40.01μs    45.40μs    72.23μs   203.17μs    22.23μs  ±47%    54.08ms     1%
:mm-clj.core/defn_check-proof                      1,317     6.93μs    16.47μs    32.22μs    39.48μs    69.98μs   562.13μs    20.46μs  ±39%    26.94ms     1%
:mm-clj.core/defn_check-compressed-proof           1,317     5.33μs    13.96μs    28.97μs    35.32μs    66.59μs   423.83μs    17.81μs  ±40%    23.45ms     1%
:mm-clj.core/defn_check-disjoint-restriction      17,172   819.00ns   925.00ns     1.50μs     1.76μs     4.27μs   113.50μs     1.22μs  ±34%    21.03ms     0%
:mm-clj.core/defn_decompress-proof                 1,317     4.39μs    12.29μs    26.03μs    32.09μs    61.54μs   355.61μs    15.86μs  ±42%    20.89ms     0%
:mm-clj.core/defn_read-file_1                          1    16.73ms    16.73ms    16.73ms    16.73ms    16.73ms    16.73ms    16.73ms   ±0%    16.73ms     0%
:mm-clj.core/defn_read-file_2                          1    16.70ms    16.70ms    16.70ms    16.70ms    16.70ms    16.70ms    16.70ms   ±0%    16.70ms     0%
:mm-clj.core/defn_decode-proof-chars_1             1,317     1.85μs     5.26μs    11.66μs    14.54μs    26.49μs   197.29μs     7.20μs  ±46%     9.49ms     0%
:mm-clj.core/defn_strip-comments_1                     1     7.45ms     7.45ms     7.45ms     7.45ms     7.45ms     7.45ms     7.45ms   ±0%     7.45ms     0%
:mm-clj.core/defn_num-to-label                    22,428    63.00ns   167.00ns   320.00ns   366.00ns   736.00ns    25.47μs   228.74ns  ±37%     5.13ms     0%
:mm-clj.core/defn_check-axiom-stmt                    16   102.62μs   177.41μs   280.63μs   280.63μs   370.30μs   370.30μs   185.19μs  ±34%     2.96ms     0%
:mm-clj.core/defn_load-includes                        1     2.65ms     2.65ms     2.65ms     2.65ms     2.65ms     2.65ms     2.65ms   ±0%     2.65ms     0%
:mm-clj.core/defn_find-block                         589   752.00ns     2.33μs     4.34μs     5.79μs    21.21μs   197.01μs     3.38μs  ±63%     1.99ms     0%
:mm-clj.core/defn_mandatory-disjoints              1,333   686.00ns   854.00ns     2.06μs     2.48μs     3.84μs   141.76μs     1.43μs  ±57%     1.90ms     0%
:mm-clj.core/defn_check-floating-stmt                 12    15.51μs    16.82μs    33.58μs    33.58μs   325.86μs   325.86μs    44.87μs ±104%   538.46μs     0%
:mm-clj.core/defn_add-constant                        11     3.17μs     4.58μs    80.25μs   256.26μs   256.26μs   256.26μs    44.28μs ±115%   487.08μs     0%
:mm-clj.core/defn_add-variable                        12     8.98μs    10.11μs    12.85μs    12.85μs   205.66μs   205.66μs    26.59μs ±112%   319.03μs     0%
:mm-clj.core/defn_set-active-variable-type            12     5.48μs     7.06μs     7.67μs     7.67μs   165.05μs   165.05μs    19.89μs ±122%   238.65μs     0%
:mm-clj.core/defn_get-active-variable-type            12     2.57μs     3.64μs     4.48μs     4.48μs    84.07μs    84.07μs    10.24μs ±120%   122.88μs     0%
:mm-clj.core/defn_check-variable-active               12     1.35μs     2.31μs     3.11μs     3.11μs    27.92μs    27.92μs     4.34μs  ±91%    52.08μs     0%

Accounted                                                                                                                                      16.29s    361%
Clock                                                                                                                                           4.52s    100%
```

#### after pulling mandatory variables into assertion scope

```
pId                                               nCalls        Min      50% ≤      90% ≤      95% ≤      99% ≤        Max       Mean   MAD      Clock  Total

:mm-clj.core/defn_parse-mm-program                     1     4.48s      4.48s      4.48s      4.48s      4.48s      4.48s      4.48s    ±0%     4.48s     91%
:mm-clj.core/defn_parse-mm-program-by-blocks           1     3.38s      3.38s      3.38s      3.38s      3.38s      3.38s      3.38s    ±0%     3.38s     69%
:mm-clj.core/defn_check-program                    3,094     5.96μs   313.19μs     1.37ms     2.46ms     6.12ms     1.10s      1.07ms ±120%     3.32s     68%
:mm-clj.core/defn_check-block-stmt                   625   150.41μs   861.80μs     3.32ms     5.22ms    10.11ms   217.14ms     1.83ms  ±87%     1.14s     23%
:mm-clj.core/defn_check-provable-stmt              1,317   104.05μs   379.57μs     1.19ms     2.24ms     5.97ms    10.71ms   687.37μs  ±72%   905.27ms    18%
:mm-clj.core/defn_check-assertion-stmt             1,333    77.73μs   347.95μs     1.16ms     2.13ms     5.94ms    10.67ms   654.45μs  ±75%   872.38ms    18%
:mm-clj.core/defn_mandatory-hypotheses             1,333     3.58μs   148.74μs   889.86μs     1.81ms     5.61ms    10.30ms   412.94μs ±113%   550.44ms    11%
:mm-clj.core/defn_verify-proofs                        1   393.74ms   393.74ms   393.74ms   393.74ms   393.74ms   393.74ms   393.74ms   ±0%   393.74ms     8%
:mm-clj.core/defn_verify-proof_2                   1,317    10.44μs   230.98μs   481.80μs   654.28μs     1.16ms     6.82ms   295.60μs  ±49%   389.30ms     8%
:mm-clj.core/defn_verify-proof_5                   1,317     1.88μs   210.28μs   453.77μs   614.90μs     1.10ms     6.64ms   273.60μs  ±50%   360.33ms     7%
:mm-clj.core/defn_apply-axiom                      6,478     8.25μs    36.98μs    96.30μs   121.75μs   204.04μs     6.41ms    51.99μs  ±60%   336.78ms     7%
:mm-clj.core/defn_add-label                        2,445     4.98μs   116.06μs   200.67μs   214.09μs   253.44μs   374.48μs   115.46μs  ±49%   282.30ms     6%
:mm-clj.core/defn_check-essential-stmt             1,100    23.32μs   140.72μs   262.39μs   288.34μs   353.55μs   707.06μs   154.33μs  ±44%   169.76ms     3%
:mm-clj.core/defn_apply-substitutions             10,164     2.62μs    12.09μs    29.07μs    35.98μs    58.14μs   296.45μs    16.56μs  ±46%   168.36ms     3%
:mm-clj.core/defn_check-disjoint-restrictions      6,478   904.00ns     9.26μs    37.82μs    49.80μs   103.02μs     2.47ms    17.87μs  ±75%   115.79ms     2%
:mm-clj.core/find-substitutions                    6,478     1.02μs     2.53μs    35.32μs    42.58μs    71.28μs   402.91μs    12.66μs ±103%    81.98ms     2%
:mm-clj.core/defn_check-variables-have-type        2,433     1.94μs    23.62μs    49.59μs    57.58μs    95.81μs   356.10μs    28.02μs  ±48%    68.16ms     1%
:mm-clj.core/defn_mandatory-variables              1,333    11.23μs    43.52μs    81.54μs    95.45μs   136.61μs   444.86μs    50.77μs  ±36%    67.68ms     1%
:mm-clj.core/defn_check-symbols                    2,433     3.22μs    19.24μs    41.69μs    46.81μs    77.17μs   192.91μs    23.24μs  ±47%    56.54ms     1%
:mm-clj.core/defn_read-file_1                          1    43.46ms    43.46ms    43.46ms    43.46ms    43.46ms    43.46ms    43.46ms   ±0%    43.46ms     1%
:mm-clj.core/defn_read-file_2                          1    43.42ms    43.42ms    43.42ms    43.42ms    43.42ms    43.42ms    43.42ms   ±0%    43.42ms     1%
:mm-clj.core/defn_check-proof                      1,317     6.90μs    17.56μs    35.93μs    44.63μs    93.96μs   624.28μs    22.46μs  ±44%    29.59ms     1%
:mm-clj.core/defn_check-compressed-proof           1,317     5.24μs    14.97μs    32.27μs    40.58μs    89.08μs   480.68μs    19.50μs  ±46%    25.68ms     1%
:mm-clj.core/defn_check-disjoint-restriction      17,172   916.00ns     1.03μs     1.63μs     1.93μs     5.49μs    84.15μs     1.36μs  ±36%    23.37ms     0%
:mm-clj.core/defn_decompress-proof                 1,317     4.25μs    12.88μs    29.61μs    37.06μs    80.81μs   408.77μs    17.28μs  ±47%    22.76ms     0%
:mm-clj.core/defn_mandatory-disjoints             17,172   548.00ns   637.00ns     1.00μs     1.29μs     3.32μs   286.26μs   902.52ns  ±44%    15.50ms     0%
:mm-clj.core/defn_decode-proof-chars_1             1,317     1.85μs     5.53μs    12.72μs    16.56μs    34.26μs   243.98μs     7.86μs  ±51%    10.36ms     0%
:mm-clj.core/defn_strip-comments_1                     1     7.35ms     7.35ms     7.35ms     7.35ms     7.35ms     7.35ms     7.35ms   ±0%     7.35ms     0%
:mm-clj.core/defn_num-to-label                    22,428    65.00ns   167.00ns   324.00ns   378.00ns   844.00ns    42.54μs   244.63ns  ±44%     5.49ms     0%
:mm-clj.core/defn_check-axiom-stmt                    16   105.84μs   173.15μs   287.53μs   287.53μs   365.94μs   365.94μs   194.14μs  ±30%     3.11ms     0%
:mm-clj.core/defn_load-includes                        1     2.77ms     2.77ms     2.77ms     2.77ms     2.77ms     2.77ms     2.77ms   ±0%     2.77ms     0%
:mm-clj.core/defn_find-block                         589   885.00ns     3.08μs     6.09μs     7.03μs    25.14μs   194.57μs     4.38μs  ±58%     2.58ms     0%
:mm-clj.core/defn_check-floating-stmt                 12    15.97μs    17.25μs    31.01μs    31.01μs   343.28μs   343.28μs    45.77μs ±108%   549.19μs     0%
:mm-clj.core/defn_add-constant                        11     3.43μs     4.89μs    81.85μs   259.31μs   259.31μs   259.31μs    45.19μs ±114%   497.06μs     0%
:mm-clj.core/defn_add-variable                        12     9.41μs    10.33μs    12.68μs    12.68μs   242.31μs   242.31μs    29.83μs ±119%   357.95μs     0%
:mm-clj.core/defn_set-active-variable-type            12     5.60μs     7.14μs     8.25μs     8.25μs   169.65μs   169.65μs    20.44μs ±122%   245.32μs     0%
:mm-clj.core/defn_get-active-variable-type            12     2.63μs     3.94μs     4.88μs     4.88μs    86.92μs    86.92μs    10.61μs ±120%   127.31μs     0%
:mm-clj.core/defn_check-variable-active               12     1.36μs     2.40μs     3.32μs     3.32μs    27.76μs    27.76μs     4.41μs  ±88%    52.98μs     0%

Accounted                                                                                                                                      17.38s    353%
Clock                                                                                                                                           4.92s    100%
```

#### after pulling mandatory-hypotheses into assertion scope

```
pId                                               nCalls        Min      50% ≤      90% ≤      95% ≤      99% ≤        Max       Mean   MAD      Clock  Total

:mm-clj.core/defn_parse-mm-program                     1     4.32s      4.32s      4.32s      4.32s      4.32s      4.32s      4.32s    ±0%     4.32s     75%
:mm-clj.core/defn_check-program                    3,094     5.91μs   302.72μs     1.32ms     2.31ms     6.72ms     1.08s      1.06ms ±122%     3.29s     57%
:mm-clj.core/defn_parse-mm-program-by-blocks           1     3.25s      3.25s      3.25s      3.25s      3.25s      3.25s      3.25s    ±0%     3.25s     56%
:mm-clj.core/defn_verify-proofs                        1     1.44s      1.44s      1.44s      1.44s      1.44s      1.44s      1.44s    ±0%     1.44s     25%
:mm-clj.core/defn_verify-proof_2                   1,317     8.35μs   832.66μs     1.96ms     2.71ms     5.23ms    16.30ms     1.09ms  ±56%     1.44s     25%
:mm-clj.core/defn_verify-proof_5                   1,317     1.60μs   809.09μs     1.92ms     2.68ms     5.20ms    16.10ms     1.06ms  ±57%     1.40s     24%
:mm-clj.core/defn_apply-axiom                      6,478    13.32μs    90.48μs   469.79μs   649.55μs     1.49ms    13.24ms   212.08μs  ±90%     1.37s     24%
:mm-clj.core/defn_check-block-stmt                   625   135.78μs   821.58μs     3.17ms     5.48ms     9.70ms   231.24ms     1.83ms  ±90%     1.14s     20%
:mm-clj.core/defn_check-disjoint-restrictions      6,478     5.76μs    62.36μs   405.83μs   569.90μs     1.39ms    13.04ms   174.52μs  ±98%     1.13s     20%
:mm-clj.core/defn_mandatory-variables             24,983     4.75μs    36.64μs    78.19μs    92.99μs   130.92μs     6.18ms    42.07μs  ±52%     1.05s     18%
:mm-clj.core/defn_check-provable-stmt              1,317    92.02μs   366.24μs     1.12ms     2.03ms     6.44ms    11.44ms   680.09μs  ±74%   895.68ms    15%
:mm-clj.core/defn_check-assertion-stmt             1,333    66.45μs   341.85μs     1.09ms     1.97ms     6.40ms    11.41ms   648.78μs  ±77%   864.82ms    15%
:mm-clj.core/defn_mandatory-disjoints             17,172    10.23μs    43.40μs    83.46μs   100.53μs   139.74μs     6.19ms    47.53μs  ±47%   816.15ms    14%
:mm-clj.core/defn_mandatory-hypotheses             1,333    17.67μs   196.17μs   884.59μs     1.72ms     6.17ms    11.18ms   464.67μs ±104%   619.40ms    11%
:mm-clj.core/defn_add-label                        2,445     4.92μs   106.17μs   199.20μs   210.68μs   263.52μs     1.34ms   111.32μs  ±49%   272.17ms     5%
:mm-clj.core/defn_apply-substitutions             10,164     2.61μs    14.62μs    31.06μs    39.02μs    63.27μs    12.69ms    18.81μs  ±53%   191.20ms     3%
:mm-clj.core/defn_check-essential-stmt             1,100    23.51μs   130.16μs   252.43μs   275.23μs   343.32μs   662.19μs   146.77μs  ±44%   161.44ms     3%
:mm-clj.core/find-substitutions                    6,478     1.02μs     2.75μs    36.87μs    47.97μs    82.05μs    12.72ms    15.44μs ±106%   100.00ms     2%
:mm-clj.core/defn_check-variables-have-type        2,433     1.86μs    22.73μs    48.08μs    53.38μs    92.29μs   307.34μs    27.07μs  ±48%    65.87ms     1%
:mm-clj.core/defn_check-symbols                    2,433     3.15μs    18.34μs    41.40μs    46.41μs    73.62μs   202.97μs    22.61μs  ±47%    55.02ms     1%
:mm-clj.core/defn_check-proof                      1,317     7.53μs    16.81μs    33.45μs    41.29μs    75.82μs   574.68μs    21.15μs  ±41%    27.85ms     0%
:mm-clj.core/defn_check-disjoint-restriction      17,172     1.01μs     1.16μs     1.81μs     2.86μs    14.05μs   115.76μs     1.59μs  ±44%    27.25ms     0%
:mm-clj.core/defn_check-compressed-proof           1,317     5.60μs    14.45μs    29.63μs    36.97μs    72.50μs   435.46μs    18.42μs  ±42%    24.26ms     0%
:mm-clj.core/defn_decompress-proof                 1,317     4.56μs    12.56μs    26.70μs    33.76μs    68.93μs   360.94μs    16.46μs  ±43%    21.68ms     0%
:mm-clj.core/defn_read-file_1                          1    17.96ms    17.96ms    17.96ms    17.96ms    17.96ms    17.96ms    17.96ms   ±0%    17.96ms     0%
:mm-clj.core/defn_read-file_2                          1    17.92ms    17.92ms    17.92ms    17.92ms    17.92ms    17.92ms    17.92ms   ±0%    17.92ms     0%
:mm-clj.core/defn_decode-proof-chars_1             1,317     1.84μs     5.21μs    11.60μs    14.27μs    30.36μs   200.61μs     7.24μs  ±47%     9.54ms     0%
:mm-clj.core/defn_strip-comments_1                     1     8.59ms     8.59ms     8.59ms     8.59ms     8.59ms     8.59ms     8.59ms   ±0%     8.59ms     0%
:mm-clj.core/defn_num-to-label                    22,428    63.00ns   167.00ns   308.00ns   426.00ns   799.00ns    35.76μs   235.62ns  ±41%     5.28ms     0%
:mm-clj.core/defn_check-axiom-stmt                    16    74.72μs   173.86μs   276.67μs   276.67μs   369.85μs   369.85μs   181.39μs  ±34%     2.90ms     0%
:mm-clj.core/defn_load-includes                        1     2.61ms     2.61ms     2.61ms     2.61ms     2.61ms     2.61ms     2.61ms   ±0%     2.61ms     0%
:mm-clj.core/defn_find-block                         589   880.00ns     2.61μs     4.92μs     6.39μs    22.16μs   148.64μs     3.72μs  ±58%     2.19ms     0%
:mm-clj.core/defn_check-floating-stmt                 12    15.45μs    16.61μs    33.02μs    33.02μs   335.23μs   335.23μs    45.50μs ±106%   545.98μs     0%
:mm-clj.core/defn_add-constant                        11     3.41μs     4.60μs    80.56μs   263.25μs   263.25μs   263.25μs    45.22μs ±115%   497.43μs     0%
:mm-clj.core/defn_add-variable                        12     8.94μs     9.90μs    12.87μs    12.87μs   201.75μs   201.75μs    26.11μs ±112%   313.34μs     0%
:mm-clj.core/defn_set-active-variable-type            12     5.39μs     6.74μs     7.73μs     7.73μs   167.67μs   167.67μs    19.98μs ±123%   239.74μs     0%
:mm-clj.core/defn_get-active-variable-type            12     2.52μs     3.68μs     4.49μs     4.49μs    84.77μs    84.77μs    10.22μs ±122%   122.63μs     0%
:mm-clj.core/defn_check-variable-active               12     1.40μs     2.37μs     3.06μs     3.06μs    26.90μs    26.90μs     4.24μs  ±89%    50.86μs     0%

Accounted                                                                                                                                      24.04s    416%
Clock                                                                                                                                           5.78s    100%
```

#### original

```
pId                                               nCalls        Min      50% ≤      90% ≤      95% ≤      99% ≤        Max       Mean   MAD      Clock  Total

:mm-clj.core/defn_parse-mm-program                     1     4.24s      4.24s      4.24s      4.24s      4.24s      4.24s      4.24s    ±0%     4.24s     62%
:mm-clj.core/defn_check-program                    3,094     5.72μs   300.57μs     1.28ms     2.27ms     7.03ms     1.08s      1.07ms ±123%     3.30s     49%
:mm-clj.core/defn_parse-mm-program-by-blocks           1     3.16s      3.16s      3.16s      3.16s      3.16s      3.16s      3.16s    ±0%     3.16s     47%
:mm-clj.core/defn_verify-proofs                        1     2.53s      2.53s      2.53s      2.53s      2.53s      2.53s      2.53s    ±0%     2.53s     37%
:mm-clj.core/defn_verify-proof_2                   1,317     8.92μs     1.37ms     3.59ms     5.08ms    10.40ms    27.07ms     1.92ms  ±59%     2.53s     37%
:mm-clj.core/defn_verify-proof_7                   1,317     1.68μs     1.34ms     3.56ms     5.04ms    10.36ms    27.04ms     1.89ms  ±60%     2.48s     37%
:mm-clj.core/defn_apply-axiom                      6,478    21.93μs   132.99μs   850.36μs     1.19ms     3.26ms    26.06ms   379.27μs  ±96%     2.46s     36%
:mm-clj.core/defn_mandatory-hypotheses             7,795     7.87μs    46.95μs   459.41μs   673.41μs     2.53ms    11.52ms   217.33μs ±113%     1.69s     25%
:mm-clj.core/defn_mandatory-variables             31,445     4.68μs    32.71μs    72.14μs    87.54μs   130.37μs    24.48ms    39.45μs  ±56%     1.24s     18%
:mm-clj.core/defn_check-block-stmt                   625   122.91μs   799.62μs     3.12ms     5.87ms    10.06ms   236.31ms     1.84ms  ±92%     1.15s     17%
:mm-clj.core/defn_check-disjoint-restrictions      6,478     5.90μs    61.61μs   387.74μs   546.03μs     1.39ms    25.91ms   175.00μs  ±99%     1.13s     17%
:mm-clj.core/defn_check-provable-stmt              1,317    86.81μs   368.32μs     1.08ms     2.03ms     6.83ms    21.19ms   683.60μs  ±76%   900.30ms    13%
:mm-clj.core/defn_mandatory-disjoints             17,172    10.11μs    40.64μs    80.49μs    95.98μs   144.13μs    24.61ms    46.86μs  ±49%   804.72ms    12%
:mm-clj.core/defn_check-proof                      1,317    30.29μs   217.11μs   919.78μs     1.75ms     6.47ms    11.57ms   485.60μs  ±99%   639.53ms     9%
:mm-clj.core/defn_check-compressed-proof           1,317    28.53μs   214.23μs   916.55μs     1.75ms     6.46ms    11.56ms   482.56μs ±100%   635.53ms     9%
:mm-clj.core/defn_add-label                        2,445     5.01μs   106.65μs   195.05μs   199.23μs   227.24μs    19.79ms   117.11μs  ±53%   286.33ms     4%
:mm-clj.core/defn_check-assertion-stmt             1,333    21.19μs   177.71μs   268.44μs   284.35μs   359.33μs    19.88ms   193.03μs  ±41%   257.31ms     4%
:mm-clj.core/defn_apply-substitutions             10,164     2.62μs    14.83μs    30.13μs    37.44μs    63.36μs     1.33ms    17.55μs  ±46%   178.40ms     3%
:mm-clj.core/defn_check-essential-stmt             1,100    22.53μs   131.33μs   244.38μs   263.45μs   338.68μs   670.95μs   144.30μs  ±43%   158.74ms     2%
:mm-clj.core/find-substitutions                    6,478     1.27μs     3.28μs    40.04μs    51.03μs    91.00μs     1.36ms    14.98μs ±101%    97.07ms     1%
:mm-clj.core/defn_check-variables-have-type        2,433     1.83μs    22.29μs    48.00μs    55.15μs    87.83μs   306.01μs    26.84μs  ±48%    65.29ms     1%
:mm-clj.core/defn_check-symbols                    2,433     2.98μs    17.92μs    40.51μs    46.56μs    72.52μs   200.93μs    22.18μs  ±47%    53.97ms     1%
:mm-clj.core/defn_check-disjoint-restriction      17,172   976.00ns     1.18μs     1.95μs     3.05μs    14.00μs    93.72μs     1.61μs  ±43%    27.69ms     0%
:mm-clj.core/defn_decompress-proof                 1,317     4.82μs    12.60μs    27.21μs    33.59μs    66.18μs   355.88μs    16.47μs  ±44%    21.69ms     0%
:mm-clj.core/defn_read-file_1                          1    13.91ms    13.91ms    13.91ms    13.91ms    13.91ms    13.91ms    13.91ms   ±0%    13.91ms     0%
:mm-clj.core/defn_read-file_2                          1    13.88ms    13.88ms    13.88ms    13.88ms    13.88ms    13.88ms    13.88ms   ±0%    13.88ms     0%
:mm-clj.core/defn_decode-proof-chars_1             1,317     2.02μs     5.29μs    12.11μs    14.80μs    30.92μs   197.38μs     7.45μs  ±49%     9.81ms     0%
:mm-clj.core/defn_num-to-label                    22,428    64.00ns   183.00ns   305.00ns   348.00ns   805.00ns    27.72μs   235.18ns  ±34%     5.27ms     0%
:mm-clj.core/defn_strip-comments_1                     1     3.55ms     3.55ms     3.55ms     3.55ms     3.55ms     3.55ms     3.55ms   ±0%     3.55ms     0%
:mm-clj.core/defn_load-includes                        1     2.58ms     2.58ms     2.58ms     2.58ms     2.58ms     2.58ms     2.58ms   ±0%     2.58ms     0%
:mm-clj.core/defn_check-axiom-stmt                    16    36.69μs   130.09μs   244.71μs   244.71μs   249.73μs   249.73μs   137.48μs  ±43%     2.20ms     0%
:mm-clj.core/defn_find-block                         589   834.00ns     2.32μs     4.66μs     6.13μs    20.68μs   156.72μs     3.35μs  ±63%     1.97ms     0%
:mm-clj.core/defn_check-floating-stmt                 12    15.88μs    17.22μs    33.32μs    33.32μs   356.94μs   356.94μs    47.70μs ±108%   572.43μs     0%
:mm-clj.core/defn_add-constant                        11     3.34μs     4.67μs    80.05μs   278.56μs   278.56μs   278.56μs    46.30μs ±115%   509.35μs     0%
:mm-clj.core/defn_add-variable                        12     9.33μs    11.14μs    23.84μs    23.84μs   198.63μs   198.63μs    28.35μs ±100%   340.19μs     0%
:mm-clj.core/defn_set-active-variable-type            12     5.66μs     7.08μs    22.28μs    22.28μs   190.00μs   190.00μs    23.29μs ±119%   279.43μs     0%
:mm-clj.core/defn_get-active-variable-type            12     2.72μs     3.74μs     4.50μs     4.50μs    83.04μs    83.04μs    10.24μs ±119%   122.84μs     0%
:mm-clj.core/defn_check-variable-active               12     1.45μs     2.40μs     3.11μs     3.11μs    26.60μs    26.60μs     4.31μs  ±86%    51.75μs     0%

Accounted                                                                                                                                      30.09s    444%
Clock                                                                                                                                           6.78s    100%
```

