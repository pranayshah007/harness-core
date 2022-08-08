/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng;

import static io.harness.CvNextGenTestBase.getResourceFilePath;

import io.harness.CategoryTest;

import io.specto.hoverfly.junit.core.HoverflyConfig;
import io.specto.hoverfly.junit.core.HoverflyMode;
import io.specto.hoverfly.junit.core.SimulationSource;
import io.specto.hoverfly.junit.rule.HoverflyRule;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.ClassRule;

@Slf4j
public abstract class HoverflyTestBase extends CategoryTest {
  //  TODO: We need make capture and simulation switching easier.
  // TODO: trying out if assigning a fixed port fixes the CI issues. We need a common rule for all tests.

  @ClassRule public static HoverflyRule HOVERFLY_RULE;

  /*
  To Run Hoverfly in Capture mode and change hoverflyMode and projectBaseFolder below
  and "--strategy=TestRunner=local" to bazel flags for run time to not run it in sandbox mode
   */
  private static HoverflyMode hoverflyMode = HoverflyMode.SIMULATE;
  private static String projectBaseFolder = "/Users/kamal/workspace/portal/";
  protected String accessToken;
  static {
    try {
      if (hoverflyMode.equals(HoverflyMode.SIMULATE)) {
        HOVERFLY_RULE = HoverflyRule.inSimulationMode(HoverflyConfig.localConfigs().disableTlsVerification());
      } else {
        HOVERFLY_RULE = HoverflyRule.inCaptureMode(HoverflyConfig.localConfigs().disableTlsVerification());
      }
    } catch (Exception e) {
      log.info("Initializing HoverflyRule inSimulationMode failed. Retrying one more time : {}", e);
      // This is rarely failing in CI with port conflict exception. So retrying one more time.
      // If you still face this issue in your PR's please notify me(kamal).
      HOVERFLY_RULE = HoverflyRule.inSimulationMode(HoverflyConfig.localConfigs().disableTlsVerification());
    }
  }

  @Before
  public void before() throws IOException {
    if (hoverflyMode == HoverflyMode.CAPTURE) {
      accessToken =
          FileUtils.readFileToString(new File(getResourceFilePath("hoverfly/gcpAccessToken")), StandardCharsets.UTF_8);
    }
    String filePath = "src/test/resources/hoverfly/";
    String fileName = StringUtils.replaceAll(this.getClass().getCanonicalName(), "\\.", "_") + "_"
        + testName.getMethodName() + ".json";
    if (hoverflyMode.equals(HoverflyMode.SIMULATE)) {
      HOVERFLY_RULE.simulate(SimulationSource.file(Paths.get(getResourceFilePath(filePath + fileName))));
    } else if (hoverflyMode.equals(HoverflyMode.CAPTURE)) {
      HOVERFLY_RULE.capture(projectBaseFolder + getResourceFilePath(filePath + fileName));
    } else {
      throw new IllegalStateException("hoverflyMode:" + hoverflyMode + " Not supported");
    }
  }
}
