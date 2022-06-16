package org.enso.interpreter.runtime.data;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import org.enso.interpreter.dsl.Builtin;
import org.enso.interpreter.runtime.Context;
import org.enso.interpreter.runtime.callable.UnresolvedSymbol;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.library.dispatch.MethodDispatchLibrary;

import java.lang.ref.PhantomReference;

/** A runtime representation of a managed resource. */
@ExportLibrary(MethodDispatchLibrary.class)
@Builtin(pkg = "resource", stdlibName = "Standard.Base.Runtime.Resource.Managed_Resource")
public class ManagedResource implements TruffleObject {
  private final Object resource;
  private PhantomReference<ManagedResource> phantomReference;

  /**
   * Creates a new managed resource.
   *
   * @param resource the underlying resource
   */
  public ManagedResource(Object resource) {
    this.resource = resource;
    this.phantomReference = null;
  }

  /** @return the underlying resource */
  public Object getResource() {
    return resource;
  }

  /** @return the phantom reference tracking this managed resource */
  public PhantomReference<ManagedResource> getPhantomReference() {
    return phantomReference;
  }

  /**
   * Sets the value of the reference used to track reachability of this managed resource.
   *
   * @param phantomReference the phantom reference tracking this managed resource.
   */
  public void setPhantomReference(PhantomReference<ManagedResource> phantomReference) {
    this.phantomReference = phantomReference;
  }

  @Builtin.Method(
      description =
          "Makes an object into a managed resource, automatically finalized when the returned object is garbage collected.")
  @Builtin.Specialize
  public static ManagedResource register(Context context, Object resource, Function function) {
    return context.getResourceManager().register(resource, function);
  }

  @Builtin.Method(
      description =
          "Takes the value held by the managed resource and removes the finalization callbacks,"
              + " effectively making the underlying resource unmanaged again.")
  @Builtin.Specialize
  public Object take(Context context) {
    context.getResourceManager().take(this);
    return this.getResource();
  }

  @Builtin.Method(
      name = "finalize",
      description = "Finalizes a managed resource, even if it is still reachable.")
  @Builtin.Specialize
  public void close(Context context) {
    context.getResourceManager().close(this);
  }

  @ExportMessage
  boolean hasFunctionalDispatch() {
    return true;
  }

  @ExportMessage
  static class GetFunctionalDispatch {

    static final int CACHE_SIZE = 10;

    @CompilerDirectives.TruffleBoundary
    static Function doResolve(UnresolvedSymbol symbol) {
      Context context = getContext();
      return symbol.resolveFor(
          context.getBuiltins().managedResource(), context.getBuiltins().any());
    }

    static Context getContext() {
      return Context.get(null);
    }

    @Specialization(
        guards = {
          "!getContext().isInlineCachingDisabled()",
          "cachedSymbol == symbol",
          "function != null"
        },
        limit = "CACHE_SIZE")
    static Function resolveCached(
        ManagedResource _this,
        UnresolvedSymbol symbol,
        @Cached("symbol") UnresolvedSymbol cachedSymbol,
        @Cached("doResolve(cachedSymbol)") Function function) {
      return function;
    }

    @Specialization(replaces = "resolveCached")
    static Function resolve(ManagedResource _this, UnresolvedSymbol symbol)
        throws MethodDispatchLibrary.NoSuchMethodException {
      Function function = doResolve(symbol);
      if (function == null) {
        throw new MethodDispatchLibrary.NoSuchMethodException();
      }
      return function;
    }
  }
}
