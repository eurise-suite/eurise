ruy (Apache 2.0, Google), sottomodulo di slimt (DavidVentura/slimt 3rd-party/ruy). Copia ridotta: `ruy/*.cc|h` senza test e
benchmark, più `ruy/profiler/instrumentation.*`. Compilata senza cpuinfo: il prodotto scalare a 8 bit dei processori ARMv8.2
(dotprod) per ora non si usa; da valutare se la velocità sul telefono non basta.
