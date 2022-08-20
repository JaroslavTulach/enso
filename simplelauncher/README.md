
### Build

```
enso$ export JAVA_HOME=/graalvm-ce-java11-21.3.0/
enso$ sbt bootstrap
enso$ sbt buildEngineDistribution
enso$ cd simplelauncher
simplelauncher$ mvn -Pnative install
simplelauncher$ ./target/simplelauncher fac.enso
```

specify path to your Enso source to execute something else than `fac.enso`.
