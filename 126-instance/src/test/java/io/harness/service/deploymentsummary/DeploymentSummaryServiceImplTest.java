/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.deploymentsummary;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.PRATYUSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.dtos.DeploymentSummaryDTO;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.K8sDeploymentInfoDTO;
import io.harness.entities.DeploymentSummary;
import io.harness.entities.deploymentinfo.DeploymentInfo;
import io.harness.entities.deploymentinfo.K8sDeploymentInfo;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.repositories.deploymentsummary.DeploymentSummaryRepository;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class DeploymentSummaryServiceImplTest extends InstancesTestBase {
  private static final String INFRA_MAPPING_ID = "TEST_INFRA_MAPPING_ID";
  private static final String ACCOUNT_ID = "TEST_ACCOUNT_ID";
  private static final String ORG_ID = "TEST_ORG_ID";
  private static final String PROJECT_ID = "TEST_PROJECT_ID";
  private static final String ENV_ID = "TEST_ENV_ID";
  private static final String SERVICE_ID = "TEST_SERVICE_ID";
  private static final String INFRA_KEY = "TEST_INFRA_KEY";
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
  @Owner(developers = {PIYUSH_BHUWALKA, PRATYUSH})
  @Category(UnitTests.class)
  public void getNthDeploymentSummaryFromNowTest() {
    int N = 2;
    InfrastructureMappingDTO infrastructureMappingDTO = mockInfraMappingDTO();
    fetchNthRecordFromNowTest(N, infrastructureMappingDTO, null);
    fetchNthRecordFromNowTest(N, infrastructureMappingDTO, false);
  }

  private void fetchNthRecordFromNowTest(
      int N, InfrastructureMappingDTO infrastructureMappingDTO, Boolean isRollbackDeployment) {
    DeploymentInfoDTO deploymentInfoDTO = K8sDeploymentInfoDTO.builder().build();
    DeploymentInfo deploymentInfo = K8sDeploymentInfo.builder().build();
    DeploymentSummary deploymentSummary = DeploymentSummary.builder().deploymentInfo(deploymentInfo).build();
    when(deploymentSummaryRepository.fetchNthRecordFromNow(
             N, INSTANCE_SYNC_KEY, infrastructureMappingDTO, isRollbackDeployment))
        .thenReturn(Optional.of(deploymentSummary));
    assertThat(deploymentSummaryService.getNthDeploymentSummaryFromNow(N, INSTANCE_SYNC_KEY, infrastructureMappingDTO)
                   .get()
                   .getDeploymentInfoDTO())
        .isEqualTo(deploymentInfoDTO);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getLatestByInstanceKeyTest() {
    InfrastructureMappingDTO infrastructureMappingDTO = mockInfraMappingDTO();
    DeploymentInfo deploymentInfo = K8sDeploymentInfo.builder().build();
    DeploymentSummary deploymentSummary = DeploymentSummary.builder().deploymentInfo(deploymentInfo).build();
    when(deploymentSummaryRepository.fetchNthRecordFromNow(1, INSTANCE_SYNC_KEY, infrastructureMappingDTO, null))
        .thenReturn(Optional.of(deploymentSummary));
    deploymentSummaryService.getLatestByInstanceKey(INSTANCE_SYNC_KEY, infrastructureMappingDTO);
    verify(deploymentSummaryRepository, times(1))
        .fetchNthRecordFromNow(1, INSTANCE_SYNC_KEY, infrastructureMappingDTO, null);
  }

  private InfrastructureMappingDTO mockInfraMappingDTO() {
    return InfrastructureMappingDTO.builder()
        .id(INFRA_MAPPING_ID)
        .accountIdentifier(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .infrastructureKind(InfrastructureKind.KUBERNETES_DIRECT)
        .envIdentifier(ENV_ID)
        .serviceIdentifier(SERVICE_ID)
        .infrastructureKey(INFRA_KEY)
        .build();
  }
}
