/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;

import static software.wings.service.impl.instance.InstanceSyncFlow.ITERATOR;
import static software.wings.service.impl.instance.InstanceSyncFlow.MANUAL;
import static software.wings.service.impl.instance.InstanceSyncFlow.NEW_DEPLOYMENT;
import static software.wings.service.impl.instance.InstanceSyncFlow.PERPETUAL_TASK;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.InfrastructureMappingType;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class InstanceHandlerTest extends WingsBaseTest {
  @Spy InstanceUtils instanceUtil;
  @InjectMocks InstanceHandler instanceHandler = mock(InstanceHandler.class, Mockito.CALLS_REAL_METHODS);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void validateInstanceType_valid_inframappings() {
    instanceHandler.validateInstanceType(InfrastructureMappingType.DIRECT_KUBERNETES.name());
    instanceHandler.validateInstanceType(InfrastructureMappingType.AWS_SSH.name());
  }

  @Test(expected = WingsException.class)
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void validateInstanceType_invalid_or_not_supported_infra() {
    instanceHandler.validateInstanceType("abc");
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void canUpdateInstancesInDb() {
    InstanceHandler handler = spy(InstanceHandler.class);

    assertTrue(handler.canUpdateInstancesInDb(MANUAL, "ACCOUNT_ID"));
    assertTrue(handler.canUpdateInstancesInDb(NEW_DEPLOYMENT, "ACCOUNT_ID"));

    assertTrue(handler.canUpdateInstancesInDb(ITERATOR, "ACCOUNT_ID"));
    assertFalse(handler.canUpdateInstancesInDb(PERPETUAL_TASK, "ACCOUNT_ID"));
  }
}
