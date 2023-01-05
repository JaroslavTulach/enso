package org.enso.interpreter.node.expression.builtin.meta;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import java.util.Arrays;
import org.enso.interpreter.dsl.BuiltinMethod;
import org.enso.interpreter.node.expression.builtin.mutable.CoerceArrayNode;
import org.enso.interpreter.runtime.EnsoContext;
import org.enso.interpreter.runtime.callable.argument.ArgumentDefinition;
import org.enso.interpreter.runtime.callable.argument.CallArgumentInfo;
import org.enso.interpreter.runtime.callable.atom.Atom;
import org.enso.interpreter.runtime.callable.atom.AtomConstructor;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.callable.function.FunctionSchema;

@BuiltinMethod(
    type = "Meta",
    name = "new_atom_builtin",
    description = "Creates a new atom with given constructor and fields.",
    autoRegister = false)
public abstract class NewAtomInstanceNode extends Node {

  static NewAtomInstanceNode build() {
    return NewAtomInstanceNodeGen.create();
  }

  abstract Atom execute(AtomConstructor constructor, Object fields, long lazyIndex);

  static boolean isSpecified(long index) {
    return index >= 0;
  }

  @Specialization(guards = "!isSpecified(lazyIndex)")
  Atom doExecute(AtomConstructor constructor, Object fields, long lazyIndex, @Cached CoerceArrayNode coerce) {
    return constructor.newInstance(coerce.execute(fields));
  }

  @Specialization(guards = "isSpecified(lazyIndex)")
  Atom doExecuteLazy(AtomConstructor constructor, Object fields, long lazyIndex,
    @Cached CoerceArrayNode coerce, @Cached SwapAtomFieldNode swapNode) {
    var instance = constructor.newInstance(coerce.execute(fields));
    if (lazyIndex < constructor.getArity()) {
      var schema = new FunctionSchema(FunctionSchema.CallerFrameAccess.NONE, new ArgumentDefinition[] {
          new ArgumentDefinition(0, "instance", ArgumentDefinition.ExecutionMode.EXECUTE),
          new ArgumentDefinition(1, "lazy_index", ArgumentDefinition.ExecutionMode.EXECUTE),
          new ArgumentDefinition(2, "fn", ArgumentDefinition.ExecutionMode.EXECUTE),
          new ArgumentDefinition(3, "value", ArgumentDefinition.ExecutionMode.EXECUTE)
      }, new boolean[] {
        true, true, true, false
      }, new CallArgumentInfo[0]);
      var preArgs = new Object[] { instance, lazyIndex, null, null };
      var function = new Function(
            swapNode.getCallTarget(),
            null,
            schema,
            preArgs,
            new Object[] {}
      );
      preArgs[2] = function;
      instance.getFields()[(int)lazyIndex] = function;
    }
    return instance;
  }

  static final class SwapAtomFieldNode extends RootNode {
    private SwapAtomFieldNode() {
      super(null);
    }

    static SwapAtomFieldNode create() {
      return new SwapAtomFieldNode();
    }

    @Override
    public Object execute(VirtualFrame frame) {
      var args = Function.ArgumentsHelper.getPositionalArguments(frame.getArguments());
      if (args[0] instanceof Atom replace) {
        if (args[1] instanceof Long l) {
          var fields = replace.getFields();
          if (fields[l.intValue()] == args[2]) {
            fields[l.intValue()] = args[3];
          }
        }
      }
      return EnsoContext.get(this).getBuiltins().nothing();
    }
  }
}
