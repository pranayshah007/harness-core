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
import io.harness.beans.ExecutionStatus;
import io.harness.event.timeseries.processor.EventProcessor;

import software.wings.api.DeploymentTimeSeriesEvent;
import software.wings.beans.EnvSummary;
import software.wings.beans.PipelineExecution.Builder;
import software.wings.beans.PipelineStageExecution;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.Artifact.ArtifactKeys;
import software.wings.service.impl.event.timeseries.TimeSeriesEventInfo;

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
                .withPipelineStageExecutions(Arrays.asList(
                    PipelineStageExecution.builder()
                        .workflowExecutions(Arrays.asList(
                            WorkflowExecution.builder()
                                .envId(UsageMetricsTestUtils.UsageMetricsTestKeys.ENV1)
                                .envType(EnvironmentType.PROD)
                                .cloudProviderIds(
                                    Lists.newArrayList(UsageMetricsTestUtils.UsageMetricsTestKeys.CLOUDPROVIDER1))
                                .serviceIds(Arrays.asList(UsageMetricsTestUtils.UsageMetricsTestKeys.SERVICE1))

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
}
