
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public class Enso {

    public static void main(String[] args) {
        var b = Context.newBuilder("enso")
			.option("log.level", java.util.logging.Level.WARNING.getName())
			.allowHostAccess(HostAccess.ALL)
			.allowAllAccess(true)
			.allowExperimentalOptions(true);

		if (args.length == 0 || !"jvm".equals(args[0])) {
			b.option("engine.IsolateLibrary", "runner.so")
			 .option("engine.SpawnIsolate", "true");
		}

        try (Context context = b.build()) {
            Value module = context.eval("enso", """
			import Standard.Base.IO

			hello x =
				IO.println "Saying hello..."
				"Hello "+x
			""");
            var x = module.invokeMember("eval_expression", "hello");
            System.out.println(x.execute(args.length > 0 ? args[args.length - 1] : "world"));
        }
    }
}
