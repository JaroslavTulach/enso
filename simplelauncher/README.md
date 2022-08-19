
### Build

```
enso$ sbt buildEngineDistribution
enso$ cd simplelauncher
simplelauncher$ mvn -Pnative install
simplelauncher$ ./target/simplelauncher fac.enso
```

specify path to your Enso source to execute something else than `fac.enso`.
