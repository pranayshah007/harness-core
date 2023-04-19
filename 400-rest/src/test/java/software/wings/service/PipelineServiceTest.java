/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.RAFAEL;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.rule.OwnerRule.VIKAS_M;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.BasicOrchestrationWorkflow.BasicOrchestrationWorkflowBuilder.aBasicOrchestrationWorkflow;
import static software.wings.beans.BuildWorkflow.BuildOrchestrationWorkflowBuilder.aBuildOrchestrationWorkflow;
import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.EntityType.ARTIFACT;
import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.EntityType.INFRASTRUCTURE_MAPPING;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.EntityType.SSH_PASSWORD;
import static software.wings.beans.EntityType.SSH_USER;
import static software.wings.beans.EntityType.USER_GROUP;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.POST_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.sm.StateType.APPROVAL;
import static software.wings.sm.StateType.ENV_STATE;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.CLONED_PIPELINE_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.INFRA_NAME;
import static software.wings.utils.WingsTestConstants.PIPELINE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static java.util.Arrays.asList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotSame;
import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.RepairActionCode;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.limits.Action;
import io.harness.limits.ActionType;
import io.harness.limits.LimitCheckerFactory;
import io.harness.limits.checker.UsageLimitExceededException;
import io.harness.persistence.HQuery;
import io.harness.resource.Loader;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentType;
import software.wings.beans.ArtifactVariable;
import software.wings.beans.EntityType;
import software.wings.beans.EnvSummary;
import software.wings.beans.FailureStrategy;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineExecution;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.RuntimeInputsConfig;
import software.wings.beans.Service;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.beans.deployment.DeploymentMetadata.Include;
import software.wings.dl.WingsPersistence;
import software.wings.scheduler.BackgroundJobScheduler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ResourceLookupService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.yaml.YamlDirectoryService;
import software.wings.sm.StateMachine;
import software.wings.sm.StateType;
import software.wings.utils.WingsTestConstants.MockChecker;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import dev.morphia.Key;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.MorphiaIterator;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class PipelineServiceTest extends WingsBaseTest {
  private static final String PIPELINE = Loader.load("pipeline/dry_run.json");
  private static final String PIPELINE_NAME = "pipeline1";
  private static final String UPDATED_PIPELINE_NAME = "Changed Pipeline";

  @Mock private AppService appService;
  @Mock private TriggerService triggerService;
  @Mock private WingsPersistence wingsPersistence;
  @Mock private WorkflowService workflowService;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private UpdateOperations<Pipeline> updateOperations;
  @Mock private HQuery<PipelineExecution> query;
  @Mock private HQuery<Pipeline> pipelineQuery;
  @Mock private HQuery<Pipeline> clonedPipelineQuery;
  @Mock private BackgroundJobScheduler jobScheduler;
  @Mock private YamlDirectoryService yamlDirectoryService;
  @Mock private MorphiaIterator<Pipeline, Pipeline> pipelineIterator;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private EnvironmentService environmentService;
  @Mock private LimitCheckerFactory limitCheckerFactory;
  @Mock private UserGroupService userGroupService;

  @Mock Query<Pipeline> pquery;
  @Mock private FieldEnd end;

  @Mock private FeatureFlagService featureFlagService;

  @Inject @InjectMocks private PipelineService pipelineService;
  @Inject @InjectMocks ResourceLookupService resourceLookupService;

  @Captor private ArgumentCaptor<Pipeline> pipelineArgumentCaptor;
  @Captor private ArgumentCaptor<StateMachine> stateMachineArgumentCaptor;

  @Before
  public void setUp() {
    when(wingsPersistence.createQuery(PipelineExecution.class)).thenReturn(query);

    when(wingsPersistence.createQuery(Pipeline.class)).thenReturn(pipelineQuery);
    when(pipelineQuery.filter(any(), any())).thenReturn(pipelineQuery);
    when(pipelineQuery.project(anyString(), anyBoolean())).thenReturn(pipelineQuery);
    when(wingsPersistence.createUpdateOperations(Pipeline.class)).thenReturn(updateOperations);
    when(appService.get(APP_ID)).thenReturn(anApplication().uuid(APP_ID).name(APP_NAME).build());
    when(updateOperations.set(any(), any())).thenReturn(updateOperations);
    when(updateOperations.unset(any())).thenReturn(updateOperations);
    when(serviceResourceService.fetchServicesByUuids(APP_ID, Arrays.asList(SERVICE_ID)))
        .thenReturn(Arrays.asList(Service.builder().name(SERVICE_NAME).uuid(SERVICE_ID).build()));
    when(workflowService.fetchDeploymentMetadata(
             any(), any(Workflow.class), any(), anyList(), anyList(), any(DeploymentMetadata.Include[].class)))
        .thenReturn(DeploymentMetadata.builder()
                        .artifactRequiredServiceIds(asList(SERVICE_ID))
                        .envIds(asList(ENV_ID))
                        .deploymentTypes(asList(DeploymentType.SSH))
                        .build());
    when(workflowService.readWorkflowWithoutServices(APP_ID, WORKFLOW_ID))
        .thenReturn(aWorkflow().orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build());
    when(environmentService.obtainEnvironmentSummaries(APP_ID, asList(ENV_ID)))
        .thenReturn(
            asList(EnvSummary.builder().name(ENV_NAME).uuid(ENV_ID).environmentType(EnvironmentType.PROD).build()));
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldCreatePipelineFromJson() {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_PIPELINE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_PIPELINE));

    String appId = "BB1xpV5rSmGHersn1KwCnA";
    String workflowId = "7-fkKHxHS7SsDeWbRa2zCw";

    Pipeline pipeline = JsonUtils.asObject(PIPELINE, Pipeline.class);
    when(workflowService.readWorkflow(appId, workflowId))
        .thenReturn(aWorkflow().orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build());
    when(workflowService.readWorkflowWithoutServices(appId, workflowId))
        .thenReturn(aWorkflow().orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build());

    when(workflowService.stencilMap(any())).thenReturn(ImmutableMap.of("ENV_STATE", StateType.ENV_STATE));
    when(workflowService.fetchDeploymentMetadata(any(), any(Workflow.class), anyMap(), any(), any(), any()))
        .thenReturn(DeploymentMetadata.builder().build());
    pipelineService.save(pipeline);

    verify(wingsPersistence).save(pipelineArgumentCaptor.capture());
    assertThat(pipelineArgumentCaptor.getValue()).isNotNull();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldCreateLargePipeline() {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_PIPELINE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_PIPELINE));

    List<String> workflowIds = new ArrayList<>();
    List<PipelineStage> pipelineStages = new ArrayList<>();
    for (int index = 0; index < 60; index++) {
      String uuid = generateUuid();
      workflowIds.add(uuid);
      when(workflowService.readWorkflow(APP_ID, uuid))
          .thenReturn(aWorkflow().orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build());
      when(workflowService.readWorkflowWithoutServices(APP_ID, uuid))
          .thenReturn(aWorkflow().orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build());

      Map<String, Object> properties = new HashMap<>();
      properties.put("envId", generateUuid());
      properties.put("workflowId", uuid);
      PipelineStage pipelineStage = PipelineStage.builder()
                                        .pipelineStageElements(asList(PipelineStageElement.builder()
                                                                          .name("STAGE" + index)
                                                                          .type(ENV_STATE.name())
                                                                          .properties(properties)
                                                                          .build()))
                                        .build();
      if (index % 16 == 0) {
        pipelineStage.setParallel(false);
      } else {
        pipelineStage.setParallel(true);
      }
      pipelineStages.add(pipelineStage);
    }

    Pipeline pipeline =
        Pipeline.builder().name("pipeline1").appId(APP_ID).uuid(PIPELINE_ID).pipelineStages(pipelineStages).build();

    when(workflowService.stencilMap(any())).thenReturn(ImmutableMap.of("ENV_STATE", StateType.ENV_STATE));

    pipelineService.save(pipeline);

    verify(wingsPersistence).save(pipelineArgumentCaptor.capture());
    assertThat(pipelineArgumentCaptor.getValue()).isNotNull();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCreatePipeline() {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_PIPELINE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_PIPELINE));

    PipelineStage pipelineStage = prepareStageSimple();

    FailureStrategy failureStrategy =
        FailureStrategy.builder().repairActionCode(RepairActionCode.MANUAL_INTERVENTION).build();
    Pipeline pipeline = Pipeline.builder()
                            .name("pipeline1")
                            .appId(APP_ID)
                            .uuid(PIPELINE_ID)
                            .pipelineStages(asList(pipelineStage))
                            .failureStrategies(asList(failureStrategy))
                            .build();

    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID))
        .thenReturn(aWorkflow().orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build());
    when(workflowService.stencilMap(any())).thenReturn(ImmutableMap.of("ENV_STATE", StateType.ENV_STATE));

    pipelineService.save(pipeline);
    verify(wingsPersistence).save(pipelineArgumentCaptor.capture());
    Pipeline argumentCaptorValue = pipelineArgumentCaptor.getValue();
    assertThat(argumentCaptorValue)
        .isNotNull()
        .extracting(Pipeline::getFailureStrategies)
        .asList()
        .containsExactly(failureStrategy);
    assertThat(argumentCaptorValue.getKeywords())
        .isNotNull()
        .contains(WorkflowType.PIPELINE.name().toLowerCase(), pipeline.getName().toLowerCase());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_createPipelineWithApprovalStage() {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_PIPELINE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_PIPELINE));
    Pipeline pipeline = getApprovalStagesPipeline();
    when(workflowService.stencilMap(any())).thenReturn(ImmutableMap.of("APPROVAL", APPROVAL));
    pipelineService.save(pipeline);
    ArgumentCaptor<String> userGroupIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(userGroupService, times(3))
        .addParentsReference(userGroupIdCaptor.capture(), any(), any(), any(), eq(EntityType.PIPELINE.name()));
    List<String> capturedUserGroupIds = userGroupIdCaptor.getAllValues();
    assertThat(capturedUserGroupIds).isEqualTo(Arrays.asList("userGroup2", "userGroup3", "userGroup1"));
    verify(userGroupService, times(0))
        .removeParentsReference(any(), any(), any(), any(), eq(EntityType.PIPELINE.name()));
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldUpdatePipelineWithFF() {
    FailureStrategy failureStrategy =
        FailureStrategy.builder().repairActionCode(RepairActionCode.MANUAL_INTERVENTION).build();
    Pipeline pipeline = getPipelineSimple(failureStrategy, prepareStageSimple());
    pipeline.getPipelineStages().get(0).setName("Stage name contains dot.");

    when(featureFlagService.isEnabled(eq(FeatureName.SPG_ENABLE_VALIDATION_WORKFLOW_PIPELINE_STAGE), any()))
        .thenReturn(true);

    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(() -> pipelineService.update(pipeline, false, false));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdatePipeline() {
    FailureStrategy failureStrategy =
        FailureStrategy.builder().repairActionCode(RepairActionCode.MANUAL_INTERVENTION).build();
    Pipeline pipeline = getPipelineSimple(failureStrategy, prepareStageSimple());

    Pipeline updatedPipeline = pipelineService.update(pipeline, false, false);

    assertThat(updatedPipeline)
        .isNotNull()
        .extracting(Pipeline::getFailureStrategies)
        .asList()
        .containsExactly(failureStrategy);

    verify(wingsPersistence, times(2)).getWithAppId(Pipeline.class, pipeline.getAppId(), pipeline.getUuid());
    verify(wingsPersistence).createQuery(Pipeline.class);
    verify(pipelineQuery, times(2)).filter(any(), any());
    verify(wingsPersistence).createUpdateOperations(Pipeline.class);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_updatePipelineWithApprovalStage() {
    PipelineStage stage1 = getSimplePipelineStageWithApprovalStep1();
    PipelineStage stage2 = getSimplePipelineStageWithApprovalStep2();
    FailureStrategy failureStrategy =
        FailureStrategy.builder().repairActionCode(RepairActionCode.MANUAL_INTERVENTION).build();
    Pipeline pipeline = Pipeline.builder()
                            .name("pipeline1")
                            .appId(APP_ID)
                            .uuid(PIPELINE_ID)
                            .pipelineStages(Collections.singletonList(stage1))
                            .failureStrategies(Collections.singletonList(failureStrategy))
                            .build();
    Pipeline updatedPipeline = Pipeline.builder()
                                   .name("pipeline2")
                                   .appId(APP_ID)
                                   .uuid(PIPELINE_ID)
                                   .pipelineStages(Collections.singletonList(stage2))
                                   .failureStrategies(Collections.singletonList(failureStrategy))
                                   .build();
    when(workflowService.stencilMap(any())).thenReturn(ImmutableMap.of("APPROVAL", APPROVAL));
    when(wingsPersistence.getWithAppId(any(), any(), any())).thenReturn(pipeline, updatedPipeline);

    pipelineService.update(updatedPipeline, false, false);

    ArgumentCaptor<String> addUserGroupIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> removeUserGroupIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(userGroupService, times(1))
        .addParentsReference(addUserGroupIdCaptor.capture(), any(), any(), any(), eq(EntityType.PIPELINE.name()));
    verify(userGroupService, times(1))
        .removeParentsReference(removeUserGroupIdCaptor.capture(), any(), any(), any(), eq(EntityType.PIPELINE.name()));
    assertThat(addUserGroupIdCaptor.getValue()).isEqualTo("userGroup3");
    assertThat(removeUserGroupIdCaptor.getValue()).isEqualTo("userGroup1");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdatePipelineWithDisableAssertion() {
    FailureStrategy failureStrategy =
        FailureStrategy.builder().repairActionCode(RepairActionCode.MANUAL_INTERVENTION).build();

    Pipeline pipeline = getPipelineSimple(failureStrategy, prepareStageDisableAssertion(ENV_ID, WORKFLOW_ID));

    Pipeline updatedPipeline = pipelineService.update(pipeline, false, false);

    assertThat(updatedPipeline)
        .isNotNull()
        .extracting(Pipeline::getFailureStrategies)
        .asList()
        .containsExactly(failureStrategy);

    verify(wingsPersistence, times(2)).getWithAppId(Pipeline.class, pipeline.getAppId(), pipeline.getUuid());
    verify(wingsPersistence).createQuery(Pipeline.class);
    verify(pipelineQuery, times(2)).filter(any(), any());
    verify(wingsPersistence).createUpdateOperations(Pipeline.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldUpdatePipelineApprovalWithDisableAssertion() {
    FailureStrategy failureStrategy =
        FailureStrategy.builder().repairActionCode(RepairActionCode.MANUAL_INTERVENTION).build();
    Pipeline pipeline = getPipelineSimple(
        failureStrategy, prepareStageApprovalDisableAssertion(), prepareStageDisableAssertion(ENV_ID, WORKFLOW_ID));
    Pipeline updatedPipeline = pipelineService.update(pipeline, false, false);
    assertThat(updatedPipeline)
        .isNotNull()
        .extracting(Pipeline::getFailureStrategies)
        .asList()
        .containsExactly(failureStrategy);

    verify(wingsPersistence, times(2)).getWithAppId(Pipeline.class, pipeline.getAppId(), pipeline.getUuid());
    verify(wingsPersistence).createQuery(Pipeline.class);
    verify(pipelineQuery, times(2)).filter(any(), any());
    verify(wingsPersistence).createUpdateOperations(Pipeline.class);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldNotUpdatePipelineWithDifferentName() {
    FailureStrategy failureStrategy =
        FailureStrategy.builder().repairActionCode(RepairActionCode.MANUAL_INTERVENTION).build();

    Pipeline saved_pipeline = Pipeline.builder()
                                  .name(PIPELINE_NAME)
                                  .appId(APP_ID)
                                  .uuid(PIPELINE_ID)
                                  .pipelineStages(asList(prepareStageSimple()))
                                  .failureStrategies(asList(failureStrategy))
                                  .build();

    Pipeline updated_pipeline = Pipeline.builder()
                                    .name(UPDATED_PIPELINE_NAME)
                                    .appId(APP_ID)
                                    .uuid(PIPELINE_ID)
                                    .pipelineStages(asList(prepareStageSimple()))
                                    .failureStrategies(asList(failureStrategy))
                                    .build();

    when(wingsPersistence.getWithAppId(any(), any(), any())).thenReturn(saved_pipeline);
    when(pipelineQuery.filter(any(), any()).getKey()).thenReturn(new Key<>(Pipeline.class, "pipelines", PIPELINE_ID));

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> pipelineService.update(updated_pipeline, false, false));
  }

  @NotNull
  private Pipeline getPipelineSimple(FailureStrategy failureStrategy, PipelineStage... pipelineStages) {
    Pipeline pipeline = Pipeline.builder()
                            .name(PIPELINE_NAME)
                            .appId(APP_ID)
                            .uuid(PIPELINE_ID)
                            .pipelineStages(asList(pipelineStages))
                            .failureStrategies(asList(failureStrategy))
                            .build();

    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID))
        .thenReturn(aWorkflow().orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build());
    when(workflowService.stencilMap(any()))
        .thenReturn(ImmutableMap.of("ENV_STATE", StateType.ENV_STATE, "APPROVAL", APPROVAL));
    when(wingsPersistence.getWithAppId(Pipeline.class, pipeline.getAppId(), pipeline.getUuid())).thenReturn(pipeline);

    pipeline.setName(UPDATED_PIPELINE_NAME);
    pipeline.setDescription("Description changed");

    return pipeline;
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldGetPipeline() {
    mockPipeline();

    Pipeline pipeline = pipelineService.readPipeline(APP_ID, PIPELINE_ID, false);
    assertThat(pipeline).isNotNull().hasFieldOrPropertyWithValue("uuid", PIPELINE_ID);
    verify(wingsPersistence).getWithAppId(Pipeline.class, APP_ID, PIPELINE_ID);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldGetPipelineWithAppId() {
    Pipeline pipeline = Pipeline.builder().name("pipeline1").appId(APP_ID).uuid(PIPELINE_ID).build();

    when(wingsPersistence.getWithAppId(Pipeline.class, pipeline.getAppId(), pipeline.getUuid())).thenReturn(pipeline);

    Pipeline pipelineFetched = pipelineService.getPipeline(APP_ID, PIPELINE_ID);
    assertThat(pipelineFetched).isNotNull();
    assertThat(pipelineFetched.getUuid()).isEqualTo(PIPELINE_ID);
    verify(wingsPersistence, times(1)).getWithAppId(Pipeline.class, APP_ID, PIPELINE_ID);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldGetPipelineWithAppIdNull() {
    Pipeline pipeline = Pipeline.builder().name("pipeline1").appId(APP_ID).uuid(PIPELINE_ID).build();

    when(wingsPersistence.getWithAppId(Pipeline.class, pipeline.getAppId(), pipeline.getUuid())).thenReturn(null);

    Pipeline pipelineFetched = pipelineService.getPipeline(APP_ID, PIPELINE_ID);
    assertThat(pipelineFetched).isNull();
    verify(wingsPersistence, times(1)).getWithAppId(Pipeline.class, APP_ID, PIPELINE_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetPipelineWithServices() {
    mockPipeline();

    when(workflowService.readWorkflowWithoutServices(eq(APP_ID), eq(WORKFLOW_ID)))
        .thenReturn(aWorkflow()
                        .services(asList(Service.builder().appId(APP_ID).uuid(SERVICE_ID).name(SERVICE_NAME).build()))
                        .orchestrationWorkflow(aBasicOrchestrationWorkflow().build())
                        .build());

    Pipeline pipeline = pipelineService.readPipeline(APP_ID, PIPELINE_ID, true);
    assertThat(pipeline).isNotNull().hasFieldOrPropertyWithValue("uuid", PIPELINE_ID);
    assertThat(pipeline.getServices()).hasSize(1).extracting("uuid").isEqualTo(asList(SERVICE_ID));
    assertThat(pipeline.getDeploymentTypes()).hasSize(1).contains(DeploymentType.SSH);
    assertThat(pipeline.getEnvSummaries()).hasSize(1).extracting("uuid").isEqualTo(asList(ENV_ID));
    assertThat(pipeline.getEnvSummaries())
        .hasSize(1)
        .extracting("environmentType")
        .isEqualTo(asList(EnvironmentType.PROD));

    verify(wingsPersistence).getWithAppId(Pipeline.class, APP_ID, PIPELINE_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldReadPipelineWithDetails() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("envId", ENV_ID);
    properties.put("workflowId", "BUILD_WORKFLOW_ID");
    PipelineStage pipelineStage2 =
        PipelineStage.builder()
            .pipelineStageElements(asList(PipelineStageElement.builder()
                                              .workflowVariables(ImmutableMap.of(ENV_NAME, ENV_ID, SERVICE_NAME,
                                                  SERVICE_ID, INFRA_NAME, INFRA_MAPPING_ID, "MyVar", ""))
                                              .name("STAGE1")
                                              .type(ENV_STATE.name())
                                              .properties(properties)
                                              .build()))
            .build();

    Pipeline pipeline2 = Pipeline.builder()
                             .name("pipeline2")
                             .appId(APP_ID)
                             .uuid(PIPELINE_ID)
                             .pipelineStages(asList(pipelineStage2,
                                 PipelineStage.builder()
                                     .pipelineStageElements(asList(PipelineStageElement
                                                                       .builder()

                                                                       .name("STAGE2")
                                                                       .type(ENV_STATE.name())
                                                                       .properties(properties)
                                                                       .build()))
                                     .build()))
                             .build();

    when(wingsPersistence.getWithAppId(Pipeline.class, APP_ID, PIPELINE_ID)).thenReturn(pipeline2);

    when(workflowService.readWorkflowWithoutServices(eq(APP_ID), eq(WORKFLOW_ID)))
        .thenReturn(aWorkflow()
                        .name(WORKFLOW_NAME)
                        .services(asList(Service.builder().appId(APP_ID).uuid(SERVICE_ID).name(SERVICE_NAME).build()))
                        .orchestrationWorkflow(
                            aBasicOrchestrationWorkflow()
                                .withUserVariables(asList(aVariable().name(ENV_NAME).entityType(ENVIRONMENT).build(),
                                    aVariable().name("MyVar").build()))
                                .build())
                        .build());

    when(workflowService.readWorkflowWithoutServices(eq(APP_ID), eq("BUILD_WORKFLOW_ID")))
        .thenReturn(aWorkflow()
                        .name(WORKFLOW_NAME)
                        .services(asList(Service.builder().appId(APP_ID).uuid(SERVICE_ID).name(SERVICE_NAME).build()))
                        .orchestrationWorkflow(
                            aBuildOrchestrationWorkflow()
                                .withUserVariables(asList(aVariable().name(ENV_NAME).entityType(ENVIRONMENT).build(),
                                    aVariable().name("MyVar").build()))
                                .build())
                        .build());

    Pipeline pipeline = pipelineService.readPipeline(APP_ID, PIPELINE_ID, true);
    assertThat(pipeline).isNotNull().hasFieldOrPropertyWithValue("uuid", PIPELINE_ID);
    assertThat(pipeline.getServices()).hasSize(1).extracting("uuid").isEqualTo(asList(SERVICE_ID));
    assertThat(pipeline.getDeploymentTypes()).hasSize(1).contains(DeploymentType.SSH);
    assertThat(pipeline.getEnvSummaries()).hasSize(1).extracting("uuid").isEqualTo(asList(ENV_ID));
    assertThat(pipeline.getEnvSummaries())
        .hasSize(1)
        .extracting("environmentType")
        .isEqualTo(asList(EnvironmentType.PROD));
    assertThat(pipeline.isValid()).isFalse();
    assertThat(pipeline.getValidationMessage()).isNotEmpty();
    assertThat(pipeline.getPipelineVariables()).hasSize(1).extracting(Variable::getName).contains("MyVar");
    assertThat(pipeline.isHasBuildWorkflow()).isTrue();

    verify(wingsPersistence).getWithAppId(Pipeline.class, APP_ID, PIPELINE_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetPipelineWithServicesAndEnvs() {
    mockPipeline();

    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID))
        .thenReturn(aWorkflow()
                        .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                                   .build())
                        .services(asList(Service.builder().appId(APP_ID).uuid(SERVICE_ID).name(SERVICE_NAME).build()))
                        .build());

    when(workflowService.resolveEnvironmentId(any(), any())).thenReturn(ENV_ID);
    when(workflowService.getResolvedServices(any(), anyMap()))
        .thenReturn(asList(Service.builder().appId(APP_ID).uuid(SERVICE_ID).name(SERVICE_NAME).build()));

    Pipeline pipeline = pipelineService.readPipelineWithResolvedVariables(APP_ID, PIPELINE_ID, null);
    assertThat(pipeline).isNotNull().hasFieldOrPropertyWithValue("uuid", PIPELINE_ID);
    assertThat(pipeline.getServices()).hasSize(1).extracting("uuid").isEqualTo(asList(SERVICE_ID));
    assertThat(pipeline.getEnvIds()).hasSize(1).contains(ENV_ID);
    verify(wingsPersistence).getWithAppId(Pipeline.class, APP_ID, PIPELINE_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotIncludeDisableStepServices() {
    when(wingsPersistence.getWithAppId(Pipeline.class, APP_ID, PIPELINE_ID))
        .thenReturn(
            Pipeline.builder()
                .appId(APP_ID)
                .uuid(PIPELINE_ID)
                .pipelineStages(asList(
                    PipelineStage.builder()
                        .pipelineStageElements(
                            asList(PipelineStageElement.builder()
                                       .name("SE")
                                       .type(ENV_STATE.name())
                                       .properties(ImmutableMap.of("envId", ENV_ID, "workflowId", WORKFLOW_ID))
                                       .build(),
                                PipelineStageElement.builder()
                                    .name("SE")
                                    .type(ENV_STATE.name())
                                    .properties(ImmutableMap.of("envId", ENV_ID, "workflowId", "DISABLE_WORKFLOW_ID"))
                                    .disable(true)
                                    .build()))
                        .build()))
                .build());

    when(workflowService.readWorkflowWithoutServices(eq(APP_ID), eq(WORKFLOW_ID)))
        .thenReturn(aWorkflow()
                        .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                                   .build())
                        .services(asList(Service.builder().appId(APP_ID).uuid(SERVICE_ID).name(SERVICE_NAME).build()))
                        .build());

    when(workflowService.readWorkflowWithoutServices(eq(APP_ID), eq("DISABLE_WORKFLOW_ID")))
        .thenReturn(aWorkflow()
                        .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                                   .build())
                        .services(asList(Service.builder()
                                             .appId(APP_ID)
                                             .uuid("DISABLE_STEP_SERVICE_ID")
                                             .name("DISABLE_STEP_SERVICE_NAME")
                                             .build()))
                        .build());

    Pipeline pipeline = pipelineService.readPipeline(APP_ID, PIPELINE_ID, true);
    assertThat(pipeline).isNotNull().hasFieldOrPropertyWithValue("uuid", PIPELINE_ID);
    assertThat(pipeline.getServices()).hasSize(1).extracting("uuid").isEqualTo(asList(SERVICE_ID));
    assertThat(pipeline.getServices()).hasSize(1).extracting("uuid").doesNotContain(asList("DISABLE_STEP_SERVICE_ID"));
    verify(wingsPersistence).getWithAppId(Pipeline.class, APP_ID, PIPELINE_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetPipelineWithTemplatizedServices() {
    when(wingsPersistence.getWithAppId(Pipeline.class, APP_ID, PIPELINE_ID))
        .thenReturn(
            Pipeline.builder()
                .appId(APP_ID)
                .uuid(PIPELINE_ID)
                .pipelineStages(
                    asList(PipelineStage.builder()
                               .pipelineStageElements(asList(
                                   PipelineStageElement.builder()
                                       .name("SE")
                                       .type(ENV_STATE.name())
                                       .properties(ImmutableMap.of("envId", ENV_ID, "workflowId", WORKFLOW_ID))
                                       .workflowVariables(ImmutableMap.of("Environment", ENV_ID, "Service", SERVICE_ID))
                                       .build()))
                               .build()))
                .build());

    when(workflowService.readWorkflowWithoutServices(eq(APP_ID), eq(WORKFLOW_ID)))
        .thenReturn(aWorkflow()
                        .orchestrationWorkflow(
                            aCanaryOrchestrationWorkflow()
                                .withUserVariables(
                                    asList(aVariable().entityType(SERVICE).name("Service").value(SERVICE_ID).build()))
                                .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                .build())
                        .build());

    Pipeline pipeline = pipelineService.readPipeline(APP_ID, PIPELINE_ID, true);

    assertThat(pipeline).isNotNull().hasFieldOrPropertyWithValue("uuid", PIPELINE_ID);
    assertThat(pipeline.getServices()).hasSize(1).extracting("uuid").isEqualTo(asList(SERVICE_ID));

    verify(wingsPersistence).getWithAppId(Pipeline.class, APP_ID, PIPELINE_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldReadPipelineWithNoPipelineVariables() {
    when(wingsPersistence.getWithAppId(Pipeline.class, APP_ID, PIPELINE_ID))
        .thenReturn(
            Pipeline.builder()
                .appId(APP_ID)
                .uuid(PIPELINE_ID)
                .pipelineStages(
                    asList(PipelineStage.builder()
                               .pipelineStageElements(asList(
                                   PipelineStageElement.builder()
                                       .name("SE")
                                       .type(ENV_STATE.name())
                                       .properties(ImmutableMap.of("envId", ENV_ID, "workflowId", WORKFLOW_ID))
                                       .workflowVariables(ImmutableMap.of("Environment", ENV_ID, "Service", SERVICE_ID))
                                       .build()))
                               .build()))
                .build());

    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID))
        .thenReturn(aWorkflow()
                        .orchestrationWorkflow(
                            aCanaryOrchestrationWorkflow()
                                .withUserVariables(
                                    asList(aVariable().entityType(SERVICE).name("Service").value(SERVICE_ID).build()))
                                .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                .build())
                        .services(asList(Service.builder().appId(APP_ID).uuid(SERVICE_ID).name(SERVICE_NAME).build()))
                        .build());

    when(workflowService.getResolvedServices(any(Workflow.class), any()))
        .thenReturn(asList(Service.builder().appId(APP_ID).uuid(SERVICE_ID).name(SERVICE_NAME).build()));
    when(workflowService.resolveEnvironmentId(any(), any())).thenReturn(ENV_ID);

    Pipeline pipeline = pipelineService.readPipelineWithResolvedVariables(APP_ID, PIPELINE_ID, null);

    assertThat(pipeline).isNotNull().hasFieldOrPropertyWithValue("uuid", PIPELINE_ID);
    assertThat(pipeline.getServices()).hasSize(1).extracting("uuid").isEqualTo(asList(SERVICE_ID));

    verify(wingsPersistence).getWithAppId(Pipeline.class, APP_ID, PIPELINE_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldReadPipelineWithResolvedPipelineVariables() {
    Pipeline pipelineWithVariables =
        Pipeline.builder()
            .appId(APP_ID)
            .uuid(PIPELINE_ID)
            .pipelineStages(
                asList(PipelineStage.builder()
                           .pipelineStageElements(
                               asList(PipelineStageElement.builder()
                                          .name("SE")
                                          .type(ENV_STATE.name())
                                          .properties(ImmutableMap.of("envId", "${ENV}", "workflowId", WORKFLOW_ID))
                                          .workflowVariables(ImmutableMap.of("Environment", "${ENV}", "Service",
                                              "${SERVICE}", "ServiceInfrastructure_SSH", "${INFRA}"))
                                          .build()))
                           .build()))
            .build();

    pipelineWithVariables.getPipelineVariables().add(aVariable().entityType(ENVIRONMENT).name("ENV").build());
    pipelineWithVariables.getPipelineVariables().add(aVariable().entityType(SERVICE).name("SERVICE").build());
    pipelineWithVariables.getPipelineVariables().add(
        aVariable().entityType(INFRASTRUCTURE_MAPPING).name("INFRA").build());

    when(wingsPersistence.getWithAppId(Pipeline.class, APP_ID, PIPELINE_ID)).thenReturn(pipelineWithVariables);

    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID))
        .thenReturn(
            aWorkflow()
                .orchestrationWorkflow(
                    aCanaryOrchestrationWorkflow()
                        .withUserVariables(asList(aVariable().entityType(SERVICE).name("Service").build(),
                            aVariable().entityType(ENVIRONMENT).name("Environment").build(),
                            aVariable().entityType(INFRASTRUCTURE_MAPPING).name("ServiceInfrastructure_SSH").build()))
                        .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                        .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                        .build())
                .services(asList(Service.builder().appId(APP_ID).uuid(SERVICE_ID).name(SERVICE_NAME).build()))
                .build());

    when(workflowService.getResolvedServices(any(Workflow.class), any()))
        .thenReturn(asList(Service.builder().appId(APP_ID).uuid(SERVICE_ID).name(SERVICE_NAME).build()));
    when(workflowService.resolveEnvironmentId(any(), any())).thenReturn(ENV_ID);

    Pipeline pipeline = pipelineService.readPipelineWithResolvedVariables(APP_ID, PIPELINE_ID,
        ImmutableMap.of("ENV", ENV_ID, "SERVICE", SERVICE_ID, "INFRA", INFRA_MAPPING_ID, "MyVar", "MyValue"));

    assertThat(pipeline).isNotNull().hasFieldOrPropertyWithValue("uuid", PIPELINE_ID);
    assertThat(pipeline.getServices()).hasSize(1).extracting("uuid").isEqualTo(asList(SERVICE_ID));

    verify(wingsPersistence).getWithAppId(Pipeline.class, APP_ID, PIPELINE_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldDeletePipeline() {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_PIPELINE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_PIPELINE));
    mockPipeline();
    when(wingsPersistence.delete(Pipeline.class, APP_ID, PIPELINE_ID)).thenReturn(true);
    when(workflowExecutionService.runningExecutionsPresent(APP_ID, PIPELINE_ID)).thenReturn(false);
    assertThat(pipelineService.deletePipeline(APP_ID, PIPELINE_ID)).isTrue();
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_deletePipelineWithApprovalStage() {
    Pipeline pipeline = getApprovalStagesPipeline();
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_PIPELINE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_PIPELINE));
    when(workflowService.stencilMap(any())).thenReturn(ImmutableMap.of("APPROVAL", APPROVAL));
    when(wingsPersistence.getWithAppId(any(), any(), any())).thenReturn(pipeline);
    when(wingsPersistence.delete(Pipeline.class, APP_ID, PIPELINE_ID)).thenReturn(true);
    when(workflowExecutionService.runningExecutionsPresent(APP_ID, PIPELINE_ID)).thenReturn(false);
    assertThat(pipelineService.deletePipeline(APP_ID, PIPELINE_ID)).isTrue();
    ArgumentCaptor<String> userGroupIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(userGroupService, times(3))
        .removeParentsReference(userGroupIdCaptor.capture(), any(), any(), any(), eq(EntityType.PIPELINE.name()));
    List<String> capturedUserGroupIds = userGroupIdCaptor.getAllValues();
    assertThat(capturedUserGroupIds).isEqualTo(Arrays.asList("userGroup2", "userGroup3", "userGroup1"));
    verify(userGroupService, times(0)).addParentsReference(any(), any(), any(), any(), eq(EntityType.PIPELINE.name()));
  }

  @Test(expected = WingsException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void deletePipelineExecutionInProgress() {
    mockPipeline();
    when(workflowExecutionService.runningExecutionsPresent(APP_ID, PIPELINE_ID)).thenReturn(true);
    pipelineService.deletePipeline(APP_ID, PIPELINE_ID);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldPruneDescendingObjects() {
    pipelineService.pruneDescendingEntities(APP_ID, PIPELINE_ID);
    InOrder inOrder = inOrder(wingsPersistence, workflowService, triggerService);
    inOrder.verify(triggerService).pruneByPipeline(APP_ID, PIPELINE_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCheckIfEnvReferenced() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("envId", ENV_ID);
    properties.put("workflowId", WORKFLOW_ID);
    PipelineStage pipelineStage =
        PipelineStage.builder()
            .pipelineStageElements(asList(
                PipelineStageElement.builder().name("STAGE1").type(ENV_STATE.name()).properties(properties).build(),
                PipelineStageElement.builder()
                    .name("STAGE2")
                    .type(ENV_STATE.name())
                    .properties(properties)
                    .workflowVariables(
                        ImmutableMap.of("Environment", ENV_ID, "Service", SERVICE_ID, "ServiceInfra", INFRA_MAPPING_ID))
                    .build()))
            .build();

    List<PipelineStage> pipelineStages = new ArrayList<>();
    pipelineStages.add(pipelineStage);

    Pipeline pipeline =
        Pipeline.builder().name("pipeline1").appId(APP_ID).pipelineStages(pipelineStages).uuid(PIPELINE_ID).build();

    when(wingsPersistence.createQuery(eq(Pipeline.class))).thenReturn(pquery);

    when(pquery.field(any())).thenReturn(end);
    when(end.in(any())).thenReturn(pquery);
    when(pquery.filter(any(), any())).thenReturn(pquery);
    when(wingsPersistence.createQuery(Pipeline.class).filter(any(), any()).get()).thenReturn(pipeline);

    when(wingsPersistence.saveAndGet(eq(Pipeline.class), eq(pipeline))).thenReturn(pipeline);

    when(pquery.fetch()).thenReturn(pipelineIterator);

    when(pipelineIterator.hasNext()).thenReturn(true).thenReturn(false);

    when(pipelineIterator.next()).thenReturn(pipeline);

    when(wingsPersistence.getWithAppId(Workflow.class, APP_ID, WORKFLOW_ID))
        .thenReturn(aWorkflow().envId(ENV_ID).build());

    List<String> refPipelines = pipelineService.obtainPipelineNamesReferencedByEnvironment(APP_ID, ENV_ID);
    assertThat(!refPipelines.isEmpty() && refPipelines.size() > 0).isTrue();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCheckTemplatedEntityReferenced() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("envId", ENV_ID);
    properties.put("workflowId", WORKFLOW_ID);
    PipelineStage pipelineStage = prepareTemplatedStage(properties);

    List<PipelineStage> pipelineStages = new ArrayList<>();
    pipelineStages.add(pipelineStage);

    Pipeline pipeline =
        Pipeline.builder().name("pipeline1").appId(APP_ID).pipelineStages(pipelineStages).uuid(PIPELINE_ID).build();

    when(wingsPersistence.createQuery(eq(Pipeline.class))).thenReturn(pquery);

    when(pquery.field(any())).thenReturn(end);
    when(end.in(any())).thenReturn(pquery);
    when(pquery.filter(any(), any())).thenReturn(pquery);
    when(wingsPersistence.createQuery(Pipeline.class).filter(any(), any()).get()).thenReturn(pipeline);

    when(wingsPersistence.saveAndGet(eq(Pipeline.class), eq(pipeline))).thenReturn(pipeline);

    when(pquery.fetch()).thenReturn(pipelineIterator);

    when(pipelineIterator.hasNext()).thenReturn(true).thenReturn(false);

    when(pipelineIterator.next()).thenReturn(pipeline);

    assertThat(pipelineService.obtainPipelineNamesReferencedByTemplatedEntity(APP_ID, ENV_ID)).isNotEmpty();
    when(pipelineIterator.hasNext()).thenReturn(true).thenReturn(false);

    when(pipelineIterator.next()).thenReturn(pipeline);
    assertThat(pipelineService.obtainPipelineNamesReferencedByTemplatedEntity(APP_ID, SERVICE_ID)).isNotEmpty();
    when(pipelineIterator.hasNext()).thenReturn(true).thenReturn(false);

    when(pipelineIterator.next()).thenReturn(pipeline);
    assertThat(pipelineService.obtainPipelineNamesReferencedByTemplatedEntity(APP_ID, INFRA_MAPPING_ID)).isNotEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldListPipelines() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("envId", ENV_ID);
    properties.put("workflowId", WORKFLOW_ID);
    PipelineStage pipelineStage =
        PipelineStage.builder()
            .pipelineStageElements(asList(
                PipelineStageElement.builder().name("STAGE1").type(ENV_STATE.name()).properties(properties).build()))
            .build();

    FailureStrategy failureStrategy =
        FailureStrategy.builder().repairActionCode(RepairActionCode.MANUAL_INTERVENTION).build();
    Pipeline pipeline = Pipeline.builder()
                            .name("pipeline1")
                            .appId(APP_ID)
                            .uuid(PIPELINE_ID)
                            .pipelineStages(asList(pipelineStage))
                            .failureStrategies(asList(failureStrategy))
                            .build();

    Map<String, Object> properties2 = new HashMap<>();
    properties2.put("envId", ENV_ID);
    properties2.put("workflowId", WORKFLOW_ID);

    Pipeline pipeline2 = Pipeline.builder()
                             .name("pipeline2")
                             .appId(APP_ID)
                             .uuid(PIPELINE_ID)
                             .pipelineStages(asList(prepareTemplatedStage(properties)))
                             .build();
    when(wingsPersistence.query(Pipeline.class, aPageRequest().build()))
        .thenReturn(aPageResponse().withResponse(asList(pipeline, pipeline2)).build());

    PageResponse pageResponse = pipelineService.listPipelines(aPageRequest().build());
    List<Pipeline> pipelines = pageResponse.getResponse();
    assertThat(pipelines).isNotEmpty().size().isEqualTo(2);
    assertThat(pipelines.get(0).getName()).isEqualTo("pipeline1");
    assertThat(pipelines.get(0).getAppId()).isEqualTo(APP_ID);
    assertThat(pipelines.get(1).getName()).isEqualTo("pipeline2");
    assertThat(pipelines.get(1).getAppId()).isEqualTo(APP_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldListPipelinesWithDetails() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("envId", ENV_ID);
    properties.put("workflowId", WORKFLOW_ID);
    PipelineStage pipelineStage =
        PipelineStage.builder()
            .pipelineStageElements(asList(
                PipelineStageElement.builder().name("STAGE1").type(ENV_STATE.name()).properties(properties).build()))
            .build();

    Pipeline pipeline = Pipeline.builder()
                            .name("pipeline1")
                            .appId(APP_ID)
                            .uuid(PIPELINE_ID)
                            .pipelineStages(asList(pipelineStage))
                            .build();

    Map<String, Object> properties2 = new HashMap<>();
    properties2.put("envId", ENV_ID);
    properties2.put("workflowId", WORKFLOW_ID);

    Pipeline pipeline2 = Pipeline.builder()
                             .name("pipeline2")
                             .appId(APP_ID)
                             .uuid(PIPELINE_ID)
                             .pipelineStages(asList(prepareTemplatedStage(properties)))
                             .build();
    when(wingsPersistence.query(Pipeline.class, aPageRequest().build()))
        .thenReturn(aPageResponse().withResponse(asList(pipeline, pipeline2)).build());

    Workflow workflow = aWorkflow().orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build();
    workflow.getOrchestrationWorkflow().setValid(true);
    when(workflowService.readWorkflowWithoutServices(eq(APP_ID), eq(WORKFLOW_ID))).thenReturn(workflow);
    PageRequest<WorkflowExecution> workflowExecutionPageRequest = aPageRequest()
                                                                      .withLimit("2")
                                                                      .addFilter("workflowId", EQ, pipeline.getUuid())
                                                                      .addFilter("appId", EQ, pipeline.getAppId())
                                                                      .build();
    when(workflowExecutionService.listExecutions(workflowExecutionPageRequest, false, false, false, false, false, true))
        .thenReturn(aPageResponse().build());

    PageResponse pageResponse = pipelineService.listPipelines(aPageRequest().build(), true, 2, false, null);
    List<Pipeline> pipelines = pageResponse.getResponse();
    assertThat(pipelines).isNotEmpty().size().isEqualTo(2);
    assertThat(pipelines.get(0).getName()).isEqualTo("pipeline1");
    assertThat(pipelines.get(0).getAppId()).isEqualTo(APP_ID);
    assertThat(pipelines.get(0).isValid()).isEqualTo(true);
    assertThat(pipelines.get(1).getName()).isEqualTo("pipeline2");
    assertThat(pipelines.get(1).isValid()).isEqualTo(false);
    assertThat(pipelines.get(1).getValidationMessage()).isNotEmpty();
    assertThat(pipelines.get(1).isHasSshInfraMapping()).isEqualTo(false);
    verify(workflowExecutionService, times(2))
        .listExecutions(any(PageRequest.class), eq(false), eq(false), eq(false), eq(false), eq(false), eq(true));
  }

  private PipelineStage prepareTemplatedStage(Map<String, Object> properties) {
    return PipelineStage.builder()
        .pipelineStageElements(asList(PipelineStageElement.builder()
                                          .workflowVariables(ImmutableMap.of("Environment", ENV_ID, "Service",
                                              SERVICE_ID, "ServiceInfra", INFRA_MAPPING_ID))
                                          .name("STAGE1")
                                          .type(ENV_STATE.name())
                                          .properties(properties)
                                          .build()))
        .build();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldListPipelinesWithVariables() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("envId", ENV_ID);
    properties.put("workflowId", WORKFLOW_ID);
    PipelineStage pipelineStage =
        PipelineStage.builder()
            .pipelineStageElements(asList(
                PipelineStageElement.builder().name("STAGE1").type(ENV_STATE.name()).properties(properties).build()))
            .build();

    FailureStrategy failureStrategy =
        FailureStrategy.builder().repairActionCode(RepairActionCode.MANUAL_INTERVENTION).build();
    Pipeline pipeline = Pipeline.builder()
                            .name("pipeline1")
                            .appId(APP_ID)
                            .uuid(PIPELINE_ID)
                            .pipelineStages(asList(pipelineStage))
                            .failureStrategies(asList(failureStrategy))
                            .build();

    Map<String, Object> properties2 = new HashMap<>();
    properties2.put("envId", ENV_ID);
    properties2.put("workflowId", WORKFLOW_ID);
    PipelineStage pipelineStage2 =
        PipelineStage.builder()
            .pipelineStageElements(asList(PipelineStageElement.builder()
                                              .workflowVariables(ImmutableMap.of(ENV_NAME, ENV_ID, SERVICE_NAME,
                                                  SERVICE_ID, INFRA_NAME, INFRA_MAPPING_ID))
                                              .name("STAGE1")
                                              .type(ENV_STATE.name())
                                              .properties(properties)
                                              .build()))
            .build();

    Pipeline pipeline2 = Pipeline.builder()
                             .name("pipeline2")
                             .appId(APP_ID)
                             .uuid(PIPELINE_ID)
                             .pipelineStages(asList(pipelineStage2))
                             .build();
    when(wingsPersistence.query(Pipeline.class, aPageRequest().build()))
        .thenReturn(aPageResponse().withResponse(asList(pipeline, pipeline2)).build());

    List<Variable> variables = Arrays.asList(aVariable().name("MyVar1").build(),
        aVariable().name("MyFixedVar").fixed(true).build(), aVariable().entityType(SERVICE).name(SERVICE_NAME).build(),
        aVariable().entityType(ENVIRONMENT).name(ENV_NAME).build(),
        aVariable().entityType(INFRASTRUCTURE_MAPPING).name(INFRA_NAME).build());

    Workflow workflow =
        aWorkflow().orchestrationWorkflow(aCanaryOrchestrationWorkflow().withUserVariables(variables).build()).build();
    when(workflowService.readWorkflowWithoutServices(eq(APP_ID), eq(WORKFLOW_ID))).thenReturn(workflow);

    when(workflowExecutionService.listExecutions(
             Mockito.any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(aPageResponse().withResponse(new ArrayList()).build());

    PageResponse pageResponse = pipelineService.listPipelines(aPageRequest().build(), true, 2, false, null);
    List<Pipeline> pipelines = pageResponse.getResponse();
    assertThat(pipelines).isNotEmpty().size().isEqualTo(2);
    assertThat(pipelines.get(0).getName()).isEqualTo("pipeline1");
    assertThat(pipelines.get(0).getPipelineVariables()).isNotEmpty().extracting(Variable::getName).contains("MyVar1");
    assertThat(pipelines.get(0).getPipelineVariables())
        .isNotEmpty()
        .extracting(Variable::getName)
        .doesNotContain("MyFixedVar");
    assertThat(pipelines.get(0).getPipelineVariables())
        .isNotEmpty()
        .extracting(Variable::getName)
        .doesNotContain(SERVICE_NAME, ENV_NAME, INFRA_NAME);
    assertThat(pipelines.get(1).getName()).isEqualTo("pipeline2");
    assertThat(pipelines.get(1).getPipelineVariables()).isNotEmpty().extracting(Variable::getName).contains("MyVar1");
    assertThat(pipelines.get(1).getPipelineVariables())
        .isNotEmpty()
        .extracting(Variable::getName)
        .doesNotContain("MyFixedVar");
    assertThat(pipelines.get(1).getPipelineVariables())
        .isNotEmpty()
        .extracting(Variable::getName)
        .doesNotContain(SERVICE_NAME, ENV_NAME, INFRA_NAME);
    assertThat(pipelines.get(1).isHasSshInfraMapping()).isEqualTo(false);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldListPipelinesWithDetailsWithSshInfraMapping() {
    PipelineStage pipelineStage = prepareStageSimple();

    Pipeline pipeline = Pipeline.builder()
                            .name("pipeline1")
                            .appId(APP_ID)
                            .uuid(PIPELINE_ID)
                            .pipelineStages(asList(pipelineStage))

                            .build();

    when(wingsPersistence.query(Pipeline.class, aPageRequest().build()))
        .thenReturn(aPageResponse().withResponse(asList(pipeline)).build());

    Workflow workflow =
        aWorkflow()
            .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                       .addWorkflowPhase(aWorkflowPhase().deploymentType(DeploymentType.SSH).build())
                                       .build())
            .build();
    workflow.getOrchestrationWorkflow().setValid(true);
    when(workflowService.readWorkflowWithoutServices(eq(APP_ID), eq(WORKFLOW_ID))).thenReturn(workflow);

    PageRequest<WorkflowExecution> workflowExecutionPageRequest = aPageRequest()
                                                                      .withLimit("2")
                                                                      .addFilter("workflowId", EQ, pipeline.getUuid())
                                                                      .addFilter("appId", EQ, pipeline.getAppId())
                                                                      .build();
    when(workflowExecutionService.listExecutions(workflowExecutionPageRequest, false, false, false, false, false, true))
        .thenReturn(aPageResponse().build());

    PageResponse pageResponse = pipelineService.listPipelines(aPageRequest().build(), true, 2, false, null);
    List<Pipeline> pipelines = pageResponse.getResponse();
    assertThat(pipelines).isNotEmpty().size().isEqualTo(1);
    assertThat(pipelines.get(0).getName()).isEqualTo("pipeline1");
    assertThat(pipelines.get(0).isValid()).isEqualTo(true);
    assertThat(pipelines.get(0).isHasSshInfraMapping()).isEqualTo(true);

    verify(workflowExecutionService, times(1))
        .listExecutions(any(PageRequest.class), eq(false), eq(false), eq(false), eq(false), eq(false), eq(true));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetRequiredEntities() {
    mockPipeline();

    Workflow workflow =
        aWorkflow()
            .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                       .withUserVariables(asList(
                                           aVariable().entityType(SERVICE).name("Service").value(SERVICE_ID).build()))
                                       .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                       .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                       .build())
            .services(asList(Service.builder().appId(APP_ID).uuid(SERVICE_ID).name(SERVICE_NAME).build()))
            .build();

    when(workflowService.readWorkflowWithoutServices(eq(APP_ID), eq(WORKFLOW_ID))).thenReturn(workflow);
    when(workflowService.fetchDeploymentMetadata(APP_ID, workflow, null, null, null, Include.ARTIFACT_SERVICE))
        .thenReturn(DeploymentMetadata.builder().artifactRequiredServiceIds(asList(SERVICE_ID)).build());
    List<EntityType> requiredEntities = pipelineService.getRequiredEntities(APP_ID, PIPELINE_ID);
    assertThat(requiredEntities).isNotEmpty().contains(ARTIFACT);

    verify(workflowService).readWorkflowWithoutServices(eq(APP_ID), eq(WORKFLOW_ID));
    verify(wingsPersistence).getWithAppId(Pipeline.class, APP_ID, PIPELINE_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetRequiredEntitiesForBuildPipeline() {
    mockPipeline();

    when(workflowService.readWorkflowWithoutServices(eq(APP_ID), eq(WORKFLOW_ID)))
        .thenReturn(aWorkflow()
                        .orchestrationWorkflow(aBuildOrchestrationWorkflow()
                                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                                   .build())
                        .build());
    List<EntityType> requiredEntities = pipelineService.getRequiredEntities(APP_ID, PIPELINE_ID);
    assertThat(requiredEntities).isEmpty();

    verify(workflowService).readWorkflowWithoutServices(eq(APP_ID), eq(WORKFLOW_ID));
    verify(wingsPersistence).getWithAppId(Pipeline.class, APP_ID, PIPELINE_ID);
  }

  @Test(expected = UsageLimitExceededException.class)
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldNotCreatePipelineWhenLimitExceeds() {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_PIPELINE)))
        .thenReturn(new MockChecker(false, ActionType.CREATE_PIPELINE));

    PipelineStage pipelineStage = prepareStageSimple();

    FailureStrategy failureStrategy =
        FailureStrategy.builder().repairActionCode(RepairActionCode.MANUAL_INTERVENTION).build();
    Pipeline pipeline = Pipeline.builder()
                            .name("pipeline1")
                            .appId(APP_ID)
                            .uuid(PIPELINE_ID)
                            .pipelineStages(asList(pipelineStage))
                            .failureStrategies(asList(failureStrategy))
                            .build();

    when(wingsPersistence.saveAndGet(eq(Pipeline.class), eq(pipeline))).thenReturn(pipeline);
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID))
        .thenReturn(aWorkflow().orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build());
    when(workflowService.stencilMap(any())).thenReturn(ImmutableMap.of("ENV_STATE", StateType.ENV_STATE));

    try {
      pipelineService.save(pipeline);
    } catch (UsageLimitExceededException e) {
      assertThat(e.getMessage()).startsWith("Usage limit reached. Limit: ");
      throw e;
    }
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotIncludeRequiredEntitiesForDisabledStep() {
    when(wingsPersistence.getWithAppId(Pipeline.class, APP_ID, PIPELINE_ID))
        .thenReturn(Pipeline.builder()
                        .appId(APP_ID)
                        .uuid(PIPELINE_ID)
                        .pipelineStages(
                            asList(PipelineStage.builder()
                                       .pipelineStageElements(asList(
                                           PipelineStageElement.builder()
                                               .name("SE")
                                               .type(ENV_STATE.name())
                                               .properties(ImmutableMap.of("envId", ENV_ID, "workflowId", WORKFLOW_ID))
                                               .disableAssertion("true")
                                               .build()))
                                       .build()))
                        .build());

    Workflow workflow =
        aWorkflow()
            .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                       .withUserVariables(asList(
                                           aVariable().entityType(SERVICE).name("Service").value(SERVICE_ID).build()))
                                       .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                       .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                       .withRequiredEntityTypes(ImmutableSet.of(ARTIFACT, SSH_USER, SSH_PASSWORD))
                                       .build())
            .services(asList(Service.builder().appId(APP_ID).uuid(SERVICE_ID).name(SERVICE_NAME).build()))
            .build();

    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    when(workflowService.fetchRequiredEntityTypes(APP_ID, workflow))
        .thenReturn(ImmutableSet.of(ARTIFACT, SSH_USER, SSH_PASSWORD));
    List<EntityType> requiredEntities = pipelineService.getRequiredEntities(APP_ID, PIPELINE_ID);
    assertThat(requiredEntities).isEmpty();

    verify(workflowService, times(0)).readWorkflow(APP_ID, WORKFLOW_ID);
    verify(wingsPersistence).getWithAppId(Pipeline.class, APP_ID, PIPELINE_ID);
  }

  private void mockPipeline() {
    when(wingsPersistence.getWithAppId(Pipeline.class, APP_ID, PIPELINE_ID))
        .thenReturn(Pipeline.builder()
                        .appId(APP_ID)
                        .uuid(PIPELINE_ID)
                        .pipelineStages(
                            asList(PipelineStage.builder()
                                       .pipelineStageElements(asList(
                                           PipelineStageElement.builder()
                                               .name("SE")
                                               .type(ENV_STATE.name())
                                               .properties(ImmutableMap.of("envId", ENV_ID, "workflowId", WORKFLOW_ID))
                                               .build()))
                                       .build()))
                        .build());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCreatePipelineWithSameName() {
    try {
      when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_PIPELINE)))
          .thenReturn(new MockChecker(true, ActionType.CREATE_PIPELINE));
      when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID))
          .thenReturn(aWorkflow().orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build());
      when(workflowService.stencilMap(any())).thenReturn(ImmutableMap.of("ENV_STATE", StateType.ENV_STATE));

      Pipeline pipeline =
          pipelineService.save(Pipeline.builder().name("pipeline").appId(APP_ID).uuid(PIPELINE_ID).build());
      assertThat(pipeline).isNotNull().hasFieldOrProperty("uuid");
      when(pipelineQuery.project(anyString(), anyBoolean()).getKey())
          .thenReturn(new Key<>(Pipeline.class, "pipelines", PIPELINE_ID));
      pipeline.setUuid(null);
      pipelineService.save(Pipeline.builder().name("pipeline").appId(APP_ID).uuid(PIPELINE_ID).build());
      fail("Expected an InvalidRequestException to be thrown");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message")).isEqualTo("Duplicate name pipeline");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testClonePipelineWithSameName() {
    try {
      when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_PIPELINE)))
          .thenReturn(new MockChecker(true, ActionType.CREATE_PIPELINE));
      when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID))
          .thenReturn(aWorkflow().orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build());
      when(workflowService.stencilMap(any())).thenReturn(ImmutableMap.of("ENV_STATE", StateType.ENV_STATE));

      Pipeline pipeline =
          pipelineService.save(Pipeline.builder().name("pipeline").appId(APP_ID).uuid(PIPELINE_ID).build());
      assertThat(pipeline).isNotNull().hasFieldOrProperty("uuid");

      when(pipelineQuery.project(anyString(), anyBoolean()).getKey())
          .thenReturn(new Key<>(Pipeline.class, "pipelines", PIPELINE_ID));
      pipelineService.clonePipeline(
          pipeline, Pipeline.builder().name("pipeline").appId(APP_ID).uuid(PIPELINE_ID).build());
      fail("Expected an InvalidRequestException to be thrown");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message")).isEqualTo("Duplicate name pipeline");
    }
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testSimpleClonePipeline() {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_PIPELINE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_PIPELINE));
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID))
        .thenReturn(aWorkflow().orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build());
    when(workflowService.stencilMap(any())).thenReturn(ImmutableMap.of("ENV_STATE", StateType.ENV_STATE));

    Pipeline savedPipeline = pipelineService.save(getPipeline("savedPipeline", APP_ID, PIPELINE_ID));
    assertThat(savedPipeline).isNotNull().hasFieldOrProperty("uuid");

    Pipeline clonedPipeline = pipelineService.clonePipeline(
        savedPipeline, Pipeline.builder().name("clonedPipeline").appId(APP_ID).uuid(CLONED_PIPELINE_ID).build());

    assertEquals("Account id should be equal", savedPipeline.getAccountId(), clonedPipeline.getAccountId());
    assertEquals("App id should be equal", savedPipeline.getAppId(), clonedPipeline.getAppId());
    assertNotSame("Pipeline id should not be same", savedPipeline.getUuid(), clonedPipeline.getUuid());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testClonePipelineWithStageInParallel() {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_PIPELINE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_PIPELINE));
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID))
        .thenReturn(aWorkflow().orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build());
    when(workflowService.stencilMap(any())).thenReturn(ImmutableMap.of("ENV_STATE", StateType.ENV_STATE));

    Pipeline pipeline = getPipeline("savedPipeline", APP_ID, PIPELINE_ID);
    pipeline.setPipelineStages(Arrays.asList(getPipelineStage("Stage1", ENV_STATE.name(), true, true)));

    Pipeline savedPipeline = pipelineService.save(pipeline);
    assertThat(savedPipeline).isNotNull().hasFieldOrProperty("uuid");

    Pipeline clonedPipeline = pipelineService.clonePipeline(
        savedPipeline, Pipeline.builder().name("clonedPipeline").appId(APP_ID).uuid(CLONED_PIPELINE_ID).build());

    assertEquals("Account id should be equal", savedPipeline.getAccountId(), clonedPipeline.getAccountId());
    assertEquals("App id should be equal", savedPipeline.getAppId(), clonedPipeline.getAppId());
    assertNotSame("Pipeline id should not be same", savedPipeline.getUuid(), clonedPipeline.getUuid());
    assertEquals("Stage name should be equal", savedPipeline.getPipelineStages().get(0).getName(),
        clonedPipeline.getPipelineStages().get(0).getName());
    assertEquals("Stage name should be equal", savedPipeline.getPipelineStages().get(0).isParallel(),
        clonedPipeline.getPipelineStages().get(0).isParallel());
    assertEquals("Stage name should be equal", savedPipeline.getPipelineStages().get(0).isValid(),
        clonedPipeline.getPipelineStages().get(0).isValid());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testClonePipelineWithStageNotInParallel() {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_PIPELINE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_PIPELINE));
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID))
        .thenReturn(aWorkflow().orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build());
    when(workflowService.stencilMap(any())).thenReturn(ImmutableMap.of("ENV_STATE", StateType.ENV_STATE));

    Pipeline pipeline = getPipeline("savedPipeline", APP_ID, PIPELINE_ID);
    pipeline.setPipelineStages(Arrays.asList(getPipelineStage("Stage1", ENV_STATE.name(), true, false)));

    Pipeline savedPipeline = pipelineService.save(pipeline);
    assertThat(savedPipeline).isNotNull().hasFieldOrProperty("uuid");

    Pipeline clonedPipeline = pipelineService.clonePipeline(
        savedPipeline, Pipeline.builder().name("clonedPipeline").appId(APP_ID).uuid(CLONED_PIPELINE_ID).build());

    assertEquals("Account id should be equal", savedPipeline.getAccountId(), clonedPipeline.getAccountId());
    assertEquals("App id should be equal", savedPipeline.getAppId(), clonedPipeline.getAppId());
    assertNotSame("Pipeline id should not be same", savedPipeline.getUuid(), clonedPipeline.getUuid());
    assertEquals("Stage name should be equal", savedPipeline.getPipelineStages().get(0).getName(),
        clonedPipeline.getPipelineStages().get(0).getName());
    assertEquals("Stage name should be equal", savedPipeline.getPipelineStages().get(0).isParallel(),
        clonedPipeline.getPipelineStages().get(0).isParallel());
    assertEquals("Stage name should be equal", savedPipeline.getPipelineStages().get(0).isValid(),
        clonedPipeline.getPipelineStages().get(0).isValid());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testClonePipelineWithSkippedStage() {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_PIPELINE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_PIPELINE));
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID))
        .thenReturn(aWorkflow().orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build());
    when(workflowService.stencilMap(any())).thenReturn(ImmutableMap.of("ENV_STATE", StateType.ENV_STATE));

    Pipeline pipeline = getPipeline("savedPipeline", APP_ID, PIPELINE_ID);
    pipeline.setPipelineStages(Arrays.asList(getPipelineStage("Stage1", ENV_STATE.name(), false, true)));

    Pipeline savedPipeline = pipelineService.save(pipeline);
    assertThat(savedPipeline).isNotNull().hasFieldOrProperty("uuid");

    Pipeline clonedPipeline = pipelineService.clonePipeline(
        savedPipeline, Pipeline.builder().name("clonedPipeline").appId(APP_ID).uuid(CLONED_PIPELINE_ID).build());

    assertEquals("Account id should be equal", savedPipeline.getAccountId(), clonedPipeline.getAccountId());
    assertEquals("App id should be equal", savedPipeline.getAppId(), clonedPipeline.getAppId());
    assertNotSame("Pipeline id should not be same", savedPipeline.getUuid(), clonedPipeline.getUuid());
    assertEquals("Stage name should be equal", savedPipeline.getPipelineStages().get(0).getName(),
        clonedPipeline.getPipelineStages().get(0).getName());
    assertEquals("Stage name should be equal", savedPipeline.getPipelineStages().get(0).isParallel(),
        clonedPipeline.getPipelineStages().get(0).isParallel());
    assertEquals("Stage name should be equal", savedPipeline.getPipelineStages().get(0).isValid(),
        clonedPipeline.getPipelineStages().get(0).isValid());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testClonePipelineWithApprovalInParallel() {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_PIPELINE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_PIPELINE));
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID))
        .thenReturn(aWorkflow().orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build());
    when(workflowService.stencilMap(any())).thenReturn(ImmutableMap.of("APPROVAL", APPROVAL));

    Pipeline pipeline = getPipeline("savedPipeline", APP_ID, PIPELINE_ID);
    pipeline.setPipelineStages(Arrays.asList(getPipelineStage("Stage1", APPROVAL.name(), true, true)));

    Pipeline savedPipeline = pipelineService.save(pipeline);
    assertThat(savedPipeline).isNotNull().hasFieldOrProperty("uuid");

    Pipeline clonedPipeline = pipelineService.clonePipeline(
        savedPipeline, Pipeline.builder().name("clonedPipeline").appId(APP_ID).uuid(CLONED_PIPELINE_ID).build());

    assertEquals("Account id should be equal", savedPipeline.getAccountId(), clonedPipeline.getAccountId());
    assertEquals("App id should be equal", savedPipeline.getAppId(), clonedPipeline.getAppId());
    assertNotSame("Pipeline id should not be same", savedPipeline.getUuid(), clonedPipeline.getUuid());
    assertEquals("Stage name should be equal", savedPipeline.getPipelineStages().get(0).getName(),
        clonedPipeline.getPipelineStages().get(0).getName());
    assertEquals("Stage name should be equal", savedPipeline.getPipelineStages().get(0).isParallel(),
        clonedPipeline.getPipelineStages().get(0).isParallel());
    assertEquals("Stage name should be equal", savedPipeline.getPipelineStages().get(0).isValid(),
        clonedPipeline.getPipelineStages().get(0).isValid());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testClonePipelineWithApprovalNotInParallel() {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_PIPELINE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_PIPELINE));
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID))
        .thenReturn(aWorkflow().orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build());
    when(workflowService.stencilMap(any())).thenReturn(ImmutableMap.of("APPROVAL", APPROVAL));

    Pipeline pipeline = getPipeline("savedPipeline", APP_ID, PIPELINE_ID);
    pipeline.setPipelineStages(Arrays.asList(getPipelineStage("Stage1", APPROVAL.name(), true, false)));

    Pipeline savedPipeline = pipelineService.save(pipeline);
    assertThat(savedPipeline).isNotNull().hasFieldOrProperty("uuid");

    Pipeline clonedPipeline = pipelineService.clonePipeline(
        savedPipeline, Pipeline.builder().name("clonedPipeline").appId(APP_ID).uuid(CLONED_PIPELINE_ID).build());

    assertEquals("Account id should be equal", savedPipeline.getAccountId(), clonedPipeline.getAccountId());
    assertEquals("App id should be equal", savedPipeline.getAppId(), clonedPipeline.getAppId());
    assertNotSame("Pipeline id should not be same", savedPipeline.getUuid(), clonedPipeline.getUuid());
    assertEquals("Stage name should be equal", savedPipeline.getPipelineStages().get(0).getName(),
        clonedPipeline.getPipelineStages().get(0).getName());
    assertEquals("Stage name should be equal", savedPipeline.getPipelineStages().get(0).isParallel(),
        clonedPipeline.getPipelineStages().get(0).isParallel());
    assertEquals("Stage name should be equal", savedPipeline.getPipelineStages().get(0).isValid(),
        clonedPipeline.getPipelineStages().get(0).isValid());
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchDeploymentMetadata() {
    validateFetchDeploymentMetadata(false, false);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchDeploymentMetadataWithId() {
    validateFetchDeploymentMetadata(true, false);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchDeploymentMetadataWithIdAndDefaultArtifact() {
    validateFetchDeploymentMetadata(true, true);
  }

  private void validateFetchDeploymentMetadata(boolean withId, boolean withDefaultArtifact) {
    String workflowId1 = WORKFLOW_ID + "_1";
    String workflowId2 = WORKFLOW_ID + "_2";
    String workflowId3 = WORKFLOW_ID + "_3";
    Pipeline pipeline = preparePipeline(prepareStageSimple(), prepareStageSimple(ENV_ID, workflowId1),
        prepareStageSimple(), prepareStageDisabled(), prepareStageApproval(), prepareStageSimple(ENV_ID, workflowId2),
        prepareStageSimple(ENV_ID, workflowId3));
    List<String> artifactNeededServiceIds = new ArrayList<>();
    List<String> envIds = new ArrayList<>();

    Workflow workflow =
        aWorkflow().uuid(WORKFLOW_ID).name("w").orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build();
    Workflow workflow1 =
        aWorkflow().uuid(workflowId1).name("w1").orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build();
    Workflow workflow2 =
        aWorkflow().uuid(workflowId2).name("w2").orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build();
    Workflow workflow3 =
        aWorkflow().uuid(workflowId3).name("w3").orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build();

    Map<String, Workflow> workflowMap = new HashMap<>();
    workflowMap.put(WORKFLOW_ID, workflow);
    workflowMap.put(workflowId1, workflow1);
    workflowMap.put(workflowId2, workflow2);
    workflowMap.put(workflowId3, workflow3);

    when(workflowService.readWorkflowWithoutServices(eq(APP_ID), anyString()))
        .thenAnswer(invocation -> workflowMap.getOrDefault((String) invocation.getArguments()[1], null));

    when(workflowService.readWorkflow(eq(APP_ID), anyString()))
        .thenAnswer(invocation -> workflowMap.getOrDefault((String) invocation.getArguments()[1], null));

    when(workflowService.fetchDeploymentMetadata(any(), any(Workflow.class), any(), any(), any(), anyBoolean(), any(),
             any(DeploymentMetadata.Include[].class)))
        .thenAnswer(invocation -> {
          Workflow argument = (Workflow) invocation.getArguments()[1];
          switch (argument.getName()) {
            case "w":
              return DeploymentMetadata.builder()
                  .artifactRequiredServiceIds(Collections.singletonList("s1"))
                  .artifactVariables(Collections.singletonList(prepareArtifactVariable("artifact", "s1")))
                  .build();
            case "w1":
              return DeploymentMetadata.builder().build();
            case "w2":
              return DeploymentMetadata.builder()
                  .artifactRequiredServiceIds(asList("s1", "s2"))
                  .artifactVariables(asList(prepareArtifactVariable("artifact", "s1"),
                      prepareArtifactVariable("artifact", "s2"), prepareArtifactVariable("artifact_tmp", "s2"), null))
                  .build();
            default:
              return null;
          }
        });

    DeploymentMetadata deploymentMetadata;
    if (withId) {
      when(wingsPersistence.getWithAppId(Pipeline.class, APP_ID, pipeline.getUuid())).thenReturn(pipeline);
      when(workflowService.getResolvedServices(any(), any())).thenReturn(Collections.emptyList());
      when(workflowService.getResolvedInfraMappingIds(any(), any())).thenReturn(Collections.emptyList());
      when(workflowService.getResolvedInfraDefinitionIds(any(), any(), any())).thenReturn(Collections.emptyList());
      when(workflowService.resolveEnvironmentId(any(), any())).thenReturn(null);
      if (withDefaultArtifact) {
        deploymentMetadata = pipelineService.fetchDeploymentMetadata(
            APP_ID, pipeline.getUuid(), null, artifactNeededServiceIds, envIds, false, null);
      } else {
        deploymentMetadata =
            pipelineService.fetchDeploymentMetadata(APP_ID, pipeline.getUuid(), null, artifactNeededServiceIds, envIds);
      }
    } else {
      deploymentMetadata = pipelineService.fetchDeploymentMetadata(APP_ID, pipeline, artifactNeededServiceIds, envIds);
    }

    assertThat(deploymentMetadata).isNotNull();

    List<String> serviceIds = deploymentMetadata.getArtifactRequiredServiceIds();
    assertThat(serviceIds).isNotNull();
    assertThat(serviceIds.size()).isEqualTo(2);
    assertThat(serviceIds).contains("s1", "s2");

    List<ArtifactVariable> artifactVariables = deploymentMetadata.getArtifactVariables();
    assertThat(artifactVariables).isNotNull();
    assertThat(artifactVariables.size()).isEqualTo(3);

    ArtifactVariable artifactVariable1 =
        artifactVariables.stream()
            .filter(artifactVariable
                -> artifactVariable.getName().equals("artifact") && artifactVariable.getEntityId().equals("s1"))
            .findFirst()
            .orElse(null);
    assertThat(artifactVariable1).isNotNull();
    assertThat(artifactVariable1.getWorkflowIds()).isNotNull();
    assertThat(artifactVariable1.getWorkflowIds().size()).isEqualTo(2);
    assertThat(artifactVariable1.getWorkflowIds()).contains(WORKFLOW_ID, workflowId2);

    ArtifactVariable artifactVariable2 =
        artifactVariables.stream()
            .filter(artifactVariable
                -> artifactVariable.getName().equals("artifact") && artifactVariable.getEntityId().equals("s2"))
            .findFirst()
            .orElse(null);
    assertThat(artifactVariable2).isNotNull();
    assertThat(artifactVariable2.getWorkflowIds()).isNotNull();
    assertThat(artifactVariable2.getWorkflowIds().size()).isEqualTo(1);
    assertThat(artifactVariable2.getWorkflowIds()).contains(workflowId2);

    ArtifactVariable artifactVariable3 =
        artifactVariables.stream()
            .filter(artifactVariable -> artifactVariable.getName().equals("artifact_tmp"))
            .findFirst()
            .orElse(null);
    assertThat(artifactVariable3).isNotNull();
    assertThat(artifactVariable3.getWorkflowIds()).isNotNull();
    assertThat(artifactVariable3.getWorkflowIds().size()).isEqualTo(1);
    assertThat(artifactVariable3.getWorkflowIds()).contains(workflowId2);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchDeploymentMetadataForBuildPipeline() {
    String workflowId0 = WORKFLOW_ID + "_0";
    String workflowId1 = WORKFLOW_ID + "_1";
    Pipeline pipeline = preparePipeline(
        prepareStageSimple(ENV_ID, workflowId0), prepareStageSimple(), prepareStageSimple(ENV_ID, workflowId1));

    Workflow workflow0 =
        aWorkflow().uuid(workflowId1).name("w0").orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build();
    when(workflowService.readWorkflowWithoutServices(eq(APP_ID), eq(workflowId0))).thenReturn(workflow0);

    Workflow workflow =
        aWorkflow()
            .uuid(WORKFLOW_ID)
            .name("w")
            .orchestrationWorkflow(
                aCanaryOrchestrationWorkflow().withOrchestrationWorkflowType(OrchestrationWorkflowType.BUILD).build())
            .build();
    when(workflowService.readWorkflowWithoutServices(eq(APP_ID), eq(WORKFLOW_ID))).thenReturn(workflow);

    Workflow workflow1 =
        aWorkflow().uuid(workflowId1).name("w1").orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build();
    when(workflowService.readWorkflowWithoutServices(eq(APP_ID), eq(workflowId1))).thenReturn(workflow1);

    when(workflowService.fetchDeploymentMetadata(any(), any(Workflow.class), anyMap(), any(), any(), any()))
        .thenAnswer(invocation -> {
          Workflow argument = (Workflow) invocation.getArguments()[1];
          switch (argument.getName()) {
            case "w":
            case "w1":
              return DeploymentMetadata.builder().build();
            case "w0":
              return DeploymentMetadata.builder()
                  .artifactRequiredServiceIds(asList("s1", "s2"))
                  .artifactVariables(
                      asList(prepareArtifactVariable("artifact", "s1"), prepareArtifactVariable("artifact", "s2")))
                  .build();
            default:
              return null;
          }
        });

    DeploymentMetadata deploymentMetadata = pipelineService.fetchDeploymentMetadata(APP_ID, pipeline, null, null);
    assertThat(deploymentMetadata).isNotNull();
    assertThat(deploymentMetadata.getArtifactVariables()).isEmpty();
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_getUserGroups() {
    Map<String, Object> properties1 = new HashMap<>();
    properties1.put("userGroups", Arrays.asList("userGroup1", "userGroup2"));
    PipelineStage stage1 = PipelineStage.builder()
                               .pipelineStageElements(Collections.singletonList(PipelineStageElement.builder()
                                                                                    .name("STAGE1")
                                                                                    .type(APPROVAL.name())
                                                                                    .properties(properties1)
                                                                                    .disable(false)
                                                                                    .build()))
                               .build();
    Map<String, Object> properties2 = new HashMap<>();
    properties2.put("userGroups", Arrays.asList("userGroup2", "userGroup3"));
    PipelineStage stage2 = PipelineStage.builder()
                               .pipelineStageElements(Collections.singletonList(PipelineStageElement.builder()
                                                                                    .name("STAGE2")
                                                                                    .type(APPROVAL.name())
                                                                                    .properties(properties2)
                                                                                    .disable(false)
                                                                                    .build()))
                               .build();
    Pipeline pipeline = preparePipeline(stage1, stage2);
    Set<String> actualUserGroups = pipelineService.getUserGroups(pipeline);
    Set<String> expectedUserGroups = new HashSet<>(Arrays.asList("userGroup1", "userGroup2", "userGroup3"));
    assertThat(actualUserGroups.size()).isEqualTo(3);
    assertThat(actualUserGroups).isEqualTo(expectedUserGroups);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void test_getUserGroupsTemplatized() {
    Map<String, Object> properties1 = new HashMap<>();
    properties1.put("workflowId", WORKFLOW_ID);

    PipelineStage stage1 = PipelineStage.builder()
                               .pipelineStageElements(Collections.singletonList(
                                   PipelineStageElement.builder()
                                       .name("STAGE1")
                                       .type(ENV_STATE.name())
                                       .properties(properties1)
                                       .workflowVariables(ImmutableMap.of("User_group", "userGroup1"))
                                       .disable(false)
                                       .build()))
                               .build();
    Map<String, Object> properties2 = new HashMap<>();
    properties2.put("workflowId", WORKFLOW_ID + 2);
    PipelineStage stage2 = PipelineStage.builder()
                               .pipelineStageElements(Collections.singletonList(
                                   PipelineStageElement.builder()
                                       .name("STAGE2")
                                       .type(ENV_STATE.name())
                                       .properties(properties2)
                                       .workflowVariables(ImmutableMap.of("User_group", "userGroup2,userGroup3"))
                                       .disable(false)
                                       .build()))
                               .build();
    Pipeline pipeline = preparePipeline(stage1, stage2);

    Workflow workflow1 =
        aWorkflow()
            .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                       .withUserVariables(Collections.singletonList(
                                           aVariable().name("User_group").entityType(USER_GROUP).build()))
                                       .build())
            .build();
    Workflow workflow2 =
        aWorkflow()
            .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                       .withUserVariables(Collections.singletonList(
                                           aVariable().name("User_group").entityType(USER_GROUP).build()))
                                       .build())
            .build();
    when(workflowService.readWorkflowWithoutServices(APP_ID, WORKFLOW_ID)).thenReturn(workflow1);
    when(workflowService.readWorkflowWithoutServices(APP_ID, WORKFLOW_ID + 2)).thenReturn(workflow2);

    Set<String> actualUserGroups = pipelineService.getUserGroups(pipeline);
    Set<String> expectedUserGroups = new HashSet<>(Arrays.asList("userGroup1", "userGroup2", "userGroup3"));
    assertThat(actualUserGroups.size()).isEqualTo(3);
    assertThat(actualUserGroups).isEqualTo(expectedUserGroups);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_getUserGroupsForRuntimeValues() {
    Map<String, Object> properties1 = new HashMap<>();
    properties1.put("userGroups", Arrays.asList("userGroup1", "userGroup2"));
    PipelineStage stage1 = PipelineStage.builder()
                               .pipelineStageElements(Collections.singletonList(PipelineStageElement.builder()
                                                                                    .name("STAGE1")
                                                                                    .type(APPROVAL.name())
                                                                                    .properties(properties1)
                                                                                    .disable(false)
                                                                                    .build()))
                               .build();
    Map<String, Object> properties2 = new HashMap<>();
    properties2.put("workflowId", WORKFLOW_ID);

    RuntimeInputsConfig runtimeInputsConfig =
        RuntimeInputsConfig.builder().userGroupIds(Arrays.asList("userGroup2", "userGroup3")).build();
    PipelineStage stage2 =
        PipelineStage.builder()
            .pipelineStageElements(Collections.singletonList(PipelineStageElement.builder()
                                                                 .name("STAGE2")
                                                                 .type(ENV_STATE.name())
                                                                 .properties(properties2)
                                                                 .runtimeInputsConfig(runtimeInputsConfig)
                                                                 .disable(false)
                                                                 .build()))
            .build();
    Pipeline pipeline = preparePipeline(stage1, stage2);

    Workflow workflow1 = aWorkflow().orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build();
    when(workflowService.readWorkflowWithoutServices(APP_ID, WORKFLOW_ID)).thenReturn(workflow1);

    Set<String> actualUserGroups = pipelineService.getUserGroups(pipeline);
    Set<String> expectedUserGroups = new HashSet<>(Arrays.asList("userGroup1", "userGroup2", "userGroup3"));
    assertThat(actualUserGroups.size()).isEqualTo(3);
    assertThat(actualUserGroups).isEqualTo(expectedUserGroups);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void test_getUserGroupsApprovalTemplatized() {
    Map<String, Object> properties1 = new HashMap<>();
    properties1.put("templateExpressions",
        Collections.singletonList(aVariable().name("${User_Group2}").entityType(USER_GROUP).build()));
    PipelineStage stage1 = PipelineStage.builder()
                               .pipelineStageElements(Collections.singletonList(PipelineStageElement.builder()
                                                                                    .name("STAGE1")
                                                                                    .type(APPROVAL.name())
                                                                                    .properties(properties1)
                                                                                    .disable(false)
                                                                                    .build()))
                               .build();
    Map<String, Object> properties2 = new HashMap<>();
    properties1.put("templateExpressions",
        Collections.singletonList(aVariable().name("${User_Group2}").entityType(USER_GROUP).build()));
    PipelineStage stage2 = PipelineStage.builder()
                               .pipelineStageElements(Collections.singletonList(PipelineStageElement.builder()
                                                                                    .name("STAGE2")
                                                                                    .type(APPROVAL.name())
                                                                                    .properties(properties2)
                                                                                    .disable(false)
                                                                                    .build()))
                               .build();
    Pipeline pipeline = preparePipeline(stage1, stage2);
    Set<String> actualUserGroups = pipelineService.getUserGroups(pipeline);
    assertThat(actualUserGroups).isEmpty();
  }

  private Pipeline getApprovalStagesPipeline() {
    PipelineStage stage1 = getSimplePipelineStageWithApprovalStep1();
    PipelineStage stage2 = getSimplePipelineStageWithApprovalStep2();
    FailureStrategy failureStrategy =
        FailureStrategy.builder().repairActionCode(RepairActionCode.MANUAL_INTERVENTION).build();
    return Pipeline.builder()
        .name("pipeline1")
        .appId(APP_ID)
        .uuid(PIPELINE_ID)
        .pipelineStages(asList(stage1, stage2))
        .failureStrategies(Collections.singletonList(failureStrategy))
        .build();
  }

  private PipelineStage getSimplePipelineStageWithApprovalStep1() {
    Map<String, Object> properties1 = new HashMap<>();
    properties1.put("userGroups", Arrays.asList("userGroup1", "userGroup2"));
    return PipelineStage.builder()
        .pipelineStageElements(Collections.singletonList(PipelineStageElement.builder()
                                                             .name("STAGE1")
                                                             .type(APPROVAL.name())
                                                             .properties(properties1)
                                                             .disable(false)
                                                             .build()))
        .build();
  }

  private PipelineStage getSimplePipelineStageWithApprovalStep2() {
    Map<String, Object> properties2 = new HashMap<>();
    properties2.put("userGroups", Arrays.asList("userGroup2", "userGroup3"));
    return PipelineStage.builder()
        .pipelineStageElements(Collections.singletonList(PipelineStageElement.builder()
                                                             .name("STAGE2")
                                                             .type(APPROVAL.name())
                                                             .properties(properties2)
                                                             .disable(false)
                                                             .build()))
        .build();
  }

  private ArtifactVariable prepareArtifactVariable(String name, String serviceId) {
    return ArtifactVariable.builder().name(name).entityType(SERVICE).entityId(serviceId).build();
  }

  private Pipeline preparePipeline(PipelineStage... pipelineStages) {
    FailureStrategy failureStrategy =
        FailureStrategy.builder().repairActionCode(RepairActionCode.MANUAL_INTERVENTION).build();
    return Pipeline.builder()
        .name("pipeline")
        .appId(APP_ID)
        .uuid(PIPELINE_ID)
        .pipelineStages(asList(pipelineStages))
        .failureStrategies(Collections.singletonList(failureStrategy))
        .build();
  }

  private PipelineStage prepareStageSimple() {
    return prepareStageSimple(ENV_ID, WORKFLOW_ID);
  }

  private PipelineStage prepareStageSimple(String envId, String workflowId) {
    Map<String, Object> properties = new HashMap<>();
    properties.put("envId", envId);
    properties.put("workflowId", workflowId);
    return PipelineStage.builder()
        .pipelineStageElements(Collections.singletonList(
            PipelineStageElement.builder().name("STAGE1").type(ENV_STATE.name()).properties(properties).build()))
        .build();
  }

  private PipelineStage prepareStageDisableAssertion(String envId, String workflowId) {
    Map<String, Object> properties = new HashMap<>();
    properties.put("envId", envId);
    properties.put("workflowId", workflowId);
    return PipelineStage.builder()
        .pipelineStageElements(Collections.singletonList(PipelineStageElement.builder()
                                                             .name("STAGE1")
                                                             .type(ENV_STATE.name())
                                                             .properties(properties)
                                                             .disableAssertion("true")
                                                             .build()))
        .build();
  }

  private PipelineStage prepareStageApprovalDisableAssertion() {
    return PipelineStage.builder()
        .pipelineStageElements(Collections.singletonList(PipelineStageElement.builder()
                                                             .name("STAGE2")
                                                             .type(APPROVAL.name())
                                                             .disableAssertion("random")
                                                             .properties(new HashMap<>())
                                                             .build()))
        .build();
  }

  private PipelineStage prepareStageDisabled() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("envId", ENV_ID);
    properties.put("workflowId", WORKFLOW_ID);
    return PipelineStage.builder()
        .pipelineStageElements(Collections.singletonList(PipelineStageElement.builder()
                                                             .name("STAGE1")
                                                             .type(ENV_STATE.name())
                                                             .disable(true)
                                                             .properties(properties)
                                                             .build()))
        .build();
  }

  private PipelineStage prepareStageApproval() {
    return PipelineStage.builder()
        .pipelineStageElements(Collections.singletonList(PipelineStageElement.builder()
                                                             .name("STAGE1")
                                                             .type(APPROVAL.name())
                                                             .properties(new HashMap<>())
                                                             .disable(false)
                                                             .build()))
        .build();
  }

  private Pipeline getPipeline(String name, String appId, String pipelineId) {
    return Pipeline.builder().name(name).appId(appId).uuid(pipelineId).build();
  }

  private PipelineStage getPipelineStage(String name, String stageType, boolean valid, boolean parallel) {
    Map<String, Object> properties = new HashMap<>();
    properties.put("workflowId", WORKFLOW_ID);

    PipelineStageElement pipelineStageElement = PipelineStageElement.builder()
                                                    .uuid("stageElementId")
                                                    .name(name)
                                                    .valid(valid)
                                                    .type(stageType)
                                                    .properties(properties)
                                                    .build();

    return PipelineStage.builder()
        .name(name)
        .valid(valid)
        .parallel(parallel)
        .pipelineStageElements(Arrays.asList(pipelineStageElement))
        .build();
  }
}
