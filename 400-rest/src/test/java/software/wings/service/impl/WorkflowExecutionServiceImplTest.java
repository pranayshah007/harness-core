/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionStatus.ABORTED;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.PREPARING;
import static io.harness.beans.ExecutionStatus.RUNNING;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.ExecutionStatus.WAITING;
import static io.harness.beans.FeatureName.ARTIFACT_COLLECTION_CONFIGURABLE;
import static io.harness.beans.FeatureName.SPG_ENABLE_POPULATE_USING_ARTIFACT_VARIABLE;
import static io.harness.beans.FeatureName.WEBHOOK_TRIGGER_AUTHORIZATION;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.RAFAEL;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.VIKAS_S;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.threading.Poller.pollFor;

import static software.wings.api.DeploymentType.SSH;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.CustomOrchestrationWorkflow.CustomOrchestrationWorkflowBuilder.aCustomOrchestrationWorkflow;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.Graph.Builder.aGraph;
import static software.wings.beans.GraphLink.Builder.aLink;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhysicalDataCenterConfig.Builder.aPhysicalDataCenterConfig;
import static software.wings.beans.ServiceInstance.Builder.aServiceInstance;
import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.User.Builder.anUser;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.beans.infrastructure.Host.Builder.aHost;
import static software.wings.infra.InfraDefinitionTestConstants.RESOURCE_CONSTRAINT_NAME;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.settings.SettingVariableTypes.KUBERNETES;
import static software.wings.settings.SettingVariableTypes.PHYSICAL_DATA_CENTER;
import static software.wings.sm.ExecutionInterrupt.ExecutionInterruptBuilder.anExecutionInterrupt;
import static software.wings.sm.InstanceStatusSummary.InstanceStatusSummaryBuilder.anInstanceStatusSummary;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.StateMachine.StateMachineBuilder.aStateMachine;
import static software.wings.sm.StateType.APPROVAL;
import static software.wings.sm.StateType.ARTIFACT_COLLECTION;
import static software.wings.sm.StateType.EMAIL;
import static software.wings.sm.StateType.ENV_STATE;
import static software.wings.sm.WorkflowStandardParams.Builder.aWorkflowStandardParams;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_NAME;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.BUILD_NO;
import static software.wings.utils.WingsTestConstants.COMPANY_NAME;
import static software.wings.utils.WingsTestConstants.ENTITY_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.FREEZE_WINDOW_ID;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.MANIFEST_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.STATE_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.TRIGGER_ID;
import static software.wings.utils.WingsTestConstants.UUID;
import static software.wings.utils.WingsTestConstants.VARIABLE_NAME;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.ResourceConstraint;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.distribution.constraint.Constraint.Strategy;
import io.harness.distribution.constraint.Consumer.State;
import io.harness.exception.DeploymentFreezeException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.governance.TimeRangeBasedFreezeConfig;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.shell.AccessType;
import io.harness.state.inspection.StateInspection;
import io.harness.state.inspection.StateInspectionService;
import io.harness.threading.Poller;
import io.harness.waiter.OrchestrationNotifyEventListener;

import software.wings.WingsBaseTest;
import software.wings.api.CloudProviderType;
import software.wings.api.DeploymentType;
import software.wings.api.InstanceElement;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.WorkflowElement;
import software.wings.beans.Account;
import software.wings.beans.AccountStatus;
import software.wings.beans.AccountType;
import software.wings.beans.Application;
import software.wings.beans.ArtifactStreamMetadata;
import software.wings.beans.ArtifactVariable;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.CanaryWorkflowExecutionAdvisor;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.ElementExecutionSummary;
import software.wings.beans.Environment;
import software.wings.beans.Environment.Builder;
import software.wings.beans.ErrorStrategy;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.ExecutionStrategy;
import software.wings.beans.Graph;
import software.wings.beans.GraphNode;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.LicenseInfo;
import software.wings.beans.NameValuePair;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.PhaseStep;
import software.wings.beans.PhaseStepType;
import software.wings.beans.PhysicalInfrastructureMapping;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.PipelineStageExecution;
import software.wings.beans.ResourceConstraintInstance;
import software.wings.beans.Service;
import software.wings.beans.ServiceInstance;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TemplateExpression;
import software.wings.beans.User;
import software.wings.beans.VariableType;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowBuilder;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.artifact.ArtifactInput;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.beans.concurrency.ConcurrencyStrategy;
import software.wings.beans.concurrency.ConcurrencyStrategy.UnitType;
import software.wings.beans.concurrency.ConcurrentExecutionResponse;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.beans.governance.GovernanceConfig;
import software.wings.beans.infrastructure.Host;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.WebHookTriggerCondition;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.PhysicalInfra;
import software.wings.licensing.LicenseService;
import software.wings.persistence.artifact.Artifact;
import software.wings.resources.stats.model.TimeRange;
import software.wings.rules.Listeners;
import software.wings.scheduler.BackgroundJobScheduler;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.impl.pipeline.resume.PipelineResumeUtils;
import software.wings.service.impl.security.auth.DeploymentAuthHandler;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.BuildSourceService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ResourceConstraintService;
import software.wings.service.intfc.ServiceInstanceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.compliance.GovernanceConfigService;
import software.wings.service.intfc.security.SSHVaultService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionEventAdvisor;
import software.wings.sm.ExecutionInterrupt;
import software.wings.sm.InstanceStatusSummary;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;
import software.wings.sm.StateMachine;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.states.EnvState.EnvStateKeys;
import software.wings.sm.states.HoldingScope;
import software.wings.utils.ArtifactType;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * The type Workflow service impl test.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@Listeners({OrchestrationNotifyEventListener.class, ExecutionEventListener.class})
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class WorkflowExecutionServiceImplTest extends WingsBaseTest {
  private static final SecureRandom random = new SecureRandom();

  @Inject private WorkflowService workflowService;
  @Inject private PipelineService pipelineService;
  @Mock private BackgroundJobScheduler jobScheduler;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private ResourceConstraintService resourceConstraintService;
  @Mock private StateInspectionService stateInspectionService;
  @Inject @InjectMocks private AccountService accountService;
  @Inject @InjectMocks private LicenseService licenseService;
  @Inject @InjectMocks private WorkflowExecutionServiceImpl workflowExecutionService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject @InjectMocks private InfrastructureDefinitionService infrastructureDefinitionService;

  @Mock private SettingsService mockSettingsService;
  @Mock private ArtifactService artifactService;
  @Mock private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Mock private MultiArtifactWorkflowExecutionServiceHelper multiArtifactWorkflowExecutionServiceHelper;
  @Mock private PipelineResumeUtils pipelineResumeUtils;
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private BuildSourceService buildSourceService;
  @Mock private ArtifactCollectionUtils artifactCollectionUtils;
  @Mock private DeploymentAuthHandler deploymentAuthHandler;
  @Mock private AuthService authService;
  @Mock private GovernanceConfigService governanceConfigService;
  @Mock private SSHVaultService sshVaultService;
  @Mock private InfrastructureProvisionerService infrastructureProvisionerService;
  @Inject private ServiceInstanceService serviceInstanceService;

  @Rule public ExpectedException thrown = ExpectedException.none();

  private static final String PHASE_PARAM = "PHASE_PARAM";

  private Account account;
  private Application app;
  private Environment env;

  @Before
  public void setup() {
    when(jobScheduler.deleteJob(any(), any())).thenReturn(false);
    when(jobScheduler.scheduleJob(any(), any())).thenReturn(null);
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    licenseInfo.setAccountType(AccountType.PAID);
    licenseInfo.setLicenseUnits(10);
    account = accountService.save(Account.Builder.anAccount()
                                      .withCompanyName(COMPANY_NAME)
                                      .withAccountName(ACCOUNT_NAME)
                                      .withLicenseInfo(licenseInfo)
                                      .build(),
        false);
    app = wingsPersistence.saveAndGet(
        Application.class, anApplication().name(APP_NAME).accountId(account.getUuid()).build());
    env = wingsPersistence.saveAndGet(Environment.class, Builder.anEnvironment().appId(app.getUuid()).build());
    Artifact artifact = anArtifact()
                            .withAccountId(ACCOUNT_ID)
                            .withAppId(app.getAppId())
                            .withUuid(generateUuid())
                            .withDisplayName(ARTIFACT_NAME)
                            .withArtifactStreamId("artifactStreamId")
                            .build();
    when(artifactService.listByAppId(anyString())).thenReturn(singletonList(artifact));
    when(artifactService.listByIds(any(), any())).thenReturn(singletonList(artifact));
  }

  /**
   * Should trigger complex workflow.
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerComplexWorkflow() throws InterruptedException {
    Host host1 = aHost().withAppId(app.getUuid()).withEnvId(env.getUuid()).withHostName("host1").build();
    wingsPersistence.save(host1);
    Host host2 = aHost().withAppId(app.getUuid()).withEnvId(env.getUuid()).withHostName("host2").build();
    wingsPersistence.save(host2);

    Service service1 = addService("svc1");
    Service service2 = addService("svc2");
    ServiceTemplate serviceTemplate1 = wingsPersistence.saveAndGet(ServiceTemplate.class,
        aServiceTemplate()
            .withAppId(app.getUuid())
            .withEnvId(env.getUuid())
            .withServiceId(service1.getUuid())
            .withName(service1.getName())
            .withAppId(app.getUuid())
            .withDescription("TEMPLATE_DESCRIPTION")
            .build());
    ServiceTemplate serviceTemplate2 = wingsPersistence.saveAndGet(ServiceTemplate.class,
        aServiceTemplate()
            .withAppId(app.getUuid())
            .withEnvId(env.getUuid())
            .withServiceId(service2.getUuid())
            .withName(service2.getName())
            .withAppId(app.getUuid())
            .withDescription("TEMPLATE_DESCRIPTION")
            .build());

    ServiceInstance.Builder builder1 =
        aServiceInstance().withServiceTemplate(serviceTemplate1).withAppId(app.getUuid()).withEnvId(env.getUuid());
    ServiceInstance.Builder builder2 =
        aServiceInstance().withServiceTemplate(serviceTemplate2).withAppId(app.getUuid()).withEnvId(env.getUuid());

    ServiceInstance inst11 = serviceInstanceService.save(builder1.withHost(host1).build());
    ServiceInstance inst12 = serviceInstanceService.save(builder1.withHost(host2).build());
    ServiceInstance inst21 = serviceInstanceService.save(builder2.withHost(host1).build());
    ServiceInstance inst22 = serviceInstanceService.save(builder2.withHost(host2).build());

    Graph graph =
        aGraph()
            .addNodes(getGraphNode("Repeat By Services", ExecutionStrategy.SERIAL),
                GraphNode.builder()
                    .id("RepeatByInstances")
                    .name("RepeatByInstances")
                    .type(StateType.REPEAT.name())
                    .properties(ImmutableMap.<String, Object>builder()
                                    .put("repeatElementExpression", "${instances}")
                                    .put("executionStrategy", ExecutionStrategy.PARALLEL)
                                    .build())
                    .build(),
                GraphNode.builder()
                    .id("svcRepeatWait")
                    .name("svcRepeatWait")
                    .type(StateType.WAIT.name())
                    .properties(ImmutableMap.<String, Object>builder().put("duration", 1).build())
                    .build(),
                GraphNode.builder()
                    .id("instRepeatWait")
                    .name("instRepeatWait")
                    .type(StateType.WAIT.name())
                    .properties(ImmutableMap.<String, Object>builder().put("duration", 1).build())
                    .build(),
                GraphNode.builder()
                    .id("instSuccessWait")
                    .name("instSuccessWait")
                    .type(StateType.WAIT.name())
                    .properties(ImmutableMap.<String, Object>builder().put("duration", 1).build())
                    .build())
            .addLinks(
                aLink().withId("l1").withFrom("Repeat By Services").withTo("svcRepeatWait").withType("repeat").build())
            .addLinks(
                aLink().withId("l2").withFrom("svcRepeatWait").withTo("RepeatByInstances").withType("success").build())
            .addLinks(
                aLink().withId("l3").withFrom("RepeatByInstances").withTo("instRepeatWait").withType("repeat").build())
            .addLinks(aLink()
                          .withId("l4")
                          .withFrom("RepeatByInstances")
                          .withTo("instSuccessWait")
                          .withType("success")
                          .build())
            .build();

    Workflow workflow = aWorkflow()
                            .envId(env.getUuid())
                            .appId(app.getUuid())
                            .name("workflow1")
                            .description("Sample Workflow")
                            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().withGraph(graph).build())
                            .workflowType(WorkflowType.ORCHESTRATION)
                            .build();
    workflow = workflowService.createWorkflow(workflow);
    assertThat(workflow).isNotNull();
    assertThat(workflow.getUuid()).isNotNull();

    ExecutionArgs executionArgs = new ExecutionArgs();
    WorkflowExecutionUpdateFake callback = new WorkflowExecutionUpdateFake();
    WorkflowExecution execution = workflowExecutionService.triggerOrchestrationWorkflowExecution(
        app.getUuid(), env.getUuid(), workflow.getUuid(), null, executionArgs, callback, null);
    callback.await(ofSeconds(45));

    assertThat(execution).isNotNull();
    String executionId = execution.getUuid();
    log.debug("Workflow executionId: {}", executionId);
    assertThat(executionId).isNotNull();
    execution = workflowExecutionService.getExecutionDetails(app.getUuid(), executionId, true, false);
    assertThat(execution)
        .isNotNull()
        .extracting("uuid", "status")
        .containsExactly(executionId, ExecutionStatus.SUCCESS);
    assertThat(execution.getKeywords())
        .contains(workflow.getName().toLowerCase(), app.getName().toLowerCase(),
            env.getEnvironmentType().name().toLowerCase(), WorkflowType.ORCHESTRATION.name().toLowerCase());

    assertThat(execution.getExecutionNode())
        .isNotNull()
        .extracting("name", "type", "status")
        .containsExactly("Pre-Deployment", "PHASE_STEP", "SUCCESS");
    // TODO: decode this logic and fix it
    //    List<GraphNode> instRepeatElements = repeatInstance.stream()
    //                                             .map(GraphNode::getGroup)
    //                                             .flatMap(group -> group.getElements().stream())
    //                                             .collect(toList());
    //    assertThat(instRepeatElements).extracting("type").contains("ELEMENT", "ELEMENT", "ELEMENT", "ELEMENT");

    //    List<GraphNode> instRepeatWait = instRepeatElements.stream().map(GraphNode::getNext).collect(toList());
    //    assertThat(instRepeatWait)
    //        .isNotNull()
    //        .hasSize(4)
    //        .extracting("name")
    //        .contains("instRepeatWait", "instRepeatWait", "instRepeatWait", "instRepeatWait");
    //    assertThat(instRepeatWait).extracting("type").contains("WAIT", "WAIT", "WAIT", "WAIT");
  }

  /**
   * Trigger pipeline.
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void triggerPipeline() throws InterruptedException {
    Service service = addService("svc1");

    Pipeline pipeline = constructPipeline(service);

    triggerPipeline(app.getUuid(), pipeline);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldTriggerPipelineWithDeploymentMetaDataFFOn() {
    Service service = addService("svc1");

    Pipeline pipeline = constructPipeline(service);
    Artifact artifact =
        Artifact.Builder.anArtifact().withAccountId(ACCOUNT_ID).withAppId(APP_ID).withUuid(ARTIFACT_ID).build();
    List<ArtifactVariable> variables = singletonList(ArtifactVariable.builder()
                                                         .name(VARIABLE_NAME)
                                                         .entityId(ENTITY_ID)
                                                         .type(VariableType.ARTIFACT)
                                                         .value(artifact.getUuid())
                                                         .build());
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifactVariables(variables);
    executionArgs.setWorkflowType(WorkflowType.PIPELINE);
    executionArgs.setPipelineId(pipeline.getUuid());
    WorkflowExecution workflowExecution =
        workflowExecutionService.triggerEnvExecution(pipeline.getAppId(), null, executionArgs, null);

    assertThat(workflowExecution.getExecutionArgs().getArtifactVariables()).isNotNull();
  }

  private Pipeline constructPipeline(Service service) {
    Host host = aHost().withAppId(app.getUuid()).withEnvId(env.getUuid()).withHostName("host").build();
    wingsPersistence.save(host);

    ServiceTemplate serviceTemplate = getServiceTemplate(service);

    software.wings.beans.ServiceInstance.Builder builder =
        aServiceInstance().withServiceTemplate(serviceTemplate).withAppId(app.getUuid()).withEnvId(env.getUuid());

    ServiceInstance inst = serviceInstanceService.save(builder.withHost(host).build());

    Graph graph =
        aGraph()
            .addNodes(getGraphNode("Repeat By Services", ExecutionStrategy.SERIAL),
                GraphNode.builder()
                    .id("RepeatByInstances")
                    .name("RepeatByInstances")
                    .type(StateType.REPEAT.name())
                    .properties(ImmutableMap.<String, Object>builder()
                                    .put("repeatElementExpression", "${instances}")
                                    .put("executionStrategy", ExecutionStrategy.PARALLEL)
                                    .build())
                    .build(),
                GraphNode.builder()
                    .id("svcRepeatWait")
                    .name("svcRepeatWait")
                    .type(StateType.WAIT.name())
                    .properties(ImmutableMap.<String, Object>builder().put("duration", 1).build())
                    .build(),
                GraphNode.builder()
                    .id("instRepeatWait")
                    .name("instRepeatWait")
                    .type(StateType.WAIT.name())
                    .properties(ImmutableMap.<String, Object>builder().put("duration", 1).build())
                    .build(),
                GraphNode.builder()
                    .id("instSuccessWait")
                    .name("instSuccessWait")
                    .type(StateType.WAIT.name())
                    .properties(ImmutableMap.<String, Object>builder().put("duration", 1).build())
                    .build())
            .addLinks(
                aLink().withId("l1").withFrom("Repeat By Services").withTo("svcRepeatWait").withType("repeat").build())
            .addLinks(
                aLink().withId("l2").withFrom("svcRepeatWait").withTo("RepeatByInstances").withType("success").build())
            .addLinks(
                aLink().withId("l3").withFrom("RepeatByInstances").withTo("instRepeatWait").withType("repeat").build())
            .addLinks(aLink()
                          .withId("l4")
                          .withFrom("RepeatByInstances")
                          .withTo("instSuccessWait")
                          .withType("success")
                          .build())
            .build();

    Workflow workflow = aWorkflow()
                            .envId(env.getUuid())
                            .appId(app.getUuid())
                            .name("workflow1")
                            .description("Sample Workflow")
                            .workflowType(WorkflowType.ORCHESTRATION)
                            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().withGraph(graph).build())
                            .build();
    workflow = workflowService.createWorkflow(workflow);
    assertThat(workflow).isNotNull();
    assertThat(workflow.getUuid()).isNotNull();

    PipelineStage stag1 =
        PipelineStage.builder()
            .pipelineStageElements(
                asList(PipelineStageElement.builder()
                           .name("DEV")
                           .type(ENV_STATE.name())
                           .properties(ImmutableMap.of("envId", env.getUuid(), "workflowId", workflow.getUuid()))
                           .build()))
            .build();

    List<PipelineStage> pipelineStages = asList(stag1);

    Pipeline pipeline = Pipeline.builder()
                            .appId(app.getUuid())
                            .name("pipeline1")
                            .description("Sample Pipeline")
                            .pipelineStages(pipelineStages)
                            .build();

    pipeline = pipelineService.save(pipeline);
    assertThat(pipeline).isNotNull();
    assertThat(pipeline.getUuid()).isNotNull();
    return pipeline;
  }

  private WorkflowExecution triggerPipeline(String appId, Pipeline pipeline) throws InterruptedException {
    String artifactKey =
        wingsPersistence.save(anArtifact().withAppId(app.getUuid()).withDisplayName(ARTIFACT_NAME).build());
    Artifact artifact = wingsPersistence.getWithAppId(Artifact.class, app.getUuid(), artifactKey);
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(asList(artifact));
    executionArgs.setWorkflowType(WorkflowType.PIPELINE);

    WorkflowExecutionUpdateFake callback = new WorkflowExecutionUpdateFake();
    WorkflowExecution execution =
        ((WorkflowExecutionServiceImpl) workflowExecutionService)
            .triggerPipelineExecution(appId, pipeline.getUuid(), executionArgs, callback, null);
    callback.await(ofSeconds(15));

    assertThat(execution).isNotNull();
    String executionId = execution.getUuid();
    log.debug("Pipeline executionId: {}", executionId);
    assertThat(executionId).isNotNull();
    execution = workflowExecutionService.getExecutionDetails(appId, executionId, true, false);
    assertThat(execution)
        .isNotNull()
        .hasFieldOrProperty("displayName")
        .hasFieldOrProperty("releaseNo")
        .extracting(WorkflowExecution::getUuid, WorkflowExecution::getStatus)
        .containsExactly(executionId, ExecutionStatus.SUCCESS);

    return execution;
  }

  /**
   * Should trigger workflow.
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void shouldTriggerWorkflow() throws InterruptedException {
    String appId = app.getUuid();
    triggerWorkflow(appId, env, "workflow1");
  }

  /**
   * Should trigger workflow.
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerWorkflowWithRelease() throws InterruptedException {
    String appId = app.getUuid();
    Workflow workflow = createExecutableWorkflow(appId, env, "workflow1");
    WorkflowExecution workflowExecution = triggerWorkflow(workflow, env);
    assertThat(workflowExecution).isNotNull().hasFieldOrPropertyWithValue("releaseNo", "1");

    WorkflowElement workflowElement = getWorkflowElement(appId, workflowExecution);
    assertThat(workflowElement).isNotNull().hasFieldOrPropertyWithValue("releaseNo", "1");

    WorkflowExecution workflowExecution2 = triggerWorkflow(workflow, env);
    assertThat(workflowExecution2).isNotNull().hasFieldOrPropertyWithValue("releaseNo", "2");
    workflowElement = getWorkflowElement(appId, workflowExecution2);
    assertThat(workflowElement)
        .isNotNull()
        .extracting(
            "releaseNo", "displayName", "lastGoodDeploymentUuid", "lastGoodDeploymentDisplayName", "lastGoodReleaseNo")
        .containsExactly(workflowExecution2.getReleaseNo(), workflowExecution2.displayName(),
            workflowExecution.getUuid(), workflowExecution.displayName(), workflowExecution.getReleaseNo());
  }

  private WorkflowElement getWorkflowElement(String appId, WorkflowExecution workflowExecution) {
    StateExecutionInstance stateExecutionInstance =
        wingsPersistence.createQuery(StateExecutionInstance.class)
            .filter(StateExecutionInstanceKeys.appId, appId)
            .filter(StateExecutionInstanceKeys.executionUuid, workflowExecution.getUuid())
            .get();

    assertThat(stateExecutionInstance).isNotNull();
    assertThat(stateExecutionInstance.getContextElements()).isNotNull();
    Optional<ContextElement> first =
        stateExecutionInstance.getContextElements()
            .stream()
            .filter(contextElement -> contextElement.getElementType() == ContextElementType.STANDARD)
            .findFirst();
    assertThat(first.isPresent()).isTrue();
    assertThat(first.get()).isInstanceOf(WorkflowStandardParams.class).hasFieldOrProperty("workflowElement");

    return ((WorkflowStandardParams) first.get()).getWorkflowElement();
  }
  /**
   * Should get node details.
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldGetNodeDetails() throws InterruptedException {
    String appId = app.getUuid();

    final WorkflowExecution triggerWorkflow = triggerWorkflow(appId, env, "workflow1");
    WorkflowExecution execution =
        workflowExecutionService.getExecutionDetails(appId, triggerWorkflow.getUuid(), true, false);
    GraphNode node0 = execution.getExecutionNode();
    final GraphNode executionDetailsForNode =
        workflowExecutionService.getExecutionDetailsForNode(appId, execution.getUuid(), node0.getId());
    assertThat(executionDetailsForNode).isEqualToIgnoringGivenFields(node0, "next");
  }

  /**
   * Should update failed count.
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldUpdateFailedCount() throws InterruptedException {
    String appId = app.getUuid();
    triggerWorkflow(appId, env, "workflow1");
    WorkflowExecution workflowExecution =
        wingsPersistence.createQuery(WorkflowExecution.class).filter(WorkflowExecutionKeys.appId, appId).get();
    workflowExecutionService.incrementFailed(workflowExecution.getAppId(), workflowExecution.getUuid(), 1);
    workflowExecution =
        wingsPersistence.createQuery(WorkflowExecution.class).filter(WorkflowExecutionKeys.appId, appId).get();
    assertThat(workflowExecution.getBreakdown().getFailed()).isEqualTo(1);
    log.info("shouldUpdateFailedCount test done");
  }

  /**
   * Trigger workflow.
   *
   * @param appId the app id
   * @param env   the env
   * @return the string
   * @throws InterruptedException the interrupted exception
   */
  public WorkflowExecution triggerWorkflow(String appId, Environment env, String workflowName)
      throws InterruptedException {
    Workflow workflow = createExecutableWorkflow(appId, env, workflowName);
    return triggerWorkflow(workflow, env);
  }

  /**
   * Trigger workflow.
   *
   * @param env   the env
   * @return the string
   * @throws InterruptedException the interrupted exception
   */
  public WorkflowExecution triggerWorkflow(Workflow workflow, Environment env) throws InterruptedException {
    String appId = workflow.getAppId();
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(singletonList(
        Artifact.Builder.anArtifact().withAccountId(ACCOUNT_ID).withAppId(APP_ID).withUuid(ARTIFACT_ID).build()));

    WorkflowExecutionUpdateFake callback = new WorkflowExecutionUpdateFake();
    WorkflowExecution execution = workflowExecutionService.triggerOrchestrationWorkflowExecution(
        appId, env.getUuid(), workflow.getUuid(), null, executionArgs, callback, null);
    callback.await(ofSeconds(15));

    assertThat(execution).isNotNull();
    String executionId = execution.getUuid();
    log.debug("Workflow executionId: {}", executionId);
    assertThat(executionId).isNotNull();
    execution = workflowExecutionService.getExecutionDetails(appId, executionId, true, false);
    assertThat(execution)
        .isNotNull()
        .hasFieldOrProperty("releaseNo")
        .extracting(WorkflowExecution::getUuid, WorkflowExecution::getStatus)
        .containsExactly(executionId, ExecutionStatus.SUCCESS);
    return execution;
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldTriggerWorkflowFailForExpiredTrialLicense() throws InterruptedException {
    shouldTriggerWorkflowFailForExpiredLicense(AccountType.TRIAL);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldTriggerWorkflowFailForExpiredPaidLicense() throws InterruptedException {
    shouldTriggerWorkflowFailForExpiredLicense(AccountType.PAID);
  }

  private void shouldTriggerWorkflowFailForExpiredLicense(String accountType) throws InterruptedException {
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(accountType);
    licenseInfo.setAccountStatus(AccountStatus.EXPIRED);
    licenseInfo.setExpiryTime(System.currentTimeMillis() + 5000);
    account.setLicenseInfo(licenseInfo);
    licenseService.updateAccountLicense(account.getUuid(), licenseInfo);

    Thread.sleep(10000);
    Workflow workflow = createExecutableWorkflow(app.getUuid(), env, "workflow1");
    String appId = workflow.getAppId();
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(asList(Artifact.Builder.anArtifact().withAppId(APP_ID).withUuid(ARTIFACT_ID).build()));

    WorkflowExecutionUpdateFake callback = new WorkflowExecutionUpdateFake();
    thrown.expect(WingsException.class);
    workflowExecutionService.triggerOrchestrationWorkflowExecution(
        appId, env.getUuid(), workflow.getUuid(), null, executionArgs, callback, null);

    // Scenario 2, update the license to be valid and test again.

    licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(accountType);
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    licenseInfo.setExpiryTime(System.currentTimeMillis() + 1000000);
    account.setLicenseInfo(licenseInfo);
    licenseService.updateAccountLicense(account.getUuid(), licenseInfo);

    WorkflowExecution workflowExecution = workflowExecutionService.triggerOrchestrationWorkflowExecution(
        appId, env.getUuid(), workflow.getUuid(), null, executionArgs, callback, null);
    assertThat(workflowExecution).isNotNull();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldTriggerPipelineFailForExpiredTrialLicense() throws InterruptedException {
    shouldTriggerPipelineFailForExpiredLicense(AccountType.TRIAL);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldTriggerPipelineFailForExpiredPaidLicense() throws InterruptedException {
    shouldTriggerPipelineFailForExpiredLicense(AccountType.PAID);
  }

  private void shouldTriggerPipelineFailForExpiredLicense(String accountType) throws InterruptedException {
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(accountType);
    licenseInfo.setAccountStatus(AccountStatus.EXPIRED);
    licenseInfo.setExpiryTime(System.currentTimeMillis() + 5000);
    account.setLicenseInfo(licenseInfo);
    licenseService.updateAccountLicense(account.getUuid(), licenseInfo);

    Thread.sleep(10000);
    Service service = addService("svc1");

    Pipeline pipeline = constructPipeline(service);
    thrown.expect(WingsException.class);
    triggerPipeline(app.getUuid(), pipeline);

    // Scenario 2, update the license to be valid and test again.
    licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(accountType);
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    licenseInfo.setExpiryTime(System.currentTimeMillis() + 1000000);
    account.setLicenseInfo(licenseInfo);
    licenseService.updateAccountLicense(account.getUuid(), licenseInfo);

    WorkflowExecution workflowExecution = triggerPipeline(app.getUuid(), pipeline);
    assertThat(workflowExecution).isNotNull();
  }

  private Workflow createExecutableWorkflow(String appId, Environment env, String workflowName) {
    Graph graph = getGraphForExecutableWorkflow();

    Workflow workflow = aWorkflow()
                            .envId(env.getUuid())
                            .appId(appId)
                            .name(workflowName)
                            .description("Sample Workflow")
                            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().withGraph(graph).build())
                            .workflowType(WorkflowType.ORCHESTRATION)
                            .build();
    workflow = workflowService.createWorkflow(workflow);
    assertThat(workflow).isNotNull();
    assertThat(workflow.getUuid()).isNotNull();
    return workflow;
  }

  private Workflow createExecutableWorkflowWithThrottling(String appId, Environment env, String workflowName) {
    Graph graph = getGraphForExecutableWorkflow();

    Workflow workflow = aWorkflow()
                            .envId(env.getUuid())
                            .appId(appId)
                            .name(workflowName)
                            .description("Sample Workflow")
                            .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                                       .withGraph(graph)
                                                       .withConcurrencyStrategy(ConcurrencyStrategy.builder().build())
                                                       .build())
                            .workflowType(WorkflowType.ORCHESTRATION)
                            .build();
    workflow = workflowService.createWorkflow(workflow);
    assertThat(workflow).isNotNull();
    assertThat(workflow.getUuid()).isNotNull();
    return workflow;
  }

  private Graph getGraphForExecutableWorkflow() {
    return aGraph()
        .addNodes(GraphNode.builder()
                      .id("n1")
                      .name("wait")
                      .type(StateType.WAIT.name())
                      .properties(ImmutableMap.<String, Object>builder().put("duration", 1L).build())
                      .origin(true)
                      .build(),
            GraphNode.builder()
                .id("n2")
                .name("email")
                .type(EMAIL.name())
                .properties(ImmutableMap.<String, Object>builder()
                                .put("toAddress", "a@b.com")
                                .put("subject", "testing")
                                .build())
                .build())
        .addLinks(aLink().withId("l1").withFrom("n1").withTo("n2").withType("success").build())
        .build();
  }

  /**
   * Should list workflow.
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void shouldListWorkflow() throws InterruptedException {
    String appId = app.getUuid();

    triggerWorkflow(appId, env, "workflow1");

    // 2nd workflow
    createExecutableWorkflow(appId, env, "workflow2");
    PageRequest<Workflow> pageRequest = aPageRequest().addFilter(Workflow.APP_ID_KEY2, EQ, appId).build();
    PageResponse<Workflow> res = workflowService.listWorkflows(pageRequest, null, false, null);

    assertThat(res).isNotNull().hasSize(2);
  }

  /**
   * Should pause and resume
   *
   * @throws InterruptedException the interrupted exception
   */
  // TODO - Fix this, it's failing in Jenkins - almost all the time
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldPauseAllAndResumeAllState() throws InterruptedException {
    Service service1 = addService("svc1");
    Service service2 = addService("svc2");

    Graph graph = getGraph();

    Workflow workflow =
        aWorkflow()
            .envId(env.getUuid())
            .appId(app.getUuid())
            .name("workflow1")
            .description("Sample Workflow")
            .orchestrationWorkflow(aCustomOrchestrationWorkflow().withValid(true).withGraph(graph).build())
            .workflowType(WorkflowType.ORCHESTRATION)
            .build();
    workflow = workflowService.createWorkflow(workflow);
    assertThat(workflow).isNotNull();
    assertThat(workflow.getUuid()).isNotNull();
    ExecutionArgs executionArgs = new ExecutionArgs();
    WorkflowExecutionUpdateFake callback = new WorkflowExecutionUpdateFake();
    WorkflowExecution execution = workflowExecutionService.triggerOrchestrationWorkflowExecution(
        app.getUuid(), env.getUuid(), workflow.getUuid(), null, executionArgs, callback, null);

    assertThat(execution).isNotNull();
    String executionId = execution.getUuid();
    log.debug("Workflow executionId: {}", executionId);
    assertThat(executionId).isNotNull();

    pollFor(ofSeconds(3), ofMillis(100), () -> {
      final WorkflowExecution pull =
          workflowExecutionService.getExecutionDetails(app.getUuid(), executionId, true, false);
      return pull.getStatus() == ExecutionStatus.RUNNING;
    });

    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .executionInterruptType(ExecutionInterruptType.PAUSE_ALL)
                                                .executionUuid(executionId)
                                                .envId(env.getUuid())
                                                .build();
    executionInterrupt = workflowExecutionService.triggerExecutionInterrupt(executionInterrupt);
    assertThat(executionInterrupt).isNotNull().hasFieldOrProperty("uuid");

    pollFor(ofSeconds(15), ofMillis(100), () -> {
      final WorkflowExecution pull =
          workflowExecutionService.getExecutionDetails(app.getUuid(), executionId, true, false);
      return pull.getStatus() == ExecutionStatus.PAUSED && pull.getExecutionNode().getGroup() != null;
    });

    execution = workflowExecutionService.getExecutionDetails(app.getUuid(), executionId, true, false);

    List<GraphNode> wait1List = execution.getExecutionNode()
                                    .getGroup()
                                    .getElements()
                                    .stream()
                                    .filter(n -> n.getNext() != null)
                                    .map(GraphNode::getNext)
                                    .collect(toList());
    List<GraphNode> wait2List =
        wait1List.stream().filter(n -> n.getNext() != null).map(GraphNode::getNext).collect(toList());

    assertThat(execution).isNotNull().extracting("uuid", "status").containsExactly(executionId, ExecutionStatus.PAUSED);
    assertThat(execution.getExecutionNode())
        .extracting("name", "type", "status")
        .containsExactly("RepeatByServices", "REPEAT", "RUNNING");
    assertThat(wait1List)
        .filteredOn("name", "wait1")
        .hasSize(2)
        .allMatch(n -> "WAIT".equals(n.getType()) && "SUCCESS".equals(n.getStatus()));
    assertThat(wait2List)
        .filteredOn("name", "wait2")
        .hasSize(2)
        .allMatch(n -> "WAIT".equals(n.getType()) && "PAUSED".equals(n.getStatus()));

    executionInterrupt = anExecutionInterrupt()
                             .appId(app.getUuid())
                             .executionInterruptType(ExecutionInterruptType.RESUME_ALL)
                             .executionUuid(executionId)
                             .envId(env.getUuid())
                             .build();
    executionInterrupt = workflowExecutionService.triggerExecutionInterrupt(executionInterrupt);
    assertThat(executionInterrupt).isNotNull().hasFieldOrProperty("uuid");

    callback.await(ofSeconds(15));

    execution = workflowExecutionService.getExecutionDetails(app.getUuid(), executionId, true, false);
    assertThat(execution)
        .isNotNull()
        .extracting("uuid", "status")
        .containsExactly(executionId, ExecutionStatus.SUCCESS);

    wait1List = execution.getExecutionNode()
                    .getGroup()
                    .getElements()
                    .stream()
                    .filter(n -> n.getNext() != null)
                    .map(GraphNode::getNext)
                    .collect(toList());
    wait2List = wait1List.stream().filter(n -> n.getNext() != null).map(GraphNode::getNext).collect(toList());

    assertThat(execution.getExecutionNode())
        .extracting("name", "type", "status")
        .containsExactly("RepeatByServices", "REPEAT", "SUCCESS");
    assertThat(wait1List)
        .filteredOn("name", "wait1")
        .hasSize(2)
        .allMatch(n -> "WAIT".equals(n.getType()) && "SUCCESS".equals(n.getStatus()));
    assertThat(wait2List)
        .filteredOn("name", "wait2")
        .hasSize(2)
        .allMatch(n -> "WAIT".equals(n.getType()) && "SUCCESS".equals(n.getStatus()));
  }

  private Graph getGraph() {
    return aGraph()
        .addNodes(getGraphNode("RepeatByServices", ExecutionStrategy.PARALLEL),
            GraphNode.builder()
                .id("wait1")
                .name("wait1")
                .type(StateType.WAIT.name())
                .properties(ImmutableMap.<String, Object>builder().put("duration", 1).build())
                .build(),
            GraphNode.builder()
                .id("wait2")
                .name("wait2")
                .type(StateType.WAIT.name())
                .properties(ImmutableMap.<String, Object>builder().put("duration", 1).build())
                .build())
        .addLinks(aLink().withId("l1").withFrom("RepeatByServices").withTo("wait1").withType("repeat").build())
        .addLinks(aLink().withId("l2").withFrom("wait1").withTo("wait2").withType("success").build())
        .build();
  }

  /**
   * Should throw invalid argument for invalid workflow id.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldThrowInvalidArgumentForInvalidWorkflowId() {
    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .executionInterruptType(ExecutionInterruptType.PAUSE)
                                                .envId(env.getUuid())
                                                .executionUuid(generateUuid())
                                                .build();
    try {
      executionInterrupt = workflowExecutionService.triggerExecutionInterrupt(executionInterrupt);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception.getParams().values()).doesNotContainNull();
      assertThat(exception.getParams().values().iterator().next())
          .isInstanceOf(String.class)
          .asString()
          .startsWith("Workflow execution does not exist.");
    }
  }

  private Graph getAbortedGraph() {
    return aGraph()
        .addNodes(GraphNode.builder()
                      .id("wait1")
                      .origin(true)
                      .name("wait1")
                      .type(StateType.WAIT.name())
                      .properties(ImmutableMap.<String, Object>builder().put("duration", 1).build())
                      .build(),
            GraphNode.builder()
                .id("pause1")
                .name("pause1")
                .type(StateType.PAUSE.name())
                .properties(ImmutableMap.<String, Object>builder().put("toAddress", "to1").build())
                .build(),
            GraphNode.builder()
                .id("wait2")
                .name("wait2")
                .type(StateType.WAIT.name())
                .properties(ImmutableMap.<String, Object>builder().put("duration", 1).build())
                .build())
        .addLinks(aLink().withId("l1").withFrom("wait1").withTo("pause1").withType("success").build())
        .addLinks(aLink().withId("l2").withFrom("pause1").withTo("wait2").withType("success").build())
        .build();
  }

  /**
   * Should abort all
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = SRINIVAS, intermittent = true)
  @Category(UnitTests.class)
  public void shouldAbortAllStates() {
    Service service1 = addService("svc1");
    Service service2 = addService("svc2");

    Graph graph = getGraph();

    Workflow workflow =
        aWorkflow()
            .envId(env.getUuid())
            .appId(app.getUuid())
            .name("workflow1")
            .description("Sample Workflow")
            .orchestrationWorkflow(aCustomOrchestrationWorkflow().withValid(true).withGraph(graph).build())
            .workflowType(WorkflowType.ORCHESTRATION)
            .build();
    workflow = workflowService.createWorkflow(workflow);
    assertThat(workflow).isNotNull();
    assertThat(workflow.getUuid()).isNotNull();
    ExecutionArgs executionArgs = new ExecutionArgs();
    WorkflowExecutionUpdateFake callback = new WorkflowExecutionUpdateFake();
    WorkflowExecution execution = workflowExecutionService.triggerOrchestrationWorkflowExecution(
        app.getUuid(), env.getUuid(), workflow.getUuid(), null, executionArgs, callback, null);

    assertThat(execution).isNotNull();
    String executionId = execution.getUuid();
    log.debug("Workflow executionId: {}", executionId);
    assertThat(executionId).isNotNull();

    pollFor(ofSeconds(5), ofMillis(100), () -> {
      final WorkflowExecution pull =
          workflowExecutionService.getExecutionDetails(app.getUuid(), executionId, true, false);
      return pull.getStatus() == ExecutionStatus.RUNNING;
    });

    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .executionInterruptType(ExecutionInterruptType.ABORT_ALL)
                                                .executionUuid(executionId)
                                                .envId(env.getUuid())
                                                .build();
    executionInterrupt = workflowExecutionService.triggerExecutionInterrupt(executionInterrupt);
    assertThat(executionInterrupt).isNotNull().hasFieldOrProperty("uuid");

    pollFor(ofSeconds(15), ofMillis(100), () -> {
      final WorkflowExecution pull =
          workflowExecutionService.getExecutionDetails(app.getUuid(), executionId, true, false);
      return pull.getStatus() == ExecutionStatus.ABORTED;
    });

    execution = workflowExecutionService.getExecutionDetails(app.getUuid(), executionId, true, false);

    assertThat(execution)
        .isNotNull()
        .extracting("uuid", "status")
        .containsExactly(executionId, ExecutionStatus.ABORTED);

    assertThat(execution.getExecutionNode()).isNotNull();
  }

  /**
   * Should wait on error
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldWaitOnError() throws InterruptedException {
    Host applicationHost1 = aHost().withAppId(app.getAppId()).withEnvId(env.getUuid()).withHostName("host1").build();
    wingsPersistence.save(applicationHost1);
    Host applicationHost2 = aHost().withAppId(app.getAppId()).withEnvId(env.getUuid()).withHostName("host2").build();
    wingsPersistence.save(applicationHost2);

    Service service = addService("svc1");
    ServiceTemplate serviceTemplate = getServiceTemplate(service);

    software.wings.beans.ServiceInstance.Builder builder =
        aServiceInstance().withServiceTemplate(serviceTemplate).withAppId(app.getUuid()).withEnvId(env.getUuid());

    ServiceInstance inst1 = serviceInstanceService.save(builder.withHost(applicationHost1).build());
    ServiceInstance inst2 = serviceInstanceService.save(builder.withHost(applicationHost2).build());

    Graph graph =
        aGraph()
            .addNodes(getGraphNode("RepeatByServices", ExecutionStrategy.PARALLEL),
                GraphNode.builder()
                    .id("RepeatByInstances")
                    .name("RepeatByInstances")
                    .type(StateType.REPEAT.name())
                    .properties(ImmutableMap.<String, Object>builder()
                                    .put("repeatElementExpression", "${instances()}")
                                    .put("executionStrategy", ExecutionStrategy.SERIAL)
                                    .build())
                    .build(),
                GraphNode.builder()
                    .id("install")
                    .name("install")
                    .type(StateType.COMMAND.name())
                    .properties(ImmutableMap.<String, Object>builder().put("command", "install").build())
                    .build())
            .addLinks(aLink()
                          .withId("l1")
                          .withFrom("RepeatByServices")
                          .withTo("RepeatByInstances")
                          .withType("repeat")
                          .build())
            .addLinks(aLink().withId("l2").withFrom("RepeatByInstances").withTo("install").withType("repeat").build())
            .build();

    Workflow workflow =
        aWorkflow()
            .envId(env.getUuid())
            .appId(app.getUuid())
            .name("workflow1")
            .description("Sample Workflow")
            .orchestrationWorkflow(aCustomOrchestrationWorkflow().withValid(true).withGraph(graph).build())
            .workflowType(WorkflowType.ORCHESTRATION)
            .build();
    workflow = workflowService.createWorkflow(workflow);
    assertThat(workflow).isNotNull();
    assertThat(workflow.getUuid()).isNotNull();
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setErrorStrategy(ErrorStrategy.PAUSE);
    WorkflowExecutionUpdateFake callback = new WorkflowExecutionUpdateFake();
    WorkflowExecution execution = ((WorkflowExecutionServiceImpl) workflowExecutionService)
                                      .triggerOrchestrationWorkflowExecution(app.getUuid(), env.getUuid(),
                                          workflow.getUuid(), null, executionArgs, callback, null);

    assertThat(execution).isNotNull();
    String executionId = execution.getUuid();
    log.debug("Workflow executionId: {}", executionId);
    assertThat(executionId).isNotNull();

    List<GraphNode> installNodes = getNodes(executionId);

    execution = workflowExecutionService.getExecutionDetails(app.getUuid(), executionId, true, false);
    assertThat(execution)
        .isNotNull()
        .hasFieldOrPropertyWithValue("uuid", executionId)
        .hasFieldOrPropertyWithValue("status", WAITING);
    assertThat(installNodes)
        .isNotNull()
        .doesNotContainNull()
        .filteredOn("name", "install")
        .hasSize(1)
        .extracting("status")
        .containsExactly(WAITING.name());

    GraphNode installNode = installNodes.get(0);
    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .envId(env.getUuid())
                                                .executionUuid(executionId)
                                                .stateExecutionInstanceId(installNode.getId())
                                                .executionInterruptType(ExecutionInterruptType.MARK_SUCCESS)
                                                .build();
    workflowExecutionService.triggerExecutionInterrupt(executionInterrupt);

    installNodes = getNodes(executionId);

    assertThat(execution)
        .isNotNull()
        .hasFieldOrPropertyWithValue("uuid", executionId)
        .hasFieldOrPropertyWithValue("status", WAITING);
    assertThat(installNodes)
        .isNotNull()
        .doesNotContainNull()
        .filteredOn("name", "install")
        .hasSize(2)
        .extracting("status")
        .contains(ExecutionStatus.SUCCESS.name(), WAITING.name());

    installNode = installNodes.stream()
                      .filter(n -> n.getStatus() != null && n.getStatus().equals(WAITING.name()))
                      .collect(toList())
                      .get(0);
    executionInterrupt = anExecutionInterrupt()
                             .appId(app.getUuid())
                             .envId(env.getUuid())
                             .executionUuid(executionId)
                             .stateExecutionInstanceId(installNode.getId())
                             .executionInterruptType(ExecutionInterruptType.IGNORE)
                             .build();
    workflowExecutionService.triggerExecutionInterrupt(executionInterrupt);
    callback.await(ofSeconds(15));

    execution = workflowExecutionService.getExecutionDetails(app.getUuid(), executionId, true, false);
    assertThat(execution)
        .isNotNull()
        .hasFieldOrPropertyWithValue("uuid", executionId)
        .hasFieldOrPropertyWithValue("status", ExecutionStatus.SUCCESS);
    installNodes = execution.getExecutionNode()
                       .getGroup()
                       .getElements()
                       .stream()
                       .filter(node -> node.getNext() != null)
                       .map(GraphNode::getNext)
                       .filter(node -> node.getGroup() != null)
                       .map(GraphNode::getGroup)
                       .filter(group -> group.getElements() != null)
                       .flatMap(group -> group.getElements().stream())
                       .filter(node -> node.getNext() != null)
                       .map(GraphNode::getNext)
                       .collect(toList());
    assertThat(installNodes)
        .isNotNull()
        .doesNotContainNull()
        .filteredOn("name", "install")
        .hasSize(2)
        .extracting("status")
        .containsExactly(ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name());
  }

  private List<GraphNode> installNodes(WorkflowExecution execution) {
    return execution.getExecutionNode()
        .getGroup()
        .getElements()
        .stream()
        .map(GraphNode::getNext)
        .filter(Objects::nonNull)
        .map(GraphNode::getGroup)
        .filter(Objects::nonNull)
        .filter(group -> group.getElements() != null)
        .flatMap(group -> group.getElements().stream())
        .map(GraphNode::getNext)
        .filter(Objects::nonNull)
        .collect(toList());
  }

  private List<GraphNode> getNodes(String executionId) {
    Poller.pollFor(ofSeconds(10), ofMillis(100), () -> {
      WorkflowExecution execution =
          workflowExecutionService.getExecutionDetails(app.getUuid(), executionId, true, false);
      if (execution.getExecutionNode() == null) {
        return false;
      }
      if (execution.getExecutionNode().getGroup() == null) {
        return false;
      }
      if (execution.getExecutionNode().getGroup().getElements() == null) {
        return false;
      }

      return installNodes(execution).stream().anyMatch(
          n -> n.getStatus() != null && n.getStatus().equals(WAITING.name()));
    });

    return installNodes(workflowExecutionService.getExecutionDetails(app.getUuid(), executionId, true, false));
  }

  /**
   * Should retry on error
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldRetryOnError() throws InterruptedException {
    Host host1 = aHost().withAppId(app.getUuid()).withEnvId(env.getUuid()).withHostName("host1").build();
    wingsPersistence.save(host1);

    Service service = addService("svc1");
    ServiceTemplate serviceTemplate = getServiceTemplate(service);

    software.wings.beans.ServiceInstance.Builder builder =
        aServiceInstance().withServiceTemplate(serviceTemplate).withAppId(app.getUuid()).withEnvId(env.getUuid());

    ServiceInstance inst1 = serviceInstanceService.save(builder.withHost(host1).build());

    Graph graph = constructGraph();

    Workflow workflow =
        aWorkflow()
            .envId(env.getUuid())
            .appId(app.getUuid())
            .name("workflow1")
            .description("Sample Workflow")
            .orchestrationWorkflow(aCustomOrchestrationWorkflow().withValid(true).withGraph(graph).build())
            .workflowType(WorkflowType.ORCHESTRATION)
            .build();
    workflow = workflowService.createWorkflow(workflow);
    assertThat(workflow).isNotNull();
    assertThat(workflow.getUuid()).isNotNull();
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setErrorStrategy(ErrorStrategy.PAUSE);
    WorkflowExecutionUpdateFake callback = new WorkflowExecutionUpdateFake();
    WorkflowExecution execution = workflowExecutionService.triggerOrchestrationWorkflowExecution(
        app.getUuid(), env.getUuid(), workflow.getUuid(), null, executionArgs, callback, null);

    assertThat(execution).isNotNull();
    String executionId = execution.getUuid();
    log.debug("Workflow executionId: {}", executionId);
    assertThat(executionId).isNotNull();

    List<GraphNode> installNodes = getNodes(executionId);

    execution = workflowExecutionService.getExecutionDetails(app.getUuid(), executionId, true, false);
    assertThat(execution)
        .isNotNull()
        .hasFieldOrPropertyWithValue("uuid", executionId)
        .hasFieldOrPropertyWithValue("status", WAITING);

    assertThat(installNodes)
        .isNotNull()
        .doesNotContainNull()
        .filteredOn("name", "install")
        .hasSize(1)
        .extracting("status")
        .containsExactly(WAITING.name());

    GraphNode installNode = installNodes.get(0);
    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .envId(env.getUuid())
                                                .executionUuid(executionId)
                                                .stateExecutionInstanceId(installNode.getId())
                                                .executionInterruptType(ExecutionInterruptType.RETRY)
                                                .build();
    workflowExecutionService.triggerExecutionInterrupt(executionInterrupt);

    installNodes = getNodes(executionId);

    assertThat(execution)
        .isNotNull()
        .hasFieldOrPropertyWithValue("uuid", executionId)
        .hasFieldOrPropertyWithValue("status", WAITING);
    assertThat(installNodes)
        .isNotNull()
        .doesNotContainNull()
        .filteredOn("name", "install")
        .hasSize(1)
        .extracting("status")
        .containsExactly(WAITING.name());

    installNode = installNodes.get(0);
    executionInterrupt = anExecutionInterrupt()
                             .appId(app.getUuid())
                             .envId(env.getUuid())
                             .executionUuid(executionId)
                             .stateExecutionInstanceId(installNode.getId())
                             .executionInterruptType(ExecutionInterruptType.MARK_SUCCESS)
                             .build();
    workflowExecutionService.triggerExecutionInterrupt(executionInterrupt);
    callback.await(ofSeconds(15));

    execution = workflowExecutionService.getExecutionDetails(app.getUuid(), executionId, true, false);
    assertThat(execution)
        .isNotNull()
        .hasFieldOrPropertyWithValue("uuid", executionId)
        .hasFieldOrPropertyWithValue("status", ExecutionStatus.SUCCESS);

    installNodes = execution.getExecutionNode()
                       .getGroup()
                       .getElements()
                       .stream()
                       .filter(node -> node.getNext() != null)
                       .map(GraphNode::getNext)
                       .filter(node -> node.getGroup() != null)
                       .map(GraphNode::getGroup)
                       .filter(group -> group.getElements() != null)
                       .flatMap(group -> group.getElements().stream())
                       .filter(node -> node.getNext() != null)
                       .map(GraphNode::getNext)
                       .collect(toList());
    assertThat(installNodes)
        .isNotNull()
        .doesNotContainNull()
        .filteredOn("name", "install")
        .hasSize(1)
        .extracting("status")
        .containsExactly(ExecutionStatus.SUCCESS.name());
  }

  private Graph constructGraph() {
    return aGraph()
        .addNodes(getGraphNode("RepeatByServices", ExecutionStrategy.PARALLEL),
            GraphNode.builder()
                .id("RepeatByInstances")
                .name("RepeatByInstances")
                .type(StateType.REPEAT.name())
                .properties(ImmutableMap.<String, Object>builder()
                                .put("repeatElementExpression", "${instances()}")
                                .put("executionStrategy", ExecutionStrategy.SERIAL)
                                .build())
                .build(),
            GraphNode.builder()
                .id("install")
                .name("install")
                .type(StateType.COMMAND.name())
                .properties(ImmutableMap.<String, Object>builder().put("command", "install").build())
                .build())
        .addLinks(
            aLink().withId("l1").withFrom("RepeatByServices").withTo("RepeatByInstances").withType("repeat").build())
        .addLinks(aLink().withId("l2").withFrom("RepeatByInstances").withTo("install").withType("repeat").build())
        .build();
  }

  private GraphNode getGraphNode(String repeatByServices, ExecutionStrategy parallel) {
    return GraphNode.builder()
        .id(repeatByServices)
        .origin(true)
        .name(repeatByServices)
        .type(StateType.REPEAT.name())
        .properties(ImmutableMap.<String, Object>builder()
                        .put("repeatElementExpression", "${services()}")
                        .put("executionStrategy", parallel)
                        .build())
        .build();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerCanaryWorkflow() throws InterruptedException {
    Service service = addService("svc1");

    ServiceTemplate serviceTemplate = getServiceTemplate(service);

    SettingAttribute computeProvider =
        aSettingAttribute().withAppId(app.getUuid()).withValue(aPhysicalDataCenterConfig().build()).build();
    when(mockSettingsService.getByAccountAndId(any(), any())).thenReturn(computeProvider);
    wingsPersistence.save(computeProvider);

    final InfrastructureDefinition infraDefinition = createInfraDefinition(computeProvider, "Name4", "host1");

    triggerWorkflow(app.getAppId(), env, service, infraDefinition);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTriggerTemplateCanaryWorkflow() throws InterruptedException {
    Service service1 = addService("svc1");

    Service service2 = addService("svc2");

    ServiceTemplate serviceTemplate1 = getServiceTemplate(service1);

    ServiceTemplate serviceTemplate2 = getServiceTemplate(service2);

    SettingAttribute computeProvider =
        aSettingAttribute().withAppId(app.getUuid()).withValue(aPhysicalDataCenterConfig().build()).build();
    when(mockSettingsService.getByAccountAndId(any(), any())).thenReturn(computeProvider);
    wingsPersistence.save(computeProvider);

    final InfrastructureDefinition infrastructureDefinition =
        infrastructureDefinitionService.save(InfrastructureDefinition.builder()
                                                 .name("Name2")
                                                 .appId(app.getUuid())
                                                 .accountId(app.getAccountId())
                                                 .envId(env.getUuid())
                                                 .deploymentType(SSH)
                                                 .cloudProviderType(CloudProviderType.PHYSICAL_DATA_CENTER)
                                                 .infrastructure(PhysicalInfra.builder()
                                                                     .hostNames(Lists.newArrayList("host1"))
                                                                     .cloudProviderId(computeProvider.getUuid())
                                                                     .hostConnectionAttrs(AccessType.KEY.name())
                                                                     .build())
                                                 .build(),
            false);

    final InfrastructureDefinition templateInfraDefinition = createInfraDefinition(computeProvider, "Name3", "host12");
    triggerTemplateWorkflow(app.getAppId(), env, service1, service2, infrastructureDefinition, templateInfraDefinition);
  }

  private ServiceTemplate getServiceTemplate(Service service) {
    return wingsPersistence.saveAndGet(ServiceTemplate.class,
        aServiceTemplate()
            .withAppId(app.getUuid())
            .withEnvId(env.getUuid())
            .withServiceId(service.getUuid())
            .withName("TEMPLATE_NAME")
            .withDescription("TEMPLATE_DESCRIPTION")
            .build());
  }

  /**
   * Trigger workflow.
   *
   * @param appId                 the app id
   * @param env                   the env
   * @param service
   * @param infrastructureDefinition
   * @return the string
   * @throws InterruptedException the interrupted exception
   */
  public String triggerWorkflow(String appId, Environment env, Service service,
      InfrastructureDefinition infrastructureDefinition) throws InterruptedException {
    Workflow workflow = createWorkflow(appId, env, service, infrastructureDefinition);
    ExecutionArgs executionArgs = new ExecutionArgs();

    WorkflowExecutionUpdateFake callback = new WorkflowExecutionUpdateFake();
    WorkflowExecution execution = workflowExecutionService.triggerOrchestrationWorkflowExecution(
        appId, env.getUuid(), workflow.getUuid(), null, executionArgs, callback, null);
    callback.await(ofSeconds(15));

    assertThat(execution).isNotNull();
    String executionId = execution.getUuid();
    log.debug("Workflow executionId: {}", executionId);
    assertThat(executionId).isNotNull();
    execution = workflowExecutionService.getExecutionDetails(appId, executionId, true, false);
    assertThat(execution)
        .isNotNull()
        .extracting(WorkflowExecution::getUuid, WorkflowExecution::getStatus)
        .containsExactly(executionId, ExecutionStatus.SUCCESS);

    List<StateExecutionInstance> response =
        wingsPersistence
            .query(StateExecutionInstance.class,
                PageRequestBuilder.aPageRequest()
                    .addFilter(StateExecutionInstanceKeys.appId, EQ, appId)
                    .addFilter(StateExecutionInstanceKeys.executionUuid, EQ, execution.getUuid())
                    .addFilter(StateExecutionInstanceKeys.stateType, EQ, EMAIL.name())
                    .build())
            .getResponse();
    assertThat(response).isNotNull().isNotEmpty();
    List<ContextElement> elements = response.get(0)
                                        .getContextElements()
                                        .stream()
                                        .filter(contextElement
                                            -> contextElement.getElementType() == ContextElementType.PARAM
                                                && contextElement.getName() == PHASE_PARAM)
                                        .collect(toList());
    assertThat(elements).isNotNull().isNotEmpty();
    assertThat(elements.get(0)).isInstanceOf(PhaseElement.class);
    assertThat(((PhaseElement) elements.get(0)).getPhaseName()).isNotEmpty();
    return executionId;
  }
  /**
   * Trigger template workflow.
   *  @param appId                 the app id
   * @param env                   the env
   * @param service
   * @param templateService
   * @param infrastructureDefinition
   * @param templateInfraDefinition
   * @throws InterruptedException the interrupted exception
   */
  private String triggerTemplateWorkflow(String appId, Environment env, Service service, Service templateService,
      InfrastructureDefinition infrastructureDefinition, InfrastructureDefinition templateInfraDefinition)
      throws InterruptedException {
    Workflow workflow = createTemplateWorkflow(appId, env, service, infrastructureDefinition);
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowVariables(ImmutableMap.of(
        "Service", templateService.getUuid(), "InfraDefinition_SSH", templateInfraDefinition.getUuid()));

    WorkflowExecutionUpdateFake callback = new WorkflowExecutionUpdateFake();
    WorkflowExecution execution = workflowExecutionService.triggerOrchestrationWorkflowExecution(
        appId, env.getUuid(), workflow.getUuid(), null, executionArgs, callback, null);
    callback.await(ofSeconds(15));

    assertThat(execution).isNotNull();
    String executionId = execution.getUuid();
    log.debug("Workflow executionId: {}", executionId);
    assertThat(executionId).isNotNull();
    execution = workflowExecutionService.getExecutionDetails(appId, executionId, true, false);
    assertThat(execution)
        .isNotNull()
        .extracting(WorkflowExecution::getUuid, WorkflowExecution::getStatus)
        .containsExactly(executionId, ExecutionStatus.SUCCESS);
    return executionId;
  }
  private Workflow createWorkflow(
      String appId, Environment env, Service service, InfrastructureDefinition infraDefinition) {
    Workflow orchestrationWorkflow =
        aWorkflow()
            .name(WORKFLOW_NAME)
            .appId(appId)
            .envId(env.getUuid())
            .workflowType(WorkflowType.ORCHESTRATION)
            .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                       .withPreDeploymentSteps(aPhaseStep(PhaseStepType.PRE_DEPLOYMENT).build())
                                       .addWorkflowPhase(aWorkflowPhase()
                                                             .name("Phase1")
                                                             .serviceId(service.getUuid())
                                                             .deploymentType(DeploymentType.KUBERNETES)
                                                             .infraDefinitionId(infraDefinition.getUuid())
                                                             .build())
                                       .withPostDeploymentSteps(aPhaseStep(PhaseStepType.POST_DEPLOYMENT).build())
                                       .build())
            .build();

    Workflow orchestrationWorkflow2 = workflowService.createWorkflow(orchestrationWorkflow);
    assertThat(orchestrationWorkflow2).isNotNull().hasFieldOrProperty("uuid");
    assertThat(orchestrationWorkflow2.getOrchestrationWorkflow())
        .isNotNull()
        .hasFieldOrProperty("preDeploymentSteps")
        .hasFieldOrProperty("postDeploymentSteps")
        .hasFieldOrProperty("graph");

    Workflow orchestrationWorkflow3 =
        workflowService.readWorkflow(orchestrationWorkflow2.getAppId(), orchestrationWorkflow2.getUuid());
    assertThat(orchestrationWorkflow3).isNotNull();
    assertThat(orchestrationWorkflow3.getOrchestrationWorkflow())
        .isNotNull()
        .isInstanceOf(CanaryOrchestrationWorkflow.class);
    assertThat(((CanaryOrchestrationWorkflow) orchestrationWorkflow3.getOrchestrationWorkflow()).getWorkflowPhases())
        .isNotNull()
        .hasSize(1);

    WorkflowPhase workflowPhase =
        ((CanaryOrchestrationWorkflow) orchestrationWorkflow3.getOrchestrationWorkflow()).getWorkflowPhases().get(0);
    PhaseStep deployPhaseStep = workflowPhase.getPhaseSteps()
                                    .stream()
                                    .filter(ps -> ps.getPhaseStepType() == PhaseStepType.DEPLOY_SERVICE)
                                    .collect(toList())
                                    .get(0);

    deployPhaseStep.getSteps().add(
        GraphNode.builder()
            .type("EMAIL")
            .name("email")
            .properties(ImmutableMap.<String, Object>builder().put("toAddress", "a@b.com").build())
            .build());

    workflowService.updateWorkflowPhase(
        orchestrationWorkflow2.getAppId(), orchestrationWorkflow2.getUuid(), workflowPhase);

    Workflow orchestrationWorkflow4 =
        workflowService.readWorkflow(orchestrationWorkflow2.getAppId(), orchestrationWorkflow2.getUuid());

    assertThat(orchestrationWorkflow4).isNotNull();
    assertThat(orchestrationWorkflow4.getOrchestrationWorkflow())
        .isNotNull()
        .isInstanceOf(CanaryOrchestrationWorkflow.class);

    log.info("Graph Json : \n {}",
        JsonUtils.asJson(((CanaryOrchestrationWorkflow) orchestrationWorkflow4.getOrchestrationWorkflow()).getGraph()));

    return orchestrationWorkflow4;
  }

  private Workflow createTemplateWorkflow(
      String appId, Environment env, Service service, InfrastructureDefinition infrastructureDefinition) {
    TemplateExpression infraExpression =
        TemplateExpression.builder()
            .fieldName("infraDefinitionId")
            .expression("${InfraDefinition_SSH}")
            .metadata(new HashMap<>(ImmutableMap.of("entityType", "INFRASTRUCTURE_DEFINITION")))
            .build();

    TemplateExpression serviceExpression = TemplateExpression.builder()
                                               .fieldName("serviceId")
                                               .expression("${Service}")
                                               .metadata(new HashMap<>(ImmutableMap.of("entityType", "SERVICE")))
                                               .build();

    Workflow orchestrationWorkflow =
        aWorkflow()
            .name(WORKFLOW_NAME)
            .appId(appId)
            .envId(env.getUuid())
            .orchestrationWorkflow(
                aCanaryOrchestrationWorkflow()
                    .withPreDeploymentSteps(aPhaseStep(PhaseStepType.PRE_DEPLOYMENT).build())
                    .addWorkflowPhase(aWorkflowPhase()
                                          .name("Phase1")
                                          .serviceId(service.getUuid())
                                          .deploymentType(SSH)
                                          .infraDefinitionId(infrastructureDefinition.getUuid())
                                          .templateExpressions(asList(infraExpression, serviceExpression))
                                          .build())
                    .withPostDeploymentSteps(aPhaseStep(PhaseStepType.POST_DEPLOYMENT).build())
                    .build())
            .build();

    Workflow orchestrationWorkflow2 = workflowService.createWorkflow(orchestrationWorkflow);
    assertThat(orchestrationWorkflow2).isNotNull().hasFieldOrProperty("uuid");
    OrchestrationWorkflow orchestrationWorkflow1 = orchestrationWorkflow2.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow1)
        .isNotNull()
        .hasFieldOrProperty("preDeploymentSteps")
        .hasFieldOrProperty("postDeploymentSteps")
        .hasFieldOrProperty("graph");
    assertThat(orchestrationWorkflow2.getTemplatizedServiceIds()).isNotNull().contains(service.getUuid());
    assertThat(orchestrationWorkflow1.getTemplatizedInfraDefinitionIds())
        .isNotNull()
        .contains(infrastructureDefinition.getUuid());
    assertThat(orchestrationWorkflow1).extracting("userVariables").isNotNull();
    assertThat(
        orchestrationWorkflow1.getUserVariables().stream().anyMatch(variable -> variable.getName().equals("Service")))
        .isTrue();
    assertThat(orchestrationWorkflow1)
        .isNotNull()
        .hasFieldOrProperty("preDeploymentSteps")
        .hasFieldOrProperty("postDeploymentSteps")
        .hasFieldOrProperty("graph");

    Workflow orchestrationWorkflow3 =
        workflowService.readWorkflow(orchestrationWorkflow2.getAppId(), orchestrationWorkflow2.getUuid());
    assertThat(orchestrationWorkflow3).isNotNull();
    assertThat(orchestrationWorkflow3.getOrchestrationWorkflow())
        .isNotNull()
        .isInstanceOf(CanaryOrchestrationWorkflow.class);
    assertThat(((CanaryOrchestrationWorkflow) orchestrationWorkflow3.getOrchestrationWorkflow()).getWorkflowPhases())
        .isNotNull()
        .hasSize(1);

    WorkflowPhase workflowPhase =
        ((CanaryOrchestrationWorkflow) orchestrationWorkflow3.getOrchestrationWorkflow()).getWorkflowPhases().get(0);
    PhaseStep deployPhaseStep = workflowPhase.getPhaseSteps()
                                    .stream()
                                    .filter(ps -> ps.getPhaseStepType() == PhaseStepType.DEPLOY_SERVICE)
                                    .collect(toList())
                                    .get(0);

    deployPhaseStep.getSteps().add(
        GraphNode.builder()
            .type("EMAIL")
            .name("email")
            .properties(ImmutableMap.<String, Object>builder().put("toAddress", "a@b.com").build())
            .build());

    workflowService.updateWorkflowPhase(
        orchestrationWorkflow2.getAppId(), orchestrationWorkflow2.getUuid(), workflowPhase);

    Workflow orchestrationWorkflow4 =
        workflowService.readWorkflow(orchestrationWorkflow2.getAppId(), orchestrationWorkflow2.getUuid());

    assertThat(orchestrationWorkflow4).isNotNull();
    assertThat(orchestrationWorkflow4.getOrchestrationWorkflow())
        .isNotNull()
        .isInstanceOf(CanaryOrchestrationWorkflow.class);

    log.info("Graph Json : \n {}",
        JsonUtils.asJson(((CanaryOrchestrationWorkflow) orchestrationWorkflow4.getOrchestrationWorkflow()).getGraph()));

    return orchestrationWorkflow4;
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldObtainNoLastGoodDeployedArtifacts() {
    String appId = app.getUuid();
    Workflow workflow = createExecutableWorkflow(appId, env, "workflow1");
    List<Artifact> artifacts =
        workflowExecutionService.obtainLastGoodDeployedArtifacts(workflow.getAppId(), workflow.getUuid());
    assertThat(artifacts).isEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldObtainLastGoodDeployedArtifacts() throws InterruptedException {
    String appId = app.getUuid();
    Workflow workflow = createExecutableWorkflow(appId, env, "workflow1");
    WorkflowExecution workflowExecution = triggerWorkflow(workflow, env);
    assertThat(workflowExecution).isNotNull().hasFieldOrPropertyWithValue("releaseNo", "1");
    List<Artifact> artifacts =
        workflowExecutionService.obtainLastGoodDeployedArtifacts(workflow.getAppId(), workflow.getUuid());
    assertThat(artifacts).isNotEmpty();
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldObtainEmptyArtifactsForRollbackWhenNoInstancesAreDeployed() throws InterruptedException {
    when(artifactStreamServiceBindingService.listArtifactStreamIds(anyString()))
        .thenReturn(singletonList("artifactStreamId"));

    String appId = app.getUuid();
    Service service = addService("Service");
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder().uuid("artifactStreamId").build();
    service.setArtifactStreams(singletonList(dockerArtifactStream));
    service.setArtifactStreamIds(singletonList(dockerArtifactStream.getUuid()));

    SettingAttribute computeProvider =
        aSettingAttribute().withAppId(app.getUuid()).withValue(aPhysicalDataCenterConfig().build()).build();
    when(mockSettingsService.getByAccountAndId(any(), any())).thenReturn(computeProvider);
    wingsPersistence.save(computeProvider);

    final InfrastructureDefinition infraDefinition = createInfraDefinition(computeProvider, "Infra", "host1");

    Workflow workflow = createWorkflow(appId, env, service, infraDefinition);

    WorkflowExecution workflowExecution1 = triggerWorkflow(workflow, env);
    WorkflowExecution workflowExecution2 = triggerWorkflow(workflow, env);

    assertThat(workflowExecution1).isNotNull().hasFieldOrPropertyWithValue("releaseNo", "1");

    List<Artifact> artifacts = workflowExecutionService.obtainLastGoodDeployedArtifacts(
        workflowExecution2, workflowExecution1.getInfraMappingIds(), true);
    assertThat(artifacts).isEmpty();
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldObtainLastGoodDeployedArtifactsWithDeployedInstancesForRollback() throws InterruptedException {
    when(artifactStreamServiceBindingService.listArtifactStreamIds(anyString()))
        .thenReturn(singletonList("artifactStreamId"));

    String appId = app.getUuid();
    Service service = addService("Service");
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder().uuid("artifactStreamId").build();
    service.setArtifactStreams(singletonList(dockerArtifactStream));
    service.setArtifactStreamIds(singletonList(dockerArtifactStream.getUuid()));

    SettingAttribute computeProvider =
        aSettingAttribute().withAppId(app.getUuid()).withValue(aPhysicalDataCenterConfig().build()).build();
    when(mockSettingsService.getByAccountAndId(any(), any())).thenReturn(computeProvider);
    wingsPersistence.save(computeProvider);

    final InfrastructureDefinition infraDefinition = createInfraDefinition(computeProvider, "Infra", "host1");

    Workflow workflow = createWorkflow(appId, env, service, infraDefinition);

    WorkflowExecution workflowExecution1 = triggerWorkflow(workflow, env);
    WorkflowExecution workflowExecution2 = triggerWorkflow(workflow, env);
    WorkflowExecution workflowExecution3 = triggerWorkflow(workflow, env);

    List<ElementExecutionSummary> executionSummaries1 = workflowExecution1.getServiceExecutionSummaries();
    InstanceStatusSummary instanceStatusSummary1 =
        anInstanceStatusSummary()
            .withInstanceElement(InstanceElement.Builder.anInstanceElement().uuid("instanceId1").build())
            .build();
    executionSummaries1.get(0).setInstanceStatusSummaries(asList(instanceStatusSummary1));

    Artifact artifact1 = workflowExecution1.getArtifacts().get(0);
    artifact1.setUuid("artifact1");

    Artifact artifact2 = workflowExecution2.getArtifacts().get(0);
    artifact2.setUuid("artifact2");

    Artifact artifact3 = workflowExecution3.getArtifacts().get(0);
    artifact3.setUuid("artifact3");

    Query<WorkflowExecution> query1 = wingsPersistence.createQuery(WorkflowExecution.class)
                                          .filter(WorkflowExecutionKeys.uuid, workflowExecution1.getUuid());

    Query<WorkflowExecution> query2 = wingsPersistence.createQuery(WorkflowExecution.class)
                                          .filter(WorkflowExecutionKeys.uuid, workflowExecution2.getUuid());

    Query<WorkflowExecution> query3 = wingsPersistence.createQuery(WorkflowExecution.class)
                                          .filter(WorkflowExecutionKeys.uuid, workflowExecution3.getUuid());

    UpdateOperations<WorkflowExecution> updateOps1 =
        wingsPersistence.createUpdateOperations(WorkflowExecution.class)
            .set(WorkflowExecutionKeys.serviceExecutionSummaries, executionSummaries1)
            .set(WorkflowExecutionKeys.deployment, true)
            .set(WorkflowExecutionKeys.artifacts, workflowExecution1.getArtifacts());

    UpdateOperations<WorkflowExecution> updateOps2 =
        wingsPersistence.createUpdateOperations(WorkflowExecution.class)
            .set(WorkflowExecutionKeys.artifacts, workflowExecution2.getArtifacts());

    UpdateOperations<WorkflowExecution> updateOps3 =
        wingsPersistence.createUpdateOperations(WorkflowExecution.class)
            .set(WorkflowExecutionKeys.artifacts, workflowExecution3.getArtifacts());

    wingsPersistence.update(query1, updateOps1);
    wingsPersistence.update(query2, updateOps2);
    wingsPersistence.update(query3, updateOps3);

    List<Artifact> artifacts = workflowExecutionService.obtainLastGoodDeployedArtifacts(
        workflowExecution3, workflowExecution1.getInfraMappingIds(), true);
    assertThat(artifacts).isNotEmpty();
    assertThat(artifacts.get(0).getUuid()).isEqualTo(workflowExecution1.getArtifacts().get(0).getUuid());
    assertThat(artifacts.get(0).getUuid()).isNotEqualTo(workflowExecution2.getArtifacts().get(0).getUuid());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testShouldUseInfraBasedRollbackArtifactFfOn() {
    when(featureFlagService.isEnabled(FeatureName.INFRA_MAPPING_BASED_ROLLBACK_ARTIFACT, ACCOUNT_ID)).thenReturn(true);
    List<InfrastructureMapping> infrastructureMappingList1 =
        asList(DirectKubernetesInfrastructureMapping.builder().deploymentType(DeploymentType.AZURE_VMSS.name()).build(),
            DirectKubernetesInfrastructureMapping.builder().deploymentType(DeploymentType.KUBERNETES.name()).build());

    assertThat(workflowExecutionService.shouldUseInfraBasedRollbackArtifact(ACCOUNT_ID, infrastructureMappingList1))
        .isTrue();

    List<InfrastructureMapping> infrastructureMappingList2 = asList(
        DirectKubernetesInfrastructureMapping.builder().deploymentType(DeploymentType.AZURE_VMSS.name()).build());
    assertThat(workflowExecutionService.shouldUseInfraBasedRollbackArtifact(ACCOUNT_ID, infrastructureMappingList2))
        .isFalse();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testShouldUseInfraBasedRollbackArtifactFfOff() {
    when(featureFlagService.isEnabled(FeatureName.INFRA_MAPPING_BASED_ROLLBACK_ARTIFACT, ACCOUNT_ID)).thenReturn(false);
    List<InfrastructureMapping> infrastructureMappingList1 =
        asList(DirectKubernetesInfrastructureMapping.builder().deploymentType(DeploymentType.AZURE_VMSS.name()).build(),
            DirectKubernetesInfrastructureMapping.builder().deploymentType(DeploymentType.KUBERNETES.name()).build());

    assertThat(workflowExecutionService.shouldUseInfraBasedRollbackArtifact(ACCOUNT_ID, infrastructureMappingList1))
        .isFalse();

    List<InfrastructureMapping> infrastructureMappingList2 = asList(
        DirectKubernetesInfrastructureMapping.builder().deploymentType(DeploymentType.AZURE_VMSS.name()).build());
    assertThat(workflowExecutionService.shouldUseInfraBasedRollbackArtifact(ACCOUNT_ID, infrastructureMappingList2))
        .isFalse();
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldNotObtainLastGoodDeployedArtifactsForRollback() throws InterruptedException {
    String appId = app.getUuid();
    Workflow workflow = createExecutableWorkflow(appId, env, "workflowName");

    WorkflowExecution workflowExecution = triggerWorkflow(workflow, env);
    assertThat(workflowExecution).isNotNull().hasFieldOrPropertyWithValue("releaseNo", "1");

    List<String> infraMappingList = singletonList("infraMappingId");
    List<Artifact> artifacts =
        workflowExecutionService.obtainLastGoodDeployedArtifacts(workflowExecution, infraMappingList, false);
    assertThat(artifacts).isEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetDeploymentMetadata() {
    String appId = app.getUuid();
    Service service = addService("svc1");

    ServiceTemplate serviceTemplate = getServiceTemplate(service);

    SettingAttribute computeProvider =
        aSettingAttribute().withAppId(app.getUuid()).withValue(aPhysicalDataCenterConfig().build()).build();
    when(mockSettingsService.getByAccountAndId(any(), any())).thenReturn(computeProvider);
    wingsPersistence.save(computeProvider);

    final InfrastructureDefinition infraDefinition = createInfraDefinition(computeProvider, "Name4", "host1");

    Workflow workflow = createWorkflow(appId, env, service, infraDefinition);

    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setOrchestrationId(workflow.getUuid());
    executionArgs.setWorkflowType(workflow.getWorkflowType());
    final DeploymentMetadata deploymentMetadata =
        workflowExecutionService.fetchDeploymentMetadata(workflow.getAppId(), executionArgs);
    assertThat(deploymentMetadata).isNotNull();
    assertThat(deploymentMetadata.getArtifactRequiredServiceIds()).isEmpty();
  }

  private InfrastructureMapping createInfraMappingService(
      ServiceTemplate serviceTemplate, SettingAttribute computeProvider, String name4, String host1) {
    return infrastructureMappingService.save(PhysicalInfrastructureMapping.Builder.aPhysicalInfrastructureMapping()
                                                 .withName(name4)
                                                 .withAppId(app.getUuid())
                                                 .withEnvId(env.getUuid())
                                                 .withAccountId(app.getAccountId())
                                                 .withHostNames(Lists.newArrayList(host1))
                                                 .withServiceTemplateId(serviceTemplate.getUuid())
                                                 .withComputeProviderSettingId(computeProvider.getUuid())
                                                 .withComputeProviderType(computeProvider.getValue().getType())
                                                 .withDeploymentType(SSH.name())
                                                 .withHostConnectionAttrs(AccessType.KEY.name())
                                                 .withInfraMappingType(PHYSICAL_DATA_CENTER.name())
                                                 .withServiceId(serviceTemplate.getServiceId())
                                                 .build(),
        null);
  }

  private InfrastructureDefinition createInfraDefinition(SettingAttribute settingAttribute, String name, String host) {
    return infrastructureDefinitionService.save(InfrastructureDefinition.builder()
                                                    .name(name)
                                                    .appId(app.getUuid())
                                                    .accountId(app.getAccountId())
                                                    .envId(env.getUuid())
                                                    .deploymentType(SSH)
                                                    .cloudProviderType(CloudProviderType.PHYSICAL_DATA_CENTER)
                                                    .infrastructure(PhysicalInfra.builder()
                                                                        .hostNames(Lists.newArrayList(host))
                                                                        .cloudProviderId(settingAttribute.getUuid())
                                                                        .hostConnectionAttrs(AccessType.KEY.name())
                                                                        .build())
                                                    .build(),
        false);
  }

  private InfrastructureMapping createContainerInfraMappingService(
      ServiceTemplate serviceTemplate, SettingAttribute computeProvider, String serviceId) {
    String infraMappingId =
        wingsPersistence.save(DirectKubernetesInfrastructureMapping.Builder.aDirectKubernetesInfrastructureMapping()
                                  .withClusterName("testClusterName")
                                  .withAccountId(app.getAccountId())
                                  .withAppId(app.getUuid())
                                  .withServiceId(serviceId)
                                  .withNamespace("default")
                                  .withEnvId(env.getUuid())
                                  .withServiceTemplateId(serviceTemplate.getUuid())
                                  .withComputeProviderSettingId(computeProvider.getUuid())
                                  .withComputeProviderType(computeProvider.getValue().getType())
                                  .withDeploymentType(DeploymentType.KUBERNETES.name())
                                  .withInfraMappingType(KUBERNETES.name())
                                  .build());

    return wingsPersistence.get(InfrastructureMapping.class, infraMappingId);
  }

  private Service addService(String svc1) {
    return wingsPersistence.saveAndGet(Service.class,
        Service.builder()
            .uuid(generateUuid())
            .name(svc1)
            .appId(app.getUuid())
            .artifactType(ArtifactType.DOCKER)
            .build());
  }

  @Test
  @Owner(developers = {SRINIVAS})
  @Category(UnitTests.class)
  public void shouldListWaitingOnDeployments() {
    List<WorkflowExecution> waitingOnDeployments = getWorkflowExecutions(false);
    assertThat(waitingOnDeployments).isNotEmpty().hasSize(2);
  }

  @Test
  @Owner(developers = PRASHANT, intermittent = true)
  @Category(UnitTests.class)
  public void testShouldNotQueueDeployment() {
    List<WorkflowExecution> waitingOnDeployments = getWorkflowExecutions(true);
    assertThat(waitingOnDeployments).isNotEmpty().hasSize(2);
    List<ExecutionStatus> executionStatuses =
        waitingOnDeployments.stream().map(WorkflowExecution::getStatus).collect(toList());
    assertThat(executionStatuses).isNotEmpty().hasSize(2);
    assertThat(executionStatuses.get(0)).isEqualTo(ExecutionStatus.RUNNING);
    assertThat(executionStatuses.get(1)).isEqualTo(ExecutionStatus.RUNNING);
  }

  private List<WorkflowExecution> getWorkflowExecutions(boolean withConcurrencyStrategy) {
    String appId = app.getUuid();
    Workflow workflow = withConcurrencyStrategy ? createExecutableWorkflowWithThrottling(appId, env, "workflow1")
                                                : createExecutableWorkflow(appId, env, "workflow1");
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(
        singletonList(Artifact.Builder.anArtifact().withAppId(APP_ID).withUuid(ARTIFACT_ID).build()));

    WorkflowExecutionUpdateFake callback = new WorkflowExecutionUpdateFake();
    WorkflowExecution firstExecution = workflowExecutionService.triggerOrchestrationWorkflowExecution(
        appId, env.getUuid(), workflow.getUuid(), null, executionArgs, callback, null);

    assertThat(firstExecution).isNotNull();

    WorkflowExecution execution = workflowExecutionService.triggerOrchestrationWorkflowExecution(
        appId, env.getUuid(), workflow.getUuid(), null, executionArgs, callback, null);
    assertThat(execution).isNotNull();

    return workflowExecutionService.listWaitingOnDeployments(appId, execution.getUuid());
  }

  @Test
  @Owner(developers = {SRINIVAS})
  @Category(UnitTests.class)
  public void shouldFetchWorkflowExecutionStartTs() throws Exception {
    String appId = app.getUuid();
    Workflow workflow = createExecutableWorkflow(appId, env, "workflow1");
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(asList(Artifact.Builder.anArtifact().withAppId(APP_ID).withUuid(ARTIFACT_ID).build()));

    WorkflowExecutionUpdateFake callback = new WorkflowExecutionUpdateFake();
    WorkflowExecution execution = workflowExecutionService.triggerOrchestrationWorkflowExecution(
        appId, env.getUuid(), workflow.getUuid(), null, executionArgs, callback, null);
    callback.await(ofSeconds(15));

    assertThat(execution).isNotNull();

    assertThat(workflowExecutionService.fetchWorkflowExecutionStartTs(execution.getAppId(), execution.getUuid()))
        .isNotNull();
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldPopulateArtifactsAndServicesWithFF() {
    String serviceId1 = SERVICE_ID + "_1";
    String artifactId1 = ARTIFACT_ID + "_1";
    String artifactStreamId1 = ARTIFACT_STREAM_ID + "_1";
    String buildNo = BUILD_NO;
    ArtifactMetadata artifactMetadata = new ArtifactMetadata() {
      { put("buildNo", buildNo); }
    };
    ExecutionArgs executionArgs =
        ExecutionArgs.builder()
            .artifactVariables(List.of(
                ArtifactVariable.builder()
                    .artifactInput(ArtifactInput.builder().artifactStreamId(artifactStreamId1).buildNo(buildNo).build())
                    .build()))
            .build();
    when(artifactService.listArtifactsByArtifactStreamId(ACCOUNT_ID, artifactStreamId1, buildNo))
        .thenReturn(asList(
            anArtifact().withUuid(ARTIFACT_ID).withArtifactStreamId(ARTIFACT_STREAM_ID).withDisplayName("art").build(),
            anArtifact()
                .withUuid(artifactId1)
                .withArtifactStreamId(artifactStreamId1)
                .withAccountId(ACCOUNT_ID)
                .withBuildNo(buildNo)
                .withMetadata(artifactMetadata)
                .withDisplayName("art1")
                .build()));

    WorkflowExecution workflowExecution =
        WorkflowExecution.builder().serviceIds(asList(SERVICE_ID, serviceId1)).build();
    when(artifactStreamServiceBindingService.listArtifactStreamIds(SERVICE_ID))
        .thenReturn(singletonList(ARTIFACT_STREAM_ID));
    when(artifactStreamServiceBindingService.listArtifactStreamIds(serviceId1))
        .thenReturn(singletonList(artifactStreamId1));
    when(artifactStreamServiceBindingService.listServices(ARTIFACT_STREAM_ID))
        .thenReturn(singletonList(Service.builder().uuid(SERVICE_ID).name("s").build()));
    when(artifactStreamServiceBindingService.listServices(artifactStreamId1))
        .thenReturn(singletonList(Service.builder().uuid(serviceId1).name("s1").build()));
    when(featureFlagService.isEnabled(SPG_ENABLE_POPULATE_USING_ARTIFACT_VARIABLE, ACCOUNT_ID)).thenReturn(true);

    WorkflowStandardParams stdParams = aWorkflowStandardParams().build();
    Set<String> keywords = new HashSet<>();
    workflowExecutionService.populateArtifactsAndServices(
        workflowExecution, stdParams, keywords, executionArgs, ACCOUNT_ID);

    Function<List<Artifact>, Boolean> checkArtifacts = artifacts
        -> EmptyPredicate.isNotEmpty(artifacts) && artifacts.size() == 2
        && artifacts.get(0).getUuid().equals(ARTIFACT_ID) && artifacts.get(1).getUuid().equals(artifactId1);
    assertThat(checkArtifacts.apply(workflowExecution.getArtifacts())).isTrue();
    assertThat(checkArtifacts.apply(executionArgs.getArtifacts())).isTrue();
    assertThat(keywords).contains("s", "s1");
    assertThat(keywords).contains(buildNo);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldPopulateArtifactsAndServicesNoArtifactIdsWithFF() {
    ExecutionArgs executionArgs = new ExecutionArgs();
    when(featureFlagService.isEnabled(SPG_ENABLE_POPULATE_USING_ARTIFACT_VARIABLE, ACCOUNT_ID)).thenReturn(true);
    WorkflowExecution workflowExecution = WorkflowExecution.builder().build();
    WorkflowStandardParams stdParams = aWorkflowStandardParams().build();
    Set<String> keywords = new HashSet<>();
    workflowExecutionService.populateArtifactsAndServices(
        workflowExecution, stdParams, keywords, executionArgs, ACCOUNT_ID);
    assertThat(workflowExecution.getArtifacts()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldPopulateArtifactsAndServices() {
    String serviceId1 = SERVICE_ID + "_1";
    String artifactId1 = ARTIFACT_ID + "_1";
    String artifactStreamId1 = ARTIFACT_STREAM_ID + "_1";
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(
        asList(anArtifact().withUuid(ARTIFACT_ID).build(), anArtifact().withUuid(artifactId1).build()));
    when(artifactService.listByIds(any(), any()))
        .thenReturn(asList(
            anArtifact().withUuid(ARTIFACT_ID).withArtifactStreamId(ARTIFACT_STREAM_ID).withDisplayName("art").build(),
            anArtifact()
                .withUuid(artifactId1)
                .withArtifactStreamId(artifactStreamId1)
                .withDisplayName("art1")
                .build()));

    WorkflowExecution workflowExecution =
        WorkflowExecution.builder().serviceIds(asList(SERVICE_ID, serviceId1)).build();
    when(artifactStreamServiceBindingService.listArtifactStreamIds(SERVICE_ID))
        .thenReturn(singletonList(ARTIFACT_STREAM_ID));
    when(artifactStreamServiceBindingService.listArtifactStreamIds(serviceId1))
        .thenReturn(singletonList(artifactStreamId1));
    when(artifactStreamServiceBindingService.listServices(ARTIFACT_STREAM_ID))
        .thenReturn(singletonList(Service.builder().uuid(SERVICE_ID).name("s").build()));
    when(artifactStreamServiceBindingService.listServices(artifactStreamId1))
        .thenReturn(singletonList(Service.builder().uuid(serviceId1).name("s1").build()));

    WorkflowStandardParams stdParams = aWorkflowStandardParams().build();
    Set<String> keywords = new HashSet<>();
    ((WorkflowExecutionServiceImpl) workflowExecutionService)
        .populateArtifactsAndServices(workflowExecution, stdParams, keywords, executionArgs, ACCOUNT_ID);

    Function<List<Artifact>, Boolean> checkArtifacts = artifacts
        -> EmptyPredicate.isNotEmpty(artifacts) && artifacts.size() == 2
        && artifacts.get(0).getUuid().equals(ARTIFACT_ID) && artifacts.get(1).getUuid().equals(artifactId1);
    assertThat(checkArtifacts.apply(workflowExecution.getArtifacts())).isTrue();
    assertThat(checkArtifacts.apply(executionArgs.getArtifacts())).isTrue();
    assertThat(keywords).contains("s", "s1");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldPopulateArtifactsAndServicesNoArtifactIds() {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(Collections.singletonList(anArtifact().build()));
    WorkflowExecution workflowExecution = WorkflowExecution.builder().build();
    WorkflowStandardParams stdParams = aWorkflowStandardParams().build();
    Set<String> keywords = new HashSet<>();
    ((WorkflowExecutionServiceImpl) workflowExecutionService)
        .populateArtifactsAndServices(workflowExecution, stdParams, keywords, executionArgs, ACCOUNT_ID);
    assertThat(workflowExecution.getArtifacts()).isNullOrEmpty();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotPopulateArtifactsAndServicesWithInvalidArtifacts() {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(Collections.singletonList(anArtifact().withUuid(ARTIFACT_ID).build()));
    when(artifactService.listByIds(any(), any())).thenReturn(Collections.emptyList());
    WorkflowExecution workflowExecution = WorkflowExecution.builder().build();
    WorkflowStandardParams stdParams = aWorkflowStandardParams().build();
    Set<String> keywords = new HashSet<>();
    ((WorkflowExecutionServiceImpl) workflowExecutionService)
        .populateArtifactsAndServices(workflowExecution, stdParams, keywords, executionArgs, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldFetchConcurrentExecutions() {
    int count = 0;
    String firstExecutionId = null;
    List<ResourceConstraintInstance> resourceConstraintInstances = new ArrayList<>();
    List<WorkflowExecution> executions = getWorkflowExecutions(true);
    for (WorkflowExecution execution : executions) {
      resourceConstraintInstances.add(ResourceConstraintInstance.builder()
                                          .releaseEntityId(execution.getUuid())
                                          .releaseEntityType(HoldingScope.WORKFLOW.name())
                                          .resourceUnit(INFRA_MAPPING_ID)
                                          .state(count == 0 ? State.ACTIVE.name() : State.BLOCKED.name())
                                          .build());
      if (count == 0) {
        firstExecutionId = execution.getUuid();
      }
      count++;
    }
    when(resourceConstraintService.fetchResourceConstraintInstancesForUnitAndEntityType(any(), any(), any(), any()))
        .thenReturn(resourceConstraintInstances);
    when(resourceConstraintService.getByName(account.getUuid(), RESOURCE_CONSTRAINT_NAME))
        .thenReturn(ResourceConstraint.builder()
                        .name(RESOURCE_CONSTRAINT_NAME)
                        .accountId(account.getUuid())
                        .capacity(1)
                        .strategy(Strategy.FIFO)
                        .build());
    ConcurrentExecutionResponse response = workflowExecutionService.fetchConcurrentExecutions(
        app.getUuid(), firstExecutionId, RESOURCE_CONSTRAINT_NAME, INFRA_MAPPING_ID);
    assertThat(response).isNotNull();
    assertThat(response.getUnitType()).isEqualTo(UnitType.INFRA);
    assertThat(response.getExecutions()).isNotNull().hasSize(2);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testExtractInfrastructureDetails() {
    Service service = addService("Service");
    ServiceTemplate serviceTemplate = getServiceTemplate(service);

    String computeProviderId = wingsPersistence.save(
        aSettingAttribute().withAppId(app.getUuid()).withValue(aPhysicalDataCenterConfig().build()).build());

    SettingAttribute computeProvider =
        wingsPersistence.getWithAppId(SettingAttribute.class, app.getUuid(), computeProviderId);

    InfrastructureMapping infrastructureMapping =
        createInfraMappingService(serviceTemplate, computeProvider, "Name4", "host1");

    WorkflowExecution execution = WorkflowExecution.builder()
                                      .appId(app.getUuid())
                                      .infraDefinitionIds(singletonList(INFRA_DEFINITION_ID))
                                      .infraMappingIds(singletonList(infrastructureMapping.getUuid()))
                                      .serviceIds(singletonList(service.getUuid()))
                                      .build();
    Map<String, Object> infraMap =
        workflowExecutionService.extractServiceInfrastructureDetails(app.getUuid(), execution);
    assertThat(infraMap).isNotNull();
    assertThat(infraMap.containsKey("Service")).isTrue();
    assertThat(infraMap.get("Service")).isEqualTo("Service");
    assertThat(infraMap.containsKey("CloudProvider")).isTrue();
    assertThat(infraMap.get("CloudProvider")).isEqualTo("PHYSICAL_DATA_CENTER");

    computeProviderId = wingsPersistence.save(
        aSettingAttribute().withAppId(app.getUuid()).withValue(KubernetesClusterConfig.builder().build()).build());
    computeProvider = wingsPersistence.getWithAppId(SettingAttribute.class, app.getUuid(), computeProviderId);

    execution.setInfraMappingIds(singletonList(
        createContainerInfraMappingService(serviceTemplate, computeProvider, service.getUuid()).getUuid()));
    infraMap = workflowExecutionService.extractServiceInfrastructureDetails(app.getUuid(), execution);
    assertThat(infraMap).isNotNull();
    assertThat(infraMap.get("ClusterName")).isEqualTo("testClusterName");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void emptyExecutionHostsWithTargetEnabledShouldThrowException() {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setHosts(Collections.emptyList());
    executionArgs.setTargetToSpecificHosts(true);
    executionArgs.setWorkflowType(WorkflowType.ORCHESTRATION);

    assertThatThrownBy(
        () -> ((WorkflowExecutionServiceImpl) workflowExecutionService).validateExecutionArgsHosts(executionArgs, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Host list can't be empty when Target To Specific Hosts option is enabled");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void executionHostsShouldNotBeSetForPipelines() {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setTargetToSpecificHosts(true);
    executionArgs.setHosts(Arrays.asList("host1", "host2"));
    executionArgs.setWorkflowType(WorkflowType.PIPELINE);

    assertThatThrownBy(
        () -> ((WorkflowExecutionServiceImpl) workflowExecutionService).validateExecutionArgsHosts(executionArgs, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("pipeline");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void executionHostsShouldNotBeSetForTriggers() {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setTargetToSpecificHosts(true);
    executionArgs.setHosts(Arrays.asList("host1", "host2"));
    executionArgs.setWorkflowType(WorkflowType.ORCHESTRATION);
    Trigger trigger = Trigger.builder().build();

    assertThatThrownBy(()
                           -> ((WorkflowExecutionServiceImpl) workflowExecutionService)
                                  .validateExecutionArgsHosts(executionArgs, trigger))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("trigger");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void executionHostsShouldNotBeSetForNonSshOrWinRmDeploymentType() {
    List<String> hosts = Arrays.asList("host1", "host2");
    WorkflowExecution workflowExecution =
        WorkflowExecution.builder().serviceIds(Collections.singletonList("serviceId")).build();
    Workflow workflow = WorkflowBuilder.aWorkflow().build();
    workflow.setDeploymentTypes(Collections.singletonList(DeploymentType.KUBERNETES));

    assertThatThrownBy(()
                           -> ((WorkflowExecutionServiceImpl) workflowExecutionService)
                                  .validateExecutionArgsHosts(hosts, workflowExecution, workflow))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Execution Hosts only supported for SSH and WinRM deployment type");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void executionHostsShouldNotBeSetForMultiServicesInSingleWorkflow() {
    List<String> hosts = Arrays.asList("host1", "host2");
    List<String> servicesIds = Arrays.asList("serviceId1", "serviceId2");
    WorkflowExecution workflowExecution = WorkflowExecution.builder().serviceIds(servicesIds).build();

    assertThatThrownBy(()
                           -> ((WorkflowExecutionServiceImpl) workflowExecutionService)
                                  .validateExecutionArgsHosts(hosts, workflowExecution, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("single service");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void targetHostsOptionDisabledShouldPassValidation() {
    ExecutionArgs executionArgs = spy(new ExecutionArgs());

    ((WorkflowExecutionServiceImpl) workflowExecutionService).validateExecutionArgsHosts(executionArgs, null);

    verify(executionArgs, times(1)).isTargetToSpecificHosts();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldTrimNonEmptyList() {
    WorkflowExecutionServiceImpl workflowExecutionService =
        (WorkflowExecutionServiceImpl) this.workflowExecutionService;
    List<String> trimmedList = workflowExecutionService.trimExecutionArgsHosts(Collections.singletonList(" abc "));

    assertThat(trimmedList).isEqualTo(Collections.singletonList("abc"));
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void trimShouldReturnEmptyForEmptyList() {
    WorkflowExecutionServiceImpl workflowExecutionService =
        (WorkflowExecutionServiceImpl) this.workflowExecutionService;
    List<String> trimmedList = workflowExecutionService.trimExecutionArgsHosts(Collections.emptyList());

    assertThat(trimmedList).isEqualTo(Collections.emptyList());
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void trimShouldReturnEmptyForNullList() {
    WorkflowExecutionServiceImpl workflowExecutionService =
        (WorkflowExecutionServiceImpl) this.workflowExecutionService;
    List<String> trimmedList = workflowExecutionService.trimExecutionArgsHosts(null);

    assertThat(trimmedList).isEqualTo(Collections.emptyList());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void testFetchWorkflowExecutionsForResourceConstraint() {
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < ids.size(); ++i) {
      String uuid = "_" + random.nextInt(1000);
      if (ids.contains(uuid)) {
        continue;
      }
      final WorkflowExecution workflowExecution = WorkflowExecution.builder().uuid(uuid).appId(APP_ID).build();
      ids.add(workflowExecution.getUuid());
      wingsPersistence.save(workflowExecution);
    }

    Collections.sort(ids);

    final List<WorkflowExecution> workflowExecutions =
        workflowExecutionService.fetchWorkflowExecutionsForResourceConstraint(ids);

    List<String> wIds = workflowExecutions.stream().map(WorkflowExecution::getUuid).collect(toList());
    assertThat(wIds).isEqualTo(ids);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetStateExecutionDataInfraMapping() {
    String executionUuid = generateUuid();
    String displayName = generateUuid();
    String serviceId = generateUuid();
    String infraMappingId = generateUuid();
    String infraDefinitionId = generateUuid();

    final StateExecutionInstance stateExecutionInstance =
        aStateExecutionInstance()
            .appId(app.getUuid())
            .executionUuid(executionUuid)
            .stateType(StateType.PHASE_STEP.name())
            .displayName(displayName)
            .contextElement(PhaseElement.builder()
                                .infraMappingId(infraMappingId)
                                .infraDefinitionId(infraDefinitionId)
                                .serviceElement(ServiceElement.builder().uuid(serviceId).build())
                                .build())
            .build();

    wingsPersistence.save(stateExecutionInstance);

    List<StateExecutionInstance> stateExecutionData = workflowExecutionService.getStateExecutionData(
        app.getUuid(), executionUuid, serviceId, infraMappingId, Optional.empty(), StateType.PHASE_STEP, displayName);
    assertThat(stateExecutionData.size()).isEqualTo(1);

    stateExecutionData = workflowExecutionService.getStateExecutionData(app.getUuid(), executionUuid, serviceId, null,
        Optional.of(infraDefinitionId), StateType.PHASE_STEP, displayName);
    assertThat(stateExecutionData.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void getDeploymentTags() {
    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .accountId(ACCOUNT_ID)
                                              .tags(asList(NameValuePair.builder().name("key1").value("value1").build(),
                                                  NameValuePair.builder().name("label").value("").build()))
                                              .build();
    Map<String, String> deploymentTags =
        workflowExecutionService.getDeploymentTags(ACCOUNT_ID, workflowExecution.getTags());
    assertThat(deploymentTags).isNotEmpty();
    assertThat(deploymentTags.entrySet())
        .extracting(Map.Entry::getKey, Map.Entry::getValue)
        .contains(tuple("key1", "value1"), tuple("label", ""));

    workflowExecution = WorkflowExecution.builder().accountId(ACCOUNT_ID).build();
    deploymentTags = workflowExecutionService.getDeploymentTags(ACCOUNT_ID, workflowExecution.getTags());
    assertThat(deploymentTags).isNull();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testTriggerPipelineResumeExecution() {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(WorkflowType.PIPELINE);
    WorkflowExecution workflowExecution =
        WorkflowExecution.builder().accountId(account.getUuid()).executionArgs(executionArgs).build();
    Pipeline pipeline =
        Pipeline.builder()
            .uuid(PIPELINE_ID)
            .appId(app.getUuid())
            .name(PIPELINE_NAME)
            .pipelineStages(singletonList(PipelineStage.builder()
                                              .pipelineStageElements(singletonList(PipelineStageElement.builder()
                                                                                       .type(APPROVAL.name())
                                                                                       .parallelIndex(1)
                                                                                       .name("APPROVAL")
                                                                                       .properties(new HashMap<>())
                                                                                       .build()))
                                              .build()))
            .build();
    when(pipelineResumeUtils.getPipelineForResume(eq(app.getUuid()), eq(1), eq(workflowExecution), any()))
        .thenReturn(pipeline);
    workflowExecutionService.triggerPipelineResumeExecution(app.getUuid(), 1, workflowExecution);
    verify(pipelineResumeUtils).getPipelineForResume(eq(app.getUuid()), eq(1), eq(workflowExecution), any());
    verify(pipelineResumeUtils).updatePipelineExecutionsAfterResume(any(), eq(workflowExecution));
  }

  /**
   * This test checks triggerPipelineResumeExecution methods integration with dependencies.
   */
  @Test
  @Owner(developers = VIKAS_S)
  @Category(UnitTests.class)
  public void testTriggerPipelineResumeExecutionWithStageName() {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(WorkflowType.PIPELINE);
    WorkflowExecution workflowExecution =
        WorkflowExecution.builder().accountId(account.getUuid()).executionArgs(executionArgs).build();
    String stageName = "stageName";
    int parallelIndex = 1;
    Pipeline pipeline =
        Pipeline.builder()
            .uuid(PIPELINE_ID)
            .appId(app.getUuid())
            .name(PIPELINE_NAME)
            .pipelineStages(singletonList(PipelineStage.builder()
                                              .pipelineStageElements(singletonList(PipelineStageElement.builder()
                                                                                       .type(APPROVAL.name())
                                                                                       .parallelIndex(parallelIndex)
                                                                                       .name(stageName)
                                                                                       .properties(new HashMap<>())
                                                                                       .build()))
                                              .build()))
            .build();
    when(pipelineResumeUtils.getParallelIndexFromPipelineStageName(eq(stageName), eq(pipeline)))
        .thenReturn(parallelIndex);
    when(pipelineResumeUtils.getPipelineFromWorkflowExecution(eq(workflowExecution), eq(app.getUuid())))
        .thenReturn(pipeline);
    when(pipelineResumeUtils.getPipelineForResume(eq(app.getUuid()), eq(parallelIndex), eq(workflowExecution), any()))
        .thenReturn(pipeline);
    workflowExecutionService.triggerPipelineResumeExecution(app.getUuid(), stageName, workflowExecution);
    verify(pipelineResumeUtils)
        .getPipelineForResume(eq(app.getUuid()), eq(parallelIndex), eq(workflowExecution), any());
    verify(pipelineResumeUtils).updatePipelineExecutionsAfterResume(any(), eq(workflowExecution));
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetResumeStages() {
    WorkflowExecution workflowExecution = WorkflowExecution.builder().accountId(ACCOUNT_ID).build();
    workflowExecutionService.getResumeStages(APP_ID, workflowExecution);
    verify(pipelineResumeUtils).getResumeStages(eq(APP_ID), eq(workflowExecution));
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetResumeHistory() {
    WorkflowExecution workflowExecution = WorkflowExecution.builder().accountId(ACCOUNT_ID).build();
    workflowExecutionService.getResumeHistory(APP_ID, workflowExecution);
    verify(pipelineResumeUtils).getResumeHistory(eq(APP_ID), eq(workflowExecution));
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testShouldCollectArtifactsAsync() {
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .jobname("releases")
                                                  .groupId("mygroup")
                                                  .artifactPaths(asList("${artifactId}"))
                                                  .autoPopulate(false)
                                                  .serviceId(SERVICE_ID)
                                                  .name("testNexus")
                                                  .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);
    when(buildSourceService.getBuild(any(), any(), any(), any()))
        .thenReturn(BuildDetails.Builder.aBuildDetails().withNumber("1.0").build());
    Map<String, String> map = new HashMap<>();
    map.put("buildNo", "1.0");
    Artifact artifact =
        Artifact.Builder.anArtifact().withMetadata(new ArtifactMetadata(map)).withUuid(ARTIFACT_ID).build();
    when(artifactCollectionUtils.getArtifact(any(), any())).thenReturn(artifact);
    when(artifactService.create(artifact, nexusArtifactStream, false)).thenReturn(artifact);
    WorkflowExecution workflowExecution = WorkflowExecution.builder().accountId(ACCOUNT_ID).build();
    Map<String, Object> map1 = new HashMap<>();
    map1.put("artifactId", "myartifact");
    map1.put("buildNo", "1.0");
    List<Artifact> artifacts =
        ((WorkflowExecutionServiceImpl) workflowExecutionService)
            .collectArtifacts(workflowExecution,
                singletonList(ArtifactVariable.builder()
                                  .entityType(SERVICE)
                                  .entityId("SERVICE_ID_1")
                                  .name("art_parameterized")
                                  .artifactStreamMetadata(ArtifactStreamMetadata.builder()
                                                              .artifactStreamId(ARTIFACT_STREAM_ID)
                                                              .runtimeValues(map1)
                                                              .build())
                                  .build()),
                APP_ID);
    assertThat(artifacts).isNotEmpty();
    assertThat(artifacts).extracting(Artifact::getUuid).containsExactly(ARTIFACT_ID);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testShouldNotCollectArtifactsAsync() {
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .jobname("releases")
                                                  .groupId("mygroup")
                                                  .artifactPaths(asList("${artifactId}"))
                                                  .autoPopulate(false)
                                                  .serviceId(SERVICE_ID)
                                                  .name("art_parameterized")
                                                  .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);
    when(buildSourceService.getBuild(anyString(), anyString(), anyString(), any())).thenReturn(null);
    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .pipelineExecutionId(PIPELINE_EXECUTION_ID)
                                              .build();
    WorkflowExecution pipelineExecution = WorkflowExecution.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .uuid(PIPELINE_EXECUTION_ID)
                                              .status(RUNNING)
                                              .build();
    wingsPersistence.save(pipelineExecution);
    Map<String, Object> map1 = new HashMap<>();
    map1.put("artifactId", "myartifact");
    map1.put("buildNo", "1.0");
    List<Artifact> artifacts = workflowExecutionService.collectArtifacts(workflowExecution,
        singletonList(
            ArtifactVariable.builder()
                .entityType(SERVICE)
                .entityId("SERVICE_ID_1")
                .name("art_parameterized")
                .artifactStreamMetadata(
                    ArtifactStreamMetadata.builder().artifactStreamId(ARTIFACT_STREAM_ID).runtimeValues(map1).build())
                .build()),
        APP_ID);
    assertThat(artifacts).isEmpty();
    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(workflowExecution.getMessage()).contains("Error collecting build for artifact source art_parameterized");
    pipelineExecution = workflowExecutionService.getWorkflowExecution(APP_ID, PIPELINE_EXECUTION_ID);
    assertThat(pipelineExecution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldAbortExecutionAfterCollectingArtifactsOnInterrupt() {
    StateMachine stateMachine = aStateMachine().build();
    ExecutionEventAdvisor executionEventAdvisor = new CanaryWorkflowExecutionAdvisor();
    WorkflowExecutionUpdate workflowExecutionUpdate = new WorkflowExecutionUpdate();
    WorkflowStandardParams stdParams = aWorkflowStandardParams().build();
    wingsPersistence.save(WorkflowExecution.builder()
                              .accountId(ACCOUNT_ID)
                              .appId(app.getUuid())
                              .status(PREPARING)
                              .message("Starting artifact collection")
                              .uuid(WORKFLOW_EXECUTION_ID)
                              .build());
    Graph graph = constructGraph();
    Workflow workflow =
        aWorkflow()
            .envId(env.getUuid())
            .appId(app.getUuid())
            .name("workflow1")
            .description("Sample Workflow")
            .orchestrationWorkflow(aCustomOrchestrationWorkflow().withValid(true).withGraph(graph).build())
            .workflowType(WorkflowType.ORCHESTRATION)
            .build();
    workflow = workflowService.createWorkflow(workflow);
    ExecutionArgs executionArgs = new ExecutionArgs();
    Map<String, Object> map1 = new HashMap<>();
    map1.put("artifactId", "myartifact");
    map1.put("buildNo", "1.0");
    executionArgs.setArtifactVariables(asList(
        ArtifactVariable.builder()
            .entityType(SERVICE)
            .entityId("SERVICE_ID_1")
            .name("art_parameterized")
            .artifactStreamMetadata(
                ArtifactStreamMetadata.builder().artifactStreamId(ARTIFACT_STREAM_ID).runtimeValues(map1).build())
            .build()));
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .jobname("releases")
                                                  .groupId("mygroup")
                                                  .artifactPaths(asList("${artifactId}"))
                                                  .autoPopulate(false)
                                                  .serviceId(SERVICE_ID)
                                                  .name("testNexus")
                                                  .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);
    when(buildSourceService.getBuild(any(), any(), any(), any()))
        .thenReturn(BuildDetails.Builder.aBuildDetails().withNumber("1.0").build());
    Map<String, String> map = new HashMap<>();
    map.put("buildNo", "1.0");
    Artifact artifact =
        Artifact.Builder.anArtifact().withMetadata(new ArtifactMetadata(map)).withUuid(ARTIFACT_ID).build();
    when(artifactCollectionUtils.getArtifact(any(), any())).thenReturn(artifact);
    when(artifactService.create(artifact, nexusArtifactStream, false)).thenReturn(artifact);
    WorkflowExecution workflowExecution =
        wingsPersistence.getWithAppId(WorkflowExecution.class, app.getUuid(), WORKFLOW_EXECUTION_ID);
    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .executionUuid(workflowExecution.getUuid())
                                                .executionInterruptType(ExecutionInterruptType.ABORT_ALL)
                                                .build();
    wingsPersistence.save(executionInterrupt);
    ((WorkflowExecutionServiceImpl) workflowExecutionService)
        .collectArtifactsAndStartExecution(workflowExecution, stateMachine, executionEventAdvisor,
            workflowExecutionUpdate, stdParams, app, workflow, null, executionArgs, null);
    workflowExecution = wingsPersistence.getWithAppId(WorkflowExecution.class, app.getUuid(), WORKFLOW_EXECUTION_ID);
    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.ABORTED);
    assertThat(workflowExecution.getMessage()).isNull();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldAbortExecutionInPreparingState() {
    wingsPersistence.save(WorkflowExecution.builder()
                              .accountId(ACCOUNT_ID)
                              .appId(app.getUuid())
                              .status(PREPARING)
                              .message("Starting artifact collection")
                              .uuid(WORKFLOW_EXECUTION_ID)
                              .build());
    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .executionUuid(WORKFLOW_EXECUTION_ID)
                                                .executionInterruptType(ExecutionInterruptType.ABORT_ALL)
                                                .build();
    workflowExecutionService.triggerExecutionInterrupt(executionInterrupt);
    WorkflowExecution workflowExecution =
        wingsPersistence.getWithAppId(WorkflowExecution.class, app.getUuid(), WORKFLOW_EXECUTION_ID);
    assertThat(workflowExecution.getStatus()).isEqualTo(ABORTED);
    assertThat(workflowExecution.getMessage()).isNull();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetWorkflowExecution() {
    Artifact artifact =
        Artifact.Builder.anArtifact().withAppId(APP_ID).withArtifactStreamId(ARTIFACT_STREAM_ID).build();
    ArtifactStream artifactStream = CustomArtifactStream.builder().uuid(ARTIFACT_STREAM_ID).name("test").build();
    List<Artifact> artifacts = new ArrayList<>();
    artifacts.add(artifact);
    String uuid = "_" + System.currentTimeMillis() + "_";
    WorkflowExecution workflowExecution =
        WorkflowExecution.builder().uuid(uuid).appId(APP_ID).artifacts(artifacts).build();
    wingsPersistence.save(workflowExecution);
    wingsPersistence.save(artifactStream);

    WorkflowExecution workflowExecution1 = workflowExecutionService.getWorkflowExecution(APP_ID, uuid);
    assertThat(workflowExecution1).isNotNull();
    assertThat(workflowExecution1.getArtifacts().get(0).getArtifactStreamName()).isEqualTo("test");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldObtainLastGoodDeployedHelmChart() {
    HelmChart helmChart1 = generateHelmChartWithVersion("1.0");
    HelmChart helmChart3 = generateHelmChartWithVersion("3.0");
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setHelmCharts(asList(helmChart1, helmChart3));
    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .uuid(WORKFLOW_EXECUTION_ID)
                                              .appId(APP_ID)
                                              .executionArgs(executionArgs)
                                              .workflowId(WORKFLOW_ID)
                                              .helmCharts(asList(helmChart1, helmChart3))
                                              .status(SUCCESS)
                                              .build();
    wingsPersistence.save(workflowExecution);
    HelmChart helmChart2 = generateHelmChartWithVersion("2.0");
    ExecutionArgs executionArgs2 = new ExecutionArgs();
    executionArgs.setHelmCharts(asList(helmChart2, helmChart3));
    WorkflowExecution workflowExecution2 = WorkflowExecution.builder()
                                               .uuid(WORKFLOW_EXECUTION_ID + 2)
                                               .workflowId(WORKFLOW_ID)
                                               .executionArgs(executionArgs2)
                                               .helmCharts(asList(helmChart2, helmChart3))
                                               .appId(APP_ID)
                                               .status(FAILED)
                                               .build();
    wingsPersistence.save(workflowExecution2);
    List<HelmChart> lastDeployedHelmCharts =
        workflowExecutionService.obtainLastGoodDeployedHelmCharts(APP_ID, WORKFLOW_ID);
    assertThat(lastDeployedHelmCharts).containsExactlyInAnyOrder(helmChart1, helmChart3);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldAppendSkipConditionDetails() {
    String skipCondition = "${context.variable}=='ok'";
    StateInspection stateInspection = StateInspection.builder().stateExecutionInstanceId(STATE_EXECUTION_ID).build();
    when(stateInspectionService.get(STATE_EXECUTION_ID)).thenReturn(stateInspection);
    PipelineStageExecution stageExecution = PipelineStageExecution.builder().build();
    workflowExecutionService.appendSkipCondition(
        PipelineStageElement.builder().disableAssertion(skipCondition).build(), stageExecution, STATE_EXECUTION_ID);
    verify(stateInspectionService).get(STATE_EXECUTION_ID);

    assertThat(stageExecution.getSkipCondition()).isEqualTo(skipCondition);
    assertThat(stageExecution.getDisableAssertionInspection()).isEqualTo(stateInspection);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldNotAppendSkipConditionDetails() {
    String skipCondition = "";
    PipelineStageExecution stageExecution = PipelineStageExecution.builder().build();
    workflowExecutionService.appendSkipCondition(
        PipelineStageElement.builder().disableAssertion(skipCondition).build(), stageExecution, STATE_EXECUTION_ID);
    verify(stateInspectionService, never()).get(STATE_EXECUTION_ID);

    assertThat(stageExecution.getSkipCondition()).isNull();
    assertThat(stageExecution.getDisableAssertionInspection()).isNull();
  }

  private HelmChart generateHelmChartWithVersion(String version) {
    return HelmChart.builder()
        .accountId(ACCOUNT_ID)
        .appId(APP_ID)
        .uuid(UUID + version)
        .applicationManifestId(MANIFEST_ID)
        .serviceId(SERVICE_ID)
        .version(version)
        .build();
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testWorkflowExecution() throws InterruptedException {
    Service service = addService("Service name");

    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .imageName("harness/to-do-list")
                                                    .name("artifactName")
                                                    .appId(app.getUuid())
                                                    .sourceName("sourceName")
                                                    .build();
    service.setArtifactStreams(singletonList(dockerArtifactStream));

    SettingAttribute computeProvider =
        aSettingAttribute().withAppId(app.getUuid()).withValue(aPhysicalDataCenterConfig().build()).build();
    when(mockSettingsService.getByAccountAndId(any(), any())).thenReturn(computeProvider);
    wingsPersistence.save(computeProvider);
    final InfrastructureDefinition infraDefinition = createInfraDefinition(computeProvider, "InfraName", "host1");

    Workflow workflow =
        aWorkflow()
            .envId(env.getUuid())
            .appId(app.getUuid())
            .name("Test Workflow")
            .description("Test Workflow Description")
            .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                       .addWorkflowPhase(aWorkflowPhase()
                                                             .serviceId(service.getUuid())
                                                             .infraDefinitionId(infraDefinition.getUuid())
                                                             .build())
                                       .build())
            .workflowType(WorkflowType.ORCHESTRATION)
            .serviceId(service.getUuid())
            .infraDefinitionId(infraDefinition.getUuid())
            .build();

    workflow = workflowService.createWorkflow(workflow);
    assertThat(workflow).isNotNull();
    assertThat(workflow.getUuid()).isNotNull();

    ExecutionArgs executionArgs = new ExecutionArgs();
    WorkflowExecutionUpdateFake callback = new WorkflowExecutionUpdateFake();
    WorkflowExecution execution = workflowExecutionService.triggerOrchestrationWorkflowExecution(
        app.getUuid(), env.getUuid(), workflow.getUuid(), null, executionArgs, callback, null);
    callback.await(ofSeconds(45));

    assertThat(execution.getServiceIds().get(0)).isEqualTo(service.getUuid());
    assertThat(execution.getInfraDefinitionIds().get(0)).isEqualTo(infraDefinition.getUuid());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowDeploymentFreezeExceptionWhenResumingFrozenStageWithoutOverridePermission() {
    // User without permission ALLOW_DEPLOYMENTS_DURING_FREEZE
    User user = anUser().uuid(generateUuid()).name("user-name").build();
    UserThreadLocal.set(user);

    doThrow(new InvalidRequestException("User is not authorized", WingsException.USER))
        .when(deploymentAuthHandler)
        .authorizeDeploymentDuringFreeze();

    WorkflowExecution workflowExecution =
        WorkflowExecution.builder().accountId(account.getUuid()).executionArgs(new ExecutionArgs()).build();
    Pipeline pipeline =
        Pipeline.builder()
            .uuid(PIPELINE_ID)
            .appId(app.getUuid())
            .name(PIPELINE_NAME)
            .pipelineStages(asList(PipelineStage.builder()
                                       .pipelineStageElements(singletonList(PipelineStageElement.builder()
                                                                                .type(APPROVAL.name())
                                                                                .parallelIndex(1)
                                                                                .name("APPROVAL")
                                                                                .properties(new HashMap<>())
                                                                                .build()))
                                       .build(),
                PipelineStage.builder()
                    .pipelineStageElements(
                        singletonList(PipelineStageElement.builder()
                                          .type(ENV_STATE.name())
                                          .parallelIndex(2)
                                          .name("WORKFLOW1")
                                          .properties(Collections.singletonMap(EnvStateKeys.envId, ENV_ID))
                                          .build()))
                    .build()))
            .build();
    when(featureFlagService.isEnabled(FeatureName.NEW_DEPLOYMENT_FREEZE, account.getUuid())).thenReturn(true);

    GovernanceConfig governanceConfig = GovernanceConfig.builder()
                                            .accountId(account.getUuid())
                                            .timeRangeBasedFreezeConfigs(Collections.singletonList(
                                                TimeRangeBasedFreezeConfig.builder()
                                                    .name("freeze1")
                                                    .uuid(FREEZE_WINDOW_ID)
                                                    .timeRange(new TimeRange(0, 1, "", false, null, null, null, false))
                                                    .build()))
                                            .build();
    when(governanceConfigService.get(account.getUuid())).thenReturn(governanceConfig);
    when(governanceConfigService.getFrozenEnvIdsForApp(account.getUuid(), app.getUuid(), governanceConfig))
        .thenReturn(Collections.singletonMap(FREEZE_WINDOW_ID, Collections.singleton(ENV_ID)));
    when(pipelineResumeUtils.getPipelineForResume(eq(app.getUuid()), eq(2), eq(workflowExecution), any()))
        .thenReturn(pipeline);
    assertThatThrownBy(
        () -> workflowExecutionService.triggerPipelineResumeExecution(app.getUuid(), 2, workflowExecution))
        .isInstanceOf(DeploymentFreezeException.class)
        .hasMessage(
            "Deployment Freeze Window [freeze1] is active for the environment. No deployments are allowed to proceed.");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldReturnEmptyListIfNoArtifactInPreviousSuccessfulExecution() {
    List<String> infraMappingList = Collections.singletonList(INFRA_MAPPING_ID);
    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .status(SUCCESS)
                                              .uuid(WORKFLOW_EXECUTION_ID)
                                              .infraMappingIds(infraMappingList)
                                              .build();
    WorkflowStandardParams stdParams = aWorkflowStandardParams().build();
    workflowExecutionService.populateRollbackArtifacts(workflowExecution, infraMappingList, stdParams, true);
    assertThat(stdParams.getRollbackArtifactIds()).isNotNull().isEmpty();
    assertThat(workflowExecution.getRollbackArtifacts()).isNotNull().isEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldReturnCorrectRollbackArtifactForDuplicateInfraMappingIds() {
    List<String> infraMappingList = Collections.singletonList(INFRA_MAPPING_ID);
    WorkflowExecution workflowExecution =
        WorkflowExecution.builder()
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .status(SUCCESS)
            .workflowId(WORKFLOW_ID)
            .uuid(WORKFLOW_EXECUTION_ID)
            .infraMappingIds(infraMappingList)
            .artifacts(Collections.singletonList(anArtifact().withUuid(ARTIFACT_ID).build()))
            .build();
    wingsPersistence.save(workflowExecution);
    WorkflowStandardParams stdParams = aWorkflowStandardParams().build();
    workflowExecutionService.populateRollbackArtifacts(
        workflowExecution, asList(INFRA_MAPPING_ID, INFRA_MAPPING_ID), stdParams, false);
    assertThat(stdParams.getRollbackArtifactIds()).containsExactly(ARTIFACT_ID);
    assertThat(workflowExecution.getRollbackArtifacts()).hasSize(1);
  }

  @Test
  @Owner(developers = {PRABU})
  @Category(UnitTests.class)
  public void shouldReturnTrueIfParameterizedArtifactPresentInExecution() {
    Artifact artifact = anArtifact().withArtifactStreamId(ARTIFACT_STREAM_ID).build();
    ArtifactVariable artifactVariable =
        ArtifactVariable.builder()
            .artifactStreamMetadata(ArtifactStreamMetadata.builder().artifactStreamId(ARTIFACT_STREAM_ID).build())
            .build();
    assertThat(workflowExecutionService.parameterizedArtifactsCollectedInWorkflowExecution(
                   Collections.singletonList(artifact), Collections.singletonList(artifactVariable)))
        .isTrue();
  }

  @Test
  @Owner(developers = {PRABU})
  @Category(UnitTests.class)
  public void shouldReturnTrueIfNoParameterizedArtifactVariablePresent() {
    Artifact artifact = anArtifact().withArtifactStreamId(ARTIFACT_STREAM_ID).build();
    ArtifactVariable artifactVariable = ArtifactVariable.builder().build();
    assertThat(workflowExecutionService.parameterizedArtifactsCollectedInWorkflowExecution(
                   Collections.singletonList(artifact), Collections.singletonList(artifactVariable)))
        .isTrue();
  }

  @Test
  @Owner(developers = {PRABU})
  @Category(UnitTests.class)
  public void shouldReturnFalseIfParameterizedArtifactNotPresentInExecution() {
    Artifact artifact = anArtifact().withArtifactStreamId(ARTIFACT_STREAM_ID + 2).build();
    ArtifactVariable artifactVariable =
        ArtifactVariable.builder()
            .artifactStreamMetadata(ArtifactStreamMetadata.builder().artifactStreamId(ARTIFACT_STREAM_ID).build())
            .build();
    assertThat(workflowExecutionService.parameterizedArtifactsCollectedInWorkflowExecution(
                   Collections.singletonList(artifact), Collections.singletonList(artifactVariable)))
        .isFalse();
  }

  @Test
  @Owner(developers = {PRABU})
  @Category(UnitTests.class)
  public void shouldReturnTrueIfMultipleParameterizedArtifactPresentInExecution() {
    Artifact artifact = anArtifact().withArtifactStreamId(ARTIFACT_STREAM_ID).build();
    Artifact artifact2 = anArtifact().withArtifactStreamId(ARTIFACT_STREAM_ID + 2).build();
    ArtifactVariable artifactVariable =
        ArtifactVariable.builder()
            .artifactStreamMetadata(ArtifactStreamMetadata.builder().artifactStreamId(ARTIFACT_STREAM_ID).build())
            .build();
    ArtifactVariable artifactVariable2 =
        ArtifactVariable.builder()
            .artifactStreamMetadata(ArtifactStreamMetadata.builder().artifactStreamId(ARTIFACT_STREAM_ID + 2).build())
            .build();
    assertThat(workflowExecutionService.parameterizedArtifactsCollectedInWorkflowExecution(
                   asList(artifact, artifact2), asList(artifactVariable, artifactVariable2)))
        .isTrue();
  }

  @Test
  @Owner(developers = {PRABU})
  @Category(UnitTests.class)
  public void shouldReturnFalseIfMultipleParameterizedArtifactAbsentInExecution() {
    Artifact artifact = anArtifact().withArtifactStreamId(ARTIFACT_STREAM_ID).build();
    ArtifactVariable artifactVariable =
        ArtifactVariable.builder()
            .artifactStreamMetadata(ArtifactStreamMetadata.builder().artifactStreamId(ARTIFACT_STREAM_ID).build())
            .build();
    ArtifactVariable artifactVariable2 =
        ArtifactVariable.builder()
            .artifactStreamMetadata(ArtifactStreamMetadata.builder().artifactStreamId(ARTIFACT_STREAM_ID + 2).build())
            .build();
    ArtifactVariable artifactVariable3 =
        ArtifactVariable.builder()
            .artifactStreamMetadata(ArtifactStreamMetadata.builder().artifactStreamId(ARTIFACT_STREAM_ID + 3).build())
            .build();
    assertThat(workflowExecutionService.parameterizedArtifactsCollectedInWorkflowExecution(
                   Collections.singletonList(artifact), asList(artifactVariable, artifactVariable2, artifactVariable3)))
        .isFalse();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldUpdatePipelineWithArtifactsCollected() {
    StateMachine stateMachine = aStateMachine().build();
    ExecutionEventAdvisor executionEventAdvisor = new CanaryWorkflowExecutionAdvisor();
    WorkflowExecutionUpdate workflowExecutionUpdate = new WorkflowExecutionUpdate();
    WorkflowStandardParams stdParams = aWorkflowStandardParams().build();
    wingsPersistence.save(WorkflowExecution.builder()
                              .accountId(ACCOUNT_ID)
                              .appId(app.getUuid())
                              .status(PREPARING)
                              .message("Starting artifact collection")
                              .uuid(WORKFLOW_EXECUTION_ID)
                              .pipelineExecutionId(PIPELINE_EXECUTION_ID)
                              .build());
    Artifact artifact2 = Artifact.Builder.anArtifact().withUuid(ARTIFACT_ID + 2).build();
    List<Artifact> artifacts = new ArrayList<>();
    artifacts.add(artifact2);
    wingsPersistence.save(WorkflowExecution.builder()
                              .accountId(ACCOUNT_ID)
                              .appId(app.getUuid())
                              .status(PREPARING)
                              .message("Starting artifact collection")
                              .uuid(PIPELINE_EXECUTION_ID)
                              .artifacts(artifacts)
                              .executionArgs(ExecutionArgs.builder().artifacts(artifacts).build())
                              .build());
    Graph graph = constructGraph();
    Workflow workflow =
        aWorkflow()
            .envId(env.getUuid())
            .appId(app.getUuid())
            .name("workflow1")
            .description("Sample Workflow")
            .orchestrationWorkflow(aCustomOrchestrationWorkflow().withValid(true).withGraph(graph).build())
            .workflowType(WorkflowType.ORCHESTRATION)
            .build();
    workflow = workflowService.createWorkflow(workflow);
    ExecutionArgs executionArgs = new ExecutionArgs();
    Map<String, Object> map1 = new HashMap<>();
    map1.put("artifactId", "myartifact");
    map1.put("buildNo", "1.0");
    executionArgs.setArtifactVariables(asList(
        ArtifactVariable.builder()
            .entityType(SERVICE)
            .entityId("SERVICE_ID_1")
            .name("art_parameterized")
            .artifactStreamMetadata(
                ArtifactStreamMetadata.builder().artifactStreamId(ARTIFACT_STREAM_ID).runtimeValues(map1).build())
            .build()));
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .jobname("releases")
                                                  .groupId("mygroup")
                                                  .artifactPaths(asList("${artifactId}"))
                                                  .autoPopulate(false)
                                                  .serviceId(SERVICE_ID)
                                                  .name("testNexus")
                                                  .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);
    when(buildSourceService.getBuild(any(), any(), any(), any()))
        .thenReturn(BuildDetails.Builder.aBuildDetails().withNumber("1.0").build());
    Map<String, String> map = new HashMap<>();
    map.put("buildNo", "1.0");
    Artifact artifact =
        Artifact.Builder.anArtifact().withMetadata(new ArtifactMetadata(map)).withUuid(ARTIFACT_ID).build();
    when(artifactCollectionUtils.getArtifact(any(), any())).thenReturn(artifact);
    when(artifactService.create(artifact, nexusArtifactStream, false)).thenReturn(artifact);
    WorkflowExecution workflowExecution =
        wingsPersistence.getWithAppId(WorkflowExecution.class, app.getUuid(), WORKFLOW_EXECUTION_ID);
    try {
      workflowExecutionService.collectArtifactsAndStartExecution(workflowExecution, stateMachine, executionEventAdvisor,
          workflowExecutionUpdate, stdParams, app, workflow, null, executionArgs, null);
    } catch (Exception e) {
      log.info(e.getMessage());
    }
    workflowExecution = wingsPersistence.getWithAppId(WorkflowExecution.class, app.getUuid(), PIPELINE_EXECUTION_ID);

    assertThat(workflowExecution.getArtifacts()).containsExactlyInAnyOrder(artifact, artifact2);
    assertThat(workflowExecution.getExecutionArgs().getArtifacts()).containsExactlyInAnyOrder(artifact, artifact2);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldMergeArtifactVariableList() {
    ArtifactVariable artifactVariable =
        ArtifactVariable.builder()
            .artifactStreamMetadata(ArtifactStreamMetadata.builder()
                                        .artifactStreamId(ARTIFACT_STREAM_ID)
                                        .runtimeValues(Collections.singletonMap("buildNo", "1"))
                                        .build())
            .build();
    ArtifactVariable artifactVariable2 =
        ArtifactVariable.builder()
            .artifactStreamMetadata(ArtifactStreamMetadata.builder()
                                        .artifactStreamId(ARTIFACT_STREAM_ID + 2)
                                        .runtimeValues(Collections.singletonMap("buildNo", "1"))
                                        .build())
            .build();
    ArtifactVariable artifactVariable3 =
        ArtifactVariable.builder()
            .artifactStreamMetadata(ArtifactStreamMetadata.builder()
                                        .artifactStreamId(ARTIFACT_STREAM_ID + 2)
                                        .runtimeValues(Collections.singletonMap("buildNo", "1"))
                                        .build())
            .build();
    WorkflowExecution workflowExecution =
        WorkflowExecution.builder()
            .accountId(ACCOUNT_ID)
            .appId(app.getUuid())
            .uuid(PIPELINE_EXECUTION_ID)
            .executionArgs(
                ExecutionArgs.builder().artifactVariables(asList(artifactVariable, artifactVariable2)).build())
            .build();
    List<ArtifactVariable> artifactVariables = workflowExecutionService.getMergedArtifactVariableList(
        workflowExecution, asList(artifactVariable, artifactVariable3));
    assertThat(artifactVariables).contains(artifactVariable, artifactVariable3);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldAddArtifactVariableToWorkflowElement() {
    ArtifactVariable artifactVariable =
        ArtifactVariable.builder()
            .artifactStreamMetadata(ArtifactStreamMetadata.builder()
                                        .artifactStreamId(ARTIFACT_STREAM_ID)
                                        .runtimeValues(Collections.singletonMap("buildNo", "1"))
                                        .build())
            .build();
    ArtifactVariable artifactVariable2 =
        ArtifactVariable.builder()
            .artifactStreamMetadata(ArtifactStreamMetadata.builder()
                                        .artifactStreamId(ARTIFACT_STREAM_ID + 2)
                                        .runtimeValues(Collections.singletonMap("buildNo", "1"))
                                        .build())
            .build();
    ArtifactVariable artifactVariable3 = ArtifactVariable.builder().build();
    WorkflowStandardParams workflowStandardParams = new WorkflowStandardParams();
    List<ArtifactVariable> artifactVariables = new ArrayList<>();
    artifactVariables.add(artifactVariable2);
    workflowStandardParams.setWorkflowElement(WorkflowElement.builder().artifactVariables(artifactVariables).build());
    workflowExecutionService.addParameterizedArtifactVariableToContext(
        asList(artifactVariable, artifactVariable3), workflowStandardParams);
    assertThat(workflowStandardParams.getWorkflowElement().getArtifactVariables())
        .containsExactlyInAnyOrder(artifactVariable, artifactVariable2);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testWorkflowAuthorizationWithWebhookTriggerAuthorizationFfOn() throws InterruptedException {
    String appId = app.getUuid();
    when(featureFlagService.isEnabled(eq(WEBHOOK_TRIGGER_AUTHORIZATION), anyString())).thenReturn(true);
    User user = anUser().build();
    UserThreadLocal.set(user);

    Workflow workflow = createExecutableWorkflow(appId, env, "workflow1");
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(singletonList(
        Artifact.Builder.anArtifact().withAccountId(ACCOUNT_ID).withAppId(APP_ID).withUuid(ARTIFACT_ID).build()));

    WorkflowExecutionUpdateFake callback = new WorkflowExecutionUpdateFake();
    WorkflowExecution execution = workflowExecutionService.triggerOrchestrationWorkflowExecution(appId, env.getUuid(),
        workflow.getUuid(), null, executionArgs, callback,
        Trigger.builder().uuid(TRIGGER_ID).condition(new WebHookTriggerCondition()).build());
    callback.await(ofSeconds(15));

    assertThat(execution).isNotNull();
    String executionId = execution.getUuid();
    log.debug("Workflow executionId: {}", executionId);
    assertThat(executionId).isNotNull();
    execution = workflowExecutionService.getExecutionDetails(appId, executionId, true, false);
    assertThat(execution)
        .isNotNull()
        .hasFieldOrProperty("releaseNo")
        .extracting(WorkflowExecution::getUuid, WorkflowExecution::getStatus)
        .containsExactly(executionId, ExecutionStatus.SUCCESS);

    verify(deploymentAuthHandler).authorizeWorkflowExecution(anyString(), anyString());
    verify(authService).checkIfUserAllowedToDeployWorkflowToEnv(anyString(), anyString());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldTriggerWorkflowWithArtifactInputsAndDisableArtifactCollectionFFOn() throws InterruptedException {
    String appId = app.getUuid();
    when(featureFlagService.isEnabled(eq(ARTIFACT_COLLECTION_CONFIGURABLE), anyString())).thenReturn(true);

    SettingAttribute computeProvider =
        aSettingAttribute().withAppId(app.getUuid()).withValue(aPhysicalDataCenterConfig().build()).build();
    when(mockSettingsService.getByAccountAndId(any(), any())).thenReturn(computeProvider);

    wingsPersistence.save(computeProvider);
    final InfrastructureDefinition infraDefinition = createInfraDefinition(computeProvider, "Name4", "host1");
    Service service = addService("svc1");
    Service service2 = addService("svc2");
    Workflow workflow = createWorkflow(app.getAppId(), env, service, infraDefinition);

    ExecutionArgs executionArgs = new ExecutionArgs();

    ArtifactVariable artifactVariable1 =
        ArtifactVariable.builder()
            .artifactInput(ArtifactInput.builder().buildNo("build1").artifactStreamId(ARTIFACT_STREAM_ID + "1").build())
            .build();

    ArtifactVariable artifactVariable2 =
        ArtifactVariable.builder()
            .artifactInput(ArtifactInput.builder().buildNo("build2").artifactStreamId(ARTIFACT_STREAM_ID + "2").build())
            .build();
    executionArgs.setArtifactVariables(asList(artifactVariable1, artifactVariable2));

    when(artifactStreamServiceBindingService.listArtifactStreamIds(service.getUuid()))
        .thenReturn(Collections.singletonList(ARTIFACT_STREAM_ID + "1"));
    when(artifactStreamServiceBindingService.listArtifactStreamIds(service2.getUuid()))
        .thenReturn(Collections.singletonList(ARTIFACT_STREAM_ID + "2"));

    WorkflowExecutionUpdateFake callback = new WorkflowExecutionUpdateFake();
    WorkflowExecution execution = workflowExecutionService.triggerOrchestrationWorkflowExecution(appId, env.getUuid(),
        workflow.getUuid(), null, executionArgs, callback,
        Trigger.builder().uuid(TRIGGER_ID).condition(new WebHookTriggerCondition()).build());
    callback.await(ofSeconds(15));

    assertThat(execution).isNotNull();
    String executionId = execution.getUuid();
    log.debug("Workflow executionId: {}", executionId);
    assertThat(executionId).isNotNull();
    execution = workflowExecutionService.getExecutionDetails(appId, executionId, true, false);
    assertThat(execution)
        .isNotNull()
        .hasFieldOrProperty("releaseNo")
        .extracting(WorkflowExecution::getUuid, WorkflowExecution::getStatus)
        .containsExactly(executionId, FAILED);

    List<StateExecutionInstance> response =
        wingsPersistence
            .query(StateExecutionInstance.class,
                PageRequestBuilder.aPageRequest()
                    .addFilter(StateExecutionInstanceKeys.appId, EQ, appId)
                    .addFilter(StateExecutionInstanceKeys.executionUuid, EQ, execution.getUuid())
                    .addFilter(StateExecutionInstanceKeys.stateType, EQ, ARTIFACT_COLLECTION)
                    .build())
            .getResponse();
    assertThat(response).isNotNull().isNotEmpty();
    List<ContextElement> elements =
        response.get(0)
            .getContextElements()
            .stream()
            .filter(contextElement -> contextElement.getElementType() == ContextElementType.STANDARD)
            .collect(toList());
    assertThat(elements).isNotNull().isNotEmpty();
    assertThat(elements.get(0)).isInstanceOf(WorkflowStandardParams.class);
    WorkflowStandardParams workflowStandardParams = (WorkflowStandardParams) elements.get(0);
    assertThat(workflowStandardParams.getArtifactInputs()).isNotNull().isNotEmpty();
    assertThat(workflowStandardParams.getArtifactInputs().get(0)).isEqualTo(artifactVariable1.getArtifactInput());
    verify(artifactStreamServiceBindingService).listArtifactStreamIds(service.getUuid());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void shouldSaveRejectedByFreezeWindowsFFOn() {
    User user = anUser().uuid(generateUuid()).name("user-name").build();
    UserThreadLocal.set(user);
    doThrow(new InvalidRequestException("User is not authorized", WingsException.USER))
        .when(deploymentAuthHandler)
        .authorizeDeploymentDuringFreeze();
    String appId = app.getUuid();
    Workflow workflow = createExecutableWorkflow(appId, env, "workflow1");
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(singletonList(
        Artifact.Builder.anArtifact().withAccountId(ACCOUNT_ID).withAppId(APP_ID).withUuid(ARTIFACT_ID).build()));

    WorkflowExecutionUpdateFake callback = new WorkflowExecutionUpdateFake();
    when(featureFlagService.isEnabled(FeatureName.NEW_DEPLOYMENT_FREEZE, account.getUuid())).thenReturn(true);
    when(featureFlagService.isEnabled(FeatureName.SPG_SAVE_REJECTED_BY_FREEZE_WINDOWS, account.getUuid()))
        .thenReturn(true);

    GovernanceConfig governanceConfig = GovernanceConfig.builder()
                                            .accountId(account.getUuid())
                                            .timeRangeBasedFreezeConfigs(Collections.singletonList(
                                                TimeRangeBasedFreezeConfig.builder()
                                                    .name("freeze1")
                                                    .uuid(FREEZE_WINDOW_ID)
                                                    .timeRange(new TimeRange(0, 1, "", false, null, null, null, false))
                                                    .build()))
                                            .build();
    when(governanceConfigService.get(account.getUuid())).thenReturn(governanceConfig);
    when(governanceConfigService.getFrozenEnvIdsForApp(account.getUuid(), appId, governanceConfig))
        .thenReturn(Collections.singletonMap(FREEZE_WINDOW_ID, Collections.singleton(env.getUuid())));
    assertThatThrownBy(()
                           -> workflowExecutionService.triggerOrchestrationWorkflowExecution(
                               appId, env.getUuid(), workflow.getUuid(), null, executionArgs, callback, null))
        .isInstanceOf(DeploymentFreezeException.class)
        .hasMessage(
            "Deployment Freeze Window [freeze1] is active for the environment. No deployments are allowed to proceed.")
        .hasFieldOrPropertyWithValue("deploymentFreezeIds", Collections.singletonList(FREEZE_WINDOW_ID))
        .hasFieldOrPropertyWithValue("deploymentFreezeNamesList", Collections.singletonList("freeze1"));
  }
}
