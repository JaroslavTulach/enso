package org.enso.interpreter.runtime;

import org.enso.example.TestClass;
import org.enso.interpreter.test.TestBase;
import org.enso.polyglot.MethodNames;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class HostClassLoadingTest extends TestBase {
  public HostClassLoadingTest() {}

  @Test
  public void loadDifferentClassThanExpected() {

    var b = defaultContextBuilder("enso");
    b.hostClassLoader(
        new ClassLoader() {
          @Override
          protected final Class<?> findClass(String name) {
            if ("fake.TestClass".equals(name)) {
              return TestClass.class;
            }
            return null;
          }
        });
    try (var ctx = b.build()) {
      var code =
          """
          polyglot java import fake.TestClass

          test a b = TestClass.add a b
          """;
      var test = ctx.eval("enso", code).invokeMember(MethodNames.Module.EVAL_EXPRESSION, "test");

      assertTrue("test is executable", test.canExecute());
      var five = test.execute(3, 2);
      assertEquals("There pretends to be a method add in the fake class", 5, five.asInt());
    }
  }
}
