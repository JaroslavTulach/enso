package org.enso.interpreter.runtime;

import static scala.jdk.javaapi.CollectionConverters.asJava;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.TreeSet;
import org.enso.compiler.core.EnsoParser;
import org.enso.compiler.core.ir.module.scope.imports.Polyglot;
import org.enso.pkg.PackageManager$;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeProxyCreation;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

public final class EnsoLibraryFeature implements Feature {
  @Override
  public void beforeAnalysis(BeforeAnalysisAccess access) {
    var hcl = new HostClassLoader(access.getApplicationClassLoader());
    var libsClassPath = System.getProperty("enso.libs.path").split(File.pathSeparator);
    var libs = new LinkedHashSet<Path>();
    for (var path : libsClassPath) {
      var p = new File(path).toPath();
      if (!p.toFile().isFile()) {
        throw new IllegalStateException("No such file: " + p);
      }
      try {
        var entry = p.toUri().toURL();
        System.err.println("adding cp: " + entry);
        hcl.add(entry);
      } catch (MalformedURLException ex) {
        throw new IllegalStateException("No such URL: " + p, ex);
      }
      var p1 = p.getParent();
      if (p1 != null && p1.getFileName().toString().equals("java")) {
        var p2 = p1.getParent();
        if (p2 != null
            && p2.getFileName().toString().equals("polyglot")
            && p2.getParent() != null) {
          libs.add(p2.getParent());
        }
      }
    }

    /*
      To run Standard.Test one shall analyze its polyglot/java files. But there are none
      to include on classpath as necessary test classes are included in Standard.Base!
      We can locate the Test library by following code or we can make sure all necessary
      imports are already mentioned in Standard.Base itself.

    if (!libs.isEmpty()) {
      var f = libs.iterator().next();
      var stdTest = f.getParent().getParent().resolve("Test").resolve(f.getFileName());
      if (stdTest.toFile().exists()) {
        libs.add(stdTest);
      }
      System.err.println("Testing library: " + stdTest);
    }
    */

    var classes = new TreeSet<String>();
    try {
      for (var p : libs) {
        var result = PackageManager$.MODULE$.Default().loadPackage(p.toFile());
        if (result.isSuccess()) {
          var pkg = result.get();
          for (var src : pkg.listSourcesJava()) {
            var code = Files.readString(src.file().toPath());
            var ir = EnsoParser.compile(code);
            for (var imp : asJava(ir.imports())) {
              if (imp instanceof Polyglot poly && poly.entity() instanceof Polyglot.Java entity) {
                var name = new StringBuilder(entity.getJavaName());
                Class<?> clazz;
                for (; ; ) {
                  try {
                    clazz = hcl.loadClass(name.toString());
                  } catch (ClassNotFoundException ex) {
                    clazz = access.findClassByName(name.toString());
                  }
                  if (clazz != null) {
                    break;
                  }
                  int at = name.toString().lastIndexOf('.');
                  if (at < 0) {
                    throw new IllegalStateException("Cannot load " + entity.getJavaName());
                  }
                  name.setCharAt(at, '$');
                }
                classes.add(clazz.getName());
                RuntimeReflection.register(clazz);
                RuntimeReflection.registerAllConstructors(clazz);
                RuntimeReflection.registerAllFields(clazz);
                RuntimeReflection.registerAllMethods(clazz);
                if (clazz.isInterface()) {
                  RuntimeProxyCreation.register(clazz);
                }
                RuntimeReflection.register(clazz.getConstructors());
                RuntimeReflection.register(clazz.getMethods());
                RuntimeReflection.register(clazz.getFields());
              }
            }
          }
        }
      }
    } catch (LinkageError | Exception ex) {
      ex.printStackTrace(System.err);
      throw new IllegalStateException(ex);
    }
    //    System.err.println("Summary for polyglot import java:");
    //    for (var className : classes) {
    //      System.err.println("  " + className);
    //    }
    System.err.println("Registered " + classes.size() + " classes for reflection");
  }
}
