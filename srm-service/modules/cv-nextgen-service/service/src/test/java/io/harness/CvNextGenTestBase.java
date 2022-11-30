/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.rule.CvNextGenRule;
import io.harness.rule.LifecycleRule;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.dropwizard.testing.ResourceHelpers;
import java.io.IOException;
import java.net.URL;
import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@Slf4j
public abstract class CvNextGenTestBase extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Rule public LifecycleRule lifecycleRule = new LifecycleRule();
  @Rule public CvNextGenRule cvNextGenRule;

  public CvNextGenTestBase(boolean forbiddenAccessControl) {
    cvNextGenRule = new CvNextGenRule(lifecycleRule.getClosingFactory(), forbiddenAccessControl);
  }
  public CvNextGenTestBase() {
    this(false);
  }

  private static boolean isBazelTest() {
    return System.getProperty("user.dir").contains("/bin/");
  }

  public static String getResourceFilePath(String filePath) {
    return isBazelTest() ? "srm-service/modules/cv-nextgen-service/service/src/test/resources/" + filePath
                         : ResourceHelpers.resourceFilePath(filePath);
  }

  public static String getSourceResourceFile(Class clazz, String filePath) {
    return isBazelTest() ? "srm-service/modules/cv-nextgen-service/service/src/main/resources" + filePath
                         : clazz.getResource(filePath).getFile();
  }

  protected String getResource(String filePath) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource(filePath);
    return Resources.toString(testFile, Charsets.UTF_8);
  }
}
