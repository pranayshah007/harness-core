/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.exception.UnexpectedException;
import io.harness.rule.Owner;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.helm.NativeHelmInstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.k8s.K8SInstanceSyncPerpetualTaskHandler;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class InstanceSyncPerpetualTaskServiceRegisterTest extends InstancesTestBase {
  @Mock K8SInstanceSyncPerpetualTaskHandler k8sInstanceSyncPerpetualService;
  @Mock NativeHelmInstanceSyncPerpetualTaskHandler nativeHelmInstanceSyncPerpetualTaskHandler;
  @InjectMocks InstanceSyncPerpetualTaskServiceRegister instanceSyncPerpetualTaskServiceRegister;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getInstanceSyncPerpetualServiceTestWhenK8S_INSTANCE_SYNCType() {
    assertThat(instanceSyncPerpetualTaskServiceRegister.getInstanceSyncPerpetualService("K8S_INSTANCE_SYNC"))
        .isEqualTo(k8sInstanceSyncPerpetualService);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getInstanceSyncPerpetualServiceTestWhenNATIVE_HELM_INSTANCE_SYNCType() {
    assertThat(instanceSyncPerpetualTaskServiceRegister.getInstanceSyncPerpetualService("NATIVE_HELM_INSTANCE_SYNC"))
        .isEqualTo(nativeHelmInstanceSyncPerpetualTaskHandler);
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getInstanceSyncPerpetualServiceTestWhenNoneOfK8sAndHelmType() {
    instanceSyncPerpetualTaskServiceRegister.getInstanceSyncPerpetualService("");
  }
}
