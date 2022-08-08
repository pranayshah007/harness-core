/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.metadata;

import static software.wings.beans.ElementExecutionSummary.ElementExecutionSummaryBuilder.anElementExecutionSummary;
import static software.wings.beans.PipelineExecution.Builder.aPipelineExecution;
import static software.wings.beans.artifact.Artifact.Builder.anArtifact;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.PIPELINE_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_NAME;
import static software.wings.utils.WingsTestConstants.PIPELINE_WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.USER_EMAIL;
import static software.wings.utils.WingsTestConstants.USER_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.ArtifactMetadata;
import io.harness.beans.CreatedByType;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.WorkflowType;

import software.wings.api.ServiceElement;
import software.wings.beans.BuildExecutionSummary;
import software.wings.beans.EnvSummary;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.GraphNode;
import software.wings.beans.NameValuePair;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.PipelineStageExecution;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.artifact.Artifact;
import software.wings.sm.PipelineSummary;
import software.wings.sm.StateType;

import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MetadataTestUtils {
  public WorkflowExecution preparePipelineWorkflowExecution(Instant now) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(asList(prepareArtifact(1), prepareArtifact(2)));
    return WorkflowExecution.builder()
        .uuid(PIPELINE_WORKFLOW_EXECUTION_ID)
        .workflowType(WorkflowType.PIPELINE)
        .accountId(ACCOUNT_ID)
        .appId(APP_ID)
        .appName(APP_NAME)
        .status(ExecutionStatus.SUCCESS)
        .executionArgs(executionArgs)
        .pipelineSummary(PipelineSummary.builder().pipelineId(PIPELINE_ID).pipelineName(PIPELINE_NAME).build())
        .pipelineExecution(
            aPipelineExecution()
                .withPipeline(
                    Pipeline.builder()
                        .uuid(PIPELINE_ID)
                        .name(PIPELINE_NAME)
                        .pipelineStages(Collections.singletonList(
                            PipelineStage.builder()
                                .name("ps")
                                .pipelineStageElements(Collections.singletonList(PipelineStageElement.builder()
                                                                                     .name("pse")
                                                                                     .parallelIndex(1)
                                                                                     .type(StateType.ENV_STATE.name())
                                                                                     .build()))
                                .parallel(false)
                                .build()))
                        .build())
                .withPipelineStageExecutions(Collections.singletonList(
                    PipelineStageExecution.builder()
                        .stateType(StateType.ENV_STATE.name())
                        .stateName("wf")
                        .status(ExecutionStatus.SUCCESS)
                        .workflowExecutions(Collections.singletonList(prepareWorkflowExecution(now)))
                        .build()))
                .build())
        .startTs(now.minus(1, ChronoUnit.MINUTES).toEpochMilli())
        .endTs(now.toEpochMilli())
        .tags(asList(prepareNameValuePair(1), prepareNameValuePair(2)))
        .createdByType(CreatedByType.USER)
        .triggeredBy(EmbeddedUser.builder().uuid(USER_ID).name(USER_NAME).email(USER_EMAIL).build())
        .build();
  }

  public void validatePipelineWorkflowExecutionMetadata(
      PipelineExecutionMetadata pipelineExecutionMetadata, Instant now) {
    assertThat(pipelineExecutionMetadata.getId()).isEqualTo(PIPELINE_WORKFLOW_EXECUTION_ID);
    assertThat(pipelineExecutionMetadata.getAppId()).isEqualTo(APP_ID);
    assertThat(pipelineExecutionMetadata.getExecutionType()).isEqualTo("Pipeline");
    assertThat(pipelineExecutionMetadata.getApplication()).isEqualTo(APP_NAME);
    assertThat(pipelineExecutionMetadata.getEntityName()).isEqualTo(PIPELINE_NAME);
    assertThat(pipelineExecutionMetadata.getInputArtifacts()).isNotNull();
    assertThat(pipelineExecutionMetadata.getInputArtifacts().size()).isEqualTo(2);
    assertThat(pipelineExecutionMetadata.getInputArtifacts().get(0).getBuildNo()).isEqualTo("dn1");
    assertThat(pipelineExecutionMetadata.getInputArtifacts().get(1).getBuildNo()).isEqualTo("dn2");
    assertThat(pipelineExecutionMetadata.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(pipelineExecutionMetadata.getStages()).isNotNull();
    assertThat(pipelineExecutionMetadata.getStages().size()).isEqualTo(1);
    assertThat(pipelineExecutionMetadata.getStages().get(0).getWorkflowExecution()).isNotNull();
    assertThat(pipelineExecutionMetadata.getTags()).isNotNull();
    assertThat(pipelineExecutionMetadata.getTags().stream().map(NameValuePair::getName).collect(Collectors.toList()))
        .containsExactly("n1", "n2");
    assertThat(pipelineExecutionMetadata.getTiming()).isNotNull();
    assertThat(pipelineExecutionMetadata.getTiming().getStartTime().toInstant())
        .isEqualTo(now.minus(1, ChronoUnit.MINUTES));
    assertThat(pipelineExecutionMetadata.getTiming().getEndTime().toInstant()).isEqualTo(now);
    assertThat(pipelineExecutionMetadata.getTiming().getDuration().toMinutes()).isEqualTo(1);
    assertThat(pipelineExecutionMetadata.getTriggeredBy()).isNotNull();
    assertThat(pipelineExecutionMetadata.getTriggeredBy().getType()).isEqualTo(CreatedByType.USER);
    assertThat(pipelineExecutionMetadata.getTriggeredBy().getName()).isEqualTo(USER_NAME);
    assertThat(pipelineExecutionMetadata.getTriggeredBy().getEmail()).isEqualTo(USER_EMAIL);
  }

  public WorkflowExecution prepareWorkflowExecution(Instant now) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(Collections.singletonList(prepareArtifact(1)));
    return WorkflowExecution.builder()
        .uuid(WORKFLOW_EXECUTION_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .workflowId(WORKFLOW_ID)
        .name(WORKFLOW_NAME)
        .accountId(ACCOUNT_ID)
        .appId(APP_ID)
        .appName(APP_NAME)
        .status(ExecutionStatus.SUCCESS)
        .environments(Collections.singletonList(
            EnvSummary.builder().name(ENV_NAME).environmentType(EnvironmentType.NON_PROD).build()))
        .serviceExecutionSummaries(Collections.singletonList(
            anElementExecutionSummary().withContextElement(ServiceElement.builder().name("s").build()).build()))
        .executionArgs(executionArgs)
        .artifacts(executionArgs.getArtifacts())
        .executionNode(GraphNode.builder().id("id").build())
        .startTs(now.minus(1, ChronoUnit.MINUTES).toEpochMilli())
        .endTs(now.toEpochMilli())
        .tags(asList(prepareNameValuePair(1), prepareNameValuePair(2)))
        .createdByType(CreatedByType.USER)
        .triggeredBy(EmbeddedUser.builder().uuid(USER_ID).name(USER_NAME).email(USER_EMAIL).build())
        .build();
  }

  public Artifact prepareArtifact(int idx) {
    return anArtifact()
        .withUuid("id" + idx)
        .withAccountId(ACCOUNT_ID)
        .withAppId(APP_ID)
        .withArtifactStreamId("as_id")
        .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", "buildNo")))
        .withArtifactSourceName("asn" + idx)
        .withUiDisplayName("dn" + idx)
        .withDisplayName("dn" + idx)
        .build();
  }

  public BuildExecutionSummary prepareBuildExecutionSummary(int idx) {
    return BuildExecutionSummary.builder()
        .artifactStreamId("as_id")
        .artifactSource("asn" + idx)
        .buildName("dn" + idx)
        .build();
  }

  public NameValuePair prepareNameValuePair(int idx) {
    return NameValuePair.builder().name("n" + idx).value("v" + idx).valueType("TEXT").build();
  }
}
