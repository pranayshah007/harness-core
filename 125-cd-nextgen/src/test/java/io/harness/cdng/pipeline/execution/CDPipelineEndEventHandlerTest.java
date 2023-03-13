/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline.execution;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.pipeline.executions.CDPipelineEndEventHandler;
import io.harness.cdng.pipeline.helpers.CDPipelineInstrumentationHelper;
import io.harness.cdng.provision.terraform.TerraformStepHelper;
import io.harness.cdng.provision.terraform.executions.TFPlanExecutionDetailsKey;
import io.harness.cdng.provision.terraform.executions.TerraformCloudPlanExecutionDetails;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExecutionDetails;
import io.harness.cdng.provision.terraformcloud.TerraformCloudStepHelper;
import io.harness.cdng.provision.terraformcloud.executiondetails.TerraformCloudPlanExecutionDetailsService;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.repositories.executions.CDAccountExecutionMetadataRepository;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class CDPipelineEndEventHandlerTest extends CategoryTest {
  private static final String STAGE_EXECUTION_ID = "stageExecutionId";
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private CDAccountExecutionMetadataRepository cdAccountExecutionMetadataRepository;
  @Mock private TerraformStepHelper helper;
  @Mock private CDPipelineInstrumentationHelper cdPipelineInstrumentationHelper;
  @Mock private AccountService accountService;
  @Mock private TerraformCloudPlanExecutionDetailsService terraformCloudPlanExecutionDetailsService;
  @Mock private TerraformCloudStepHelper terraformCloudStepHelper;
  @InjectMocks private CDPipelineEndEventHandler cdPipelineEndEventHandler = new CDPipelineEndEventHandler();

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleEvent() {
    String panExecutionId = "panExecutionId";
    TFPlanExecutionDetailsKey tfPlanExecutionDetailsKey = mock(TFPlanExecutionDetailsKey.class);
    List<TerraformPlanExecutionDetails> terraformPlanExecutionDetailsList =
        Collections.singletonList(mock(TerraformPlanExecutionDetails.class));
    List<TerraformCloudPlanExecutionDetails> terraformCloudPlanExecutionDetailsList =
        Collections.singletonList(mock(TerraformCloudPlanExecutionDetails.class));
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(panExecutionId)
            .setStageExecutionId(STAGE_EXECUTION_ID)
            .addLevels(Level.newBuilder()
                           .setStepType(
                               StepType.newBuilder().setType(ExecutionNodeType.DEPLOYMENT_STAGE_STEP.getName()).build())
                           .build())
            .setMetadata(
                ExecutionMetadata.newBuilder()
                    .setPipelineIdentifier("pipelineId")
                    .setTriggerInfo(
                        ExecutionTriggerInfo.newBuilder()
                            .setTriggeredBy(TriggeredBy.newBuilder().putExtraInfo("email", "test@harness.io").build())
                            .build())
                    .build())
            .build();
    doReturn(mock(AccountDTO.class)).when(accountService).getAccount(any());
    doReturn(tfPlanExecutionDetailsKey).when(helper).createTFPlanExecutionDetailsKey(eq(ambiance));
    doReturn(terraformPlanExecutionDetailsList)
        .when(helper)
        .getAllPipelineTFPlanExecutionDetails(eq(tfPlanExecutionDetailsKey));
    doReturn(terraformCloudPlanExecutionDetailsList)
        .when(terraformCloudPlanExecutionDetailsService)
        .listAllPipelineTFCloudPlanExecutionDetails(any(), any());

    cdPipelineEndEventHandler.handleEvent(
        OrchestrationEvent.builder().status(Status.SUCCEEDED).ambiance(ambiance).build());

    verify(helper).createTFPlanExecutionDetailsKey(ambiance);
    verify(helper).getAllPipelineTFPlanExecutionDetails(tfPlanExecutionDetailsKey);
    verify(helper).cleanupTerraformVaultSecret(ambiance, terraformPlanExecutionDetailsList, panExecutionId);
    verify(helper).cleanupTfPlanJson(terraformPlanExecutionDetailsList);
    verify(helper).cleanupTfPlanHumanReadable(terraformPlanExecutionDetailsList);
    verify(helper).cleanupAllTerraformPlanExecutionDetails(tfPlanExecutionDetailsKey);

    verify(terraformCloudStepHelper).cleanupTfPlanJson(terraformCloudPlanExecutionDetailsList);
    verify(terraformCloudStepHelper).cleanupPolicyCheckJson(terraformCloudPlanExecutionDetailsList);
    verify(terraformCloudStepHelper).cleanupTerraformCloudRuns(terraformCloudPlanExecutionDetailsList, ambiance);
    verify(terraformCloudPlanExecutionDetailsService).deleteAllTerraformCloudPlanExecutionDetails(any(), any());
  }
}
