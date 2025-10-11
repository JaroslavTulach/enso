package org.enso.os.environment.jni;

import java.io.File;
import java.nio.file.Files;
import java.util.Random;

import org.enso.jvm.channel.Channel;
import org.enso.jvm.channel.JVM;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class LoadClassTest {
  // TBD: Make the number bigger again
  //   - which currently exceeds the maximum size of a message
  //   - so fix it
  private static final int MAX = 5; // 5000;
  private static final int MIN = 1; // 1000;
  private static final String PATH = System.getProperty("java.home");
  // set from TestCollectorFeature
  public static String MODULE_PATH;

  private static JVM impl;

  private static JVM jvm() {
    if (impl == null) {
      System.err.println("Initializing JVM");
      var jdk = PATH;
      System.err.println("Path is " + jdk);
      if (System.getProperty("JAVA_HOME") instanceof String v) {
        jdk = v;
      }
      System.err.println("JAVA_HOME -D property " + jdk);
      assert MODULE_PATH != null : "MODULE_PATH field must be set!";
      var path = new File(jdk);
      assert path.isDirectory() : "Java home exists: " + path;
      impl =
          JVM.create(
              path,
              "--module-path=" + MODULE_PATH,
              "--enable-native-access=org.enso.os.environment",
              "-Djdk.module.main=org.enso.os.environment",
              "-Dsay=Ahoj");
    }
    return impl;
  }

  private Channel<JVMPeer> channel;

  @Before
  public void initializeChannel() throws Exception {
    System.out.println("Ready to perform init of channel");
    System.in.read();
    channel = Channel.create(jvm(), JVMPeer.class);
    // assertTrue("Created channel is master", channel.isMaster());
  }

  @Test
  public void executeMainClass() throws Exception {
    var out = File.createTempFile("check-main", ".log");
    var gen = new Random();
    for (var i = 0; i < 1; i++) {
      var n = gen.nextInt(MIN, MAX);
      System.out.println("Ready to perform round " + i);
      System.in.read();
      jvm().executeMain("org/enso/os/environment/jni/TestMain", out.getPath(), "" + n);
      System.out.println("TestMain executed");
      var content = Files.readString(out.toPath());
      assertEquals("Factorial of " + n + " is the same", TestMain.factorial(n).toString(), content);
      out.delete();
    }
  }
}
