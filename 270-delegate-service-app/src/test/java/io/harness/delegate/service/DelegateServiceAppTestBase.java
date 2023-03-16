/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import io.harness.CategoryTest;
import io.harness.rule.LifecycleRule;

import io.dropwizard.testing.ResourceHelpers;
import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@Slf4j
public abstract class DelegateServiceAppTestBase extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Rule public LifecycleRule lifecycleRule = new LifecycleRule();

  private static boolean isBazelTest() {
    return System.getProperty("user.dir").contains("/bin/");
  }

  public static String getResourceFilePath(String filePath) {
    return isBazelTest() ? "270-delegate-service-app/src/test/resources/" + filePath
                         : ResourceHelpers.resourceFilePath(filePath);
  }

  public static String getSourceResourceFile(Class clazz, String filePath) {
    return isBazelTest() ? "270-delegate-service-app/src/main/resources" + filePath
                         : clazz.getResource(filePath).getFile();
  }
}