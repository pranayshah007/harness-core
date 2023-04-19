/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.FeatureName.GITHUB_WEBHOOK_AUTHENTICATION;
import static io.harness.beans.FeatureName.SERVICE_ID_FILTER_FOR_TRIGGERS;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.beans.WorkflowType.ORCHESTRATION;
import static io.harness.beans.WorkflowType.PIPELINE;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.ATHARVA;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.MOUNIK;
import static io.harness.rule.OwnerRule.POOJA;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.api.CloudProviderType.AWS;
import static software.wings.api.DeploymentType.SSH;
import static software.wings.beans.AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping;
import static software.wings.beans.BuildWorkflow.BuildOrchestrationWorkflowBuilder.aBuildOrchestrationWorkflow;
import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.EntityType.INFRASTRUCTURE_DEFINITION;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.Variable.ENTITY_TYPE;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.trigger.ArtifactSelection.Type.ARTIFACT_SOURCE;
import static software.wings.beans.trigger.ArtifactSelection.Type.LAST_COLLECTED;
import static software.wings.beans.trigger.ArtifactSelection.Type.LAST_DEPLOYED;
import static software.wings.beans.trigger.ArtifactSelection.Type.PIPELINE_SOURCE;
import static software.wings.beans.trigger.ArtifactSelection.Type.WEBHOOK_VARIABLE;
import static software.wings.beans.trigger.WebHookTriggerCondition.WEBHOOK_SECRET;
import static software.wings.beans.trigger.WebhookEventType.PULL_REQUEST;
import static software.wings.beans.trigger.WebhookEventType.PUSH;
import static software.wings.beans.trigger.WebhookSource.BITBUCKET;
import static software.wings.beans.trigger.WebhookSource.GITHUB;
import static software.wings.beans.trigger.WebhookSource.GITLAB;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.persistence.artifact.ArtifactFile.Builder.anArtifactFile;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.artifact;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildArtifactTrigger;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildArtifactTriggerWithArtifactSelections;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildJenkinsArtifactStream;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildNewArtifactTrigger;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildNewInstanceTrigger;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildNewManifestTrigger;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildNexusArtifactStream;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildPipeline;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildPipelineCondTrigger;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildPipelineWebhookTriggerWithFileContentChanged;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildScheduledCondTrigger;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildWebhookCondTrigger;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildWorkflow;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildWorkflowArtifactTrigger;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildWorkflowScheduledCondTrigger;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildWorkflowWebhookTrigger;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildWorkflowWebhookTriggerWithFileContentChanged;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.setPipelineStages;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_MANIFEST_NAME;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_FILTER;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_SOURCE_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.BUILD_NO;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.FILE_ID;
import static software.wings.utils.WingsTestConstants.FILE_NAME;
import static software.wings.utils.WingsTestConstants.FREEZE_WINDOW_ID;
import static software.wings.utils.WingsTestConstants.HELM_CHART_ID;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.INFRA_NAME;
import static software.wings.utils.WingsTestConstants.MANIFEST_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID_CHANGED;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.TRIGGER_ID;
import static software.wings.utils.WingsTestConstants.TRIGGER_NAME;
import static software.wings.utils.WingsTestConstants.UUID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedDataParent;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.distribution.idempotence.IdempotentLock;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.DeploymentFreezeException;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.GitConfig;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.TemplateExpression;
import software.wings.beans.Variable;
import software.wings.beans.VariableType;
import software.wings.beans.WebHookToken;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.appmanifest.ManifestSummary;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.JenkinsArtifactStream;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.beans.deployment.DeploymentMetadata.Include;
import software.wings.beans.instance.dashboard.ArtifactSummary;
import software.wings.beans.trigger.ArtifactSelection;
import software.wings.beans.trigger.ArtifactTriggerCondition;
import software.wings.beans.trigger.ManifestSelection;
import software.wings.beans.trigger.ManifestSelection.ManifestSelectionType;
import software.wings.beans.trigger.ManifestTriggerCondition;
import software.wings.beans.trigger.NewInstanceTriggerCondition;
import software.wings.beans.trigger.PipelineTriggerCondition;
import software.wings.beans.trigger.ScheduledTriggerCondition;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.TriggerExecution;
import software.wings.beans.trigger.WebHookTriggerCondition;
import software.wings.beans.trigger.WebhookEventType;
import software.wings.beans.trigger.WebhookParameters;
import software.wings.beans.trigger.WebhookSource;
import software.wings.common.MongoIdempotentRegistry;
import software.wings.dl.WingsPersistence;
import software.wings.infra.AwsInstanceInfrastructure;
import software.wings.infra.GoogleKubernetesEngine;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.scheduler.BackgroundJobScheduler;
import software.wings.scheduler.ScheduledTriggerJob;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.deployment.checks.DeploymentFreezeUtils;
import software.wings.service.impl.trigger.TriggerServiceImpl.TriggerIdempotentResult;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactCollectionService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.HarnessTagService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;
import software.wings.service.intfc.trigger.TriggerExecutionService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.settings.SettingVariableTypes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.quartz.JobDetail;
import org.quartz.TriggerKey;

@OwnedBy(CDC)
@TargetModule(HarnessModule._815_CG_TRIGGERS)
public class TriggerServiceTest extends WingsBaseTest {
  private static final String CATALOG_SERVICE_NAME = "Catalog";
  private static final String ARTIFACT_STREAM_ID_1 = "ARTIFACT_STREAM_ID_1";
  private static final String BUILD_NUMBER = "123";
  private static final String SECRET_ID = "secret_id";

  @Mock private BackgroundJobScheduler jobScheduler;
  @Mock private PipelineService pipelineService;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private WebhookTriggerProcessor webhookTriggerProcessor;
  @Mock private ArtifactService artifactService;
  @Mock private ArtifactCollectionService artifactCollectionServiceAsync;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private WorkflowService workflowService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private InfrastructureDefinitionService infrastructureDefinitionService;
  @Mock private MongoIdempotentRegistry<TriggerIdempotentResult> idempotentRegistry;
  @Mock private EnvironmentService environmentService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private AppService appService;
  @Mock private HarnessTagService harnessTagService;
  @Mock private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Mock private YamlPushService yamlPushService;
  @Mock private AuditServiceHelper auditServiceHelper;
  @Mock private SettingsService settingsService;
  @Mock private TriggerAuthHandler triggerAuthHandler;
  @Mock private TriggerExecutionService triggerExecutionService;
  @Mock private ApplicationManifestService applicationManifestService;
  @Mock private HelmChartService helmChartService;
  @Mock private DeploymentFreezeUtils deploymentFreezeUtils;
  @Mock private ArtifactCollectionService syncArtifactCollectionServiceImpl;

  @Inject @InjectMocks TriggerServiceHelper triggerServiceHelper;
  @Inject @InjectMocks private TriggerServiceImpl triggerService;
  @Inject private WingsPersistence wingsPersistence;

  Trigger webhookConditionTrigger = buildWebhookCondTrigger();

  Trigger pipelineWebhookConditionTriggerWithFileContentChanged =
      buildPipelineWebhookTriggerWithFileContentChanged("testRepo", "master", UUID, PUSH, "index.yaml");

  Trigger artifactConditionTrigger = buildArtifactTrigger();

  Trigger newManifestConditionTrigger = buildNewManifestTrigger();

  Trigger artifactConditionTriggerWithArtifactSelections = buildArtifactTriggerWithArtifactSelections();

  Trigger workflowArtifactConditionTrigger = buildWorkflowArtifactTrigger();

  Trigger pipelineCondTrigger = buildPipelineCondTrigger();

  Trigger scheduledConditionTrigger = buildScheduledCondTrigger();

  Trigger workflowScheduledConditionTrigger = buildWorkflowScheduledCondTrigger();

  Trigger workflowWebhookConditionTrigger = buildWorkflowWebhookTrigger();

  Trigger workflowWebhookConditionTriggerWithFileContentChanged =
      buildWorkflowWebhookTriggerWithFileContentChanged("testRepo", "master", UUID, PUSH, "index.yaml");

  Trigger newInstanceTrigger = buildNewInstanceTrigger();

  Pipeline pipeline = buildPipeline();

  Workflow workflow = buildWorkflow();

  JenkinsArtifactStream artifactStream = buildJenkinsArtifactStream();

  GitConfig gitConfig = GitConfig.builder().urlType(GitConfig.UrlType.ACCOUNT).build();

  @Before
  public void setUp() {
    Reflect.on(triggerServiceHelper).set("artifactCollectionService", syncArtifactCollectionServiceImpl);
    Pipeline pipeline = buildPipeline();
    pipeline.setServices(Lists.newArrayList(Service.builder().uuid(SERVICE_ID).name(CATALOG_SERVICE_NAME).build()));
    when(pipelineService.readPipelineWithResolvedVariables(eq(APP_ID), eq(PIPELINE_ID), any())).thenReturn(pipeline);
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(pipeline);
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, false)).thenReturn(pipeline);
    when(pipelineService.pipelineExists(any(), any())).thenReturn(true);
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(buildWorkflow());
    when(workflowService.workflowExists(any(), any())).thenReturn(true);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(buildJenkinsArtifactStream());
    when(serviceResourceService.get(APP_ID, SERVICE_ID, false))
        .thenReturn(Service.builder().uuid(SERVICE_ID).name(CATALOG_SERVICE_NAME).build());
    when(idempotentRegistry.create(any(), any(), any(), any()))
        .thenReturn(IdempotentLock.<TriggerIdempotentResult>builder()
                        .registry(idempotentRegistry)
                        .resultData(Optional.empty())
                        .build());
    when(artifactService.getArtifactByBuildNumber(artifactStream, ARTIFACT_FILTER, false)).thenReturn(artifact);
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(artifactStreamServiceBindingService.getService(APP_ID, ARTIFACT_STREAM_ID, true))
        .thenReturn(Service.builder().uuid(SERVICE_ID).name(CATALOG_SERVICE_NAME).build());
    when(artifactStreamServiceBindingService.getService(APP_ID, ARTIFACT_STREAM_ID, false))
        .thenReturn(Service.builder().uuid(SERVICE_ID).name(CATALOG_SERVICE_NAME).build());
    doNothing().when(harnessTagService).pruneTagLinks(any(), any());

    doNothing().when(auditServiceHelper).reportForAuditingUsingAccountId(any(), any(), any(), any());
    doNothing().when(auditServiceHelper).reportDeleteForAuditingUsingAccountId(any(), any());
    doNothing().when(auditServiceHelper).reportDeleteForAuditing(any(), any());
    doNothing().when(auditServiceHelper).reportForAuditingUsingAppId(any(), any(), any(), any());

    when(settingsService.fetchGitConfigFromConnectorId(any())).thenReturn(gitConfig);
    when(featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, ACCOUNT_ID)).thenReturn(true);
    when(featureFlagService.isEnabled(SERVICE_ID_FILTER_FOR_TRIGGERS, ACCOUNT_ID)).thenReturn(true);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldListTriggers() {
    Trigger trigger = triggerService.save(artifactConditionTrigger);
    assertThat(trigger).isNotNull();
    PageRequest<Trigger> pageRequest = new PageRequest<>();
    PageResponse<Trigger> triggers = triggerService.list(pageRequest, false, null);
    assertThat(triggers.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGet() {
    Trigger trigger = triggerService.save(webhookConditionTrigger);
    assertThat(trigger).isNotNull();

    Trigger savedTrigger = triggerService.get(APP_ID, trigger.getUuid());
    assertThat(savedTrigger.getUuid()).isEqualTo(trigger.getUuid());
    assertThat(savedTrigger.getName()).isEqualTo(TRIGGER_NAME);
    assertThat(savedTrigger.getAppId()).isEqualTo(APP_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetExcludeHostsWithSameArtifact() {
    webhookConditionTrigger.setExcludeHostsWithSameArtifact(true);
    Trigger trigger = triggerService.save(webhookConditionTrigger);
    assertThat(trigger).isNotNull();

    Trigger savedTrigger = triggerService.get(APP_ID, trigger.getUuid());
    assertThat(savedTrigger.getUuid()).isEqualTo(trigger.getUuid());
    assertThat(savedTrigger.getName()).isEqualTo(TRIGGER_NAME);
    assertThat(savedTrigger.getAppId()).isEqualTo(APP_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldSaveArtifactConditionTrigger() {
    Trigger trigger = triggerService.save(artifactConditionTrigger);

    assertThat(trigger.getUuid()).isNotEmpty();
    assertThat(trigger.getCondition()).isInstanceOf(ArtifactTriggerCondition.class);
    assertThat(((ArtifactTriggerCondition) trigger.getCondition()).getArtifactFilter())
        .isNotNull()
        .isEqualTo(ARTIFACT_FILTER);
    assertThat(((ArtifactTriggerCondition) trigger.getCondition()).getArtifactStreamId())
        .isNotNull()
        .isEqualTo(ARTIFACT_STREAM_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldSaveWorkflowArtifactWConditionTrigger() {
    Trigger trigger = triggerService.save(workflowArtifactConditionTrigger);
    assertThat(trigger.getUuid()).isNotEmpty();
    assertThat(trigger.getWorkflowId()).isEqualTo(WORKFLOW_ID);
    assertThat(trigger.getWorkflowType()).isEqualTo(ORCHESTRATION);
    assertThat(trigger.getCondition()).isInstanceOf(ArtifactTriggerCondition.class);
    assertThat(((ArtifactTriggerCondition) trigger.getCondition()).getArtifactFilter())
        .isNotNull()
        .isEqualTo(ARTIFACT_FILTER);
    assertThat(((ArtifactTriggerCondition) trigger.getCondition()).getArtifactStreamId())
        .isNotNull()
        .isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(((ArtifactTriggerCondition) trigger.getCondition()).isRegex()).isFalse();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateArtifactConditionTrigger() {
    Trigger trigger = triggerService.save(artifactConditionTrigger);
    assertThat(trigger).isNotNull();
    assertThat(trigger.getUuid()).isNotEmpty();

    Trigger savedTrigger = triggerService.get(trigger.getAppId(), trigger.getUuid());
    assertThat(savedTrigger).isNotNull();
    assertThat(savedTrigger.getCondition()).isInstanceOf(ArtifactTriggerCondition.class);

    ArtifactTriggerCondition artifactTriggerCondition = (ArtifactTriggerCondition) savedTrigger.getCondition();
    artifactTriggerCondition.setRegex(true);

    savedTrigger.setArtifactSelections(
        asList(ArtifactSelection.builder().serviceId(SERVICE_ID).type(ARTIFACT_SOURCE).build(),
            ArtifactSelection.builder()
                .type(LAST_COLLECTED)
                .serviceId(SERVICE_ID)
                .artifactStreamId(ARTIFACT_STREAM_ID)
                .artifactFilter(ARTIFACT_FILTER)
                .build()));

    Trigger updatedTrigger = triggerService.update(savedTrigger, false);

    assertThat(updatedTrigger.getUuid()).isNotEmpty();
    assertThat(updatedTrigger.getCondition()).isInstanceOf(ArtifactTriggerCondition.class);
    assertThat(((ArtifactTriggerCondition) updatedTrigger.getCondition()).getArtifactFilter())
        .isNotNull()
        .isEqualTo(ARTIFACT_FILTER);
    assertThat(((ArtifactTriggerCondition) updatedTrigger.getCondition()).getArtifactStreamId())
        .isNotNull()
        .isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(updatedTrigger.getArtifactSelections())
        .isNotNull()
        .extracting(ArtifactSelection::getType)
        .contains(ARTIFACT_SOURCE);

    assertThat(((ArtifactTriggerCondition) updatedTrigger.getCondition()).isRegex()).isTrue();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateWorkflowArtifactConditionTrigger() {
    Trigger trigger = triggerService.save(workflowArtifactConditionTrigger);
    assertThat(trigger).isNotNull();
    assertThat(trigger.getUuid()).isNotEmpty();

    Trigger savedWorkflowTrigger = triggerService.get(trigger.getAppId(), trigger.getUuid());
    assertThat(savedWorkflowTrigger).isNotNull();
    assertThat(savedWorkflowTrigger.getCondition()).isInstanceOf(ArtifactTriggerCondition.class);

    savedWorkflowTrigger.setArtifactSelections(
        asList(ArtifactSelection.builder().serviceId(SERVICE_ID).type(ARTIFACT_SOURCE).build(),
            ArtifactSelection.builder()
                .type(LAST_COLLECTED)
                .serviceId(SERVICE_ID)
                .artifactStreamId(ARTIFACT_STREAM_ID)
                .artifactFilter(ARTIFACT_FILTER)
                .build(),
            ArtifactSelection.builder().serviceId(SERVICE_ID).type(LAST_DEPLOYED).workflowId(WORKFLOW_ID).build()));

    when(workflowService.readWorkflow(trigger.getAppId(), WORKFLOW_ID)).thenReturn(buildWorkflow());

    Trigger updatedTrigger = triggerService.update(savedWorkflowTrigger, false);

    assertThat(updatedTrigger.getUuid()).isNotEmpty();
    assertThat(updatedTrigger.getCondition()).isInstanceOf(ArtifactTriggerCondition.class);
    assertThat(((ArtifactTriggerCondition) updatedTrigger.getCondition()).getArtifactFilter())
        .isNotNull()
        .isEqualTo(ARTIFACT_FILTER);
    assertThat(((ArtifactTriggerCondition) updatedTrigger.getCondition()).getArtifactStreamId())
        .isNotNull()
        .isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(updatedTrigger.getArtifactSelections())
        .isNotNull()
        .extracting(ArtifactSelection::getType)
        .contains(ARTIFACT_SOURCE, LAST_DEPLOYED, LAST_COLLECTED);
    assertThat(updatedTrigger.getWorkflowType()).isEqualTo(ORCHESTRATION);

    verify(workflowService, times(2)).readWorkflow(trigger.getAppId(), WORKFLOW_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldSavePipelineConditionTrigger() {
    Trigger trigger = triggerService.save(pipelineCondTrigger);
    assertThat(trigger.getUuid()).isNotEmpty();
    assertThat(trigger.getCondition()).isInstanceOf(PipelineTriggerCondition.class);
    assertThat(((PipelineTriggerCondition) trigger.getCondition()).getPipelineId()).isNotNull().isEqualTo(PIPELINE_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdatePipelineConditionTrigger() {
    Trigger savedPipelineCondTrigger = triggerService.save(pipelineCondTrigger);

    savedPipelineCondTrigger.setArtifactSelections(
        asList(ArtifactSelection.builder().type(PIPELINE_SOURCE).serviceId(SERVICE_ID).build(),
            ArtifactSelection.builder().serviceId(SERVICE_ID).type(LAST_DEPLOYED).pipelineId(PIPELINE_ID).build()));

    Trigger updatedTrigger = triggerService.update(savedPipelineCondTrigger, false);

    assertThat(updatedTrigger.getUuid()).isEqualTo(TRIGGER_ID);
    assertThat(updatedTrigger.getCondition()).isInstanceOf(PipelineTriggerCondition.class);
    assertThat(updatedTrigger.getArtifactSelections())
        .isNotNull()
        .extracting(ArtifactSelection::getType)
        .contains(PIPELINE_SOURCE, LAST_DEPLOYED);

    verify(pipelineService, times(2)).readPipeline(APP_ID, PIPELINE_ID, true);
  }

  @Test
  @Owner(developers = ATHARVA)
  @Category(UnitTests.class)
  public void shouldValidateWorkflowVariables() {
    Map<String, String> workflowVariables = new HashMap<>();
    workflowVariables.put("var1", "val1");
    workflowVariables.put("var2", "val2");
    Variable var1 = new Variable();
    var1.setName("var1");
    var1.setAllowedList(asList("val1", "val3"));
    Variable var2 = new Variable();
    var2.setName("var2");
    var2.setAllowedList(asList("val4", "val5"));
    List<Variable> allowedValues = new ArrayList<>();
    allowedValues.add(var1);
    allowedValues.add(var2);
    assertThatThrownBy(() -> triggerService.validateWorkflowVariable(workflowVariables, allowedValues))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Trigger rejected because passed workflow variable value val2 was not present in allowed values list [val4,val5]");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldValidateWorkflowVariablesMultiValues() {
    Map<String, String> workflowVariables = new HashMap<>();
    workflowVariables.put("var1", "val1, val4");
    List<Variable> variableList = new ArrayList<>();
    variableList.add(aVariable().name("var1").allowedList(asList("val1", "val3", "val4")).build());
    variableList.add(aVariable().name("var2").allowedList(asList("val4", "val5")).build());
    triggerService.validateWorkflowVariable(workflowVariables, variableList);

    workflowVariables.put("var2", "val2");
    assertThatThrownBy(() -> triggerService.validateWorkflowVariable(workflowVariables, variableList))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Trigger rejected because passed workflow variable value val2 was not present in allowed values list [val4,val5]");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldNotUpdatePipelineConditionTrigger() {
    Trigger savedPipelineCondTrigger = triggerService.save(pipelineCondTrigger);

    savedPipelineCondTrigger.setArtifactSelections(
        asList(ArtifactSelection.builder().type(PIPELINE_SOURCE).serviceId(SERVICE_ID).build(),
            ArtifactSelection.builder().serviceId(SERVICE_ID).type(LAST_DEPLOYED).build()));

    triggerService.update(savedPipelineCondTrigger, false);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldSaveScheduledConditionTrigger() {
    Trigger trigger = triggerService.save(scheduledConditionTrigger);
    Trigger savedScheduledTrigger = triggerService.get(trigger.getAppId(), trigger.getUuid());

    assertThat(savedScheduledTrigger.getUuid()).isNotEmpty();
    assertThat(savedScheduledTrigger.getCondition()).isInstanceOf(ScheduledTriggerCondition.class);
    assertThat(((ScheduledTriggerCondition) trigger.getCondition()).getCronDescription()).isNotNull();
    assertThat(((ScheduledTriggerCondition) trigger.getCondition()).getCronExpression())
        .isNotNull()
        .isEqualTo("0/5 0 ? * * *");
    assertThat(savedScheduledTrigger.getNextIterations()).isNotEmpty();
    assertThat(trigger.getNextIterations()).isNotEmpty();
    verify(jobScheduler).scheduleJob(any(JobDetail.class), any(org.quartz.Trigger.class));
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void shouldNotSaveScheduledConditionTrigger() {
    scheduledConditionTrigger.setCondition(ScheduledTriggerCondition.builder().cronExpression("* * * * ?").build());
    assertThatThrownBy(() -> triggerService.save(scheduledConditionTrigger))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Deployments must be triggered at intervals greater than or equal to 5 minutes. Cron Expression should evaluate to time intervals of at least "
            + 300 + " seconds.");
    scheduledConditionTrigger.setCondition(ScheduledTriggerCondition.builder().cronExpression("0/2 0 ? * * *").build());
    assertThatThrownBy(() -> triggerService.save(scheduledConditionTrigger))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Deployments must be triggered at intervals greater than or equal to 5 minutes. Cron Expression should evaluate to time intervals of at least "
            + 300 + " seconds.");
    scheduledConditionTrigger.setCondition(ScheduledTriggerCondition.builder().cronExpression("0/5 0 ? * * *").build());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateScheduledConditionTrigger() {
    scheduledConditionTrigger = triggerService.save(scheduledConditionTrigger);
    scheduledConditionTrigger.setArtifactSelections(asList(ArtifactSelection.builder()
                                                               .type(LAST_COLLECTED)
                                                               .serviceId(SERVICE_ID)
                                                               .artifactStreamId(ARTIFACT_STREAM_ID)
                                                               .artifactFilter(ARTIFACT_FILTER)
                                                               .build(),
        ArtifactSelection.builder().type(LAST_DEPLOYED).serviceId(SERVICE_ID).workflowId(PIPELINE_ID).build()));

    Trigger updatedTrigger = triggerService.update(scheduledConditionTrigger, false);
    assertThat(updatedTrigger.getUuid()).isEqualTo(TRIGGER_ID);

    assertThat(updatedTrigger.getCondition()).isInstanceOf(ScheduledTriggerCondition.class);
    assertThat(((ScheduledTriggerCondition) updatedTrigger.getCondition()).getCronDescription()).isNotNull();
    assertThat(((ScheduledTriggerCondition) updatedTrigger.getCondition()).getCronExpression())
        .isNotNull()
        .isEqualTo("0/5 0 ? * * *");
    assertThat(updatedTrigger.getArtifactSelections())
        .isNotNull()
        .extracting(ArtifactSelection::getType)
        .contains(LAST_COLLECTED, LAST_DEPLOYED);
    assertThat(updatedTrigger.getNextIterations()).isNotEmpty();
    verify(jobScheduler).rescheduleJob(any(TriggerKey.class), any(org.quartz.Trigger.class));
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void shouldNotUpdateScheduledConditionTrigger() {
    scheduledConditionTrigger = triggerService.save(scheduledConditionTrigger);
    scheduledConditionTrigger.setCondition(ScheduledTriggerCondition.builder().cronExpression("* * * * ?").build());
    assertThatThrownBy(() -> triggerService.update(scheduledConditionTrigger, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Deployments must be triggered at intervals greater than or equal to 5 minutes. Cron Expression should evaluate to time intervals of at least "
            + 300 + " seconds.");
    scheduledConditionTrigger.setCondition(ScheduledTriggerCondition.builder().cronExpression("0/2 0 ? * * *").build());
    assertThatThrownBy(() -> triggerService.update(scheduledConditionTrigger, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Deployments must be triggered at intervals greater than or equal to 5 minutes. Cron Expression should evaluate to time intervals of at least "
            + 300 + " seconds.");
    scheduledConditionTrigger.setCondition(ScheduledTriggerCondition.builder().cronExpression("0/5 0 ? * * *").build());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldSaveWebhookConditionTrigger() {
    Pipeline pipeline = buildPipeline();
    setPipelineStages(pipeline);

    Trigger trigger = triggerService.save(buildWebhookCondTrigger());
    assertThat(trigger.getUuid()).isEqualTo(TRIGGER_ID);
    assertThat(trigger.getCondition()).isInstanceOf(WebHookTriggerCondition.class);
    assertThat(((WebHookTriggerCondition) trigger.getCondition()).getWebHookToken()).isNotNull();
    assertThat(((WebHookTriggerCondition) trigger.getCondition()).getWebHookToken().getWebHookToken()).isNotNull();
    verify(pipelineService, times(1)).readPipeline(APP_ID, PIPELINE_ID, true);
    verify(pipelineService, times(1)).readPipelineWithResolvedVariables(eq(APP_ID), eq(PIPELINE_ID), any());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldSaveWorkflowWebhookTriggerNoArtifactSelections() {
    Trigger trigger = triggerService.save(workflowWebhookConditionTrigger);
    Trigger savedTrigger = triggerService.get(APP_ID, trigger.getUuid());

    assertThat(savedTrigger.getUuid()).isEqualTo(TRIGGER_ID);
    assertThat(savedTrigger.getCondition()).isInstanceOf(WebHookTriggerCondition.class);
    WebHookToken webHookToken = ((WebHookTriggerCondition) savedTrigger.getCondition()).getWebHookToken();
    assertThat(webHookToken).isNotNull();
    assertThat(webHookToken.getWebHookToken()).isNotNull();
    assertThat(webHookToken.getPayload()).isNotEmpty();

    HashMap<String, Object> hashMap = new Gson().fromJson(webHookToken.getPayload(), HashMap.class);
    assertThat(hashMap).containsKeys("application", "parameters");
    assertThat(hashMap.get("application")).isEqualTo(APP_ID);
    assertThat(hashMap.get("artifacts")).isNull();
    assertThat(hashMap.get("parameters")).isNotNull().toString().contains("MyVar=MyVar_placeholder");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldSaveWorkflowWebhookTriggerWithArtifactSelections() {
    workflowWebhookConditionTrigger.setArtifactSelections(asList(ArtifactSelection.builder()
                                                                     .type(WEBHOOK_VARIABLE)
                                                                     .serviceId(SERVICE_ID)
                                                                     .artifactStreamId(ARTIFACT_STREAM_ID)
                                                                     .build()));

    Trigger trigger = triggerService.save(workflowWebhookConditionTrigger);
    Trigger savedTrigger = triggerService.get(APP_ID, trigger.getUuid());

    assertThat(savedTrigger.getUuid()).isEqualTo(TRIGGER_ID);
    assertThat(savedTrigger.getCondition()).isInstanceOf(WebHookTriggerCondition.class);
    WebHookToken webHookToken = ((WebHookTriggerCondition) savedTrigger.getCondition()).getWebHookToken();
    assertThat(webHookToken).isNotNull();
    assertThat(webHookToken.getWebHookToken()).isNotNull();
    assertThat(webHookToken.getPayload()).isNotEmpty();

    HashMap<String, Object> hashMap = new Gson().fromJson(webHookToken.getPayload(), HashMap.class);
    assertThat(hashMap).containsKeys("application", "artifacts", "parameters");
    assertThat(hashMap).containsKeys("application", "artifacts", "parameters");
    assertThat(hashMap.get("application")).isEqualTo(APP_ID);
    assertThat(hashMap.get("artifacts"))
        .isNotNull()
        .toString()
        .contains(
            "{service=Catalog, buildNumber=Catalog_BUILD_NUMBER_PLACE_HOLDER}, {service=Order, buildNumber=Order_BUILD_NUMBER_PLACE_HOLDER}");
    assertThat(hashMap.get("parameters")).isNotNull().toString().contains("MyVar=MyVar_placeholder");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldSaveWorkflowWebhookTriggerWithFileContentChanged() {
    Trigger trigger = triggerService.save(workflowWebhookConditionTriggerWithFileContentChanged);
    Trigger savedTrigger = triggerService.get(APP_ID, trigger.getUuid());

    assertThat(savedTrigger.getUuid()).isEqualTo(TRIGGER_ID);
    assertThat(savedTrigger.getCondition()).isInstanceOf(WebHookTriggerCondition.class);
    WebHookToken webHookToken = ((WebHookTriggerCondition) savedTrigger.getCondition()).getWebHookToken();
    assertThat(webHookToken).isNotNull();
    assertThat(webHookToken.getWebHookToken()).isNotNull();
    assertThat(webHookToken.getPayload()).isNotEmpty();

    HashMap<String, Object> hashMap = new Gson().fromJson(webHookToken.getPayload(), HashMap.class);
    assertThat(hashMap).containsKeys("application", "parameters");
    assertThat(hashMap.get("application")).isEqualTo(APP_ID);
    assertThat(hashMap.get("artifacts")).isNull();
    assertThat(hashMap.get("parameters")).isNotNull().toString().contains("MyVar=MyVar_placeholder");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldNotSaveWorkflowWebhookTriggerWithoutGitConnectorId() {
    Trigger workflowWebhookConditionTrigger =
        buildWorkflowWebhookTriggerWithFileContentChanged("testRepo", "master", null, PUSH, "index.yaml");

    triggerService.save(workflowWebhookConditionTrigger);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldNotSaveWorkflowWebhookTriggerWithoutBranchName() {
    Trigger workflowWebhookConditionTrigger =
        buildWorkflowWebhookTriggerWithFileContentChanged("testRepo", null, UUID, PUSH, "index.yaml");

    triggerService.save(workflowWebhookConditionTrigger);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldNotSaveWorkflowWebhookTriggerWithoutRepoName() {
    Trigger workflowWebhookConditionTrigger =
        buildWorkflowWebhookTriggerWithFileContentChanged(null, "master", UUID, PUSH, "index.yaml");

    triggerService.save(workflowWebhookConditionTrigger);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldNotSaveWorkflowWebhookTriggerWithoutFilePaths() {
    Trigger workflowWebhookConditionTrigger =
        buildWorkflowWebhookTriggerWithFileContentChanged("testRepo", "master", UUID, PUSH, null);

    triggerService.save(workflowWebhookConditionTrigger);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldNotSaveWorkflowWebhookTriggerWithoutEventType() {
    Trigger workflowWebhookConditionTrigger =
        buildWorkflowWebhookTriggerWithFileContentChanged("testRepo", "master", UUID, null, "index.yaml");

    triggerService.save(workflowWebhookConditionTrigger);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldNotSaveWorkflowWebhookTriggerWithWrongEventType() {
    Trigger workflowWebhookConditionTrigger =
        buildWorkflowWebhookTriggerWithFileContentChanged("testRepo", "master", UUID, PULL_REQUEST, "index.yaml");

    triggerService.save(workflowWebhookConditionTrigger);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldSavePipelineWebhookTriggerNoArtifactSelections() {
    setPipelineStages(pipeline);
    pipeline.getPipelineVariables().add(aVariable().name("MyVar").build());
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(pipeline);

    Trigger trigger = triggerService.save(webhookConditionTrigger);

    Trigger savedTrigger = triggerService.get(APP_ID, trigger.getUuid());
    assertThat(savedTrigger.getCondition()).isInstanceOf(WebHookTriggerCondition.class);
    WebHookToken webHookToken = ((WebHookTriggerCondition) savedTrigger.getCondition()).getWebHookToken();
    assertThat(webHookToken).isNotNull();
    assertThat(webHookToken.getWebHookToken()).isNotNull();
    assertThat(webHookToken.getPayload()).isNotEmpty();

    HashMap<String, Object> hashMap = new Gson().fromJson(webHookToken.getPayload(), HashMap.class);
    assertThat(hashMap).containsKeys("application", "parameters");
    assertThat(hashMap.get("application")).isEqualTo(APP_ID);
    assertThat(hashMap.get("artifacts")).isNull();
    assertThat(hashMap.get("parameters")).isNotNull().toString().contains("MyVar=MyVar_placeholder");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldSavePipelineWebhookTriggerWithArtifactSelections() {
    setPipelineStages(pipeline);
    pipeline.getPipelineVariables().add(aVariable().name("MyVar").build());

    pipeline.getPipelineVariables().add(aVariable().name("MyVar").build());
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(pipeline);
    setWebhookArtifactSelections();

    Trigger trigger = triggerService.save(webhookConditionTrigger);

    Trigger savedTrigger = triggerService.get(APP_ID, trigger.getUuid());
    assertThat(savedTrigger.getCondition()).isInstanceOf(WebHookTriggerCondition.class);
    WebHookToken webHookToken = ((WebHookTriggerCondition) savedTrigger.getCondition()).getWebHookToken();
    assertThat(webHookToken).isNotNull();
    assertThat(webHookToken.getWebHookToken()).isNotNull();
    assertThat(webHookToken.getPayload()).isNotEmpty();

    HashMap<String, Object> hashMap = new Gson().fromJson(webHookToken.getPayload(), HashMap.class);
    assertThat(hashMap).containsKeys("application", "artifacts", "parameters");
    assertThat(hashMap).containsKeys("application", "artifacts", "parameters");
    assertThat(hashMap.get("application")).isEqualTo(APP_ID);
    assertThat(hashMap.get("artifacts"))
        .isNotNull()
        .toString()
        .contains(
            "{service=Catalog, buildNumber=Catalog_BUILD_NUMBER_PLACE_HOLDER}, {service=Order, buildNumber=Order_BUILD_NUMBER_PLACE_HOLDER}");
    assertThat(hashMap.get("parameters")).isNotNull().toString().contains("MyVar=MyVar_placeholder");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateWebhookConditionTrigger() {
    setWebhookArtifactSelections();

    webhookConditionTrigger = triggerService.save(webhookConditionTrigger);

    Trigger updatedTrigger = triggerService.update(webhookConditionTrigger, false);
    assertThat(updatedTrigger.getUuid()).isEqualTo(TRIGGER_ID);
    assertThat(updatedTrigger.getCondition()).isInstanceOf(WebHookTriggerCondition.class);
    assertThat(((WebHookTriggerCondition) updatedTrigger.getCondition()).getWebHookToken().getPayload()).isNotNull();
    assertThat(((WebHookTriggerCondition) updatedTrigger.getCondition()).getWebHookToken().getWebHookToken())
        .isNotNull();
    assertThat(updatedTrigger.getArtifactSelections())
        .isNotNull()
        .extracting(ArtifactSelection::getType)
        .contains(LAST_COLLECTED, LAST_DEPLOYED);
  }

  private void setWebhookArtifactSelections() {
    webhookConditionTrigger.setArtifactSelections(asList(ArtifactSelection.builder()
                                                             .type(LAST_COLLECTED)
                                                             .artifactStreamId(ARTIFACT_STREAM_ID)
                                                             .serviceId(SERVICE_ID)
                                                             .artifactFilter(ARTIFACT_FILTER)
                                                             .build(),
        ArtifactSelection.builder().type(LAST_DEPLOYED).serviceId(SERVICE_ID).workflowId(PIPELINE_ID).build()));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateScheduledConditionTriggerToOtherType() {
    Trigger trigger = triggerService.save(scheduledConditionTrigger);
    assertThat(trigger.getUuid()).isEqualTo(TRIGGER_ID);
    assertThat(trigger.getCondition()).isInstanceOf(ScheduledTriggerCondition.class);
    assertThat(((ScheduledTriggerCondition) trigger.getCondition()).getCronDescription()).isNotNull();
    assertThat(((ScheduledTriggerCondition) trigger.getCondition()).getCronExpression())
        .isNotNull()
        .isEqualTo("0/5 0 ? * * *");
    assertThat(trigger.getNextIterations()).isNotEmpty();
    verify(jobScheduler).scheduleJob(any(JobDetail.class), any(org.quartz.Trigger.class));

    setWebhookArtifactSelections();

    Trigger updatedTrigger = triggerService.update(webhookConditionTrigger, false);

    assertThat(updatedTrigger.getCondition()).isInstanceOf(WebHookTriggerCondition.class);
    assertThat(((WebHookTriggerCondition) updatedTrigger.getCondition()).getWebHookToken().getPayload()).isNotNull();
    assertThat(((WebHookTriggerCondition) updatedTrigger.getCondition()).getWebHookToken()).isNotNull();
    assertThat(updatedTrigger.getArtifactSelections())
        .isNotNull()
        .extracting(ArtifactSelection::getType)
        .contains(LAST_COLLECTED, LAST_DEPLOYED);
    assertThat(updatedTrigger.getNextIterations()).isEmpty();
    verify(jobScheduler).deleteJob(TRIGGER_ID, ScheduledTriggerJob.GROUP);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateOtherConditionTriggerToScheduled() {
    Trigger trigger = triggerService.save(artifactConditionTrigger);
    assertThat(trigger.getUuid()).isNotEmpty();
    assertThat(trigger.getCondition()).isInstanceOf(ArtifactTriggerCondition.class);
    assertThat(((ArtifactTriggerCondition) trigger.getCondition()).getArtifactFilter())
        .isNotNull()
        .isEqualTo(ARTIFACT_FILTER);
    assertThat(((ArtifactTriggerCondition) trigger.getCondition()).getArtifactStreamId())
        .isNotNull()
        .isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(trigger.getNextIterations()).isEmpty();

    Trigger updatedTrigger = triggerService.update(scheduledConditionTrigger, false);
    assertThat(updatedTrigger.getUuid()).isEqualTo(TRIGGER_ID);
    assertThat(updatedTrigger.getCondition()).isInstanceOf(ScheduledTriggerCondition.class);
    assertThat(((ScheduledTriggerCondition) updatedTrigger.getCondition()).getCronDescription()).isNotNull();
    assertThat(((ScheduledTriggerCondition) updatedTrigger.getCondition()).getCronExpression())
        .isNotNull()
        .isEqualTo("0/5 0 ? * * *");
    assertThat(updatedTrigger.getNextIterations()).isNotEmpty();
    verify(jobScheduler).scheduleJob(any(JobDetail.class), any(org.quartz.Trigger.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldDeleteScheduleTrigger() {
    triggerService.save(scheduledConditionTrigger);
    assertThat(triggerService.delete(APP_ID, TRIGGER_ID)).isTrue();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldDeleteArtifactTrigger() {
    triggerService.save(artifactConditionTrigger);
    assertThat(triggerService.delete(APP_ID, TRIGGER_ID)).isTrue();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldDeleteTriggersForPipeline() {
    Trigger trigger = triggerService.save(pipelineCondTrigger);
    assertThat(trigger).isNotNull();
    assertThat(trigger.getUuid()).isEqualTo(TRIGGER_ID);
    assertThat(trigger.getAppId()).isEqualTo(APP_ID);
    scheduledConditionTrigger.setUuid("Trigger 2");
    Trigger trigger2 = triggerService.save(scheduledConditionTrigger);
    assertThat(trigger2).isNotNull();
    assertThat(trigger2.getUuid()).isEqualTo("Trigger 2");
    assertThat(trigger2.getAppId()).isEqualTo(APP_ID);

    triggerService.pruneByPipeline(APP_ID, PIPELINE_ID);
    verify(auditServiceHelper).reportDeleteForAuditing(APP_ID, trigger);
    verify(auditServiceHelper).reportDeleteForAuditing(APP_ID, trigger2);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldDeleteTriggersForWorkflow() {
    Trigger trigger = triggerService.save(workflowWebhookConditionTrigger);
    assertThat(trigger).isNotNull();
    assertThat(trigger.getUuid()).isEqualTo(TRIGGER_ID);
    assertThat(trigger.getAppId()).isEqualTo(APP_ID);
    assertThat(trigger.getWorkflowId()).isEqualTo(WORKFLOW_ID);
    triggerService.pruneByWorkflow(APP_ID, WORKFLOW_ID);
    verify(auditServiceHelper).reportDeleteForAuditing(APP_ID, trigger);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldDeleteTriggersForArtifactStream() {
    Trigger trigger = triggerService.save(artifactConditionTrigger);
    assertThat(trigger).isNotNull();
    assertThat(trigger.getUuid()).isEqualTo(TRIGGER_ID);
    assertThat(trigger.getAppId()).isEqualTo(APP_ID);

    triggerService.pruneByArtifactStream(APP_ID, ARTIFACT_STREAM_ID);
    verify(auditServiceHelper).reportDeleteForAuditing(APP_ID, trigger);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldDeleteTriggersForAppManifests() {
    setUpForNewManifestCondition();
    Trigger trigger = triggerService.save(newManifestConditionTrigger);
    assertThat(trigger).isNotNull();
    assertThat(trigger.getUuid()).isEqualTo(TRIGGER_ID);
    assertThat(trigger.getAppId()).isEqualTo(APP_ID);

    triggerService.pruneByApplicationManifest(APP_ID, MANIFEST_ID);
    verify(auditServiceHelper).reportDeleteForAuditing(APP_ID, trigger);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldDeleteTriggersByApp() {
    Trigger trigger = triggerService.save(scheduledConditionTrigger);
    assertThat(trigger).isNotNull();
    assertThat(trigger.getUuid()).isEqualTo(TRIGGER_ID);
    assertThat(trigger.getAppId()).isEqualTo(APP_ID);
    triggerService.pruneByApplication(APP_ID);
    verify(auditServiceHelper).reportDeleteForAuditing(APP_ID, trigger);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGenerateWebHookToken() {
    triggerService.save(webhookConditionTrigger);
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(pipeline);
    WebHookToken webHookToken = triggerService.generateWebHookToken(APP_ID, TRIGGER_ID);
    assertThat(webHookToken).isNotNull();
    assertThat(webHookToken.getPayload()).contains("application");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerExecutionPostArtifactCollectionAsync() {
    Artifact artifact = anArtifact()
                            .withAppId(APP_ID)
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", ARTIFACT_FILTER)))
                            .build();
    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());

    triggerService.save(artifactConditionTrigger);

    triggerService.triggerExecutionPostArtifactCollectionAsync(APP_ID, ARTIFACT_STREAM_ID, asList(artifact));
    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldNotTriggerExecutionPostArtifactCollectionAsyncNoArtifactsMatched() {
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);
    Artifact artifact = anArtifact()
                            .withAppId(APP_ID)
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", ARTIFACT_FILTER)))
                            .build();
    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());

    triggerService.save(artifactConditionTriggerWithArtifactSelections);

    triggerService.triggerExecutionPostArtifactCollectionAsync(APP_ID, ARTIFACT_STREAM_ID, asList(artifact));
    verify(workflowExecutionService, times(0))
        .triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotRunArtifactCollectionForDisabledTrigger() {
    Artifact artifact = anArtifact()
                            .withAppId(APP_ID)
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", ARTIFACT_FILTER)))
                            .build();
    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());

    artifactConditionTrigger.setDisabled(true);
    triggerService.save(artifactConditionTrigger);

    triggerService.triggerExecutionPostArtifactCollectionAsync(APP_ID, ARTIFACT_STREAM_ID, asList(artifact));
    verify(workflowExecutionService, never())
        .triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerExecutionPostArtifactCollectionWithFileNotMatchesArtifactFilter() {
    Artifact artifact = anArtifact()
                            .withAppId(APP_ID)
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", ARTIFACT_FILTER)))
                            .withArtifactFiles(singletonList(
                                anArtifactFile().withAppId(APP_ID).withFileUuid(FILE_ID).withName(FILE_NAME).build()))
                            .build();
    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());

    triggerService.triggerExecutionPostArtifactCollectionAsync(APP_ID, ARTIFACT_STREAM_ID, asList(artifact));

    verify(workflowExecutionService, times(0))
        .triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerExecutionPostArtifactCollectionWithFileRegexNotStartsWith() {
    Artifact artifact = anArtifact()
                            .withAppId(APP_ID)
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", ARTIFACT_FILTER)))
                            .withArtifactFiles(singletonList(
                                anArtifactFile().withAppId(APP_ID).withFileUuid(FILE_ID).withName(FILE_NAME).build()))
                            .build();
    ArtifactTriggerCondition artifactTriggerCondition =
        (ArtifactTriggerCondition) artifactConditionTrigger.getCondition();
    artifactTriggerCondition.setRegex(true);
    artifactTriggerCondition.setArtifactFilter("^(?!release)");

    triggerService.save(artifactConditionTrigger);

    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());

    triggerService.triggerExecutionPostArtifactCollectionAsync(APP_ID, ARTIFACT_STREAM_ID, asList(artifact));

    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerExecutionPostArtifactCollectionRegexNotStartsWith() {
    Artifact artifact = anArtifact()
                            .withAppId(APP_ID)
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", ARTIFACT_FILTER)))
                            .build();
    ArtifactTriggerCondition artifactTriggerCondition =
        (ArtifactTriggerCondition) artifactConditionTrigger.getCondition();
    artifactTriggerCondition.setRegex(true);
    artifactTriggerCondition.setArtifactFilter("^(?!release)");

    triggerService.save(artifactConditionTrigger);

    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());

    triggerService.triggerExecutionPostArtifactCollectionAsync(APP_ID, ARTIFACT_STREAM_ID, asList(artifact));

    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerExecutionPostArtifactCollectionRegexMatch() {
    Artifact artifact = anArtifact()
                            .withAppId(APP_ID)
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", "release2345")))
                            .build();
    ArtifactTriggerCondition artifactTriggerCondition =
        (ArtifactTriggerCondition) artifactConditionTrigger.getCondition();
    artifactTriggerCondition.setRegex(true);
    artifactTriggerCondition.setArtifactFilter("^release");

    triggerService.save(artifactConditionTrigger);

    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());

    triggerService.triggerExecutionPostArtifactCollectionAsync(APP_ID, ARTIFACT_STREAM_ID, asList(artifact));

    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerExecutionPostArtifactCollectionRegexDoesNotMatch() {
    Artifact artifact = anArtifact()
                            .withAppId(APP_ID)
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", "@33release23")))
                            .build();
    ArtifactTriggerCondition artifactTriggerCondition =
        (ArtifactTriggerCondition) artifactConditionTrigger.getCondition();
    artifactTriggerCondition.setRegex(true);
    artifactTriggerCondition.setArtifactFilter("^release");

    triggerService.save(artifactConditionTrigger);

    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());

    triggerService.triggerExecutionPostArtifactCollectionAsync(APP_ID, ARTIFACT_STREAM_ID, asList(artifact));

    verify(workflowExecutionService, times(0))
        .triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerPostArtifactCollectionBothArtifactsDoesNotMatch() {
    Artifact artifact = anArtifact()
                            .withAppId(APP_ID)
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", "@33release23")))
                            .build();
    Artifact artifact2 = anArtifact()
                             .withAppId(APP_ID)
                             .withUuid(ARTIFACT_ID)
                             .withArtifactStreamId(ARTIFACT_STREAM_ID)
                             .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", "@34release23")))
                             .build();

    ArtifactTriggerCondition artifactTriggerCondition =
        (ArtifactTriggerCondition) artifactConditionTrigger.getCondition();
    artifactTriggerCondition.setRegex(true);
    artifactTriggerCondition.setArtifactFilter("^release");

    triggerService.save(artifactConditionTrigger);

    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());

    triggerService.triggerExecutionPostArtifactCollectionAsync(APP_ID, ARTIFACT_STREAM_ID, asList(artifact, artifact2));

    verify(workflowExecutionService, times(0))
        .triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerPostArtifactCollectionBothArtifactsMatch() {
    Artifact artifact = anArtifact()
                            .withAppId(APP_ID)
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", "release23")))
                            .build();
    Artifact artifact2 = anArtifact()
                             .withAppId(APP_ID)
                             .withUuid(ARTIFACT_ID)
                             .withArtifactStreamId(ARTIFACT_STREAM_ID)
                             .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", "release456")))
                             .build();
    ArtifactTriggerCondition artifactTriggerCondition =
        (ArtifactTriggerCondition) artifactConditionTrigger.getCondition();
    artifactTriggerCondition.setRegex(true);
    artifactTriggerCondition.setArtifactFilter("^release");

    triggerService.save(artifactConditionTrigger);

    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());

    triggerService.triggerExecutionPostArtifactCollectionAsync(APP_ID, ARTIFACT_STREAM_ID, asList(artifact, artifact2));

    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerPostArtifactCollectionForAllArtifactsMatch() {
    Artifact artifact = anArtifact()
                            .withAppId(APP_ID)
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", "release23")))
                            .build();
    Artifact artifact2 = anArtifact()
                             .withAppId(APP_ID)
                             .withUuid(ARTIFACT_ID)
                             .withArtifactStreamId(ARTIFACT_STREAM_ID)
                             .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", "release456")))
                             .build();
    ArtifactTriggerCondition artifactTriggerCondition =
        (ArtifactTriggerCondition) artifactConditionTrigger.getCondition();
    artifactTriggerCondition.setRegex(true);
    artifactTriggerCondition.setArtifactFilter("^release");
    artifactConditionTrigger.setArtifactSelections(
        asList(ArtifactSelection.builder().type(ARTIFACT_SOURCE).serviceId(SERVICE_ID).build()));

    triggerService.save(artifactConditionTrigger);
    when(featureFlagService.isEnabled(FeatureName.TRIGGER_FOR_ALL_ARTIFACTS, ACCOUNT_ID)).thenReturn(true);
    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());
    when(artifactStreamServiceBindingService.getServiceId(APP_ID, ARTIFACT_STREAM_ID, true)).thenReturn(SERVICE_ID);

    triggerService.triggerExecutionPostArtifactCollectionAsync(
        ACCOUNT_ID, APP_ID, ARTIFACT_STREAM_ID, asList(artifact, artifact2));

    verify(workflowExecutionService, times(2))
        .triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerPostArtifactCollectionOneArtifactMatchesOtherDoesNotMatch() {
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true))
        .thenReturn(Pipeline.builder()
                        .appId(APP_ID)
                        .uuid(PIPELINE_ID)
                        .services(asList(Service.builder().uuid(SERVICE_ID_CHANGED).name("Order").build()))
                        .build());

    Artifact artifact = anArtifact()
                            .withAppId(APP_ID)
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", "@33release23")))
                            .build();
    Artifact artifact2 = anArtifact()
                             .withAppId(APP_ID)
                             .withUuid(UUIDGenerator.generateUuid())
                             .withArtifactStreamId(ARTIFACT_STREAM_ID)
                             .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", "release456")))
                             .build();

    ArtifactTriggerCondition artifactTriggerCondition =
        (ArtifactTriggerCondition) artifactConditionTrigger.getCondition();
    artifactTriggerCondition.setRegex(true);
    artifactTriggerCondition.setArtifactFilter("^release");

    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());

    triggerService.save(artifactConditionTrigger);

    triggerService.triggerExecutionPostArtifactCollectionAsync(APP_ID, ARTIFACT_STREAM_ID, asList(artifact, artifact2));

    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotTriggerPostArtifactCollectionIfArtifactsMissing() {
    Artifact artifact = anArtifact()
                            .withAppId(APP_ID)
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", "@33release23")))
                            .build();
    Artifact artifact2 = anArtifact()
                             .withAppId(APP_ID)
                             .withUuid(UUIDGenerator.generateUuid())
                             .withArtifactStreamId(ARTIFACT_STREAM_ID)
                             .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", "release456")))
                             .build();

    ArtifactTriggerCondition artifactTriggerCondition =
        (ArtifactTriggerCondition) artifactConditionTrigger.getCondition();
    artifactTriggerCondition.setRegex(true);
    artifactTriggerCondition.setArtifactFilter("^release");

    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());

    triggerService.save(artifactConditionTrigger);

    triggerService.triggerExecutionPostArtifactCollectionAsync(APP_ID, ARTIFACT_STREAM_ID, asList(artifact, artifact2));

    verify(serviceResourceService).fetchServiceNamesByUuids(APP_ID, asList(SERVICE_ID));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerExecutionPostArtifactCollectionWithArtifactMatchesArtifactFilter() {
    triggerService.save(artifactConditionTrigger);
    Artifact artifact = anArtifact()
                            .withAppId(APP_ID)
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", ARTIFACT_FILTER)))
                            .build();
    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());

    triggerService.triggerExecutionPostArtifactCollectionAsync(APP_ID, ARTIFACT_STREAM_ID, asList(artifact));

    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerExecutionPostArtifactCollectionWithArtifactSelections() {
    Artifact artifact = prepareArtifact(ARTIFACT_ID);
    artifactConditionTrigger.setArtifactSelections(
        asList(ArtifactSelection.builder().serviceId(SERVICE_ID).type(ARTIFACT_SOURCE).build(),
            ArtifactSelection.builder()
                .type(LAST_COLLECTED)
                .serviceId(SERVICE_ID)
                .artifactStreamId(ARTIFACT_STREAM_ID)
                .artifactFilter(ARTIFACT_FILTER)
                .build(),
            ArtifactSelection.builder().type(LAST_DEPLOYED).serviceId(SERVICE_ID).workflowId(PIPELINE_ID).build()));

    triggerService.save(artifactConditionTrigger);

    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactStream);
    when(artifactService.fetchLastCollectedApprovedArtifactForArtifactStream(artifactStream)).thenReturn(artifact);
    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());

    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(singletonList(artifact));
    when(workflowExecutionService.listExecutions(any(PageRequest.class), anyBoolean()))
        .thenReturn(aPageResponse()
                        .withResponse(singletonList(
                            WorkflowExecution.builder().appId(APP_ID).executionArgs(executionArgs).build()))
                        .build());

    triggerService.triggerExecutionPostArtifactCollectionAsync(APP_ID, ARTIFACT_STREAM_ID, asList(artifact));
    verify(syncArtifactCollectionServiceImpl).collectNewArtifacts(APP_ID, ARTIFACT_STREAM_ID);
    verifyNoMoreInteractions(syncArtifactCollectionServiceImpl);
    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
    verify(artifactStreamService, times(4)).get(ARTIFACT_STREAM_ID);
    verify(artifactService).getArtifactByBuildNumber(artifactStream, ARTIFACT_FILTER, false);
  }

  private Artifact prepareArtifact(String artifactId) {
    return anArtifact()
        .withAppId(APP_ID)
        .withUuid(artifactId)
        .withArtifactStreamId(ARTIFACT_STREAM_ID)
        .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", ARTIFACT_FILTER)))
        .build();
  }

  private Artifact prepareArtifact(String artifactId, String artifactStreamId) {
    return anArtifact()
        .withAppId(APP_ID)
        .withUuid(artifactId)
        .withArtifactStreamId(artifactStreamId)
        .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", ARTIFACT_FILTER)))
        .build();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerWorkflowExecutionPostArtifactCollectionWithArtifactSelections() {
    Artifact artifact = prepareArtifact(ARTIFACT_ID);
    workflowArtifactConditionTrigger.setArtifactSelections(
        asList(ArtifactSelection.builder().serviceId(SERVICE_ID).type(ARTIFACT_SOURCE).build(),
            ArtifactSelection.builder()
                .type(LAST_COLLECTED)
                .serviceId(SERVICE_ID)
                .artifactStreamId(ARTIFACT_STREAM_ID)
                .artifactFilter(ARTIFACT_FILTER)
                .build(),
            ArtifactSelection.builder().type(LAST_DEPLOYED).serviceId(SERVICE_ID).workflowId(WORKFLOW_ID).build()));

    when(workflowService.fetchWorkflowName(APP_ID, WORKFLOW_ID)).thenReturn(WORKFLOW_NAME);

    triggerService.save(workflowArtifactConditionTrigger);

    ArtifactStream artifactStream = buildJenkinsArtifactStream();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactStream);
    when(artifactService.fetchLastCollectedApprovedArtifactForArtifactStream(artifactStream)).thenReturn(artifact);

    when(workflowService.fetchDeploymentMetadata(
             APP_ID, workflow, new HashMap<>(), null, null, Include.ARTIFACT_SERVICE))
        .thenReturn(DeploymentMetadata.builder().artifactRequiredServiceIds(asList(SERVICE_ID)).build());

    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());

    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(singletonList(artifact));
    when(workflowExecutionService.listExecutions(any(PageRequest.class), anyBoolean()))
        .thenReturn(aPageResponse()
                        .withResponse(singletonList(
                            WorkflowExecution.builder().appId(APP_ID).executionArgs(executionArgs).build()))
                        .build());

    triggerService.triggerExecutionPostArtifactCollectionAsync(APP_ID, ARTIFACT_STREAM_ID, asList(artifact));

    verify(syncArtifactCollectionServiceImpl).collectNewArtifacts(APP_ID, ARTIFACT_STREAM_ID);
    verifyNoMoreInteractions(syncArtifactCollectionServiceImpl);
    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
    verify(artifactStreamService, times(4)).get(ARTIFACT_STREAM_ID);
    verify(artifactService).getArtifactByBuildNumber(artifactStream, ARTIFACT_FILTER, false);
    verify(workflowExecutionService).obtainLastGoodDeployedArtifacts(APP_ID, WORKFLOW_ID, SERVICE_ID);
    verify(workflowService, times(2)).readWorkflow(APP_ID, WORKFLOW_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerTemplateWorkflowExecution() {
    Workflow workflow = buildWorkflow();
    workflow.getOrchestrationWorkflow().getUserVariables().add(
        aVariable().name("Environment").value(ENV_ID).entityType(ENVIRONMENT).build());

    workflowArtifactConditionTrigger.setArtifactSelections(
        asList(ArtifactSelection.builder().serviceId(SERVICE_ID).type(ARTIFACT_SOURCE).build(),
            ArtifactSelection.builder()
                .type(LAST_COLLECTED)
                .serviceId(SERVICE_ID)
                .artifactStreamId(ARTIFACT_STREAM_ID)
                .artifactFilter(ARTIFACT_FILTER)
                .build(),
            ArtifactSelection.builder().type(LAST_DEPLOYED).serviceId(SERVICE_ID).workflowId(WORKFLOW_ID).build()));

    final ImmutableMap<String, String> triggerVariables =
        ImmutableMap.of("Environment", ENV_ID, "Service", SERVICE_ID, "ServiceInfraStructure", INFRA_MAPPING_ID);
    workflowArtifactConditionTrigger.setWorkflowVariables(triggerVariables);

    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(buildJenkinsArtifactStream());

    when(workflowService.readWorkflowWithoutOrchestration(APP_ID, WORKFLOW_ID)).thenReturn(buildWorkflow());

    triggerService.save(workflowArtifactConditionTrigger);

    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());

    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(singletonList(artifact));
    executionArgs.setWorkflowVariables(ImmutableMap.of("MyVar", "MyVal"));

    when(workflowService.fetchDeploymentMetadata(
             APP_ID, workflow, triggerVariables, null, null, Include.ARTIFACT_SERVICE))
        .thenReturn(DeploymentMetadata.builder().artifactRequiredServiceIds(asList(SERVICE_ID)).build());

    when(workflowExecutionService.listExecutions(any(PageRequest.class), anyBoolean()))
        .thenReturn(aPageResponse()
                        .withResponse(singletonList(
                            WorkflowExecution.builder().appId(APP_ID).executionArgs(executionArgs).build()))
                        .build());

    triggerService.triggerExecutionPostArtifactCollectionAsync(APP_ID, ARTIFACT_STREAM_ID, asList(artifact));

    verify(syncArtifactCollectionServiceImpl).collectNewArtifacts(APP_ID, ARTIFACT_STREAM_ID);
    verifyNoMoreInteractions(syncArtifactCollectionServiceImpl);
    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
    verify(artifactStreamService, times(4)).get(ARTIFACT_STREAM_ID);
    verify(artifactService).getArtifactByBuildNumber(artifactStream, ARTIFACT_FILTER, false);
    verify(workflowExecutionService).obtainLastGoodDeployedArtifacts(APP_ID, WORKFLOW_ID, SERVICE_ID);
    verify(workflowService, times(2)).readWorkflow(APP_ID, WORKFLOW_ID);
    verify(workflowService).fetchWorkflowName(APP_ID, WORKFLOW_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerExecutionPostPipelineCompletion() {
    Artifact artifact = prepareArtifact(ARTIFACT_ID);

    pipelineCondTrigger.setArtifactSelections(
        asList(ArtifactSelection.builder().type(PIPELINE_SOURCE).serviceId(SERVICE_ID).build(),
            ArtifactSelection.builder().serviceId(SERVICE_ID).type(LAST_DEPLOYED).pipelineId(PIPELINE_ID).build()));

    triggerService.save(pipelineCondTrigger);

    pipelineCompletionMocks(singletonList(artifact));

    when(workflowExecutionService.obtainLastGoodDeployedArtifacts(APP_ID, PIPELINE_ID, SERVICE_ID))
        .thenReturn(asList(artifact));

    triggerService.triggerExecutionPostPipelineCompletionAsync(APP_ID, PIPELINE_ID);
    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerExecutionPostPipelineCompletionForAllMatchedOnes() {
    Artifact artifact = prepareArtifact(ARTIFACT_ID);

    pipelineCondTrigger.setArtifactSelections(
        asList(ArtifactSelection.builder().type(PIPELINE_SOURCE).serviceId(SERVICE_ID).build(),
            ArtifactSelection.builder().serviceId(SERVICE_ID).type(LAST_DEPLOYED).pipelineId(PIPELINE_ID).build()));

    triggerService.save(pipelineCondTrigger);

    triggerService.save(Trigger.builder()
                            .workflowId(PIPELINE_ID)
                            .uuid(UUIDGenerator.generateUuid())
                            .appId(APP_ID)
                            .name(TRIGGER_NAME)
                            .condition(PipelineTriggerCondition.builder().pipelineId(PIPELINE_ID).build())
                            .build());

    pipelineCompletionMocks(singletonList(artifact));

    when(workflowExecutionService.obtainLastGoodDeployedArtifacts(APP_ID, PIPELINE_ID, SERVICE_ID))
        .thenReturn(asList(artifact));

    triggerService.triggerExecutionPostPipelineCompletionAsync(APP_ID, PIPELINE_ID);
    verify(workflowExecutionService, times(2))
        .triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotTriggerExecutionPostPipelineCompletion() {
    pipelineCompletionMocks(singletonList(artifact));

    triggerService.triggerExecutionPostPipelineCompletionAsync(APP_ID, PIPELINE_ID);
    verify(workflowExecutionService, times(0))
        .triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  private void pipelineCompletionMocks(List<Artifact> artifacts) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(artifacts);

    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());
    when(workflowExecutionService.listExecutions(any(PageRequest.class), anyBoolean()))
        .thenReturn(aPageResponse()
                        .withResponse(singletonList(
                            WorkflowExecution.builder().appId(APP_ID).executionArgs(executionArgs).build()))
                        .build());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerExecutionPostPipelineCompletionWithArtifactSelections() {
    Artifact artifact = prepareArtifact(ARTIFACT_ID);
    pipelineCondTrigger.setArtifactSelections(
        asList(ArtifactSelection.builder().serviceId(SERVICE_ID).type(PIPELINE_SOURCE).build(),
            ArtifactSelection.builder()
                .type(LAST_COLLECTED)
                .serviceId(SERVICE_ID)
                .artifactStreamId(ARTIFACT_STREAM_ID)
                .artifactFilter(ARTIFACT_FILTER)
                .build(),
            ArtifactSelection.builder().type(LAST_DEPLOYED).serviceId(SERVICE_ID).workflowId(PIPELINE_ID).build()));

    triggerService.save(pipelineCondTrigger);

    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactStream);
    when(artifactService.fetchLastCollectedApprovedArtifactForArtifactStream(artifactStream)).thenReturn(artifact);
    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());

    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(singletonList(artifact));
    when(workflowExecutionService.listExecutions(any(PageRequest.class), anyBoolean()))
        .thenReturn(aPageResponse()
                        .withResponse(singletonList(
                            WorkflowExecution.builder().appId(APP_ID).executionArgs(executionArgs).build()))
                        .build());

    triggerService.triggerExecutionPostPipelineCompletionAsync(APP_ID, PIPELINE_ID);
    verify(syncArtifactCollectionServiceImpl).collectNewArtifacts(APP_ID, ARTIFACT_STREAM_ID);
    verifyNoMoreInteractions(syncArtifactCollectionServiceImpl);
    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
    verify(artifactStreamService, times(3)).get(ARTIFACT_STREAM_ID);
    verify(artifactService).getArtifactByBuildNumber(artifactStream, ARTIFACT_FILTER, false);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerScheduledExecution() {
    Artifact artifact = prepareArtifact(ARTIFACT_ID);
    Artifact artifact2 = prepareArtifact(UUIDGenerator.generateUuid());

    scheduledTriggerMocks();

    when(workflowExecutionService.obtainLastGoodDeployedArtifacts(APP_ID, PIPELINE_ID, SERVICE_ID))
        .thenReturn(asList(artifact, artifact2));

    triggerService.save(scheduledConditionTrigger);

    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(Pipeline.builder().appId(APP_ID).build());

    triggerService.triggerScheduledExecutionAsync(scheduledConditionTrigger, new Date());

    verify(idempotentRegistry).create(any(), any(), any(), any());
    verify(workflowExecutionService, times(1))
        .triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotRunnDisabledTrigger() {
    Artifact artifact = prepareArtifact(ARTIFACT_ID);
    Artifact artifact2 = prepareArtifact(UUIDGenerator.generateUuid());

    scheduledTriggerMocks();

    when(workflowExecutionService.obtainLastGoodDeployedArtifacts(APP_ID, PIPELINE_ID, SERVICE_ID))
        .thenReturn(asList(artifact, artifact2));

    scheduledConditionTrigger.setDisabled(true);
    triggerService.save(scheduledConditionTrigger);

    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(Pipeline.builder().appId(APP_ID).build());

    triggerService.triggerScheduledExecutionAsync(scheduledConditionTrigger, new Date());

    verify(idempotentRegistry).create(any(), any(), any(), any());
    verify(workflowExecutionService, never())
        .triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotTriggerScheduledExecutionMissingArtifacts() {
    Artifact artifact = prepareArtifact(ARTIFACT_ID);
    scheduledTriggerMocks();
    when(workflowExecutionService.obtainLastGoodDeployedArtifacts(APP_ID, PIPELINE_ID, SERVICE_ID))
        .thenReturn(asList(artifact));

    triggerService.save(scheduledConditionTrigger);

    triggerService.triggerScheduledExecutionAsync(scheduledConditionTrigger, new Date());

    verify(idempotentRegistry).create(any(), any(), any(), any());
    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerScheduledExecutionIfNoArtifacts() {
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true))
        .thenReturn(Pipeline.builder().uuid(PIPELINE_ID).build());
    scheduledTriggerMocks();
    triggerService.save(scheduledConditionTrigger);

    triggerService.triggerScheduledExecutionAsync(scheduledConditionTrigger, new Date());

    verify(idempotentRegistry).create(any(), any(), any(), any());
    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerScheduledExecutionForBuildWorkflow() {
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID))
        .thenReturn(aWorkflow()
                        .name(WORKFLOW_NAME)
                        .uuid(WORKFLOW_ID)
                        .orchestrationWorkflow(aBuildOrchestrationWorkflow().build())
                        .build());

    triggerService.save(workflowScheduledConditionTrigger);

    triggerService.triggerScheduledExecutionAsync(workflowScheduledConditionTrigger, new Date());

    verify(idempotentRegistry).create(any(), any(), any(), any());
    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  private void scheduledTriggerMocks() {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(singletonList(artifact));

    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());
    when(workflowExecutionService.listExecutions(any(PageRequest.class), anyBoolean()))
        .thenReturn(aPageResponse()
                        .withResponse(singletonList(
                            WorkflowExecution.builder().appId(APP_ID).executionArgs(executionArgs).build()))
                        .build());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerScheduledExecutionWithArtifactSelections() {
    Artifact artifact = prepareArtifact(ARTIFACT_ID);

    scheduledConditionTrigger.setArtifactSelections(asList(ArtifactSelection.builder()
                                                               .type(LAST_COLLECTED)
                                                               .serviceId(SERVICE_ID)
                                                               .artifactStreamId(ARTIFACT_STREAM_ID)
                                                               .artifactFilter(ARTIFACT_FILTER)
                                                               .build(),
        ArtifactSelection.builder().type(LAST_DEPLOYED).serviceId(SERVICE_ID).workflowId(PIPELINE_ID).build()));

    triggerService.save(scheduledConditionTrigger);

    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactStream);
    when(artifactService.fetchLastCollectedApprovedArtifactForArtifactStream(artifactStream)).thenReturn(artifact);
    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());

    triggerService.triggerScheduledExecutionAsync(scheduledConditionTrigger, new Date());
    verify(syncArtifactCollectionServiceImpl).collectNewArtifacts(APP_ID, ARTIFACT_STREAM_ID);
    verifyNoMoreInteractions(syncArtifactCollectionServiceImpl);
    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
    verify(artifactStreamService, times(3)).get(ARTIFACT_STREAM_ID);
    verify(artifactService).getArtifactByBuildNumber(artifactStream, ARTIFACT_FILTER, false);
    verify(idempotentRegistry).create(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldTriggerPipelineExecutionByWebhook() {
    when(webhookTriggerProcessor.checkFileContentOptionSelected(any())).thenReturn(true);
    triggerService.save(pipelineWebhookConditionTriggerWithFileContentChanged);

    triggerService.triggerExecutionByWebHook(APP_ID,
        pipelineWebhookConditionTriggerWithFileContentChanged.getWebHookToken(),
        ImmutableMap.of(SERVICE_ID, ArtifactSummary.builder().buildNo(BUILD_NUMBER).build()), Collections.emptyMap(),
        TriggerExecution.builder().build(), new HashMap<>());

    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldTriggerPipelineExecutionByWebhookWithLastExecution() {
    when(webhookTriggerProcessor.checkFileContentOptionSelected(any())).thenReturn(true);
    TriggerExecution lastTriggerExecution =
        getLastTriggerExecution(pipelineWebhookConditionTriggerWithFileContentChanged);
    when(webhookTriggerProcessor.fetchLastExecutionForContentChanged(any())).thenReturn(lastTriggerExecution);
    triggerService.save(pipelineWebhookConditionTriggerWithFileContentChanged);

    triggerService.triggerExecutionByWebHook(APP_ID,
        pipelineWebhookConditionTriggerWithFileContentChanged.getWebHookToken(),
        ImmutableMap.of(SERVICE_ID, ArtifactSummary.builder().buildNo(BUILD_NUMBER).build()), Collections.emptyMap(),
        TriggerExecution.builder().build(), new HashMap<>());

    verify(webhookTriggerProcessor)
        .initiateTriggerContentChangeDelegateTask(
            any(Trigger.class), any(TriggerExecution.class), any(TriggerExecution.class), any());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldTriggerWorkflowExecutionByWebhook() {
    when(webhookTriggerProcessor.checkFileContentOptionSelected(any())).thenReturn(true);
    triggerService.save(workflowWebhookConditionTriggerWithFileContentChanged);

    triggerService.triggerExecutionByWebHook(APP_ID,
        workflowWebhookConditionTriggerWithFileContentChanged.getWebHookToken(),
        ImmutableMap.of(SERVICE_ID, ArtifactSummary.builder().buildNo(BUILD_NUMBER).build()), Collections.emptyMap(),
        TriggerExecution.builder().build(), new HashMap<>());

    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldTriggerWorkflowExecutionByWebhookWithLastExecution() {
    when(webhookTriggerProcessor.checkFileContentOptionSelected(any())).thenReturn(true);
    TriggerExecution lastTriggerExecution =
        getLastTriggerExecution(workflowWebhookConditionTriggerWithFileContentChanged);
    when(webhookTriggerProcessor.fetchLastExecutionForContentChanged(any())).thenReturn(lastTriggerExecution);
    triggerService.save(workflowWebhookConditionTriggerWithFileContentChanged);

    triggerService.triggerExecutionByWebHook(APP_ID,
        workflowWebhookConditionTriggerWithFileContentChanged.getWebHookToken(),
        ImmutableMap.of(SERVICE_ID, ArtifactSummary.builder().buildNo(BUILD_NUMBER).build()), Collections.emptyMap(),
        TriggerExecution.builder().build(), new HashMap<>());

    verify(webhookTriggerProcessor)
        .initiateTriggerContentChangeDelegateTask(
            any(Trigger.class), any(TriggerExecution.class), any(TriggerExecution.class), any());
  }

  private TriggerExecution getLastTriggerExecution(Trigger trigger) {
    return TriggerExecution.builder()
        .triggerId(trigger.getUuid())
        .appId(trigger.getAppId())
        .status(TriggerExecution.Status.SUCCESS)
        .workflowExecutionId(trigger.getWorkflowId())
        .build();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerArtifactCollectionForWebhookTrigger() {
    Artifact artifact = prepareArtifact(ARTIFACT_ID);

    setArtifactSelectionsForWebhookTrigger();

    triggerService.save(webhookConditionTrigger);

    JenkinsArtifactStream jenkinsArtifactStream = buildJenkinsArtifactStream();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(jenkinsArtifactStream);
    when(artifactService.fetchLatestArtifactForArtifactStream(artifactStream)).thenReturn(artifact);
    when(artifactService.getArtifactByBuildNumber(artifactStream, BUILD_NUMBER, false))
        .thenReturn(null)
        .thenReturn(artifact);

    when(artifactCollectionServiceAsync.collectNewArtifacts(APP_ID, jenkinsArtifactStream, BUILD_NUMBER))
        .thenReturn(artifact);

    triggerService.triggerExecutionByWebHook(APP_ID, webhookConditionTrigger.getWebHookToken(),
        ImmutableMap.of(SERVICE_ID, ArtifactSummary.builder().buildNo(BUILD_NUMBER).build()), Collections.emptyMap(),
        null, new HashMap<>());

    verify(syncArtifactCollectionServiceImpl, times(2)).collectNewArtifacts(APP_ID, ARTIFACT_STREAM_ID);
    verifyNoMoreInteractions(syncArtifactCollectionServiceImpl);
    verify(artifactCollectionServiceAsync).collectNewArtifacts(APP_ID, jenkinsArtifactStream, BUILD_NUMBER);
    verify(artifactService).fetchLastCollectedArtifact(artifactStream);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldNotTriggerExecutionByWebhookWithoutBuildNumber() {
    setArtifactSelectionsForWebhookTrigger();
    triggerService.save(webhookConditionTrigger);

    triggerService.triggerExecutionByWebHook(APP_ID, webhookConditionTrigger.getWebHookToken(),
        ImmutableMap.of(SERVICE_ID, ArtifactSummary.builder().buildNo(null).build()), Collections.emptyMap(), null,
        new HashMap<>());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldNotTriggerExecutionByWebhookWithWrongServiceId() {
    setArtifactSelectionsForWebhookTrigger();
    triggerService.save(webhookConditionTrigger);

    triggerService.triggerExecutionByWebHook(APP_ID, webhookConditionTrigger.getWebHookToken(),
        ImmutableMap.of("WrongServiceId", ArtifactSummary.builder().buildNo(BUILD_NUMBER).build()),
        Collections.emptyMap(), null, new HashMap<>());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldNotTriggerExecutionByWebhookWithoutArtifactStreamIds() {
    setArtifactSelectionsForWebhookTriggerWithoutStreamId();
    triggerService.save(webhookConditionTrigger);

    triggerService.triggerExecutionByWebHook(APP_ID, webhookConditionTrigger.getWebHookToken(),
        ImmutableMap.of(SERVICE_ID, ArtifactSummary.builder().buildNo(BUILD_NUMBER).name(null).build()),
        Collections.emptyMap(), null, new HashMap<>());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldNotTriggerExecutionByWebhookWithMoreThanOneArtifactStreamId() {
    when(artifactStreamService.fetchArtifactStreamIdsForService(any(), any()))
        .thenReturn(Arrays.asList(SERVICE_ID, SERVICE_ID.concat("_2")));
    setArtifactSelectionsForWebhookTriggerWithoutStreamId();
    triggerService.save(webhookConditionTrigger);

    triggerService.triggerExecutionByWebHook(APP_ID, webhookConditionTrigger.getWebHookToken(),
        ImmutableMap.of(SERVICE_ID, ArtifactSummary.builder().buildNo(BUILD_NUMBER).name(null).build()),
        Collections.emptyMap(), null, new HashMap<>());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldNotTriggerExecutionByWebhookWithWrongArtifactStreamId() {
    when(artifactStreamService.fetchArtifactStreamIdsForService(any(), any())).thenReturn(Arrays.asList(SERVICE_ID));
    setArtifactSelectionsForWebhookTriggerWithoutStreamId();
    triggerService.save(webhookConditionTrigger);

    triggerService.triggerExecutionByWebHook(APP_ID, webhookConditionTrigger.getWebHookToken(),
        ImmutableMap.of(SERVICE_ID, ArtifactSummary.builder().buildNo(BUILD_NUMBER).name(null).build()),
        Collections.emptyMap(), null, new HashMap<>());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerExecutionByWebhookWithNoBuildNumber() {
    Artifact artifact = prepareArtifact(ARTIFACT_ID);
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(singletonList(artifact));

    setArtifactSelectionsForWebhookTrigger();

    triggerService.save(webhookConditionTrigger);

    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactStream);
    when(artifactService.fetchLastCollectedApprovedArtifactForArtifactStream(artifactStream)).thenReturn(artifact);
    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());
    when(workflowExecutionService.listExecutions(any(PageRequest.class), anyBoolean()))
        .thenReturn(aPageResponse()
                        .withResponse(singletonList(
                            WorkflowExecution.builder().appId(APP_ID).executionArgs(executionArgs).build()))
                        .build());
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(buildJenkinsArtifactStream());
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(buildPipeline());
    when(artifactService.getArtifactByBuildNumber(artifactStream, "123", false)).thenReturn(artifact);

    triggerService.triggerExecutionByWebHook(APP_ID, webhookConditionTrigger.getWebHookToken(),
        ImmutableMap.of(SERVICE_ID, ArtifactSummary.builder().buildNo("123").build()), Collections.emptyMap(), null,
        new HashMap<>());

    verify(syncArtifactCollectionServiceImpl, times(2)).collectNewArtifacts(APP_ID, ARTIFACT_STREAM_ID);
    verifyNoMoreInteractions(syncArtifactCollectionServiceImpl);
    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
    verify(artifactStreamService, times(11)).get(ARTIFACT_STREAM_ID);
    verify(artifactService).getArtifactByBuildNumber(artifactStream, ARTIFACT_FILTER, false);
  }

  private void setArtifactSelectionsForWebhookTrigger() {
    webhookConditionTrigger.setArtifactSelections(asList(ArtifactSelection.builder()
                                                             .type(WEBHOOK_VARIABLE)
                                                             .serviceId(SERVICE_ID)
                                                             .artifactStreamId(ARTIFACT_STREAM_ID)
                                                             .artifactSourceName(ARTIFACT_SOURCE_NAME)
                                                             .build(),
        ArtifactSelection.builder()
            .type(LAST_COLLECTED)
            .serviceId(SERVICE_ID)
            .artifactStreamId(ARTIFACT_STREAM_ID)
            .artifactFilter(ARTIFACT_FILTER)
            .artifactSourceName(ARTIFACT_SOURCE_NAME)
            .build(),
        ArtifactSelection.builder()
            .type(LAST_COLLECTED)
            .serviceId(SERVICE_ID)
            .artifactStreamId(ARTIFACT_STREAM_ID)
            .artifactSourceName(ARTIFACT_SOURCE_NAME)
            .build(),
        ArtifactSelection.builder().type(LAST_DEPLOYED).serviceId(SERVICE_ID).workflowId(PIPELINE_ID).build()));
  }

  private void setArtifactSelectionsForWebhookTriggerWithoutStreamId() {
    webhookConditionTrigger.setArtifactSelections(asList(ArtifactSelection.builder()
                                                             .type(WEBHOOK_VARIABLE)
                                                             .serviceId(SERVICE_ID)
                                                             .artifactSourceName(ARTIFACT_SOURCE_NAME)
                                                             .build()));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerExecutionByWebhookWithBuildNumber() {
    Artifact artifact = prepareArtifact(ARTIFACT_ID);
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(singletonList(artifact));

    setArtifactSelectionsForWebhookTrigger();

    triggerService.save(webhookConditionTrigger);

    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactStream);
    when(artifactService.fetchLastCollectedApprovedArtifactForArtifactStream(artifactStream)).thenReturn(artifact);
    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());
    when(workflowExecutionService.listExecutions(any(PageRequest.class), anyBoolean()))
        .thenReturn(aPageResponse()
                        .withResponse(singletonList(
                            WorkflowExecution.builder().appId(APP_ID).executionArgs(executionArgs).build()))
                        .build());
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactStream);
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(buildPipeline());
    when(artifactService.getArtifactByBuildNumber(artifactStream, "123", false)).thenReturn(artifact);

    triggerService.triggerExecutionByWebHook(APP_ID, webhookConditionTrigger.getWebHookToken(),
        ImmutableMap.of(SERVICE_ID, ArtifactSummary.builder().buildNo("123").build()), Collections.emptyMap(), null,
        new HashMap<>());

    verify(syncArtifactCollectionServiceImpl, times(2)).collectNewArtifacts(APP_ID, ARTIFACT_STREAM_ID);
    verifyNoMoreInteractions(syncArtifactCollectionServiceImpl);
    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
    verify(artifactStreamService, times(11)).get(ARTIFACT_STREAM_ID);
    verify(artifactService).getArtifactByBuildNumber(artifactStream, ARTIFACT_FILTER, false);

    verify(workflowExecutionService).obtainLastGoodDeployedArtifacts(APP_ID, PIPELINE_ID, SERVICE_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerWorkflowExecutionByWebhookWithBuildNumber() {
    Artifact artifact = prepareArtifact(ARTIFACT_ID);
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(singletonList(artifact));

    workflowWebhookConditionTrigger.setArtifactSelections(asList(ArtifactSelection.builder()
                                                                     .type(WEBHOOK_VARIABLE)
                                                                     .serviceId(SERVICE_ID)
                                                                     .artifactStreamId(ARTIFACT_STREAM_ID)
                                                                     .build(),
        ArtifactSelection.builder()
            .type(LAST_COLLECTED)
            .serviceId(SERVICE_ID)
            .artifactStreamId(ARTIFACT_STREAM_ID)
            .artifactFilter(ARTIFACT_FILTER)
            .build(),
        ArtifactSelection.builder().type(LAST_DEPLOYED).serviceId(SERVICE_ID).workflowId(WORKFLOW_ID).build()));

    when(workflowService.readWorkflowWithoutOrchestration(APP_ID, WORKFLOW_ID)).thenReturn(workflow);

    workflowWebhookConditionTrigger = triggerService.save(workflowWebhookConditionTrigger);

    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactStream);
    when(artifactService.fetchLastCollectedApprovedArtifactForArtifactStream(artifactStream)).thenReturn(artifact);
    when(workflowExecutionService.triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class)))
        .thenReturn(WorkflowExecution.builder().appId(APP_ID).status(SUCCESS).build());
    when(workflowExecutionService.listExecutions(any(PageRequest.class), anyBoolean()))
        .thenReturn(aPageResponse()
                        .withResponse(singletonList(
                            WorkflowExecution.builder().appId(APP_ID).executionArgs(executionArgs).build()))
                        .build());
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactStream);
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(buildPipeline());
    when(artifactService.getArtifactByBuildNumber(artifactStream, "123", false)).thenReturn(artifact);

    when(workflowService.fetchDeploymentMetadata(APP_ID, workflow,
             workflowWebhookConditionTrigger.getWorkflowVariables(), null, null, Include.ARTIFACT_SERVICE))
        .thenReturn(DeploymentMetadata.builder().artifactRequiredServiceIds(asList(SERVICE_ID)).build());

    triggerService.triggerExecutionByWebHook(APP_ID, workflowWebhookConditionTrigger.getWebHookToken(),
        ImmutableMap.of(SERVICE_ID, ArtifactSummary.builder().buildNo("123").build()), Collections.emptyMap(), null,
        new HashMap<>());

    verify(syncArtifactCollectionServiceImpl).collectNewArtifacts(APP_ID, ARTIFACT_STREAM_ID);
    verifyNoMoreInteractions(syncArtifactCollectionServiceImpl);
    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
    verify(artifactStreamService, times(7)).get(ARTIFACT_STREAM_ID);
    verify(artifactService).getArtifactByBuildNumber(artifactStream, ARTIFACT_FILTER, false);

    verify(workflowExecutionService).obtainLastGoodDeployedArtifacts(APP_ID, WORKFLOW_ID, SERVICE_ID);
    verify(workflowService, times(4)).readWorkflow(APP_ID, WORKFLOW_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetTriggersHasPipelineAction() {
    pipelineCondTrigger.setArtifactSelections(
        asList(ArtifactSelection.builder().serviceId(SERVICE_ID).type(PIPELINE_SOURCE).build(),
            ArtifactSelection.builder()
                .type(LAST_COLLECTED)
                .serviceId(SERVICE_ID)
                .artifactStreamId(ARTIFACT_STREAM_ID)
                .artifactFilter(ARTIFACT_FILTER)
                .build(),
            ArtifactSelection.builder().type(LAST_DEPLOYED).serviceId(SERVICE_ID).workflowId(PIPELINE_ID).build()));

    triggerService.save(pipelineCondTrigger);

    List<Trigger> triggersHasPipelineAction = triggerService.getTriggersHasPipelineAction(APP_ID, PIPELINE_ID);
    assertThat(triggersHasPipelineAction).isNotEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetTriggersHasArtifactStreamAction() {
    artifactConditionTrigger.setArtifactSelections(
        asList(ArtifactSelection.builder().serviceId(SERVICE_ID).type(PIPELINE_SOURCE).build(),
            ArtifactSelection.builder()
                .type(LAST_COLLECTED)
                .serviceId(SERVICE_ID)
                .artifactStreamId(ARTIFACT_STREAM_ID)
                .artifactFilter(ARTIFACT_FILTER)
                .build(),
            ArtifactSelection.builder().type(LAST_DEPLOYED).serviceId(SERVICE_ID).workflowId(PIPELINE_ID).build()));

    triggerService.save(artifactConditionTrigger);

    List<Trigger> triggersHasArtifactStreamAction =
        triggerService.getTriggersHasArtifactStreamAction(APP_ID, ARTIFACT_STREAM_ID);

    assertThat(triggersHasArtifactStreamAction).isNotEmpty();
  }

  @Test(expected = WingsException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldValidateArtifactStreamSelections() {
    webhookConditionTrigger.setArtifactSelections(
        asList(ArtifactSelection.builder().type(LAST_COLLECTED).artifactFilter(ARTIFACT_FILTER).build(),
            ArtifactSelection.builder().type(LAST_DEPLOYED).build()));

    triggerService.save(webhookConditionTrigger);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldValidateUpdateArtifactStreamSelections() {
    webhookConditionTrigger.setArtifactSelections(
        asList(ArtifactSelection.builder().type(LAST_COLLECTED).artifactFilter(ARTIFACT_FILTER).build(),
            ArtifactSelection.builder().type(LAST_DEPLOYED).workflowId(PIPELINE_ID).build()));

    triggerService.update(webhookConditionTrigger, false);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldListPipelineWebhookParameters() {
    setPipelineStages(pipeline);
    pipeline.getPipelineVariables().add(aVariable().name("MyVar").build());

    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(pipeline);

    WebhookParameters webhookParameters =
        triggerService.listWebhookParameters(APP_ID, PIPELINE_ID, PIPELINE, BITBUCKET, PULL_REQUEST);
    assertThat(webhookParameters.getParams()).isEmpty();
    assertThat(webhookParameters.getExpressions()).isNotNull().contains(WebhookParameters.PULL_REQUEST_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldListWorkflowWebhookParameters() {
    WebhookParameters webhookParameters =
        triggerService.listWebhookParameters(APP_ID, WORKFLOW_ID, ORCHESTRATION, BITBUCKET, PULL_REQUEST);
    assertThat(webhookParameters.getExpressions()).isNotNull().contains(WebhookParameters.PULL_REQUEST_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldListWorkflowGitWebhookParameters() {
    WebhookParameters webhookParameters =
        triggerService.listWebhookParameters(APP_ID, WORKFLOW_ID, ORCHESTRATION, GITHUB, PULL_REQUEST);
    assertThat(webhookParameters.getExpressions()).isNotNull().contains(WebhookParameters.GH_PR_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldSaveNewInstanceTrigger() {
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID))
        .thenReturn(anAwsInfrastructureMapping()
                        .withUuid(INFRA_MAPPING_ID)
                        .withServiceId(SERVICE_ID)
                        .withDeploymentType(SSH.name())
                        .withComputeProviderType("AWS")
                        .build());

    Trigger trigger = triggerService.save(newInstanceTrigger);
    assertThat(trigger.getUuid()).isEqualTo(TRIGGER_ID);
    assertThat(trigger.getCondition()).isInstanceOf(NewInstanceTriggerCondition.class);
    assertThat(trigger.getServiceInfraWorkflows()).isNotNull();
    verify(infrastructureMappingService).get(APP_ID, INFRA_MAPPING_ID);
    verify(workflowService, times(2)).readWorkflow(APP_ID, WORKFLOW_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerExecutionByServiceInfra() {
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID))
        .thenReturn(anAwsInfrastructureMapping()
                        .withUuid(INFRA_MAPPING_ID)
                        .withServiceId(SERVICE_ID)
                        .withEnvId(ENV_ID)
                        .withDeploymentType(SSH.name())
                        .withComputeProviderType("AWS")
                        .build());

    triggerService.save(newInstanceTrigger);

    when(workflowExecutionService.fetchWorkflowExecution(APP_ID, asList(SERVICE_ID), asList(ENV_ID), WORKFLOW_ID))
        .thenReturn(WorkflowExecution.builder().build());
    assertThat(triggerService.triggerExecutionByServiceInfra(APP_ID, INFRA_MAPPING_ID)).isTrue();
    verify(infrastructureMappingService, times(2)).get(APP_ID, INFRA_MAPPING_ID);
    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldEnvironmentReferencedByTrigger() {
    artifactConditionTrigger.setWorkflowVariables(ImmutableMap.of("Environment", ENV_ID));
    triggerService.save(artifactConditionTrigger);
    List<String> triggerNames = triggerService.obtainTriggerNamesReferencedByTemplatedEntityId(APP_ID, ENV_ID);
    assertThat(triggerNames).isNotEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerWorkflowExecutionByBitBucketWebhook() {
    when(workflowService.readWorkflowWithoutOrchestration(APP_ID, WORKFLOW_ID)).thenReturn(workflow);

    WebHookTriggerCondition webHookTriggerCondition =
        (WebHookTriggerCondition) workflowWebhookConditionTrigger.getCondition();

    webHookTriggerCondition.setWebhookSource(WebhookSource.BITBUCKET);
    webHookTriggerCondition.setEventTypes(Arrays.asList(WebhookEventType.PULL_REQUEST));

    Trigger savedTrigger = triggerService.save(workflowWebhookConditionTrigger);
    assertThat(savedTrigger).isNotNull();
    assertThat(savedTrigger.getWorkflowVariables()).isNotEmpty().containsKey("MyVar");

    Map<String, String> parameters = new HashMap<>();
    parameters.put("MyVar", "MyValue");

    triggerService.triggerExecutionByWebHook(workflowWebhookConditionTrigger, parameters, null);

    verify(workflowService).fetchDeploymentMetadata(any(), any(Workflow.class), anyMap(), any(), any(), any());

    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerTemplatedWorkflowExecutionByManualTrigger() {
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(
            InfrastructureDefinition.builder().infrastructure(AwsInstanceInfrastructure.builder().build()).build());
    setTemplatedWorkflow();
    Map<String, String> workflowVariables = new HashMap<>();
    workflowVariables.put("Environment", ENV_NAME);
    workflowVariables.put("Service", SERVICE_ID);
    workflowVariables.put("InfraDef_Ssh", INFRA_DEFINITION_ID);

    Map<String, String> parameters = new HashMap<>();
    parameters.put("MyVar", "MyValue");

    workflowWebhookConditionTrigger.setArtifactSelections(
        asList(ArtifactSelection.builder().type(WEBHOOK_VARIABLE).serviceId(SERVICE_ID).build()));

    workflowWebhookConditionTrigger.setWorkflowVariables(workflowVariables);

    WebHookTriggerCondition webHookTriggerCondition =
        (WebHookTriggerCondition) workflowWebhookConditionTrigger.getCondition();

    webHookTriggerCondition.setWebhookSource(WebhookSource.BITBUCKET);
    webHookTriggerCondition.setEventTypes(Arrays.asList(WebhookEventType.PULL_REQUEST));

    Trigger trigger = triggerService.save(workflowWebhookConditionTrigger);

    assertThat(trigger).isNotNull();

    when(artifactStreamService.fetchArtifactStreamIdsForService(APP_ID, SERVICE_ID))
        .thenReturn(Arrays.asList(ARTIFACT_STREAM_ID));
    triggerService.triggerExecutionByWebHook(APP_ID, trigger.getWebHookToken(),
        ImmutableMap.of(SERVICE_ID, ArtifactSummary.builder().buildNo(BUILD_NO).build()), Collections.emptyMap(),
        TriggerExecution.builder().build(), parameters);

    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
    verify(environmentService).getEnvironmentByName(APP_ID, ENV_NAME, false);
    verify(infrastructureDefinitionService).get(APP_ID, INFRA_DEFINITION_ID);
    verify(serviceResourceService).get(APP_ID, SERVICE_ID, false);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerTemplatedWorkflowExecutionByBitBucketWebhook() {
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(
            InfrastructureDefinition.builder().infrastructure(AwsInstanceInfrastructure.builder().build()).build());
    setTemplatedWorkflow();

    Map<String, String> workflowVariables = new HashMap<>();
    workflowVariables.put("Environment", ENV_NAME);
    workflowVariables.put("Service", SERVICE_ID);
    workflowVariables.put("InfraDef_Ssh", INFRA_DEFINITION_ID);

    workflowWebhookConditionTrigger.setWorkflowVariables(workflowVariables);

    WebHookTriggerCondition webHookTriggerCondition =
        (WebHookTriggerCondition) workflowWebhookConditionTrigger.getCondition();

    webHookTriggerCondition.setWebhookSource(WebhookSource.BITBUCKET);
    webHookTriggerCondition.setEventTypes(Arrays.asList(WebhookEventType.PULL_REQUEST));

    Trigger trigger = triggerService.save(workflowWebhookConditionTrigger);

    Map<String, String> parameters = new HashMap<>();
    parameters.put("MyVar", "MyValue");

    triggerService.triggerExecutionByWebHook(trigger, parameters, null);

    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
    verify(environmentService).getEnvironmentByName(APP_ID, ENV_NAME, false);
    verify(infrastructureDefinitionService).get(APP_ID, INFRA_DEFINITION_ID);
    verify(serviceResourceService).get(APP_ID, SERVICE_ID, false);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldTriggerTemplatedWorkflowExecutionWithoutArtifactSelection() {
    when(artifactStreamService.fetchArtifactStreamIdsForService(APP_ID, SERVICE_ID))
        .thenReturn(Arrays.asList(ARTIFACT_STREAM_ID));
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(
            InfrastructureDefinition.builder().infrastructure(AwsInstanceInfrastructure.builder().build()).build());

    Map<String, String> workflowVariables = new HashMap<>();
    workflowVariables.put("Environment", ENV_NAME);
    workflowVariables.put("InfraDef_Ssh", INFRA_DEFINITION_ID);
    workflowVariables.put("Service", SERVICE_ID);
    setTemplatedWorkflow();

    Map<String, String> parameters = new HashMap<>();
    parameters.put("MyVar", "MyValue");

    workflowWebhookConditionTrigger.setWorkflowVariables(workflowVariables);

    WebHookTriggerCondition webHookTriggerCondition =
        (WebHookTriggerCondition) workflowWebhookConditionTrigger.getCondition();

    webHookTriggerCondition.setEventTypes(Arrays.asList(WebhookEventType.PULL_REQUEST));

    webHookTriggerCondition.setWebhookSource(WebhookSource.BITBUCKET);

    Trigger trigger = triggerService.save(workflowWebhookConditionTrigger);

    assertThat(trigger).isNotNull();

    triggerService.triggerExecutionByWebHook(APP_ID, trigger.getWebHookToken(),
        ImmutableMap.of(SERVICE_ID, ArtifactSummary.builder().buildNo(BUILD_NO).build()), Collections.emptyMap(),
        TriggerExecution.builder().build(), parameters);

    verify(environmentService).getEnvironmentByName(APP_ID, ENV_NAME, false);

    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));

    verify(serviceResourceService).get(APP_ID, SERVICE_ID, false);
    verify(infrastructureDefinitionService).get(APP_ID, INFRA_DEFINITION_ID);
  }

  private void setTemplatedWorkflow() {
    workflow.setTemplateExpressions(asList(TemplateExpression.builder()
                                               .fieldName("envId")
                                               .expression("${Environment}")
                                               .metadata(ImmutableMap.of("entityType", "ENVIRONMENT"))
                                               .build(),
        TemplateExpression.builder()
            .fieldName("infraDefinitionId")
            .expression("${InfraDef_SSH}")
            .metadata(ImmutableMap.of("entityType", "INFRASTRUCTURE_DEFINITION"))

            .build(),
        TemplateExpression.builder()
            .fieldName("serviceId")
            .expression("${Service}")
            .metadata(ImmutableMap.of("entityType", "SERVICE"))
            .build()));

    workflow.getOrchestrationWorkflow().getUserVariables().add(
        aVariable().entityType(ENVIRONMENT).name("Environment").build());
    workflow.getOrchestrationWorkflow().getUserVariables().add(aVariable().entityType(SERVICE).name("Service").build());
    workflow.getOrchestrationWorkflow().getUserVariables().add(
        aVariable().entityType(INFRASTRUCTURE_DEFINITION).name("InfraDef_Ssh").build());
    when(workflowService.readWorkflowWithoutOrchestration(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    when(environmentService.getEnvironmentByName(APP_ID, ENV_NAME, false))
        .thenReturn(anEnvironment().appId(APP_ID).uuid(ENV_ID).build());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerTemplatedPipelineExecutionByBitBucketWebhook() {
    Map<String, String> variables = new HashMap<>();
    variables.put("ENV", ENV_ID);
    variables.put("SERVICE", SERVICE_NAME);
    variables.put("INFRA", INFRA_NAME);

    webhookConditionTrigger.setWorkflowVariables(variables);

    WebHookTriggerCondition webHookTriggerCondition =
        (WebHookTriggerCondition) workflowWebhookConditionTrigger.getCondition();

    webHookTriggerCondition.setWebhookSource(WebhookSource.BITBUCKET);
    webHookTriggerCondition.setEventTypes(Arrays.asList(WebhookEventType.PULL_REQUEST));

    triggerService.save(webhookConditionTrigger);

    final Pipeline pipeline = Pipeline.builder().appId(APP_ID).uuid(PIPELINE_ID).build();

    pipeline.getPipelineVariables().add(aVariable().entityType(ENVIRONMENT).name("ENV").build());
    pipeline.getPipelineVariables().add(aVariable().entityType(SERVICE).name("SERVICE").build());
    pipeline.getPipelineVariables().add(aVariable().entityType(INFRASTRUCTURE_DEFINITION).name("INFRA").build());

    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(pipeline);

    when(environmentService.getEnvironmentByName(APP_ID, ENV_NAME, false))
        .thenReturn(anEnvironment().appId(APP_ID).uuid(ENV_ID).build());
    when(serviceResourceService.getServiceByName(APP_ID, SERVICE_NAME, false))
        .thenReturn(Service.builder().name(SERVICE_NAME).build());
    when(infrastructureDefinitionService.getInfraDefByName(APP_ID, ENV_ID, INFRA_NAME))
        .thenReturn(InfrastructureDefinition.builder()
                        .uuid(INFRA_DEFINITION_ID)
                        .deploymentType(SSH)
                        .cloudProviderType(AWS)
                        .infrastructure(AwsInstanceInfrastructure.builder().build())
                        .build());

    Map<String, String> parameters = new HashMap<>();
    parameters.put("MyVar", "MyValue");
    parameters.put("ENV", ENV_NAME);

    triggerService.triggerExecutionByWebHook(webhookConditionTrigger, parameters, null);

    verify(workflowExecutionService).triggerEnvExecution(any(), any(), any(ExecutionArgs.class), any(Trigger.class));
    verify(environmentService).getEnvironmentByName(APP_ID, ENV_NAME, false);
    verify(infrastructureDefinitionService).getInfraDefByName(APP_ID, ENV_ID, INFRA_NAME);
    verify(serviceResourceService).getServiceByName(APP_ID, SERVICE_NAME, false);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCheckTemplatedEntityReferenced() {
    setTemplatedWorkflow();

    Map<String, String> workflowVariables = new HashMap<>();
    workflowVariables.put("Environment", ENV_NAME);
    workflowVariables.put("Service", SERVICE_ID);
    workflowVariables.put("ServiceInfra_Ssh", INFRA_MAPPING_ID);

    workflowWebhookConditionTrigger.setWorkflowVariables(workflowVariables);

    WebHookTriggerCondition webHookTriggerCondition =
        (WebHookTriggerCondition) workflowWebhookConditionTrigger.getCondition();

    webHookTriggerCondition.setWebhookSource(WebhookSource.BITBUCKET);
    webHookTriggerCondition.setEventTypes(Arrays.asList(WebhookEventType.PULL_REQUEST));

    Trigger trigger = triggerService.save(workflowWebhookConditionTrigger);
    assertThat(triggerService.obtainTriggerNamesReferencedByTemplatedEntityId(trigger.getAppId(), SERVICE_ID))
        .isNotEmpty();
    assertThat(triggerService.obtainTriggerNamesReferencedByTemplatedEntityId(trigger.getAppId(), INFRA_MAPPING_ID))
        .isNotEmpty();
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void shouldGetInfraMappingByName() {
    InfrastructureMapping infrastructureMappingMocked = anAwsInfrastructureMapping()
                                                            .withUuid(INFRA_MAPPING_ID)
                                                            .withServiceId(SERVICE_ID)
                                                            .withDeploymentType(SSH.name())
                                                            .withComputeProviderType("AWS")
                                                            .build();
    when(infrastructureMappingService.getInfraMappingByName(APP_ID, ENV_ID, INFRA_NAME))
        .thenReturn(infrastructureMappingMocked);

    TriggerServiceImpl triggerService1 = (TriggerServiceImpl) triggerService;
    InfrastructureMapping infrastructureMapping = triggerService1.getInfrastructureMapping(APP_ID, ENV_ID, INFRA_NAME);
    assertThat(infrastructureMapping).isNotNull();
    assertThat(infrastructureMapping).isEqualTo(infrastructureMappingMocked);
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void shouldGetInfraDefinitionByName() {
    InfrastructureDefinition infrastructureDefinitionMocked =
        InfrastructureDefinition.builder()
            .infrastructure(GoogleKubernetesEngine.builder().namespace("test").build())
            .build();
    when(infrastructureDefinitionService.getInfraDefByName(APP_ID, ENV_ID, INFRA_NAME))
        .thenReturn(infrastructureDefinitionMocked);

    TriggerServiceImpl triggerService1 = (TriggerServiceImpl) triggerService;
    InfrastructureDefinition infrastructureDefinition =
        triggerService1.getInfrastructureDefinition(APP_ID, ENV_ID, INFRA_NAME);
    assertThat(infrastructureDefinition).isNotNull();
    assertThat(infrastructureDefinition).isEqualTo(infrastructureDefinitionMocked);
  }

  // after migration support old curl command test
  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void shouldResolveInfraDefOldPayloadAutoGeneratedName() {
    InfrastructureMapping infrastructureMappingMocked = anAwsInfrastructureMapping()
                                                            .withUuid(INFRA_MAPPING_ID)
                                                            .withServiceId(SERVICE_ID)
                                                            .withDeploymentType(SSH.name())
                                                            .withComputeProviderType("AWS")
                                                            .build();
    infrastructureMappingMocked.setInfrastructureDefinitionId(INFRA_DEFINITION_ID);

    Map<String, String> triggerWorkflowVariables = new HashMap<>();
    triggerWorkflowVariables.put("Environment", ENV_NAME);
    triggerWorkflowVariables.put("Service", SERVICE_NAME);
    triggerWorkflowVariables.put("ServiceInfra_ECS", INFRA_NAME);
    triggerWorkflowVariables.put("InfraDefinition_ECS", "${Infra}");

    List<Variable> workflowVariables =
        asList(aVariable().entityType(SERVICE).name("Service").value("Service 1").build(),
            aVariable().entityType(ENVIRONMENT).name("Environment").value("env 1").build(),
            aVariable().entityType(INFRASTRUCTURE_DEFINITION).name("InfraDefinition_ECS").value("${Infra}").build());

    when(infrastructureMappingService.getInfraMappingByName(APP_ID, ENV_ID, INFRA_NAME))
        .thenReturn(infrastructureMappingMocked);

    TriggerServiceImpl triggerService1 = (TriggerServiceImpl) triggerService;
    triggerService1.resolveInfraDefinitions(APP_ID, triggerWorkflowVariables, ENV_ID, workflowVariables);
    assertThat(triggerWorkflowVariables.get("InfraDefinition_ECS")).isNotNull();
    assertThat(triggerWorkflowVariables.get("InfraDefinition_ECS")).isEqualTo(INFRA_DEFINITION_ID);
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void shouldResolveInfraDefOldPayloadCustomName() {
    InfrastructureMapping infrastructureMappingMocked = anAwsInfrastructureMapping()
                                                            .withUuid(INFRA_MAPPING_ID)
                                                            .withServiceId(SERVICE_ID)
                                                            .withDeploymentType(SSH.name())
                                                            .withComputeProviderType("AWS")
                                                            .build();
    infrastructureMappingMocked.setInfrastructureDefinitionId(INFRA_DEFINITION_ID);

    Map<String, String> triggerWorkflowVariables = new HashMap<>();
    triggerWorkflowVariables.put("Environment", ENV_NAME);
    triggerWorkflowVariables.put("Service", SERVICE_NAME);
    triggerWorkflowVariables.put("Infra", INFRA_NAME);

    List<Variable> workflowVariables =
        asList(aVariable().entityType(SERVICE).name("Service").value("Service 1").build(),
            aVariable().entityType(ENVIRONMENT).name("Environment").value("env 1").build(),
            aVariable().entityType(INFRASTRUCTURE_DEFINITION).name("Infra").value("${Infra}").build());
    when(infrastructureMappingService.getInfraMappingByName(APP_ID, ENV_ID, INFRA_NAME))
        .thenReturn(infrastructureMappingMocked);

    TriggerServiceImpl triggerService1 = (TriggerServiceImpl) triggerService;
    triggerService1.resolveInfraDefinitions(APP_ID, triggerWorkflowVariables, ENV_ID, workflowVariables);
    assertThat(triggerWorkflowVariables.get("Infra")).isNotNull();
    assertThat(triggerWorkflowVariables.get("Infra")).isEqualTo(INFRA_DEFINITION_ID);
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void shouldResolveInfraDefNewPayloadCustomName() {
    Map<String, String> triggerWorkflowVariables = new HashMap<>();
    triggerWorkflowVariables.put("Environment", ENV_NAME);
    triggerWorkflowVariables.put("Service", SERVICE_NAME);
    triggerWorkflowVariables.put("Infra", INFRA_NAME);

    Map<String, Object> metadata = new HashMap<>();
    metadata.put(Variable.ENV_ID, ENV_ID);
    metadata.put(ENTITY_TYPE, INFRASTRUCTURE_DEFINITION);

    List<Variable> workflowVariables =
        asList(aVariable().entityType(SERVICE).name("Service").value("Service 1").build(),
            aVariable().entityType(ENVIRONMENT).name("Environment").value("env 1").build(),
            aVariable().name("Infra").value("${Infra}").metadata(metadata).build());

    InfrastructureDefinition infrastructureDefinitionMocked =
        InfrastructureDefinition.builder()
            .uuid(INFRA_DEFINITION_ID)
            .infrastructure(GoogleKubernetesEngine.builder().namespace("test").build())
            .build();
    when(infrastructureDefinitionService.getInfraDefByName(APP_ID, ENV_ID, INFRA_NAME))
        .thenReturn(infrastructureDefinitionMocked);

    TriggerServiceImpl triggerService1 = (TriggerServiceImpl) triggerService;
    triggerService1.resolveInfraDefinitions(APP_ID, triggerWorkflowVariables, ENV_ID, workflowVariables);
    assertThat(triggerWorkflowVariables.get("Infra")).isNotNull();
    assertThat(triggerWorkflowVariables.get("Infra")).isEqualTo(INFRA_DEFINITION_ID);
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void shouldResolveInfraDefNewPayloadAutoGeneratedName() {
    Map<String, String> triggerWorkflowVariables = new HashMap<>();
    triggerWorkflowVariables.put("Environment", ENV_NAME);
    triggerWorkflowVariables.put("Service", SERVICE_NAME);
    triggerWorkflowVariables.put("InfraDefinition_ECS", INFRA_NAME);

    List<Variable> workflowVariables =
        asList(aVariable().entityType(SERVICE).name("Service").value("Service 1").build(),
            aVariable().entityType(ENVIRONMENT).name("Environment").value("env 1").build(),
            aVariable().entityType(INFRASTRUCTURE_DEFINITION).name("InfraDefinition_ECS").value("${Infra}").build());

    InfrastructureDefinition infrastructureDefinitionMocked =
        InfrastructureDefinition.builder()
            .uuid(INFRA_DEFINITION_ID)
            .infrastructure(GoogleKubernetesEngine.builder().namespace("test").build())
            .build();
    when(infrastructureDefinitionService.getInfraDefByName(APP_ID, ENV_ID, INFRA_NAME))
        .thenReturn(infrastructureDefinitionMocked);

    TriggerServiceImpl triggerService1 = (TriggerServiceImpl) triggerService;
    triggerService1.resolveInfraDefinitions(APP_ID, triggerWorkflowVariables, ENV_ID, workflowVariables);
    assertThat(triggerWorkflowVariables.get("InfraDefinition_ECS")).isNotNull();
    assertThat(triggerWorkflowVariables.get("InfraDefinition_ECS")).isEqualTo(INFRA_DEFINITION_ID);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldListTriggersWithParameterizedStreams() {
    Trigger trigger1 = triggerService.save(artifactConditionTrigger);
    assertThat(trigger1).isNotNull();

    when(artifactStreamService.get(ARTIFACT_STREAM_ID_1)).thenReturn(buildNexusArtifactStream());
    when(artifactStreamServiceBindingService.getService(APP_ID, ARTIFACT_STREAM_ID_1, false))
        .thenReturn(Service.builder().uuid(SERVICE_ID).name(CATALOG_SERVICE_NAME).accountId(ACCOUNT_ID).build());

    Trigger t2 = Trigger.builder()
                     .workflowId(WORKFLOW_ID)
                     .workflowType(ORCHESTRATION)
                     .uuid("TRIGGER_ID_2")
                     .appId(APP_ID)
                     .name("TRIGGER_NAME_2")
                     .condition(WebHookTriggerCondition.builder()
                                    .webHookToken(WebHookToken.builder().build())
                                    .parameters(ImmutableMap.of("MyVar", "MyVal"))
                                    .build())
                     .build();
    t2.setArtifactSelections(asList(ArtifactSelection.builder()
                                        .type(WEBHOOK_VARIABLE)
                                        .serviceId(SERVICE_ID)
                                        .artifactStreamId(ARTIFACT_STREAM_ID_1)
                                        .build()));

    Trigger trigger2 = triggerService.save(t2);
    assertThat(trigger2).isNotNull();
    PageRequest<Trigger> pageRequest = new PageRequest<>();
    PageResponse<Trigger> triggers = triggerService.list(pageRequest, false, null);
    assertThat(triggers.size()).isEqualTo(2);
    for (Trigger trigger : triggers) {
      if (trigger.getUuid().equals("TRIGGER_ID_2")) {
        assertThat(trigger.getArtifactSelections().get(0).getUiDisplayName())
            .isEqualTo("testNexus (requires values on runtime)");
      }
    }
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldTriggerArtifactCollectionForWebhookTriggerWithParameterizedArtifactStream() {
    Artifact artifact = prepareArtifact(ARTIFACT_ID, ARTIFACT_STREAM_ID_1);
    NexusArtifactStream nexusArtifactStream = buildNexusArtifactStream();
    nexusArtifactStream.setSourceName(ARTIFACT_SOURCE_NAME);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID_1)).thenReturn(nexusArtifactStream);
    when(artifactStreamServiceBindingService.getService(APP_ID, ARTIFACT_STREAM_ID_1, false))
        .thenReturn(Service.builder().uuid(SERVICE_ID).name(CATALOG_SERVICE_NAME).build());
    webhookConditionTrigger.setArtifactSelections(asList(ArtifactSelection.builder()
                                                             .type(WEBHOOK_VARIABLE)
                                                             .serviceId(SERVICE_ID)
                                                             .artifactStreamId(ARTIFACT_STREAM_ID_1)
                                                             .artifactSourceName(ARTIFACT_SOURCE_NAME)
                                                             .build()));

    triggerService.save(webhookConditionTrigger);
    String buildNumber = "123";
    Map<String, Object> artifactParameters = new HashMap<>();
    artifactParameters.put("path", "todolist");
    artifactParameters.put("groupId", "mygroup");
    artifactParameters.put("buildNo", "123");

    when(artifactCollectionServiceAsync.collectNewArtifacts(
             APP_ID, nexusArtifactStream, buildNumber, artifactParameters))
        .thenReturn(artifact);
    when(artifactService.getArtifactByBuildNumberAndSourceName(any(), any(), anyBoolean(), any())).thenReturn(null);

    triggerService.triggerExecutionByWebHook(APP_ID, webhookConditionTrigger.getWebHookToken(),
        ImmutableMap.of(
            SERVICE_ID, ArtifactSummary.builder().buildNo(buildNumber).artifactParameters(artifactParameters).build()),
        Collections.emptyMap(), null, new HashMap<>());

    verify(artifactCollectionServiceAsync, times(1))
        .collectNewArtifacts(APP_ID, nexusArtifactStream, buildNumber, artifactParameters);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testShouldNotAddParameterizedArtifactSourceOnNewConditionTrigger() {
    Trigger trigger = buildNewArtifactTrigger();
    trigger.setArtifactSelections(
        asList(ArtifactSelection.builder().type(ARTIFACT_SOURCE).serviceId(SERVICE_ID).build()));
    when(artifactStreamService.get(ARTIFACT_STREAM_ID_1)).thenReturn(buildNexusArtifactStream());
    triggerService.save(trigger);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotAddParameterizedArtifactSourceOnLastCollectedType() {
    Trigger trigger = buildPipelineCondTrigger();
    trigger.setArtifactSelections(asList(ArtifactSelection.builder()
                                             .type(LAST_COLLECTED)
                                             .serviceId(SERVICE_ID)
                                             .artifactStreamId(ARTIFACT_STREAM_ID_1)
                                             .build()));
    when(artifactStreamService.get(ARTIFACT_STREAM_ID_1)).thenReturn(buildNexusArtifactStream());
    triggerService.save(trigger);
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void shouldGenerateWebhookTokenWithTempService() {
    Trigger trigger = buildWorkflowWebhookTrigger();
    WebHookTriggerCondition webhookCondition = (WebHookTriggerCondition) trigger.getCondition();
    webhookCondition.setWebHookToken(null);
    webhookCondition.setParameters(null);

    Variable serviceVar = aVariable().name("Service").entityType(SERVICE).build();
    Workflow workflow = buildWorkflow();
    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    canaryOrchestrationWorkflow.setUserVariables(Collections.singletonList(serviceVar));
    trigger.setWorkflowVariables(ImmutableMap.of("Service", "${srv}"));

    when(workflowService.readWorkflow(any(), any())).thenReturn(workflow);
    TriggerServiceImpl triggerServiceImpl = (TriggerServiceImpl) triggerService;
    WebHookToken webHookToken = triggerServiceImpl.generateWebHookToken(trigger, WebHookToken.builder().build());
    assertThat(webHookToken).isNotNull();
    assertThat(webHookToken.getPayload()).isNotEmpty();
    assertThat(webHookToken.getPayload())
        .isEqualTo(
            "{\"application\":\"APP_ID\",\"manifests\":[{\"service\":\"Service_PLACEHOLDER\",\"appManifestName\":\"Service_APPLICATION_MANIFEST_NAME_PLACE_HOLDER\",\"versionNumber\":\"Service_VERSION_NUMBER_PLACE_HOLDER\"}],\"parameters\":{\"Service\":\"Service_placeholder\"},\"artifacts\":[{\"artifactSourceName\":\"Service_ARTIFACT_SOURCE_NAME_PLACE_HOLDER\",\"service\":\"Service_PLACEHOLDER\",\"buildNumber\":\"Service_BUILD_NUMBER_PLACE_HOLDER\"}]}");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldGenerateWebhookTokenWithTempServiceForPipeline() {
    Trigger trigger = buildPipelineCondTrigger();
    trigger.setWorkflowVariables(ImmutableMap.of("srv", "hello"));
    trigger.setArtifactSelections(Lists.newArrayList(
        ArtifactSelection.builder().artifactSourceName("artifactSourceName").serviceId("serviceId").build()));
    TriggerServiceImpl triggerServiceImpl = (TriggerServiceImpl) triggerService;
    WebHookToken webHookToken = triggerServiceImpl.generateWebHookToken(trigger, WebHookToken.builder().build());
    assertThat(webHookToken).isNotNull();
    assertThat(webHookToken.getPayload()).isNotEmpty();
    assertThat(webHookToken.getPayload())
        .isEqualTo(
            "{\"application\":\"APP_ID\",\"parameters\":{\"VARIABLE_NAME\":\"VARIABLE_NAME_placeholder\"},\"artifacts\":[{\"artifactSourceName\":\"Catalog_ARTIFACT_SOURCE_NAME_PLACE_HOLDER\",\"service\":\"Catalog\",\"buildNumber\":\"Catalog_BUILD_NUMBER_PLACE_HOLDER\"}]}");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testTriggerActionExistsWorkflowType() {
    boolean actionExists = triggerService.triggerActionExists(buildWorkflowWebhookTrigger());
    assertThat(actionExists).isTrue();
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testTriggerActionExistsPipelineType() {
    boolean actionExists = triggerService.triggerActionExists(buildPipelineCondTrigger());
    assertThat(actionExists).isTrue();
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testTriggerActionExistsNoType() {
    boolean actionExists = triggerService.triggerActionExists(buildArtifactTrigger());
    assertThat(actionExists).isTrue();
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testAuthorizeAppAccessEmptyList() {
    triggerService.authorizeAppAccess(Collections.EMPTY_LIST);
    verify(triggerAuthHandler, times(0)).authorizeAppAccess(any(), any());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testAuthorizeAppAccessNoAppId() {
    List<String> appIds = Arrays.asList("");
    triggerService.authorizeAppAccess(appIds);
    verify(triggerAuthHandler, times(0)).authorizeAppAccess(any(), any());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testAuthorizeAppAccess() {
    List<String> appIds = Arrays.asList("appId");
    when(appService.getAccountIdByAppId(any())).thenReturn(ACCOUNT_ID);

    triggerService.authorizeAppAccess(appIds);
    verify(triggerAuthHandler, times(1)).authorizeAppAccess(any(), any());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testAuthorizeWorkflow() {
    when(workflowService.readWorkflow(any(), any())).thenReturn(workflow);

    triggerService.authorize(buildWorkflowWebhookTrigger(), true);
    verify(workflowService, times(1)).readWorkflow(any(), any());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testAuthorizePipeline() {
    triggerService.authorize(buildPipelineCondTrigger(), true);
    verify(workflowService, times(0)).readWorkflow(any(), any());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testAuthorizeWorkflowAndEnvironment() {
    setTemplatedWorkflow();
    when(workflowService.readWorkflow(any(), any())).thenReturn(workflow);

    triggerService.authorize(buildWorkflowWebhookTrigger(), true);
    verify(workflowService, times(1)).readWorkflow(any(), any());
  }

  @Test(expected = WingsException.class)
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testAuthorizeWorkflowAndEnvironmentWithException() {
    setTemplatedWorkflow();
    when(workflowService.readWorkflow(any(), any())).thenReturn(workflow);

    triggerService.authorize(buildWorkflowWebhookTrigger(), false);
  }

  private void validateUpdate(Trigger trigger) {
    assertThatThrownBy(() -> triggerService.update(trigger, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "Deploy if files has changed, Git Connector, Branch Name, Repo Name and File Paths cannot be changed on updating Trigger");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldNotUpdateTriggerToWebhookTriggerWithFileContentChanged() {
    triggerService.save(scheduledConditionTrigger);
    WebHookTriggerCondition webHookTriggerCondition = (WebHookTriggerCondition) webhookConditionTrigger.getCondition();
    webHookTriggerCondition.setCheckFileContentChanged(true);
    validateUpdate(webhookConditionTrigger);

    webHookTriggerCondition.setCheckFileContentChanged(false);
    webHookTriggerCondition.setBranchName("master");
    validateUpdate(webhookConditionTrigger);

    webHookTriggerCondition.setBranchName(null);
    webHookTriggerCondition.setGitConnectorId(UUID);
    validateUpdate(webhookConditionTrigger);

    webHookTriggerCondition.setGitConnectorId(null);
    webHookTriggerCondition.setFilePaths(asList("test.yaml", "test1.yaml"));
    validateUpdate(webhookConditionTrigger);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldNotUpdateWebhookTriggerWithFileContentChanged() {
    Trigger webhookTrigger =
        buildWorkflowWebhookTriggerWithFileContentChanged("testRepo", "master", UUID, PUSH, "index.yaml");
    WebHookTriggerCondition webHookTriggerCondition = (WebHookTriggerCondition) webhookTrigger.getCondition();
    triggerService.save(webhookTrigger);

    webHookTriggerCondition.setCheckFileContentChanged(true);

    webHookTriggerCondition.setBranchName(null);
    validateUpdate(webhookTrigger);

    webHookTriggerCondition.setBranchName("master");
    webHookTriggerCondition.setGitConnectorId("Git-connector");
    validateUpdate(webhookTrigger);

    webHookTriggerCondition.setGitConnectorId(UUID);
    webHookTriggerCondition.setFilePaths(asList("test1.yaml", "test2.yaml"));
    validateUpdate(webhookTrigger);

    webHookTriggerCondition.setFilePaths(asList("index.yaml"));
    webHookTriggerCondition.setRepoName("repoUpdated");
    validateUpdate(webhookTrigger);

    webHookTriggerCondition.setRepoName("");
    validateUpdate(webhookTrigger);

    webHookTriggerCondition.setRepoName(" ");
    validateUpdate(webhookTrigger);

    webHookTriggerCondition.setRepoName(null);
    validateUpdate(webhookTrigger);
  }

  private void setUpForNewManifestCondition() {
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .serviceId(SERVICE_ID)
                                                  .pollForChanges(Boolean.TRUE)
                                                  .storeType(StoreType.HelmChartRepo)
                                                  .build();
    applicationManifest.setUuid(MANIFEST_ID);
    when(applicationManifestService.getById(any(), any())).thenReturn(applicationManifest);
    when(featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, ACCOUNT_ID)).thenReturn(true);
    when(serviceResourceService.get(APP_ID, SERVICE_ID))
        .thenReturn(Service.builder().uuid(SERVICE_ID).name(CATALOG_SERVICE_NAME).build());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldSaveWithOnNewManifestCondition() {
    setUpForNewManifestCondition();

    Trigger trigger = triggerService.save(newManifestConditionTrigger);
    assertThat(trigger.getUuid()).isNotEmpty();
    assertThat(trigger.getCondition()).isInstanceOf(ManifestTriggerCondition.class);
    assertThat(((ManifestTriggerCondition) trigger.getCondition()).getServiceId()).isNotNull().isEqualTo(SERVICE_ID);
    assertThat(((ManifestTriggerCondition) trigger.getCondition()).getAppManifestId())
        .isNotNull()
        .isEqualTo(MANIFEST_ID);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldNotSaveOrUpdateNewManifestConditionWithFFOff() {
    setUpForNewManifestCondition();

    Trigger savedTrigger = triggerService.save(newManifestConditionTrigger);
    assertThat(savedTrigger).isNotNull();

    when(featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, ACCOUNT_ID)).thenReturn(false);

    assertThatThrownBy(() -> triggerService.save(newManifestConditionTrigger))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid trigger condition type");

    assertThatThrownBy(() -> triggerService.update(newManifestConditionTrigger, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid trigger condition type");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldNotSaveOrUpdateIfPollingDisabled() {
    setUpForNewManifestCondition();

    Trigger savedTrigger = triggerService.save(newManifestConditionTrigger);
    assertThat(savedTrigger).isNotNull();

    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .serviceId(SERVICE_ID)
                                                  .pollForChanges(Boolean.FALSE)
                                                  .storeType(StoreType.HelmChartRepo)
                                                  .build();
    applicationManifest.setUuid(MANIFEST_ID);
    when(applicationManifestService.getById(any(), any())).thenReturn(applicationManifest);

    assertThatThrownBy(() -> triggerService.save(newManifestConditionTrigger))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Cannot select service for which poll for manifest is not enabled");

    assertThatThrownBy(() -> triggerService.update(newManifestConditionTrigger, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Cannot select service for which poll for manifest is not enabled");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldNotSaveOrUpdateWithIncorrectRegex() {
    setUpForNewManifestCondition();

    Trigger savedTrigger = triggerService.save(newManifestConditionTrigger);
    assertThat(savedTrigger).isNotNull();

    Trigger newTrigger = buildNewManifestTrigger();
    newTrigger.setCondition(ManifestTriggerCondition.builder()
                                .appManifestId(MANIFEST_ID)
                                .serviceName(SERVICE_NAME)
                                .versionRegex("**")
                                .build());

    assertThatThrownBy(() -> triggerService.save(newTrigger))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid versionRegex, Please provide a valid regex");

    assertThatThrownBy(() -> triggerService.update(newTrigger, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid versionRegex, Please provide a valid regex");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetNewManifestConditionTrigger() {
    setUpForNewManifestCondition();

    Trigger savedTrigger = triggerService.save(newManifestConditionTrigger);
    assertThat(savedTrigger).isNotNull();
    assertThat(savedTrigger.getAppId()).isEqualTo(APP_ID);

    when(serviceResourceService.getName(any(), any())).thenReturn(SERVICE_NAME);

    Trigger trigger = triggerService.get(savedTrigger.getAppId(), savedTrigger.getUuid());
    assertThat(trigger).isNotNull();
    assertThat(trigger.getCondition()).isInstanceOf(ManifestTriggerCondition.class);
    assertThat(((ManifestTriggerCondition) trigger.getCondition()).getServiceId()).isNotNull().isEqualTo(SERVICE_ID);
    assertThat(((ManifestTriggerCondition) trigger.getCondition()).getAppManifestId())
        .isNotNull()
        .isEqualTo(MANIFEST_ID);
    assertThat(((ManifestTriggerCondition) trigger.getCondition()).getServiceName())
        .isNotNull()
        .isEqualTo(SERVICE_NAME);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldListTriggersWithServiceName() {
    setUpForNewManifestCondition();

    Trigger newManifestTrigger = buildNewManifestTrigger();
    newManifestTrigger.setUuid("TRIGGER_ID_1");
    Trigger trigger1 = triggerService.save(newManifestTrigger);
    assertThat(trigger1).isNotNull();

    Trigger trigger2 = triggerService.save(artifactConditionTrigger);
    assertThat(trigger2).isNotNull();

    when(serviceResourceService.getServiceNames(any(), anySet()))
        .thenReturn(Collections.singletonMap(SERVICE_ID, SERVICE_NAME));

    PageRequest<Trigger> pageRequest = new PageRequest<>();
    PageResponse<Trigger> triggers = triggerService.list(pageRequest, false, null);
    assertThat(triggers).hasSize(2);
    for (Trigger trigger : triggers) {
      if (trigger.getUuid().equals("TRIGGER_ID_1")) {
        assertThat(trigger.getCondition()).isNotNull();
        ManifestTriggerCondition condition = (ManifestTriggerCondition) trigger.getCondition();
        assertThat(condition.getServiceName()).isNotNull().isEqualTo(SERVICE_NAME);
      }
    }
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateManifestFromSelectionsForScheduled() {
    scheduledConditionTrigger.setManifestSelections(asList(ManifestSelection.builder()
                                                               .type(ManifestSelectionType.LAST_COLLECTED)
                                                               .serviceId(SERVICE_ID)
                                                               .appManifestId(MANIFEST_ID)
                                                               .versionRegex(ARTIFACT_FILTER)
                                                               .build(),
        ManifestSelection.builder()
            .type(ManifestSelectionType.LAST_DEPLOYED)
            .serviceId(SERVICE_ID + 2)
            .appManifestId(MANIFEST_ID + 2)
            .pipelineId(PIPELINE_ID)
            .versionRegex(ARTIFACT_FILTER)
            .build()));

    when(helmChartService.getLastCollectedManifestMatchingRegex(ACCOUNT_ID, MANIFEST_ID, ARTIFACT_FILTER))
        .thenReturn(
            HelmChart.builder().uuid(HELM_CHART_ID).applicationManifestId(APP_MANIFEST_NAME).version("1").build());
    when(workflowExecutionService.obtainLastGoodDeployedHelmCharts(APP_ID, PIPELINE_ID))
        .thenReturn(asList(HelmChart.builder().uuid(HELM_CHART_ID + 2).serviceId(SERVICE_ID + 2).build()));
    ApplicationManifest appManifest =
        ApplicationManifest.builder().accountId(ACCOUNT_ID).storeType(StoreType.HelmChartRepo).build();
    appManifest.setUuid(MANIFEST_ID);
    when(applicationManifestService.getById(APP_ID, MANIFEST_ID)).thenReturn(appManifest);

    ArgumentCaptor<ExecutionArgs> argsArgumentCaptor = ArgumentCaptor.forClass(ExecutionArgs.class);
    triggerService.triggerScheduledExecutionAsync(scheduledConditionTrigger, new Date());
    verify(workflowExecutionService, times(1))
        .triggerEnvExecution(eq(APP_ID), any(), argsArgumentCaptor.capture(), eq(scheduledConditionTrigger));
    assertThat(argsArgumentCaptor.getValue().getHelmCharts()).hasSize(2);
    assertThat(argsArgumentCaptor.getValue().getHelmCharts().stream().map(HelmChart::getUuid))
        .containsExactlyInAnyOrder(HELM_CHART_ID, HELM_CHART_ID + 2);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateManifestFromSelectionsForNewArtifact() {
    Trigger trigger = buildArtifactTrigger();
    ArtifactTriggerCondition artifactTriggerCondition = (ArtifactTriggerCondition) trigger.getCondition();
    artifactTriggerCondition.setArtifactFilter(null);
    trigger.setManifestSelections(asList(ManifestSelection.builder()
                                             .type(ManifestSelectionType.LAST_COLLECTED)
                                             .serviceId(SERVICE_ID)
                                             .appManifestId(MANIFEST_ID)
                                             .build(),
        ManifestSelection.builder()
            .type(ManifestSelectionType.LAST_DEPLOYED)
            .serviceId(SERVICE_ID + 2)
            .appManifestId(MANIFEST_ID + 2)
            .pipelineId(WORKFLOW_ID)
            .build()));
    mockGetAppManifestAndGetService();
    triggerService.save(trigger);

    when(helmChartService.getLastCollectedManifest(ACCOUNT_ID, MANIFEST_ID))
        .thenReturn(
            HelmChart.builder().uuid(HELM_CHART_ID).applicationManifestId(APP_MANIFEST_NAME).version("1").build());
    when(workflowExecutionService.obtainLastGoodDeployedHelmCharts(APP_ID, WORKFLOW_ID))
        .thenReturn(asList(HelmChart.builder().uuid(HELM_CHART_ID + 2).serviceId(SERVICE_ID + 2).build()));
    ApplicationManifest appManifest =
        ApplicationManifest.builder().accountId(ACCOUNT_ID).storeType(StoreType.HelmChartRepo).build();
    appManifest.setUuid(MANIFEST_ID);
    when(applicationManifestService.getById(APP_ID, MANIFEST_ID)).thenReturn(appManifest);

    ArgumentCaptor<ExecutionArgs> argsArgumentCaptor = ArgumentCaptor.forClass(ExecutionArgs.class);
    triggerService.triggerExecutionPostArtifactCollectionAsync(
        ACCOUNT_ID, APP_ID, ARTIFACT_STREAM_ID, Collections.singletonList(anArtifact().withUuid(ARTIFACT_ID).build()));
    verify(workflowExecutionService, times(1))
        .triggerEnvExecution(eq(APP_ID), any(), argsArgumentCaptor.capture(), eq(trigger));
    assertThat(argsArgumentCaptor.getValue().getHelmCharts()).hasSize(2);
    assertThat(argsArgumentCaptor.getValue().getHelmCharts().stream().map(HelmChart::getUuid))
        .containsExactlyInAnyOrder(HELM_CHART_ID, HELM_CHART_ID + 2);
  }

  private void mockGetAppManifestAndGetService() {
    ApplicationManifest appManifest =
        ApplicationManifest.builder().accountId(ACCOUNT_ID).storeType(StoreType.HelmChartRepo).build();
    appManifest.setUuid(MANIFEST_ID + 2);
    when(applicationManifestService.getManifestByServiceId(APP_ID, SERVICE_ID + 2)).thenReturn(appManifest);
    ApplicationManifest appManifest1 =
        ApplicationManifest.builder().accountId(ACCOUNT_ID).storeType(StoreType.HelmChartRepo).build();
    appManifest1.setUuid(MANIFEST_ID);
    when(applicationManifestService.getManifestByServiceId(APP_ID, SERVICE_ID)).thenReturn(appManifest1);
    when(serviceResourceService.get(APP_ID, SERVICE_ID + 2, false))
        .thenReturn(Service.builder().name(SERVICE_NAME + 2).uuid(SERVICE_ID + 2).build());
    return;
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateManifestFromSelectionsForPipelineCompletion() {
    Trigger trigger = buildPipelineCondTrigger();
    trigger.setManifestSelections(asList(ManifestSelection.builder()
                                             .type(ManifestSelectionType.PIPELINE_SOURCE)
                                             .serviceId(SERVICE_ID)
                                             .appManifestId(MANIFEST_ID)
                                             .build(),
        ManifestSelection.builder()
            .type(ManifestSelectionType.LAST_COLLECTED)
            .serviceId(SERVICE_ID + 2)
            .appManifestId(MANIFEST_ID + 2)
            .build()));
    mockGetAppManifestAndGetService();
    triggerService.save(trigger);

    when(helmChartService.getLastCollectedManifest(ACCOUNT_ID, MANIFEST_ID))
        .thenReturn(
            HelmChart.builder().uuid(HELM_CHART_ID).applicationManifestId(APP_MANIFEST_NAME).version("1").build());
    when(workflowExecutionService.obtainLastGoodDeployedHelmCharts(APP_ID, PIPELINE_ID))
        .thenReturn(asList(HelmChart.builder().uuid(HELM_CHART_ID + 2).serviceId(SERVICE_ID + 2).build(),
            HelmChart.builder().uuid(HELM_CHART_ID + 3).serviceId(SERVICE_ID).build()));
    ApplicationManifest appManifest =
        ApplicationManifest.builder().accountId(ACCOUNT_ID).storeType(StoreType.HelmChartRepo).build();
    appManifest.setUuid(MANIFEST_ID);
    when(applicationManifestService.getById(APP_ID, MANIFEST_ID + 2)).thenReturn(appManifest);

    ArgumentCaptor<ExecutionArgs> argsArgumentCaptor = ArgumentCaptor.forClass(ExecutionArgs.class);
    triggerService.triggerExecutionPostPipelineCompletionAsync(APP_ID, PIPELINE_ID);
    verify(workflowExecutionService, times(1))
        .triggerEnvExecution(eq(APP_ID), any(), argsArgumentCaptor.capture(), eq(trigger));
    assertThat(argsArgumentCaptor.getValue().getHelmCharts()).hasSize(2);
    assertThat(argsArgumentCaptor.getValue().getHelmCharts().stream().map(HelmChart::getUuid))
        .containsExactlyInAnyOrder(HELM_CHART_ID, HELM_CHART_ID + 3);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateManifestFromSelectionsForWebhook() {
    Trigger trigger = buildWebhookCondTrigger();
    trigger.setManifestSelections(asList(ManifestSelection.builder()
                                             .type(ManifestSelectionType.WEBHOOK_VARIABLE)
                                             .serviceId(SERVICE_ID)
                                             .appManifestId(MANIFEST_ID)
                                             .build(),
        ManifestSelection.builder()
            .type(ManifestSelectionType.WEBHOOK_VARIABLE)
            .serviceId(SERVICE_ID + 2)
            .appManifestId(MANIFEST_ID + 2)
            .build()));
    mockGetAppManifestAndGetService();
    triggerService.save(trigger);

    when(helmChartService.getManifestByVersionNumber(eq(ACCOUNT_ID), any(), any()))
        .thenAnswer(invocationOnMock
            -> HelmChart.builder()
                   .uuid(HELM_CHART_ID + invocationOnMock.getArgument(2, String.class))
                   .version(invocationOnMock.getArgument(2, String.class))
                   .build());
    ApplicationManifest appManifest =
        ApplicationManifest.builder().accountId(ACCOUNT_ID).storeType(StoreType.HelmChartRepo).build();
    appManifest.setUuid(MANIFEST_ID);
    when(applicationManifestService.getById(APP_ID, MANIFEST_ID + 2)).thenReturn(appManifest);
    when(applicationManifestService.getById(APP_ID, MANIFEST_ID)).thenReturn(appManifest);

    ArgumentCaptor<ExecutionArgs> argsArgumentCaptor = ArgumentCaptor.forClass(ExecutionArgs.class);
    Map<String, ManifestSummary> serviceManifestMapping =
        ImmutableMap.of(SERVICE_ID, ManifestSummary.builder().versionNo("1").appManifestName("name").build(),
            SERVICE_ID + 2, ManifestSummary.builder().versionNo("5").appManifestName("name").build());
    triggerService.triggerExecutionByWebHook(APP_ID, trigger.getWebHookToken(), Collections.emptyMap(),
        serviceManifestMapping, null, Collections.singletonMap("service", SERVICE_NAME));
    verify(workflowExecutionService, times(1))
        .triggerEnvExecution(eq(APP_ID), eq(null), argsArgumentCaptor.capture(), any());
    verify(helmChartService, times(2)).getManifestByVersionNumber(eq(ACCOUNT_ID), any(), any());
    assertThat(argsArgumentCaptor.getValue().getHelmCharts()).hasSize(2);
    assertThat(argsArgumentCaptor.getValue().getHelmCharts().stream().map(HelmChart::getVersion))
        .containsExactlyInAnyOrder("1", "5");

    when(helmChartService.getManifestByVersionNumber(eq(ACCOUNT_ID), any(), any())).thenReturn(null);
    when(featureFlagService.isEnabled(FeatureName.BYPASS_HELM_FETCH, ACCOUNT_ID)).thenReturn(true);
    triggerService.triggerExecutionByWebHook(APP_ID, trigger.getWebHookToken(), Collections.emptyMap(),
        serviceManifestMapping, null, Collections.singletonMap("service", SERVICE_NAME));
    verify(helmChartService).createHelmChartWithVersionForAppManifest(any(), eq("1"));
    verify(helmChartService).createHelmChartWithVersionForAppManifest(any(), eq("5"));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldFailForInvalidWebhookManifestInput() {
    Trigger trigger = buildWebhookCondTrigger();
    trigger.setManifestSelections(asList(ManifestSelection.builder()
                                             .type(ManifestSelectionType.WEBHOOK_VARIABLE)
                                             .serviceId(SERVICE_ID)
                                             .appManifestId(MANIFEST_ID)
                                             .build(),
        ManifestSelection.builder()
            .type(ManifestSelectionType.WEBHOOK_VARIABLE)
            .serviceId(SERVICE_ID + 2)
            .appManifestId(MANIFEST_ID + 2)
            .build()));
    mockGetAppManifestAndGetService();
    triggerService.save(trigger);

    when(helmChartService.getManifestByVersionNumber(eq(ACCOUNT_ID), eq("1"), any()))
        .thenReturn(HelmChart.builder().uuid(HELM_CHART_ID).version("1").build());
    ApplicationManifest appManifest =
        ApplicationManifest.builder().accountId(ACCOUNT_ID).storeType(StoreType.HelmChartRepo).build();
    appManifest.setUuid(MANIFEST_ID);
    when(applicationManifestService.getById(APP_ID, MANIFEST_ID + 2)).thenReturn(appManifest);
    when(applicationManifestService.getById(APP_ID, MANIFEST_ID)).thenReturn(appManifest);

    Map<String, ManifestSummary> serviceManifestMapping =
        ImmutableMap.of(SERVICE_ID, ManifestSummary.builder().versionNo("1").appManifestName("name").build(),
            SERVICE_ID + 2, ManifestSummary.builder().versionNo("5").appManifestName("name").build());
    assertThatThrownBy(
        ()
            -> triggerService.triggerExecutionByWebHook(APP_ID, trigger.getWebHookToken(), Collections.emptyMap(),
                serviceManifestMapping, null, Collections.singletonMap("service", SERVICE_NAME)))
        .isInstanceOf(GeneralException.class)
        .hasMessage("Helm chart with given version number doesn't exist");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldTriggerForManifestCollectedWithoutRegex() {
    Trigger trigger = buildNewManifestTrigger();
    ManifestTriggerCondition manifestTriggerCondition = (ManifestTriggerCondition) trigger.getCondition();
    manifestTriggerCondition.setVersionRegex(null);
    trigger.setManifestSelections(asList(ManifestSelection.builder()
                                             .type(ManifestSelectionType.FROM_APP_MANIFEST)
                                             .serviceId(SERVICE_ID)
                                             .appManifestId(MANIFEST_ID)
                                             .build(),
        ManifestSelection.builder()
            .type(ManifestSelectionType.LAST_COLLECTED)
            .serviceId(SERVICE_ID + 2)
            .appManifestId(MANIFEST_ID + 2)
            .build()));

    ApplicationManifest appManifest = ApplicationManifest.builder()
                                          .accountId(ACCOUNT_ID)
                                          .serviceId(SERVICE_ID)
                                          .storeType(StoreType.HelmChartRepo)
                                          .pollForChanges(true)
                                          .build();
    appManifest.setUuid(MANIFEST_ID);
    when(applicationManifestService.getById(APP_ID, MANIFEST_ID + 2)).thenReturn(appManifest);
    when(applicationManifestService.getById(APP_ID, MANIFEST_ID)).thenReturn(appManifest);
    when(serviceResourceService.get(APP_ID, SERVICE_ID))
        .thenReturn(Service.builder().name(SERVICE_NAME).uuid(SERVICE_ID).build());
    mockGetAppManifestAndGetService();
    triggerService.save(trigger);

    HelmChart helmChart1 = HelmChart.builder()
                               .uuid(HELM_CHART_ID)
                               .serviceId(SERVICE_ID)
                               .applicationManifestId(MANIFEST_ID)
                               .version("1")
                               .createdAt(1)
                               .build();
    HelmChart helmChart2 = HelmChart.builder()
                               .uuid(HELM_CHART_ID + 2)
                               .serviceId(SERVICE_ID)
                               .applicationManifestId(MANIFEST_ID)
                               .version("2")
                               .createdAt(2)
                               .build();
    HelmChart helmChart3 = HelmChart.builder()
                               .uuid(HELM_CHART_ID + 3)
                               .serviceId(SERVICE_ID + 2)
                               .applicationManifestId(MANIFEST_ID)
                               .version("2")
                               .createdAt(2)
                               .build();
    when(helmChartService.getLastCollectedManifest(ACCOUNT_ID, MANIFEST_ID)).thenReturn(helmChart3);

    ArgumentCaptor<ExecutionArgs> argsArgumentCaptor = ArgumentCaptor.forClass(ExecutionArgs.class);
    triggerService.triggerExecutionPostManifestCollectionAsync(APP_ID, MANIFEST_ID, asList(helmChart1, helmChart2));
    verify(workflowExecutionService, times(1))
        .triggerEnvExecution(eq(APP_ID), any(), argsArgumentCaptor.capture(), any());
    assertThat(argsArgumentCaptor.getValue().getHelmCharts()).hasSize(2);
    assertThat(argsArgumentCaptor.getValue().getHelmCharts()).containsExactlyInAnyOrder(helmChart1, helmChart3);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotTriggerForManifestCollectedWithNoneMatchingRegex() {
    Trigger trigger = buildNewManifestTrigger();
    ManifestTriggerCondition manifestTriggerCondition = (ManifestTriggerCondition) trigger.getCondition();
    manifestTriggerCondition.setVersionRegex("2\\.*");
    trigger.setManifestSelections(asList(ManifestSelection.builder()
                                             .type(ManifestSelectionType.FROM_APP_MANIFEST)
                                             .serviceId(SERVICE_ID)
                                             .appManifestId(MANIFEST_ID)
                                             .build(),
        ManifestSelection.builder()
            .type(ManifestSelectionType.LAST_DEPLOYED)
            .serviceId(SERVICE_ID + 2)
            .pipelineId(PIPELINE_ID)
            .appManifestId(MANIFEST_ID + 2)
            .build()));

    ApplicationManifest appManifest = ApplicationManifest.builder()
                                          .accountId(ACCOUNT_ID)
                                          .serviceId(SERVICE_ID)
                                          .storeType(StoreType.HelmChartRepo)
                                          .pollForChanges(true)
                                          .build();
    appManifest.setUuid(MANIFEST_ID);
    when(applicationManifestService.getById(APP_ID, MANIFEST_ID + 2)).thenReturn(appManifest);
    when(applicationManifestService.getById(APP_ID, MANIFEST_ID)).thenReturn(appManifest);
    when(serviceResourceService.get(APP_ID, SERVICE_ID))
        .thenReturn(Service.builder().name(SERVICE_NAME).uuid(SERVICE_ID).build());
    when(serviceResourceService.get(APP_ID, SERVICE_ID + 2, false))
        .thenReturn(Service.builder().name(SERVICE_NAME + 2).uuid(SERVICE_ID + 2).build());
    triggerService.save(trigger);

    HelmChart helmChart1 = HelmChart.builder()
                               .uuid(HELM_CHART_ID)
                               .serviceId(SERVICE_ID)
                               .applicationManifestId(MANIFEST_ID)
                               .version("1.0")
                               .createdAt(1)
                               .build();
    HelmChart helmChart2 = HelmChart.builder()
                               .uuid(HELM_CHART_ID + 2)
                               .serviceId(SERVICE_ID)
                               .applicationManifestId(MANIFEST_ID)
                               .version("3.0")
                               .createdAt(2)
                               .build();
    HelmChart helmChart3 = HelmChart.builder()
                               .uuid(HELM_CHART_ID + 3)
                               .serviceId(SERVICE_ID + 2)
                               .applicationManifestId(MANIFEST_ID)
                               .version("2")
                               .createdAt(2)
                               .build();
    when(workflowExecutionService.obtainLastGoodDeployedHelmCharts(APP_ID, PIPELINE_ID)).thenReturn(asList(helmChart3));

    triggerService.triggerExecutionPostManifestCollectionAsync(APP_ID, MANIFEST_ID, asList(helmChart1, helmChart2));
    verify(workflowExecutionService, never()).triggerEnvExecution(eq(APP_ID), any(), any(), any());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotTriggerForManifestCollectedIfTriggerDisabled() {
    Trigger trigger = buildNewManifestTrigger();
    trigger.setManifestSelections(asList(ManifestSelection.builder()
                                             .type(ManifestSelectionType.FROM_APP_MANIFEST)
                                             .serviceId(SERVICE_ID)
                                             .appManifestId(MANIFEST_ID)
                                             .build()));
    trigger.setDisabled(true);

    ApplicationManifest appManifest = ApplicationManifest.builder()
                                          .accountId(ACCOUNT_ID)
                                          .serviceId(SERVICE_ID)
                                          .storeType(StoreType.HelmChartRepo)
                                          .pollForChanges(true)
                                          .build();
    appManifest.setUuid(MANIFEST_ID);
    when(applicationManifestService.getById(APP_ID, MANIFEST_ID + 2)).thenReturn(appManifest);
    when(applicationManifestService.getById(APP_ID, MANIFEST_ID)).thenReturn(appManifest);
    when(serviceResourceService.get(APP_ID, SERVICE_ID))
        .thenReturn(Service.builder().name(SERVICE_NAME).uuid(SERVICE_ID).build());
    triggerService.save(trigger);

    HelmChart helmChart1 = HelmChart.builder()
                               .uuid(HELM_CHART_ID)
                               .serviceId(SERVICE_ID)
                               .applicationManifestId(MANIFEST_ID)
                               .version("1.0")
                               .createdAt(1)
                               .build();
    triggerService.triggerExecutionPostManifestCollectionAsync(APP_ID, MANIFEST_ID, asList(helmChart1));
    verify(workflowExecutionService, never()).triggerEnvExecution(eq(APP_ID), any(), any(), any());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateManifestForTemplatizedServiceInWebhook() {
    Trigger trigger = buildWebhookCondTrigger();
    trigger.setManifestSelections(asList(ManifestSelection.builder()
                                             .type(ManifestSelectionType.WEBHOOK_VARIABLE)
                                             .serviceId(SERVICE_ID)
                                             .appManifestId(MANIFEST_ID)
                                             .build()));
    ApplicationManifest appManifest = ApplicationManifest.builder()
                                          .accountId(ACCOUNT_ID)
                                          .storeType(StoreType.HelmChartRepo)
                                          .pollForChanges(true)
                                          .build();
    appManifest.setUuid(MANIFEST_ID);
    when(applicationManifestService.getManifestByServiceId(APP_ID, SERVICE_ID)).thenReturn(appManifest);
    triggerService.save(trigger);

    when(helmChartService.getManifestByVersionNumber(eq(ACCOUNT_ID), any(), any()))
        .thenAnswer(invocationOnMock
            -> HelmChart.builder()
                   .uuid(HELM_CHART_ID + invocationOnMock.getArgument(2, String.class))
                   .version(invocationOnMock.getArgument(2, String.class))
                   .build());
    when(applicationManifestService.getManifestByServiceId(APP_ID, SERVICE_ID + 2)).thenReturn(appManifest);
    when(applicationManifestService.getAppManifestByName(APP_ID, null, SERVICE_ID + 2, "name")).thenReturn(appManifest);
    when(applicationManifestService.getById(APP_ID, MANIFEST_ID)).thenReturn(appManifest);

    ArgumentCaptor<ExecutionArgs> argsArgumentCaptor = ArgumentCaptor.forClass(ExecutionArgs.class);
    Map<String, ManifestSummary> serviceManifestMapping =
        ImmutableMap.of(SERVICE_ID, ManifestSummary.builder().versionNo("1").appManifestName("name").build(),
            SERVICE_ID + 2, ManifestSummary.builder().versionNo("5").appManifestName("name").build());
    triggerService.triggerExecutionByWebHook(APP_ID, trigger.getWebHookToken(), Collections.emptyMap(),
        serviceManifestMapping, null, Collections.singletonMap("service", SERVICE_NAME));
    verify(workflowExecutionService, times(1))
        .triggerEnvExecution(eq(APP_ID), eq(null), argsArgumentCaptor.capture(), any());
    verify(helmChartService, times(2)).getManifestByVersionNumber(eq(ACCOUNT_ID), any(), any());
    verify(applicationManifestService, times(1)).getById(any(), any());
    assertThat(argsArgumentCaptor.getValue().getHelmCharts()).hasSize(2);
    assertThat(argsArgumentCaptor.getValue().getHelmCharts().stream().map(HelmChart::getVersion))
        .containsExactlyInAnyOrder("1", "5");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotTriggerForAllArtifactsWhenLastCollectedIsSelected() {
    Artifact artifact = anArtifact().withUuid(ARTIFACT_ID).withArtifactStreamId(ARTIFACT_STREAM_ID).build();
    Artifact artifact2 = anArtifact().withUuid(ARTIFACT_ID + 2).withArtifactStreamId(ARTIFACT_STREAM_ID).build();
    Trigger trigger = buildArtifactTrigger();
    ArtifactTriggerCondition artifactTriggerCondition = (ArtifactTriggerCondition) trigger.getCondition();
    artifactTriggerCondition.setArtifactFilter(null);
    trigger.setArtifactSelections(asList(ArtifactSelection.builder()
                                             .type(LAST_COLLECTED)
                                             .artifactStreamId(ARTIFACT_STREAM_ID)
                                             .serviceId(SERVICE_ID)
                                             .build()));
    triggerService.save(trigger);
    when(featureFlagService.isEnabled(FeatureName.TRIGGER_FOR_ALL_ARTIFACTS, ACCOUNT_ID)).thenReturn(true);
    when(serviceResourceService.get(trigger.getAppId(), SERVICE_ID, false))
        .thenReturn(Service.builder().uuid(SERVICE_ID).build());
    when(artifactService.fetchLastCollectedArtifact(any())).thenReturn(artifact);
    when(artifactStreamServiceBindingService.listArtifactStreamIds(APP_ID, SERVICE_ID))
        .thenReturn(asList(ARTIFACT_STREAM_ID));
    triggerService.triggerExecutionPostArtifactCollectionAsync(
        ACCOUNT_ID, APP_ID, ARTIFACT_STREAM_ID, asList(artifact, artifact2));

    ArgumentCaptor<ExecutionArgs> argsArgumentCaptor = ArgumentCaptor.forClass(ExecutionArgs.class);
    verify(workflowExecutionService, times(1))
        .triggerEnvExecution(eq(APP_ID), eq(null), argsArgumentCaptor.capture(), any());
    assertThat(argsArgumentCaptor.getValue().getArtifacts()).hasSize(1);
    assertThat(argsArgumentCaptor.getValue().getArtifacts().get(0).getUuid()).isEqualTo(ARTIFACT_ID);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldTriggerForAllArtifactsWhenTriggeringArtifactIsSelected() {
    Artifact artifact = anArtifact().withUuid(ARTIFACT_ID).withArtifactStreamId(ARTIFACT_STREAM_ID).build();
    Artifact artifact2 = anArtifact().withUuid(ARTIFACT_ID + 2).withArtifactStreamId(ARTIFACT_STREAM_ID).build();
    Trigger trigger = buildArtifactTrigger();
    ArtifactTriggerCondition artifactTriggerCondition = (ArtifactTriggerCondition) trigger.getCondition();
    artifactTriggerCondition.setArtifactFilter(null);
    trigger.setArtifactSelections(
        asList(ArtifactSelection.builder().type(ARTIFACT_SOURCE).serviceId(SERVICE_ID).build()));
    triggerService.save(trigger);

    when(featureFlagService.isEnabled(FeatureName.TRIGGER_FOR_ALL_ARTIFACTS, ACCOUNT_ID)).thenReturn(true);
    when(serviceResourceService.get(trigger.getAppId(), SERVICE_ID, false))
        .thenReturn(Service.builder().uuid(SERVICE_ID).build());
    when(artifactService.fetchLastCollectedArtifact(any())).thenReturn(artifact);
    when(artifactStreamServiceBindingService.listArtifactStreamIds(APP_ID, SERVICE_ID))
        .thenReturn(asList(ARTIFACT_STREAM_ID));
    when(artifactStreamServiceBindingService.getServiceId(trigger.getAppId(), ARTIFACT_STREAM_ID, true))
        .thenReturn(SERVICE_ID);
    triggerService.triggerExecutionPostArtifactCollectionAsync(
        ACCOUNT_ID, APP_ID, ARTIFACT_STREAM_ID, asList(artifact, artifact2));

    ArgumentCaptor<ExecutionArgs> argsArgumentCaptor = ArgumentCaptor.forClass(ExecutionArgs.class);
    verify(workflowExecutionService, times(2))
        .triggerEnvExecution(eq(APP_ID), eq(null), argsArgumentCaptor.capture(), any());
    assertThat(argsArgumentCaptor.getAllValues()).hasSize(2);
    List<Artifact> artifacts = argsArgumentCaptor.getAllValues()
                                   .stream()
                                   .flatMap(executionArgs -> executionArgs.getArtifacts().stream())
                                   .collect(Collectors.toList());
    assertThat(artifacts.stream().map(Artifact::getUuid).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ARTIFACT_ID, ARTIFACT_ID + 2);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void shouldNotSaveNonWebhookTriggerWithExpressionValue() {
    Map<String, String> variables = new HashMap<>();
    variables.put("SERVICE", "${expression}");
    pipelineCondTrigger.setWorkflowVariables(variables);

    Pipeline testPipeline = Pipeline.builder()
                                .appId(APP_ID)
                                .uuid(PIPELINE_ID)
                                .services(singletonList(Service.builder().uuid(SERVICE_ID).name("testService").build()))
                                .build();

    List<Variable> userVariables = new ArrayList<>();
    userVariables.add(aVariable().name("SERVICE").value("Service").type(VariableType.ENTITY).build());
    testPipeline.setPipelineVariables(userVariables);

    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(testPipeline);

    assertThatThrownBy(() -> triggerService.save(pipelineCondTrigger))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Expressions are not allowed for Entity Variables for Workflow Variables");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void shouldSaveNonWebhookTriggerWithNonExpressionValueAs() {
    Map<String, String> variables = new HashMap<>();
    variables.put("ENV", "nonExpressionValue");
    variables.put("textVar", "${expression}");

    pipelineCondTrigger.setWorkflowVariables(variables);

    Pipeline testPipeline = Pipeline.builder()
                                .appId(APP_ID)
                                .uuid(PIPELINE_ID)
                                .services(singletonList(Service.builder().uuid(SERVICE_ID).name("testService").build()))
                                .build();

    List<Variable> userVariables = new ArrayList<>();
    userVariables.add(aVariable().name("textVar").value("TEXT_VAR").type(VariableType.TEXT).build());
    userVariables.add(aVariable().name("ENV").value("env").type(VariableType.ENTITY).build());
    testPipeline.setPipelineVariables(userVariables);

    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(testPipeline);
    Trigger trigger = triggerService.save(pipelineCondTrigger);

    assertThat(trigger.getUuid()).isNotEmpty();
    assertThat(trigger.getCondition()).isInstanceOf(PipelineTriggerCondition.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldSendTriggerPipelineRejectedNotificationForDeploymentFreeze() {
    Trigger trigger = buildWebhookCondTrigger();
    triggerService.save(trigger);

    List<String> deploymentFreezeIds = asList(FREEZE_WINDOW_ID, FREEZE_WINDOW_ID + 2);
    Map<String, String> serviceManifestMapping = new HashMap<>();
    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(null), any(), eq(trigger)))
        .thenThrow(new DeploymentFreezeException(ErrorCode.DEPLOYMENT_GOVERNANCE_ERROR, Level.INFO, WingsException.USER,
            ACCOUNT_ID, deploymentFreezeIds, Collections.singletonList(""), "FREEZE_NAMES", false, false));
    when(appService.get(APP_ID)).thenReturn(Application.Builder.anApplication().name(APP_NAME).build());
    when(pipelineService.getPipeline(APP_ID, PIPELINE_ID)).thenReturn(Pipeline.builder().name(PIPELINE_NAME).build());

    assertThatThrownBy(() -> triggerService.triggerExecutionByWebHook(trigger, serviceManifestMapping, null))
        .isInstanceOf(DeploymentFreezeException.class)
        .hasMessage(
            "Deployment Freeze Windows FREEZE_NAMES are active for the environment. No deployments are allowed to proceed.");
    ArgumentCaptor<Map> placeholderCaptor = ArgumentCaptor.forClass(Map.class);
    verify(deploymentFreezeUtils, times(1))
        .sendTriggerRejectedNotification(
            eq(ACCOUNT_ID), eq(APP_ID), eq(deploymentFreezeIds), placeholderCaptor.capture());
    assertThat(placeholderCaptor.getValue()).containsEntry(TriggerServiceHelper.TRIGGER_NAME, TRIGGER_NAME);
    assertThat(placeholderCaptor.getValue()).containsEntry(TriggerServiceHelper.APP_NAME, APP_NAME);
    assertThat(placeholderCaptor.getValue()).containsEntry(TriggerServiceHelper.WORKFLOW_NAME, PIPELINE_NAME);
    assertThat(placeholderCaptor.getValue())
        .containsEntry(TriggerServiceHelper.TRIGGER_URL, "PORTAL_URL/#/account/ACCOUNT_ID/app/APP_ID/triggers");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldSendTriggerWorkflowRejectedNotificationForDeploymentFreeze() {
    Trigger trigger = buildWorkflowWebhookTrigger();
    triggerService.save(trigger);

    List<String> deploymentFreezeIds = asList(FREEZE_WINDOW_ID, FREEZE_WINDOW_ID + 2);
    Map<String, String> serviceManifestMapping = new HashMap<>();
    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(ENV_ID), any(), eq(trigger)))
        .thenThrow(new DeploymentFreezeException(ErrorCode.DEPLOYMENT_GOVERNANCE_ERROR, Level.INFO, WingsException.USER,
            ACCOUNT_ID, deploymentFreezeIds, Collections.singletonList(""), "FREEZE_NAMES", false, false));
    when(appService.get(APP_ID)).thenReturn(Application.Builder.anApplication().name(APP_NAME).build());
    when(workflowService.getWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(buildWorkflow());

    assertThatThrownBy(() -> triggerService.triggerExecutionByWebHook(trigger, serviceManifestMapping, null))
        .isInstanceOf(DeploymentFreezeException.class)
        .hasMessage(
            "Deployment Freeze Windows FREEZE_NAMES are active for the environment. No deployments are allowed to proceed.");
    ArgumentCaptor<Map> placeholderCaptor = ArgumentCaptor.forClass(Map.class);
    verify(deploymentFreezeUtils, times(1))
        .sendTriggerRejectedNotification(
            eq(ACCOUNT_ID), eq(APP_ID), eq(deploymentFreezeIds), placeholderCaptor.capture());
    assertThat(placeholderCaptor.getValue()).containsEntry(TriggerServiceHelper.TRIGGER_NAME, TRIGGER_NAME);
    assertThat(placeholderCaptor.getValue()).containsEntry(TriggerServiceHelper.APP_NAME, APP_NAME);
    assertThat(placeholderCaptor.getValue()).containsEntry(TriggerServiceHelper.WORKFLOW_NAME, WORKFLOW_NAME);
    assertThat(placeholderCaptor.getValue())
        .containsEntry(TriggerServiceHelper.TRIGGER_URL, "PORTAL_URL/#/account/ACCOUNT_ID/app/APP_ID/triggers");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateManifestForOnNewArtifactTriggerWithLastCollectedFilter() {
    Trigger trigger = buildArtifactTrigger();
    ArtifactTriggerCondition artifactTriggerCondition = (ArtifactTriggerCondition) trigger.getCondition();
    artifactTriggerCondition.setArtifactFilter(null);
    trigger.setArtifactSelections(asList(ArtifactSelection.builder()
                                             .artifactStreamId(ARTIFACT_STREAM_ID)
                                             .serviceId(SERVICE_ID)
                                             .type(LAST_COLLECTED)
                                             .artifactFilter("FILTER")
                                             .build()));
    trigger.setManifestSelections(asList(ManifestSelection.builder()
                                             .type(ManifestSelectionType.LAST_COLLECTED)
                                             .serviceId(SERVICE_ID)
                                             .appManifestId(MANIFEST_ID)
                                             .build(),
        ManifestSelection.builder()
            .type(ManifestSelectionType.LAST_DEPLOYED)
            .serviceId(SERVICE_ID + 2)
            .appManifestId(MANIFEST_ID + 2)
            .pipelineId(WORKFLOW_ID)
            .build()));
    mockGetAppManifestAndGetService();
    triggerService.save(trigger);

    when(featureFlagService.isEnabled(FeatureName.ON_NEW_ARTIFACT_TRIGGER_WITH_LAST_COLLECTED_FILTER, ACCOUNT_ID))
        .thenReturn(true);

    when(helmChartService.getLastCollectedManifest(ACCOUNT_ID, MANIFEST_ID))
        .thenReturn(
            HelmChart.builder().uuid(HELM_CHART_ID).applicationManifestId(APP_MANIFEST_NAME).version("1").build());
    when(workflowExecutionService.obtainLastGoodDeployedHelmCharts(APP_ID, WORKFLOW_ID))
        .thenReturn(asList(HelmChart.builder().uuid(HELM_CHART_ID + 2).serviceId(SERVICE_ID + 2).build()));
    ApplicationManifest appManifest =
        ApplicationManifest.builder().accountId(ACCOUNT_ID).storeType(StoreType.HelmChartRepo).build();
    appManifest.setUuid(MANIFEST_ID);
    when(applicationManifestService.getById(APP_ID, MANIFEST_ID)).thenReturn(appManifest);
    when(artifactService.getArtifactByBuildNumber(artifactStream, "FILTER", false))
        .thenReturn(anArtifact().withUuid(ARTIFACT_ID).build());
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactStream);
    ArgumentCaptor<ExecutionArgs> argsArgumentCaptor = ArgumentCaptor.forClass(ExecutionArgs.class);

    triggerService.triggerExecutionPostArtifactCollectionAsync(
        ACCOUNT_ID, APP_ID, ARTIFACT_STREAM_ID, Collections.singletonList(anArtifact().withUuid(ARTIFACT_ID).build()));

    verify(syncArtifactCollectionServiceImpl).collectNewArtifacts(APP_ID, ARTIFACT_STREAM_ID);
    verifyNoMoreInteractions(syncArtifactCollectionServiceImpl);
    verify(workflowExecutionService, times(1))
        .triggerEnvExecution(eq(APP_ID), any(), argsArgumentCaptor.capture(), eq(trigger));
    assertThat(argsArgumentCaptor.getValue().getHelmCharts()).hasSize(2);
    assertThat(argsArgumentCaptor.getValue().getHelmCharts().stream().map(HelmChart::getUuid))
        .containsExactlyInAnyOrder(HELM_CHART_ID, HELM_CHART_ID + 2);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldRejectTriggerForMasterFreeze() {
    triggerService.save(scheduledConditionTrigger);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(null), any(), eq(scheduledConditionTrigger)))
        .thenThrow(new DeploymentFreezeException(ErrorCode.DEPLOYMENT_GOVERNANCE_ERROR, Level.INFO, WingsException.USER,
            ACCOUNT_ID, Collections.emptyList(), Collections.emptyList(), null, true, false));

    TriggerServiceImpl triggerServiceImpl = (TriggerServiceImpl) triggerService;
    assertThatThrownBy(()
                           -> triggerServiceImpl.triggerDeployment(
                               Collections.emptyList(), Collections.emptyList(), null, scheduledConditionTrigger))
        .isInstanceOf(DeploymentFreezeException.class)
        .hasMessage("Master Deployment Freeze is active. No deployments are allowed.");
    verify(deploymentFreezeUtils, never()).sendTriggerRejectedNotification(eq(ACCOUNT_ID), eq(APP_ID), any(), anyMap());
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldAddAllTheCollectedArtifactsWhenThereAreNoSelections() {
    List<Artifact> collectedArtifacts = asList(anArtifact().withRevision("1").build(),
        anArtifact().withRevision("2").build(), anArtifact().withRevision("3").build());
    List<Artifact> selectedArtifacts = new ArrayList<>();
    triggerService.addArtifactsFromSelectionsTriggeringArtifactSource(
        APP_ID, Trigger.builder().build(), selectedArtifacts, collectedArtifacts);
    assertThat(selectedArtifacts).containsAll(collectedArtifacts);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldThrowErrorIfWebHookSecretNotGivenWithGithub() {
    when(featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, ACCOUNT_ID)).thenReturn(true);

    Trigger bitbucketTrigger = createWebHookTriggerWithSecret(BITBUCKET);
    assertThatThrownBy(() -> triggerService.save(bitbucketTrigger))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("WebHook Secret is only supported with Github repository");

    Trigger gitLabTrigger = createWebHookTriggerWithSecret(GITLAB);
    assertThatThrownBy(() -> triggerService.save(gitLabTrigger))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("WebHook Secret is only supported with Github repository");

    Trigger customTrigger = createWebHookTriggerWithSecret(null);
    assertThatThrownBy(() -> triggerService.save(customTrigger))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("WebHook Secret is only supported with Github repository");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldSaveWebHookSecretWithTrigger() {
    when(featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, ACCOUNT_ID)).thenReturn(true);
    wingsPersistence.save(EncryptedData.builder().uuid(SECRET_ID).build());

    Trigger trigger = triggerService.save(createWebHookTriggerWithSecret(GITHUB));
    assertThat(trigger).isNotNull();
    assertThat(trigger.getCondition()).isNotNull();
    WebHookTriggerCondition webHookTriggerCondition = (WebHookTriggerCondition) trigger.getCondition();
    assertThat(webHookTriggerCondition.getWebHookSecret()).isEqualTo(SECRET_ID);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldNotSaveWebHookSecretWhenFfOff() {
    assertThatThrownBy(() -> triggerService.save(createWebHookTriggerWithSecret(GITHUB)))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Please enable feature flag to authenticate your webhook sources");
  }

  private Trigger createWebHookTriggerWithSecret(WebhookSource webhookSource) {
    return Trigger.builder()
        .workflowId(WORKFLOW_ID)
        .workflowType(ORCHESTRATION)
        .uuid(TRIGGER_ID)
        .appId(APP_ID)
        .name(TRIGGER_NAME)
        .condition(WebHookTriggerCondition.builder()
                       .webhookSource(webhookSource)
                       .webHookToken(WebHookToken.builder().build())
                       .webHookSecret(SECRET_ID)
                       .parameters(ImmutableMap.of("MyVar", "MyVal"))
                       .build())
        .build();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testAddSecretParentOnTriggerService() {
    Trigger trigger = createWebHookTriggerWithSecret(GITHUB);
    when(featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, ACCOUNT_ID)).thenReturn(true);
    wingsPersistence.save(EncryptedData.builder().uuid(SECRET_ID).build());

    Trigger savedTrigger = triggerService.save(trigger);
    assertThat(savedTrigger).isNotNull();
    EncryptedData encryptedData = wingsPersistence.get(EncryptedData.class, SECRET_ID);
    assertThat(encryptedData).isNotNull();
    assertThat(encryptedData.getParents()).hasSize(1);
    assertThat(encryptedData.getParents())
        .isEqualTo(new HashSet(singletonList(
            new EncryptedDataParent(savedTrigger.getUuid(), SettingVariableTypes.TRIGGER, WEBHOOK_SECRET))));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testUpdateSecretParentOnTriggerService() {
    Trigger trigger = createWebHookTriggerWithSecret(GITHUB);
    when(featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, ACCOUNT_ID)).thenReturn(true);
    wingsPersistence.save(EncryptedData.builder().uuid(SECRET_ID).build());
    wingsPersistence.save(EncryptedData.builder().uuid(SECRET_ID + 2).build());
    triggerService.save(trigger);

    Trigger newTrigger = Trigger.builder()
                             .workflowId(WORKFLOW_ID)
                             .workflowType(ORCHESTRATION)
                             .uuid(TRIGGER_ID)
                             .appId(APP_ID)
                             .name(TRIGGER_NAME)
                             .accountId(ACCOUNT_ID)
                             .condition(WebHookTriggerCondition.builder()
                                            .webhookSource(GITHUB)
                                            .webHookToken(WebHookToken.builder().build())
                                            .webHookSecret(SECRET_ID + 2)
                                            .parameters(ImmutableMap.of("MyVar", "MyVal"))
                                            .build())
                             .build();

    Trigger updatedTrigger = triggerService.update(newTrigger, false);
    assertThat(updatedTrigger).isNotNull();
    EncryptedData oldEncryptedData = wingsPersistence.get(EncryptedData.class, SECRET_ID);
    assertThat(oldEncryptedData).isNotNull();
    assertThat(oldEncryptedData.getParents()).isEmpty();
    EncryptedData newEncryptedData = wingsPersistence.get(EncryptedData.class, SECRET_ID + 2);
    assertThat(newEncryptedData).isNotNull();
    assertThat(newEncryptedData.getParents()).hasSize(1);
    assertThat(newEncryptedData.getParents())
        .isEqualTo(new HashSet(singletonList(
            new EncryptedDataParent(updatedTrigger.getUuid(), SettingVariableTypes.TRIGGER, WEBHOOK_SECRET))));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testDeleteSecretParentOnTriggerService() {
    Trigger trigger = createWebHookTriggerWithSecret(GITHUB);
    when(featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, ACCOUNT_ID)).thenReturn(true);
    wingsPersistence.save(EncryptedData.builder().uuid(SECRET_ID).build());
    triggerService.save(trigger);

    triggerService.delete(APP_ID, TRIGGER_ID);
    EncryptedData encryptedData = wingsPersistence.get(EncryptedData.class, SECRET_ID);
    assertThat(encryptedData).isNotNull();
    assertThat(encryptedData.getParents()).isEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotRunDisabledTrigger() {
    Artifact artifact = prepareArtifact(ARTIFACT_ID);
    Artifact artifact2 = prepareArtifact(UUIDGenerator.generateUuid());

    scheduledTriggerMocks();

    when(workflowExecutionService.obtainLastGoodDeployedArtifacts(APP_ID, PIPELINE_ID, SERVICE_ID))
        .thenReturn(asList(artifact, artifact2));

    ScheduledTriggerCondition scheduledTriggerCondition =
        (ScheduledTriggerCondition) scheduledConditionTrigger.getCondition();
    scheduledTriggerCondition.setCronExpression("0 5 31 2 ?");
    assertThatThrownBy(() -> triggerService.save(scheduledConditionTrigger))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Given cron expression doesn't evaluate to a valid time. Please check the expression provided");
  }
}
