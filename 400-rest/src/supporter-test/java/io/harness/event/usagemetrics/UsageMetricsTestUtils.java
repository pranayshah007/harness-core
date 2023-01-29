/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.usagemetrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.ArtifactMetadata;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.ExecutionStatus;
import io.harness.event.timeseries.processor.EventProcessor;
import io.harness.event.timeseries.processor.StepEventProcessor;

import software.wings.api.ApprovalStateExecutionData;
import software.wings.api.DeploymentStepTimeSeriesEvent;
import software.wings.api.DeploymentTimeSeriesEvent;
import software.wings.api.ExecutionInterruptTimeSeriesEvent;
import software.wings.beans.EnvSummary;
import software.wings.beans.PipelineExecution.Builder;
import software.wings.beans.PipelineStageExecution;
import software.wings.beans.WorkflowExecution;
import software.wings.persistence.artifact.Artifact;
import software.wings.persistence.artifact.Artifact.ArtifactKeys;
import software.wings.service.impl.event.timeseries.TimeSeriesEventInfo;
import software.wings.sm.ExecutionInterrupt;
import software.wings.sm.ExecutionInterrupt.ExecutionInterruptBuilder;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionData.StateExecutionDataBuilder;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.states.ApprovalState.ApprovalStateType;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(innerTypeName = "UsageMetricsTestKeys")
public class UsageMetricsTestUtils {
  private String ACCOUNTID;
  private String APPID;
  private String WORKFLOWID;
  private String CLOUDPROVIDER1, CLOUDPROVIDER2;
  private String PIPELINEID;
  private String PIPELINEEXECUTIONID;
  private String SERVICE1;
  private String ENV1;
  private String ENV1NAME;
  private String TRIGGER1;
  private String USER1;
  private String EXECUTIONID;

  public static void validateTimeSeriesEventInfo(DeploymentTimeSeriesEvent timeSeriesEvent, int number) {
    TimeSeriesEventInfo timeSeriesEventInfo = timeSeriesEvent.getTimeSeriesEventInfo();
    assertThat(timeSeriesEventInfo).isNotNull();
    assertThat(timeSeriesEventInfo.getAccountId()).isEqualTo(UsageMetricsTestUtils.UsageMetricsTestKeys.ACCOUNTID);
    assertThat(timeSeriesEventInfo.getStringData().get(EventProcessor.EXECUTIONID))
        .isEqualTo(UsageMetricsTestUtils.UsageMetricsTestKeys.EXECUTIONID + number);
    assertThat(timeSeriesEventInfo.getLongData().get(EventProcessor.STARTTIME)).isEqualTo(100L);
    assertThat(timeSeriesEventInfo.getLongData().get(EventProcessor.ENDTIME)).isEqualTo(200L);
    assertThat(timeSeriesEventInfo.getLongData().get(EventProcessor.DURATION)).isEqualTo(100L);
    assertThat(timeSeriesEventInfo.getLongData().get(EventProcessor.ROLLBACK_DURATION)).isEqualTo(100L);
    assertThat(timeSeriesEventInfo.getStringData().get(EventProcessor.APPID))
        .isEqualTo(UsageMetricsTestUtils.UsageMetricsTestKeys.APPID);
    assertThat(timeSeriesEventInfo.getStringData().get(EventProcessor.STATUS))
        .isEqualTo(ExecutionStatus.SUCCESS.name());
    assertThat(timeSeriesEventInfo.getStringData().get(EventProcessor.PARENT_EXECUTION))
        .contains(UsageMetricsTestUtils.UsageMetricsTestKeys.PIPELINEEXECUTIONID);
    assertThat(timeSeriesEventInfo.getStringData().get(EventProcessor.PIPELINE))
        .contains(UsageMetricsTestUtils.UsageMetricsTestKeys.PIPELINEID);
    assertThat(timeSeriesEventInfo.getListData().get(EventProcessor.CLOUD_PROVIDER_LIST))
        .contains(UsageMetricsTestUtils.UsageMetricsTestKeys.CLOUDPROVIDER1);
    assertThat(timeSeriesEventInfo.getStringData().get(EventProcessor.TRIGGER_ID))
        .contains(UsageMetricsTestUtils.UsageMetricsTestKeys.TRIGGER1);
    assertThat(timeSeriesEventInfo.getStringData().get(EventProcessor.TRIGGERED_BY))
        .contains(UsageMetricsTestUtils.UsageMetricsTestKeys.USER1);
    assertThat(timeSeriesEventInfo.getListData().get(EventProcessor.ENV_LIST))
        .contains(UsageMetricsTestUtils.UsageMetricsTestKeys.ENV1);
    assertThat(timeSeriesEventInfo.getListData().get(EventProcessor.ENVTYPES)).contains(EnvironmentType.PROD.name());
    assertThat(timeSeriesEventInfo.getListData().get(EventProcessor.SERVICE_LIST))
        .contains(UsageMetricsTestUtils.UsageMetricsTestKeys.SERVICE1);
  }

  public static WorkflowExecution generateWorkflowExecution(int number) {
    Map<String, String> artifactBuildNumber = new HashMap<>();
    artifactBuildNumber.put(ArtifactKeys.metadata_buildNo, "123");
    return WorkflowExecution.builder()
        .uuid(UsageMetricsTestUtils.UsageMetricsTestKeys.EXECUTIONID + number)
        .accountId(UsageMetricsTestUtils.UsageMetricsTestKeys.ACCOUNTID)
        .appId(UsageMetricsTestUtils.UsageMetricsTestKeys.APPID)
        .status(ExecutionStatus.SUCCESS)
        .workflowId(UsageMetricsTestUtils.UsageMetricsTestKeys.WORKFLOWID)
        .startTs(100L)
        .endTs(200L)
        .cloudProviderIds(Lists.newArrayList(UsageMetricsTestUtils.UsageMetricsTestKeys.CLOUDPROVIDER1,
            UsageMetricsTestUtils.UsageMetricsTestKeys.CLOUDPROVIDER2))
        .deployedCloudProviders(Lists.newArrayList(UsageMetricsTestUtils.UsageMetricsTestKeys.CLOUDPROVIDER1,
            UsageMetricsTestUtils.UsageMetricsTestKeys.CLOUDPROVIDER2))
        .environments(Lists.newArrayList(EnvSummary.builder().environmentType(EnvironmentType.PROD).build()))
        .deployedEnvironments(Lists.newArrayList(EnvSummary.builder()
                                                     .environmentType(EnvironmentType.PROD)
                                                     .uuid(UsageMetricsTestUtils.UsageMetricsTestKeys.ENV1)
                                                     .build()))
        .pipelineExecution(
            Builder.aPipelineExecution()
                .withPipelineId(UsageMetricsTestUtils.UsageMetricsTestKeys.PIPELINEID)
                .withPipelineStageExecutions(
                    Arrays.asList(PipelineStageExecution.builder()
                                      .workflowExecutions(Arrays.asList(
                                          WorkflowExecution.builder()
                                              .envId(UsageMetricsTestUtils.UsageMetricsTestKeys.ENV1)
                                              .envType(EnvironmentType.PROD)
                                              .cloudProviderIds(Lists.newArrayList(UsageMetricsTestKeys.CLOUDPROVIDER1))
                                              .serviceIds(Arrays.asList(UsageMetricsTestKeys.SERVICE1))
                                              .build()))
                                      .build()))
                .build())
        .pipelineExecutionId(UsageMetricsTestUtils.UsageMetricsTestKeys.PIPELINEEXECUTIONID)
        .artifacts(Lists.newArrayList(
            Artifact.Builder.anArtifact().withMetadata(new ArtifactMetadata(artifactBuildNumber)).build()))
        .serviceIds(Arrays.asList(UsageMetricsTestUtils.UsageMetricsTestKeys.SERVICE1))
        .deployedServices(Arrays.asList(UsageMetricsTestUtils.UsageMetricsTestKeys.SERVICE1))
        .triggeredBy(EmbeddedUser.builder().uuid(UsageMetricsTestUtils.UsageMetricsTestKeys.USER1).build())
        .deploymentTriggerId(UsageMetricsTestUtils.UsageMetricsTestKeys.TRIGGER1)
        .duration(100L)
        .rollbackDuration(100L)
        .build();
  }

  public static StateExecutionInstance generateStateExecutionInstance() {
    Map<String, StateExecutionData> map1 = new HashMap<>();
    ApprovalStateExecutionData approvalStateExecutionData = ApprovalStateExecutionData.builder().approvedOn(2l).build();
    approvalStateExecutionData.setStartTs(2l);
    approvalStateExecutionData.setTimeoutMillis(5);
    approvalStateExecutionData.setComments("abc");
    approvalStateExecutionData.setApprovalStateType(ApprovalStateType.USER_GROUP);
    approvalStateExecutionData.setApprovedBy(EmbeddedUser.builder().uuid("user").build());
    approvalStateExecutionData.setErrorMsg("error");
    map1.put(StepEventProcessor.STEP_NAME, approvalStateExecutionData);
    StateExecutionInstance instance = StateExecutionInstance.Builder.aStateExecutionInstance()
                                          .accountId(StepEventProcessor.ACCOUNT_ID)
                                          .uuid(StepEventProcessor.ID)
                                          .appId(StepEventProcessor.APP_ID)
                                          .stateName(StepEventProcessor.STEP_NAME)
                                          .stateType("APPROVAL")
                                          .status(ExecutionStatus.FAILED)
                                          .executionUuid(StepEventProcessor.EXECUTION_ID)
                                          .stateExecutionMap(map1)
                                          .startTs(1l)
                                          .stateExecutionDataHistory(Arrays.asList(
                                              StateExecutionDataBuilder.aStateExecutionData().withStartTs(0l).build()))
                                          .endTs(2l)
                                          .build();
    instance.setStageName(StepEventProcessor.STAGE_NAME);
    instance.setWaitingForManualIntervention(false);
    return instance;
  }

  public static ExecutionInterrupt generateExecutionInterrupt() {
    return ExecutionInterruptBuilder.anExecutionInterrupt()
        .uuid(StepEventProcessor.EXECUTION_INTERRUPT_ID)
        .executionInterruptType(ExecutionInterruptType.IGNORE)
        .executionUuid(StepEventProcessor.EXECUTION_ID)
        .appId(StepEventProcessor.APP_ID)
        .stateExecutionInstanceId(StepEventProcessor.ID)
        .createdBy(EmbeddedUser.builder().uuid("user1").build())
        .lastUpdatedBy(EmbeddedUser.builder().uuid("user2").build())
        .createdAt(0l)
        .lastUpdatedAt(2l)
        .build();
  }

  public static void validateTimeSeriesEventInfo(ExecutionInterruptTimeSeriesEvent executionInterruptTimeSeriesEvent) {
    TimeSeriesEventInfo eventInfo = executionInterruptTimeSeriesEvent.getTimeSeriesEventInfo();
    assertThat(eventInfo).isNotNull();
    assertThat(eventInfo.getStringData()).isNotNull();
    assertThat(eventInfo.getLongData()).isNotNull();
    assertThat(eventInfo.getAccountId()).isEqualTo(StepEventProcessor.ACCOUNT_ID);
    assertThat(eventInfo.getStringData().get(StepEventProcessor.EXECUTION_INTERRUPT_ID))
        .isEqualTo(StepEventProcessor.EXECUTION_INTERRUPT_ID);
    assertThat(eventInfo.getStringData().get(StepEventProcessor.ID)).isEqualTo(StepEventProcessor.ID);
    assertThat(eventInfo.getStringData().get(StepEventProcessor.EXECUTION_INTERRUPT_TYPE))
        .isEqualTo(ExecutionInterruptType.IGNORE.toString());
    assertThat(eventInfo.getStringData().get(StepEventProcessor.EXECUTION_ID))
        .isEqualTo(StepEventProcessor.EXECUTION_ID);
    assertThat(eventInfo.getStringData().get(StepEventProcessor.APP_ID)).isEqualTo(StepEventProcessor.APP_ID);
    assertThat(eventInfo.getLongData().get(StepEventProcessor.EXECUTION_INTERRUPT_CREATED_AT)).isEqualTo(0l);
    assertThat(eventInfo.getStringData().get(StepEventProcessor.EXECUTION_INTERRUPT_CREATED_BY)).isEqualTo("user1");
    assertThat(eventInfo.getLongData().get(StepEventProcessor.EXECUTION_INTERRUPT_UPDATED_AT)).isEqualTo(2l);
    assertThat(eventInfo.getStringData().get(StepEventProcessor.EXECUTION_INTERRUPT_UPDATED_BY)).isEqualTo("user2");
  }

  public static void validateTimeSeriesEventInfo(DeploymentStepTimeSeriesEvent deploymentStepTimeSeriesEvent) {
    TimeSeriesEventInfo eventInfo = deploymentStepTimeSeriesEvent.getTimeSeriesEventInfo();
    assertThat(eventInfo).isNotNull();
    assertThat(eventInfo.getStringData()).isNotNull();
    assertThat(eventInfo.getLongData()).isNotNull();
    assertThat(eventInfo.getBooleanData()).isNotNull();
    assertThat(eventInfo.getAccountId()).isEqualTo(StepEventProcessor.ACCOUNT_ID);
    assertThat(eventInfo.getStringData().get(StepEventProcessor.ID)).isEqualTo(StepEventProcessor.ID);
    assertThat(eventInfo.getStringData().get(StepEventProcessor.APP_ID)).isEqualTo(StepEventProcessor.APP_ID);
    assertThat(eventInfo.getStringData().get(StepEventProcessor.STEP_NAME)).isEqualTo(StepEventProcessor.STEP_NAME);
    assertThat(eventInfo.getStringData().get(StepEventProcessor.STEP_TYPE)).isEqualTo("APPROVAL");
    assertThat(eventInfo.getStringData().get(StepEventProcessor.STATUS)).isEqualTo(ExecutionStatus.FAILED.toString());
    assertThat(eventInfo.getStringData().get(StepEventProcessor.FAILURE_DETAILS)).isEqualTo("error");
    assertThat(eventInfo.getLongData().get(StepEventProcessor.START_TIME)).isEqualTo(0l);
    assertThat(eventInfo.getLongData().get(StepEventProcessor.END_TIME)).isEqualTo(2l);
    assertThat(eventInfo.getLongData().get(StepEventProcessor.DURATION)).isEqualTo(2l);
    assertThat(eventInfo.getStringData().get(StepEventProcessor.STAGE_NAME)).isEqualTo(StepEventProcessor.STAGE_NAME);
    assertThat(eventInfo.getStringData().get(StepEventProcessor.EXECUTION_ID))
        .isEqualTo(StepEventProcessor.EXECUTION_ID);
    assertThat(eventInfo.getStringData().get(StepEventProcessor.APPROVED_BY)).isEqualTo("user");
    assertThat(eventInfo.getStringData().get(StepEventProcessor.APPROVAL_TYPE))
        .isEqualTo(ApprovalStateType.USER_GROUP.toString());
    assertThat(eventInfo.getLongData().get(StepEventProcessor.APPROVED_AT)).isEqualTo(2l);
    assertThat(eventInfo.getStringData().get(StepEventProcessor.APPROVAL_COMMENT)).isEqualTo("abc");
    assertThat(eventInfo.getLongData().get(StepEventProcessor.APPROVAL_EXPIRY)).isEqualTo(7l);
    assertThat(eventInfo.getBooleanData().get(StepEventProcessor.MANUAL_INTERVENTION)).isEqualTo(false);
  }
}
