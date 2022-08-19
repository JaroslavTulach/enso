package org.enso.launcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

public final class Launcher {
    private static final Context c;
    static {
        File f = null;
        try {
            f = new File(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            while (!new File(f, "runtime.jar").exists()) {
                f = f.getParentFile();
            }
        } catch (URISyntaxException ex) {
            throw new IllegalStateException(ex);
        }
        
        System.setProperty("truffle.class.path.append", new File(f, "runtime.jar").getAbsolutePath());
        Path dist = Paths.get(f.toURI()).resolve(Paths.get("built-distribution", "enso-engine-0.0.0-dev-linux-amd64", "enso-0.0.0-dev", "component"));
        if (!Files.exists(dist)) {
            throw new IllegalStateException("Cannot find " + dist);
        }
        Engine test = Engine.create();
        System.err.println("Languages: " + test.getLanguages().keySet());

        Engine eng = Engine.newBuilder()
                .allowExperimentalOptions(true)
                .logHandler(new ByteArrayOutputStream())
                .option("enso.languageHomeOverride", dist.toString())
                .build();
        Context ctx = Context.newBuilder()
                .engine(eng)
                .allowIO(true)
                .allowHostClassLoading(true)
                .allowHostClassLookup((c) -> true)
                .build();

        c = ctx;

    }
    private Launcher() {
    }

    public static void main(String[] args) throws Exception {
        Source src;
        if (args.length > 0) {
            File file = new File(args[0]);
            // System.err.println("file: " + file);
            src = Source.newBuilder("enso", file).build();
        } else {
            src = Source.newBuilder("enso",
                "main = 'Hello from Enso!'",
                "demo.enso"
            ).build();
            
            System.err.println("Provide a path to .enso file. Meanwhile executing: " + src.getCharacters());
        }
        // System.err.println("source: " + src);
        Value module = c.eval(src);
        // System.err.println("module: " + module);
        // System.err.println("members: " + module.getMemberKeys());
        Value constr = module.invokeMember("get_associated_constructor");
        // System.err.println("constr: " + constr);
        Value main = module.invokeMember("get_method", constr, "main");
        // System.err.println("main: " + main);
        Value res = main.execute();
        System.out.println(res);
        // module.invokeMember("eval_expression", "main");
    }
}
