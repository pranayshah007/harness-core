/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.deploymentsummary;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.dtos.DeploymentSummaryDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.K8sDeploymentInfoDTO;
import io.harness.entities.DeploymentSummary;
import io.harness.entities.deploymentinfo.DeploymentInfo;
import io.harness.entities.deploymentinfo.K8sDeploymentInfo;
import io.harness.repositories.deploymentsummary.DeploymentSummaryRepository;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class DeploymentSummaryServiceImplTest extends InstancesTestBase {
  private final String DEPLOYMENT_SUMMARY_ID = "id";
  private final String INSTANCE_SYNC_KEY = "key";
  @Mock DeploymentSummaryRepository deploymentSummaryRepository;
  @InjectMocks DeploymentSummaryServiceImpl deploymentSummaryService;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void saveTest() {
    DeploymentInfoDTO deploymentInfoDTO = K8sDeploymentInfoDTO.builder().build();
    DeploymentSummaryDTO deploymentSummaryDTO =
        DeploymentSummaryDTO.builder().deploymentInfoDTO(deploymentInfoDTO).build();
    DeploymentInfo deploymentInfo = K8sDeploymentInfo.builder().build();
    DeploymentSummary deploymentSummary = DeploymentSummary.builder().deploymentInfo(deploymentInfo).build();
    when(deploymentSummaryRepository.save(deploymentSummary)).thenReturn(deploymentSummary);
    assertThat(deploymentSummaryService.save(deploymentSummaryDTO).getDeploymentInfoDTO()).isEqualTo(deploymentInfoDTO);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getByDeploymentSummaryIdTest() {
    DeploymentInfoDTO deploymentInfoDTO = K8sDeploymentInfoDTO.builder().build();
    DeploymentSummaryDTO deploymentSummaryDTO =
        DeploymentSummaryDTO.builder().deploymentInfoDTO(deploymentInfoDTO).build();
    DeploymentInfo deploymentInfo = K8sDeploymentInfo.builder().build();
    DeploymentSummary deploymentSummary = DeploymentSummary.builder().deploymentInfo(deploymentInfo).build();
    when(deploymentSummaryRepository.findById(DEPLOYMENT_SUMMARY_ID)).thenReturn(Optional.of(deploymentSummary));
    assertThat(deploymentSummaryService.getByDeploymentSummaryId(DEPLOYMENT_SUMMARY_ID).get().getDeploymentInfoDTO())
        .isEqualTo(deploymentInfoDTO);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getNthDeploymentSummaryFromNowTest() {
    DeploymentInfoDTO deploymentInfoDTO = K8sDeploymentInfoDTO.builder().build();
    DeploymentSummaryDTO deploymentSummaryDTO =
        DeploymentSummaryDTO.builder().deploymentInfoDTO(deploymentInfoDTO).build();
    DeploymentInfo deploymentInfo = K8sDeploymentInfo.builder().build();
    DeploymentSummary deploymentSummary = DeploymentSummary.builder().deploymentInfo(deploymentInfo).build();
    when(deploymentSummaryRepository.fetchNthRecordFromNow(2, INSTANCE_SYNC_KEY))
        .thenReturn(Optional.of(deploymentSummary));
    assertThat(
        deploymentSummaryService.getNthDeploymentSummaryFromNow(2, INSTANCE_SYNC_KEY).get().getDeploymentInfoDTO())
        .isEqualTo(deploymentInfoDTO);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getLatestByInstanceKeyTest() {
    DeploymentInfoDTO deploymentInfoDTO = K8sDeploymentInfoDTO.builder().build();
    DeploymentSummaryDTO deploymentSummaryDTO =
        DeploymentSummaryDTO.builder().deploymentInfoDTO(deploymentInfoDTO).build();
    DeploymentInfo deploymentInfo = K8sDeploymentInfo.builder().build();
    DeploymentSummary deploymentSummary = DeploymentSummary.builder().deploymentInfo(deploymentInfo).build();
    when(deploymentSummaryRepository.fetchNthRecordFromNow(1, INSTANCE_SYNC_KEY))
        .thenReturn(Optional.of(deploymentSummary));
    deploymentSummaryService.getLatestByInstanceKey(INSTANCE_SYNC_KEY);
    verify(deploymentSummaryRepository, times(1)).fetchNthRecordFromNow(1, INSTANCE_SYNC_KEY);
  }
}
