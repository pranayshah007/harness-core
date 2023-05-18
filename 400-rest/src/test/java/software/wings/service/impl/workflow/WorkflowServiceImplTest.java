/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.ERSHAD_MOHAMMAD;
import static io.harness.rule.OwnerRule.MOUNIK;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static software.wings.beans.BasicOrchestrationWorkflow.BasicOrchestrationWorkflowBuilder.aBasicOrchestrationWorkflow;
import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.VERIFY_SERVICE;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_MANIFEST_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_SOURCE_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID_ARTIFACTORY;
import static software.wings.utils.WingsTestConstants.BUILD_NO;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID_CHANGED;
import static software.wings.utils.WingsTestConstants.HELM_CHART_ID;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.MANIFEST_ID;
import static software.wings.utils.WingsTestConstants.PHASE_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.UUID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.limits.LimitEnforcementUtils;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.Event;
import software.wings.beans.FailureStrategy;
import software.wings.beans.GraphNode;
import software.wings.beans.HelmChartConfig;
import software.wings.beans.LastDeployedArtifactInformation;
import software.wings.beans.ManifestVariable;
import software.wings.beans.PhaseStep;
import software.wings.beans.PhaseStepType;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.Service;
import software.wings.beans.VariableType;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.appmanifest.LastDeployedHelmChartInformation;
import software.wings.beans.appmanifest.ManifestSummary;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.beans.stats.CloneMetadata;
import software.wings.dl.WingsPersistence;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.sm.PipelineSummary;
import software.wings.sm.StateMachine;
import software.wings.sm.StateMachine.StateMachineBuilder;
import software.wings.sm.StateType;
import software.wings.sm.states.EnvState.EnvStateKeys;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class WorkflowServiceImplTest extends WingsBaseTest {
  public static final String CHART_NAME = "CHART_NAME";
  public static final String VERSION = "VERSION";
  private static final String HTTP = "HTTP";
  @Mock private WingsPersistence wingsPersistence;
  @Mock private ArtifactService artifactService;
  @Mock private HelmChartService helmChartService;
  @Mock private AppService appService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private ApplicationManifestService applicationManifestService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private PipelineService pipelineService;
  @InjectMocks @Inject private WorkflowService workflowService;
  @Mock private WorkflowServiceHelper workflowServiceHelper;
  @Mock private Query<WorkflowExecution> query;
  @Mock private Query<StateMachine> stateMachineQuery;
  @Mock private Query<Workflow> workflowQuery;
  @Mock private Query<WorkflowExecution> emptyQuery;
  @Mock private FieldEnd fieldEnd;
  @Mock UpdateOperations<Workflow> updateOperations;
  @Mock private YamlPushService yamlPushService;

  @Before
  public void setUp() {
    when(featureFlagService.isEnabled(eq(FeatureName.HELM_CHART_AS_ARTIFACT), anyString())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.TIMEOUT_FAILURE_SUPPORT), anyString())).thenReturn(false);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGetLastDeployedArtifactFromPreviousIndirectExecution() {
    Workflow workflow = aWorkflow().name(WORKFLOW_NAME).uuid(WORKFLOW_ID).accountId(ACCOUNT_ID).appId(APP_ID).build();

    List<Artifact> artifacts =
        asList(anArtifact()
                   .withUuid(ARTIFACT_ID)
                   .withArtifactSourceName(ARTIFACT_SOURCE_NAME)
                   .withArtifactStreamId(ARTIFACT_STREAM_ID)
                   .withMetadata(new ArtifactMetadata(Collections.singletonMap(ArtifactMetadataKeys.buildNo, BUILD_NO)))
                   .build());

    List<HelmChart> helmCharts = asList(HelmChart.builder()
                                            .uuid(HELM_CHART_ID)
                                            .serviceId(SERVICE_ID)
                                            .name(CHART_NAME)
                                            .applicationManifestId(MANIFEST_ID)
                                            .version(VERSION)
                                            .build());
    WorkflowExecution workflowExecution =
        WorkflowExecution.builder()
            .artifacts(artifacts)
            .helmCharts(helmCharts)
            .appId(APP_ID)
            .pipelineResumeId(PIPELINE_EXECUTION_ID)
            .pipelineExecutionId(PIPELINE_EXECUTION_ID)
            .pipelineSummary(PipelineSummary.builder().pipelineId(PIPELINE_ID).pipelineName(PIPELINE_NAME).build())
            .build();

    when(wingsPersistence.createQuery(WorkflowExecution.class)).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.accountId, workflow.getAccountId())).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.appId, workflow.getAppId())).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.workflowId, workflow.getUuid())).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.serviceIds, SERVICE_ID)).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.status, SUCCESS)).thenReturn(query);
    when(query.order(any(Sort.class))).thenReturn(query);
    when(query.get()).thenReturn(workflowExecution);
    PageResponse<Artifact> pageResponse = new PageResponse<>();
    pageResponse.setResponse(artifacts);
    when(artifactService.listArtifactsForService(eq(APP_ID), eq(SERVICE_ID), any())).thenReturn(pageResponse);
    WorkflowServiceImpl workflowServiceImpl = (WorkflowServiceImpl) workflowService;
    LastDeployedArtifactInformation artifactInformation = workflowServiceImpl.fetchLastDeployedArtifact(
        workflow, asList(ARTIFACT_STREAM_ID, ARTIFACT_STREAM_ID_ARTIFACTORY), SERVICE_ID);
    assertThat(artifactInformation).isNotNull();
    assertThat(artifactInformation.getArtifact().getArtifactSourceName()).isEqualTo(ARTIFACT_SOURCE_NAME);
    assertThat(artifactInformation.getArtifact().getBuildNo()).isEqualTo(BUILD_NO);
    assertThat(artifactInformation.getExecutionId()).isEqualTo(PIPELINE_EXECUTION_ID);
    assertThat(artifactInformation.getExecutionEntityId()).isEqualTo(PIPELINE_ID);
    assertThat(artifactInformation.getExecutionEntityType()).isEqualTo(WorkflowType.PIPELINE);
    assertThat(artifactInformation.getExecutionEntityName()).isEqualTo(PIPELINE_NAME);

    when(helmChartService.listHelmChartsForService(APP_ID, SERVICE_ID, null, new PageRequest<>(), true))
        .thenReturn(ImmutableMap.of(APP_MANIFEST_NAME, helmCharts));

    LastDeployedHelmChartInformation helmChartInformation =
        workflowServiceImpl.fetchLastDeployedHelmChart(workflow, SERVICE_ID);
    assertThat(helmChartInformation).isNotNull();
    assertThat(helmChartInformation.getHelmchart().getName()).isEqualTo(CHART_NAME);
    assertThat(helmChartInformation.getHelmchart().getVersion()).isEqualTo(VERSION);
    assertThat(helmChartInformation.getExecutionId()).isEqualTo(PIPELINE_EXECUTION_ID);
    assertThat(helmChartInformation.getExecutionEntityId()).isEqualTo(PIPELINE_ID);
    assertThat(helmChartInformation.getExecutionEntityType()).isEqualTo(WorkflowType.PIPELINE);
    assertThat(helmChartInformation.getExecutionEntityName()).isEqualTo(PIPELINE_NAME);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldReturnNullIfNoSuccessfulExecutionFound() {
    Workflow workflow = aWorkflow().name(WORKFLOW_NAME).uuid(WORKFLOW_ID).accountId(ACCOUNT_ID).appId(APP_ID).build();

    when(wingsPersistence.createQuery(WorkflowExecution.class)).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.accountId, workflow.getAccountId())).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.appId, workflow.getAppId())).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.workflowId, workflow.getUuid())).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.serviceIds, SERVICE_ID)).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.status, SUCCESS)).thenReturn(query);
    when(query.order(any(Sort.class))).thenReturn(query);
    when(query.get()).thenReturn(null);
    WorkflowServiceImpl workflowServiceImpl = (WorkflowServiceImpl) workflowService;
    LastDeployedArtifactInformation artifactInformation = workflowServiceImpl.fetchLastDeployedArtifact(
        workflow, asList(ARTIFACT_STREAM_ID, ARTIFACT_STREAM_ID_ARTIFACTORY), SERVICE_ID);
    assertThat(artifactInformation).isNull();

    LastDeployedHelmChartInformation helmChartInformation =
        workflowServiceImpl.fetchLastDeployedHelmChart(workflow, SERVICE_ID);
    assertThat(helmChartInformation).isNull();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldReturnNullIfArtifactStreamOrAppManifestChanged() {
    Workflow workflow = aWorkflow().name(WORKFLOW_NAME).uuid(WORKFLOW_ID).accountId(ACCOUNT_ID).appId(APP_ID).build();

    WorkflowExecution workflowExecution =
        WorkflowExecution.builder()
            .artifacts(asList(anArtifact()
                                  .withArtifactSourceName(ARTIFACT_SOURCE_NAME)
                                  .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                  .withMetadata(new ArtifactMetadata(
                                      Collections.singletonMap(ArtifactMetadataKeys.buildNo, BUILD_NO)))
                                  .build()))
            .status(ExecutionStatus.SUCCESS)
            .pipelineExecutionId(PIPELINE_EXECUTION_ID)
            .pipelineSummary(PipelineSummary.builder().pipelineId(PIPELINE_ID).pipelineName(PIPELINE_NAME).build())
            .build();

    when(wingsPersistence.createQuery(WorkflowExecution.class)).thenReturn(query);
    when(query.filter(any(), any())).thenReturn(query);
    when(query.field(any())).thenReturn(fieldEnd);
    when(fieldEnd.contains(any())).thenReturn(query);
    when(query.order(any(Sort.class))).thenReturn(query);
    when(query.get()).thenReturn(workflowExecution);
    WorkflowServiceImpl workflowServiceImpl = (WorkflowServiceImpl) workflowService;
    LastDeployedArtifactInformation artifactInformation =
        workflowServiceImpl.fetchLastDeployedArtifact(workflow, asList(ARTIFACT_STREAM_ID_ARTIFACTORY), SERVICE_ID);
    assertThat(artifactInformation).isNull();

    LastDeployedHelmChartInformation helmChartInformation =
        workflowServiceImpl.fetchLastDeployedHelmChart(workflow, SERVICE_ID);
    assertThat(helmChartInformation).isNull();
  }

  private PhaseStep getSinglePhaseWithSingleStep(String phaseName, String stepName) {
    GraphNode graphnode = GraphNode.builder().name(stepName).build();
    List<GraphNode> graphNodes = asList(graphnode);
    PhaseStep phaseStep = aPhaseStep(VERIFY_SERVICE).addAllSteps(graphNodes).build();
    phaseStep.setName(phaseName);
    return phaseStep;
  }

  private WorkflowPhase getWorkflowWithPhaseStep(PhaseStep phaseStep) {
    List<PhaseStep> phaseSteps = asList(phaseStep);
    WorkflowPhase workflowPhase = aWorkflowPhase().phaseSteps(phaseSteps).build();
    return workflowPhase;
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void checkStepNames() {
    WorkflowServiceImpl workflowServiceImpl = (WorkflowServiceImpl) workflowService;
    final PhaseStep phaseStepWithInvalidStep = getSinglePhaseWithSingleStep("Phase 1", "Step.1");
    assertThatThrownBy(() -> workflowServiceImpl.checkPhaseStepAndStepNames(phaseStepWithInvalidStep))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Step name should not contain dots");
    final WorkflowPhase workflowPhaseWithInvalidStep = getWorkflowWithPhaseStep(phaseStepWithInvalidStep);
    assertThatThrownBy(() -> workflowService.updatePostDeployment(APP_ID, WORKFLOW_ID, phaseStepWithInvalidStep))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Step name should not contain dots");
    assertThatThrownBy(() -> workflowService.updatePreDeployment(APP_ID, WORKFLOW_ID, phaseStepWithInvalidStep))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Step name should not contain dots");
    assertThatThrownBy(() -> workflowService.updateWorkflowPhase(APP_ID, WORKFLOW_ID, workflowPhaseWithInvalidStep))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Step name should not contain dots");
    assertThatThrownBy(
        () -> workflowService.updateWorkflowPhaseRollback(APP_ID, WORKFLOW_ID, PHASE_ID, workflowPhaseWithInvalidStep))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Step name should not contain dots");
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void checkPhaseStepNamesWithValidSteps() {
    final PhaseStep phaseStepWithInvalidName = getSinglePhaseWithSingleStep("Phase.1", "Step 1");
    WorkflowServiceImpl workflowServiceImpl = (WorkflowServiceImpl) workflowService;
    assertThatThrownBy(() -> workflowServiceImpl.checkPhaseStepAndStepNames(phaseStepWithInvalidName))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Phase Step name should not contain dots");
    final WorkflowPhase workflowPhaseWithInvalidPhaseStep = getWorkflowWithPhaseStep(phaseStepWithInvalidName);
    assertThatThrownBy(() -> workflowServiceImpl.checkWorkflowForStepNames(workflowPhaseWithInvalidPhaseStep))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Phase Step name should not contain dots");
    assertThatThrownBy(() -> workflowService.updatePostDeployment(APP_ID, WORKFLOW_ID, phaseStepWithInvalidName))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Phase Step name should not contain dots");
    assertThatThrownBy(() -> workflowService.updatePreDeployment(APP_ID, WORKFLOW_ID, phaseStepWithInvalidName))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Phase Step name should not contain dots");
    assertThatThrownBy(
        () -> workflowService.updateWorkflowPhase(APP_ID, WORKFLOW_ID, workflowPhaseWithInvalidPhaseStep))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Phase Step name should not contain dots");
    assertThatThrownBy(()
                           -> workflowService.updateWorkflowPhaseRollback(
                               APP_ID, WORKFLOW_ID, PHASE_ID, workflowPhaseWithInvalidPhaseStep))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Phase Step name should not contain dots");
  }
  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void checkPhaseStepNamesWithInvalidSteps() {
    final PhaseStep phaseStepWithInvalidName = getSinglePhaseWithSingleStep("Phase.1", "Step.1");
    WorkflowServiceImpl workflowServiceImpl = (WorkflowServiceImpl) workflowService;
    assertThatThrownBy(() -> workflowServiceImpl.checkPhaseStepAndStepNames(phaseStepWithInvalidName))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Phase Step name should not contain dots");
    final WorkflowPhase workflowPhaseWithInvalidPhaseStep = getWorkflowWithPhaseStep(phaseStepWithInvalidName);
    assertThatThrownBy(() -> workflowServiceImpl.checkWorkflowForStepNames(workflowPhaseWithInvalidPhaseStep))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Phase Step name should not contain dots");
    assertThatThrownBy(() -> workflowService.updatePostDeployment(APP_ID, WORKFLOW_ID, phaseStepWithInvalidName))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Phase Step name should not contain dots");
    assertThatThrownBy(() -> workflowService.updatePreDeployment(APP_ID, WORKFLOW_ID, phaseStepWithInvalidName))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Phase Step name should not contain dots");
    assertThatThrownBy(
        () -> workflowService.updateWorkflowPhase(APP_ID, WORKFLOW_ID, workflowPhaseWithInvalidPhaseStep))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Phase Step name should not contain dots");
    assertThatThrownBy(()
                           -> workflowService.updateWorkflowPhaseRollback(
                               APP_ID, WORKFLOW_ID, PHASE_ID, workflowPhaseWithInvalidPhaseStep))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Phase Step name should not contain dots");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldReturnNullIfArtifactOrManifestModifiedInStream() {
    Workflow workflow = aWorkflow().name(WORKFLOW_NAME).uuid(WORKFLOW_ID).accountId(ACCOUNT_ID).appId(APP_ID).build();

    List<Artifact> artifacts =
        asList(anArtifact()
                   .withUuid(ARTIFACT_ID)
                   .withArtifactSourceName(ARTIFACT_SOURCE_NAME)
                   .withArtifactStreamId(ARTIFACT_STREAM_ID)
                   .withMetadata(new ArtifactMetadata(Collections.singletonMap(ArtifactMetadataKeys.buildNo, BUILD_NO)))
                   .build());
    List<HelmChart> helmCharts = asList(HelmChart.builder()
                                            .uuid(HELM_CHART_ID)
                                            .serviceId(SERVICE_ID)
                                            .name(CHART_NAME)
                                            .applicationManifestId(MANIFEST_ID)
                                            .version(VERSION)
                                            .build());
    WorkflowExecution workflowExecution =
        WorkflowExecution.builder()
            .artifacts(artifacts)
            .helmCharts(helmCharts)
            .appId(APP_ID)
            .pipelineResumeId(PIPELINE_EXECUTION_ID)
            .pipelineExecutionId(PIPELINE_EXECUTION_ID)
            .pipelineSummary(PipelineSummary.builder().pipelineId(PIPELINE_ID).pipelineName(PIPELINE_NAME).build())
            .build();

    when(wingsPersistence.createQuery(WorkflowExecution.class)).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.accountId, workflow.getAccountId())).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.appId, workflow.getAppId())).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.workflowId, workflow.getUuid())).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.serviceIds, SERVICE_ID)).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.status, SUCCESS)).thenReturn(query);
    when(query.order(any(Sort.class))).thenReturn(query);
    when(query.get()).thenReturn(workflowExecution);
    PageResponse<Artifact> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(
        anArtifact()
            .withUuid(ARTIFACT_ID + 2)
            .withArtifactSourceName(ARTIFACT_SOURCE_NAME)
            .withArtifactStreamId(ARTIFACT_STREAM_ID)
            .withMetadata(new ArtifactMetadata(Collections.singletonMap(ArtifactMetadataKeys.buildNo, BUILD_NO + 2)))
            .build()));
    when(artifactService.listArtifactsForService(eq(APP_ID), eq(SERVICE_ID), any())).thenReturn(pageResponse);
    when(helmChartService.listHelmChartsForService(APP_ID, SERVICE_ID, null, new PageRequest<>(), true))
        .thenReturn(ImmutableMap.of(APP_MANIFEST_NAME,
            asList(HelmChart.builder()
                       .uuid(HELM_CHART_ID + 2)
                       .serviceId(SERVICE_ID)
                       .name(CHART_NAME)
                       .applicationManifestId(MANIFEST_ID)
                       .version(VERSION + 2)
                       .build())));

    WorkflowServiceImpl workflowServiceImpl = (WorkflowServiceImpl) workflowService;
    LastDeployedArtifactInformation artifactInformation = workflowServiceImpl.fetchLastDeployedArtifact(
        workflow, asList(ARTIFACT_STREAM_ID, ARTIFACT_STREAM_ID_ARTIFACTORY), SERVICE_ID);
    assertThat(artifactInformation).isNull();

    LastDeployedHelmChartInformation helmChartInformation =
        workflowServiceImpl.fetchLastDeployedHelmChart(workflow, SERVICE_ID);
    assertThat(helmChartInformation).isNull();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateManifestVariableForHttpShellScript() {
    GraphNode shellScript = GraphNode.builder()
                                .type(StateType.SHELL_SCRIPT.name())
                                .name("printHelm")
                                .properties(ImmutableMap.<String, Object>builder()
                                                .put("scriptType", "BASH")
                                                .put("scriptString", "echo ${helmChart.name}")
                                                .put("executeOnDelegate", "true")
                                                .put("timeout", 120000)
                                                .build())
                                .build();

    Workflow workflow = createWorkflowWithPhaseStep(shellScript);

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(StoreType.HelmChartRepo).pollForChanges(true).build();
    when(applicationManifestService.getManifestByServiceId(APP_ID, SERVICE_ID)).thenReturn(applicationManifest);

    when(serviceResourceService.get(APP_ID, SERVICE_ID))
        .thenReturn(Service.builder().name(SERVICE_NAME).artifactFromManifest(true).build());

    DeploymentMetadata deploymentMetadata =
        workflowService.fetchDeploymentMetadata(APP_ID, workflow, Collections.EMPTY_MAP, Collections.EMPTY_LIST,
            Collections.EMPTY_LIST, false, null, DeploymentMetadata.Include.ARTIFACT_SERVICE);

    assertThat(deploymentMetadata.getManifestVariables()).isNotNull();
    assertThat(deploymentMetadata.getManifestVariables()).hasSize(1);
    ManifestVariable variable = deploymentMetadata.getManifestVariables().get(0);

    assertThat(variable.getType()).isEqualTo(VariableType.MANIFEST);
    assertThat(variable.getName()).isEqualTo("helmChart");

    GraphNode httpStep = GraphNode.builder()
                             .type(StateType.HTTP.name())
                             .name("accessHelmRepo")
                             .properties(ImmutableMap.<String, Object>builder()
                                             .put("url", "${helmChart.url}/list")
                                             .put("timeout", 120000)
                                             .build())
                             .build();

    workflow = aWorkflow()
                   .name(WORKFLOW_NAME)
                   .appId(APP_ID)
                   .workflowType(WorkflowType.ORCHESTRATION)
                   .orchestrationWorkflow(aBasicOrchestrationWorkflow()
                                              .withRollbackWorkflowPhaseIdMap(Collections.singletonMap(PHASE_ID,
                                                  aWorkflowPhase()
                                                      .serviceId(SERVICE_ID)
                                                      .phaseSteps(asList(aPhaseStep(PhaseStepType.COLLECT_ARTIFACT,
                                                          PhaseStepType.COLLECT_ARTIFACT.toString())
                                                                             .addStep(httpStep)
                                                                             .build()))
                                                      .build()))
                                              .build())
                   .templatized(true)
                   .build();

    deploymentMetadata = workflowService.fetchDeploymentMetadata(APP_ID, workflow, Collections.EMPTY_MAP,
        Collections.EMPTY_LIST, Collections.EMPTY_LIST, false, null, DeploymentMetadata.Include.ARTIFACT_SERVICE);

    assertThat(deploymentMetadata.getManifestVariables()).isNotNull();
    assertThat(deploymentMetadata.getManifestVariables()).hasSize(1);
    variable = deploymentMetadata.getManifestVariables().get(0);

    assertThat(variable.getType()).isEqualTo(VariableType.MANIFEST);
    assertThat(variable.getName()).isEqualTo("helmChart");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateManifestVariableForK8sHelmDeploy() {
    GraphNode helmDeploy = GraphNode.builder().type(StateType.HELM_DEPLOY.name()).name("helmDeploy").build();

    Workflow workflow = createWorkflowWithPhaseStep(helmDeploy);

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(StoreType.HelmChartRepo).pollForChanges(true).build();
    when(applicationManifestService.getManifestByServiceId(APP_ID, SERVICE_ID)).thenReturn(applicationManifest);

    when(serviceResourceService.get(APP_ID, SERVICE_ID))
        .thenReturn(Service.builder().name(SERVICE_NAME).artifactFromManifest(true).build());

    DeploymentMetadata deploymentMetadata =
        workflowService.fetchDeploymentMetadata(APP_ID, workflow, Collections.EMPTY_MAP, Collections.EMPTY_LIST,
            Collections.EMPTY_LIST, false, null, DeploymentMetadata.Include.ARTIFACT_SERVICE);

    assertThat(deploymentMetadata.getManifestVariables()).isNotNull();
    assertThat(deploymentMetadata.getManifestVariables()).hasSize(1);
    ManifestVariable variable = deploymentMetadata.getManifestVariables().get(0);

    assertThat(variable.getType()).isEqualTo(VariableType.MANIFEST);
    assertThat(variable.getName()).isEqualTo("helmChart");

    GraphNode k8sDeploy = GraphNode.builder().type(StateType.K8S_CANARY_DEPLOY.name()).name("K8sDeploy").build();

    workflow = aWorkflow()
                   .name(WORKFLOW_NAME)
                   .appId(APP_ID)
                   .workflowType(WorkflowType.ORCHESTRATION)
                   .orchestrationWorkflow(aBasicOrchestrationWorkflow()
                                              .withRollbackWorkflowPhaseIdMap(Collections.singletonMap(PHASE_ID,
                                                  aWorkflowPhase()
                                                      .serviceId(SERVICE_ID)
                                                      .phaseSteps(asList(aPhaseStep(PhaseStepType.COLLECT_ARTIFACT,
                                                          PhaseStepType.COLLECT_ARTIFACT.toString())
                                                                             .addStep(k8sDeploy)
                                                                             .build()))
                                                      .build()))
                                              .build())
                   .templatized(true)
                   .build();

    deploymentMetadata = workflowService.fetchDeploymentMetadata(APP_ID, workflow, Collections.EMPTY_MAP,
        Collections.EMPTY_LIST, Collections.EMPTY_LIST, false, null, DeploymentMetadata.Include.ARTIFACT_SERVICE);

    assertThat(deploymentMetadata.getManifestVariables()).isNotNull();
    assertThat(deploymentMetadata.getManifestVariables()).hasSize(1);
    variable = deploymentMetadata.getManifestVariables().get(0);

    assertThat(variable.getType()).isEqualTo(VariableType.MANIFEST);
    assertThat(variable.getName()).isEqualTo("helmChart");
  }

  @NotNull
  private Workflow createWorkflowWithPhaseStep(GraphNode helmDeploy) {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(
            aBasicOrchestrationWorkflow()
                .withWorkflowPhases(asList(aWorkflowPhase()
                                               .serviceId(SERVICE_ID)
                                               .phaseSteps(asList(aPhaseStep(PhaseStepType.COLLECT_ARTIFACT,
                                                   PhaseStepType.COLLECT_ARTIFACT.toString())
                                                                      .addStep(helmDeploy)
                                                                      .build()))
                                               .build()))
                .build())
        .templatized(true)
        .build();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateAppManifestSummaryAndServiceName() {
    GraphNode k8sDeploy = GraphNode.builder().type(StateType.K8S_BLUE_GREEN_DEPLOY.name()).name("K8sDeploy").build();

    Workflow workflow = createWorkflowWithPhaseStep(k8sDeploy);

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder()
            .storeType(StoreType.HelmChartRepo)
            .pollForChanges(true)
            .helmChartConfig(HelmChartConfig.builder().connectorId(SETTING_ID).build())
            .build();
    applicationManifest.setUuid(MANIFEST_ID);
    when(applicationManifestService.getManifestByServiceId(APP_ID, SERVICE_ID)).thenReturn(applicationManifest);
    when(applicationManifestService.getManifestsByServiceId(APP_ID, SERVICE_ID, AppManifestKind.K8S_MANIFEST))
        .thenReturn(Collections.singletonList(applicationManifest));
    when(serviceResourceService.get(APP_ID, SERVICE_ID))
        .thenReturn(Service.builder().name(SERVICE_NAME).artifactFromManifest(true).build());
    when(helmChartService.getLastCollectedManifest(ACCOUNT_ID, MANIFEST_ID))
        .thenReturn(HelmChart.builder().uuid(HELM_CHART_ID).name("chart").version("1").displayName("chart-1").build());

    DeploymentMetadata deploymentMetadata =
        workflowService.fetchDeploymentMetadata(APP_ID, workflow, Collections.EMPTY_MAP, Collections.EMPTY_LIST,
            Collections.EMPTY_LIST, false, null, DeploymentMetadata.Include.ARTIFACT_SERVICE);

    assertThat(deploymentMetadata.getManifestVariables()).hasSize(1);
    ManifestVariable manifestVariable = deploymentMetadata.getManifestVariables().get(0);
    ManifestSummary manifestSummary =
        manifestVariable.getApplicationManifestSummary().get(0).getLastCollectedManifest();
    assertThat(manifestSummary.getUuid()).isEqualTo(HELM_CHART_ID);
    assertThat(manifestSummary.getVersionNo()).isEqualTo("1");
    assertThat(manifestVariable.getServiceName()).isEqualTo(SERVICE_NAME);
    assertThat(manifestVariable.getApplicationManifestSummary().get(0).getDefaultManifest()).isNull();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateDefaultVersionForRerun() {
    String appManifestName = "appManifest123";
    GraphNode k8sDeploy = GraphNode.builder().type(StateType.K8S_APPLY.name()).name("K8sDeploy").build();

    Workflow workflow = createWorkflowWithPhaseStep(k8sDeploy);

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder()
            .name(appManifestName)
            .storeType(StoreType.HelmChartRepo)
            .pollForChanges(true)
            .helmChartConfig(HelmChartConfig.builder().connectorId(SETTING_ID).build())
            .build();
    applicationManifest.setUuid(MANIFEST_ID);
    when(applicationManifestService.getManifestByServiceId(APP_ID, SERVICE_ID)).thenReturn(applicationManifest);
    when(applicationManifestService.getManifestsByServiceId(APP_ID, SERVICE_ID, AppManifestKind.K8S_MANIFEST))
        .thenReturn(Collections.singletonList(applicationManifest));
    when(serviceResourceService.get(APP_ID, SERVICE_ID))
        .thenReturn(Service.builder().name(SERVICE_NAME).artifactFromManifest(true).build());
    when(helmChartService.getLastCollectedManifest(ACCOUNT_ID, MANIFEST_ID))
        .thenReturn(HelmChart.builder().uuid(HELM_CHART_ID).name("chart").version("1").displayName("chart-1").build());

    List<HelmChart> helmCharts = asList(HelmChart.builder()
                                            .uuid(HELM_CHART_ID + 1)
                                            .serviceId(SERVICE_ID)
                                            .name(CHART_NAME)
                                            .applicationManifestId(MANIFEST_ID)
                                            .version(VERSION + 1)
                                            .build());
    WorkflowExecution workflowExecution =
        WorkflowExecution.builder()
            .helmCharts(helmCharts)
            .appId(APP_ID)
            .pipelineResumeId(PIPELINE_EXECUTION_ID)
            .pipelineExecutionId(PIPELINE_EXECUTION_ID)
            .pipelineSummary(PipelineSummary.builder().pipelineId(PIPELINE_ID).pipelineName(PIPELINE_NAME).build())
            .build();

    DeploymentMetadata deploymentMetadata =
        workflowService.fetchDeploymentMetadata(APP_ID, workflow, Collections.EMPTY_MAP, Collections.EMPTY_LIST,
            Collections.EMPTY_LIST, false, workflowExecution, DeploymentMetadata.Include.ARTIFACT_SERVICE);

    assertThat(deploymentMetadata.getManifestVariables()).hasSize(1);
    ManifestVariable manifestVariable = deploymentMetadata.getManifestVariables().get(0);
    assertThat(manifestVariable.getApplicationManifestSummary().get(0).getAppManifestName()).isEqualTo(appManifestName);
    ManifestSummary manifestSummary =
        manifestVariable.getApplicationManifestSummary().get(0).getLastCollectedManifest();
    assertThat(manifestSummary.getUuid()).isEqualTo(HELM_CHART_ID);
    assertThat(manifestSummary.getVersionNo()).isEqualTo("1");
    assertThat(manifestVariable.getServiceName()).isEqualTo(SERVICE_NAME);
    manifestSummary = manifestVariable.getApplicationManifestSummary().get(0).getDefaultManifest();
    assertThat(manifestSummary.getUuid()).isEqualTo(HELM_CHART_ID + 1);
    assertThat(manifestSummary.getVersionNo()).isEqualTo(VERSION + 1);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGetLastDeployedArtifactFromDifferentServices() {
    Workflow workflow = aWorkflow().name(WORKFLOW_NAME).uuid(WORKFLOW_ID).accountId(ACCOUNT_ID).appId(APP_ID).build();

    List<Artifact> artifacts =
        asList(anArtifact()
                   .withUuid(ARTIFACT_ID)
                   .withArtifactSourceName(ARTIFACT_SOURCE_NAME)
                   .withArtifactStreamId(ARTIFACT_STREAM_ID)
                   .withMetadata(new ArtifactMetadata(Collections.singletonMap(ArtifactMetadataKeys.buildNo, BUILD_NO)))
                   .build());

    List<HelmChart> helmCharts = asList(HelmChart.builder()
                                            .uuid(HELM_CHART_ID)
                                            .serviceId(SERVICE_ID)
                                            .name(CHART_NAME)
                                            .applicationManifestId(MANIFEST_ID)
                                            .version(VERSION)
                                            .build());
    WorkflowExecution workflowExecution =
        WorkflowExecution.builder()
            .artifacts(artifacts)
            .helmCharts(helmCharts)
            .appId(APP_ID)
            .pipelineResumeId(PIPELINE_EXECUTION_ID)
            .pipelineExecutionId(PIPELINE_EXECUTION_ID)
            .pipelineSummary(PipelineSummary.builder().pipelineId(PIPELINE_ID).pipelineName(PIPELINE_NAME).build())
            .build();

    when(wingsPersistence.createQuery(WorkflowExecution.class)).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.accountId, workflow.getAccountId())).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.appId, workflow.getAppId())).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.workflowId, workflow.getUuid())).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.serviceIds, SERVICE_ID)).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.status, SUCCESS)).thenReturn(query);
    when(query.order(any(Sort.class))).thenReturn(query);
    when(query.get()).thenReturn(workflowExecution);
    PageResponse<Artifact> pageResponse = new PageResponse<>();
    pageResponse.setResponse(artifacts);
    when(artifactService.listArtifactsForService(eq(APP_ID), eq(SERVICE_ID), any())).thenReturn(pageResponse);
    WorkflowServiceImpl workflowServiceImpl = (WorkflowServiceImpl) workflowService;
    LastDeployedArtifactInformation artifactInformation = workflowServiceImpl.fetchLastDeployedArtifact(
        workflow, asList(ARTIFACT_STREAM_ID, ARTIFACT_STREAM_ID_ARTIFACTORY), SERVICE_ID);
    assertThat(artifactInformation).isNotNull();
    assertThat(artifactInformation.getArtifact().getArtifactSourceName()).isEqualTo(ARTIFACT_SOURCE_NAME);
    assertThat(artifactInformation.getArtifact().getBuildNo()).isEqualTo(BUILD_NO);
    assertThat(artifactInformation.getExecutionId()).isEqualTo(PIPELINE_EXECUTION_ID);
    assertThat(artifactInformation.getExecutionEntityId()).isEqualTo(PIPELINE_ID);
    assertThat(artifactInformation.getExecutionEntityType()).isEqualTo(WorkflowType.PIPELINE);
    assertThat(artifactInformation.getExecutionEntityName()).isEqualTo(PIPELINE_NAME);

    when(helmChartService.listHelmChartsForService(APP_ID, SERVICE_ID, null, new PageRequest<>(), true))
        .thenReturn(ImmutableMap.of(APP_MANIFEST_NAME, helmCharts));

    LastDeployedHelmChartInformation helmChartInformation =
        workflowServiceImpl.fetchLastDeployedHelmChart(workflow, SERVICE_ID);
    assertThat(helmChartInformation).isNotNull();
    assertThat(helmChartInformation.getHelmchart().getName()).isEqualTo(CHART_NAME);
    assertThat(helmChartInformation.getHelmchart().getVersion()).isEqualTo(VERSION);
    assertThat(helmChartInformation.getExecutionId()).isEqualTo(PIPELINE_EXECUTION_ID);
    assertThat(helmChartInformation.getExecutionEntityId()).isEqualTo(PIPELINE_ID);
    assertThat(helmChartInformation.getExecutionEntityType()).isEqualTo(WorkflowType.PIPELINE);
    assertThat(helmChartInformation.getExecutionEntityName()).isEqualTo(PIPELINE_NAME);

    when(fieldEnd.contains(SERVICE_ID + 2)).thenReturn(emptyQuery);
    when(emptyQuery.filter(WorkflowExecutionKeys.status, SUCCESS)).thenReturn(emptyQuery);
    when(emptyQuery.order(any(Sort.class))).thenReturn(emptyQuery);
    when(query.get()).thenReturn(null);

    artifactInformation = workflowServiceImpl.fetchLastDeployedArtifact(
        workflow, asList(ARTIFACT_STREAM_ID, ARTIFACT_STREAM_ID_ARTIFACTORY), SERVICE_ID);
    assertThat(artifactInformation).isNull();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldUpdatePipelinesWhenEnvIsChanged() {
    Workflow oldWorkflow = aWorkflow().name(WORKFLOW_NAME).appId(APP_ID).envId(ENV_ID).uuid(WORKFLOW_ID).build();
    Workflow newWorkflow =
        aWorkflow().name(WORKFLOW_NAME).appId(APP_ID).envId(ENV_ID_CHANGED).uuid(WORKFLOW_ID).build();
    when(wingsPersistence.getWithAppId(any(), anyString(), anyString())).thenReturn(oldWorkflow);

    HashMap<String, Object> properties = new HashMap<>();
    properties.put(EnvStateKeys.workflowId, WORKFLOW_ID);
    properties.put(EnvStateKeys.envId, ENV_ID);
    Pipeline pipeline =
        Pipeline.builder()
            .appId(APP_ID)
            .pipelineStages(
                asList(PipelineStage.builder()
                           .pipelineStageElements(asList(PipelineStageElement.builder().properties(properties).build()))
                           .build(),
                    PipelineStage.builder()
                        .pipelineStageElements(asList(PipelineStageElement.builder()
                                                          .properties(ImmutableMap.of(EnvStateKeys.workflowId,
                                                              WORKFLOW_ID + 2, EnvStateKeys.envId, ENV_ID))
                                                          .build()))
                        .build()))
            .build();
    Pipeline pipeline2 =
        Pipeline.builder()
            .appId(APP_ID)
            .pipelineStages(
                asList(PipelineStage.builder()
                           .pipelineStageElements(asList(PipelineStageElement.builder()
                                                             .properties(ImmutableMap.of(EnvStateKeys.workflowId,
                                                                 WORKFLOW_ID + 2, EnvStateKeys.envId, ENV_ID))
                                                             .build()))
                           .build()))
            .build();

    when(pipelineService.listPipelines(any()))
        .thenReturn(aPageResponse().withResponse(asList(pipeline, pipeline2)).build());
    when(wingsPersistence.createQuery(StateMachine.class)).thenReturn(stateMachineQuery);
    when(wingsPersistence.createQuery(Workflow.class)).thenReturn(workflowQuery);
    when(workflowQuery.filter(anyString(), any())).thenReturn(workflowQuery);
    when(wingsPersistence.createUpdateOperations(Workflow.class)).thenReturn(updateOperations);
    when(updateOperations.set(any(), any())).thenReturn(updateOperations);
    when(stateMachineQuery.filter(anyString(), any())).thenReturn(stateMachineQuery);
    when(stateMachineQuery.get()).thenReturn(StateMachineBuilder.aStateMachine().build());

    when(wingsPersistence.update(any(Query.class), any(UpdateOperations.class))).thenReturn(null);
    workflowService.updateWorkflow(newWorkflow, null, false, false, false);
    ArgumentCaptor<List> pipelineArgumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(pipelineService).savePipelines(pipelineArgumentCaptor.capture(), eq(true));
    assertThat(pipelineArgumentCaptor.getValue()).hasSize(2);
    pipeline = (Pipeline) pipelineArgumentCaptor.getValue().get(0);
    pipeline2 = (Pipeline) pipelineArgumentCaptor.getValue().get(1);
    assertThat(
        pipeline.getPipelineStages().get(0).getPipelineStageElements().get(0).getProperties().get(EnvStateKeys.envId))
        .isEqualTo(ENV_ID_CHANGED);
    assertThat(
        pipeline.getPipelineStages().get(1).getPipelineStageElements().get(0).getProperties().get(EnvStateKeys.envId))
        .isEqualTo(ENV_ID);
    assertThat(
        pipeline2.getPipelineStages().get(0).getPipelineStageElements().get(0).getProperties().get(EnvStateKeys.envId))
        .isEqualTo(ENV_ID);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldDoNothingForNoLinkedPipelinesWhenEnvIsChanged() {
    Workflow oldWorkflow = aWorkflow().name(WORKFLOW_NAME).appId(APP_ID).envId(ENV_ID).uuid(WORKFLOW_ID).build();
    Workflow newWorkflow =
        aWorkflow().name(WORKFLOW_NAME).appId(APP_ID).envId(ENV_ID_CHANGED).uuid(WORKFLOW_ID).build();
    when(wingsPersistence.getWithAppId(any(), anyString(), anyString())).thenReturn(oldWorkflow);

    when(pipelineService.listPipelines(any())).thenReturn(aPageResponse().withResponse(null).build());
    when(wingsPersistence.createQuery(StateMachine.class)).thenReturn(stateMachineQuery);
    when(wingsPersistence.createQuery(Workflow.class)).thenReturn(workflowQuery);
    when(workflowQuery.filter(anyString(), any())).thenReturn(workflowQuery);
    when(wingsPersistence.createUpdateOperations(Workflow.class)).thenReturn(updateOperations);
    when(updateOperations.set(any(), any())).thenReturn(updateOperations);
    when(stateMachineQuery.filter(anyString(), any())).thenReturn(stateMachineQuery);
    when(stateMachineQuery.get()).thenReturn(StateMachineBuilder.aStateMachine().build());

    when(wingsPersistence.update(any(Query.class), any(UpdateOperations.class))).thenReturn(null);
    workflowService.updateWorkflow(newWorkflow, null, false, false, false);
    verify(pipelineService, Mockito.never()).savePipelines(any(), eq(true));
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenNoSpecificStepsAreProvidedForTimeoutErrorFailureType() {
    when(featureFlagService.isEnabled(FeatureName.TIMEOUT_FAILURE_SUPPORT, ACCOUNT_ID)).thenReturn(true);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    PhaseStep phaseStep =
        aPhaseStep(PhaseStepType.PRE_DEPLOYMENT)
            .addStep(GraphNode.builder().type(HTTP).name(HTTP).build())
            .withFailureStrategies(Collections.singletonList(
                FailureStrategy.builder().failureTypes(Collections.singletonList(FailureType.TIMEOUT_ERROR)).build()))
            .build();
    WorkflowPhase phase = aWorkflowPhase().phaseSteps(Collections.singletonList(phaseStep)).build();
    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        aCanaryOrchestrationWorkflow().addWorkflowPhase(phase).build();

    assertThatThrownBy(()
                           -> workflowService.updateWorkflow(aWorkflow().appId(APP_ID).name(WORKFLOW_NAME).build(),
                               canaryOrchestrationWorkflow, false, false, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Specify the steps for timeout error. Allowed step types are:");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenNonSupportedStepsAreProvidedForTimeoutErrorFailureType() {
    when(featureFlagService.isEnabled(FeatureName.TIMEOUT_FAILURE_SUPPORT, ACCOUNT_ID)).thenReturn(true);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    PhaseStep phaseStep = aPhaseStep(PhaseStepType.PRE_DEPLOYMENT)
                              .addStep(GraphNode.builder().type("JIRA_CREATE_UPDATE").name("Jira").build())
                              .withFailureStrategies(Collections.singletonList(
                                  FailureStrategy.builder()
                                      .failureTypes(Collections.singletonList(FailureType.TIMEOUT_ERROR))
                                      .specificSteps(Collections.singletonList("Jira"))
                                      .build()))
                              .build();
    WorkflowPhase phase = aWorkflowPhase().phaseSteps(Collections.singletonList(phaseStep)).build();
    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        aCanaryOrchestrationWorkflow().addWorkflowPhase(phase).build();

    assertThatThrownBy(()
                           -> workflowService.updateWorkflow(aWorkflow().appId(APP_ID).name(WORKFLOW_NAME).build(),
                               canaryOrchestrationWorkflow, false, false, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Timeout error is allowed only for step types:");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenTimeoutErrorFailureTypeIsProvidedOnOrchestrationLevel() {
    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        aCanaryOrchestrationWorkflow()
            .withFailureStrategies(Collections.singletonList(
                FailureStrategy.builder().failureTypes(Collections.singletonList(FailureType.TIMEOUT_ERROR)).build()))
            .build();

    assertThatThrownBy(()
                           -> workflowService.updateWorkflow(aWorkflow().appId(APP_ID).name(WORKFLOW_NAME).build(),
                               canaryOrchestrationWorkflow, false, false, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Timeout error is not supported on orchestration level.");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenTimeoutErrorFailureTypeIsProvidedWhenFeatureFlagIsDisabled() {
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    PhaseStep phaseStep = aPhaseStep(PhaseStepType.PRE_DEPLOYMENT)
                              .addStep(GraphNode.builder().type(HTTP).name(HTTP).build())
                              .withFailureStrategies(Collections.singletonList(
                                  FailureStrategy.builder()
                                      .failureTypes(Collections.singletonList(FailureType.TIMEOUT_ERROR))
                                      .specificSteps(Collections.singletonList(HTTP))
                                      .build()))
                              .build();
    WorkflowPhase phase = aWorkflowPhase().phaseSteps(Collections.singletonList(phaseStep)).build();
    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        aCanaryOrchestrationWorkflow().addWorkflowPhase(phase).build();

    assertThatThrownBy(()
                           -> workflowService.updateWorkflow(aWorkflow().appId(APP_ID).name(WORKFLOW_NAME).build(),
                               canaryOrchestrationWorkflow, false, false, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Timeout error is not supported");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenNonSupportedStepsAreProvidedForTimeoutErrorFailureTypeOnRollbackPhasesLevel() {
    when(featureFlagService.isEnabled(FeatureName.TIMEOUT_FAILURE_SUPPORT, ACCOUNT_ID)).thenReturn(true);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    PhaseStep phaseStep = aPhaseStep(PhaseStepType.K8S_PHASE_STEP)
                              .addStep(GraphNode.builder().type("JIRA_CREATE_UPDATE").name("Jira").build())
                              .withFailureStrategies(Collections.singletonList(
                                  FailureStrategy.builder()
                                      .failureTypes(Collections.singletonList(FailureType.TIMEOUT_ERROR))
                                      .specificSteps(Collections.singletonList("Jira"))
                                      .build()))
                              .build();
    WorkflowPhase phase = aWorkflowPhase().phaseSteps(Collections.singletonList(phaseStep)).build();
    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        aCanaryOrchestrationWorkflow().withRollbackWorkflowPhaseIdMap(Collections.singletonMap(UUID, phase)).build();

    assertThatThrownBy(()
                           -> workflowService.updateWorkflow(aWorkflow().appId(APP_ID).name(WORKFLOW_NAME).build(),
                               canaryOrchestrationWorkflow, false, false, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Timeout error is allowed only for step types:");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenNoStepsAreProvidedForTimeoutErrorFailureTypeOnRollbackPhasesLevel() {
    when(featureFlagService.isEnabled(FeatureName.TIMEOUT_FAILURE_SUPPORT, ACCOUNT_ID)).thenReturn(true);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    PhaseStep phaseStep =
        aPhaseStep(PhaseStepType.K8S_PHASE_STEP)
            .addStep(GraphNode.builder().type("JIRA_CREATE_UPDATE").name("Jira").build())
            .withFailureStrategies(Collections.singletonList(
                FailureStrategy.builder().failureTypes(Collections.singletonList(FailureType.TIMEOUT_ERROR)).build()))
            .build();
    WorkflowPhase phase = aWorkflowPhase().phaseSteps(Collections.singletonList(phaseStep)).build();
    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        aCanaryOrchestrationWorkflow().withRollbackWorkflowPhaseIdMap(Collections.singletonMap(UUID, phase)).build();

    assertThatThrownBy(()
                           -> workflowService.updateWorkflow(aWorkflow().appId(APP_ID).name(WORKFLOW_NAME).build(),
                               canaryOrchestrationWorkflow, false, false, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Specify the steps for timeout error. Allowed step types are:");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateMultipleAppManifestSummaryAndServiceName() {
    GraphNode k8sDeploy = GraphNode.builder().type(StateType.K8S_BLUE_GREEN_DEPLOY.name()).name("K8sDeploy").build();

    Workflow workflow = createWorkflowWithPhaseStep(k8sDeploy);

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder()
            .storeType(StoreType.HelmChartRepo)
            .pollForChanges(true)
            .helmChartConfig(HelmChartConfig.builder().connectorId(SETTING_ID).build())
            .build();
    ApplicationManifest applicationManifest2 =
        ApplicationManifest.builder()
            .storeType(StoreType.HelmChartRepo)
            .pollForChanges(true)
            .helmChartConfig(HelmChartConfig.builder().connectorId(SETTING_ID).build())
            .build();
    applicationManifest.setUuid(MANIFEST_ID);
    applicationManifest2.setUuid(MANIFEST_ID + 2);
    when(applicationManifestService.getManifestByServiceId(APP_ID, SERVICE_ID)).thenReturn(applicationManifest);
    when(applicationManifestService.getManifestsByServiceId(APP_ID, SERVICE_ID, AppManifestKind.K8S_MANIFEST))
        .thenReturn(asList(applicationManifest, applicationManifest2));
    when(serviceResourceService.get(APP_ID, SERVICE_ID))
        .thenReturn(Service.builder().name(SERVICE_NAME).artifactFromManifest(true).build());
    when(helmChartService.getLastCollectedManifest(ACCOUNT_ID, MANIFEST_ID))
        .thenReturn(HelmChart.builder().uuid(HELM_CHART_ID).name("chart").version("1").displayName("chart-1").build());

    DeploymentMetadata deploymentMetadata =
        workflowService.fetchDeploymentMetadata(APP_ID, workflow, Collections.EMPTY_MAP, Collections.EMPTY_LIST,
            Collections.EMPTY_LIST, false, null, DeploymentMetadata.Include.ARTIFACT_SERVICE);

    assertThat(deploymentMetadata.getManifestVariables()).hasSize(1);
    ManifestVariable manifestVariable = deploymentMetadata.getManifestVariables().get(0);
    assertThat(manifestVariable.getApplicationManifestSummary()).hasSize(2);
    ManifestSummary manifestSummary =
        manifestVariable.getApplicationManifestSummary().get(0).getLastCollectedManifest();
    assertThat(manifestSummary.getUuid()).isEqualTo(HELM_CHART_ID);
    assertThat(manifestSummary.getVersionNo()).isEqualTo("1");
    assertThat(manifestVariable.getServiceName()).isEqualTo(SERVICE_NAME);
    assertThat(manifestVariable.getApplicationManifestSummary().get(0).getDefaultManifest()).isNull();
    assertThat(manifestVariable.getApplicationManifestSummary().get(1).getAppManifestId()).isEqualTo(MANIFEST_ID + 2);
    assertThat(manifestVariable.getApplicationManifestSummary().get(0).getAppManifestId()).isEqualTo(MANIFEST_ID);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void shouldGenerateDefaultTerragruntRollbackProvisioner() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("provisionerId", "some_provisioner_id");

    PhaseStep phaseStep = aPhaseStep(PRE_DEPLOYMENT)
                              .addAllSteps(Collections.singletonList(GraphNode.builder()
                                                                         .name("Terragrunt provision step")
                                                                         .type("TERRAGRUNT_PROVISION")
                                                                         .properties(properties)
                                                                         .build()))
                              .build();
    phaseStep.setName("Pre-deployment-step");

    PhaseStep rollbackStep = workflowService.generateRollbackProvisioners(
        phaseStep, PhaseStepType.ROLLBACK_PROVISIONERS, "Rollback provisioners");

    assertThat(rollbackStep).isNotNull();
    assertThat(rollbackStep.getName()).isEqualTo("Rollback provisioners");
    assertThat(rollbackStep.isRollback()).isTrue();
    assertThat(rollbackStep.getSteps()).isNotNull();
    assertThat(rollbackStep.getSteps().size()).isEqualTo(1);

    GraphNode defaultRollbackStep = rollbackStep.getSteps().get(0);
    assertThat(defaultRollbackStep.isRollback()).isTrue();
    assertThat(defaultRollbackStep.getType()).isEqualTo("TERRAGRUNT_ROLLBACK");
    assertThat(defaultRollbackStep.getName()).isEqualTo("Rollback Terragrunt provision step");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void shouldNotGenerateTerragruntRollbackProvisionerWhenSkipDefaultRollbackIsTrue() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("provisionerId", "some_provisioner_id");
    properties.put("skipRollback", true);

    PhaseStep phaseStep = aPhaseStep(PRE_DEPLOYMENT)
                              .addAllSteps(Collections.singletonList(GraphNode.builder()
                                                                         .name("Terragrunt provision step")
                                                                         .type("TERRAGRUNT_PROVISION")
                                                                         .properties(properties)
                                                                         .build()))
                              .build();
    phaseStep.setName("Pre-deployment-step");

    PhaseStep rollbackStep = workflowService.generateRollbackProvisioners(
        phaseStep, PhaseStepType.ROLLBACK_PROVISIONERS, "Rollback provisioners");

    assertThat(rollbackStep).isNotNull();
    assertThat(rollbackStep.getName()).isEqualTo("Rollback provisioners");
    assertThat(rollbackStep.isRollback()).isTrue();
    assertThat(rollbackStep.getSteps()).isNotNull();
    assertThat(rollbackStep.getSteps().isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void shouldGenerateTerragruntRollbackProvisionerWhenSkipDefaultRollbackIsTrueInPlanAndNotSetInInheret() {
    Map<String, Object> properties1 = new HashMap<>();
    properties1.put("provisionerId", "some_provisioner_id");
    properties1.put("runPlanOnly", true);

    Map<String, Object> properties2 = new HashMap<>();
    properties2.put("provisionerId", "some_provisioner_id");
    properties2.put("inheritApprovedPlan", true);

    PhaseStep phaseStep = aPhaseStep(PRE_DEPLOYMENT)
                              .addAllSteps(asList(GraphNode.builder()
                                                      .name("Terragrunt provision plan step")
                                                      .type("TERRAGRUNT_PROVISION")
                                                      .properties(properties1)
                                                      .build(),
                                  GraphNode.builder()
                                      .name("Terragrunt provision apply step")
                                      .type("TERRAGRUNT_PROVISION")
                                      .properties(properties2)
                                      .build()))
                              .build();
    phaseStep.setName("Pre-deployment-step");

    PhaseStep rollbackStep = workflowService.generateRollbackProvisioners(
        phaseStep, PhaseStepType.ROLLBACK_PROVISIONERS, "Rollback provisioners");

    assertThat(rollbackStep).isNotNull();
    assertThat(rollbackStep.getName()).isEqualTo("Rollback provisioners");
    assertThat(rollbackStep.isRollback()).isTrue();
    assertThat(rollbackStep.getSteps()).isNotNull();
    assertThat(rollbackStep.getSteps().size()).isEqualTo(1);

    GraphNode defaultRollbackStep = rollbackStep.getSteps().get(0);
    assertThat(defaultRollbackStep.isRollback()).isTrue();
    assertThat(defaultRollbackStep.getType()).isEqualTo("TERRAGRUNT_ROLLBACK");
    assertThat(defaultRollbackStep.getName()).isEqualTo("Rollback Terragrunt provision apply step");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void shouldNotGenerateTerragruntRollbackProvisionerWhenSkipDefaultRollbackIsTrueInPlanAndNotSetInInheret() {
    Map<String, Object> properties1 = new HashMap<>();
    properties1.put("provisionerId", "some_provisioner_id");
    properties1.put("runPlanOnly", true);
    properties1.put("skipRollback", true);

    Map<String, Object> properties2 = new HashMap<>();
    properties2.put("provisionerId", "some_provisioner_id");
    properties2.put("inheritApprovedPlan", true);

    PhaseStep phaseStep = aPhaseStep(PRE_DEPLOYMENT)
                              .addAllSteps(asList(GraphNode.builder()
                                                      .name("Terragrunt provision plan step")
                                                      .type("TERRAGRUNT_PROVISION")
                                                      .properties(properties1)
                                                      .build(),
                                  GraphNode.builder()
                                      .name("Terragrunt provision apply step")
                                      .type("TERRAGRUNT_PROVISION")
                                      .properties(properties2)
                                      .build()))
                              .build();
    phaseStep.setName("Pre-deployment-step");

    PhaseStep rollbackStep = workflowService.generateRollbackProvisioners(
        phaseStep, PhaseStepType.ROLLBACK_PROVISIONERS, "Rollback provisioners");

    assertThat(rollbackStep).isNotNull();
    assertThat(rollbackStep.getName()).isEqualTo("Rollback provisioners");
    assertThat(rollbackStep.isRollback()).isTrue();
    assertThat(rollbackStep.getSteps()).isNotNull();
    assertThat(rollbackStep.getSteps().isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = ERSHAD_MOHAMMAD)
  @Category(UnitTests.class)
  public void cloneWorkflowShouldHaveEmptyServiceIdsForPhasesWhenClonedToDifferentApp() {
    WorkflowServiceImpl workflowServiceImpl = (WorkflowServiceImpl) workflowService;
    WorkflowPhase workflowPhase =
        aWorkflowPhase().uuid(PHASE_ID).serviceId(SERVICE_ID).infraDefinitionId(INFRA_DEFINITION_ID).build();
    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        aCanaryOrchestrationWorkflow().addWorkflowPhase(workflowPhase).build();
    Workflow oldWorkflow = aWorkflow()
                               .name(WORKFLOW_NAME)
                               .uuid(WORKFLOW_ID)
                               .accountId(ACCOUNT_ID)
                               .appId(APP_ID)
                               .orchestrationWorkflow(canaryOrchestrationWorkflow)
                               .build();
    Workflow newWorkflow = aWorkflow().name("Cloned").uuid(WORKFLOW_ID).accountId(ACCOUNT_ID).appId("NewApp").build();

    Map<String, String> serviceMappings = new HashMap<>();

    CloneMetadata cloneMetadata =
        CloneMetadata.builder().workflow(newWorkflow).serviceMapping(serviceMappings).targetAppId("NewApp").build();
    doNothing().when(workflowServiceHelper).validateServiceMapping(any(), any(), any());
    mockStatic(LimitEnforcementUtils.class)
        .when(() -> LimitEnforcementUtils.withLimitCheck(any(), any()))
        .thenReturn(newWorkflow);
    when(wingsPersistence.createUpdateOperations(Workflow.class)).thenReturn(updateOperations);
    when(wingsPersistence.getWithAppId(any(), anyString(), anyString())).thenReturn(newWorkflow);
    when(wingsPersistence.createQuery(StateMachine.class)).thenReturn(stateMachineQuery);
    when(stateMachineQuery.filter(anyString(), any())).thenReturn(stateMachineQuery);
    when(wingsPersistence.createQuery(Workflow.class)).thenReturn(workflowQuery);
    when(workflowQuery.filter(anyString(), any())).thenReturn(workflowQuery);
    when(wingsPersistence.createUpdateOperations(Workflow.class)).thenReturn(updateOperations);
    when(updateOperations.set(anyString(), any())).thenReturn(updateOperations);
    Workflow updatedWorkFLow = workflowServiceImpl.cloneWorkflow(APP_ID, oldWorkflow, cloneMetadata);

    CanaryOrchestrationWorkflow canaryWorkflow =
        (CanaryOrchestrationWorkflow) updatedWorkFLow.getOrchestrationWorkflow();
    canaryWorkflow.getWorkflowPhases().forEach(updatedWorkflowPhase -> {
      assertThat(updatedWorkflowPhase.getInfraDefinitionId()).isNull();
      assertThat(updatedWorkflowPhase.getServiceId()).isNull();
    });
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_clonedWorkflowActionType() {
    WorkflowServiceImpl workflowServiceImpl = (WorkflowServiceImpl) workflowService;
    WorkflowPhase workflowPhase =
        aWorkflowPhase().uuid(PHASE_ID).serviceId(SERVICE_ID).infraDefinitionId(INFRA_DEFINITION_ID).build();
    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        aCanaryOrchestrationWorkflow().addWorkflowPhase(workflowPhase).build();
    Workflow oldWorkflow = aWorkflow()
                               .name(WORKFLOW_NAME)
                               .uuid(WORKFLOW_ID)
                               .accountId(ACCOUNT_ID)
                               .appId(APP_ID)
                               .orchestrationWorkflow(canaryOrchestrationWorkflow)
                               .build();
    Workflow newWorkflow = aWorkflow().name("Cloned").uuid(WORKFLOW_ID).accountId(ACCOUNT_ID).appId(APP_ID).build();
    Map<String, String> serviceMappings = new HashMap<>();
    CloneMetadata cloneMetadata =
        CloneMetadata.builder().workflow(newWorkflow).serviceMapping(serviceMappings).targetAppId("NewApp").build();
    doNothing().when(workflowServiceHelper).validateServiceMapping(any(), any(), any());
    mockStatic(LimitEnforcementUtils.class)
        .when(() -> LimitEnforcementUtils.withLimitCheck(any(), any()))
        .thenReturn(newWorkflow);

    when(wingsPersistence.createUpdateOperations(Workflow.class)).thenReturn(updateOperations);
    when(wingsPersistence.getWithAppId(any(), anyString(), anyString())).thenReturn(newWorkflow);
    when(wingsPersistence.createQuery(StateMachine.class)).thenReturn(stateMachineQuery);
    when(stateMachineQuery.filter(anyString(), any())).thenReturn(stateMachineQuery);
    when(wingsPersistence.createQuery(Workflow.class)).thenReturn(workflowQuery);
    when(workflowQuery.filter(anyString(), any())).thenReturn(workflowQuery);
    when(wingsPersistence.createUpdateOperations(Workflow.class)).thenReturn(updateOperations);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(updateOperations.set(anyString(), any())).thenReturn(updateOperations);

    Workflow updatedWorkFLow = workflowServiceImpl.cloneWorkflow(APP_ID, oldWorkflow, cloneMetadata);

    assertThat(updatedWorkFLow).isNotNull();
    ArgumentCaptor<Event.Type> typeArgumentCaptor1 = ArgumentCaptor.forClass(Event.Type.class);
    verify(yamlPushService, times(1))
        .pushYamlChangeSet(eq(ACCOUNT_ID), any(), any(), typeArgumentCaptor1.capture(), eq(false), eq(false));
    assertThat(typeArgumentCaptor1.getValue()).isEqualTo(Event.Type.CREATE);
    ArgumentCaptor<Event.Type> typeArgumentCaptor2 = ArgumentCaptor.forClass(Event.Type.class);

    Workflow updateWorkFLow1 = workflowServiceImpl.updateWorkflow(oldWorkflow, canaryOrchestrationWorkflow, false);

    assertThat(updateWorkFLow1).isNotNull();
    verify(yamlPushService, times(1))
        .pushYamlChangeSet(eq(ACCOUNT_ID), any(), any(), typeArgumentCaptor2.capture(), eq(false), eq(true));
    assertThat(typeArgumentCaptor2.getValue()).isEqualTo(Event.Type.UPDATE);
  }
}
