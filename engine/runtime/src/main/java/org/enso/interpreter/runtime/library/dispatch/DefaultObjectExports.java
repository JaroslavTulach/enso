package org.enso.interpreter.runtime.library.dispatch;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import org.enso.interpreter.node.expression.builtin.meta.AtomWithAHoleNode;
import org.enso.interpreter.runtime.EnsoContext;
import org.enso.interpreter.runtime.data.EnsoObject;
import org.enso.interpreter.runtime.data.Type;
import org.enso.interpreter.runtime.error.PanicException;

@ExportLibrary(value = TypesLibrary.class, receiverType = Object.class)
final class DefaultObjectExports {
  @ExportMessage
  static boolean hasType(Object receiver, @Shared("extractType") @Cached ExtractTypeNode node) {
    try {
      var type = node.execute(receiver);
      return type != null;
    } catch (PanicException ex) {
      return false;
    }
  }

  @ExportMessage
  static Type getType(Object receiver, @Shared("extractType") @Cached ExtractTypeNode node) {
    var type = node.execute(receiver);
    if (type == null) {
      throw CompilerDirectives.shouldNotReachHere();
    }
    return type;
  }

  @GenerateUncached
  abstract static class ExtractTypeNode extends Node {

    abstract Type execute(Object value);

    static ExtractTypeNode build() {
      return DefaultObjectExportsFactory.ExtractTypeNodeGen.create();
    }

    static ExtractTypeNode getUncached() {
      return DefaultObjectExportsFactory.ExtractTypeNodeGen.getUncached();
    }

    @Specialization
    Type withoutType(
        Object value,
        @CachedLibrary(limit = "3") InteropLibrary interop,
        @Cached WithoutType delegate) {
      var interopType = WithoutType.Interop.resolve(value, interop);
      var type = delegate.execute(interopType, value);
      if (type instanceof Type t) {
        return t;
      } else {
        return null;
      }
    }

    @GenerateUncached
    abstract static class WithoutType extends Node {
      abstract Type execute(Interop op, Object value);

      @Specialization(guards = {"type.isArray()"})
      Type doPolyglotArray(Interop type, Object value) {
        return EnsoContext.get(this).getBuiltins().array();
      }

      @Specialization(guards = {"type.isMap()"})
      Type doPolygotMap(Interop type, Object value) {
        return EnsoContext.get(this).getBuiltins().map();
      }

      @Specialization(guards = {"type.isString()"})
      Type doPolyglotString(Interop type, Object value) {
        return EnsoContext.get(this).getBuiltins().text();
      }

      @Specialization(guards = {"type.isNumber()"})
      Type doPolyglotNumber(
          Interop type,
          Object value,
          @Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop) {
        var builtins = EnsoContext.get(this).getBuiltins();
        if (interop.fitsInLong(value)) {
          return builtins.number().getInteger();
        } else if (interop.fitsInBigInteger(value)) {
          return builtins.number().getInteger();
        } else if (interop.fitsInDouble(value)) {
          return builtins.number().getFloat();
        } else {
          return EnsoContext.get(this).getBuiltins().number().getNumber();
        }
      }

      @Specialization(guards = {"type.isDateTime()"})
      Type doDateTime(Interop type, Object value) {
        return EnsoContext.get(this).getBuiltins().dateTime();
      }

      @Specialization(guards = {"type.isTimeZone()"})
      Type doTimeZone(Interop type, Object value) {
        EnsoContext ctx = EnsoContext.get(this);
        return ctx.getBuiltins().timeZone();
      }

      @Specialization(guards = {"type.isDate()"})
      Type doDate(Interop type, Object value) {

        EnsoContext ctx = EnsoContext.get(this);
        return ctx.getBuiltins().date();
      }

      @Specialization(guards = {"type.isTime()"})
      Type doTime(Interop type, Object value) {

        EnsoContext ctx = EnsoContext.get(this);
        return ctx.getBuiltins().timeOfDay();
      }

      @Specialization(guards = "type.isDuration()")
      Type doDuration(Interop type, Object value) {
        EnsoContext ctx = EnsoContext.get(this);
        return ctx.getBuiltins().duration();
      }

      @Specialization(guards = {"type.isMetaObject()"})
      Type doMetaObject(
          Interop type,
          Object value,
          @Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop) {
        try {
          throw new PanicException(interop.getMetaObject(value), this);
        } catch (UnsupportedMessageException e) {
          CompilerDirectives.transferToInterpreter();
          var builtins = EnsoContext.get(this).getBuiltins();
          throw new PanicException(builtins.error().makeCompileError("invalid meta object"), this);
        }
      }

      @Fallback
      @CompilerDirectives.TruffleBoundary
      Type doAny(Interop any, Object value) {
        return null;
      }

      enum Interop {
        NONE,
        STRING,
        NUMBER,
        ARRAY,
        MAP,
        DATE_TIME,
        TIME_ZONE,
        DATE,
        TIME,
        DURATION,
        META_OBJECT;

        static Interop resolve(Object value, InteropLibrary interop) {
          assert !(value instanceof EnsoObject) || AtomWithAHoleNode.isHole(value)
              : "Don't use interop for EnsoObject: " + value.getClass().getName();
          if (interop.isString(value)) {
            return STRING;
          }
          if (interop.isNumber(value)) {
            return NUMBER;
          }
          if (interop.hasArrayElements(value)) {
            return ARRAY;
          }
          if (interop.hasHashEntries(value)) {
            return MAP;
          }
          boolean time = interop.isTime(value);
          boolean date = interop.isDate(value);
          if (time) {
            return date ? DATE_TIME : TIME;
          }
          if (date) {
            return DATE;
          }
          if (interop.isTimeZone(value)) {
            return TIME_ZONE;
          }
          if (interop.isDuration(value)) {
            return DURATION;
          }
          if (interop.hasMetaObject(value)) {
            return META_OBJECT;
          }
          return NONE;
        }

        boolean isString() {
          return this == STRING;
        }

        boolean isNumber() {
          return this == NUMBER;
        }

        boolean isArray() {
          return this == ARRAY;
        }

        boolean isMap() {
          return this == MAP;
        }

        boolean isDateTime() {
          return this == DATE_TIME;
        }

        boolean isTimeZone() {
          return this == TIME_ZONE;
        }

        boolean isTime() {
          return this == TIME;
        }

        boolean isDate() {
          return this == DATE;
        }

        boolean isDuration() {
          return this == DURATION;
        }

        boolean isMetaObject() {
          return this == META_OBJECT;
        }

        boolean isNone() {
          return this == NONE;
        }
      }
    }
  }
}
