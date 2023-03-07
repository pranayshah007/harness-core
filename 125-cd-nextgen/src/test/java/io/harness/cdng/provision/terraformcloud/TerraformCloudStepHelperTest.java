/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud;

import static io.harness.cdng.provision.terraformcloud.TerraformCloudTestStepUtils.TFC_SWEEPING_OUTPUT_IDENTIFIER;
import static io.harness.delegate.beans.FileBucket.TERRAFORM_CLOUD_POLICY_CHECKS;
import static io.harness.delegate.beans.FileBucket.TERRAFORM_PLAN_JSON;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;
import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.fileservice.FileServiceClient;
import io.harness.cdng.fileservice.FileServiceClientFactory;
import io.harness.cdng.provision.terraform.executions.RunDetails;
import io.harness.cdng.provision.terraform.executions.TerraformCloudPlanExecutionDetails;
import io.harness.cdng.provision.terraform.executions.TerraformCloudPlanExecutionDetails.TerraformCloudPlanExecutionDetailsKeys;
import io.harness.cdng.provision.terraformcloud.executiondetails.TerraformCloudPlanExecutionDetailsService;
import io.harness.cdng.provision.terraformcloud.output.TerraformCloudPlanOutput;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialSpecDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudTokenCredentialsDTO;
import io.harness.delegate.task.terraformcloud.cleanup.TerraformCloudCleanupTaskParams;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudRunTaskResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.TaskType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudStepHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private FileServiceClientFactory fileService;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private TerraformCloudPlanExecutionDetailsService terraformCloudPlanExecutionDetailsService;
  @Mock private TerraformCloudConnectorDTO mockConnectorDTO;
  @Mock private EncryptionHelper encryptionHelper;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @InjectMocks @Spy private TerraformCloudStepHelper helper;

  private TerraformCloudTestStepUtils utils = new TerraformCloudTestStepUtils();

  @Before
  public void setup() throws IOException {}

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGenerateFullIdentifier() {
    String provisionerId = "provisionerId";
    String identifier = helper.generateFullIdentifier(provisionerId, utils.getAmbiance());
    assertThat(identifier).isEqualTo("test-account/test-org/test-project/provisionerId");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGenerateFullIdentifierWithSpace() {
    String provisionerId = "provisioner id";
    assertThatThrownBy(() -> helper.generateFullIdentifier(provisionerId, utils.getAmbiance()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Provisioner Identifier cannot contain special characters or spaces:");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetTerraformCloudConnectorWhenPlan() {
    when(cdStepHelper.getConnector("tcConnectorRef", utils.getAmbiance()))
        .thenReturn(ConnectorInfoDTO.builder()
                        .connectorType(ConnectorType.TERRAFORM_CLOUD)
                        .connectorConfig(mockConnectorDTO)
                        .identifier("tcConnectorRef")
                        .build());
    TerraformCloudConnectorDTO terraformCloudConnector =
        helper.getTerraformCloudConnector(utils.getPlanSpecParameters(), utils.getAmbiance());
    assertThat(terraformCloudConnector).isEqualTo(mockConnectorDTO);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetTerraformCloudConnectorWhenApply() {
    when(executionSweepingOutputService.resolveOptional(
             utils.getAmbiance(), RefObjectUtils.getSweepingOutputRefObject(TFC_SWEEPING_OUTPUT_IDENTIFIER)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(TerraformCloudPlanOutput.builder()
                                    .terraformCloudConnectorRef("connectorPlanRef")
                                    .runId("run-123")
                                    .build())
                        .build());
    when(cdStepHelper.getConnector("connectorPlanRef", utils.getAmbiance()))
        .thenReturn(ConnectorInfoDTO.builder()
                        .connectorType(ConnectorType.TERRAFORM_CLOUD)
                        .connectorConfig(mockConnectorDTO)
                        .identifier("tcConnectorRef")
                        .build());

    TerraformCloudConnectorDTO terraformCloudConnector =
        helper.getTerraformCloudConnector(utils.getApplySpecParameters(), utils.getAmbiance());
    assertThat(terraformCloudConnector).isEqualTo(mockConnectorDTO);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testSaveTerraformCloudPlanOutput() {
    ArgumentCaptor<TerraformCloudPlanOutput> planOutputArgumentCaptor =
        ArgumentCaptor.forClass(TerraformCloudPlanOutput.class);
    TerraformCloudRunTaskResponse response = TerraformCloudRunTaskResponse.builder().runId("run-123").build();
    helper.saveTerraformCloudPlanOutput(utils.getPlanSpecParameters(), response, utils.getAmbiance());
    verify(executionSweepingOutputService, times(1))
        .consume(any(), eq(TFC_SWEEPING_OUTPUT_IDENTIFIER), planOutputArgumentCaptor.capture(),
            eq(StepOutcomeGroup.STAGE.name()));
    assertThat(planOutputArgumentCaptor.getValue().getTerraformCloudConnectorRef()).isEqualTo("tcConnectorRef");
    assertThat(planOutputArgumentCaptor.getValue().getRunId()).isEqualTo("run-123");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetPlanRunIdFromSweepingOutput() {
    when(executionSweepingOutputService.resolveOptional(
             utils.getAmbiance(), RefObjectUtils.getSweepingOutputRefObject(TFC_SWEEPING_OUTPUT_IDENTIFIER)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(TerraformCloudPlanOutput.builder()
                                    .terraformCloudConnectorRef("connectorPlanRef")
                                    .runId("run-123")
                                    .build())
                        .build());
    String runId = helper.getPlanRunId("provisionerId", utils.getAmbiance());
    assertThat(runId).isEqualTo("run-123");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testSaveTerraformCloudPlanExecutionDetails() {
    String provisionerIdentifier = "provisionerIdentifier";
    String policyCheckFileJsonId = "policyCheckFileJsonId";
    String planFileJsonId = "planFileJsonId";
    RunDetails runDetails = RunDetails.builder().build();
    Ambiance ambiance = utils.getAmbiance();
    ArgumentCaptor<TerraformCloudPlanExecutionDetails> captor =
        ArgumentCaptor.forClass(TerraformCloudPlanExecutionDetails.class);

    helper.saveTerraformCloudPlanExecutionDetails(
        ambiance, planFileJsonId, policyCheckFileJsonId, provisionerIdentifier, runDetails);

    verify(terraformCloudPlanExecutionDetailsService).save(captor.capture());
    TerraformCloudPlanExecutionDetails executionDetails = captor.getValue();
    assertThat(executionDetails.getRunDetails()).isEqualTo(runDetails);
    assertThat(executionDetails.getTfcPolicyChecksFileId()).isEqualTo(policyCheckFileJsonId);
    assertThat(executionDetails.getTfPlanJsonFieldId()).isEqualTo(planFileJsonId);
    assertThat(executionDetails.getProvisionerId()).isEqualTo(provisionerIdentifier);
    assertThat(executionDetails.getAccountIdentifier()).isEqualTo("test-account");
    assertThat(executionDetails.getOrgIdentifier()).isEqualTo("test-org");
    assertThat(executionDetails.getProjectIdentifier()).isEqualTo("test-project");
    assertThat(executionDetails.getPipelineExecutionId()).isEqualTo("planExecutionId");
    assertThat(executionDetails.getStageExecutionId()).isEqualTo("stageExecutionId");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testUpdateRunDetails() {
    String runId = "runId";
    Ambiance ambiance = utils.getAmbiance();
    ArgumentCaptor<Scope> captor = ArgumentCaptor.forClass(Scope.class);
    ArgumentCaptor<Map<String, Object>> updatesCaptor = ArgumentCaptor.forClass(Map.class);

    helper.updateRunDetails(ambiance, runId);

    verify(terraformCloudPlanExecutionDetailsService)
        .updateTerraformCloudPlanExecutionDetails(
            captor.capture(), eq("planExecutionId"), eq(runId), updatesCaptor.capture());

    Scope scope = captor.getValue();
    assertThat(scope.getOrgIdentifier()).isEqualTo("test-org");
    assertThat(scope.getAccountIdentifier()).isEqualTo("test-account");
    assertThat(scope.getProjectIdentifier()).isEqualTo("test-project");

    Map<String, Object> updates = updatesCaptor.getValue();
    assertThat(updates.get(TerraformCloudPlanExecutionDetailsKeys.runDetails)).isNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testCleanupTfPlanJson() {
    FileServiceClient fileServiceClient = mock(FileServiceClient.class);
    List<TerraformCloudPlanExecutionDetails> terraformCloudPlanExecutionDetailsList =
        Arrays.asList(TerraformCloudPlanExecutionDetails.builder()
                          .tfPlanJsonFieldId("tfPlanJsonFieldId")
                          .tfPlanFileBucket(TERRAFORM_PLAN_JSON.name())
                          .build(),
            TerraformCloudPlanExecutionDetails.builder()
                .tfPlanJsonFieldId("tfPlanJsonFieldId2")
                .tfPlanFileBucket(TERRAFORM_PLAN_JSON.name())
                .build());
    doReturn(fileServiceClient).when(fileService).get();

    helper.cleanupTfPlanJson(terraformCloudPlanExecutionDetailsList);

    verify(fileServiceClient, times(1)).deleteFile(eq("tfPlanJsonFieldId"), eq(TERRAFORM_PLAN_JSON));
    verify(fileServiceClient, times(1)).deleteFile(eq("tfPlanJsonFieldId2"), eq(TERRAFORM_PLAN_JSON));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testCleanupPolicyCheckJson() {
    FileServiceClient fileServiceClient = mock(FileServiceClient.class);
    List<TerraformCloudPlanExecutionDetails> terraformCloudPlanExecutionDetailsList =
        Arrays.asList(TerraformCloudPlanExecutionDetails.builder()
                          .tfcPolicyChecksFileId("tfcPolicyChecksFileId")
                          .tfcPolicyChecksFileBucket(TERRAFORM_CLOUD_POLICY_CHECKS.name())
                          .build(),
            TerraformCloudPlanExecutionDetails.builder()
                .tfcPolicyChecksFileId("tfcPolicyChecksFileId2")
                .tfcPolicyChecksFileBucket(TERRAFORM_CLOUD_POLICY_CHECKS.name())
                .build());
    doReturn(fileServiceClient).when(fileService).get();

    helper.cleanupPolicyCheckJson(terraformCloudPlanExecutionDetailsList);

    verify(fileServiceClient, times(1)).deleteFile(eq("tfcPolicyChecksFileId"), eq(TERRAFORM_CLOUD_POLICY_CHECKS));
    verify(fileServiceClient, times(1)).deleteFile(eq("tfcPolicyChecksFileId2"), eq(TERRAFORM_CLOUD_POLICY_CHECKS));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetEncryptionDetail() {
    TerraformCloudCredentialSpecDTO terraformCloudCredentialSpecDTO =
        TerraformCloudTokenCredentialsDTO.builder().build();
    Ambiance ambiance = utils.getAmbiance();
    TerraformCloudConnectorDTO cloudConnectorDTO =
        TerraformCloudConnectorDTO.builder()
            .credential(TerraformCloudCredentialDTO.builder().spec(terraformCloudCredentialSpecDTO).build())
            .build();

    helper.getEncryptionDetail(ambiance, cloudConnectorDTO);

    verify(encryptionHelper).getEncryptionDetail(eq(terraformCloudCredentialSpecDTO), any(), any(), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testCleanupTerraformCloudRuns() {
    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();
    TerraformCloudConnectorDTO connectorDTO = TerraformCloudConnectorDTO.builder().build();
    Ambiance ambiance = utils.getAmbiance();
    List<TerraformCloudPlanExecutionDetails> terraformCloudPlanExecutionDetailsList = Arrays.asList(
        TerraformCloudPlanExecutionDetails.builder().runDetails(RunDetails.builder().runId("runId").build()).build(),
        TerraformCloudPlanExecutionDetails.builder().runDetails(RunDetails.builder().build()).build());
    doReturn(ConnectorInfoDTO.builder().connectorConfig(connectorDTO).build())
        .when(cdStepHelper)
        .getConnector(any(), any());
    doReturn(encryptionDetails).when(helper).getEncryptionDetail(any(), any());
    doReturn("taskId").when(delegateGrpcClientWrapper).submitAsyncTaskV2(any(), any());
    ArgumentCaptor<DelegateTaskRequest> captor = ArgumentCaptor.forClass(DelegateTaskRequest.class);

    helper.cleanupTerraformCloudRuns(terraformCloudPlanExecutionDetailsList, ambiance);

    verify(delegateGrpcClientWrapper).submitAsyncTaskV2(captor.capture(), any());
    verify(waitNotifyEngine).waitForAllOn(eq(NG_ORCHESTRATION), any(), eq("taskId"));

    DelegateTaskRequest delegateTaskRequest = captor.getValue();
    TerraformCloudCleanupTaskParams taskParams =
        (TerraformCloudCleanupTaskParams) delegateTaskRequest.getTaskParameters();
    assertThat(delegateTaskRequest.getTaskType()).isEqualTo(TaskType.TERRAFORM_CLOUD_CLEANUP_TASK_NG.name());
    assertThat(taskParams.getTerraformCloudConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(taskParams.getEncryptionDetails()).isEqualTo(encryptionDetails);
    assertThat(taskParams.getRunId()).isEqualTo("runId");
  }
}
