/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.models.dashboard;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class InstanceCountDetailsByEnvTypeBaseTest extends InstancesTestBase {
  @Mock private Map<EnvironmentType, Integer> envTypeVsInstanceCountMap;
  @InjectMocks InstanceCountDetailsByEnvTypeBase instanceCountDetailsByEnvTypeBase;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getNonProdInstancesTest() {
    when(envTypeVsInstanceCountMap.getOrDefault(EnvironmentType.PreProduction, 0)).thenReturn(1);
    assertThat(instanceCountDetailsByEnvTypeBase.getNonProdInstances()).isEqualTo(1);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getProdInstancesTest() {
    when(envTypeVsInstanceCountMap.getOrDefault(EnvironmentType.Production, 0)).thenReturn(2);
    assertThat(instanceCountDetailsByEnvTypeBase.getProdInstances()).isEqualTo(2);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getTotalInstancesTest() {
    when(envTypeVsInstanceCountMap.getOrDefault(EnvironmentType.PreProduction, 0)).thenReturn(1);
    when(envTypeVsInstanceCountMap.getOrDefault(EnvironmentType.Production, 0)).thenReturn(2);
    assertThat(instanceCountDetailsByEnvTypeBase.getTotalInstances()).isEqualTo(3);
  }
}
