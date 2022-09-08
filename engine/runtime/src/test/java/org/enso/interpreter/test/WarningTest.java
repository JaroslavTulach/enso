package org.enso.interpreter.test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Paths;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.enso.polyglot.RuntimeOptions;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class WarningTest {
  private Context ctx;

  @Before
  public void prepareCtx() {
    Engine eng = Engine.newBuilder()
      .allowExperimentalOptions(true)
      .logHandler(new ByteArrayOutputStream())
      .option(
        RuntimeOptions.LANGUAGE_HOME_OVERRIDE,
        Paths.get("../../distribution/component").toFile().getAbsolutePath()
      ).build();
    this.ctx = Context.newBuilder()
      .engine(eng)
      .allowIO(true)
      .allowAllAccess(true)
      .build();
    final Map<String, Language> langs = ctx.getEngine().getLanguages();
    assertNotNull("Enso found: " + langs, langs.get("enso"));
  }

  @Test
  public void withWarningsInArray() throws Exception {
    final URI facUri = new URI("memory://warnings.enso");
    final Source facSrc = Source.newBuilder("enso", """
    import Standard.Base.Data.Vector
    import Standard.Base.Warning

    check =
        one = Warning.attach "First" 1
        snd = Warning.attach "Second" 2
        tri = 3
        [one, snd, tri]

    msg obj = Warning.get_all obj . map _.value . fold "" (+)

    """, "warnings.enso")
            .uri(facUri)
            .buildLiteral();

    var module = ctx.eval(facSrc);
    var res = module.invokeMember("eval_expression", "check");
    assertTrue("is vector type", res.hasArrayElements());
    Value one = res.getArrayElement(0);
    Value snd = res.getArrayElement(1);
    Value tri = res.getArrayElement(2);

    assertTrue(one.fitsInInt());
    assertTrue(snd.fitsInInt());
    assertTrue(tri.fitsInInt());

    assertEquals("One", 1, one.asInt());
    assertEquals("Second", 2, snd.asInt());
    assertEquals("Third", 3, tri.asInt());

    var msg = module.invokeMember("eval_expression", "msg");
    Value msgOne = msg.execute(one);
    Value msgSnd = msg.execute(snd);
    Value msgTri = msg.execute(tri);
    assertEquals("Warning extracted " + msgOne, "First", msgOne.asString());
    assertEquals("Warning extracted " + msgSnd, "Second", msgSnd.asString());
    assertEquals("No warning: " + msgTri, "", msgTri.asString());
  }
}
