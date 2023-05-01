/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.rollback;

import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.dtos.rollback.PostProdRollbackCheckDTO;
import io.harness.dtos.rollback.PostProdRollbackResponseDTO;
import io.harness.entities.Instance;
import io.harness.entities.InstanceType;
import io.harness.exception.InvalidRequestException;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.contracts.execution.Status;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.instance.InstanceRepository;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDP)
public class PostProdRollbackServiceTest extends CategoryTest {
  @Mock private InstanceRepository instanceRepository;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private PipelineServiceClient pipelineServiceClient;

  @InjectMocks @Spy private PostProdRollbackServiceImpl postProdRollbackService;
  String instanceKey = "instanceUuid";
  String infraMappingId = "instanceUuid";
  String accountId = "accountId";
  String planExecutionId = "planExecutionId";
  String orgId = "orgId";
  String projectId = "projectId";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCheckIfRollbackAllowed() {
    doReturn(false).when(cdFeatureFlagHelper).isEnabled(accountId, FeatureName.POST_PROD_ROLLBACK);

    assertThatThrownBy(() -> postProdRollbackService.checkIfRollbackAllowed(accountId, instanceKey, infraMappingId))
        .isInstanceOf(InvalidRequestException.class);

    doReturn(true).when(cdFeatureFlagHelper).isEnabled(accountId, FeatureName.POST_PROD_ROLLBACK);
    doReturn(Instance.builder()
                 .lastPipelineExecutionId(planExecutionId)
                 .stageStatus(Status.FAILED)
                 .infrastructureMappingId(infraMappingId)
                 .id(instanceKey)
                 .build())
        .when(instanceRepository)
        .getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, infraMappingId);

    PostProdRollbackCheckDTO response =
        postProdRollbackService.checkIfRollbackAllowed(accountId, instanceKey, infraMappingId);
    assertThat(response.isRollbackAllowed()).isFalse();

    doReturn(Instance.builder()
                 .stageStatus(Status.SUCCEEDED)
                 .instanceType(InstanceType.ASG_INSTANCE)
                 .instanceKey(instanceKey)
                 .infrastructureMappingId(infraMappingId)
                 .build())
        .when(instanceRepository)
        .getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, infraMappingId);
    response = postProdRollbackService.checkIfRollbackAllowed(accountId, instanceKey, infraMappingId);
    assertThat(response.isRollbackAllowed()).isFalse();

    doReturn(Instance.builder()
                 .stageStatus(Status.SUCCEEDED)
                 .instanceType(InstanceType.K8S_INSTANCE)
                 .instanceKey(instanceKey)
                 .infrastructureMappingId(infraMappingId)
                 .build())
        .when(instanceRepository)
        .getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, infraMappingId);
    response = postProdRollbackService.checkIfRollbackAllowed(accountId, instanceKey, infraMappingId);
    assertThat(response.isRollbackAllowed()).isTrue();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testTriggerRollback() {
    String newPlanExecutionId = "newPlanExecutionId";
    String stageNodeExecutionId = "stageNodeExecutionId";
    Map<String, Map> responseMap = new HashMap<>();
    Map<String, String> planExecutionMap = new HashMap<>();
    planExecutionMap.put("uuid", newPlanExecutionId);
    responseMap.put("planExecution", planExecutionMap);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(responseMap);

    doReturn(false).when(cdFeatureFlagHelper).isEnabled(accountId, FeatureName.POST_PROD_ROLLBACK);
    assertThatThrownBy(() -> postProdRollbackService.checkIfRollbackAllowed(accountId, instanceKey, infraMappingId))
        .isInstanceOf(InvalidRequestException.class);

    doReturn(true).when(cdFeatureFlagHelper).isEnabled(accountId, FeatureName.POST_PROD_ROLLBACK);

    doThrow(new InvalidRequestException("invalid request"))
        .when(pipelineServiceClient)
        .triggerPostExecutionRollback(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    assertThatThrownBy(() -> postProdRollbackService.triggerRollback(accountId, instanceKey, infraMappingId))
        .isInstanceOf(InvalidRequestException.class);

    doReturn(null)
        .when(pipelineServiceClient)
        .triggerPostExecutionRollback(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    doReturn(Instance.builder()
                 .accountIdentifier(accountId)
                 .orgIdentifier(orgId)
                 .projectIdentifier(projectId)
                 .stageNodeExecutionId(stageNodeExecutionId)
                 .lastPipelineExecutionId(planExecutionId)
                 .stageStatus(Status.SUCCEEDED)
                 .instanceType(InstanceType.K8S_INSTANCE)
                 .instanceKey(instanceKey)
                 .infrastructureMappingId(infraMappingId)
                 .build())
        .when(instanceRepository)
        .getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, infraMappingId);
    PostProdRollbackResponseDTO response =
        postProdRollbackService.triggerRollback(accountId, instanceKey, infraMappingId);

    verify(pipelineServiceClient, times(1))
        .triggerPostExecutionRollback(
            planExecutionId, accountId, orgId, projectId, "getPipelineId", stageNodeExecutionId);
    assertThat(response.isRollbackTriggered()).isTrue();
    assertThat(response.getPlanExecutionId()).isEqualTo(newPlanExecutionId);
  }
}
