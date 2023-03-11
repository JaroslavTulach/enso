package org.enso.compiler;

import buildinfo.Info;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.source.Source;
import org.apache.commons.lang3.StringUtils;
import org.enso.compiler.core.IR;
import org.enso.interpreter.runtime.EnsoContext;
import org.enso.interpreter.runtime.Module;
import org.enso.interpreter.runtime.builtin.Builtins;

import org.enso.compiler.core.IR$Function$Lambda;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.enso.compiler.core.ir.DiagnosticStorage;
import org.enso.compiler.core.ir.MetadataStorage;
import scala.Function1;
import scala.Option;
import scala.collection.immutable.List;

public final class ModuleCache extends Cache<ModuleCache.CachedModule, ModuleCache.Metadata> {

    private final Module module;

    public ModuleCache(Module module) {
        this.module = module;
        this.logLevel = Level.FINEST;
        this.stringRepr = module.getName().toString();
        this.entryName = module.getName().item();
        this.dataSuffix = irCacheDataExtension;
        this.metadataSuffix = irCacheMetadataExtension;
    }

    @Override
    protected byte[] metadata(String sourceDigest, String blobDigest, CachedModule entry) {
        try {
            return objectMapper.writeValueAsBytes(new Metadata(sourceDigest, blobDigest, entry.compilationStage().toString()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean needsSourceDigestVerification() {
        return true;
    }

    @Override
    protected boolean needsDataDigestVerification() {
        return false;
    }

    @Override
    protected CachedModule validateReadObject(Object obj, Metadata meta, TruffleLogger logger) throws CacheException {
        if (obj instanceof IR.Module ir) {
            try {
                return new CachedModule(ir, Module.CompilationStage.valueOf(meta.compilationStage()), module.getSource());
            } catch (IOException ioe) {
                throw new CacheException(ioe.getMessage());
            }
        } else {
            throw new CacheException("Expected IR.Module, got " + obj.getClass());
        }
    }

    @Override
    protected Optional<Metadata> metadataFromBytes(byte[] bytes, TruffleLogger logger) {
        var maybeJsonString = new String(bytes, Cache.metadataCharset);
        try {
            return Optional.of(objectMapper.readValue(maybeJsonString, Metadata.class));
        } catch (JsonProcessingException e) {
            logger.log(logLevel, "Failed to deserialize module's metadata: " + e.getMessage(), e);
            return Optional.empty();
        }
    }

    private Optional<String> computeDigestOfModuleSources(Source source) {
        if (source != null) {
            byte[] sourceBytes;
            if (source.hasBytes()) {
                sourceBytes = source.getBytes().toByteArray();
            } else {
                sourceBytes = source.getCharacters().toString().getBytes(metadataCharset);
            }
            return Optional.of(computeDigestFromBytes(sourceBytes));
        } else {
            return Optional.empty();
        }
    }

    @Override
    protected Optional<String> computeDigest(CachedModule entry, TruffleLogger logger) {
        return computeDigestOfModuleSources(entry.source());
    }

    @Override
    protected Optional<String> computeDigestFromSource(EnsoContext context, TruffleLogger logger) {
        try {
            return computeDigestOfModuleSources(module.getSource());
        } catch (IOException e) {
            logger.log(logLevel, "failed to retrieve the source of " + module.getName(), e);
            return Optional.empty();
        }
    }

    @Override
    protected Optional<Cache.Roots> getCacheRoots(EnsoContext context) {
        if (module != context.getBuiltins().getModule()) {
            return context.getPackageOf(module.getSourceFile()).map(pkg -> {
                var irCacheRoot    = pkg.getIrCacheRootForPackage(Info.ensoVersion());
                var qualName       = module.getName();
                var localCacheRoot = irCacheRoot.resolve(qualName.path().mkString("/"));

                var distribution = context.getDistributionManager();
                var pathSegmentsJava = new ArrayList<String>();
                pathSegmentsJava.addAll(Arrays.asList(
                        pkg.namespace(),
                        pkg.name(),
                        pkg.config().version(),
                        Info.ensoVersion()
                ));
                pathSegmentsJava.addAll(qualName.pathAsJava());
                var path = distribution.LocallyInstalledDirectories().irCacheDirectory()
                        .resolve(StringUtils.join(pathSegmentsJava, "/"));
                var globalCacheRoot = context.getTruffleFile(path.toFile());

                return new Cache.Roots(localCacheRoot, globalCacheRoot);
            });
        } else {
            var distribution = context.getDistributionManager();
            var pathSegmentsJava = new ArrayList<String>();
            pathSegmentsJava.addAll(Arrays.asList(
                    Builtins.NAMESPACE,
                    Builtins.PACKAGE_NAME,
                    Info.ensoVersion(),
                    Info.ensoVersion()
            ));
            pathSegmentsJava.addAll(module.getName().pathAsJava());
            var path = distribution.LocallyInstalledDirectories().irCacheDirectory()
                    .resolve(StringUtils.join(pathSegmentsJava, "/"));
            var globalCacheRoot = context.getTruffleFile(path.toFile());

            return Optional.of(new Cache.Roots(globalCacheRoot, globalCacheRoot));
        }
    }

    @Override
    protected Object extractObjectToSerialize(CachedModule entry) {
      int[] counter = { 0 };
      System.err.println("extractObjectToSerialize");
      var o = entry.moduleIR().mapExpressions(new Function1<IR.Expression, IR.Expression>() {
        @Override
        public IR.Expression apply(IR.Expression v1) {
          return switch (v1) {
            case IR$Function$Lambda __ -> new ProxyIR(v1, counter[0]++);
            default -> v1.mapExpressions(this);
          };
        }
      });
      System.err.println("counter = " + counter[0]);
      return o;
    }

    static class NoIR extends BaseIR {
      static final long serialVersionUID = ProxyIR.serialVersionUID;
      static final ObjectStreamClass STREAM = ObjectStreamClass.lookup(NoIR.class);

      private NoIR() {
        super(-1);
      }

      private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        var getters = ois.readFields();
        System.err.println("read NoIR: " + index + " real: " + getters.get("real", null));
      }
    }

      static class BaseIR implements IR.Function {
        static final long serialVersionUID = ProxyIR.serialVersionUID;
        final int index;

        private BaseIR(int v) {
          this.index = v;
        }

        @Override
        public List<DefinitionArgument> arguments() {
          throw new UnsupportedOperationException();
        }

        @Override
        public Expression body() {
          throw new UnsupportedOperationException("I am " + getClass() + " index: " + index + " and I need body!");
        }

        @Override
        public boolean canBeTCO() {
          throw new UnsupportedOperationException();
        }

        @Override
        public Function mapExpressions(Function1<Expression, Expression> fn) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Function setLocation(Option<IdentifiedLocation> location) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Function duplicate(boolean keepLocations, boolean keepMetadata, boolean keepDiagnostics, boolean keepIdentifiers) {
          throw new UnsupportedOperationException();
        }

        @Override
        public MetadataStorage passData() {
          return new MetadataStorage(nil());
        }

        @Override
        public Option<IdentifiedLocation> location() {
          throw new UnsupportedOperationException();
        }

        @Override
        public List<IR> children() {
          return nil();
        }

        @Override
        public UUID id() {
          throw new UnsupportedOperationException();
        }

        @Override
        public void id_$eq(UUID x$1) {
          throw new UnsupportedOperationException();
        }

        @Override
        public DiagnosticStorage diagnostics() {
          throw new UnsupportedOperationException();
        }

        @Override
        public String showCode(int indent) {
          throw new UnsupportedOperationException();
        }

        @SuppressWarnings("unchecked")
        private static <T> List<T> nil() {
          Object o = scala.collection.immutable.Nil$.MODULE$;
          return (scala.collection.immutable.List<T>) o;
        }
    }

    static final class ProxyIR extends BaseIR {
      static final long serialVersionUID = 332L;
      static final ObjectStreamClass STREAM = ObjectStreamClass.lookup(ProxyIR.class);

      private IR.Expression real;

      ProxyIR(IR.Expression real, int index) {
        super(index);
        this.real = real;
      }

      private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        System.err.println("Before reading real for #" + index);
        // var getters = ois.readFields();
        // var r = (IR.Expression) getters.get("real", null);
        var a = ois.available();
        System.err.println("avail: " + a);
        ois.skip(a);
        System.err.println("After reading real for #" + index);
        real = null;
        // throw new ClassNotFoundException("Don't read me! : " + getters.get("real", null));
      }

      private Object readResolve() {
        return this; // or real
      }
    }

    // CachedModule is not a record **on purpose**. There appears to be a Frgaal bug leading to invalid compilation error.
    static class CachedModule {
        private final IR.Module _moduleIR;
        private final Module.CompilationStage _compilationStage;
        private final Source _source;
        public CachedModule(IR.Module moduleIR, Module.CompilationStage compilationStage, Source source) {
            this._moduleIR = moduleIR;
            this._compilationStage = compilationStage;
            this._source = source;
        }

        IR.Module moduleIR() {
            return _moduleIR;
        }
        Module.CompilationStage compilationStage() {
            return _compilationStage;
        }
        Source source() {
            return _source;
        }

    }

    public record Metadata(
            @JsonProperty("source_hash") String sourceHash,
            @JsonProperty("blob_hash") String blobHash,
            @JsonProperty("compilation_stage") String compilationStage) implements Cache.Metadata {}

    private final static String irCacheDataExtension = ".ir";

    private final static String irCacheMetadataExtension = ".meta";

    private final static ObjectMapper objectMapper = new ObjectMapper();

}
