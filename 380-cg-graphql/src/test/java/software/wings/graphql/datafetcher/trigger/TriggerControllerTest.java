/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.trigger;

import static io.harness.rule.OwnerRule.MILAN;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.FeatureName;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.beans.BuildWorkflow;
import software.wings.beans.Pipeline;
import software.wings.beans.Workflow;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.beans.trigger.ArtifactSelection;
import software.wings.beans.trigger.ArtifactTriggerCondition;
import software.wings.beans.trigger.PipelineTriggerCondition;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.Trigger.TriggerBuilder;
import software.wings.graphql.datafetcher.execution.PipelineExecutionController;
import software.wings.graphql.datafetcher.execution.WorkflowExecutionController;
import software.wings.graphql.schema.mutation.execution.input.QLExecutionType;
import software.wings.graphql.schema.type.trigger.QLArtifactSelection;
import software.wings.graphql.schema.type.trigger.QLConditionType;
import software.wings.graphql.schema.type.trigger.QLCreateOrUpdateTriggerInput;
import software.wings.graphql.schema.type.trigger.QLCreateOrUpdateTriggerInput.QLCreateOrUpdateTriggerInputBuilder;
import software.wings.graphql.schema.type.trigger.QLLastCollected;
import software.wings.graphql.schema.type.trigger.QLOnPipelineCompletion;
import software.wings.graphql.schema.type.trigger.QLTrigger;
import software.wings.graphql.schema.type.trigger.QLTrigger.QLTriggerBuilder;
import software.wings.graphql.schema.type.trigger.QLTriggerActionInput;
import software.wings.graphql.schema.type.trigger.QLTriggerConditionInput;
import software.wings.graphql.schema.type.trigger.QLTriggerConditionType;
import software.wings.graphql.schema.type.trigger.QLTriggerPayload;
import software.wings.graphql.schema.type.trigger.QLTriggerVariableValue;
import software.wings.graphql.schema.type.trigger.QLWebhookConditionInput;
import software.wings.graphql.schema.type.trigger.QLWorkflowAction;
import software.wings.service.impl.security.auth.DeploymentAuthHandler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
public class TriggerControllerTest extends CategoryTest {
  public static final String ACCOUNT_ID = "accountId";
  public static final String APP_ID = "appId";
  @Mock TriggerConditionController triggerConditionController;
  @Mock TriggerActionController triggerActionController;
  @Mock WorkflowService workflowService;
  @Mock PipelineService pipelineService;
  @Mock AppService appService;
  @Mock WorkflowExecutionController workflowExecutionController;
  @Mock PipelineExecutionController pipelineExecutionController;
  @Mock DeploymentAuthHandler deploymentAuthHandler;
  @Mock AuthService authService;
  @Mock HPersistence persistence;
  @Mock SettingsService settingsService;
  @Mock FeatureFlagService featureFlagService;

  @InjectMocks TriggerController triggerController = spy(new TriggerController());

  @Before
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void populateTriggerShouldReturnTriggerObject() {
    PipelineTriggerCondition pipelineTriggerCondition =
        PipelineTriggerCondition.builder().pipelineId("pipelineId").pipelineName("pipelineName").build();

    EmbeddedUser embeddedUser = EmbeddedUser.builder().name("name").email("email").uuid("uuid").build();

    Trigger trigger = Trigger.builder()
                          .workflowName("workflowName")
                          .workflowId("workflowId")
                          .condition(pipelineTriggerCondition)
                          .description("description")
                          .uuid("uuid")
                          .name("trigger")
                          .workflowType(WorkflowType.ORCHESTRATION)
                          .createdAt(13123213123L)
                          .createdBy(embeddedUser)
                          .build();

    QLOnPipelineCompletion qlOnPipelineCompletionMock =
        QLOnPipelineCompletion.builder()
            .pipelineId("pipelineId")
            .pipelineName("pipelineName")
            .triggerConditionType(QLTriggerConditionType.PIPELINE_COMPLETION)
            .build();

    when(triggerConditionController.populateTriggerCondition(any(Trigger.class), anyString()))
        .thenReturn(qlOnPipelineCompletionMock);

    List<QLArtifactSelection> artifactSelections = asList(QLLastCollected.builder()
                                                              .artifactSourceName("sourceName")
                                                              .artifactSourceId("sourceId")
                                                              .regex(true)
                                                              .artifactFilter("filter")
                                                              .serviceId("serviceId")
                                                              .serviceName("serviceName")
                                                              .build());

    List<QLTriggerVariableValue> variableValues =
        asList(QLTriggerVariableValue.builder().name("varName").value("varValue").build());

    QLWorkflowAction qlWorkflowActionMock = QLWorkflowAction.builder()
                                                .workflowId("workflowId")
                                                .workflowName("workflowName")
                                                .artifactSelections(artifactSelections)
                                                .variables(variableValues)
                                                .build();

    when(triggerActionController.populateTriggerAction(any(Trigger.class))).thenReturn(qlWorkflowActionMock);

    QLTriggerBuilder qlTriggerBuilder = QLTrigger.builder();
    triggerController.populateTrigger(trigger, qlTriggerBuilder, ACCOUNT_ID);
    QLTrigger qlTrigger = qlTriggerBuilder.build();

    assertThat(qlTrigger).isNotNull();
    assertThat(qlTrigger.getId()).isEqualTo(trigger.getUuid());
    assertThat(qlTrigger.getName()).isEqualTo(trigger.getName());
    assertThat(qlTrigger.getDescription()).isEqualTo(trigger.getDescription());
    assertThat(qlTrigger.getCreatedAt()).isEqualTo(trigger.getCreatedAt());
    assertThat(qlTrigger.getExcludeHostsWithSameArtifact()).isEqualTo(false);

    assertThat(qlTrigger.getCondition()).isNotNull();
    QLOnPipelineCompletion qlOnPipelineCompletion = (QLOnPipelineCompletion) qlTrigger.getCondition();
    assertThat(qlOnPipelineCompletion.getTriggerConditionType())
        .isEqualByComparingTo(QLTriggerConditionType.PIPELINE_COMPLETION);
    assertThat(qlOnPipelineCompletion.getPipelineName()).isEqualTo(pipelineTriggerCondition.getPipelineName());
    assertThat(qlOnPipelineCompletion.getPipelineId()).isEqualTo(pipelineTriggerCondition.getPipelineId());

    assertThat(qlTrigger.getAction()).isNotNull();
    QLWorkflowAction qlWorkflowAction = (QLWorkflowAction) qlTrigger.getAction();

    assertThat(qlWorkflowAction.getWorkflowId()).isEqualTo(trigger.getWorkflowId());
    assertThat(qlWorkflowAction.getWorkflowName()).isEqualTo(trigger.getWorkflowName());

    assertThat(qlWorkflowAction.getArtifactSelections()).isNotNull();
    assertThat(qlWorkflowAction.getArtifactSelections()).isNotEmpty();
    assertThat(qlWorkflowAction.getArtifactSelections().get(0)).isNotNull();
    QLLastCollected qlLastCollected = (QLLastCollected) qlWorkflowAction.getArtifactSelections().get(0);
    QLLastCollected qlLastCollectedMock = (QLLastCollected) artifactSelections.get(0);

    assertThat(qlLastCollected.getArtifactFilter()).isEqualTo(qlLastCollectedMock.getArtifactFilter());
    assertThat(qlLastCollected.getArtifactSourceId()).isEqualTo(qlLastCollectedMock.getArtifactSourceId());
    assertThat(qlLastCollected.getArtifactSourceName()).isEqualTo(qlLastCollectedMock.getArtifactSourceName());
    assertThat(qlLastCollected.getServiceId()).isEqualTo(qlLastCollectedMock.getServiceId());
    assertThat(qlLastCollected.getServiceName()).isEqualTo(qlLastCollectedMock.getServiceName());
    assertThat(qlLastCollected.getRegex()).isEqualTo(qlLastCollectedMock.getRegex());

    assertThat(qlWorkflowAction.getVariables()).isNotNull();
    assertThat(qlWorkflowAction.getVariables()).isNotEmpty();
    assertThat(qlWorkflowAction.getVariables().get(0)).isNotNull();
    assertThat(qlWorkflowAction.getVariables().get(0).getName()).isEqualTo("varName");
    assertThat(qlWorkflowAction.getVariables().get(0).getValue()).isEqualTo("varValue");

    assertThat(qlTrigger.getCreatedBy()).isNotNull();
    assertThat(qlTrigger.getCreatedBy().getId()).isEqualTo(trigger.getCreatedBy().getUuid());
    assertThat(qlTrigger.getCreatedBy().getName()).isEqualTo(trigger.getCreatedBy().getName());
    assertThat(qlTrigger.getCreatedBy().getEmail()).isEqualTo(trigger.getCreatedBy().getEmail());
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void prepareQLTriggerShouldReturnQLTriggerPayload() {
    QLOnPipelineCompletion qlOnPipelineCompletionMock =
        QLOnPipelineCompletion.builder()
            .pipelineId("pipelineId")
            .pipelineName("pipelineName")
            .triggerConditionType(QLTriggerConditionType.PIPELINE_COMPLETION)
            .build();

    when(triggerConditionController.populateTriggerCondition(any(Trigger.class), anyString()))
        .thenReturn(qlOnPipelineCompletionMock);

    List<QLArtifactSelection> artifactSelections = asList(QLLastCollected.builder()
                                                              .artifactSourceName("sourceName")
                                                              .artifactSourceId("sourceId")
                                                              .regex(true)
                                                              .artifactFilter("filter")
                                                              .serviceId("serviceId")
                                                              .serviceName("serviceName")
                                                              .build());

    List<QLTriggerVariableValue> variableValues =
        asList(QLTriggerVariableValue.builder().name("varName").value("varValue").build());

    QLWorkflowAction qlWorkflowActionMock = QLWorkflowAction.builder()
                                                .workflowId("workflowId")
                                                .workflowName("workflowName")
                                                .artifactSelections(artifactSelections)
                                                .variables(variableValues)
                                                .build();

    when(triggerActionController.populateTriggerAction(any(Trigger.class))).thenReturn(qlWorkflowActionMock);

    EmbeddedUser embeddedUser = EmbeddedUser.builder().name("name").email("email").uuid("uuid").build();

    Trigger trigger = Trigger.builder()
                          .workflowName("workflowName")
                          .workflowId("workflowId")
                          .description("description")
                          .uuid("uuid")
                          .name("trigger")
                          .workflowType(WorkflowType.ORCHESTRATION)
                          .createdAt(13123213123L)
                          .createdBy(embeddedUser)
                          .build();

    QLTriggerPayload qlTriggerPayload = triggerController.prepareQLTrigger(trigger, "mutationId", ACCOUNT_ID);
    assertThat(qlTriggerPayload).isNotNull();
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void prepareTriggerInOrchestrationWorkflowShouldReturnTrigger() {
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput = QLCreateOrUpdateTriggerInput.builder()
                                                                    .triggerId("triggerId")
                                                                    .name("name")
                                                                    .applicationId(APP_ID)
                                                                    .description("desc")
                                                                    .action(QLTriggerActionInput.builder()
                                                                                .entityId("entityId")
                                                                                .executionType(QLExecutionType.WORKFLOW)
                                                                                .artifactSelections(new ArrayList<>())
                                                                                .variables(new ArrayList<>())
                                                                                .build())
                                                                    .build();

    List<ArtifactSelection> artifactSelections = getArtifactSelections("serviceId", "serviceName", "workflowName");

    Map<String, String> workflowVariables = new HashMap<>();
    workflowVariables.put("ID", "value");

    ArtifactTriggerCondition artifactTriggerCondition = ArtifactTriggerCondition.builder()
                                                            .artifactStreamId("artifactStreamId")
                                                            .artifactSourceName("artifactSourceName")
                                                            .artifactFilter("artifactFilter")
                                                            .regex(true)
                                                            .build();

    doNothing().when(triggerController).validateTrigger(any(QLCreateOrUpdateTriggerInput.class), anyString());

    when(workflowService.readWorkflow(
             qlCreateOrUpdateTriggerInput.getApplicationId(), qlCreateOrUpdateTriggerInput.getAction().getEntityId()))
        .thenReturn(mock(Workflow.class));
    doNothing().when(triggerController).validateWorkflow(anyString(), anyString(), any(Workflow.class));
    when(workflowExecutionController.resolveEnvId(any(Workflow.class), anyList())).thenReturn("envId");
    doNothing().when(deploymentAuthHandler).authorizeWorkflowExecution(anyString(), anyString());
    doNothing().when(authService).checkIfUserAllowedToDeployWorkflowToEnv(anyString(), anyString());

    when(triggerActionController.validateAndResolveWorkflowVariables(anyList(), any(Workflow.class), anyString()))
        .thenReturn(workflowVariables);
    //    doNothing()
    //        .when(triggerController)
    //        .validateAndSetArtifactSelectionsWorkflow(anyMap(), anyList(), any(Workflow.class));
    when(triggerActionController.resolveArtifactSelections(any(QLCreateOrUpdateTriggerInput.class), anyList()))
        .thenReturn(artifactSelections);
    when(triggerConditionController.resolveTriggerCondition(any(QLCreateOrUpdateTriggerInput.class)))
        .thenReturn(artifactTriggerCondition);

    Trigger trigger = triggerController.prepareTrigger(qlCreateOrUpdateTriggerInput, ACCOUNT_ID);

    verify(triggerController, times(1)).validateTrigger(any(QLCreateOrUpdateTriggerInput.class), anyString());
    verify(workflowService, times(1)).readWorkflow(anyString(), anyString());
    verify(triggerController, times(1)).validateWorkflow(anyString(), anyString(), any(Workflow.class));
    verify(workflowExecutionController, times(1)).resolveEnvId(any(Workflow.class), anyList());
    verify(deploymentAuthHandler, times(1)).authorizeWorkflowExecution(anyString(), anyString());
    verify(authService, times(1)).checkIfUserAllowedToDeployWorkflowToEnv(anyString(), anyString());
    verify(triggerActionController, times(1))
        .validateAndResolveWorkflowVariables(anyList(), any(Workflow.class), anyString());
    verify(triggerController, times(1))
        .validateAndSetArtifactSelectionsWorkflow(
            anyMap(), any(QLCreateOrUpdateTriggerInput.class), any(Workflow.class), any(TriggerBuilder.class));
    verify(triggerActionController, times(1))
        .resolveArtifactSelections(any(QLCreateOrUpdateTriggerInput.class), anyList());
    verify(triggerConditionController, times(1)).resolveTriggerCondition(any(QLCreateOrUpdateTriggerInput.class));

    assertThat(trigger).isNotNull();
    assertThat(trigger.getUuid()).isEqualTo(qlCreateOrUpdateTriggerInput.getTriggerId());
    assertThat(trigger.getName()).isEqualTo(qlCreateOrUpdateTriggerInput.getName());
    assertThat(trigger.getDescription()).isEqualTo(qlCreateOrUpdateTriggerInput.getDescription());
    assertThat(trigger.getWorkflowId()).isEqualTo(qlCreateOrUpdateTriggerInput.getAction().getEntityId());

    assertThat(trigger.getArtifactSelections()).isNotNull();
    assertThat(trigger.getArtifactSelections()).isNotEmpty();
    assertThat(trigger.getArtifactSelections().get(0).getArtifactStreamId())
        .isEqualTo(artifactSelections.get(0).getArtifactStreamId());
    assertThat(trigger.getArtifactSelections().get(0).getArtifactSourceName())
        .isEqualTo(artifactSelections.get(0).getArtifactSourceName());
    assertThat(trigger.getArtifactSelections().get(0).getArtifactFilter())
        .isEqualTo(artifactSelections.get(0).getArtifactFilter());
    assertThat(trigger.getArtifactSelections().get(0).isRegex()).isEqualTo(artifactSelections.get(0).isRegex());

    assertThat(trigger.getWorkflowVariables()).isNotNull();
    assertThat(trigger.getWorkflowVariables().get("ID")).isEqualTo("value");

    ArtifactTriggerCondition returnedArtifactTriggerCondition = (ArtifactTriggerCondition) trigger.getCondition();
    assertThat(returnedArtifactTriggerCondition).isNotNull();
    assertThat(returnedArtifactTriggerCondition.getArtifactStreamId())
        .isEqualTo(artifactTriggerCondition.getArtifactStreamId());
    assertThat(returnedArtifactTriggerCondition.getArtifactSourceName())
        .isEqualTo(artifactTriggerCondition.getArtifactSourceName());
    assertThat(returnedArtifactTriggerCondition.getArtifactFilter())
        .isEqualTo(artifactTriggerCondition.getArtifactFilter());
    assertThat(returnedArtifactTriggerCondition.isRegex()).isEqualTo(artifactTriggerCondition.isRegex());
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void prepareTriggerInPipelineWorkflowShouldReturnTrigger() {
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput = QLCreateOrUpdateTriggerInput.builder()
                                                                    .triggerId("triggerId")
                                                                    .name("name")
                                                                    .applicationId(APP_ID)
                                                                    .description("desc")
                                                                    .action(QLTriggerActionInput.builder()
                                                                                .entityId("entityId")
                                                                                .executionType(QLExecutionType.PIPELINE)
                                                                                .artifactSelections(new ArrayList<>())
                                                                                .variables(new ArrayList<>())
                                                                                .build())
                                                                    .build();

    List<ArtifactSelection> artifactSelections = asList(ArtifactSelection.builder()
                                                            .type(ArtifactSelection.Type.LAST_DEPLOYED)
                                                            .serviceId("serviceId")
                                                            .serviceName("serviceName")
                                                            .artifactStreamId("artifactStreamId")
                                                            .artifactSourceName("sourceName")
                                                            .artifactFilter("artifactFilter")
                                                            .pipelineId("pipelineId")
                                                            .pipelineName("pipelineName")
                                                            .regex(true)
                                                            .build());

    Map<String, String> workflowVariables = new HashMap<>();
    workflowVariables.put("ID", "value");

    ArtifactTriggerCondition artifactTriggerCondition = ArtifactTriggerCondition.builder()
                                                            .artifactStreamId("artifactStreamId")
                                                            .artifactSourceName("artifactSourceName")
                                                            .artifactFilter("artifactFilter")
                                                            .regex(true)
                                                            .build();

    doNothing().when(triggerController).validateTrigger(any(QLCreateOrUpdateTriggerInput.class), anyString());
    when(pipelineService.readPipeline(anyString(), anyString(), anyBoolean())).thenReturn(mock(Pipeline.class));
    doNothing().when(triggerController).validatePipeline(anyString(), anyString(), any(Pipeline.class));
    when(pipelineExecutionController.resolveEnvId(any(Pipeline.class), anyList())).thenReturn("envId");
    doNothing().when(deploymentAuthHandler).authorizePipelineExecution(anyString(), anyString());
    doNothing().when(authService).checkIfUserAllowedToDeployPipelineToEnv(anyString(), anyString());
    doReturn(false).when(featureFlagService).isEnabled(eq(FeatureName.PIPELINE_PER_ENV_DEPLOYMENT_PERMISSION), any());

    when(triggerActionController.validateAndResolvePipelineVariables(anyList(), any(Pipeline.class), any()))
        .thenReturn(workflowVariables);
    //    doNothing()
    //        .when(triggerController)
    //        .validateAndSetArtifactSelectionsPipeline(anyMap(), anyList(), any(Pipeline.class));
    when(triggerActionController.resolveArtifactSelections(any(QLCreateOrUpdateTriggerInput.class), anyList()))
        .thenReturn(artifactSelections);
    when(triggerConditionController.resolveTriggerCondition(any(QLCreateOrUpdateTriggerInput.class)))
        .thenReturn(artifactTriggerCondition);

    doNothing().when(triggerController).validateTrigger(any(QLCreateOrUpdateTriggerInput.class), anyString());

    Trigger trigger = triggerController.prepareTrigger(qlCreateOrUpdateTriggerInput, ACCOUNT_ID);

    verify(triggerController, times(1)).validateTrigger(any(QLCreateOrUpdateTriggerInput.class), anyString());
    verify(pipelineService, times(1)).readPipeline(anyString(), anyString(), anyBoolean());
    verify(triggerController, times(1)).validatePipeline(anyString(), anyString(), any(Pipeline.class));
    verify(pipelineExecutionController, times(1)).resolveEnvId(any(Pipeline.class), anyList(), eq(true));
    verify(deploymentAuthHandler, times(1)).authorizePipelineExecution(anyString(), anyString());
    verify(triggerActionController, times(1))
        .validateAndResolvePipelineVariables(anyList(), any(Pipeline.class), any());
    verify(triggerController, times(1))
        .validateAndSetArtifactSelectionsPipeline(
            anyMap(), any(QLCreateOrUpdateTriggerInput.class), any(Pipeline.class), any(TriggerBuilder.class));
    verify(triggerActionController, times(1))
        .resolveArtifactSelections(any(QLCreateOrUpdateTriggerInput.class), anyList());
    verify(triggerConditionController, times(1)).resolveTriggerCondition(any(QLCreateOrUpdateTriggerInput.class));
    verify(authService, times(1)).checkIfUserAllowedToDeployPipelineToEnv(anyString(), any());

    assertThat(trigger).isNotNull();
    assertThat(trigger.getUuid()).isEqualTo(qlCreateOrUpdateTriggerInput.getTriggerId());
    assertThat(trigger.getName()).isEqualTo(qlCreateOrUpdateTriggerInput.getName());
    assertThat(trigger.getDescription()).isEqualTo(qlCreateOrUpdateTriggerInput.getDescription());
    assertThat(trigger.getWorkflowId()).isEqualTo(qlCreateOrUpdateTriggerInput.getAction().getEntityId());

    assertThat(trigger.getArtifactSelections()).isNotNull();
    assertThat(trigger.getArtifactSelections()).isNotEmpty();
    assertThat(trigger.getArtifactSelections().get(0).getArtifactStreamId())
        .isEqualTo(artifactSelections.get(0).getArtifactStreamId());
    assertThat(trigger.getArtifactSelections().get(0).getArtifactSourceName())
        .isEqualTo(artifactSelections.get(0).getArtifactSourceName());
    assertThat(trigger.getArtifactSelections().get(0).getArtifactFilter())
        .isEqualTo(artifactSelections.get(0).getArtifactFilter());
    assertThat(trigger.getArtifactSelections().get(0).isRegex()).isEqualTo(artifactSelections.get(0).isRegex());

    assertThat(trigger.getWorkflowVariables()).isNotNull();
    assertThat(trigger.getWorkflowVariables().get("ID")).isEqualTo("value");

    ArtifactTriggerCondition returnedArtifactTriggerCondition = (ArtifactTriggerCondition) trigger.getCondition();
    assertThat(returnedArtifactTriggerCondition).isNotNull();
    assertThat(returnedArtifactTriggerCondition.getArtifactStreamId())
        .isEqualTo(artifactTriggerCondition.getArtifactStreamId());
    assertThat(returnedArtifactTriggerCondition.getArtifactSourceName())
        .isEqualTo(artifactTriggerCondition.getArtifactSourceName());
    assertThat(returnedArtifactTriggerCondition.getArtifactFilter())
        .isEqualTo(artifactTriggerCondition.getArtifactFilter());
    assertThat(returnedArtifactTriggerCondition.isRegex()).isEqualTo(artifactTriggerCondition.isRegex());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void prepareTriggerInPipelineWorkflowShouldReturnTriggerEnvExec() {
    QLCreateOrUpdateTriggerInput createOrUpdateTriggerInput = QLCreateOrUpdateTriggerInput.builder()
                                                                  .triggerId("id")
                                                                  .name("triggername")
                                                                  .applicationId(APP_ID)
                                                                  .description("desc")
                                                                  .action(QLTriggerActionInput.builder()
                                                                              .entityId("entityId")
                                                                              .executionType(QLExecutionType.PIPELINE)
                                                                              .artifactSelections(new ArrayList<>())
                                                                              .variables(new ArrayList<>())
                                                                              .build())
                                                                  .build();

    List<ArtifactSelection> artifactSelectionList = asList(ArtifactSelection.builder()
                                                               .type(ArtifactSelection.Type.LAST_DEPLOYED)
                                                               .serviceId("id")
                                                               .serviceName("name")
                                                               .artifactStreamId("artifactStreamId")
                                                               .artifactSourceName("sourceName")
                                                               .artifactFilter("artifactFilter")
                                                               .pipelineId("pipelineId")
                                                               .pipelineName("pipelineName")
                                                               .regex(true)
                                                               .build());

    Map<String, String> workflowVariables = new HashMap<>();
    workflowVariables.put("ID", "value");

    ArtifactTriggerCondition triggerCondition = ArtifactTriggerCondition.builder()
                                                    .artifactStreamId("id")
                                                    .artifactSourceName("name")
                                                    .artifactFilter("filter")
                                                    .regex(true)
                                                    .build();

    doNothing().when(triggerController).validateTrigger(any(QLCreateOrUpdateTriggerInput.class), anyString());
    when(pipelineService.readPipeline(anyString(), anyString(), anyBoolean())).thenReturn(mock(Pipeline.class));
    doNothing().when(triggerController).validatePipeline(anyString(), anyString(), any(Pipeline.class));
    when(pipelineExecutionController.resolveEnvId(any(Pipeline.class), anyList())).thenReturn("envId");
    doNothing().when(deploymentAuthHandler).authorizePipelineExecution(anyString(), anyString());
    doNothing().when(authService).checkIfUserAllowedToDeployPipelineToEnv(anyString(), anyString());
    when(triggerActionController.validateAndResolvePipelineVariables(anyList(), any(Pipeline.class), anyString()))
        .thenReturn(workflowVariables);
    doNothing()
        .when(triggerController)
        .validateAndSetArtifactSelectionsPipeline(
            anyMap(), any(QLCreateOrUpdateTriggerInput.class), any(Pipeline.class), any(TriggerBuilder.class));
    when(triggerActionController.resolveArtifactSelections(any(QLCreateOrUpdateTriggerInput.class), anyList()))
        .thenReturn(artifactSelectionList);
    when(triggerConditionController.resolveTriggerCondition(any(QLCreateOrUpdateTriggerInput.class)))
        .thenReturn(triggerCondition);
    doReturn(false)
        .when(featureFlagService)
        .isEnabled(eq(FeatureName.PIPELINE_PER_ENV_DEPLOYMENT_PERMISSION), anyString());

    doNothing().when(triggerController).validateTrigger(any(QLCreateOrUpdateTriggerInput.class), anyString());

    Trigger trigger = triggerController.prepareTrigger(createOrUpdateTriggerInput, ACCOUNT_ID);
    verify(deploymentAuthHandler, times(1)).authorizePipelineExecution(anyString(), anyString());

    verify(authService, times(1)).checkIfUserAllowedToDeployPipelineToEnv(any(), any());
    assertThat(trigger).isNotNull();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void prepareTriggerInOrchestrationWorkflowShouldReturnTriggerExecRights() {
    QLCreateOrUpdateTriggerInput createOrUpdateTriggerInput = QLCreateOrUpdateTriggerInput.builder()
                                                                  .triggerId("id")
                                                                  .name("name")
                                                                  .applicationId(APP_ID)
                                                                  .description("desc")
                                                                  .action(QLTriggerActionInput.builder()
                                                                              .entityId("entityId")
                                                                              .executionType(QLExecutionType.WORKFLOW)
                                                                              .artifactSelections(new ArrayList<>())
                                                                              .variables(new ArrayList<>())
                                                                              .build())
                                                                  .build();

    List<ArtifactSelection> artifactSelectionList = getArtifactSelections("id", "name", "workflow-name");

    Map<String, String> workflowVariables = new HashMap<>();
    workflowVariables.put("ID", "value");

    ArtifactTriggerCondition triggerCondition = ArtifactTriggerCondition.builder()
                                                    .artifactStreamId("id")
                                                    .artifactSourceName("name")
                                                    .artifactFilter("filter")
                                                    .regex(true)
                                                    .build();

    doNothing().when(triggerController).validateTrigger(any(QLCreateOrUpdateTriggerInput.class), anyString());

    when(workflowService.readWorkflow(
             createOrUpdateTriggerInput.getApplicationId(), createOrUpdateTriggerInput.getAction().getEntityId()))
        .thenReturn(mock(Workflow.class));
    doNothing().when(triggerController).validateWorkflow(anyString(), anyString(), any(Workflow.class));
    when(workflowExecutionController.resolveEnvId(any(Workflow.class), anyList())).thenReturn("envId");
    doNothing().when(deploymentAuthHandler).authorizeWorkflowExecution(anyString(), anyString());
    doNothing().when(authService).checkIfUserAllowedToDeployWorkflowToEnv(anyString(), anyString());

    when(triggerActionController.validateAndResolveWorkflowVariables(anyList(), any(Workflow.class), anyString()))
        .thenReturn(workflowVariables);
    doNothing()
        .when(triggerController)
        .validateAndSetArtifactSelectionsWorkflow(
            anyMap(), any(QLCreateOrUpdateTriggerInput.class), any(Workflow.class), any(TriggerBuilder.class));
    when(triggerActionController.resolveArtifactSelections(any(QLCreateOrUpdateTriggerInput.class), anyList()))
        .thenReturn(artifactSelectionList);
    when(triggerConditionController.resolveTriggerCondition(any(QLCreateOrUpdateTriggerInput.class)))
        .thenReturn(triggerCondition);

    Trigger trigger = triggerController.prepareTrigger(createOrUpdateTriggerInput, ACCOUNT_ID);

    verify(triggerController, times(1)).validateTrigger(any(QLCreateOrUpdateTriggerInput.class), anyString());
    verify(workflowService, times(1)).readWorkflow(anyString(), anyString());
    verify(triggerController, times(1)).validateWorkflow(anyString(), anyString(), any(Workflow.class));
    verify(workflowExecutionController, times(1)).resolveEnvId(any(Workflow.class), anyList());
    verify(deploymentAuthHandler, times(1)).authorizeWorkflowExecution(anyString(), anyString());
    verify(authService, times(1)).checkIfUserAllowedToDeployWorkflowToEnv(anyString(), anyString());

    assertThat(trigger).isNotNull();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void prepareTriggerWorkflowShouldReturnTrigger() {
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput = getTrigger();

    List<ArtifactSelection> artifactSelections = getArtifactSelections("serviceId", "serviceName", "workflowName");

    Map<String, String> workflowVariables = new HashMap<>();
    workflowVariables.put("ID", "value");

    ArtifactTriggerCondition artifactTriggerCondition = ArtifactTriggerCondition.builder()
                                                            .artifactStreamId("artifactStreamId")
                                                            .artifactSourceName("artifactSourceName")
                                                            .artifactFilter("artifactFilter")
                                                            .regex(true)
                                                            .build();

    Workflow workflow = aWorkflow()
                            .uuid("workflowId")
                            .appId(APP_ID)
                            .orchestrationWorkflow(
                                BuildWorkflow.BuildOrchestrationWorkflowBuilder.aBuildOrchestrationWorkflow().build())
                            .build();
    when(workflowService.readWorkflow(
             qlCreateOrUpdateTriggerInput.getApplicationId(), qlCreateOrUpdateTriggerInput.getAction().getEntityId()))
        .thenReturn(workflow);

    when(workflowExecutionController.resolveEnvId(any(Workflow.class), anyList())).thenReturn("envId");
    doNothing().when(deploymentAuthHandler).authorizeWorkflowExecution(anyString(), anyString());
    doNothing().when(authService).checkIfUserAllowedToDeployWorkflowToEnv(anyString(), anyString());

    when(triggerActionController.validateAndResolveWorkflowVariables(anyList(), any(Workflow.class), anyString()))
        .thenReturn(workflowVariables);
    when(triggerActionController.resolveArtifactSelections(any(QLCreateOrUpdateTriggerInput.class), anyList()))
        .thenReturn(artifactSelections);
    when(triggerConditionController.resolveTriggerCondition(any(QLCreateOrUpdateTriggerInput.class)))
        .thenReturn(artifactTriggerCondition);
    when(persistence.get(eq(Trigger.class), anyString()))
        .thenReturn(Trigger.builder().appId(APP_ID).workflowType(WorkflowType.ORCHESTRATION).build());
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);

    Trigger trigger = triggerController.prepareTrigger(qlCreateOrUpdateTriggerInput, ACCOUNT_ID);

    verify(triggerController, times(1)).validateTrigger(any(QLCreateOrUpdateTriggerInput.class), anyString());
    verify(workflowExecutionController, times(1)).resolveEnvId(any(Workflow.class), anyList());
    verify(deploymentAuthHandler, times(1)).authorizeWorkflowExecution(anyString(), anyString());
    verify(authService, times(1)).checkIfUserAllowedToDeployWorkflowToEnv(anyString(), anyString());
    verify(triggerActionController, times(1))
        .validateAndResolveWorkflowVariables(anyList(), any(Workflow.class), anyString());
    verify(triggerController, times(1))
        .validateAndSetArtifactSelectionsWorkflow(
            anyMap(), any(QLCreateOrUpdateTriggerInput.class), any(Workflow.class), any(TriggerBuilder.class));
    verify(triggerActionController, times(1))
        .resolveArtifactSelections(any(QLCreateOrUpdateTriggerInput.class), anyList());
    verify(triggerConditionController, times(1)).resolveTriggerCondition(any(QLCreateOrUpdateTriggerInput.class));

    assertThat(trigger).isNotNull();
    assertThat(trigger.getUuid()).isEqualTo(qlCreateOrUpdateTriggerInput.getTriggerId());
    assertThat(trigger.getWorkflowId()).isEqualTo(qlCreateOrUpdateTriggerInput.getAction().getEntityId());

    assertThat(trigger.getArtifactSelections()).isNotEmpty();
    assertThat(trigger.getArtifactSelections().get(0).getArtifactStreamId())
        .isEqualTo(artifactSelections.get(0).getArtifactStreamId());

    assertThat(trigger.getWorkflowVariables()).isNotNull();
    assertThat(trigger.getWorkflowVariables().get("ID")).isEqualTo("value");

    ArtifactTriggerCondition returnedArtifactTriggerCondition = (ArtifactTriggerCondition) trigger.getCondition();
    assertThat(returnedArtifactTriggerCondition).isNotNull();
    assertThat(returnedArtifactTriggerCondition.getArtifactStreamId())
        .isEqualTo(artifactTriggerCondition.getArtifactStreamId());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void prepareTriggerWorkflowInvalidCases() {
    QLCreateOrUpdateTriggerInputBuilder qlCreateOrUpdateTriggerInputBuilder = QLCreateOrUpdateTriggerInput.builder();
    assertThatThrownBy(() -> triggerController.prepareTrigger(qlCreateOrUpdateTriggerInputBuilder.build(), ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ApplicationId must not be empty");

    qlCreateOrUpdateTriggerInputBuilder.applicationId(APP_ID);
    assertThatThrownBy(() -> triggerController.prepareTrigger(qlCreateOrUpdateTriggerInputBuilder.build(), ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ApplicationId doesn't belong to this account");

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    qlCreateOrUpdateTriggerInputBuilder.name("");
    assertThatThrownBy(() -> triggerController.prepareTrigger(qlCreateOrUpdateTriggerInputBuilder.build(), ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Trigger name must not be empty");

    qlCreateOrUpdateTriggerInputBuilder.name("trigger");
    qlCreateOrUpdateTriggerInputBuilder.action(QLTriggerActionInput.builder().entityId("").build());
    assertThatThrownBy(() -> triggerController.prepareTrigger(qlCreateOrUpdateTriggerInputBuilder.build(), ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Entity Id must not be empty");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void updateTriggerWorkflowInvalidCases() {
    QLCreateOrUpdateTriggerInputBuilder qlCreateOrUpdateTriggerInputBuilder =
        QLCreateOrUpdateTriggerInput.builder().triggerId("triggerId");
    assertThatThrownBy(() -> triggerController.prepareTrigger(qlCreateOrUpdateTriggerInputBuilder.build(), ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Trigger doesn't exist");

    when(persistence.get(eq(Trigger.class), anyString()))
        .thenReturn(Trigger.builder().appId(APP_ID).workflowType(WorkflowType.ORCHESTRATION).build());
    assertThatThrownBy(() -> triggerController.prepareTrigger(qlCreateOrUpdateTriggerInputBuilder.build(), ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Trigger doesn't exist");

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    qlCreateOrUpdateTriggerInputBuilder.action(
        QLTriggerActionInput.builder().executionType(QLExecutionType.PIPELINE).build());
    assertThatThrownBy(() -> triggerController.prepareTrigger(qlCreateOrUpdateTriggerInputBuilder.build(), ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Execution Type cannot be modified");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorOnMissingArtifactSelection() {
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput = getTrigger();

    Workflow workflow = aWorkflow()
                            .uuid("workflowId")
                            .appId(APP_ID)
                            .orchestrationWorkflow(
                                BuildWorkflow.BuildOrchestrationWorkflowBuilder.aBuildOrchestrationWorkflow().build())
                            .build();
    when(workflowService.readWorkflow(
             qlCreateOrUpdateTriggerInput.getApplicationId(), qlCreateOrUpdateTriggerInput.getAction().getEntityId()))
        .thenReturn(workflow);

    when(workflowExecutionController.resolveEnvId(any(Workflow.class), anyList())).thenReturn("envId");
    doNothing().when(deploymentAuthHandler).authorizeWorkflowExecution(anyString(), anyString());
    doNothing().when(authService).checkIfUserAllowedToDeployWorkflowToEnv(anyString(), anyString());

    when(triggerActionController.validateAndResolveWorkflowVariables(anyList(), any(Workflow.class), anyString()))
        .thenReturn(Collections.emptyMap());
    when(persistence.get(eq(Trigger.class), anyString()))
        .thenReturn(Trigger.builder().appId(APP_ID).workflowType(WorkflowType.ORCHESTRATION).build());
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(workflowService.fetchDeploymentMetadata(eq(workflow.getAppId()), eq(workflow), any(), eq(null), eq(null),
             eq(DeploymentMetadata.Include.ARTIFACT_SERVICE)))
        .thenReturn(DeploymentMetadata.builder().artifactRequiredServiceIds(asList("serviceId")).build());

    assertThatThrownBy(() -> triggerController.prepareTrigger(qlCreateOrUpdateTriggerInput, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Artifact Source for service id: serviceId must be specified");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorOnInaccessibleGitConnector() {
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .triggerId("triggerId")
            .name("name")
            .applicationId(APP_ID)
            .description("desc")
            .condition(QLTriggerConditionInput.builder()
                           .conditionType(QLConditionType.ON_WEBHOOK)
                           .webhookConditionInput(QLWebhookConditionInput.builder()
                                                      .deployOnlyIfFilesChanged(true)
                                                      .gitConnectorId("connectorId")
                                                      .build())
                           .build())
            .action(QLTriggerActionInput.builder()
                        .entityId("entityId")
                        .executionType(QLExecutionType.PIPELINE)
                        .artifactSelections(new ArrayList<>())
                        .variables(new ArrayList<>())
                        .build())
            .build();

    when(persistence.get(eq(Trigger.class), anyString()))
        .thenReturn(Trigger.builder().appId(APP_ID).workflowType(WorkflowType.PIPELINE).build());
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(settingsService.get("connectorId")).thenReturn(aSettingAttribute().withAccountId(ACCOUNT_ID).build());

    assertThatThrownBy(() -> triggerController.prepareTrigger(qlCreateOrUpdateTriggerInput, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("User doesn't have access to use the GitConnector: connectorId");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorForPipelineDoesntExist() {
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder()
            .triggerId("triggerId")
            .name("name")
            .applicationId(APP_ID)
            .description("desc")
            .condition(QLTriggerConditionInput.builder()
                           .conditionType(QLConditionType.ON_WEBHOOK)
                           .webhookConditionInput(QLWebhookConditionInput.builder()
                                                      .deployOnlyIfFilesChanged(true)
                                                      .gitConnectorId("connectorId")
                                                      .build())
                           .build())
            .action(QLTriggerActionInput.builder()
                        .entityId("entityId")
                        .executionType(QLExecutionType.PIPELINE)
                        .artifactSelections(new ArrayList<>())
                        .variables(new ArrayList<>())
                        .build())
            .build();

    List<ArtifactSelection> artifactSelections = getArtifactSelections("serviceId", "serviceName", "workflowName");

    Map<String, String> workflowVariables = new HashMap<>();
    workflowVariables.put("ID", "value");

    ArtifactTriggerCondition artifactTriggerCondition = ArtifactTriggerCondition.builder()
                                                            .artifactStreamId("artifactStreamId")
                                                            .artifactSourceName("artifactSourceName")
                                                            .artifactFilter("artifactFilter")
                                                            .regex(true)
                                                            .build();

    Workflow workflow = aWorkflow()
                            .uuid("workflowId")
                            .appId(APP_ID)
                            .orchestrationWorkflow(
                                BuildWorkflow.BuildOrchestrationWorkflowBuilder.aBuildOrchestrationWorkflow().build())
                            .build();
    when(workflowService.readWorkflow(
             qlCreateOrUpdateTriggerInput.getApplicationId(), qlCreateOrUpdateTriggerInput.getAction().getEntityId()))
        .thenReturn(workflow);

    when(workflowExecutionController.resolveEnvId(any(Workflow.class), anyList())).thenReturn("envId");
    doNothing().when(deploymentAuthHandler).authorizeWorkflowExecution(anyString(), anyString());
    doNothing().when(authService).checkIfUserAllowedToDeployWorkflowToEnv(anyString(), anyString());

    when(triggerActionController.validateAndResolveWorkflowVariables(anyList(), any(Workflow.class), anyString()))
        .thenReturn(workflowVariables);
    when(triggerActionController.resolveArtifactSelections(any(QLCreateOrUpdateTriggerInput.class), anyList()))
        .thenReturn(artifactSelections);
    when(triggerConditionController.resolveTriggerCondition(any(QLCreateOrUpdateTriggerInput.class)))
        .thenReturn(artifactTriggerCondition);
    when(persistence.get(eq(Trigger.class), anyString()))
        .thenReturn(Trigger.builder().appId(APP_ID).workflowType(WorkflowType.PIPELINE).build());
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(settingsService.get("connectorId")).thenReturn(aSettingAttribute().withAccountId(ACCOUNT_ID).build());
    when(settingsService.getFilteredSettingAttributes(any(), any(), any(), anyBoolean()))
        .thenReturn(Collections.singletonList(aSettingAttribute().build()));

    assertThatThrownBy(() -> triggerController.prepareTrigger(qlCreateOrUpdateTriggerInput, ACCOUNT_ID))
        .isInstanceOf(GeneralException.class)
        .hasMessage("Pipeline entityId doesn't exist in the specified application appId");
  }

  private QLCreateOrUpdateTriggerInput getTrigger() {
    return QLCreateOrUpdateTriggerInput.builder()
        .triggerId("triggerId")
        .name("name")
        .applicationId(APP_ID)
        .description("desc")
        .condition(QLTriggerConditionInput.builder().conditionType(QLConditionType.ON_NEW_ARTIFACT).build())
        .action(QLTriggerActionInput.builder()
                    .entityId("entityId")
                    .executionType(QLExecutionType.WORKFLOW)
                    .artifactSelections(new ArrayList<>())
                    .variables(new ArrayList<>())
                    .build())
        .build();
  }

  @NotNull
  private List<ArtifactSelection> getArtifactSelections(String serviceId, String serviceName, String workflowName) {
    return asList(ArtifactSelection.builder()
                      .type(ArtifactSelection.Type.LAST_DEPLOYED)
                      .serviceId(serviceId)
                      .serviceName(serviceName)
                      .artifactStreamId("artifactStreamId")
                      .artifactSourceName("sourceName")
                      .artifactFilter("artifactFilter")
                      .workflowId("workflowId")
                      .workflowName(workflowName)
                      .regex(true)
                      .build());
  }
}
