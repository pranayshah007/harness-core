/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.WorkflowType.ORCHESTRATION;
import static io.harness.beans.WorkflowType.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static software.wings.beans.BasicOrchestrationWorkflow.BasicOrchestrationWorkflowBuilder.aBasicOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.POST_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.PipelineStage.PipelineStageElement;
import static software.wings.beans.PipelineStage.builder;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.VariableType.TEXT;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.sm.StateType.ENV_STATE;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_FILTER;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_SOURCE_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.ENTITY_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.MANIFEST_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID_CHANGED;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.SETTING_NAME;
import static software.wings.utils.WingsTestConstants.TRIGGER_ID;
import static software.wings.utils.WingsTestConstants.TRIGGER_NAME;
import static software.wings.utils.WingsTestConstants.VARIABLE_NAME;
import static software.wings.utils.WingsTestConstants.VARIABLE_VALUE;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ArtifactMetadata;

import software.wings.beans.ArtifactVariable;
import software.wings.beans.AzureConfig;
import software.wings.beans.EntityType;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.Variable;
import software.wings.beans.VariableType;
import software.wings.beans.WebHookToken;
import software.wings.beans.Workflow;
import software.wings.beans.artifact.JenkinsArtifactStream;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.beans.trigger.ArtifactSelection;
import software.wings.beans.trigger.ArtifactTriggerCondition;
import software.wings.beans.trigger.ManifestTriggerCondition;
import software.wings.beans.trigger.NewInstanceTriggerCondition;
import software.wings.beans.trigger.PipelineAction;
import software.wings.beans.trigger.PipelineTriggerCondition;
import software.wings.beans.trigger.ScheduledTriggerCondition;
import software.wings.beans.trigger.ServiceInfraWorkflow;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.TriggerArgs;
import software.wings.beans.trigger.TriggerArtifactSelectionLastCollected;
import software.wings.beans.trigger.TriggerArtifactSelectionLastDeployed;
import software.wings.beans.trigger.TriggerArtifactSelectionWebhook;
import software.wings.beans.trigger.TriggerArtifactVariable;
import software.wings.beans.trigger.TriggerLastDeployedType;
import software.wings.beans.trigger.WebHookTriggerCondition;
import software.wings.beans.trigger.WebhookEventType;
import software.wings.beans.trigger.WebhookSource;
import software.wings.beans.trigger.WorkflowAction;
import software.wings.persistence.artifact.Artifact;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(CDC)
@TargetModule(HarnessModule._815_CG_TRIGGERS)
public class TriggerServiceTestHelper {
  private static final String ARTIFACT_STREAM_ID_1 = "ARTIFACT_STREAM_ID_1";
  public static Artifact artifact = anArtifact()
                                        .withAppId(APP_ID)
                                        .withUuid(ARTIFACT_ID)
                                        .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                        .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", ARTIFACT_FILTER)))
                                        .build();

  public static Trigger buildArtifactTrigger() {
    return Trigger.builder()
        .workflowId(PIPELINE_ID)
        .uuid(TRIGGER_ID)
        .appId(APP_ID)
        .name(TRIGGER_NAME)
        .condition(ArtifactTriggerCondition.builder()
                       .artifactFilter(ARTIFACT_FILTER)
                       .artifactStreamId(ARTIFACT_STREAM_ID)
                       .build())
        .build();
  }

  public static Trigger buildArtifactTriggerWithArtifactSelections() {
    return Trigger.builder()
        .workflowId(PIPELINE_ID)
        .uuid(TRIGGER_ID)
        .appId(APP_ID)
        .name(TRIGGER_NAME)
        .artifactSelections(Arrays.asList(ArtifactSelection.builder()
                                              .serviceId(SERVICE_ID)
                                              .artifactStreamId(ARTIFACT_STREAM_ID)
                                              .pipelineId(PIPELINE_ID)
                                              .type(ArtifactSelection.Type.PIPELINE_SOURCE)
                                              .build()))
        .condition(ArtifactTriggerCondition.builder()
                       .artifactFilter(ARTIFACT_FILTER)
                       .artifactStreamId(ARTIFACT_STREAM_ID)
                       .build())
        .build();
  }

  public static Trigger buildWorkflowArtifactTrigger() {
    return Trigger.builder()
        .workflowId(WORKFLOW_ID)
        .uuid(TRIGGER_ID)
        .workflowType(ORCHESTRATION)
        .appId(APP_ID)
        .name(TRIGGER_NAME)
        .condition(ArtifactTriggerCondition.builder()
                       .artifactFilter(ARTIFACT_FILTER)
                       .artifactStreamId(ARTIFACT_STREAM_ID)
                       .build())
        .build();
  }

  public static Trigger buildPipelineCondTrigger() {
    return Trigger.builder()
        .workflowId(PIPELINE_ID)
        .uuid(TRIGGER_ID)
        .appId(APP_ID)
        .workflowType(PIPELINE)
        .name(TRIGGER_NAME)
        .condition(PipelineTriggerCondition.builder().pipelineId(PIPELINE_ID).build())
        .build();
  }

  public static Trigger buildScheduledCondTrigger() {
    return Trigger.builder()
        .workflowId(PIPELINE_ID)
        .accountId(ACCOUNT_ID)
        .uuid(TRIGGER_ID)
        .appId(APP_ID)
        .name(TRIGGER_NAME)
        .condition(ScheduledTriggerCondition.builder().cronExpression("0/5 0 ? * * *").build())
        .build();
  }

  public static Trigger buildNewArtifactTrigger() {
    return Trigger.builder()
        .workflowId(PIPELINE_ID)
        .uuid(TRIGGER_ID)
        .appId(APP_ID)
        .name(TRIGGER_NAME)
        .condition(ArtifactTriggerCondition.builder().artifactStreamId(ARTIFACT_STREAM_ID_1).build())
        .build();
  }

  public static Trigger buildNewManifestTrigger() {
    return Trigger.builder()
        .workflowId(PIPELINE_ID)
        .uuid(TRIGGER_ID)
        .appId(APP_ID)
        .name(TRIGGER_NAME)
        .condition(ManifestTriggerCondition.builder()
                       .appManifestId(MANIFEST_ID)
                       .serviceName(SERVICE_NAME)
                       .versionRegex("chart")
                       .build())
        .build();
  }

  public static WorkflowAction getWorkflowAction() {
    return WorkflowAction.builder().workflowId(WORKFLOW_ID).triggerArgs(getTriggerArgs()).build();
  }

  public static TriggerArgs getTriggerArgs() {
    return TriggerArgs.builder()
        .triggerArtifactVariables(asList(TriggerArtifactVariable.builder()
                                             .variableName(VARIABLE_NAME)
                                             .variableValue(TriggerArtifactSelectionLastCollected.builder()
                                                                .artifactStreamId("${streamId}")
                                                                .artifactServerId("${serverId}")
                                                                .artifactFilter("${filter}")
                                                                .build())
                                             .entityId(ENTITY_ID)
                                             .entityType(EntityType.SERVICE)
                                             .build(),
            TriggerArtifactVariable.builder()
                .variableName(VARIABLE_NAME)
                .variableValue(TriggerArtifactSelectionLastDeployed.builder()
                                   .id("${id}")
                                   .type(TriggerLastDeployedType.WORKFLOW)
                                   .build())
                .entityId(ENTITY_ID)
                .entityType(EntityType.SERVICE)
                .build(),
            TriggerArtifactVariable.builder()
                .variableName(VARIABLE_NAME)
                .variableValue(TriggerArtifactSelectionWebhook.builder()
                                   .artifactStreamId("${streamId}")
                                   .artifactServerId("${serverId}")
                                   .build())
                .entityId(ENTITY_ID)
                .entityType(EntityType.SERVICE)
                .build()))
        .variables(asList(aVariable().type(TEXT).name("name").value("${abc}").mandatory(true).build()))
        .build();
  }

  public static PipelineAction getPipelineAction() {
    return PipelineAction.builder().pipelineId(PIPELINE_ID).triggerArgs(getTriggerArgs()).build();
  }

  public static Trigger buildWorkflowScheduledCondTrigger() {
    return Trigger.builder()
        .workflowId(WORKFLOW_ID)
        .uuid(TRIGGER_ID)
        .appId(APP_ID)
        .workflowType(ORCHESTRATION)
        .name(TRIGGER_NAME)
        .condition(ScheduledTriggerCondition.builder().cronExpression("0/5 0 ? * * *").build())
        .build();
  }

  public static Trigger buildWebhookCondTrigger() {
    return Trigger.builder()
        .workflowId(PIPELINE_ID)
        .uuid(TRIGGER_ID)
        .appId(APP_ID)
        .name(TRIGGER_NAME)
        .condition(WebHookTriggerCondition.builder()
                       .webHookToken(WebHookToken.builder().webHookToken(generateUuid()).build())
                       .build())
        .build();
  }

  public static Trigger buildPipelineWebhookTriggerWithFileContentChanged(
      String repoName, String branchName, String gitConnectorId, WebhookEventType webhookEventType, String filePath) {
    return Trigger.builder()
        .workflowId(PIPELINE_ID)
        .workflowType(PIPELINE)
        .uuid(TRIGGER_ID)
        .appId(APP_ID)
        .name(TRIGGER_NAME)
        .condition(WebHookTriggerCondition.builder()
                       .webHookToken(WebHookToken.builder().build())
                       .webhookSource(WebhookSource.GITHUB)
                       .parameters(ImmutableMap.of("MyVar", "MyVal"))
                       .checkFileContentChanged(true)
                       .repoName(repoName)
                       .branchName(branchName)
                       .gitConnectorId(gitConnectorId)
                       .eventTypes(Arrays.asList(webhookEventType))
                       .filePaths(Arrays.asList(filePath))
                       .build())
        .build();
  }

  public static Trigger buildNewInstanceTrigger() {
    return Trigger.builder()
        .uuid(TRIGGER_ID)
        .appId(APP_ID)
        .workflowType(ORCHESTRATION)
        .workflowId(WORKFLOW_ID)
        .name("New Instance Trigger")
        .serviceInfraWorkflows(
            asList(ServiceInfraWorkflow.builder().infraMappingId(INFRA_MAPPING_ID).workflowId(WORKFLOW_ID).build()))
        .condition(NewInstanceTriggerCondition.builder().build())
        .build();
  }

  public static Pipeline buildPipeline() {
    Pipeline pipeline = Pipeline.builder()
                            .appId(APP_ID)
                            .uuid(PIPELINE_ID)
                            .services(asList(Service.builder().uuid(SERVICE_ID).name("Catalog").build()))
                            .build();
    List<Variable> userVariables = new ArrayList<>();
    userVariables.add(ArtifactVariable.builder()
                          .entityId(ENTITY_ID)
                          .name(VARIABLE_NAME)
                          .value(VARIABLE_VALUE)
                          .type(VariableType.ARTIFACT)
                          .build());

    pipeline.setPipelineVariables(userVariables);

    return pipeline;
  }

  public static Trigger buildWorkflowWebhookTrigger() {
    return Trigger.builder()
        .workflowId(WORKFLOW_ID)
        .workflowType(ORCHESTRATION)
        .uuid(TRIGGER_ID)
        .appId(APP_ID)
        .name(TRIGGER_NAME)
        .accountId(ACCOUNT_ID)
        .condition(WebHookTriggerCondition.builder()
                       .webHookToken(WebHookToken.builder().build())
                       .parameters(ImmutableMap.of("MyVar", "MyVal"))
                       .build())
        .build();
  }

  public static Trigger buildWorkflowWebhookTriggerWithFileContentChanged(
      String repoName, String branchName, String gitConnectorId, WebhookEventType webhookEventType, String filePath) {
    return Trigger.builder()
        .workflowId(WORKFLOW_ID)
        .workflowType(ORCHESTRATION)
        .uuid(TRIGGER_ID)
        .appId(APP_ID)
        .name(TRIGGER_NAME)
        .condition(WebHookTriggerCondition.builder()
                       .webHookToken(WebHookToken.builder().build())
                       .webhookSource(WebhookSource.GITHUB)
                       .parameters(ImmutableMap.of("MyVar", "MyVal"))
                       .checkFileContentChanged(true)
                       .repoName(repoName)
                       .branchName(branchName)
                       .gitConnectorId(gitConnectorId)
                       .eventTypes(Arrays.asList(webhookEventType))
                       .filePaths(Arrays.asList(filePath))
                       .build())
        .build();
  }

  public static Workflow buildWorkflow() {
    List<Variable> userVariables = new ArrayList<>();
    userVariables.add(aVariable().name("MyVar").value("MyVal").build());
    return aWorkflow()
        .envId(ENV_ID)
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .serviceId(SERVICE_ID)
        .infraMappingId(INFRA_MAPPING_ID)
        .orchestrationWorkflow(aBasicOrchestrationWorkflow()
                                   .withUserVariables(userVariables)
                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT, "Pre-Deployment").build())
                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT, "Post-Deployment").build())
                                   .build())
        .services(asList(Service.builder().uuid(SERVICE_ID).name("Catalog").build(),
            Service.builder().uuid(SERVICE_ID_CHANGED).name("Order").build()))
        .build();
  }

  public static JenkinsArtifactStream buildJenkinsArtifactStream() {
    return JenkinsArtifactStream.builder()
        .appId(APP_ID)
        .uuid(ARTIFACT_STREAM_ID)
        .sourceName(ARTIFACT_SOURCE_NAME)
        .settingId(SETTING_ID)
        .jobname("JOB")
        .serviceId(SERVICE_ID)
        .artifactPaths(asList("*WAR"))
        .build();
  }

  public static NexusArtifactStream buildNexusArtifactStream() {
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("releases")
                                                  .groupId("${groupId}")
                                                  .artifactPaths(asList("${path}"))
                                                  .autoPopulate(false)
                                                  .serviceId(SERVICE_ID)
                                                  .name("testNexus")
                                                  .uuid(ARTIFACT_STREAM_ID_1)
                                                  .build();
    nexusArtifactStream.setArtifactStreamParameterized(true);
    return nexusArtifactStream;
  }

  public static SettingAttribute buildSettingAttribute() {
    return aSettingAttribute()
        .withName(SETTING_NAME)
        .withUuid(SETTING_ID)
        .withValue(AzureConfig.builder().clientId("ClientId").tenantId("tenantId").key("key".toCharArray()).build())
        .build();
  }

  public static void setPipelineStages(Pipeline pipeline) {
    Map<String, Object> properties = new HashMap<>();
    properties.put("envId", ENV_ID);
    properties.put("workflowId", WORKFLOW_ID);

    List<PipelineStage> pipelineStages = new ArrayList<>();
    PipelineStage pipelineStage =
        builder()
            .pipelineStageElements(asList(PipelineStageElement.builder()
                                              .name("STAGE 1")
                                              .type(ENV_STATE.name())
                                              .properties(properties)
                                              .workflowVariables(ImmutableMap.of("Environment", ENV_ID, "Service",
                                                  SERVICE_ID, "ServiceInfraStructure", INFRA_MAPPING_ID))
                                              .build()))
            .build();
    pipelineStages.add(pipelineStage);
    pipeline.setPipelineStages(pipelineStages);
  }

  public static Service buildSimpleService(String serviceId) {
    return Service.builder().accountId(ACCOUNT_ID).name(SERVICE_NAME).appId(APP_ID).uuid(serviceId).build();
  }

  public static ArtifactSelection buildArtifactSelection(ArtifactSelection.Type type) {
    return ArtifactSelection.builder()
        .serviceId(SERVICE_ID)
        .serviceName(SERVICE_NAME)
        .type(type)
        .artifactFilter(null)
        .regex(false)
        .build();
  }

  public static Trigger buildTrigger(ArtifactSelection artifactSelection) {
    return Trigger.builder()
        .uuid(TRIGGER_ID)
        .accountId(ACCOUNT_ID)
        .appId(APP_ID)
        .name("trigger name")
        .workflowId(WORKFLOW_ID)
        .workflowName(WORKFLOW_NAME)
        .workflowType(ORCHESTRATION)
        .artifactSelections(List.of(artifactSelection))
        .build();
  }

  public static LinkedHashMap<String, String> buildParameters() {
    return new LinkedHashMap<>(Map.of("key1", "val1"));
  }

  public static WebHookToken buildWebhookToken() {
    return WebHookToken.builder().webHookToken("TOKEN").httpMethod("GET").build();
  }

  public static String completePayload() {
    return "{\"application\":\"APP_ID\",\"parameters\":{\"key1\":\"val1\"},\"artifacts\":[{\"artifactSourceName\":\"SERVICE_NAME_ARTIFACT_SOURCE_NAME_PLACE_HOLDER\",\"service\":\"SERVICE_NAME\",\"buildNumber\":\"SERVICE_NAME_BUILD_NUMBER_PLACE_HOLDER\"}]}";
  }

  public static String simplePayload() {
    return "{\"application\":\"APP_ID\",\"parameters\":{\"key1\":\"val1\"}}";
  }
}
