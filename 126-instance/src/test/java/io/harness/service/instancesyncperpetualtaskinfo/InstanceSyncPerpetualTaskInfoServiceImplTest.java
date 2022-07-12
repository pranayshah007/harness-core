/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtaskinfo;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.dtos.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoDTO;
import io.harness.entities.instancesyncperpetualtaskinfo.DeploymentInfoDetails;
import io.harness.entities.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfo;
import io.harness.entities.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfo.InstanceSyncPerpetualTaskInfoKeys;
import io.harness.mappers.DeploymentInfoDetailsMapper;
import io.harness.repositories.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoInfoRepository;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

public class InstanceSyncPerpetualTaskInfoServiceImplTest extends InstancesTestBase {
  private static final String ID = "id";
  private static final String ACCOUNT_IDENTIFIER = "iden";
  private static final String INFRASTRUCTURE_MAPPING_ID = "infra";
  private List<DeploymentInfoDetails> deploymentInfoDetailsList = Arrays.asList();
  private static final String PERPETUAL_TASK_ID = "taskId";
  private static final long CREATED_AT = 1323;
  private static final long LAST_UPDATED_AT = 1324;
  @Mock InstanceSyncPerpetualTaskInfoInfoRepository instanceSyncPerpetualTaskInfoInfoRepository;
  @InjectMocks InstanceSyncPerpetualTaskInfoServiceImpl instanceSyncPerpetualTaskInfoService;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void findByInfrastructureMappingIdTest() {
    InstanceSyncPerpetualTaskInfo instanceSyncPerpetualTaskInfo =
        InstanceSyncPerpetualTaskInfo.builder()
            .id(ID)
            .accountIdentifier(ACCOUNT_IDENTIFIER)
            .infrastructureMappingId(INFRASTRUCTURE_MAPPING_ID)
            .deploymentInfoDetailsList(deploymentInfoDetailsList)
            .perpetualTaskId(PERPETUAL_TASK_ID)
            .createdAt(CREATED_AT)
            .lastUpdatedAt(LAST_UPDATED_AT)
            .build();
    when(instanceSyncPerpetualTaskInfoInfoRepository.findByInfrastructureMappingId(INFRASTRUCTURE_MAPPING_ID))
        .thenReturn(Optional.of(instanceSyncPerpetualTaskInfo));
    InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO =
        InstanceSyncPerpetualTaskInfoDTO.builder()
            .id(ID)
            .accountIdentifier(ACCOUNT_IDENTIFIER)
            .infrastructureMappingId(INFRASTRUCTURE_MAPPING_ID)
            .deploymentInfoDetailsDTOList(Arrays.asList())
            .perpetualTaskId(PERPETUAL_TASK_ID)
            .createdAt(CREATED_AT)
            .lastUpdatedAt(LAST_UPDATED_AT)
            .build();
    assertThat(instanceSyncPerpetualTaskInfoService.findByInfrastructureMappingId(INFRASTRUCTURE_MAPPING_ID))
        .isEqualTo(Optional.of(instanceSyncPerpetualTaskInfoDTO));
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void findByPerpetualTaskIdTest() {
    InstanceSyncPerpetualTaskInfo instanceSyncPerpetualTaskInfo =
        InstanceSyncPerpetualTaskInfo.builder()
            .id(ID)
            .accountIdentifier(ACCOUNT_IDENTIFIER)
            .infrastructureMappingId(INFRASTRUCTURE_MAPPING_ID)
            .deploymentInfoDetailsList(deploymentInfoDetailsList)
            .perpetualTaskId(PERPETUAL_TASK_ID)
            .createdAt(CREATED_AT)
            .lastUpdatedAt(LAST_UPDATED_AT)
            .build();
    when(instanceSyncPerpetualTaskInfoInfoRepository.findByAccountIdentifierAndPerpetualTaskId(
             ACCOUNT_IDENTIFIER, PERPETUAL_TASK_ID))
        .thenReturn(Optional.of(instanceSyncPerpetualTaskInfo));
    InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO =
        InstanceSyncPerpetualTaskInfoDTO.builder()
            .id(ID)
            .accountIdentifier(ACCOUNT_IDENTIFIER)
            .infrastructureMappingId(INFRASTRUCTURE_MAPPING_ID)
            .deploymentInfoDetailsDTOList(Arrays.asList())
            .perpetualTaskId(PERPETUAL_TASK_ID)
            .createdAt(CREATED_AT)
            .lastUpdatedAt(LAST_UPDATED_AT)
            .build();
    assertThat(instanceSyncPerpetualTaskInfoService.findByPerpetualTaskId(ACCOUNT_IDENTIFIER, PERPETUAL_TASK_ID))
        .isEqualTo(Optional.of(instanceSyncPerpetualTaskInfoDTO));
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void saveTest() {
    InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO =
        InstanceSyncPerpetualTaskInfoDTO.builder()
            .id(ID)
            .accountIdentifier(ACCOUNT_IDENTIFIER)
            .infrastructureMappingId(INFRASTRUCTURE_MAPPING_ID)
            .deploymentInfoDetailsDTOList(Arrays.asList())
            .perpetualTaskId(PERPETUAL_TASK_ID)
            .build();
    InstanceSyncPerpetualTaskInfo instanceSyncPerpetualTaskInfo =
        InstanceSyncPerpetualTaskInfo.builder()
            .id(ID)
            .accountIdentifier(ACCOUNT_IDENTIFIER)
            .infrastructureMappingId(INFRASTRUCTURE_MAPPING_ID)
            .deploymentInfoDetailsList(deploymentInfoDetailsList)
            .perpetualTaskId(PERPETUAL_TASK_ID)
            .build();
    when(instanceSyncPerpetualTaskInfoInfoRepository.save(instanceSyncPerpetualTaskInfo))
        .thenReturn(instanceSyncPerpetualTaskInfo);
    assertThat(instanceSyncPerpetualTaskInfoService.save(instanceSyncPerpetualTaskInfoDTO))
        .isEqualTo(instanceSyncPerpetualTaskInfoDTO);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void deleteByIdTest() {
    instanceSyncPerpetualTaskInfoService.deleteById(ACCOUNT_IDENTIFIER, ID);
    verify(instanceSyncPerpetualTaskInfoInfoRepository, times(1))
        .deleteByAccountIdentifierAndId(ACCOUNT_IDENTIFIER, ID);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void updateDeploymentInfoDetailsListTest() {
    InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO =
        InstanceSyncPerpetualTaskInfoDTO.builder()
            .id(ID)
            .accountIdentifier(ACCOUNT_IDENTIFIER)
            .infrastructureMappingId(INFRASTRUCTURE_MAPPING_ID)
            .deploymentInfoDetailsDTOList(Arrays.asList())
            .perpetualTaskId(PERPETUAL_TASK_ID)
            .build();
    InstanceSyncPerpetualTaskInfo instanceSyncPerpetualTaskInfo =
        InstanceSyncPerpetualTaskInfo.builder()
            .id(ID)
            .accountIdentifier(ACCOUNT_IDENTIFIER)
            .infrastructureMappingId(INFRASTRUCTURE_MAPPING_ID)
            .deploymentInfoDetailsList(deploymentInfoDetailsList)
            .perpetualTaskId(PERPETUAL_TASK_ID)
            .build();
    Criteria criteria = Criteria.where(InstanceSyncPerpetualTaskInfoKeys.accountIdentifier)
                            .is(instanceSyncPerpetualTaskInfoDTO.getAccountIdentifier())
                            .and(InstanceSyncPerpetualTaskInfoKeys.id)
                            .is(instanceSyncPerpetualTaskInfoDTO.getId());
    Update update = new Update().set(InstanceSyncPerpetualTaskInfoKeys.deploymentInfoDetailsList,
        DeploymentInfoDetailsMapper.toDeploymentInfoDetailsEntityList(
            instanceSyncPerpetualTaskInfoDTO.getDeploymentInfoDetailsDTOList()));
    when(instanceSyncPerpetualTaskInfoInfoRepository.update(criteria, update))
        .thenReturn(instanceSyncPerpetualTaskInfo);
    assertThat(instanceSyncPerpetualTaskInfoService.updateDeploymentInfoDetailsList(instanceSyncPerpetualTaskInfoDTO))
        .isEqualTo(instanceSyncPerpetualTaskInfoDTO);
  }
}
