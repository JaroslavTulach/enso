package org.enso.interpreter.bench.benchmarks.semantic;

import java.io.ByteArrayOutputStream;
import java.nio.file.Paths;
import java.util.AbstractList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;


@BenchmarkMode(Mode.AverageTime)
@Fork(1)
@Warmup(iterations = 2)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class CopyBenchmarks {
  private Object[] array;
  private int[] intArray;
  private long[] longArray;

  @Setup
  public void initializeBenchmark(BenchmarkParams params) {
    switch (params.getBenchmark().replaceFirst(".*\\.", "")) {
      case "forLoopObject":
      case "arrayCopyObject": {
        array = new Object[1000000];
        break;
      }
      case "forLoopInt":
      case "arrayCopyInt":
        intArray = new int[1000000];
        break;
      case "forLoopLong":
      case "arrayCopyLong":
        longArray = new long[1000000];
        break;
    }
  }

  @Benchmark
  public void forLoopObject(Blackhole matter) {
    Object[] copy = new Object[array.length];
    for (int i = 0; i < array.length; i++) {
      copy[i] = array[i];
    }
    matter.consume(copy);
  }

  @Benchmark
  public void arrayCopyObject(Blackhole matter) {
    Object[] copy = new Object[array.length];
    System.arraycopy(array, 0, copy, 0, array.length);
    matter.consume(copy);
  }

  @Benchmark
  public void forLoopInt(Blackhole matter) {
    int[] copy = new int[intArray.length];
    for (int i = 0; i < intArray.length; i++) {
      copy[i] = intArray[i];
    }
    matter.consume(copy);
  }

  @Benchmark
  public void arrayCopyInt(Blackhole matter) {
    int[] copy = new int[intArray.length];
    System.arraycopy(intArray, 0, copy, 0, intArray.length);
    matter.consume(copy);
  }
  
  @Benchmark
  public void forLoopLong(Blackhole matter) {
    long[] copy = new long[longArray.length];
    for (int i = 0; i < longArray.length; i++) {
      copy[i] = longArray[i];
    }
    matter.consume(copy);
  }

  @Benchmark
  public void arrayCopyLong(Blackhole matter) {
    long[] copy = new long[longArray.length];
    System.arraycopy(longArray, 0, copy, 0, longArray.length);
    matter.consume(copy);
  }
  
}

