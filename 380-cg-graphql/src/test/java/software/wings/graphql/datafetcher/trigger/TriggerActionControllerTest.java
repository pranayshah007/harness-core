/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.trigger;

import static io.harness.rule.OwnerRule.MILAN;
import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.MANIFEST_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.beans.trigger.ArtifactSelection;
import software.wings.beans.trigger.ArtifactSelection.Type;
import software.wings.beans.trigger.ManifestSelection;
import software.wings.beans.trigger.ManifestSelection.ManifestSelectionType;
import software.wings.beans.trigger.Trigger;
import software.wings.graphql.schema.mutation.execution.input.QLExecutionType;
import software.wings.graphql.schema.type.trigger.QLArtifactConditionInput;
import software.wings.graphql.schema.type.trigger.QLArtifactSelectionInput;
import software.wings.graphql.schema.type.trigger.QLArtifactSelectionType;
import software.wings.graphql.schema.type.trigger.QLConditionType;
import software.wings.graphql.schema.type.trigger.QLCreateOrUpdateTriggerInput;
import software.wings.graphql.schema.type.trigger.QLFromTriggeringPipeline;
import software.wings.graphql.schema.type.trigger.QLFromWebhookPayload;
import software.wings.graphql.schema.type.trigger.QLLastCollected;
import software.wings.graphql.schema.type.trigger.QLLastCollectedManifest;
import software.wings.graphql.schema.type.trigger.QLLastDeployedFromPipeline;
import software.wings.graphql.schema.type.trigger.QLLastDeployedFromWorkflow;
import software.wings.graphql.schema.type.trigger.QLLastDeployedManifestFromPipeline;
import software.wings.graphql.schema.type.trigger.QLLastDeployedManifestFromWorkflow;
import software.wings.graphql.schema.type.trigger.QLManifestConditionInput;
import software.wings.graphql.schema.type.trigger.QLManifestFromTriggeringPipeline;
import software.wings.graphql.schema.type.trigger.QLManifestFromWebhookPayload;
import software.wings.graphql.schema.type.trigger.QLManifestSelectionInput;
import software.wings.graphql.schema.type.trigger.QLPipelineAction;
import software.wings.graphql.schema.type.trigger.QLPipelineConditionInput;
import software.wings.graphql.schema.type.trigger.QLTriggerActionInput;
import software.wings.graphql.schema.type.trigger.QLTriggerConditionInput;
import software.wings.graphql.schema.type.trigger.QLWebhookConditionInput;
import software.wings.graphql.schema.type.trigger.QLWebhookSource;
import software.wings.graphql.schema.type.trigger.QLWorkflowAction;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.WorkflowService;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
public class TriggerActionControllerTest extends CategoryTest {
  private static final String PIPELINE_ID = "PIPELINE_ID";
  @Mock PipelineService pipelineService;
  @Mock WorkflowService workflowService;
  @Mock ServiceResourceService serviceResourceService;
  @Mock ArtifactStreamService artifactStreamService;

  @InjectMocks TriggerActionController triggerActionController = Mockito.spy(new TriggerActionController());

  @Before
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void populateTriggerActionShouldReturnPipelineWorkflowWithFromTriggeringArtifactSourceService() {
    ArtifactSelection artifactSource = ArtifactSelection.builder()
                                           .serviceId("serviceId")
                                           .serviceName("serviceName")
                                           .type(Type.ARTIFACT_SOURCE)
                                           .build();
    List<ArtifactSelection> artifactSelections = asList(artifactSource);
    Map<String, String> variables = new HashMap<>();
    variables.put("variableKey", "variableResult");
    Trigger trigger = Trigger.builder()
                          .workflowId("workflowId")
                          .workflowName("workflowName")
                          .artifactSelections(artifactSelections)
                          .workflowVariables(variables)
                          .build();

    QLPipelineAction qlPipelineAction = (QLPipelineAction) triggerActionController.populateTriggerAction(trigger);

    assertThat(qlPipelineAction).isNotNull();
    assertThat(qlPipelineAction.getPipelineId()).isEqualTo(trigger.getWorkflowId());
    assertThat(qlPipelineAction.getPipelineName()).isEqualTo(trigger.getWorkflowName());

    assertThat(qlPipelineAction.getArtifactSelections()).isNotNull();
    assertThat(qlPipelineAction.getArtifactSelections().get(0).getServiceId())
        .isEqualTo(trigger.getArtifactSelections().get(0).getServiceId());
    assertThat(qlPipelineAction.getArtifactSelections().get(0).getServiceName())
        .isEqualTo(trigger.getArtifactSelections().get(0).getServiceName());

    assertThat(qlPipelineAction.getVariables()).isNotNull();
    assertThat(qlPipelineAction.getVariables().get(0).getValue())
        .isEqualTo(trigger.getWorkflowVariables().get("variableKey"));
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void populateTriggerActionShouldReturnPipelineWorkflowWitQLLastCollectedService() {
    ArtifactSelection artifactSource = ArtifactSelection.builder()
                                           .serviceId("serviceId")
                                           .serviceName("serviceName")
                                           .artifactStreamId("streamId")
                                           .artifactSourceName("sourceName")
                                           .artifactFilter("filter")
                                           .regex(true)
                                           .type(Type.LAST_COLLECTED)
                                           .build();
    List<ArtifactSelection> artifactSelections = asList(artifactSource);

    Map<String, String> variables = new HashMap<>();
    variables.put("variableKey", "variableResult");
    Trigger trigger = Trigger.builder()
                          .workflowId("workflowId")
                          .workflowName("workflowName")
                          .artifactSelections(artifactSelections)
                          .workflowVariables(variables)
                          .build();

    QLPipelineAction qlPipelineAction = (QLPipelineAction) triggerActionController.populateTriggerAction(trigger);

    assertThat(qlPipelineAction).isNotNull();
    assertThat(qlPipelineAction.getPipelineId()).isEqualTo(trigger.getWorkflowId());
    assertThat(qlPipelineAction.getPipelineName()).isEqualTo(trigger.getWorkflowName());

    assertThat(qlPipelineAction.getArtifactSelections()).isNotNull();
    QLLastCollected qlLastCollected = (QLLastCollected) qlPipelineAction.getArtifactSelections().get(0);
    assertThat(qlLastCollected.getServiceId()).isEqualTo(trigger.getArtifactSelections().get(0).getServiceId());
    assertThat(qlLastCollected.getServiceName()).isEqualTo(trigger.getArtifactSelections().get(0).getServiceName());
    assertThat(qlLastCollected.getArtifactSourceId())
        .isEqualTo(trigger.getArtifactSelections().get(0).getArtifactStreamId());
    assertThat(qlLastCollected.getArtifactSourceName())
        .isEqualTo(trigger.getArtifactSelections().get(0).getArtifactSourceName());
    assertThat(qlLastCollected.getArtifactFilter())
        .isEqualTo(trigger.getArtifactSelections().get(0).getArtifactFilter());
    assertThat(qlLastCollected.getRegex()).isEqualTo(trigger.getArtifactSelections().get(0).isRegex());

    assertThat(qlPipelineAction.getVariables()).isNotNull();
    assertThat(qlPipelineAction.getVariables().get(0).getValue())
        .isEqualTo(trigger.getWorkflowVariables().get("variableKey"));
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void populateTriggerActionShouldReturnPipelineWorkflowWitQLLastDeployedService() {
    ArtifactSelection artifactSource = ArtifactSelection.builder()
                                           .serviceId("serviceId")
                                           .serviceName("serviceName")
                                           .pipelineId("pipelineId")
                                           .pipelineName("pipelineName")
                                           .type(Type.LAST_DEPLOYED)
                                           .build();
    List<ArtifactSelection> artifactSelections = asList(artifactSource);

    Map<String, String> variables = new HashMap<>();
    variables.put("variableKey", "variableResult");

    Trigger trigger = Trigger.builder()
                          .workflowId("workflowId")
                          .workflowName("workflowName")
                          .artifactSelections(artifactSelections)
                          .workflowVariables(variables)
                          .workflowType(WorkflowType.PIPELINE)
                          .build();

    QLPipelineAction qlPipelineAction = (QLPipelineAction) triggerActionController.populateTriggerAction(trigger);

    assertThat(qlPipelineAction).isNotNull();
    assertThat(qlPipelineAction.getPipelineId()).isEqualTo(trigger.getWorkflowId());
    assertThat(qlPipelineAction.getPipelineName()).isEqualTo(trigger.getWorkflowName());

    assertThat(qlPipelineAction.getArtifactSelections()).isNotNull();
    QLLastDeployedFromPipeline qlLastDeployedFromPipeline =
        (QLLastDeployedFromPipeline) qlPipelineAction.getArtifactSelections().get(0);
    assertThat(qlLastDeployedFromPipeline.getServiceId())
        .isEqualTo(trigger.getArtifactSelections().get(0).getServiceId());
    assertThat(qlLastDeployedFromPipeline.getServiceName())
        .isEqualTo(trigger.getArtifactSelections().get(0).getServiceName());
    assertThat(qlLastDeployedFromPipeline.getPipelineId())
        .isEqualTo(trigger.getArtifactSelections().get(0).getPipelineId());
    assertThat(qlLastDeployedFromPipeline.getPipelineName())
        .isEqualTo(trigger.getArtifactSelections().get(0).getPipelineName());

    assertThat(qlPipelineAction.getVariables()).isNotNull();
    assertThat(qlPipelineAction.getVariables().get(0).getValue())
        .isEqualTo(trigger.getWorkflowVariables().get("variableKey"));
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void populateTriggerActionShouldReturnPipelineWorkflowWithQLFromTriggeringPipelineSourceService() {
    ArtifactSelection artifactSource = ArtifactSelection.builder()
                                           .serviceId("serviceId")
                                           .serviceName("serviceName")
                                           .type(Type.PIPELINE_SOURCE)
                                           .build();
    List<ArtifactSelection> artifactSelections = asList(artifactSource);

    Map<String, String> variables = new HashMap<>();
    variables.put("variableKey", "variableResult");

    Trigger trigger = Trigger.builder()
                          .workflowId("workflowId")
                          .workflowName("workflowName")
                          .artifactSelections(artifactSelections)
                          .workflowVariables(variables)
                          .workflowType(WorkflowType.PIPELINE)
                          .build();

    QLPipelineAction qlPipelineAction = (QLPipelineAction) triggerActionController.populateTriggerAction(trigger);

    assertThat(qlPipelineAction).isNotNull();
    assertThat(qlPipelineAction.getPipelineId()).isEqualTo(trigger.getWorkflowId());
    assertThat(qlPipelineAction.getPipelineName()).isEqualTo(trigger.getWorkflowName());

    assertThat(qlPipelineAction.getArtifactSelections()).isNotNull();
    QLFromTriggeringPipeline qlFromTriggeringPipeline =
        (QLFromTriggeringPipeline) qlPipelineAction.getArtifactSelections().get(0);
    assertThat(qlFromTriggeringPipeline.getServiceId())
        .isEqualTo(trigger.getArtifactSelections().get(0).getServiceId());
    assertThat(qlFromTriggeringPipeline.getServiceName())
        .isEqualTo(trigger.getArtifactSelections().get(0).getServiceName());

    assertThat(qlPipelineAction.getVariables()).isNotNull();
    assertThat(qlPipelineAction.getVariables().get(0).getValue())
        .isEqualTo(trigger.getWorkflowVariables().get("variableKey"));
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void populateTriggerActionShouldReturnPipelineWorkflowWithQLFromWebhookPayloadService() {
    ArtifactSelection artifactSource = ArtifactSelection.builder()
                                           .serviceId("serviceId")
                                           .serviceName("serviceName")
                                           .artifactStreamId("sourceId")
                                           .artifactSourceName("sourceName")
                                           .type(Type.WEBHOOK_VARIABLE)
                                           .build();
    List<ArtifactSelection> artifactSelections = asList(artifactSource);

    Map<String, String> variables = new HashMap<>();
    variables.put("variableKey", "variableResult");

    Trigger trigger = Trigger.builder()
                          .workflowId("workflowId")
                          .workflowName("workflowName")
                          .artifactSelections(artifactSelections)
                          .workflowVariables(variables)
                          .workflowType(WorkflowType.PIPELINE)
                          .build();

    QLPipelineAction qlPipelineAction = (QLPipelineAction) triggerActionController.populateTriggerAction(trigger);

    assertThat(qlPipelineAction).isNotNull();
    assertThat(qlPipelineAction.getPipelineId()).isEqualTo(trigger.getWorkflowId());
    assertThat(qlPipelineAction.getPipelineName()).isEqualTo(trigger.getWorkflowName());

    assertThat(qlPipelineAction.getArtifactSelections()).isNotNull();
    QLFromWebhookPayload qlFromWebhookPayload = (QLFromWebhookPayload) qlPipelineAction.getArtifactSelections().get(0);
    assertThat(qlFromWebhookPayload.getServiceId()).isEqualTo(trigger.getArtifactSelections().get(0).getServiceId());
    assertThat(qlFromWebhookPayload.getServiceName())
        .isEqualTo(trigger.getArtifactSelections().get(0).getServiceName());
    assertThat(qlFromWebhookPayload.getArtifactSourceId())
        .isEqualTo(trigger.getArtifactSelections().get(0).getArtifactStreamId());
    assertThat(qlFromWebhookPayload.getArtifactSourceName())
        .isEqualTo(trigger.getArtifactSelections().get(0).getArtifactSourceName());

    assertThat(qlPipelineAction.getVariables()).isNotNull();
    assertThat(qlPipelineAction.getVariables().get(0).getValue())
        .isEqualTo(trigger.getWorkflowVariables().get("variableKey"));
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void populateTriggerActionShouldReturnOrchestrationWorkflowWithFromTriggeringArtifactSourceService() {
    ArtifactSelection artifactSource = ArtifactSelection.builder()
                                           .serviceId("serviceId")
                                           .serviceName("serviceName")
                                           .type(Type.ARTIFACT_SOURCE)
                                           .build();
    List<ArtifactSelection> artifactSelections = asList(artifactSource);
    Map<String, String> variables = new HashMap<>();
    variables.put("variableKey", "variableResult");
    Trigger trigger = Trigger.builder()
                          .workflowType(WorkflowType.ORCHESTRATION)
                          .workflowId("workflowId")
                          .workflowName("workflowName")
                          .artifactSelections(artifactSelections)
                          .workflowVariables(variables)
                          .build();

    QLWorkflowAction qlWorkflowAction = (QLWorkflowAction) triggerActionController.populateTriggerAction(trigger);

    assertThat(qlWorkflowAction).isNotNull();
    assertThat(qlWorkflowAction.getWorkflowId()).isEqualTo(trigger.getWorkflowId());
    assertThat(qlWorkflowAction.getWorkflowName()).isEqualTo(trigger.getWorkflowName());

    assertThat(qlWorkflowAction.getArtifactSelections()).isNotNull();
    assertThat(qlWorkflowAction.getArtifactSelections().get(0).getServiceId())
        .isEqualTo(trigger.getArtifactSelections().get(0).getServiceId());
    assertThat(qlWorkflowAction.getArtifactSelections().get(0).getServiceName())
        .isEqualTo(trigger.getArtifactSelections().get(0).getServiceName());

    assertThat(qlWorkflowAction.getVariables()).isNotNull();
    assertThat(qlWorkflowAction.getVariables().get(0).getValue())
        .isEqualTo(trigger.getWorkflowVariables().get("variableKey"));
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void populateTriggerActionShouldReturnOrchestrationWorkflowWitQLLastCollectedService() {
    ArtifactSelection artifactSource = ArtifactSelection.builder()
                                           .serviceId("serviceId")
                                           .serviceName("serviceName")
                                           .artifactStreamId("streamId")
                                           .artifactSourceName("sourceName")
                                           .artifactFilter("filter")
                                           .regex(true)
                                           .type(Type.LAST_COLLECTED)
                                           .build();
    List<ArtifactSelection> artifactSelections = asList(artifactSource);

    Map<String, String> variables = new HashMap<>();
    variables.put("variableKey", "variableResult");
    Trigger trigger = Trigger.builder()
                          .workflowId("workflowId")
                          .workflowName("workflowName")
                          .artifactSelections(artifactSelections)
                          .workflowVariables(variables)
                          .workflowType(WorkflowType.ORCHESTRATION)
                          .build();

    QLWorkflowAction qlWorkflowAction = (QLWorkflowAction) triggerActionController.populateTriggerAction(trigger);

    assertThat(qlWorkflowAction).isNotNull();
    assertThat(qlWorkflowAction.getWorkflowId()).isEqualTo(trigger.getWorkflowId());
    assertThat(qlWorkflowAction.getWorkflowName()).isEqualTo(trigger.getWorkflowName());

    assertThat(qlWorkflowAction.getArtifactSelections()).isNotNull();
    QLLastCollected qlLastCollected = (QLLastCollected) qlWorkflowAction.getArtifactSelections().get(0);
    assertThat(qlLastCollected.getServiceId()).isEqualTo(trigger.getArtifactSelections().get(0).getServiceId());
    assertThat(qlLastCollected.getServiceName()).isEqualTo(trigger.getArtifactSelections().get(0).getServiceName());
    assertThat(qlLastCollected.getArtifactSourceId())
        .isEqualTo(trigger.getArtifactSelections().get(0).getArtifactStreamId());
    assertThat(qlLastCollected.getArtifactSourceName())
        .isEqualTo(trigger.getArtifactSelections().get(0).getArtifactSourceName());
    assertThat(qlLastCollected.getArtifactFilter())
        .isEqualTo(trigger.getArtifactSelections().get(0).getArtifactFilter());
    assertThat(qlLastCollected.getRegex()).isEqualTo(trigger.getArtifactSelections().get(0).isRegex());

    assertThat(qlWorkflowAction.getVariables()).isNotNull();
    assertThat(qlWorkflowAction.getVariables().get(0).getValue())
        .isEqualTo(trigger.getWorkflowVariables().get("variableKey"));
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void populateTriggerActionShouldReturnOrchestrationWorkflowWitQLLastDeployedService() {
    ArtifactSelection artifactSource = ArtifactSelection.builder()
                                           .serviceId("serviceId")
                                           .serviceName("serviceName")
                                           .workflowId("pipelineId")
                                           .workflowName("pipelineName")
                                           .type(Type.LAST_DEPLOYED)
                                           .build();
    List<ArtifactSelection> artifactSelections = asList(artifactSource);

    Map<String, String> variables = new HashMap<>();
    variables.put("variableKey", "variableResult");

    Trigger trigger = Trigger.builder()
                          .workflowId("workflowId")
                          .workflowName("workflowName")
                          .artifactSelections(artifactSelections)
                          .workflowVariables(variables)
                          .workflowType(WorkflowType.ORCHESTRATION)
                          .build();

    QLWorkflowAction qlWorkflowAction = (QLWorkflowAction) triggerActionController.populateTriggerAction(trigger);

    assertThat(qlWorkflowAction).isNotNull();
    assertThat(qlWorkflowAction.getWorkflowId()).isEqualTo(trigger.getWorkflowId());
    assertThat(qlWorkflowAction.getWorkflowName()).isEqualTo(trigger.getWorkflowName());

    assertThat(qlWorkflowAction.getArtifactSelections()).isNotNull();
    QLLastDeployedFromWorkflow qlLastDeployedFromWorkflow =
        (QLLastDeployedFromWorkflow) qlWorkflowAction.getArtifactSelections().get(0);
    assertThat(qlLastDeployedFromWorkflow.getServiceId())
        .isEqualTo(trigger.getArtifactSelections().get(0).getServiceId());
    assertThat(qlLastDeployedFromWorkflow.getServiceName())
        .isEqualTo(trigger.getArtifactSelections().get(0).getServiceName());
    assertThat(qlLastDeployedFromWorkflow.getWorkflowId())
        .isEqualTo(trigger.getArtifactSelections().get(0).getWorkflowId());
    assertThat(qlLastDeployedFromWorkflow.getWorkflowName())
        .isEqualTo(trigger.getArtifactSelections().get(0).getWorkflowName());

    assertThat(qlWorkflowAction.getVariables()).isNotNull();
    assertThat(qlWorkflowAction.getVariables().get(0).getValue())
        .isEqualTo(trigger.getWorkflowVariables().get("variableKey"));
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void populateTriggerActionShouldReturnOrchestrationWorkflowWithQLFromTriggeringPipelineSourceService() {
    ArtifactSelection artifactSource = ArtifactSelection.builder()
                                           .serviceId("serviceId")
                                           .serviceName("serviceName")
                                           .type(Type.PIPELINE_SOURCE)
                                           .build();
    List<ArtifactSelection> artifactSelections = asList(artifactSource);

    Map<String, String> variables = new HashMap<>();
    variables.put("variableKey", "variableResult");

    Trigger trigger = Trigger.builder()
                          .workflowId("workflowId")
                          .workflowName("workflowName")
                          .artifactSelections(artifactSelections)
                          .workflowVariables(variables)
                          .workflowType(WorkflowType.ORCHESTRATION)
                          .build();

    QLWorkflowAction qlWorkflowAction = (QLWorkflowAction) triggerActionController.populateTriggerAction(trigger);

    assertThat(qlWorkflowAction).isNotNull();
    assertThat(qlWorkflowAction.getWorkflowId()).isEqualTo(trigger.getWorkflowId());
    assertThat(qlWorkflowAction.getWorkflowName()).isEqualTo(trigger.getWorkflowName());

    assertThat(qlWorkflowAction.getArtifactSelections()).isNotNull();
    QLFromTriggeringPipeline qlFromTriggeringPipeline =
        (QLFromTriggeringPipeline) qlWorkflowAction.getArtifactSelections().get(0);
    assertThat(qlFromTriggeringPipeline.getServiceId())
        .isEqualTo(trigger.getArtifactSelections().get(0).getServiceId());
    assertThat(qlFromTriggeringPipeline.getServiceName())
        .isEqualTo(trigger.getArtifactSelections().get(0).getServiceName());

    assertThat(qlWorkflowAction.getVariables()).isNotNull();
    assertThat(qlWorkflowAction.getVariables().get(0).getValue())
        .isEqualTo(trigger.getWorkflowVariables().get("variableKey"));
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void populateTriggerActionShouldReturnOrchestrationWorkflowWithQLFromWebhookPayloadService() {
    ArtifactSelection artifactSource = ArtifactSelection.builder()
                                           .serviceId("serviceId")
                                           .serviceName("serviceName")
                                           .artifactStreamId("sourceId")
                                           .artifactSourceName("sourceName")
                                           .type(Type.WEBHOOK_VARIABLE)
                                           .build();
    List<ArtifactSelection> artifactSelections = asList(artifactSource);

    Map<String, String> variables = new HashMap<>();
    variables.put("variableKey", "variableResult");

    Trigger trigger = Trigger.builder()
                          .workflowId("workflowId")
                          .workflowName("workflowName")
                          .artifactSelections(artifactSelections)
                          .workflowVariables(variables)
                          .workflowType(WorkflowType.ORCHESTRATION)
                          .build();

    QLWorkflowAction qlWorkflowAction = (QLWorkflowAction) triggerActionController.populateTriggerAction(trigger);

    assertThat(qlWorkflowAction).isNotNull();
    assertThat(qlWorkflowAction.getWorkflowId()).isEqualTo(trigger.getWorkflowId());
    assertThat(qlWorkflowAction.getWorkflowName()).isEqualTo(trigger.getWorkflowName());

    assertThat(qlWorkflowAction.getArtifactSelections()).isNotNull();
    QLFromWebhookPayload qlFromWebhookPayload = (QLFromWebhookPayload) qlWorkflowAction.getArtifactSelections().get(0);
    assertThat(qlFromWebhookPayload.getServiceId()).isEqualTo(trigger.getArtifactSelections().get(0).getServiceId());
    assertThat(qlFromWebhookPayload.getServiceName())
        .isEqualTo(trigger.getArtifactSelections().get(0).getServiceName());
    assertThat(qlFromWebhookPayload.getArtifactSourceId())
        .isEqualTo(trigger.getArtifactSelections().get(0).getArtifactStreamId());
    assertThat(qlFromWebhookPayload.getArtifactSourceName())
        .isEqualTo(trigger.getArtifactSelections().get(0).getArtifactSourceName());

    assertThat(qlWorkflowAction.getVariables()).isNotNull();
    assertThat(qlWorkflowAction.getVariables().get(0).getValue())
        .isEqualTo(trigger.getWorkflowVariables().get("variableKey"));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void resolveArtifactSelectionsShouldThrowEmptyServiceIdException() throws Exception {
    QLArtifactSelectionInput qlArtifactSelectionInput =
        QLArtifactSelectionInput.builder()
            .artifactSelectionType(QLArtifactSelectionType.FROM_TRIGGERING_ARTIFACT)
            .serviceId("S1")
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().artifactSelections(asList(qlArtifactSelectionInput)).build())
            .build();
    triggerActionController.resolveArtifactSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void resolveArtifactSelectionsShouldThrowServiceDoesNotExistException() throws Exception {
    QLArtifactSelectionInput qlArtifactSelectionInput =
        QLArtifactSelectionInput.builder()
            .artifactSelectionType(QLArtifactSelectionType.FROM_TRIGGERING_ARTIFACT)
            .serviceId("serviceId")
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().artifactSelections(asList(qlArtifactSelectionInput)).build())
            .build();

    Mockito.when(serviceResourceService.get(ArgumentMatchers.anyString())).thenReturn(null);

    triggerActionController.resolveArtifactSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"));
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void resolveArtifactSelectionsShouldReturnEmptyList() {
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().artifactSelections(null).build())
            .build();

    List<ArtifactSelection> artifactSelections = triggerActionController.resolveArtifactSelections(
        qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"));

    assertThat(artifactSelections).isEmpty();
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void resolveArtifactSelectionsShouldReturnOnTriggeringArtifactArtifactSelectionType() throws Exception {
    QLArtifactSelectionInput qlArtifactSelectionInput =
        QLArtifactSelectionInput.builder()
            .artifactSelectionType(QLArtifactSelectionType.FROM_TRIGGERING_ARTIFACT)
            .serviceId("serviceId")
            .artifactSourceId("artifactSourceId")
            .artifactFilter("filter")
            .regex(true)
            .pipelineId("pipelineId")
            .workflowId("workflowId")
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().artifactSelections(asList(qlArtifactSelectionInput)).build())
            .build();

    Mockito.doReturn(Type.ARTIFACT_SOURCE)
        .when(triggerActionController)
        .validateAndResolveFromTriggeringArtifactArtifactSelectionType(
            ArgumentMatchers.any(QLCreateOrUpdateTriggerInput.class));

    Mockito.when(serviceResourceService.get(ArgumentMatchers.anyString())).thenReturn(Mockito.mock(Service.class));

    ArtifactSelection returnedArtifactSelection =
        triggerActionController.resolveArtifactSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"))
            .get(0);

    assertThat(returnedArtifactSelection).isNotNull();
    assertThat(returnedArtifactSelection.getType()).isEqualByComparingTo(ArtifactSelection.Type.ARTIFACT_SOURCE);
    assertThat(returnedArtifactSelection.getServiceId()).isEqualTo(qlArtifactSelectionInput.getServiceId());
    assertThat(returnedArtifactSelection.getArtifactStreamId())
        .isEqualTo(qlArtifactSelectionInput.getArtifactSourceId());
    assertThat(returnedArtifactSelection.getArtifactFilter()).isEqualTo(qlArtifactSelectionInput.getArtifactFilter());
    assertThat(returnedArtifactSelection.isRegex()).isEqualTo(qlArtifactSelectionInput.getRegex());
    assertThat(returnedArtifactSelection.getPipelineId()).isEqualTo(qlArtifactSelectionInput.getPipelineId());
    assertThat(returnedArtifactSelection.getWorkflowId()).isEqualTo(qlArtifactSelectionInput.getWorkflowId());
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void resolveArtifactSelectionsShouldReturnOnTriggeringPipelineArtifactSelectionType() throws Exception {
    QLArtifactSelectionInput qlArtifactSelectionInput =
        QLArtifactSelectionInput.builder()
            .artifactSelectionType(QLArtifactSelectionType.FROM_TRIGGERING_PIPELINE)
            .serviceId("serviceId")
            .artifactSourceId("artifactSourceId")
            .artifactFilter("filter")
            .regex(true)
            .pipelineId("pipelineId")
            .workflowId("workflowId")
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().artifactSelections(asList(qlArtifactSelectionInput)).build())
            .build();

    Mockito.doReturn(Type.PIPELINE_SOURCE)
        .when(triggerActionController)
        .validateAndResolveFromTriggeringPipelineArtifactSelectionType(
            ArgumentMatchers.any(QLCreateOrUpdateTriggerInput.class));

    Mockito.when(serviceResourceService.get(ArgumentMatchers.anyString())).thenReturn(Mockito.mock(Service.class));

    ArtifactSelection returnedArtifactSelection =
        triggerActionController.resolveArtifactSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"))
            .get(0);

    assertThat(returnedArtifactSelection).isNotNull();
    assertThat(returnedArtifactSelection.getType()).isEqualByComparingTo(ArtifactSelection.Type.PIPELINE_SOURCE);
    assertThat(returnedArtifactSelection.getServiceId()).isEqualTo(qlArtifactSelectionInput.getServiceId());
    assertThat(returnedArtifactSelection.getArtifactStreamId())
        .isEqualTo(qlArtifactSelectionInput.getArtifactSourceId());
    assertThat(returnedArtifactSelection.getArtifactFilter()).isEqualTo(qlArtifactSelectionInput.getArtifactFilter());
    assertThat(returnedArtifactSelection.isRegex()).isEqualTo(qlArtifactSelectionInput.getRegex());
    assertThat(returnedArtifactSelection.getPipelineId()).isEqualTo(qlArtifactSelectionInput.getPipelineId());
    assertThat(returnedArtifactSelection.getWorkflowId()).isEqualTo(qlArtifactSelectionInput.getWorkflowId());
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void resolveArtifactSelectionsShouldReturnLastCollectedArtifactSelectionType() throws Exception {
    QLArtifactSelectionInput qlArtifactSelectionInput =
        QLArtifactSelectionInput.builder()
            .artifactSelectionType(QLArtifactSelectionType.LAST_COLLECTED)
            .serviceId("serviceId")
            .artifactSourceId("artifactSourceId")
            .artifactFilter("filter")
            .regex(true)
            .pipelineId("pipelineId")
            .workflowId("workflowId")
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().artifactSelections(asList(qlArtifactSelectionInput)).build())
            .build();

    Mockito.doReturn(Type.LAST_COLLECTED)
        .when(triggerActionController)
        .validateAndResolveLastCollectedArtifactSelectionType(ArgumentMatchers.any(QLArtifactSelectionInput.class));

    Mockito.when(serviceResourceService.get(ArgumentMatchers.anyString())).thenReturn(Mockito.mock(Service.class));

    ArtifactSelection returnedArtifactSelection =
        triggerActionController.resolveArtifactSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"))
            .get(0);

    assertThat(returnedArtifactSelection).isNotNull();
    assertThat(returnedArtifactSelection.getType()).isEqualByComparingTo(ArtifactSelection.Type.LAST_COLLECTED);
    assertThat(returnedArtifactSelection.getServiceId()).isEqualTo(qlArtifactSelectionInput.getServiceId());
    assertThat(returnedArtifactSelection.getArtifactStreamId())
        .isEqualTo(qlArtifactSelectionInput.getArtifactSourceId());
    assertThat(returnedArtifactSelection.getArtifactFilter()).isEqualTo(qlArtifactSelectionInput.getArtifactFilter());
    assertThat(returnedArtifactSelection.isRegex()).isEqualTo(qlArtifactSelectionInput.getRegex());
    assertThat(returnedArtifactSelection.getPipelineId()).isEqualTo(qlArtifactSelectionInput.getPipelineId());
    assertThat(returnedArtifactSelection.getWorkflowId()).isEqualTo(qlArtifactSelectionInput.getWorkflowId());
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void resolveArtifactSelectionsShouldReturnFromPayloadSourceArtifactSelectionType() throws Exception {
    QLArtifactSelectionInput qlArtifactSelectionInput =
        QLArtifactSelectionInput.builder()
            .artifactSelectionType(QLArtifactSelectionType.FROM_PAYLOAD_SOURCE)
            .serviceId("serviceId")
            .artifactSourceId("artifactSourceId")
            .artifactFilter("filter")
            .regex(true)
            .pipelineId("pipelineId")
            .workflowId("workflowId")
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().artifactSelections(asList(qlArtifactSelectionInput)).build())
            .build();

    Mockito.doReturn(Type.WEBHOOK_VARIABLE)
        .when(triggerActionController)
        .validateAndResolveFromPayloadSourceArtifactSelectionType(
            ArgumentMatchers.any(QLCreateOrUpdateTriggerInput.class),
            ArgumentMatchers.any(QLArtifactSelectionInput.class));

    Mockito.when(serviceResourceService.get(ArgumentMatchers.anyString())).thenReturn(Mockito.mock(Service.class));

    ArtifactSelection returnedArtifactSelection =
        triggerActionController.resolveArtifactSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"))
            .get(0);

    assertThat(returnedArtifactSelection).isNotNull();
    assertThat(returnedArtifactSelection.getType()).isEqualByComparingTo(ArtifactSelection.Type.WEBHOOK_VARIABLE);
    assertThat(returnedArtifactSelection.getServiceId()).isEqualTo(qlArtifactSelectionInput.getServiceId());
    assertThat(returnedArtifactSelection.getArtifactStreamId())
        .isEqualTo(qlArtifactSelectionInput.getArtifactSourceId());
    assertThat(returnedArtifactSelection.getArtifactFilter()).isEqualTo(qlArtifactSelectionInput.getArtifactFilter());
    assertThat(returnedArtifactSelection.isRegex()).isEqualTo(qlArtifactSelectionInput.getRegex());
    assertThat(returnedArtifactSelection.getPipelineId()).isEqualTo(qlArtifactSelectionInput.getPipelineId());
    assertThat(returnedArtifactSelection.getWorkflowId()).isEqualTo(qlArtifactSelectionInput.getWorkflowId());
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void resolveArtifactSelectionsShouldReturnLastDeployedPipelineArtifactSelectionType() throws Exception {
    QLArtifactSelectionInput qlArtifactSelectionInput =
        QLArtifactSelectionInput.builder()
            .artifactSelectionType(QLArtifactSelectionType.LAST_DEPLOYED_PIPELINE)
            .serviceId("serviceId")
            .artifactSourceId("artifactSourceId")
            .artifactFilter("filter")
            .regex(true)
            .pipelineId("pipelineId")
            .workflowId("workflowId")
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().artifactSelections(asList(qlArtifactSelectionInput)).build())
            .build();

    Mockito.doReturn(Type.LAST_DEPLOYED)
        .when(triggerActionController)
        .validateAndResolveLastDeployedPipelineArtifactSelectionType(
            ArgumentMatchers.any(QLCreateOrUpdateTriggerInput.class));

    Mockito.when(serviceResourceService.get(ArgumentMatchers.anyString())).thenReturn(Mockito.mock(Service.class));

    ArtifactSelection returnedArtifactSelection =
        triggerActionController.resolveArtifactSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"))
            .get(0);

    assertThat(returnedArtifactSelection).isNotNull();
    assertThat(returnedArtifactSelection.getType()).isEqualByComparingTo(ArtifactSelection.Type.LAST_DEPLOYED);
    assertThat(returnedArtifactSelection.getServiceId()).isEqualTo(qlArtifactSelectionInput.getServiceId());
    assertThat(returnedArtifactSelection.getArtifactStreamId())
        .isEqualTo(qlArtifactSelectionInput.getArtifactSourceId());
    assertThat(returnedArtifactSelection.getArtifactFilter()).isEqualTo(qlArtifactSelectionInput.getArtifactFilter());
    assertThat(returnedArtifactSelection.isRegex()).isEqualTo(qlArtifactSelectionInput.getRegex());
    assertThat(returnedArtifactSelection.getPipelineId()).isEqualTo(qlArtifactSelectionInput.getPipelineId());
    assertThat(returnedArtifactSelection.getWorkflowId()).isEqualTo(qlArtifactSelectionInput.getWorkflowId());
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void resolveArtifactSelectionsShouldReturnLastDeployedWorkflowArtifactSelectionType() throws Exception {
    QLArtifactSelectionInput qlArtifactSelectionInput =
        QLArtifactSelectionInput.builder()
            .artifactSelectionType(QLArtifactSelectionType.LAST_DEPLOYED_WORKFLOW)
            .serviceId("serviceId")
            .artifactSourceId("artifactSourceId")
            .artifactFilter("filter")
            .regex(true)
            .pipelineId("pipelineId")
            .workflowId("workflowId")
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().artifactSelections(asList(qlArtifactSelectionInput)).build())
            .build();

    Mockito.doReturn(Type.LAST_DEPLOYED)
        .when(triggerActionController)
        .validateAndResolveLastDeployedWorkflowArtifactSelectionType(
            ArgumentMatchers.any(QLCreateOrUpdateTriggerInput.class));

    Mockito.when(serviceResourceService.get(ArgumentMatchers.anyString())).thenReturn(Mockito.mock(Service.class));

    ArtifactSelection returnedArtifactSelection =
        triggerActionController.resolveArtifactSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"))
            .get(0);

    assertThat(returnedArtifactSelection).isNotNull();
    assertThat(returnedArtifactSelection.getType()).isEqualByComparingTo(ArtifactSelection.Type.LAST_DEPLOYED);
    assertThat(returnedArtifactSelection.getServiceId()).isEqualTo(qlArtifactSelectionInput.getServiceId());
    assertThat(returnedArtifactSelection.getArtifactStreamId())
        .isEqualTo(qlArtifactSelectionInput.getArtifactSourceId());
    assertThat(returnedArtifactSelection.getArtifactFilter()).isEqualTo(qlArtifactSelectionInput.getArtifactFilter());
    assertThat(returnedArtifactSelection.isRegex()).isEqualTo(qlArtifactSelectionInput.getRegex());
    assertThat(returnedArtifactSelection.getPipelineId()).isEqualTo(qlArtifactSelectionInput.getPipelineId());
    assertThat(returnedArtifactSelection.getWorkflowId()).isEqualTo(qlArtifactSelectionInput.getWorkflowId());
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void resolveWorkflowTypeShouldReturnOrchestrationWorkflowType() {
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().executionType(QLExecutionType.WORKFLOW).build())
            .build();

    assertThat(triggerActionController.resolveWorkflowType(qlCreateOrUpdateTriggerInput))
        .isEqualByComparingTo(WorkflowType.ORCHESTRATION);
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void resolveWorkflowTypeShouldReturnPipelineWorkflowType() {
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().executionType(QLExecutionType.PIPELINE).build())
            .build();

    assertThat(triggerActionController.resolveWorkflowType(qlCreateOrUpdateTriggerInput))
        .isEqualByComparingTo(WorkflowType.PIPELINE);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void validatePipelineShouldThrowAppIdMustNotBeEmptyException() {
    QLTriggerConditionInput qlTriggerConditionInput = QLTriggerConditionInput.builder().build();
    QLTriggerActionInput qlTriggerActionInput = QLTriggerActionInput.builder().entityId("entityId").build();
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput = QLCreateOrUpdateTriggerInput.builder()
                                                                    .condition(qlTriggerConditionInput)
                                                                    .action(qlTriggerActionInput)
                                                                    .applicationId(null)
                                                                    .build();

    triggerActionController.validatePipeline(qlCreateOrUpdateTriggerInput, "pipelineId");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void validatePipelineShouldThrowPipelineIdMustNotBeEmptyException() {
    QLTriggerActionInput qlTriggerActionInput = QLTriggerActionInput.builder().entityId(null).build();
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder().action(qlTriggerActionInput).applicationId("appId").build();

    triggerActionController.validatePipeline(qlCreateOrUpdateTriggerInput, "pipelineId");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void validatePipelineShouldThrowPipelineDoesntExistException() {
    QLTriggerActionInput qlTriggerActionInput = QLTriggerActionInput.builder().entityId("entityId").build();
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder().action(qlTriggerActionInput).applicationId("appId").build();

    Mockito
        .when(pipelineService.readPipeline(
            ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean()))
        .thenReturn(null);

    triggerActionController.validatePipeline(qlCreateOrUpdateTriggerInput, "pipelineId");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void validatePipelineShouldThrowPipelineDoesntBelongToThisAppException() {
    QLTriggerActionInput qlTriggerActionInput = QLTriggerActionInput.builder().entityId("entityId").build();
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder().action(qlTriggerActionInput).applicationId("appId").build();

    Mockito
        .when(pipelineService.readPipeline(
            ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean()))
        .thenReturn(Pipeline.builder().appId("appId2").build());

    triggerActionController.validatePipeline(qlCreateOrUpdateTriggerInput, "pipelineId");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void resolveArtifactSelectionsShouldThrowInvalidServiceIdException() {
    QLArtifactSelectionInput qlArtifactSelectionInput =
        QLArtifactSelectionInput.builder()
            .artifactSelectionType(QLArtifactSelectionType.FROM_TRIGGERING_ARTIFACT)
            .serviceId("S1")
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().artifactSelections(asList(qlArtifactSelectionInput)).build())
            .build();

    when(serviceResourceService.get(ArgumentMatchers.anyString())).thenReturn(null);
    assertThatThrownBy(()
                           -> triggerActionController.resolveArtifactSelections(
                               qlCreateOrUpdateTriggerInput, Collections.singletonList("S1")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ServiceId does not exist for Artifact Selection. ServiceId: S1");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldResolveArtifactSelectionsForTriggeringAppArtifact() {
    QLArtifactSelectionInput qlArtifactSelectionInput =
        QLArtifactSelectionInput.builder()
            .artifactSelectionType(QLArtifactSelectionType.FROM_TRIGGERING_ARTIFACT)
            .serviceId(SERVICE_ID)
            .artifactFilter("filter")
            .pipelineId(PIPELINE_ID)
            .workflowId(WORKFLOW_ID)
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().artifactSelections(asList(qlArtifactSelectionInput)).build())
            .condition(QLTriggerConditionInput.builder()
                           .conditionType(QLConditionType.ON_NEW_ARTIFACT)
                           .artifactConditionInput(
                               QLArtifactConditionInput.builder().artifactSourceId(ARTIFACT_STREAM_ID).build())
                           .build())
            .build();

    Mockito.when(serviceResourceService.get(ArgumentMatchers.anyString())).thenReturn(Mockito.mock(Service.class));

    ArtifactSelection artifactSelection =
        triggerActionController.resolveArtifactSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"))
            .get(0);

    assertThat(artifactSelection).isNotNull();
    assertThat(artifactSelection.getType()).isEqualTo(Type.ARTIFACT_SOURCE);
    assertThat(artifactSelection.getServiceId()).isEqualTo(qlArtifactSelectionInput.getServiceId());
    assertThat(artifactSelection.getArtifactFilter()).isEqualTo(qlArtifactSelectionInput.getArtifactFilter());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void validateWorkflowShouldThrowWorkflowDoesntExistException() {
    QLTriggerActionInput qlTriggerActionInput =
        QLTriggerActionInput.builder()
            .entityId("entityId")
            .artifactSelections(asList(QLArtifactSelectionInput.builder().workflowId("workflowId").build()))
            .build();
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder().action(qlTriggerActionInput).applicationId("appId").build();

    Mockito.when(workflowService.readWorkflow(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenReturn(null);

    triggerActionController.validateWorkflow(qlCreateOrUpdateTriggerInput, "workflowId");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void validateWorkflowShouldThrowWorkflowDoesntBelongToThisAppException() {
    QLTriggerActionInput qlTriggerActionInput =
        QLTriggerActionInput.builder()
            .entityId("entityId")
            .artifactSelections(asList(QLArtifactSelectionInput.builder().workflowId("workflowId").build()))
            .build();
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder().action(qlTriggerActionInput).applicationId("appId").build();

    when(workflowService.readWorkflow(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenReturn(aWorkflow().appId("appId2").build());

    triggerActionController.validateWorkflow(qlCreateOrUpdateTriggerInput, "workflowId");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void resolveArtifactSelectionsShouldThrowEmptyServiceException() {
    QLArtifactSelectionInput qlArtifactSelectionInput =
        QLArtifactSelectionInput.builder()
            .artifactSelectionType(QLArtifactSelectionType.FROM_TRIGGERING_ARTIFACT)
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().artifactSelections(asList(qlArtifactSelectionInput)).build())
            .build();

    assertThatThrownBy(()
                           -> triggerActionController.resolveArtifactSelections(
                               qlCreateOrUpdateTriggerInput, Collections.singletonList("S1")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Empty serviceId in Artifact Selection");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorOnArtifactSelectionsInvalidCombinations() {
    QLArtifactSelectionInput qlArtifactSelectionInput =
        QLArtifactSelectionInput.builder()
            .artifactSelectionType(QLArtifactSelectionType.FROM_PAYLOAD_SOURCE)
            .artifactSourceId(ARTIFACT_STREAM_ID)
            .serviceId(SERVICE_ID)
            .pipelineId(PIPELINE_ID)
            .workflowId(WORKFLOW_ID)
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().artifactSelections(asList(qlArtifactSelectionInput)).build())
            .condition(QLTriggerConditionInput.builder()
                           .conditionType(QLConditionType.ON_WEBHOOK)
                           .webhookConditionInput(
                               QLWebhookConditionInput.builder().webhookSourceType(QLWebhookSource.GITHUB).build())
                           .build())
            .build();

    Mockito.when(serviceResourceService.get(ArgumentMatchers.anyString())).thenReturn(Mockito.mock(Service.class));

    assertThatThrownBy(()
                           -> triggerActionController.resolveArtifactSelections(
                               qlCreateOrUpdateTriggerInput, Collections.singletonList("S1")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("FROM_PAYLOAD_SOURCE can be used only with CUSTOM Webhook Event");

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput2 =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().artifactSelections(asList(qlArtifactSelectionInput)).build())
            .condition(QLTriggerConditionInput.builder().conditionType(QLConditionType.ON_SCHEDULE).build())
            .build();

    assertThatThrownBy(()
                           -> triggerActionController.resolveArtifactSelections(
                               qlCreateOrUpdateTriggerInput2, Collections.singletonList("S1")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("FROM_PAYLOAD_SOURCE can be used only with ON_WEBHOOK Condition Type");

    qlArtifactSelectionInput = QLArtifactSelectionInput.builder()
                                   .artifactSelectionType(QLArtifactSelectionType.FROM_TRIGGERING_ARTIFACT)
                                   .serviceId(SERVICE_ID)
                                   .artifactFilter("filter")
                                   .pipelineId(PIPELINE_ID)
                                   .workflowId(WORKFLOW_ID)
                                   .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput3 =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().artifactSelections(asList(qlArtifactSelectionInput)).build())
            .condition(QLTriggerConditionInput.builder().conditionType(QLConditionType.ON_SCHEDULE).build())
            .build();

    assertThatThrownBy(
        ()
            -> triggerActionController
                   .resolveArtifactSelections(qlCreateOrUpdateTriggerInput3, Collections.singletonList("S1"))
                   .get(0))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("FROM_TRIGGERING_ARTIFACT can be used only with ON_NEW_ARTIFACT Condition Type");

    qlArtifactSelectionInput = QLArtifactSelectionInput.builder()
                                   .artifactSelectionType(QLArtifactSelectionType.FROM_TRIGGERING_PIPELINE)
                                   .serviceId(SERVICE_ID)
                                   .artifactFilter("filter")
                                   .pipelineId(PIPELINE_ID)
                                   .workflowId(WORKFLOW_ID)
                                   .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput4 =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().artifactSelections(asList(qlArtifactSelectionInput)).build())
            .condition(QLTriggerConditionInput.builder().conditionType(QLConditionType.ON_SCHEDULE).build())
            .build();

    assertThatThrownBy(
        ()
            -> triggerActionController
                   .resolveArtifactSelections(qlCreateOrUpdateTriggerInput4, Collections.singletonList("S1"))
                   .get(0))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("FROM_TRIGGERING_PIPELINE can be used only with ON_PIPELINE_COMPLETION Condition Type");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorOnArtifactSelectionsMissingEntities() {
    QLArtifactSelectionInput qlArtifactSelectionInput =
        QLArtifactSelectionInput.builder()
            .artifactSelectionType(QLArtifactSelectionType.LAST_DEPLOYED_WORKFLOW)
            .serviceId(SERVICE_ID)
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .applicationId(APP_ID)
            .action(QLTriggerActionInput.builder()
                        .executionType(QLExecutionType.WORKFLOW)
                        .artifactSelections(asList(qlArtifactSelectionInput))
                        .build())
            .condition(QLTriggerConditionInput.builder()
                           .conditionType(QLConditionType.ON_WEBHOOK)
                           .webhookConditionInput(
                               QLWebhookConditionInput.builder().webhookSourceType(QLWebhookSource.CUSTOM).build())
                           .build())
            .build();

    Mockito.when(serviceResourceService.get(ArgumentMatchers.anyString())).thenReturn(Mockito.mock(Service.class));
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(null);

    assertThatThrownBy(
        ()
            -> triggerActionController
                   .resolveArtifactSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"))
                   .get(0))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Workflow Id to select artifact from is required when using LAST_DEPLOYED_WORKFLOW");

    qlArtifactSelectionInput = QLArtifactSelectionInput.builder()
                                   .artifactSelectionType(QLArtifactSelectionType.LAST_DEPLOYED_WORKFLOW)
                                   .serviceId(SERVICE_ID)
                                   .workflowId(WORKFLOW_ID)
                                   .pipelineId(PIPELINE_ID)
                                   .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput3 =
        QLCreateOrUpdateTriggerInput.builder()
            .applicationId(APP_ID)
            .action(QLTriggerActionInput.builder()
                        .executionType(QLExecutionType.PIPELINE)
                        .artifactSelections(asList(qlArtifactSelectionInput))
                        .build())
            .condition(QLTriggerConditionInput.builder()
                           .conditionType(QLConditionType.ON_WEBHOOK)
                           .webhookConditionInput(
                               QLWebhookConditionInput.builder().webhookSourceType(QLWebhookSource.CUSTOM).build())
                           .build())
            .build();

    assertThatThrownBy(
        ()
            -> triggerActionController
                   .resolveArtifactSelections(qlCreateOrUpdateTriggerInput3, Collections.singletonList("S1"))
                   .get(0))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Artifact Selection is not allowed for current Workflow Type");

    qlArtifactSelectionInput = QLArtifactSelectionInput.builder()
                                   .artifactSelectionType(QLArtifactSelectionType.LAST_DEPLOYED_PIPELINE)
                                   .serviceId(SERVICE_ID)
                                   .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput2 =
        QLCreateOrUpdateTriggerInput.builder()
            .applicationId(APP_ID)
            .action(QLTriggerActionInput.builder()
                        .executionType(QLExecutionType.PIPELINE)
                        .artifactSelections(asList(qlArtifactSelectionInput))
                        .build())
            .condition(QLTriggerConditionInput.builder()
                           .conditionType(QLConditionType.ON_WEBHOOK)
                           .webhookConditionInput(
                               QLWebhookConditionInput.builder().webhookSourceType(QLWebhookSource.CUSTOM).build())
                           .build())
            .build();

    Mockito.when(serviceResourceService.get(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenReturn(Mockito.mock(Service.class));
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, false)).thenReturn(null);

    assertThatThrownBy(
        ()
            -> triggerActionController
                   .resolveArtifactSelections(qlCreateOrUpdateTriggerInput2, Collections.singletonList("S1"))
                   .get(0))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Pipeline Id to select artifact from is required when using LAST_DEPLOYED_PIPELINE");

    qlArtifactSelectionInput = QLArtifactSelectionInput.builder()
                                   .artifactSelectionType(QLArtifactSelectionType.LAST_DEPLOYED_PIPELINE)
                                   .serviceId(SERVICE_ID)
                                   .pipelineId(PIPELINE_ID)
                                   .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput4 =
        QLCreateOrUpdateTriggerInput.builder()
            .applicationId(APP_ID)
            .action(QLTriggerActionInput.builder()
                        .executionType(QLExecutionType.WORKFLOW)
                        .artifactSelections(asList(qlArtifactSelectionInput))
                        .build())
            .condition(QLTriggerConditionInput.builder()
                           .conditionType(QLConditionType.ON_WEBHOOK)
                           .webhookConditionInput(
                               QLWebhookConditionInput.builder().webhookSourceType(QLWebhookSource.CUSTOM).build())
                           .build())
            .build();

    assertThatThrownBy(
        ()
            -> triggerActionController
                   .resolveArtifactSelections(qlCreateOrUpdateTriggerInput4, Collections.singletonList("S1"))
                   .get(0))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Artifact Selection is not allowed for current Workflow Type");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorOnLastCollectedArtifactStreamIdMissing() {
    QLArtifactSelectionInput qlArtifactSelectionInput =
        QLArtifactSelectionInput.builder()
            .artifactSelectionType(QLArtifactSelectionType.LAST_COLLECTED)
            .serviceId(SERVICE_ID)
            .artifactFilter("filter")
            .pipelineId(PIPELINE_ID)
            .workflowId(WORKFLOW_ID)
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().artifactSelections(asList(qlArtifactSelectionInput)).build())
            .condition(QLTriggerConditionInput.builder()
                           .conditionType(QLConditionType.ON_WEBHOOK)
                           .webhookConditionInput(
                               QLWebhookConditionInput.builder().webhookSourceType(QLWebhookSource.CUSTOM).build())
                           .build())
            .build();

    Mockito.when(serviceResourceService.get(ArgumentMatchers.anyString())).thenReturn(Mockito.mock(Service.class));

    assertThatThrownBy(
        ()
            -> triggerActionController
                   .resolveArtifactSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"))
                   .get(0))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Artifact Source Id to select artifact from is required when using LAST_COLLECTED");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorOnLastCollectedArtifactStreamIdInvalid() {
    QLArtifactSelectionInput qlArtifactSelectionInput =
        QLArtifactSelectionInput.builder()
            .artifactSelectionType(QLArtifactSelectionType.LAST_COLLECTED)
            .serviceId(SERVICE_ID)
            .artifactFilter("filter")
            .artifactSourceId(ARTIFACT_STREAM_ID)
            .pipelineId(PIPELINE_ID)
            .workflowId(WORKFLOW_ID)
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().artifactSelections(asList(qlArtifactSelectionInput)).build())
            .condition(QLTriggerConditionInput.builder()
                           .conditionType(QLConditionType.ON_WEBHOOK)
                           .webhookConditionInput(
                               QLWebhookConditionInput.builder().webhookSourceType(QLWebhookSource.CUSTOM).build())
                           .build())
            .build();

    Mockito.when(serviceResourceService.get(ArgumentMatchers.anyString())).thenReturn(Mockito.mock(Service.class));

    assertThatThrownBy(
        ()
            -> triggerActionController
                   .resolveArtifactSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"))
                   .get(0))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Artifact Stream for given id does not exist. Id: ARTIFACT_STREAM_ID");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorOnLastCollectedArtifactStreamInvalid() {
    QLArtifactSelectionInput qlArtifactSelectionInput =
        QLArtifactSelectionInput.builder()
            .artifactSelectionType(QLArtifactSelectionType.LAST_COLLECTED)
            .serviceId(SERVICE_ID)
            .artifactFilter("filter")
            .artifactSourceId(ARTIFACT_STREAM_ID)
            .pipelineId(PIPELINE_ID)
            .workflowId(WORKFLOW_ID)
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().artifactSelections(asList(qlArtifactSelectionInput)).build())
            .condition(QLTriggerConditionInput.builder()
                           .conditionType(QLConditionType.ON_WEBHOOK)
                           .webhookConditionInput(
                               QLWebhookConditionInput.builder().webhookSourceType(QLWebhookSource.CUSTOM).build())
                           .build())
            .build();

    Mockito.when(serviceResourceService.get(ArgumentMatchers.anyString())).thenReturn(Mockito.mock(Service.class));
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(CustomArtifactStream.builder().build());

    assertThatThrownBy(
        ()
            -> triggerActionController
                   .resolveArtifactSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"))
                   .get(0))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Artifact Source does not belong to the service. Service: SERVICE_ID");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void resolveManifestSelectionsShouldThrowInvalidServiceIdException() {
    QLManifestSelectionInput qlManifestSelectionInput =
        QLManifestSelectionInput.builder()
            .manifestSelectionType(ManifestSelectionType.FROM_APP_MANIFEST)
            .serviceId("S1")
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().manifestSelections(asList(qlManifestSelectionInput)).build())
            .build();

    when(serviceResourceService.get(ArgumentMatchers.anyString())).thenReturn(null);
    triggerActionController.resolveManifestSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void resolveManifestSelectionsShouldThrowMissingServiceIdException() {
    QLManifestSelectionInput qlManifestSelectionInput =
        QLManifestSelectionInput.builder()
            .manifestSelectionType(ManifestSelectionType.FROM_APP_MANIFEST)
            .serviceId("S1")
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().manifestSelections(asList(qlManifestSelectionInput)).build())
            .build();

    when(serviceResourceService.get("S1")).thenReturn(Service.builder().uuid("S1").build());
    triggerActionController.resolveManifestSelections(qlCreateOrUpdateTriggerInput, asList("S1", "S2"));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void resolveManifestSelectionsShouldReturnEmptyList() {
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder().action(QLTriggerActionInput.builder().build()).build();

    List<ManifestSelection> manifestSelections =
        triggerActionController.resolveManifestSelections(qlCreateOrUpdateTriggerInput, Collections.emptyList());

    assertThat(manifestSelections).isEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldResolveManifestSelectionsForTriggeringAppManifest() {
    QLManifestSelectionInput qlManifestSelectionInput =
        QLManifestSelectionInput.builder()
            .manifestSelectionType(ManifestSelectionType.FROM_APP_MANIFEST)
            .serviceId(SERVICE_ID)
            .versionRegex("filter")
            .pipelineId(PIPELINE_ID)
            .workflowId(WORKFLOW_ID)
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().manifestSelections(asList(qlManifestSelectionInput)).build())
            .condition(
                QLTriggerConditionInput.builder()
                    .conditionType(QLConditionType.ON_NEW_MANIFEST)
                    .manifestConditionInput(QLManifestConditionInput.builder().appManifestId(MANIFEST_ID).build())
                    .build())
            .build();

    Mockito.when(serviceResourceService.get(any(), any())).thenReturn(Mockito.mock(Service.class));

    ManifestSelection returnedManifestSelection =
        triggerActionController.resolveManifestSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"))
            .get(0);

    assertThat(returnedManifestSelection).isNotNull();
    assertThat(returnedManifestSelection.getType()).isEqualByComparingTo(ManifestSelectionType.FROM_APP_MANIFEST);
    assertThat(returnedManifestSelection.getServiceId()).isEqualTo(qlManifestSelectionInput.getServiceId());
    assertThat(returnedManifestSelection.getVersionRegex()).isEqualTo(qlManifestSelectionInput.getVersionRegex());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldResolveManifestSelectionsForTriggeringPipeline() {
    QLManifestSelectionInput qlManifestSelectionInput =
        QLManifestSelectionInput.builder()
            .manifestSelectionType(ManifestSelectionType.PIPELINE_SOURCE)
            .serviceId(SERVICE_ID)
            .versionRegex("filter")
            .pipelineId(PIPELINE_ID)
            .workflowId(WORKFLOW_ID)
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().manifestSelections(asList(qlManifestSelectionInput)).build())
            .condition(
                QLTriggerConditionInput.builder()
                    .conditionType(QLConditionType.ON_PIPELINE_COMPLETION)
                    .pipelineConditionInput(QLPipelineConditionInput.builder().pipelineId(PIPELINE_ID + 2).build())
                    .build())
            .build();

    Mockito.when(serviceResourceService.get(any(), any())).thenReturn(Mockito.mock(Service.class));

    ManifestSelection returnedManifestSelection =
        triggerActionController.resolveManifestSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"))
            .get(0);

    assertThat(returnedManifestSelection).isNotNull();
    assertThat(returnedManifestSelection.getType()).isEqualByComparingTo(ManifestSelectionType.PIPELINE_SOURCE);
    assertThat(returnedManifestSelection.getServiceId()).isEqualTo(qlManifestSelectionInput.getServiceId());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldResolveManifestSelectionsForWebhookSource() {
    QLManifestSelectionInput qlManifestSelectionInput =
        QLManifestSelectionInput.builder()
            .manifestSelectionType(ManifestSelectionType.WEBHOOK_VARIABLE)
            .serviceId(SERVICE_ID)
            .versionRegex("filter")
            .pipelineId(PIPELINE_ID)
            .workflowId(WORKFLOW_ID)
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().manifestSelections(asList(qlManifestSelectionInput)).build())
            .condition(QLTriggerConditionInput.builder()
                           .conditionType(QLConditionType.ON_WEBHOOK)
                           .webhookConditionInput(
                               QLWebhookConditionInput.builder().webhookSourceType(QLWebhookSource.CUSTOM).build())
                           .build())
            .build();

    Mockito.when(serviceResourceService.get(any(), any())).thenReturn(Mockito.mock(Service.class));

    ManifestSelection returnedManifestSelection =
        triggerActionController.resolveManifestSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"))
            .get(0);

    assertThat(returnedManifestSelection).isNotNull();
    assertThat(returnedManifestSelection.getType()).isEqualByComparingTo(ManifestSelectionType.WEBHOOK_VARIABLE);
    assertThat(returnedManifestSelection.getServiceId()).isEqualTo(qlManifestSelectionInput.getServiceId());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void populateManifestSelectionFromTriggeringManifest() {
    ManifestSelection manifestSelection = ManifestSelection.builder()
                                              .serviceId("serviceId")
                                              .serviceName("serviceName")
                                              .type(ManifestSelectionType.FROM_APP_MANIFEST)
                                              .build();
    List<ManifestSelection> manifestSelections = Arrays.asList(manifestSelection);
    Map<String, String> variables = new HashMap<>();
    variables.put("variableKey", "variableResult");
    Trigger trigger = Trigger.builder()
                          .workflowId("workflowId")
                          .workflowName("workflowName")
                          .manifestSelections(manifestSelections)
                          .workflowVariables(variables)
                          .build();

    QLPipelineAction qlPipelineAction = (QLPipelineAction) triggerActionController.populateTriggerAction(trigger);

    assertThat(qlPipelineAction).isNotNull();
    assertThat(qlPipelineAction.getPipelineId()).isEqualTo(trigger.getWorkflowId());
    assertThat(qlPipelineAction.getPipelineName()).isEqualTo(trigger.getWorkflowName());

    assertThat(qlPipelineAction.getManifestSelections()).isNotNull();
    assertThat(qlPipelineAction.getManifestSelections().get(0).getServiceId())
        .isEqualTo(trigger.getManifestSelections().get(0).getServiceId());
    assertThat(qlPipelineAction.getManifestSelections().get(0).getServiceName())
        .isEqualTo(trigger.getManifestSelections().get(0).getServiceName());

    assertThat(qlPipelineAction.getVariables()).isNotNull();
    assertThat(qlPipelineAction.getVariables().get(0).getValue())
        .isEqualTo(trigger.getWorkflowVariables().get("variableKey"));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldResolveManifestSelectionsForLastCollected() {
    QLManifestSelectionInput qlManifestSelectionInput = QLManifestSelectionInput.builder()
                                                            .manifestSelectionType(ManifestSelectionType.LAST_COLLECTED)
                                                            .serviceId(SERVICE_ID)
                                                            .versionRegex("filter")
                                                            .pipelineId(PIPELINE_ID)
                                                            .workflowId(WORKFLOW_ID)
                                                            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().manifestSelections(asList(qlManifestSelectionInput)).build())
            .condition(QLTriggerConditionInput.builder()
                           .conditionType(QLConditionType.ON_WEBHOOK)
                           .webhookConditionInput(
                               QLWebhookConditionInput.builder().webhookSourceType(QLWebhookSource.CUSTOM).build())
                           .build())
            .build();

    Mockito.when(serviceResourceService.get(any(), any())).thenReturn(Mockito.mock(Service.class));

    ManifestSelection returnedManifestSelection =
        triggerActionController.resolveManifestSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"))
            .get(0);

    assertThat(returnedManifestSelection).isNotNull();
    assertThat(returnedManifestSelection.getType()).isEqualByComparingTo(ManifestSelectionType.LAST_COLLECTED);
    assertThat(returnedManifestSelection.getServiceId()).isEqualTo(qlManifestSelectionInput.getServiceId());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void populateManifestSelectionFromLastCollected() {
    ManifestSelection manifestSelection = ManifestSelection.builder()
                                              .serviceId("serviceId")
                                              .serviceName("serviceName")
                                              .versionRegex("filter")
                                              .appManifestId("manifestId")
                                              .type(ManifestSelectionType.LAST_COLLECTED)
                                              .build();
    List<ManifestSelection> manifestSelections = Arrays.asList(manifestSelection);

    Map<String, String> variables = new HashMap<>();
    variables.put("variableKey", "variableResult");
    Trigger trigger = Trigger.builder()
                          .workflowId("workflowId")
                          .workflowName("workflowName")
                          .manifestSelections(manifestSelections)
                          .workflowVariables(variables)
                          .build();

    QLPipelineAction qlPipelineAction = (QLPipelineAction) triggerActionController.populateTriggerAction(trigger);

    assertThat(qlPipelineAction).isNotNull();
    assertThat(qlPipelineAction.getPipelineId()).isEqualTo(trigger.getWorkflowId());
    assertThat(qlPipelineAction.getPipelineName()).isEqualTo(trigger.getWorkflowName());

    assertThat(qlPipelineAction.getManifestSelections()).isNotNull();
    QLLastCollectedManifest qlLastCollected = (QLLastCollectedManifest) qlPipelineAction.getManifestSelections().get(0);
    assertThat(qlLastCollected.getServiceId()).isEqualTo(trigger.getManifestSelections().get(0).getServiceId());
    assertThat(qlLastCollected.getServiceName()).isEqualTo(trigger.getManifestSelections().get(0).getServiceName());
    assertThat(qlLastCollected.getAppManifestId()).isEqualTo(trigger.getManifestSelections().get(0).getAppManifestId());
    assertThat(qlLastCollected.getVersionRegex()).isEqualTo(trigger.getManifestSelections().get(0).getVersionRegex());

    assertThat(qlPipelineAction.getVariables()).isNotNull();
    assertThat(qlPipelineAction.getVariables().get(0).getValue())
        .isEqualTo(trigger.getWorkflowVariables().get("variableKey"));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldResolveManifestSelectionsForLastDeployedPipeline() {
    QLManifestSelectionInput qlManifestSelectionInput = QLManifestSelectionInput.builder()
                                                            .manifestSelectionType(ManifestSelectionType.LAST_DEPLOYED)
                                                            .serviceId(SERVICE_ID)
                                                            .versionRegex("filter")
                                                            .pipelineId(PIPELINE_ID)
                                                            .workflowId(WORKFLOW_ID)
                                                            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .applicationId(APP_ID)
            .action(QLTriggerActionInput.builder()
                        .executionType(QLExecutionType.PIPELINE)
                        .manifestSelections(asList(qlManifestSelectionInput))
                        .build())
            .condition(QLTriggerConditionInput.builder()
                           .conditionType(QLConditionType.ON_WEBHOOK)
                           .webhookConditionInput(
                               QLWebhookConditionInput.builder().webhookSourceType(QLWebhookSource.CUSTOM).build())
                           .build())
            .build();

    Mockito.when(serviceResourceService.get(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenReturn(Mockito.mock(Service.class));
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, false)).thenReturn(Pipeline.builder().appId(APP_ID).build());

    ManifestSelection returnedManifestSelection =
        triggerActionController.resolveManifestSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"))
            .get(0);

    assertThat(returnedManifestSelection).isNotNull();
    assertThat(returnedManifestSelection.getType()).isEqualByComparingTo(ManifestSelectionType.LAST_DEPLOYED);
    assertThat(returnedManifestSelection.getServiceId()).isEqualTo(qlManifestSelectionInput.getServiceId());
    assertThat(returnedManifestSelection.getPipelineId()).isEqualTo(qlManifestSelectionInput.getPipelineId());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void populateManifestSelectionFromLastDeployed() {
    ManifestSelection manifestSelection = ManifestSelection.builder()
                                              .serviceId("serviceId")
                                              .serviceName("serviceName")
                                              .pipelineId("pipelineId")
                                              .pipelineName("pipelineName")
                                              .type(ManifestSelectionType.LAST_DEPLOYED)
                                              .build();
    List<ManifestSelection> manifestSelections = Arrays.asList(manifestSelection);

    Map<String, String> variables = new HashMap<>();
    variables.put("variableKey", "variableResult");

    Trigger trigger = Trigger.builder()
                          .pipelineId("pipelineId")
                          .pipelineName("pipelineName")
                          .manifestSelections(manifestSelections)
                          .workflowVariables(variables)
                          .workflowType(WorkflowType.PIPELINE)
                          .build();

    QLPipelineAction qlPipelineAction = (QLPipelineAction) triggerActionController.populateTriggerAction(trigger);

    assertThat(qlPipelineAction).isNotNull();
    assertThat(qlPipelineAction.getPipelineId()).isEqualTo(trigger.getWorkflowId());
    assertThat(qlPipelineAction.getPipelineName()).isEqualTo(trigger.getWorkflowName());

    assertThat(qlPipelineAction.getManifestSelections()).isNotNull();
    QLLastDeployedManifestFromPipeline qlLastDeployedFromPipeline =
        (QLLastDeployedManifestFromPipeline) qlPipelineAction.getManifestSelections().get(0);
    assertThat(qlLastDeployedFromPipeline.getServiceId()).isEqualTo(manifestSelection.getServiceId());
    assertThat(qlLastDeployedFromPipeline.getServiceName()).isEqualTo(manifestSelection.getServiceName());
    assertThat(qlLastDeployedFromPipeline.getPipelineId()).isEqualTo(manifestSelection.getPipelineId());
    assertThat(qlLastDeployedFromPipeline.getPipelineName()).isEqualTo(manifestSelection.getPipelineName());

    assertThat(qlPipelineAction.getVariables()).isNotNull();
    assertThat(qlPipelineAction.getVariables().get(0).getValue())
        .isEqualTo(trigger.getWorkflowVariables().get("variableKey"));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldResolveManifestSelectionsForLastDeployedWorkflow() {
    QLManifestSelectionInput qlManifestSelectionInput = QLManifestSelectionInput.builder()
                                                            .manifestSelectionType(ManifestSelectionType.LAST_DEPLOYED)
                                                            .serviceId(SERVICE_ID)
                                                            .versionRegex("filter")
                                                            .pipelineId(PIPELINE_ID)
                                                            .workflowId(WORKFLOW_ID)
                                                            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .applicationId(APP_ID)
            .action(QLTriggerActionInput.builder()
                        .executionType(QLExecutionType.WORKFLOW)
                        .manifestSelections(asList(qlManifestSelectionInput))
                        .build())
            .condition(QLTriggerConditionInput.builder()
                           .conditionType(QLConditionType.ON_WEBHOOK)
                           .webhookConditionInput(
                               QLWebhookConditionInput.builder().webhookSourceType(QLWebhookSource.CUSTOM).build())
                           .build())
            .build();

    Mockito.when(serviceResourceService.get(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenReturn(Mockito.mock(Service.class));
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(aWorkflow().appId(APP_ID).build());

    ManifestSelection returnedManifestSelection =
        triggerActionController.resolveManifestSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"))
            .get(0);

    assertThat(returnedManifestSelection).isNotNull();
    assertThat(returnedManifestSelection.getType()).isEqualByComparingTo(ManifestSelectionType.LAST_DEPLOYED);
    assertThat(returnedManifestSelection.getServiceId()).isEqualTo(qlManifestSelectionInput.getServiceId());
    assertThat(returnedManifestSelection.getWorkflowId()).isEqualTo(qlManifestSelectionInput.getWorkflowId());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void populateManifestSelectionFromLastDeployedWorkflow() {
    ManifestSelection manifestSelection = ManifestSelection.builder()
                                              .serviceId("serviceId")
                                              .serviceName("serviceName")
                                              .workflowId("workflowId")
                                              .workflowName("workflowName")
                                              .type(ManifestSelectionType.LAST_DEPLOYED)
                                              .build();
    List<ManifestSelection> manifestSelections = Arrays.asList(manifestSelection);

    Map<String, String> variables = new HashMap<>();
    variables.put("variableKey", "variableResult");

    Trigger trigger = Trigger.builder()
                          .workflowId("workflowId")
                          .workflowName("workflowName")
                          .manifestSelections(manifestSelections)
                          .workflowVariables(variables)
                          .workflowType(WorkflowType.ORCHESTRATION)
                          .build();

    QLWorkflowAction qlPipelineAction = (QLWorkflowAction) triggerActionController.populateTriggerAction(trigger);

    assertThat(qlPipelineAction).isNotNull();
    assertThat(qlPipelineAction.getWorkflowId()).isEqualTo(trigger.getWorkflowId());
    assertThat(qlPipelineAction.getWorkflowName()).isEqualTo(trigger.getWorkflowName());

    assertThat(qlPipelineAction.getManifestSelections()).isNotNull();
    QLLastDeployedManifestFromWorkflow qlLastDeployedFromPipeline =
        (QLLastDeployedManifestFromWorkflow) qlPipelineAction.getManifestSelections().get(0);
    assertThat(qlLastDeployedFromPipeline.getServiceId()).isEqualTo(manifestSelection.getServiceId());
    assertThat(qlLastDeployedFromPipeline.getServiceName()).isEqualTo(manifestSelection.getServiceName());
    assertThat(qlLastDeployedFromPipeline.getWorkflowId()).isEqualTo(manifestSelection.getWorkflowId());
    assertThat(qlLastDeployedFromPipeline.getWorkflowName()).isEqualTo(manifestSelection.getWorkflowName());

    assertThat(qlPipelineAction.getVariables()).isNotNull();
    assertThat(qlPipelineAction.getVariables().get(0).getValue())
        .isEqualTo(trigger.getWorkflowVariables().get("variableKey"));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorOnManifestSelectionsInvalidEntities() {
    QLManifestSelectionInput qlManifestSelectionInput = QLManifestSelectionInput.builder()
                                                            .manifestSelectionType(ManifestSelectionType.LAST_DEPLOYED)
                                                            .serviceId(SERVICE_ID)
                                                            .versionRegex("filter")
                                                            .pipelineId(PIPELINE_ID)
                                                            .workflowId(WORKFLOW_ID)
                                                            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .applicationId(APP_ID)
            .action(QLTriggerActionInput.builder()
                        .executionType(QLExecutionType.WORKFLOW)
                        .manifestSelections(asList(qlManifestSelectionInput))
                        .build())
            .condition(QLTriggerConditionInput.builder()
                           .conditionType(QLConditionType.ON_WEBHOOK)
                           .webhookConditionInput(
                               QLWebhookConditionInput.builder().webhookSourceType(QLWebhookSource.CUSTOM).build())
                           .build())
            .build();

    Mockito.when(serviceResourceService.get(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenReturn(Mockito.mock(Service.class));
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(null);

    assertThatThrownBy(
        ()
            -> triggerActionController
                   .resolveManifestSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"))
                   .get(0))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Workflow doesn't exist");

    qlManifestSelectionInput = QLManifestSelectionInput.builder()
                                   .manifestSelectionType(ManifestSelectionType.LAST_DEPLOYED)
                                   .serviceId(SERVICE_ID)
                                   .versionRegex("filter")
                                   .pipelineId(PIPELINE_ID)
                                   .workflowId(WORKFLOW_ID)
                                   .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput2 =
        QLCreateOrUpdateTriggerInput.builder()
            .applicationId(APP_ID)
            .action(QLTriggerActionInput.builder()
                        .executionType(QLExecutionType.PIPELINE)
                        .manifestSelections(asList(qlManifestSelectionInput))
                        .build())
            .condition(QLTriggerConditionInput.builder()
                           .conditionType(QLConditionType.ON_WEBHOOK)
                           .webhookConditionInput(
                               QLWebhookConditionInput.builder().webhookSourceType(QLWebhookSource.CUSTOM).build())
                           .build())
            .build();

    Mockito.when(serviceResourceService.get(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenReturn(Mockito.mock(Service.class));
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, false)).thenReturn(null);

    assertThatThrownBy(
        ()
            -> triggerActionController
                   .resolveManifestSelections(qlCreateOrUpdateTriggerInput2, Collections.singletonList("S1"))
                   .get(0))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Pipeline doesn't exist");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void populateManifestSelectionFromPipelineCompletion() {
    ManifestSelection manifestSource = ManifestSelection.builder()
                                           .serviceId("serviceId")
                                           .serviceName("serviceName")
                                           .type(ManifestSelectionType.PIPELINE_SOURCE)
                                           .build();
    List<ManifestSelection> manifestSelections = Arrays.asList(manifestSource);

    Map<String, String> variables = new HashMap<>();
    variables.put("variableKey", "variableResult");

    Trigger trigger = Trigger.builder()
                          .workflowId("workflowId")
                          .workflowName("workflowName")
                          .manifestSelections(manifestSelections)
                          .workflowVariables(variables)
                          .workflowType(WorkflowType.PIPELINE)
                          .build();

    QLPipelineAction qlPipelineAction = (QLPipelineAction) triggerActionController.populateTriggerAction(trigger);

    assertThat(qlPipelineAction).isNotNull();
    assertThat(qlPipelineAction.getPipelineId()).isEqualTo(trigger.getWorkflowId());
    assertThat(qlPipelineAction.getPipelineName()).isEqualTo(trigger.getWorkflowName());

    assertThat(qlPipelineAction.getManifestSelections()).isNotNull();
    QLManifestFromTriggeringPipeline qlFromTriggeringPipeline =
        (QLManifestFromTriggeringPipeline) qlPipelineAction.getManifestSelections().get(0);
    assertThat(qlFromTriggeringPipeline.getServiceId())
        .isEqualTo(trigger.getManifestSelections().get(0).getServiceId());
    assertThat(qlFromTriggeringPipeline.getServiceName())
        .isEqualTo(trigger.getManifestSelections().get(0).getServiceName());

    assertThat(qlPipelineAction.getVariables()).isNotNull();
    assertThat(qlPipelineAction.getVariables().get(0).getValue())
        .isEqualTo(trigger.getWorkflowVariables().get("variableKey"));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorOnManifestSelectionsInvalidCombinations() {
    QLManifestSelectionInput qlManifestSelectionInput =
        QLManifestSelectionInput.builder()
            .manifestSelectionType(ManifestSelectionType.WEBHOOK_VARIABLE)
            .serviceId(SERVICE_ID)
            .versionRegex("filter")
            .pipelineId(PIPELINE_ID)
            .workflowId(WORKFLOW_ID)
            .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().manifestSelections(asList(qlManifestSelectionInput)).build())
            .condition(QLTriggerConditionInput.builder()
                           .conditionType(QLConditionType.ON_WEBHOOK)
                           .webhookConditionInput(
                               QLWebhookConditionInput.builder().webhookSourceType(QLWebhookSource.GITHUB).build())
                           .build())
            .build();

    Mockito.when(serviceResourceService.get(any(), any())).thenReturn(Mockito.mock(Service.class));

    assertThatThrownBy(
        ()
            -> triggerActionController
                   .resolveManifestSelections(qlCreateOrUpdateTriggerInput, Collections.singletonList("S1"))
                   .get(0))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("WEBHOOK_VARIABLE can be used only with CUSTOM Webhook Event");

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput2 =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().manifestSelections(asList(qlManifestSelectionInput)).build())
            .condition(QLTriggerConditionInput.builder().conditionType(QLConditionType.ON_SCHEDULE).build())
            .build();

    assertThatThrownBy(
        ()
            -> triggerActionController
                   .resolveManifestSelections(qlCreateOrUpdateTriggerInput2, Collections.singletonList("S1"))
                   .get(0))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("WEBHOOK_VARIABLE can be used only with ON_WEBHOOK Condition Type");

    qlManifestSelectionInput = QLManifestSelectionInput.builder()
                                   .manifestSelectionType(ManifestSelectionType.FROM_APP_MANIFEST)
                                   .serviceId(SERVICE_ID)
                                   .versionRegex("filter")
                                   .pipelineId(PIPELINE_ID)
                                   .workflowId(WORKFLOW_ID)
                                   .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput3 =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().manifestSelections(asList(qlManifestSelectionInput)).build())
            .condition(QLTriggerConditionInput.builder().conditionType(QLConditionType.ON_SCHEDULE).build())
            .build();

    assertThatThrownBy(
        ()
            -> triggerActionController
                   .resolveManifestSelections(qlCreateOrUpdateTriggerInput3, Collections.singletonList("S1"))
                   .get(0))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("FROM_APP_MANIFEST can be used only with ON_NEW_MANIFEST Condition Type");

    qlManifestSelectionInput = QLManifestSelectionInput.builder()
                                   .manifestSelectionType(ManifestSelectionType.PIPELINE_SOURCE)
                                   .serviceId(SERVICE_ID)
                                   .versionRegex("filter")
                                   .pipelineId(PIPELINE_ID)
                                   .workflowId(WORKFLOW_ID)
                                   .build();

    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput4 =
        QLCreateOrUpdateTriggerInput.builder()
            .action(QLTriggerActionInput.builder().manifestSelections(asList(qlManifestSelectionInput)).build())
            .condition(QLTriggerConditionInput.builder().conditionType(QLConditionType.ON_SCHEDULE).build())
            .build();

    assertThatThrownBy(
        ()
            -> triggerActionController
                   .resolveManifestSelections(qlCreateOrUpdateTriggerInput4, Collections.singletonList("S1"))
                   .get(0))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("PIPELINE_SOURCE can be used only with ON_PIPELINE_COMPLETION Condition Type");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void populateManifestSelectionFromWebhookPayload() {
    ManifestSelection manifestSource = ManifestSelection.builder()
                                           .serviceId("serviceId")
                                           .serviceName("serviceName")
                                           .appManifestId("appManifestId")
                                           .type(ManifestSelectionType.WEBHOOK_VARIABLE)
                                           .build();
    List<ManifestSelection> manifestSelections = Arrays.asList(manifestSource);

    Map<String, String> variables = new HashMap<>();
    variables.put("variableKey", "variableResult");

    Trigger trigger = Trigger.builder()
                          .workflowId("workflowId")
                          .workflowName("workflowName")
                          .manifestSelections(manifestSelections)
                          .workflowVariables(variables)
                          .workflowType(WorkflowType.ORCHESTRATION)
                          .build();

    QLWorkflowAction qlWorkflowAction = (QLWorkflowAction) triggerActionController.populateTriggerAction(trigger);

    assertThat(qlWorkflowAction).isNotNull();
    assertThat(qlWorkflowAction.getWorkflowId()).isEqualTo(trigger.getWorkflowId());
    assertThat(qlWorkflowAction.getWorkflowName()).isEqualTo(trigger.getWorkflowName());

    assertThat(qlWorkflowAction.getManifestSelections()).isNotNull();
    QLManifestFromWebhookPayload qlFromWebhookPayload =
        (QLManifestFromWebhookPayload) qlWorkflowAction.getManifestSelections().get(0);
    assertThat(qlFromWebhookPayload.getServiceId()).isEqualTo(trigger.getManifestSelections().get(0).getServiceId());
    assertThat(qlFromWebhookPayload.getServiceName())
        .isEqualTo(trigger.getManifestSelections().get(0).getServiceName());
    assertThat(qlFromWebhookPayload.getAppManifestId())
        .isEqualTo(trigger.getManifestSelections().get(0).getAppManifestId());

    assertThat(qlWorkflowAction.getVariables()).isNotNull();
    assertThat(qlWorkflowAction.getVariables().get(0).getValue())
        .isEqualTo(trigger.getWorkflowVariables().get("variableKey"));
  }
}
