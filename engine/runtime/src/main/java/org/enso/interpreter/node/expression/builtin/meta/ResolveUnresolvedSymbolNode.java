package org.enso.interpreter.node.expression.builtin.meta;

import org.enso.interpreter.dsl.BuiltinMethod;
import org.enso.interpreter.runtime.EnsoContext;
import org.enso.interpreter.runtime.Module;
import org.enso.interpreter.runtime.builtin.Builtins;
import org.enso.interpreter.runtime.callable.UnresolvedConversion;
import org.enso.interpreter.runtime.callable.UnresolvedSymbol;
import org.enso.interpreter.runtime.data.Type;
import org.enso.interpreter.runtime.error.PanicException;
import org.enso.interpreter.runtime.scope.ModuleScope;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

@BuiltinMethod(
    type = "Meta",
    name = "resolve_unresolved_symbol",
    description = "Resolves name in the scope of  an unresolved symbol",
    autoRegister = false)
public abstract class ResolveUnresolvedSymbolNode extends Node {
  static ResolveUnresolvedSymbolNode build() {
    return ResolveUnresolvedSymbolNodeGen.create();
  }

  abstract Object execute(Object symbol);

  private Object searchScope(Module m, String name) {
    String moduleName = m.getName().toString();
    if (name.equals(moduleName)) {
      return m;
    }
    if (name.startsWith(moduleName + ".")) {
      var rest = name.substring(moduleName.length() + 1);
      Type t = m.getScope().getType(rest);
      if (t != null) {
        return t;
      }
    }
    return null;
  }

  @Specialization
  Object doSymbol(UnresolvedSymbol symbol) {
    var ctx = EnsoContext.get(this);
    String name = symbol.getName();
    if (searchScope(symbol.getScope().getModule(), name) instanceof Object found) {
      return found;
    }
    for (var imp : symbol.getScope().getImports()) {
      if (searchScope(imp.getModule(), name) instanceof Object found) {
        return found;
      }
      for (var m : imp.getExports()) {
        if (searchScope(m.getModule(), name) instanceof Object found) {
          return found;
        }
      }
    }
    /* Trivial global search accross all the known modules:

    for (var m : ctx.getTopScope().getModules()) {
      if (searchScope(m, name) instanceof Object found) {
        return found;
      }
    }
    */
    return ctx.getBuiltins().nothing();
  }

  @Specialization
  ModuleScope doConversion(UnresolvedConversion symbol) {
    return symbol.getScope();
  }

  @Fallback
  ModuleScope doFallback(Object symbol) {
    Builtins builtins = EnsoContext.get(this).getBuiltins();
    throw new PanicException(
        builtins.error().makeTypeError("Unresolved_Symbol", symbol, "symbol"), this);
  }
}
