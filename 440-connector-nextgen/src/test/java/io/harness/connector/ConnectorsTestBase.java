/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector;

import io.harness.CategoryTest;
import io.harness.rule.LifecycleRule;
import io.harness.runners.GuiceRunner;
import io.harness.runners.ModuleProvider;

import org.junit.Rule;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(GuiceRunner.class)
@ModuleProvider(ConnectorTestRule.class)
public abstract class ConnectorsTestBase extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Rule public LifecycleRule lifecycleRule = new LifecycleRule();
  @Rule public ConnectorTestRule connectorTestRule = new ConnectorTestRule(lifecycleRule.getClosingFactory());
}
