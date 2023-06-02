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
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dtos.deploymentinfo.K8sDeploymentInfoDTO;
import io.harness.entities.deploymentinfo.K8sDeploymentInfo;
import io.harness.helper.K8sAzureCloudConfigMetadata;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.LinkedHashSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class K8sDeploymentInfoMapperTest {
  private static final String BLUE_GREEN_STAGE_COLOR = "blueGreenColor";
  private static final String NAMESPACE = "test";
  private static final String RELEASE_NAME = "release-9a854787f0afbb105cf115d533f7a54624e1ba57";

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testToDTO() {
    K8sDeploymentInfo entity =
        K8sDeploymentInfo.builder()
            .blueGreenStageColor(BLUE_GREEN_STAGE_COLOR)
            .namespaces(new LinkedHashSet<>(Collections.singleton(NAMESPACE)))
            .releaseName(RELEASE_NAME)
            .cloudConfigMetadata(
                K8sAzureCloudConfigMetadata.builder().clusterName("clusterName").subscription("subscription").build())
            .build();
    K8sDeploymentInfoDTO dto = K8sDeploymentInfoMapper.toDTO(entity);
    assertEquals(BLUE_GREEN_STAGE_COLOR, dto.getBlueGreenStageColor());
    assertTrue(dto.getNamespaces().contains(NAMESPACE));
    assertThat(dto.getCloudConfigMetadata()).isNotNull();
    assertThat(dto.getCloudConfigMetadata().getClusterName()).isEqualTo("clusterName");
    assertThat(dto.getCloudConfigMetadata()).isInstanceOf(K8sAzureCloudConfigMetadata.class);
    K8sAzureCloudConfigMetadata k8sAzureCloudConfigMetadata =
        (K8sAzureCloudConfigMetadata) dto.getCloudConfigMetadata();
    assertThat(k8sAzureCloudConfigMetadata.getSubscription()).isEqualTo("subscription");
    assertEquals(RELEASE_NAME, dto.getReleaseName());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testToEntity() {
    K8sDeploymentInfoDTO dto =
        K8sDeploymentInfoDTO.builder()
            .blueGreenStageColor(BLUE_GREEN_STAGE_COLOR)
            .namespaces(new LinkedHashSet<>(Collections.singleton(NAMESPACE)))
            .releaseName(RELEASE_NAME)
            .cloudConfigMetadata(
                K8sAzureCloudConfigMetadata.builder().clusterName("clusterName").subscription("subscription").build())
            .build();
    K8sDeploymentInfo entity = K8sDeploymentInfoMapper.toEntity(dto);
    assertEquals(BLUE_GREEN_STAGE_COLOR, entity.getBlueGreenStageColor());
    assertTrue(entity.getNamespaces().contains(NAMESPACE));
    assertThat(entity.getCloudConfigMetadata()).isNotNull();
    assertThat(entity.getCloudConfigMetadata().getClusterName()).isEqualTo("clusterName");
    assertThat(entity.getCloudConfigMetadata()).isInstanceOf(K8sAzureCloudConfigMetadata.class);
    K8sAzureCloudConfigMetadata k8sAzureCloudConfigMetadata =
        (K8sAzureCloudConfigMetadata) entity.getCloudConfigMetadata();
    assertThat(k8sAzureCloudConfigMetadata.getSubscription()).isEqualTo("subscription");
    assertEquals(RELEASE_NAME, entity.getReleaseName());
  }
}
