/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core;

import io.harness.CategoryTest;
import io.harness.MockableTestMixin;
import io.harness.rule.LifecycleRule;

import org.junit.Rule;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public abstract class PmsSdkCoreTestBase extends CategoryTest implements MockableTestMixin {
  public static final String PMS_SDK_CORE_SERVICE_NAME = "PMS_SDK_CORE_SERVICE_NAME";

  @Rule public LifecycleRule lifecycleRule = new LifecycleRule();
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Rule public PmsSdkCoreRule orchestrationRule = new PmsSdkCoreRule(lifecycleRule.getClosingFactory());
}
