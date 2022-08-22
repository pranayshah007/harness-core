/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet.util;

import static io.harness.rule.OwnerRule.HITESH;
import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.Resource;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class K8sResourceUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetFargateVCpu() {
    assertThat(K8sResourceUtils.getFargateVCpu(.2 * 1024)).isEqualTo(0.25);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetFargateVCpu1() {
    assertThat(K8sResourceUtils.getFargateVCpu(.4 * 1024)).isEqualTo(0.5);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetFargateVCpu2() {
    assertThat(K8sResourceUtils.getFargateVCpu(1.2 * 1024)).isEqualTo(2);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetFargateVCpu3() {
    assertThat(K8sResourceUtils.getFargateVCpu(2 * 1024)).isEqualTo(2);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetFargateMemoryGb() {
    assertThat(K8sResourceUtils.getFargateMemoryGb(0.4 * 1024)).isEqualTo(0.5);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetFargateMemoryGb1() {
    assertThat(K8sResourceUtils.getFargateMemoryGb(1.2 * 1024)).isEqualTo(2);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetFargateMemoryGb2() {
    assertThat(K8sResourceUtils.getFargateMemoryGb(4 * 1024)).isEqualTo(4);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetResourceFromAnnotationMap() {
    assertResource("2vCPU 9GB", 2D, 9D);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetResourceFromAnnotationMapWithSpace() {
    assertResource("   2vCPU    9GB   ", 2D, 9D);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetResourceFromAnnotationMapDecimal() {
    assertResource("0.78vCPU  0.34GB", 0.78D, 0.34D);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetResourceFromAnnotationMapDecimal2() {
    assertResource("1.78vCPU  1.34GB", 1.78D, 1.34D);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetResourceFromAnnotationMapDecimal3() {
    assertResource(".78vCPU  .34GB", 0.78D, 0.34D);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetResourceFromAnnotationMapDecimal4() {
    assertResource("78.vCPU  34.GB", 78D, 34D);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetResourceFromAnnotationMapDecimal5() {
    assertResource("78.3vCPU34.3GB", 78.3D, 34.3D);
  }

  private static void assertResource(String capacityProvisioned, Double cpu, Double memory) {
    final Map<String, String> annotationMap = ImmutableMap.of("CapacityProvisioned", capacityProvisioned);
    assertThat(K8sResourceUtils.getResourceFromAnnotationMap(annotationMap))
        .isEqualTo(Resource.builder().cpuUnits(cpu * 1024D).memoryMb(memory * 1024D).build());
  }
}
