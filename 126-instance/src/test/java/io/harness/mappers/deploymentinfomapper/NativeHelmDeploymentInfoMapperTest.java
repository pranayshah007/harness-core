/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers.deploymentinfomapper;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import io.harness.category.element.UnitTests;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.dtos.deploymentinfo.NativeHelmDeploymentInfoDTO;
import io.harness.entities.deploymentinfo.NativeHelmDeploymentInfo;
import io.harness.k8s.model.HelmVersion;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NativeHelmDeploymentInfoMapperTest {
  private static final String NAMESPACE = "test";
  private static final String RELEASE_NAME = "release-9a854787f0afbb105cf115d533f7a54624e1ba57";
  private static final String HELM_CHART_NAME = "testChart";
  private static final Map<String, List<String>> WORKLOAD_LABEL_SELECTORS =
      Map.of("workload", List.of("label1=value1", "label2=value2"));

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testToDTO() {
    NativeHelmDeploymentInfo entity = NativeHelmDeploymentInfo.builder()
                                          .namespaces(new LinkedHashSet<>(Collections.singleton(NAMESPACE)))
                                          .releaseName(RELEASE_NAME)
                                          .helmChartInfo(HelmChartInfo.builder().name(HELM_CHART_NAME).build())
                                          .helmVersion(HelmVersion.V3)
                                          .workloadLabelSelectors(WORKLOAD_LABEL_SELECTORS)
                                          .build();
    NativeHelmDeploymentInfoDTO dto = NativeHelmDeploymentInfoMapper.toDTO(entity);
    assertTrue(dto.getNamespaces().contains(NAMESPACE));
    assertEquals(RELEASE_NAME, dto.getReleaseName());
    assertEquals(HELM_CHART_NAME, dto.getHelmChartInfo().getName());
    assertEquals(HelmVersion.V3, dto.getHelmVersion());
    assertEquals(WORKLOAD_LABEL_SELECTORS, dto.getWorkloadLabelSelectors());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void toEntity() {
    NativeHelmDeploymentInfoDTO dto = NativeHelmDeploymentInfoDTO.builder()
                                          .namespaces(new LinkedHashSet<>(Collections.singleton(NAMESPACE)))
                                          .releaseName(RELEASE_NAME)
                                          .helmChartInfo(HelmChartInfo.builder().name(HELM_CHART_NAME).build())
                                          .helmVersion(HelmVersion.V3)
                                          .workloadLabelSelectors(WORKLOAD_LABEL_SELECTORS)
                                          .build();
    NativeHelmDeploymentInfo entity = NativeHelmDeploymentInfoMapper.toEntity(dto);
    assertTrue(entity.getNamespaces().contains(NAMESPACE));
    assertEquals(RELEASE_NAME, entity.getReleaseName());
    assertEquals(HELM_CHART_NAME, entity.getHelmChartInfo().getName());
    assertEquals(HelmVersion.V3, entity.getHelmVersion());
    assertEquals(WORKLOAD_LABEL_SELECTORS, entity.getWorkloadLabelSelectors());
  }
}
