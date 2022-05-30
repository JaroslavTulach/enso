package org.enso.interpreter.bench.benchmarks.semantic;

import java.util.Random;
import org.enso.interpreter.test.DefaultInterpreterRunner;
import org.enso.interpreter.test.InterpreterContext;

class LogicalFixtures implements DefaultInterpreterRunner {
  private static final int SIZE = 10_000;
  private final boolean [] mask;
  private MainMethod findFalse;
  private final InterpreterContext ctx;

  LogicalFixtures() {
    this.ctx = new InterpreterContext((id) -> id);

    var r = new Random();
    this.mask = new boolean[SIZE];

    for (int i = 0; i < SIZE; i++) {
      mask[i] = r.nextBoolean();
    }
  }

  public boolean[] getMask() {
    return mask;
  }

  @Override
  public void org$enso$interpreter$test$DefaultInterpreterRunner$_setter_$interpreterContext_$eq(InterpreterContext x$1) {
    throw new UnsupportedOperationException();
  }

  @Override
  public InterpreterContext interpreterContext() {
    return ctx;
  }

  @Override
  public MainMethod$ MainMethod() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void org$enso$interpreter$test$InterpreterRunner$_setter_$rawTQ_$eq(String x$1) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String rawTQ() {
    throw new UnsupportedOperationException();
  }

  MainMethod findFalse() {
    if (findFalse == null) {
      var code = ""
              + "from Standard.Base.Data.Boolean import True, False\n"
              + "\n"
              + "main = \n"
              + "    findFalse : Boolean -> Number -> Vec Boolean -> Boolean\n"
              + "    findFalse at mask =\n"
              + "      end = at >= mask.length\n"
              + "      if end then True else\n"
              + "          (mask.at at) && findFalse at+1 mask\n"
              + "\n"
              + "    findFalse 0\n"
              + "\n";
      findFalse = getMain(code, this.interpreterContext());
    }
    return findFalse;
  }
}
