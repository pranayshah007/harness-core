/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static java.lang.Integer.MAX_VALUE;
import static org.junit.rules.RuleChain.outerRule;

import io.harness.data.presentation.ByteCountUtils;
import io.harness.rule.CategoryTimeoutRule;
import io.harness.rule.OwnerRule;
import io.harness.rule.OwnerWatcherRule;
import io.harness.rule.RepeatRule;
import io.harness.rule.ThreadRule;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.powermock.core.classloader.annotations.PowerMockIgnore;

@Slf4j
@PowerMockIgnore({"javax.management.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "org.w3c.dom.*",
    "com.sun.org.apache.xalan.*", "javax.activation.*", "jdk.internal.reflect.*", "javax.net.ssl.*",
    "javax.management.*", "javax.crypto.JceSecurity.*", "javax.crypto.*"})
public class CategoryTest {
  @ClassRule public static ThreadRule threadRule = new ThreadRule();

  @Rule public OwnerWatcherRule ownerWatcherRule = new OwnerWatcherRule();
  @Rule public TestName testName = new TestName();

  private RepeatRule repeatRule = new RepeatRule();

  @Rule
  public TestRule chain = outerRule(repeatRule).around(outerRule(new OwnerRule()).around(new CategoryTimeoutRule()));

  /**
   * Log test case name.
   */
  @Before
  public void logTestCaseName() {
    StringBuilder sb = new StringBuilder("Running test ").append(testName.getMethodName());

    int repetition = repeatRule.getRepetition();
    if (repetition > 0) {
      sb.append(" - ").append(repetition);
    }
    log.info(sb.toString());
  }

  private static final long MAX_HEAP_SIZE = 32L * 1024L * 1024L * 1024L;

  @After
  public void dumpHeapMap() throws IOException {
    long heapSize = Runtime.getRuntime().totalMemory();
    if (heapSize < MAX_HEAP_SIZE) {
      log.info("The heap size at the end of the test is {}", ByteCountUtils.humanReadableBin(heapSize));
      return;
    }

    log.error("The heap size at the end of the test is {}", ByteCountUtils.humanReadableBin(heapSize));
    // allocate impossible to trigger heap dump
    long[] block = new long[MAX_VALUE];
    log.info("{}", block.length);
  }
}
