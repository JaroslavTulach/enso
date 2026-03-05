package org.enso.polyglot.test

class Slf4jTest extends org.scalatest.flatspec.AnyFlatSpec {

  private val logger = org.slf4j.LoggerFactory.getLogger(this.getClass)

  it should "boo" in {
    logger.error("Boo!")
  }

  it should "whoo when failing" in {
    logger.error("Whoo!")
    throw new AssertionError("OK failure")
  }
}
