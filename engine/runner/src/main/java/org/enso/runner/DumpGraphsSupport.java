package org.enso.runner;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;

class DumpGraphsSupport {
  static void enableDumping() {
    final var timer = new Timer("Enable IGV graph dumping", true);
    TimerTask enableDumpingTask =
        new TimerTask() {
          @Override
          public void run() {
            boolean success = false;
            final var jmxServer = ManagementFactory.getPlatformMBeanServer();
            for (var mb : jmxServer.queryMBeans(null, null)) {
              if ("org.graalvm.compiler.hotspot.management.HotSpotGraalRuntimeMBean"
                  .equals(mb.getClassName())) {
                final ObjectName name = mb.getObjectName();
                try {
                  jmxServer.setAttribute(name, new Attribute("Dump", "\":1\""));
                  jmxServer.setAttribute(name, new Attribute("PrintGraph", "Network"));
                  System.err.println("IGV graph dumping enabled for " + name);
                  success = true;
                } catch (MBeanException
                    | AttributeNotFoundException
                    | InstanceNotFoundException
                    | ReflectionException
                    | InvalidAttributeValueException ex) {
                  ex.printStackTrace();
                }
              }
            }
            if (success) {
              timer.cancel();
            }
          }
        };
    timer.scheduleAtFixedRate(enableDumpingTask, 100, 500);
  }

  static void configureDumping(Map<String, String> options) {
    options.put("engine.TraceCompilation", "true");
    options.put("engine.MultiTier", "false");
  }
}
