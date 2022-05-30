package org.enso.interpreter.bench.benchmarks.semantic;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class LogicalBenchmarks {
  private static final LogicalFixtures fixtures = new LogicalFixtures();
  @Benchmark
  public void benchAndOperation() {
    var main = fixtures.findFalse();
    var function = main.mainFunction().value().execute(main.mainConstructor());
    var v = function.execute((Object) fixtures.getMask()).asBoolean();
    if (v) {
      throw new IllegalStateException("There shall be false in the aray somewhere " + Arrays.toString(fixtures.getMask()));
    }
  }
}
