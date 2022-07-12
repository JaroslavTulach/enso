package org.enso.interpreter.node.expression.builtin.date;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import java.time.LocalDate;
import org.enso.interpreter.dsl.BuiltinMethod;
import org.enso.interpreter.runtime.Context;

@BuiltinMethod(
    type = "Date",
    name = "now",
    description = "Returns current Date")
public abstract class NowNode extends Node {
  static NowNode build() {
    return NowNodeGen.create();
  }

  abstract Object execute(Object self);

  @Specialization
  Object doNow(Object self) {
    return Context.get(this).getEnvironment().asGuestValue(LocalDate.now());
  }
}
