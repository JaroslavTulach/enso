package org.enso.interpreter.node.expression.builtin.interop.java;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import java.io.File;
import org.enso.interpreter.dsl.BuiltinMethod;
import org.enso.interpreter.node.expression.builtin.text.util.ExpectStringNode;
import org.enso.interpreter.runtime.EnsoContext;

@BuiltinMethod(
    type = "Java",
    name = "add_to_class_path",
    description = "Adds a path to the host class path.",
    autoRegister = false)
public abstract class AddToClassPathNode extends Node {

  static AddToClassPathNode build() {
    return AddToClassPathNodeGen.create();
  }

  abstract Object execute(Object path);

  @CompilerDirectives.TruffleBoundary
  @Specialization
  Object doExecute(Object path, @Cached ExpectStringNode expectStringNode) {
    var ctx = EnsoContext.get(this);
    var file = ctx.getTruffleFile(new File(expectStringNode.execute(path)));
    var pkg = ctx.getPackageOf(file);
    if (pkg.isEmpty()) {
      throw ctx.raiseAssertionPanic(this, "File " + file + "should be in a package", null);
    } else {
      ctx.addToClassPath(file, pkg.get());
      return ctx.getBuiltins().nothing();
    }
  }
}
