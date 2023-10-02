/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.CvNextGenTestBase.getResourceFilePath;

import io.harness.CategoryTest;

import io.specto.hoverfly.junit.core.HoverflyMode;
import io.specto.hoverfly.junit.core.SimulationSource;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

@Slf4j
public abstract class DSLHoverflyTestSuiteChildBase extends CategoryTest {
  private static String projectBaseFolder = "***";
  protected String accessToken;

  @BeforeClass
  @SneakyThrows
  public static void beforeClass() {
    if (!DSLSuiteTest.testSuiteStarted) {
      // In local testing, if running outside test suits, start the hoverfly.
      MethodUtils.invokeMethod(DSLSuiteTest.HOVERFLY_RULE, true, "before");
    }
  }

  @AfterClass
  @SneakyThrows
  public static void afterClass() {
    if (!DSLSuiteTest.testSuiteStarted) {
      // if running outside test suits, close the hoverfly.
      MethodUtils.invokeMethod(DSLSuiteTest.HOVERFLY_RULE, true, "after");
    }
  }

  @Before
  public void before() throws IOException {
    if (DSLSuiteTest.hoverflyMode == HoverflyMode.CAPTURE) {
      accessToken =
          FileUtils.readFileToString(new File(getResourceFilePath("hoverfly/gcpAccessToken")), StandardCharsets.UTF_8);
    }
    String filePath = "src/test/resources/hoverfly/";
    String fileName = StringUtils.replaceAll(this.getClass().getCanonicalName(), "\\.", "_") + "_"
        + testName.getMethodName() + ".json";
    if (DSLSuiteTest.hoverflyMode.equals(HoverflyMode.SIMULATE)) {
      DSLSuiteTest.HOVERFLY_RULE.simulate(SimulationSource.file(Paths.get(getResourceFilePath(filePath + fileName))));
    } else if (DSLSuiteTest.hoverflyMode.equals(HoverflyMode.CAPTURE)) {
      DSLSuiteTest.HOVERFLY_RULE.capture(projectBaseFolder + getResourceFilePath(filePath + fileName));
    } else {
      throw new IllegalStateException("hoverflyMode:" + DSLSuiteTest.hoverflyMode + " Not supported");
    }
  }
}
