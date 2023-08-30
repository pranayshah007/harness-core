/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ApiKeyInfo.getEmbeddedUserFromApiKey;
import static io.harness.beans.ExecutionInterruptType.ABORT_ALL;
import static io.harness.beans.ExecutionInterruptType.PAUSE;
import static io.harness.beans.ExecutionInterruptType.PAUSE_ALL;
import static io.harness.beans.ExecutionInterruptType.RESUME_ALL;
import static io.harness.beans.ExecutionStatus.ABORTED;
import static io.harness.beans.ExecutionStatus.ERROR;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.NEW;
import static io.harness.beans.ExecutionStatus.PAUSED;
import static io.harness.beans.ExecutionStatus.PAUSING;
import static io.harness.beans.ExecutionStatus.PREPARING;
import static io.harness.beans.ExecutionStatus.QUEUED;
import static io.harness.beans.ExecutionStatus.REJECTED;
import static io.harness.beans.ExecutionStatus.RUNNING;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.beans.ExecutionStatus.STARTING;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.ExecutionStatus.WAITING;
import static io.harness.beans.ExecutionStatus.activeStatuses;
import static io.harness.beans.ExecutionStatus.isActiveStatus;
import static io.harness.beans.FeatureName.ADD_MANIFEST_COLLECTION_STEP;
import static io.harness.beans.FeatureName.ARTIFACT_COLLECTION_CONFIGURABLE;
import static io.harness.beans.FeatureName.AUTO_REJECT_PREVIOUS_APPROVALS;
import static io.harness.beans.FeatureName.HELM_CHART_AS_ARTIFACT;
import static io.harness.beans.FeatureName.INFRA_MAPPING_BASED_ROLLBACK_ARTIFACT;
import static io.harness.beans.FeatureName.NEW_DEPLOYMENT_FREEZE;
import static io.harness.beans.FeatureName.PIPELINE_PER_ENV_DEPLOYMENT_PERMISSION;
import static io.harness.beans.FeatureName.RESOLVE_DEPLOYMENT_TAGS_BEFORE_EXECUTION;
import static io.harness.beans.FeatureName.SPG_ALLOW_REFRESH_PIPELINE_EXECUTION_BEFORE_CONTINUE_PIPELINE;
import static io.harness.beans.FeatureName.SPG_ENABLE_POPULATE_USING_ARTIFACT_VARIABLE;
import static io.harness.beans.FeatureName.SPG_SAVE_REJECTED_BY_FREEZE_WINDOWS;
import static io.harness.beans.FeatureName.WEBHOOK_TRIGGER_AUTHORIZATION;
import static io.harness.beans.FeatureName.WORKFLOW_EXECUTION_REFRESH_STATUS;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.GE;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.beans.SearchFilter.Operator.LT_EQ;
import static io.harness.beans.SearchFilter.Operator.NOT_EXISTS;
import static io.harness.beans.SortOrder.Builder.aSortOrder;
import static io.harness.beans.WorkflowType.ORCHESTRATION;
import static io.harness.beans.WorkflowType.PIPELINE;
import static io.harness.data.structure.CollectionUtils.trimmedLowercaseSet;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eraro.ErrorCode.INVALID_ARGUMENT;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.exception.WingsException.USER;
import static io.harness.govern.Switch.unhandled;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;
import static io.harness.mongo.MongoConfig.NO_LIMIT;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.threading.Morpheus.quietSleep;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.ApprovalDetails.Action.APPROVE;
import static software.wings.beans.ApprovalDetails.Action.REJECT;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.ElementExecutionSummary.ElementExecutionSummaryBuilder.anElementExecutionSummary;
import static software.wings.beans.EntityType.DEPLOYMENT;
import static software.wings.beans.PipelineExecution.Builder.aPipelineExecution;
import static software.wings.beans.WorkflowExecution.ACCOUNTID_STARTTS_SERVICEIDS;
import static software.wings.beans.WorkflowExecution.APPID_STATUS_WORKFLOWID_INFRAMAPPINGIDS_CREATEDAT;
import static software.wings.beans.WorkflowExecution.APPID_WORKFLOWID_STATUS_CREATEDAT;
import static software.wings.beans.WorkflowExecution.APPID_WORKFLOWID_STATUS_DEPLOYEDSERVICES_CREATEDAT;
import static software.wings.beans.WorkflowExecution.LAST_INFRAMAPPING_SEARCH_2;
import static software.wings.beans.deployment.DeploymentMetadata.Include;
import static software.wings.beans.deployment.DeploymentMetadata.Include.ARTIFACT_SERVICE;
import static software.wings.beans.deployment.DeploymentMetadata.Include.DEPLOYMENT_TYPE;
import static software.wings.beans.deployment.DeploymentMetadata.Include.ENVIRONMENT;
import static software.wings.service.impl.ApplicationManifestServiceImpl.CHART_NAME;
import static software.wings.service.impl.pipeline.PipelineServiceHelper.generatePipelineExecutionUrl;
import static software.wings.sm.ExecutionInterrupt.ExecutionInterruptBuilder.anExecutionInterrupt;
import static software.wings.sm.InstanceStatusSummary.InstanceStatusSummaryBuilder.anInstanceStatusSummary;
import static software.wings.sm.StateType.APPROVAL;
import static software.wings.sm.StateType.APPROVAL_RESUME;
import static software.wings.sm.StateType.ARTIFACT_COLLECTION;
import static software.wings.sm.StateType.ARTIFACT_COLLECT_LOOP_STATE;
import static software.wings.sm.StateType.AZURE_WEBAPP_SLOT_SETUP;
import static software.wings.sm.StateType.CUSTOM_DEPLOYMENT_FETCH_INSTANCES;
import static software.wings.sm.StateType.ENV_LOOP_RESUME_STATE;
import static software.wings.sm.StateType.ENV_LOOP_STATE;
import static software.wings.sm.StateType.ENV_RESUME_STATE;
import static software.wings.sm.StateType.ENV_ROLLBACK_STATE;
import static software.wings.sm.StateType.ENV_STATE;
import static software.wings.sm.StateType.PCF_RESIZE;
import static software.wings.sm.StateType.PHASE;
import static software.wings.sm.StateType.PHASE_STEP;
import static software.wings.sm.states.ArtifactCollectLoopState.ArtifactCollectLoopStateKeys;

import static dev.morphia.mapping.Mapper.ID_KEY;
import static io.fabric8.utils.Lists.isNullOrEmpty;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ofDays;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofMinutes;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.alert.AlertData;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ApiKeyInfo;
import io.harness.beans.CreatedByType;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EventPayload;
import io.harness.beans.EventType;
import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.ResourceConstraint;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SortOrder.OrderType;
import io.harness.beans.SweepingOutputInstance.Scope;
import io.harness.beans.WorkflowType;
import io.harness.beans.event.cg.CgPipelineStartPayload;
import io.harness.beans.event.cg.CgWorkflowStartPayload;
import io.harness.beans.event.cg.application.ApplicationEventData;
import io.harness.beans.event.cg.entities.EnvironmentEntity;
import io.harness.beans.event.cg.entities.InfraDefinitionEntity;
import io.harness.beans.event.cg.entities.ServiceEntity;
import io.harness.beans.event.cg.pipeline.ExecutionArgsEventData;
import io.harness.beans.event.cg.pipeline.PipelineEventData;
import io.harness.beans.event.cg.pipeline.PipelineExecData;
import io.harness.beans.event.cg.workflow.WorkflowEventData;
import io.harness.beans.event.cg.workflow.WorkflowExecData;
import io.harness.cache.MongoStore;
import io.harness.context.ContextElementType;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.distribution.constraint.Consumer;
import io.harness.distribution.constraint.Consumer.State;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.DeploymentFreezeException;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ff.FeatureFlagService;
import io.harness.limits.InstanceUsageExceededLimitException;
import io.harness.limits.checker.LimitApproachingException;
import io.harness.limits.checker.UsageLimitExceededException;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.index.BasicDBUtils;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.queue.QueuePublisher;
import io.harness.serializer.KryoSerializer;
import io.harness.serializer.MapperUtils;
import io.harness.service.EventService;
import io.harness.state.inspection.StateInspectionService;
import io.harness.tasks.ResponseData;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.api.AppManifestCollectionExecutionData;
import software.wings.api.ApprovalStateExecutionData;
import software.wings.api.ArtifactCollectionExecutionData;
import software.wings.api.AwsAmiDeployStateExecutionData;
import software.wings.api.CommandStateExecutionData;
import software.wings.api.ContinuePipelineResponseData;
import software.wings.api.DeploymentType;
import software.wings.api.EnvStateExecutionData;
import software.wings.api.HelmDeployStateExecutionData;
import software.wings.api.InstanceElement;
import software.wings.api.KubernetesSteadyStateCheckExecutionData;
import software.wings.api.PhaseElement;
import software.wings.api.PhaseExecutionData;
import software.wings.api.PhaseStepExecutionData;
import software.wings.api.PipelineElement;
import software.wings.api.ServiceElement;
import software.wings.api.ServiceTemplateElement;
import software.wings.api.SkipStateExecutionData;
import software.wings.api.WorkflowElement;
import software.wings.api.WorkflowElement.WorkflowElementBuilder;
import software.wings.api.customdeployment.InstanceFetchStateExecutionData;
import software.wings.api.k8s.K8sStateExecutionData;
import software.wings.api.pcf.PcfDeployStateExecutionData;
import software.wings.app.MainConfiguration;
import software.wings.beans.AmiDeploymentType;
import software.wings.beans.ApiKeyEntry;
import software.wings.beans.Application;
import software.wings.beans.ApprovalAuthorization;
import software.wings.beans.ApprovalDetails;
import software.wings.beans.ArtifactStreamMetadata;
import software.wings.beans.ArtifactVariable;
import software.wings.beans.AwsLambdaExecutionSummary;
import software.wings.beans.BuildExecutionSummary;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.CanaryWorkflowExecutionAdvisor;
import software.wings.beans.CollectionEntityType;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.CountsByStatuses;
import software.wings.beans.CustomOrchestrationWorkflow;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.ElementExecutionSummary;
import software.wings.beans.EntityType;
import software.wings.beans.EntityVersion;
import software.wings.beans.EntityVersion.ChangeType;
import software.wings.beans.EnvSummary;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.GraphGroup;
import software.wings.beans.GraphNode;
import software.wings.beans.HelmChartInputType;
import software.wings.beans.HelmExecutionSummary;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.ManifestVariable;
import software.wings.beans.NameValuePair;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.ParallelInfo;
import software.wings.beans.PhaseStep;
import software.wings.beans.Pipeline;
import software.wings.beans.Pipeline.PipelineKeys;
import software.wings.beans.PipelineExecution;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.PipelineStageExecution;
import software.wings.beans.PipelineStageExecutionAdvisor;
import software.wings.beans.PipelineStageGroupedInfo;
import software.wings.beans.RequiredExecutionArgs;
import software.wings.beans.ResourceConstraintInstance;
import software.wings.beans.RuntimeInputsConfig;
import software.wings.beans.Service;
import software.wings.beans.ServiceInstance;
import software.wings.beans.StateExecutionElement;
import software.wings.beans.StateExecutionInterrupt;
import software.wings.beans.User;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.DeploymentRateApproachingLimitAlert;
import software.wings.beans.alert.UsageLimitExceededAlert;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.appmanifest.ManifestInput;
import software.wings.beans.approval.ApprovalInfo;
import software.wings.beans.approval.PreviousApprovalDetails;
import software.wings.beans.artifact.ArtifactInput;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.baseline.WorkflowExecutionBaseline;
import software.wings.beans.baseline.WorkflowExecutionBaseline.WorkflowExecutionBaselineKeys;
import software.wings.beans.concurrency.ConcurrentExecutionResponse;
import software.wings.beans.concurrency.ConcurrentExecutionResponse.ConcurrentExecutionResponseBuilder;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.beans.deployment.WorkflowVariablesMetadata;
import software.wings.beans.execution.RollbackType;
import software.wings.beans.execution.RollbackWorkflowExecutionInfo;
import software.wings.beans.execution.WorkflowExecutionInfo;
import software.wings.beans.execution.WorkflowExecutionInfo.WorkflowExecutionInfoBuilder;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.TriggerConditionType;
import software.wings.dl.WingsPersistence;
import software.wings.exception.InvalidBaselineConfigurationException;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.url.SubdomainUrlHelper;
import software.wings.infra.AwsAmiInfrastructure;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.security.ExecutableElementsFilter;
import software.wings.security.UserThreadLocal;
import software.wings.service.ArtifactStreamHelper;
import software.wings.service.impl.WorkflowTree.WorkflowTreeBuilder;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.impl.deployment.checks.DeploymentCtx;
import software.wings.service.impl.deployment.checks.DeploymentFreezeChecker;
import software.wings.service.impl.pipeline.PipelineServiceHelper;
import software.wings.service.impl.pipeline.resume.PipelineResumeUtils;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.impl.security.auth.DeploymentAuthHandler;
import software.wings.service.impl.workflow.WorkflowNotificationHelper;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.service.impl.workflow.queuing.WorkflowConcurrencyHelper;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.ApiKeyService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.BarrierService;
import software.wings.service.intfc.BarrierService.OrchestrationWorkflowInfo;
import software.wings.service.intfc.BarrierService.OrchestrationWorkflowInfo.OrchestrationWorkflowInfoBuilder;
import software.wings.service.intfc.BuildSourceService;
import software.wings.service.intfc.EntityVersionService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ResourceConstraintService;
import software.wings.service.intfc.ServiceInstanceService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.WorkflowExecutionBaselineService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;
import software.wings.service.intfc.compliance.GovernanceConfigService;
import software.wings.service.intfc.deployment.AccountExpiryCheck;
import software.wings.service.intfc.deployment.PreDeploymentChecker;
import software.wings.service.intfc.deployment.RateLimitCheck;
import software.wings.service.intfc.deployment.ServiceInstanceUsage;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionEventAdvisor;
import software.wings.sm.ExecutionInterrupt;
import software.wings.sm.ExecutionInterrupt.ExecutionInterruptKeys;
import software.wings.sm.ExecutionInterruptEffect;
import software.wings.sm.ExecutionInterruptManager;
import software.wings.sm.InfraDefinitionSummary;
import software.wings.sm.InstanceStatusSummary;
import software.wings.sm.PhaseExecutionSummary;
import software.wings.sm.PhaseStepExecutionSummary;
import software.wings.sm.PipelineSummary;
import software.wings.sm.RollbackConfirmation;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;
import software.wings.sm.StateMachine;
import software.wings.sm.StateMachineExecutionSimulator;
import software.wings.sm.StateMachineExecutor;
import software.wings.sm.StateType;
import software.wings.sm.StepExecutionSummary;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.rollback.RollbackStateMachineGenerator;
import software.wings.sm.states.ElementStateExecutionData;
import software.wings.sm.states.EnvState.EnvStateKeys;
import software.wings.sm.states.ForkState.ForkStateExecutionData;
import software.wings.sm.states.HoldingScope;
import software.wings.sm.states.RepeatState.RepeatStateExecutionData;
import software.wings.sm.states.azure.AzureVMSSDeployStateExecutionData;
import software.wings.sm.states.azure.appservices.AzureAppServiceSlotSetupExecutionData;
import software.wings.sm.states.spotinst.SpotInstDeployStateExecutionData;
import software.wings.sm.status.StateStatusUpdateInfo;
import software.wings.sm.status.WorkflowStatusPropagator;
import software.wings.sm.status.WorkflowStatusPropagatorFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import dev.morphia.query.CriteriaContainer;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.constraints.NotNull;
import javax.validation.executable.ValidateOnExecution;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * The Class WorkflowExecutionServiceImpl.
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@OwnedBy(CDC)
@Singleton
@ValidateOnExecution
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class WorkflowExecutionServiceImpl implements WorkflowExecutionService {
  @Inject private MainConfiguration mainConfiguration;
  @Inject private BarrierService barrierService;
  @Inject private StateMachineExecutor stateMachineExecutor;
  @Inject private EnvironmentService environmentService;
  @Inject private StateExecutionService stateExecutionService;
  @Inject private ExecutionInterruptManager executionInterruptManager;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private ServiceInstanceService serviceInstanceService;
  @Inject private ArtifactService artifactService;
  @Inject private StateMachineExecutionSimulator stateMachineExecutionSimulator;
  @Inject private GraphRenderer graphRenderer;
  @Inject private AppService appService;
  @Inject private WorkflowService workflowService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private PipelineService pipelineService;
  @Inject private ExecutorService executorService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private QueuePublisher<ExecutionEvent> executionEventQueue;
  @Inject private WorkflowExecutionBaselineService workflowExecutionBaselineService;
  @Inject private EntityVersionService entityVersionService;
  @Inject private MongoStore mongoStore;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private UserGroupService userGroupService;
  @Inject private AuthHandler authHandler;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private PreDeploymentChecks preDeploymentChecks;
  @Inject private AlertService alertService;
  @Inject private WorkflowServiceHelper workflowServiceHelper;
  @Inject private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private WorkflowExecutionServiceHelper workflowExecutionServiceHelper;
  @Inject
  private software.wings.service.impl
      .MultiArtifactWorkflowExecutionServiceHelper multiArtifactWorkflowExecutionServiceHelper;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private ArtifactCollectionUtils artifactCollectionUtils;
  @Inject private GovernanceConfigService governanceConfigService;
  @Inject private HostService hostService;
  @Inject private WorkflowConcurrencyHelper workflowConcurrencyHelper;
  @Inject private ResourceConstraintService resourceConstraintService;
  @Inject private RollbackStateMachineGenerator rollbackStateMachineGenerator;
  @Inject private ResourceLookupFilterHelper resourceLookupFilterHelper;
  @Inject private PipelineResumeUtils pipelineResumeUtils;
  @Inject private ApiKeyService apiKeyService;
  @Inject private BuildSourceService buildSourceService;
  @Inject private ArtifactStreamHelper artifactStreamHelper;
  @Inject private DeploymentAuthHandler deploymentAuthHandler;
  @Inject private AuthService authService;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private HelmChartService helmChartService;
  @Inject private StateInspectionService stateInspectionService;
  @Inject private ApplicationManifestService applicationManifestService;
  @Inject private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;
  @Inject private Injector injector;
  @Inject private SubdomainUrlHelper subdomainUrlHelper;

  @Inject @RateLimitCheck private PreDeploymentChecker deployLimitChecker;
  @Inject @ServiceInstanceUsage private PreDeploymentChecker siUsageChecker;
  @Inject @AccountExpiryCheck private PreDeploymentChecker accountExpirationChecker;
  @Inject private WorkflowStatusPropagatorFactory workflowStatusPropagatorFactory;
  @Inject private WorkflowExecutionUpdate executionUpdate;
  @Inject private WorkflowNotificationHelper workflowNotificationHelper;
  private static final long SIXTY_DAYS_IN_MILLIS = 60 * 24 * 60 * 60 * 1000L;

  @Inject private EventService eventService;

  @Override
  public HIterator<WorkflowExecution> executions(
      String appId, long startedFrom, long statedTo, Set<String> includeOnlyFields) {
    Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                         .filter(WorkflowExecutionKeys.appId, appId)
                                         .field(WorkflowExecutionKeys.startTs)
                                         .greaterThanOrEq(startedFrom)
                                         .field(WorkflowExecutionKeys.startTs)
                                         .lessThan(statedTo);
    if (isNotEmpty(includeOnlyFields)) {
      includeOnlyFields.forEach(field -> query.project(field, true));
    }

    return new HIterator<>(query.limit(NO_LIMIT).fetch());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public PageResponse<WorkflowExecution> listExecutions(
      PageRequest<WorkflowExecution> pageRequest, boolean includeGraph) {
    return listExecutions(pageRequest, includeGraph, false, true, true, false, false);
  }

  @Override
  public List<WorkflowExecution> listExecutionsUsingQuery(
      Query<WorkflowExecution> query, FindOptions findOptions, boolean includeGraph) {
    List<WorkflowExecution> res = query.asList(findOptions);
    return processExecutions(res, includeGraph, false, true, true, false);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public PageResponse<WorkflowExecution> listExecutions(PageRequest<WorkflowExecution> pageRequest,
      boolean includeGraph, boolean runningOnly, boolean withBreakdownAndSummary, boolean includeStatus,
      boolean withFailureDetails, boolean fromUi) {
    PageResponse<WorkflowExecution> res;
    res = fromUi ? wingsPersistence.queryAnalytics(WorkflowExecution.class, pageRequest)
                 : wingsPersistence.query(WorkflowExecution.class, pageRequest);
    return (PageResponse<WorkflowExecution>) processExecutions(
        res, includeGraph, runningOnly, withBreakdownAndSummary, includeStatus, withFailureDetails);
  }

  private List<WorkflowExecution> processExecutions(List<WorkflowExecution> res, boolean includeGraph,
      boolean runningOnly, boolean withBreakdownAndSummary, boolean includeStatus, boolean withFailureDetails) {
    if (isEmpty(res)) {
      return res;
    }

    for (int i = 0; i < res.size(); i++) {
      WorkflowExecution workflowExecution = res.get(i);
      try {
        refreshBreakdown(workflowExecution);
        if (workflowExecution.getWorkflowType() == WorkflowType.PIPELINE) {
          // pipeline
          refreshPipelineExecution(workflowExecution);
          PipelineExecution pipelineExecution = workflowExecution.getPipelineExecution();

          // Done to ignore inconsistent pipeline executions with mismatch from setup
          if (pipelineExecution == null || pipelineExecution.getPipelineStageExecutions() == null
              || pipelineExecution.getPipeline() == null
              || pipelineExecution.getPipeline().getPipelineStages() == null) {
            res.remove(i);
            i--;
          } else if (pipelineExecution.getPipelineStageExecutions().size()
              != pipelineExecution.getPipeline().getPipelineStages().size()) {
            boolean isAnyStageLooped =
                pipelineExecution.getPipelineStageExecutions().stream().anyMatch(t -> t.isLooped());
            boolean hasAnyEnvRollback = pipelineExecution.getPipelineStageExecutions().stream().anyMatch(
                t -> ENV_ROLLBACK_STATE.getType().equals(t.getStateType()));
            if (isAnyStageLooped || hasAnyEnvRollback) {
              continue;
            } else {
              res.remove(i);
              i--;
            }
          }
          continue;
        }
      } catch (Exception e) {
        log.error("Failed to process executions");
      }
      if (withBreakdownAndSummary) {
        try {
          refreshSummaries(workflowExecution);
        } catch (Exception e) {
          log.error(
              format("Failed to refresh service summaries for the workflow execution %s", workflowExecution.getUuid()),
              e);
        }
      }

      if (!runningOnly || ExecutionStatus.isRunningStatus(workflowExecution.getStatus())
          || ExecutionStatus.isHaltedStatus(workflowExecution.getStatus())) {
        try {
          populateNodeHierarchy(workflowExecution, includeGraph, includeStatus, false);
        } catch (Exception e) {
          log.error("Failed to populate node hierarchy for the workflow execution {}", res.toString(), e);
        }
      }
    }
    for (WorkflowExecution workflowExecution : res) {
      if (withFailureDetails) {
        try {
          populateFailureDetails(workflowExecution);
        } catch (Exception e) {
          log.error("Failed to populate the failure details.");
        }
      }
    }

    return res;
  }

  @Override
  public boolean updateNotes(String appId, String workflowExecutionId, ExecutionArgs executionArgs) {
    notNullCheck("executionArgs", executionArgs, USER);
    notNullCheck("notes", executionArgs.getNotes(), USER);

    WorkflowExecution workflowExecution =
        getWorkflowExecution(appId, workflowExecutionId, WorkflowExecutionKeys.uuid, WorkflowExecutionKeys.appId);
    notNullCheck("workflowExecution", workflowExecution, USER);

    try {
      Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                           .filter(WorkflowExecutionKeys.appId, workflowExecution.getAppId())
                                           .filter(ID_KEY, workflowExecution.getUuid());
      UpdateOperations<WorkflowExecution> updateOps = wingsPersistence.createUpdateOperations(WorkflowExecution.class)
                                                          .set("executionArgs.notes", executionArgs.getNotes());
      UpdateResults updateResults = wingsPersistence.update(query, updateOps);
      return updateResults != null && updateResults.getWriteResult() != null
          && updateResults.getWriteResult().getN() > 0;

    } catch (Exception ex) {
      return false;
    }
  }

  @Override
  public boolean approveOrRejectExecution(
      String appId, List<String> userGroupIds, ApprovalDetails approvalDetails, ApiKeyEntry apiEntryKey) {
    if (apiEntryKey == null) {
      return approveOrRejectExecution(appId, userGroupIds, approvalDetails, (String) null);
    }
    if (apiEntryKey != null && isNotEmpty(userGroupIds)
        && !verifyAuthorizedToAcceptOrReject(userGroupIds, apiEntryKey.getUserGroupIds(), appId, null)) {
      throw new InvalidRequestException("User not authorized to accept or reject the approval");
    }

    return approveOrRejectExecution(appId, approvalDetails, null);
  }

  @Override
  public boolean approveOrRejectExecution(
      String appId, List<String> userGroupIds, ApprovalDetails approvalDetails, String executionUuid) {
    if (isNotEmpty(userGroupIds) && !verifyAuthorizedToAcceptOrReject(userGroupIds, appId, null)) {
      throw new InvalidRequestException("User not authorized to accept or reject the approval");
    }

    return approveOrRejectExecution(appId, approvalDetails, executionUuid);
  }

  private boolean approveOrRejectExecution(String appId, ApprovalDetails approvalDetails, String executionUuid) {
    User user = UserThreadLocal.get();
    if (user != null && approvalDetails.getApprovedBy() == null) {
      approvalDetails.setApprovedBy(
          EmbeddedUser.builder().uuid(user.getUuid()).email(user.getEmail()).name(user.getName()).build());
    }

    if (null == approvalDetails.getApprovedBy()) {
      log.error("Approved by not set in approval details. Details: {}", approvalDetails);
    }

    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalId(approvalDetails.getApprovalId())
                                                   .approvedBy(approvalDetails.getApprovedBy())
                                                   .comments(approvalDetails.getComments())
                                                   .approvalFromSlack(approvalDetails.isApprovalFromSlack())
                                                   .approvalFromGraphQL(approvalDetails.isApprovalFromGraphQL())
                                                   .approvalViaApiKey(approvalDetails.isApprovalViaApiKey())
                                                   .variables(approvalDetails.getVariables())
                                                   .build();

    if (approvalDetails.getAction() == APPROVE) {
      executionData.setStatus(SUCCESS);
    } else {
      executionData.setStatus(ExecutionStatus.REJECTED);
    }

    waitNotifyEngine.doneWith(approvalDetails.getApprovalId(), executionData);

    if (approvalDetails.getAction().isRollbackAction() && executionUuid != null) {
      WorkflowExecution workflowExecution = fetchWorkflowExecution(appId, executionUuid);
      if (workflowExecution.getWorkflowType() == PIPELINE) {
        executionData.setStatus(ExecutionStatus.REJECTED);
        throw new InvalidRequestException("Unsupported rollback from pipeline, defaulting to reject.");
      }
      ExecutionInterruptType executionInterruptType = null;

      switch (approvalDetails.getAction()) {
        case ROLLBACK_PROVISIONER_AFTER_PHASES:
          executionInterruptType = ExecutionInterruptType.ROLLBACK_PROVISIONER_AFTER_PHASES_ON_APPROVAL;
          break;
        default:
          executionInterruptType = ExecutionInterruptType.ROLLBACK_ON_APPROVAL;
      }
      ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                  .executionUuid(workflowExecution.getUuid())
                                                  .appId(appId)
                                                  .executionInterruptType(executionInterruptType)
                                                  .build();

      triggerExecutionInterrupt(executionInterrupt);
    }
    return true;
  }

  @Override
  public ApprovalStateExecutionData fetchApprovalStateExecutionDataFromWorkflowExecution(
      String appId, String workflowExecutionId, String stateExecutionId, ApprovalDetails approvalDetails) {
    notNullCheck("appId", appId, USER);

    notNullCheck("ApprovalDetails", approvalDetails, USER);
    notNullCheck("ApprovalId", approvalDetails.getApprovalId());
    String approvalId = approvalDetails.getApprovalId();
    notNullCheck("Approval action", approvalDetails.getAction());

    notNullCheck("workflowExecutionId", workflowExecutionId, USER);
    String[] fields = {WorkflowExecutionKeys.appId, WorkflowExecutionKeys.createdAt,
        WorkflowExecutionKeys.pipelineExecution, WorkflowExecutionKeys.status, WorkflowExecutionKeys.triggeredBy,
        WorkflowExecutionKeys.uuid, WorkflowExecutionKeys.workflowType};
    WorkflowExecution workflowExecution = getWorkflowExecution(appId, workflowExecutionId, fields);
    if (workflowExecution == null) {
      throw new InvalidRequestException(
          "No Execution found for given appId [" + appId + "] and executionId [" + workflowExecutionId + "]", USER);
    }
    if (workflowExecution.getWorkflowType() == PIPELINE) {
      refreshPipelineExecution(workflowExecution);
    }

    notNullCheck("workflowExecution", workflowExecution, USER);

    ApprovalStateExecutionData approvalStateExecutionData = null;
    String workflowType = "";

    // Pipeline approval
    if (workflowExecution.getWorkflowType() == WorkflowType.PIPELINE) {
      workflowType = "Pipeline";
      approvalStateExecutionData =
          fetchPipelineWaitingApprovalStateExecutionData(workflowExecution.getPipelineExecution(), approvalId);
    }

    // Workflow approval
    if (workflowExecution.getWorkflowType() == WorkflowType.ORCHESTRATION) {
      workflowType = "Workflow";
      approvalStateExecutionData =
          fetchWorkflowWaitingApprovalStateExecutionData(workflowExecution, stateExecutionId, approvalId);
    }

    if (approvalStateExecutionData == null) {
      throw new WingsException(INVALID_ARGUMENT, USER)
          .addParam("args",
              "No " + workflowType + " execution [" + workflowExecutionId + "] waiting for approval id: " + approvalId);
    }

    return approvalStateExecutionData;
  }

  @Override
  public List<ApprovalStateExecutionData> fetchApprovalStateExecutionsDataFromWorkflowExecution(
      String appId, String workflowExecutionId) {
    notNullCheck("appId", appId, USER);

    notNullCheck("workflowExecutionId", workflowExecutionId, USER);

    String[] fields = {WorkflowExecutionKeys.appId, WorkflowExecutionKeys.createdAt,
        WorkflowExecutionKeys.pipelineExecution, WorkflowExecutionKeys.status, WorkflowExecutionKeys.triggeredBy,
        WorkflowExecutionKeys.uuid, WorkflowExecutionKeys.workflowType};
    WorkflowExecution workflowExecution = getWorkflowExecution(appId, workflowExecutionId, fields);
    if (workflowExecution == null) {
      throw new InvalidRequestException(
          "No Execution found for given appId [" + appId + "] and executionId [" + workflowExecutionId + "]", USER);
    }
    if (workflowExecution.getWorkflowType() == PIPELINE) {
      refreshPipelineExecution(workflowExecution);
    }

    notNullCheck("workflowExecution", workflowExecution, USER);

    List<ApprovalStateExecutionData> approvalStateExecutionsData = new LinkedList<>();
    String workflowType = "";

    // Pipeline approval
    if (workflowExecution.getWorkflowType() == WorkflowType.PIPELINE) {
      workflowType = "Pipeline";
      approvalStateExecutionsData = fetchPipelineWaitingApprovalStateExecutionsData(workflowExecution);
    }

    // Workflow approval
    if (workflowExecution.getWorkflowType() == WorkflowType.ORCHESTRATION) {
      workflowType = "Workflow";
      approvalStateExecutionsData = fetchWorkflowWaitingApprovalStateExecutionsData(workflowExecution);
    }

    if (isEmpty(approvalStateExecutionsData)) {
      throw new InvalidRequestException("No Approval found for execution [" + workflowExecutionId + "]", USER);
    }

    return approvalStateExecutionsData;
  }

  private ApprovalStateExecutionData fetchPipelineWaitingApprovalStateExecutionData(
      PipelineExecution pipelineExecution, String approvalId) {
    if (pipelineExecution == null || pipelineExecution.getPipelineStageExecutions() == null) {
      return null;
    }

    for (PipelineStageExecution pe : pipelineExecution.getPipelineStageExecutions()) {
      if (pe.getStateExecutionData() instanceof ApprovalStateExecutionData) {
        ApprovalStateExecutionData approvalStateExecutionData = (ApprovalStateExecutionData) pe.getStateExecutionData();

        if (pe.getStatus() == ExecutionStatus.PAUSED && approvalStateExecutionData.getStatus() == ExecutionStatus.PAUSED
            && approvalId.equals(approvalStateExecutionData.getApprovalId())) {
          return approvalStateExecutionData;
        }
      }
    }

    return null;
  }

  private ApprovalStateExecutionData fetchWorkflowWaitingApprovalStateExecutionData(
      WorkflowExecution workflowExecution, String stateExecutionId, String approvalId) {
    if (stateExecutionId != null) {
      StateExecutionInstance stateExecutionInstance =
          wingsPersistence.createQuery(StateExecutionInstance.class).filter(ID_KEY, stateExecutionId).get();

      ApprovalStateExecutionData approvalStateExecutionData =
          (ApprovalStateExecutionData) stateExecutionInstance.fetchStateExecutionData();
      // Check for Approval Id in PAUSED status
      if (approvalStateExecutionData != null && approvalStateExecutionData.getStatus() == ExecutionStatus.PAUSED
          && approvalStateExecutionData.getApprovalId().equals(approvalId)) {
        return approvalStateExecutionData;
      }
    } else {
      List<StateExecutionInstance> stateExecutionInstances = getStateExecutionInstances(workflowExecution);
      for (StateExecutionInstance stateExecutionInstance : stateExecutionInstances) {
        if (stateExecutionInstance.fetchStateExecutionData() instanceof ApprovalStateExecutionData) {
          ApprovalStateExecutionData approvalStateExecutionData =
              (ApprovalStateExecutionData) stateExecutionInstance.fetchStateExecutionData();
          // Check for Approval Id in PAUSED status
          if (approvalStateExecutionData != null && approvalStateExecutionData.getStatus() == ExecutionStatus.PAUSED
              && approvalStateExecutionData.getApprovalId().equals(approvalId)) {
            return approvalStateExecutionData;
          }
        }
      }
    }

    return null;
  }

  private List<ApprovalStateExecutionData> fetchPipelineWaitingApprovalStateExecutionsData(
      WorkflowExecution pipelineWorkflowExecution) {
    PipelineExecution pipelineExecution = pipelineWorkflowExecution.getPipelineExecution();
    if (pipelineExecution == null || pipelineExecution.getPipelineStageExecutions() == null) {
      return null;
    }

    List<ApprovalStateExecutionData> approvalStateExecutionsData = new LinkedList<>();

    for (PipelineStageExecution pe : pipelineExecution.getPipelineStageExecutions()) {
      if (pe.getStateExecutionData() instanceof ApprovalStateExecutionData) {
        ApprovalStateExecutionData approvalStateExecutionData = (ApprovalStateExecutionData) pe.getStateExecutionData();

        if (pe.getStatus() == ExecutionStatus.PAUSED
            && approvalStateExecutionData.getStatus() == ExecutionStatus.PAUSED) {
          approvalStateExecutionData.setStageName(
              getStageNameForApprovalStateExecutionData(pipelineExecution, pe.getPipelineStageElementId()));
          approvalStateExecutionData.setExecutionUuid(pipelineWorkflowExecution.getUuid());
          approvalStateExecutionsData.add(approvalStateExecutionData);
        }
      } else {
        List<WorkflowExecution> workflowExecutions = pe.getWorkflowExecutions();
        for (WorkflowExecution workflowExecution : workflowExecutions) {
          if (RUNNING.equals(workflowExecution.getStatus()) || PAUSED.equals(workflowExecution.getStatus())) {
            List<ApprovalStateExecutionData> approvalStateExecutionDataList =
                fetchWorkflowWaitingApprovalStateExecutionsData(workflowExecution);
            approvalStateExecutionDataList.forEach(approvalStateExecutionData
                -> approvalStateExecutionData.setStageName(workflowExecution.getStageName()));
            approvalStateExecutionsData.addAll(approvalStateExecutionDataList);
          }
        }
      }
    }

    return approvalStateExecutionsData;
  }

  public String getStageNameForApprovalStateExecutionData(
      PipelineExecution pipelineExecution, String pipelineStageElementId) {
    List<PipelineStage> pipelineStages = pipelineExecution.getPipeline().getPipelineStages();
    if (pipelineStages == null) {
      return null;
    }

    for (PipelineStage pipelineStage : pipelineStages) {
      String stageName = pipelineStage.getName();
      for (PipelineStageElement pipelineStageElement : pipelineStage.getPipelineStageElements()) {
        if (pipelineStageElement != null && pipelineStageElement.getUuid().equals(pipelineStageElementId)) {
          return stageName;
        }
      }
    }

    return null;
  }

  private List<ApprovalStateExecutionData> fetchWorkflowWaitingApprovalStateExecutionsData(
      WorkflowExecution workflowExecution) {
    List<ApprovalStateExecutionData> approvalStateExecutionsData = new LinkedList<>();

    List<StateExecutionInstance> stateExecutionInstances = getStateExecutionInstances(workflowExecution);
    for (StateExecutionInstance stateExecutionInstance : stateExecutionInstances) {
      if (stateExecutionInstance.fetchStateExecutionData() instanceof ApprovalStateExecutionData) {
        ApprovalStateExecutionData approvalStateExecutionData =
            (ApprovalStateExecutionData) stateExecutionInstance.fetchStateExecutionData();
        // Check for Approval Id in PAUSED status
        if (approvalStateExecutionData != null && approvalStateExecutionData.getStatus() == ExecutionStatus.PAUSED) {
          // State name is unique inside a phase step
          approvalStateExecutionData.setApprovalStateIdentifier(
              stateExecutionInstance.getChildStateMachineId() + "_" + stateExecutionInstance.getStateName());
          approvalStateExecutionData.setExecutionUuid(workflowExecution.getUuid());
          approvalStateExecutionsData.add(approvalStateExecutionData);
        }
      }
    }

    return approvalStateExecutionsData;
  }

  @Override
  public void refreshPipelineExecution(WorkflowExecution workflowExecution) {
    if (workflowExecution == null || workflowExecution.getPipelineExecution() == null) {
      return;
    }

    if (workflowExecution.getPipelineExecution().getPipelineStageExecutions() == null) {
      workflowExecution.getPipelineExecution().setPipelineStageExecutions(new ArrayList<>());
    }

    if (ExecutionStatus.isFinalStatus(workflowExecution.getPipelineExecution().getStatus())
        && workflowExecution.getPipelineExecution()
               .getPipelineStageExecutions()
               .stream()
               .flatMap(pipelineStageExecution -> pipelineStageExecution.getWorkflowExecutions().stream())
               .allMatch(workflowExecution1 -> ExecutionStatus.isFinalStatus(workflowExecution1.getStatus()))) {
      return;
    }

    PipelineExecution pipelineExecution = workflowExecution.getPipelineExecution();
    Pipeline pipeline = pipelineExecution.getPipeline();
    ImmutableMap<String, StateExecutionInstance> stateExecutionInstanceMap =
        getStateExecutionInstanceMap(workflowExecution);
    List<PipelineStageExecution> stageExecutionDataList = new ArrayList<>();

    pipeline.getPipelineStages()
        .stream()
        .flatMap(pipelineStage -> pipelineStage.getPipelineStageElements().stream())
        .forEach(pipelineStageElement -> {
          StateExecutionInstance stateExecutionInstance = stateExecutionInstanceMap.get(pipelineStageElement.getName());

          if (stateExecutionInstance == null) {
            Long estimatedTime = pipeline.getStateEtaMap().get(pipelineStageElement.getUuid());
            // required for backward compatibility, Can be removed later
            if (estimatedTime == null) {
              estimatedTime = pipeline.getStateEtaMap().get(pipelineStageElement.getName());
            }
            stageExecutionDataList.add(
                PipelineStageExecution.builder()
                    .parallelInfo(ParallelInfo.builder().groupIndex(pipelineStageElement.getParallelIndex()).build())
                    .pipelineStageElementId(pipelineStageElement.getUuid())
                    .stateUuid(pipelineStageElement.getUuid())
                    .stateType(pipelineStageElement.getType())
                    .stateName(pipelineStageElement.getName())
                    .status(QUEUED)
                    .estimatedTime(estimatedTime)
                    .build());

          } else {
            PipelineStageExecution stageExecution =
                PipelineStageExecution.builder()
                    .parallelInfo(ParallelInfo.builder().groupIndex(pipelineStageElement.getParallelIndex()).build())
                    .pipelineStageElementId(pipelineStageElement.getUuid())
                    .stateUuid(pipelineStageElement.getUuid())
                    .stateType(stateExecutionInstance.getStateType())
                    .status(stateExecutionInstance.getStatus())
                    .stateName(stateExecutionInstance.getDisplayName())
                    .startTs(stateExecutionInstance.getStartTs())
                    .triggeredBy(workflowExecution.getTriggeredBy())
                    .expiryTs(stateExecutionInstance.getExpiryTs())
                    .endTs(stateExecutionInstance.getEndTs())
                    .build();

            appendSkipCondition(pipelineStageElement, stageExecution, stateExecutionInstance.getUuid());

            StateExecutionData stateExecutionData = stateExecutionInstance.fetchStateExecutionData();

            if (stateExecutionData instanceof SkipStateExecutionData) {
              stageExecution.setStateExecutionData(stateExecutionData);
              stageExecution.setMessage(stateExecutionData.getErrorMsg());
              stageExecutionDataList.add(stageExecution);
            } else if (APPROVAL.name().equals(stateExecutionInstance.getStateType())
                || APPROVAL_RESUME.name().equals(stateExecutionInstance.getStateType())) {
              if (stateExecutionData instanceof ApprovalStateExecutionData) {
                stageExecution.setStateExecutionData(stateExecutionData);

                ApprovalStateExecutionData approvalStateExecutionData = (ApprovalStateExecutionData) stateExecutionData;
                approvalStateExecutionData.setUserGroupList(
                    userGroupService.fetchUserGroupNamesFromIds(approvalStateExecutionData.getUserGroups()));
                approvalStateExecutionData.setAuthorized(
                    verifyAuthorizedToAcceptOrReject(approvalStateExecutionData.getUserGroups(), pipeline.getAppId(),
                        pipelineExecution.getPipelineId()));
                approvalStateExecutionData.setAppId(pipeline.getAppId());
                approvalStateExecutionData.setWorkflowId(pipelineExecution.getPipelineId());
              }
              stageExecution.setMessage(stateExecutionData != null ? stateExecutionData.getErrorMsg() : "");
              stageExecutionDataList.add(stageExecution);

            } else if (ENV_STATE.name().equals(stateExecutionInstance.getStateType())
                || ENV_RESUME_STATE.name().equals(stateExecutionInstance.getStateType())) {
              if (stateExecutionData instanceof EnvStateExecutionData) {
                EnvStateExecutionData envStateExecutionData = (EnvStateExecutionData) stateExecutionData;
                setWaitingForInputFlag(stateExecutionInstance, stageExecution);
                if (envStateExecutionData.getWorkflowExecutionId() != null) {
                  WorkflowExecution workflowExecution2 = getExecutionDetailsWithoutGraph(
                      workflowExecution.getAppId(), envStateExecutionData.getWorkflowExecutionId());
                  workflowExecution2.setStateMachine(null);

                  stageExecution.setWorkflowExecutions(asList(workflowExecution2));
                  stageExecution.setStatus(workflowExecution2.getStatus());
                }
                stageExecution.setMessage(envStateExecutionData.getErrorMsg());
              }
              stageExecutionDataList.add(stageExecution);
            } else if (ENV_LOOP_STATE.name().equals(stateExecutionInstance.getStateType())
                || ENV_LOOP_RESUME_STATE.name().equals(stateExecutionInstance.getStateType())) {
              setWaitingForInputFlag(stateExecutionInstance, stageExecution);
              if (stateExecutionData instanceof ForkStateExecutionData) {
                handleEnvLoopStateExecutionData(workflowExecution.getAppId(), stateExecutionInstanceMap,
                    stageExecutionDataList, (ForkStateExecutionData) stateExecutionData, pipelineStageElement,
                    stateExecutionInstance.getUuid());
              } else if (stateExecutionData instanceof EnvStateExecutionData) {
                EnvStateExecutionData envStateExecutionData = (EnvStateExecutionData) stateExecutionData;
                stageExecution.setMessage(envStateExecutionData.getErrorMsg());
                stageExecutionDataList.add(stageExecution);
              }

            } else {
              throw new InvalidRequestException("Unknown stateType " + stateExecutionInstance.getStateType());
            }
          }
        });

    stateExecutionInstanceMap.entrySet()
        .stream()
        .filter(entry -> entry.getValue().getStateType().equals(ENV_ROLLBACK_STATE.getType()))
        .map(entry -> entry.getValue())
        .sorted(Comparator.comparingInt(instance -> {
          StateExecutionData stateExecutionData = instance.fetchStateExecutionData();
          if (stateExecutionData instanceof SkipStateExecutionData) {
            return ((SkipStateExecutionData) stateExecutionData).getPipelineStageParallelIndex();
          }
          return ((EnvStateExecutionData) stateExecutionData).getPipelineStageParallelIndex();
        }))
        .forEach(stateExecutionInstance -> {
          StateExecutionData stateExecutionData = stateExecutionInstance.fetchStateExecutionData();
          PipelineStageExecution stageExecution = PipelineStageExecution.builder()
                                                      .parallelInfo(ParallelInfo.builder().build())
                                                      .stateType(stateExecutionInstance.getStateType())
                                                      .status(stateExecutionInstance.getStatus())
                                                      .stateName(stateExecutionInstance.getDisplayName())
                                                      .startTs(stateExecutionInstance.getStartTs())
                                                      .triggeredBy(workflowExecution.getTriggeredBy())
                                                      .expiryTs(stateExecutionInstance.getExpiryTs())
                                                      .endTs(stateExecutionInstance.getEndTs())
                                                      .build();
          if (stateExecutionData instanceof EnvStateExecutionData) {
            EnvStateExecutionData envStateExecutionData = (EnvStateExecutionData) stateExecutionData;
            stageExecution.getParallelInfo().setGroupIndex(envStateExecutionData.getPipelineStageParallelIndex());
            stageExecution.setPipelineStageElementId(envStateExecutionData.getPipelineStageElementId());

            if (envStateExecutionData.getWorkflowExecutionId() != null) {
              WorkflowExecution workflowExecution2 = getExecutionDetailsWithoutGraph(
                  workflowExecution.getAppId(), envStateExecutionData.getWorkflowExecutionId());
              workflowExecution2.setStateMachine(null);

              stageExecution.setWorkflowExecutions(asList(workflowExecution2));
              stageExecution.setStatus(workflowExecution2.getStatus());
            }
            stageExecution.setMessage(stateExecutionData.getErrorMsg());
            stageExecutionDataList.add(stageExecution);
          }
          if (stateExecutionData instanceof SkipStateExecutionData) {
            SkipStateExecutionData skipStateExecutionData = (SkipStateExecutionData) stateExecutionData;
            stageExecution.setStateExecutionData(skipStateExecutionData);
            stageExecution.setStatus(SKIPPED);
            stageExecution.setSkipCondition("true");
            stageExecution.setNeedsInputButNotReceivedYet(false);
            stageExecution.setDisableAssertionInspection(null);
            stageExecution.setStateExecutionData(stateExecutionData);
            stageExecution.setStateUuid(stateExecutionInstance.getUuid());
            stageExecution.setPipelineStageElementId(skipStateExecutionData.getPipelineStageElementId());
            stageExecution.setWaitingForInputs(false);
            stageExecution.setMessage(stateExecutionData.getErrorMsg());
            stageExecutionDataList.add(stageExecution);
          }
        });

    pipelineExecution.setPipelineStageExecutions(stageExecutionDataList);

    if (ExecutionStatus.isFinalStatus(workflowExecution.getStatus())) {
      pipelineExecution.setStatus(workflowExecution.getStatus());
    } else if (stageExecutionDataList.stream().anyMatch(
                   pipelineStageExecution -> pipelineStageExecution.getStatus() == WAITING)) {
      pipelineExecution.setStatus(WAITING);
    } else if (stageExecutionDataList.stream().anyMatch(pipelineStageExecution
                   -> pipelineStageExecution.getStatus() == PAUSED || pipelineStageExecution.getStatus() == PAUSING)
        && stageExecutionDataList.stream().noneMatch(se -> RUNNING == se.getStatus())) {
      pipelineExecution.setStatus(PAUSED);
    } else {
      pipelineExecution.setStatus(workflowExecution.getStatus());
    }

    workflowExecution.setStatus(pipelineExecution.getStatus());

    try {
      Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                           .filter(WorkflowExecutionKeys.appId, workflowExecution.getAppId())
                                           .filter(ID_KEY, workflowExecution.getUuid());
      UpdateOperations<WorkflowExecution> updateOps =
          wingsPersistence.createUpdateOperations(WorkflowExecution.class).set("pipelineExecution", pipelineExecution);
      wingsPersistence.update(query, updateOps);
      executorService.submit(() -> updatePipelineEstimates(workflowExecution));
    } catch (ConcurrentModificationException cex) {
      // do nothing as it gets refreshed in next fetch
      log.warn("Pipeline execution update failed ", cex); // TODO: add retry
    }
  }

  @VisibleForTesting
  void appendSkipCondition(PipelineStageElement pipelineStageElement, PipelineStageExecution stageExecution,
      String stateExecutionInstanceId) {
    if (isNotEmpty(pipelineStageElement.getDisableAssertion())) {
      stageExecution.setDisableAssertionInspection(stateInspectionService.get(stateExecutionInstanceId));
      stageExecution.setSkipCondition(pipelineStageElement.getDisableAssertion());
    }
  }

  private void setWaitingForInputFlag(
      StateExecutionInstance stateExecutionInstance, PipelineStageExecution stageExecution) {
    stageExecution.setWaitingForInputs(stateExecutionInstance.isWaitingForInputs());
    stageExecution.setNeedsInputButNotReceivedYet(
        stateExecutionInstance.isWaitingForInputs() && !stateExecutionInstance.isContinued());
    if (stateExecutionInstance.getStatus() != PAUSED) {
      stageExecution.setWaitingForInputs(false);
    }
  }

  @VisibleForTesting
  void handleEnvLoopStateExecutionData(String appId,
      ImmutableMap<String, StateExecutionInstance> stateExecutionInstanceMap,
      List<PipelineStageExecution> stageExecutionDataList, ForkStateExecutionData envStateExecutionData,
      PipelineStageElement pipelineStageElement, String stateExecutionInstanceId) {
    if (isNotEmpty(envStateExecutionData.getForkStateNames())) {
      for (String element : envStateExecutionData.getForkStateNames()) {
        StateExecutionInstance executionInstanceLooped = stateExecutionInstanceMap.get(element);
        if (executionInstanceLooped == null) {
          continue;
        }
        PipelineStageExecution stageExecution =
            PipelineStageExecution.builder()
                .parallelInfo(ParallelInfo.builder().groupIndex(pipelineStageElement.getParallelIndex()).build())
                .pipelineStageElementId(pipelineStageElement.getUuid())
                .stateUuid(executionInstanceLooped.getUuid())
                .stateType(executionInstanceLooped.getStateType())
                .stateName(executionInstanceLooped.getStateName())
                .status(executionInstanceLooped.getStatus())
                .startTs(executionInstanceLooped.getStartTs())
                .endTs(executionInstanceLooped.getEndTs())
                .looped(true)
                .build();

        StateExecutionData stateExecutionDataLooped = executionInstanceLooped.fetchStateExecutionData();
        if (stateExecutionDataLooped instanceof EnvStateExecutionData) {
          EnvStateExecutionData envStateExecutionDataLooped = (EnvStateExecutionData) stateExecutionDataLooped;
          if (envStateExecutionDataLooped.getWorkflowExecutionId() != null) {
            WorkflowExecution workflowExecution2 =
                getExecutionDetailsWithoutGraph(appId, envStateExecutionDataLooped.getWorkflowExecutionId());
            workflowExecution2.setStateMachine(null);

            stageExecution.setWorkflowExecutions(asList(workflowExecution2));
            stageExecution.setStatus(workflowExecution2.getStatus());
            stageExecution.setTriggeredBy(workflowExecution2.getTriggeredBy());
          }
          stageExecution.setMessage(envStateExecutionDataLooped.getErrorMsg());
        }
        if (stateExecutionDataLooped instanceof ForkStateExecutionData) {
          stageExecution.setMessage(stateExecutionDataLooped.getErrorMsg());
        }
        appendSkipCondition(pipelineStageElement, stageExecution, stateExecutionInstanceId);
        stageExecutionDataList.add(stageExecution);
      }
    }
  }

  private void updatePipelineEstimates(WorkflowExecution workflowExecution) {
    if (!ExecutionStatus.isFinalStatus(workflowExecution.getStatus())) {
      return;
    }

    PageRequest pageRequest = aPageRequest()
                                  .addFilter(WorkflowExecutionKeys.appId, EQ, workflowExecution.getAppId())
                                  .addFilter(WorkflowExecutionKeys.workflowId, EQ, workflowExecution.getWorkflowId())
                                  .addFilter(WorkflowExecutionKeys.status, EQ, SUCCESS)
                                  .addOrder(WorkflowExecutionKeys.endTs, OrderType.DESC)
                                  .withLimit("5")
                                  .build();

    pageRequest.addFieldsIncluded(WorkflowExecutionKeys.pipelineExecution);

    List<WorkflowExecution> workflowExecutions = wingsPersistence.queryAnalytics(WorkflowExecution.class, pageRequest);
    // Adding check for pse.getStateUuid() == null for backward compatibility. Can be removed later
    Map<String, LongSummaryStatistics> stateEstimatesSum =
        workflowExecutions.stream()
            .map(WorkflowExecution::getPipelineExecution)
            .flatMap(pe -> pe.getPipelineStageExecutions().stream())
            .collect(Collectors.groupingBy(pse
                -> (pse.getStateUuid() == null) ? pse.getStateName() : pse.getStateUuid(),
                Collectors.summarizingLong(this::getEstimate)));

    Map<String, Long> newEstimates = new HashMap<>();

    stateEstimatesSum.keySet().forEach(s -> {
      LongSummaryStatistics longSummaryStatistics = stateEstimatesSum.get(s);
      if (longSummaryStatistics.getCount() != 0) {
        newEstimates.put(s, longSummaryStatistics.getSum() / longSummaryStatistics.getCount());
      }
    });
    wingsPersistence.update(wingsPersistence.createQuery(Pipeline.class)
                                .filter(PipelineKeys.appId, workflowExecution.getAppId())
                                .filter(ID_KEY, workflowExecution.getWorkflowId()),
        wingsPersistence.createUpdateOperations(Pipeline.class).set("stateEtaMap", newEstimates));
  }

  private Long getEstimate(PipelineStageExecution pipelineStageExecution) {
    if (pipelineStageExecution.getEndTs() != null && pipelineStageExecution.getStartTs() != null
        && pipelineStageExecution.getEndTs() > pipelineStageExecution.getStartTs()) {
      return pipelineStageExecution.getEndTs() - pipelineStageExecution.getStartTs();
    }
    return null;
  }

  private ImmutableMap<String, StateExecutionInstance> getStateExecutionInstanceMap(
      WorkflowExecution workflowExecution) {
    List<StateExecutionInstance> stateExecutionInstances = getStateExecutionInstances(workflowExecution);
    return Maps.uniqueIndex(stateExecutionInstances, StateExecutionInstance::getDisplayName);
  }

  private List<StateExecutionInstance> getStateExecutionInstances(WorkflowExecution workflowExecution) {
    PageRequest<StateExecutionInstance> req =
        aPageRequest()
            .withLimit(UNLIMITED)
            .addFilter("appId", EQ, workflowExecution.getAppId())
            .addFilter("executionUuid", EQ, workflowExecution.getUuid())
            .addFilter(StateExecutionInstanceKeys.createdAt, GE, workflowExecution.getCreatedAt())
            .build();
    return wingsPersistence.query(StateExecutionInstance.class, req).getResponse();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public WorkflowExecution getExecutionDetails(
      String appId, String workflowExecutionId, boolean upToDate, boolean withFailureDetails) {
    WorkflowExecution workflowExecution = getExecutionDetailsWithoutGraph(appId, workflowExecutionId);

    if (workflowExecution.getWorkflowType() == PIPELINE) {
      populateNodeHierarchy(workflowExecution, false, true, upToDate);
    } else {
      populateNodeHierarchy(workflowExecution, true, false, upToDate);
    }

    if (withFailureDetails) {
      try {
        populateFailureDetails(workflowExecution);
      } catch (Exception e) {
        log.error("Failed to populate failure details.");
      }
    }
    return workflowExecution;
  }

  @Override
  public WorkflowExecution getExecutionDetailsWithoutGraph(String appId, String workflowExecutionId) {
    WorkflowExecution workflowExecution = getExecutionWithoutSummary(appId, workflowExecutionId);

    if (workflowExecution.getWorkflowType() == PIPELINE) {
      refreshPipelineExecution(workflowExecution);
    } else {
      refreshBreakdown(workflowExecution);
      refreshSummaries(workflowExecution);
      // CDS-36623
      refreshStatus(workflowExecution);
    }
    return workflowExecution;
  }

  @Override
  public WorkflowExecution getExecutionWithoutSummary(String appId, String workflowExecutionId) {
    // CRITICAL CODE PATH: NEED A DEEP EVALUATION BEFORE ADD PROJECTION FIELDS
    WorkflowExecution workflowExecution = getWorkflowExecution(appId, workflowExecutionId);
    notNullCheck("WorkflowExecution", workflowExecution, USER);

    ExecutionArgs executionArgs = workflowExecution.getExecutionArgs();
    if (executionArgs != null) {
      if (executionArgs.getServiceInstanceIdNames() != null) {
        executionArgs.setServiceInstances(
            serviceInstanceService.fetchServiceInstances(appId, executionArgs.getServiceInstanceIdNames().keySet()));
      }
      // TODO: This is being called multiple times during execution. We can optimize it by not fetching the artifacts
      // details again if fetched already
      if (executionArgs.getArtifactIdNames() != null) {
        String accountId = appService.getAccountIdByAppId(appId);
        executionArgs.setArtifacts(artifactService.listByIds(accountId, executionArgs.getArtifactIdNames().keySet()));
      } else {
        executionArgs.setArtifacts(emptyList());
      }
    }
    return workflowExecution;
  }

  @Override
  public void stateExecutionStatusUpdated(@NotNull StateStatusUpdateInfo updateInfo) {
    try (AutoLogContext ignore = new WorkflowExecutionLogContext(updateInfo.getWorkflowExecutionId(), OVERRIDE_NESTS);
         AutoLogContext ignore2 =
             new StateExecutionInstanceLogContext(updateInfo.getStateExecutionInstanceId(), OVERRIDE_NESTS)) {
      WorkflowStatusPropagator workflowStatusPropagator =
          workflowStatusPropagatorFactory.obtainHandler(updateInfo.getStatus());
      workflowStatusPropagator.handleStatusUpdate(updateInfo);
    } catch (Exception e) {
      // Ignore Exception for Now
      log.error("Status Update Failed from propagator Hooks ExecutionId: {}, Status : {}",
          updateInfo.getWorkflowExecutionId(), updateInfo.getStatus());
    }
  }

  private WorkflowTree calculateTree(String appId, String workflowExecutionId, String accountId) {
    Map<String, StateExecutionInstance> allInstancesIdMap =
        stateExecutionService.executionStatesMap(appId, workflowExecutionId);
    Long lastUpdate = allInstancesIdMap.values()
                          .stream()
                          .map(StateExecutionInstance::getLastUpdatedAt)
                          .max(Long::compare)
                          .orElseGet(() -> Long.valueOf(0));

    List<String> params = getParamsForTree();
    WorkflowTree tree =
        mongoStore.get(GraphRenderer.algorithmId, WorkflowTree.STRUCTURE_HASH, workflowExecutionId, params);
    if (tree != null && tree.getContextOrder() >= lastUpdate) {
      return tree;
    }

    WorkflowTreeBuilder workflowTreeBuilder =
        WorkflowTree.builder().key(workflowExecutionId).params(params).contextOrder(lastUpdate).wasInvalidated(false);
    if (allInstancesIdMap.values().stream().anyMatch(i -> i.getStatus() == ExecutionStatus.WAITING)) {
      workflowTreeBuilder.overrideStatus(ExecutionStatus.WAITING);
    }

    workflowTreeBuilder.graph(graphRenderer.generateHierarchyNode(allInstancesIdMap));

    WorkflowTree cacheTree = workflowTreeBuilder.build();

    executorService.submit(() -> { mongoStore.upsert(cacheTree, ofDays(30), false, accountId); });
    return cacheTree;
  }

  private List<String> getParamsForTree() {
    return Collections.singletonList(String.valueOf(GraphRenderer.AGGREGATION_LIMIT));
  }

  @Override
  public WorkflowExecution getWorkflowExecution(String appId, String workflowExecutionId, String... fields) {
    final Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                               .filter(WorkflowExecutionKeys.appId, appId)
                                               .filter(WorkflowExecutionKeys.uuid, workflowExecutionId);
    prepareWorkflowExecutionProjectionFields(query, fields);
    WorkflowExecution workflowExecution = query.get();
    if (workflowExecution != null && workflowExecution.getArtifacts() != null) {
      for (Artifact artifact : workflowExecution.getArtifacts()) {
        ArtifactStream artifactStream = wingsPersistence.get(ArtifactStream.class, artifact.getArtifactStreamId());
        artifact.setArtifactStreamName(
            artifactStream == null ? artifact.getArtifactSourceName() : artifactStream.getName());
      }
    }
    return workflowExecution;
  }

  private void prepareWorkflowExecutionProjectionFields(Query<WorkflowExecution> query, String[] fields) {
    if (featureFlagService.isNotGlobalEnabled(FeatureName.SPG_CG_WFE_PROJECTION_FIELDS)) {
      return;
    }
    if (isEmpty(fields)) {
      return;
    }
    // TURN ARRAY INTO A SET TO REMOVE DUPLICATES IF EXIST
    // ADD COMMON FIELDS HELPFUL ALONG THE EXECUTION FLOW
    Set<String> uniqueFields = new HashSet<>(Arrays.asList(fields));
    uniqueFields.add(WorkflowExecutionKeys.uuid);
    uniqueFields.add(WorkflowExecutionKeys.appId);
    uniqueFields.add(WorkflowExecutionKeys.accountId);

    for (String field : uniqueFields) {
      query.project(field, true);
    }
  }

  @Override
  public String getPipelineExecutionId(String appId, String workflowExecutionId) {
    return wingsPersistence.createQuery(WorkflowExecution.class)
        .filter("_id", workflowExecutionId)
        .project(WorkflowExecutionKeys.pipelineExecutionId, true)
        .get()
        .getPipelineExecutionId();
  }

  /**
   * Refresh workflow execution status looking inside deployment execution flow hierarchy. It's executed when the
   * incoming workflow execution is NOT at final state and only if the FF {@code WORKFLOW_EXECUTION_REFRESH_STATUS} is
   * enabled for the account.
   */
  @Override
  public void refreshStatus(WorkflowExecution workflowExecution) {
    // CDS-36623
    if (ExecutionStatus.isNotFinalStatus(workflowExecution.getStatus())) {
      if (featureFlagService.isEnabled(WORKFLOW_EXECUTION_REFRESH_STATUS, workflowExecution.getAccountId())) {
        populateNodeHierarchy(workflowExecution, false, true, true);
      }
    }
  }

  private void populateNodeHierarchy(
      WorkflowExecution workflowExecution, boolean includeGraph, boolean includeStatus, boolean upToDate) {
    if (includeGraph) {
      includeStatus = true;
    }

    if (!includeStatus && !includeGraph) {
      return;
    }

    try (AutoLogContext ignore = new WorkflowExecutionLogContext(workflowExecution.getUuid(), OVERRIDE_NESTS)) {
      WorkflowTree tree = null;
      List<String> params = null;
      params = getParamsForTree();
      if (!upToDate) {
        tree = mongoStore.<WorkflowTree>get(
            GraphRenderer.algorithmId, WorkflowTree.STRUCTURE_HASH, workflowExecution.getUuid(), params);
      }

      if (upToDate || tree == null || tree.isWasInvalidated()
          || tree.getLastUpdatedAt() < (System.currentTimeMillis() - 5000)) {
        tree =
            calculateTree(workflowExecution.getAppId(), workflowExecution.getUuid(), workflowExecution.getAccountId());
      }

      if (includeStatus && tree.getOverrideStatus() != null) {
        if (ExecutionStatus.isFinalStatus(workflowExecution.getStatus())
            && ExecutionStatus.isFinalStatus(tree.getOverrideStatus())) {
          log.error("Workflow Execution is in final status but override status is not Override Status: {}",
              tree.getOverrideStatus());
        }
        workflowExecution.setStatus(tree.getOverrideStatus());
      }
      if (includeGraph) {
        workflowExecution.setExecutionNode(tree.getGraph());
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public WorkflowExecution triggerPipelineExecution(
      String appId, String pipelineId, ExecutionArgs executionArgs, Trigger trigger) {
    return triggerPipelineExecution(appId, pipelineId, executionArgs, null, trigger);
  }

  private void constructBarriers(Pipeline pipeline, String pipelineExecutionId) {
    // Initializing the list workarounds an issue with having the first stage having isParallel set.
    List<OrchestrationWorkflowInfo> orchestrationWorkflows = new ArrayList<>();
    int parallelIndex = 0;
    for (PipelineStage stage : pipeline.getPipelineStages()) {
      if (!stage.isParallel()) {
        if (!isEmpty(orchestrationWorkflows)) {
          barrierService
              .obtainInstances(pipeline.getAppId(), orchestrationWorkflows, pipelineExecutionId, parallelIndex)
              .forEach(barrier -> barrierService.save(barrier));
        }
        orchestrationWorkflows = new ArrayList<>();
      }

      // this array is legacy, we always have just one item
      PipelineStageElement element = stage.getPipelineStageElements().get(0);
      parallelIndex = element.getParallelIndex();

      if (element.checkDisableAssertion()) {
        continue;
      }

      if (ENV_STATE.name().equals(element.getType())) {
        Workflow workflow =
            workflowService.readWorkflow(pipeline.getAppId(), (String) element.getProperties().get("workflowId"));

        if (workflow.getOrchestrationWorkflow() != null) {
          OrchestrationWorkflowInfoBuilder builder = OrchestrationWorkflowInfo.builder()
                                                         .workflowId(workflow.getUuid())
                                                         .pipelineStageId(element.getUuid())
                                                         .orchestrationWorkflow(workflow.getOrchestrationWorkflow());
          orchestrationWorkflows.add(builder.build());
        }
      } else if (ENV_LOOP_STATE.name().equals(element.getType())) {
        Workflow workflow =
            workflowService.readWorkflow(pipeline.getAppId(), (String) element.getProperties().get("workflowId"));

        if (workflow.getOrchestrationWorkflow() != null) {
          OrchestrationWorkflowInfoBuilder builder = OrchestrationWorkflowInfo.builder()
                                                         .workflowId(workflow.getUuid())
                                                         .pipelineStageId(element.getUuid())
                                                         .orchestrationWorkflow(workflow.getOrchestrationWorkflow())
                                                         .isLooped(true);
          List<String> values = (List<String>) element.getProperties().get("loopedValues");
          values.stream().map(value -> builder.build()).forEach(orchestrationWorkflows::add);
        }
      }
    }

    if (!isEmpty(orchestrationWorkflows)) {
      barrierService.obtainInstances(pipeline.getAppId(), orchestrationWorkflows, pipelineExecutionId, parallelIndex)
          .forEach(barrier -> barrierService.save(barrier));
    }
  }

  /**
   * Trigger pipeline execution workflow execution.
   *
   * @param appId                   the app id
   * @param pipelineId              the pipeline id
   * @param executionArgs           the execution args
   * @param workflowExecutionUpdate the workflow execution update  @return the workflow execution
   * @return the workflow execution
   */
  @VisibleForTesting
  WorkflowExecution triggerPipelineExecution(String appId, String pipelineId, ExecutionArgs executionArgs,
      WorkflowExecutionUpdate workflowExecutionUpdate, Trigger trigger) {
    Pipeline pipeline = pipelineService.readPipelineResolvedVariablesLoopedInfo(
        appId, pipelineId, executionArgs.getWorkflowVariables());
    return triggerPipelineExecution(appId, pipeline, executionArgs, workflowExecutionUpdate, trigger, null);
  }

  private WorkflowExecution triggerPipelineExecution(String appId, Pipeline pipeline, ExecutionArgs executionArgs,
      WorkflowExecutionUpdate workflowExecutionUpdate, Trigger trigger, String pipelineResumeId) {
    if (pipeline == null) {
      throw new WingsException(ErrorCode.NON_EXISTING_PIPELINE);
    }

    String pipelineId = pipeline.getUuid();
    String accountId = appService.getAccountIdByAppId(appId);

    User user = UserThreadLocal.get();
    boolean shouldAuthorizeExecution = trigger == null && user != null;
    if (shouldAuthorizeExecution) {
      deploymentAuthHandler.authorizePipelineExecution(appId, pipelineId);
      if (featureFlagService.isEnabled(PIPELINE_PER_ENV_DEPLOYMENT_PERMISSION, accountId)) {
        deploymentAuthHandler.authorizeExecutableDeployableInEnv(
            new HashSet<>(pipeline.getEnvIds()), appId, pipelineId, ExecutableElementsFilter.FilterType.PIPELINE);
      }
      if (isNotEmpty(pipeline.getEnvIds())) {
        pipeline.getEnvIds().forEach(s -> authService.checkIfUserAllowedToDeployPipelineToEnv(appId, s));
      }
    }

    if (featureFlagService.isEnabled(WEBHOOK_TRIGGER_AUTHORIZATION, accountId)) {
      if (trigger != null && user != null
          && trigger.getCondition().getConditionType() == TriggerConditionType.WEBHOOK) {
        deploymentAuthHandler.authorizePipelineExecution(appId, pipelineId);
        if (isNotEmpty(pipeline.getEnvIds())) {
          pipeline.getEnvIds().forEach(s -> authService.checkIfUserAllowedToDeployPipelineToEnv(appId, s));
        }
      }
    }

    checkPreDeploymentConditions(accountId, appId);

    PreDeploymentChecker deploymentFreezeChecker = new DeploymentFreezeChecker(governanceConfigService,
        new DeploymentCtx(appId,
            featureFlagService.isEnabled(NEW_DEPLOYMENT_FREEZE, accountId)
                ? PipelineServiceHelper.getEnvironmentIdsForParallelIndex(pipeline, 1)
                : pipeline.getEnvIds(),
            getPipelineServiceIds(pipeline)),
        environmentService, featureFlagService);
    /*
    Following scenarios cannot override freeze
    1. triggers without API key
    2. triggers with API key which don't have override freeze permission
    3. users without override freeze permission
     */
    boolean canOverrideFreeze = user != null && checkIfOverrideFreeze();
    if (!canOverrideFreeze && !featureFlagService.isEnabled(SPG_SAVE_REJECTED_BY_FREEZE_WINDOWS, accountId)) {
      deploymentFreezeChecker.check(accountId);
    }

    if (isEmpty(pipeline.getPipelineStages())) {
      throw new WingsException("You can not deploy an empty pipeline.", WingsException.USER);
    }
    preDeploymentChecks.checkIfPipelineUsingRestrictedFeatures(pipeline);

    List<WorkflowExecution> runningWorkflowExecutions =
        getRunningWorkflowExecutions(WorkflowType.PIPELINE, appId, pipelineId);
    if (runningWorkflowExecutions != null) {
      for (WorkflowExecution workflowExecution : runningWorkflowExecutions) {
        if (workflowExecution.getStatus() == NEW) {
          throw new WingsException(ErrorCode.PIPELINE_ALREADY_TRIGGERED).addParam("pipelineName", pipeline.getName());
        }
        // TODO: if (workflowExecution.getStatus() == RUNNING)
        // Analyze if pipeline is in initial stage
      }
    }

    if (isEmpty(pipeline.getAccountId())) {
      pipeline.setAccountId(accountId);
    }
    StateMachine stateMachine = new StateMachine(pipeline, workflowService.stencilMap(pipeline.getAppId()));
    stateMachine.setOrchestrationWorkflow(null);

    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .uuid(generateUuid())
                                              .status(NEW)
                                              .appId(appId)
                                              .workflowId(pipelineId)
                                              .workflowType(WorkflowType.PIPELINE)
                                              .stateMachine(stateMachine)
                                              .name(pipeline.getName())
                                              .canOverrideFreeze(canOverrideFreeze)
                                              .build();

    constructBarriers(pipeline, workflowExecution.getUuid());

    // Do not remove this. Morphia referencing it by id and one object getting
    // overridden by the other
    pipeline.setUuid(generateUuid() + "_embedded");

    PipelineExecution pipelineExecution =
        aPipelineExecution().withPipelineId(pipelineId).withPipeline(pipeline).build();
    workflowExecution.setPipelineExecution(pipelineExecution);
    workflowExecution.setPipelineSummary(
        PipelineSummary.builder().pipelineId(pipelineId).pipelineName(pipeline.getName()).build());

    WorkflowStandardParams stdParams = new WorkflowStandardParams();
    stdParams.setAppId(appId);
    if (isNotEmpty(executionArgs.getArtifacts())) {
      stdParams.setArtifactIds(executionArgs.getArtifacts().stream().map(Artifact::getUuid).collect(toList()));
    }
    if (isNotEmpty(executionArgs.getHelmCharts())) {
      stdParams.setHelmChartIds(executionArgs.getHelmCharts().stream().map(HelmChart::getUuid).collect(toList()));
    }
    if (isNotEmpty(executionArgs.getWorkflowVariables())) {
      stdParams.setWorkflowVariables(executionArgs.getWorkflowVariables());
    }
    if (containArtifactInputs(executionArgs, accountId)) {
      List<ArtifactInput> artifactInputs = executionArgs.getArtifactVariables()
                                               .stream()
                                               .filter(artifactVariable -> artifactVariable.getArtifactInput() != null)
                                               .map(ArtifactVariable::getArtifactInput)
                                               .collect(toList());
      if (isNotEmpty(artifactInputs)) {
        stdParams.setArtifactInputs(artifactInputs);
      }
    }

    if (containManifestInputs(executionArgs, accountId)) {
      List<ManifestInput> manifestInputs =
          executionArgs.getManifestVariables()
              .stream()
              .filter(manifestVariable -> HelmChartInputType.VERSION.equals(manifestVariable.getInputType()))
              .map(ManifestVariable::mapManifestVariableToManifestInput)
              .collect(toList());
      if (isNotEmpty(manifestInputs)) {
        stdParams.setManifestInputs(manifestInputs);
      }
    }
    // Setting  exclude hosts with same artifact
    stdParams.setExcludeHostsWithSameArtifact(executionArgs.isExcludeHostsWithSameArtifact());
    stdParams.setNotifyTriggeredUserOnly(executionArgs.isNotifyTriggeredUserOnly());
    stdParams.setContinueWithDefaultValues(executionArgs.isContinueWithDefaultValues());

    if (pipelineResumeId != null) {
      stdParams.setWorkflowElement(WorkflowElement.builder().pipelineResumeUuid(pipelineResumeId).build());
    }

    if (user != null) {
      stdParams.setCurrentUser(
          EmbeddedUser.builder().uuid(user.getUuid()).email(user.getEmail()).name(user.getName()).build());
    }
    workflowExecution.setExecutionArgs(executionArgs);

    if (pipeline.getServices() != null) {
      List<ElementExecutionSummary> serviceExecutionSummaries = new ArrayList<>();
      pipeline.getServices().forEach(service -> {
        serviceExecutionSummaries.add(
            anElementExecutionSummary()
                .withContextElement(ServiceElement.builder().uuid(service.getUuid()).name(service.getName()).build())
                .build());
      });
      workflowExecution.setServiceExecutionSummaries(serviceExecutionSummaries);
      workflowExecution.setServiceIds(getPipelineServiceIds(pipeline));
    }
    workflowExecution.setEnvIds(pipeline.getEnvIds());
    workflowExecution.setWorkflowIds(pipeline.getWorkflowIds());
    workflowExecution.setInfraMappingIds(pipeline.getInfraMappingIds());
    workflowExecution.setCloudProviderIds(
        infrastructureMappingService.fetchCloudProviderIds(appId, workflowExecution.getInfraMappingIds()));
    workflowExecution.setInfraDefinitionIds(pipeline.getInfraDefinitionIds());

    return triggerExecution(workflowExecution, stateMachine, workflowExecutionUpdate, stdParams, trigger, pipeline,
        null, canOverrideFreeze, deploymentFreezeChecker);
  }

  private List<String> getPipelineServiceIds(Pipeline pipeline) {
    if (isEmpty(pipeline.getServices())) {
      return emptyList();
    }
    return pipeline.getServices().stream().map(Service::getUuid).collect(toList());
  }

  private boolean checkIfOverrideFreeze() {
    boolean canOverrideFreeze = true;
    try {
      deploymentAuthHandler.authorizeDeploymentDuringFreeze();
    } catch (AccessDeniedException | InvalidRequestException e) {
      log.info("User can not override deployment freezes. Performing deployment freeze checks...");
      canOverrideFreeze = false;
    }
    return canOverrideFreeze;
  }

  // validations to check is a deployment should be allowed or not
  private void checkPreDeploymentConditions(String accountId, String appId) {
    accountExpirationChecker.check(accountId);
    checkInstanceUsageLimit(accountId, appId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public WorkflowExecution triggerOrchestrationExecution(
      String appId, String envId, String workflowId, ExecutionArgs executionArgs, Trigger trigger) {
    return triggerOrchestrationWorkflowExecution(appId, envId, workflowId, null, executionArgs, null, trigger);
  }

  @Override
  public int getActiveServiceCount(String accountId) {
    long sixtyDays = currentTimeMillis() - SIXTY_DAYS_IN_MILLIS;
    Query query = wingsPersistence.createAnalyticsQuery(WorkflowExecution.class, excludeAuthority);
    query.filter(WorkflowExecutionKeys.accountId, accountId);
    query.field(WorkflowExecutionKeys.startTs).greaterThanOrEq(sixtyDays);
    query.project("serviceIds", true);
    FindOptions findOptions = new FindOptions();
    findOptions.hint(BasicDBUtils.getIndexObject(WorkflowExecution.mongoIndexes(), ACCOUNTID_STARTTS_SERVICEIDS));
    findOptions.readPreference(ReadPreference.secondaryPreferred());
    List<WorkflowExecution> workflowExecutions = query.asList(findOptions);
    Set<String> flattenedSvcSet = new HashSet<>();
    if (isNotEmpty(workflowExecutions)) {
      workflowExecutions.forEach(workflowExecution -> {
        List<String> serviceIdList = workflowExecution.getServiceIds();
        if (isNotEmpty(serviceIdList)) {
          serviceIdList.forEach(serviceId -> flattenedSvcSet.add(serviceId));
        }
      });
    }
    return flattenedSvcSet.size();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public WorkflowExecution triggerOrchestrationExecution(String appId, String envId, String workflowId,
      String pipelineExecutionId, ExecutionArgs executionArgs, Trigger trigger) {
    return triggerOrchestrationWorkflowExecution(
        appId, envId, workflowId, pipelineExecutionId, executionArgs, null, trigger);
  }

  @Override
  public WorkflowExecution triggerOrchestrationWorkflowExecution(String appId, String envId, String workflowId,
      String pipelineExecutionId, @NotNull ExecutionArgs executionArgs, WorkflowExecutionUpdate workflowExecutionUpdate,
      Trigger trigger) {
    String accountId = appService.getAccountIdByAppId(appId);

    log.info("Execution Triggered. Type: {}", executionArgs.getWorkflowType());

    // TODO - validate list of artifact Ids if it's matching for all the services involved in this orchestration

    Workflow workflow = workflowExecutionServiceHelper.obtainWorkflow(appId, workflowId);
    String resolveEnvId = workflowService.resolveEnvironmentId(workflow, executionArgs.getWorkflowVariables());
    List<String> resolvedServiceIds =
        workflowService.getResolvedServiceIds(workflow, executionArgs.getWorkflowVariables());
    envId = resolveEnvId != null ? resolveEnvId : envId;
    if (workflow.getOrchestrationWorkflow() != null
        && !OrchestrationWorkflowType.BUILD.equals(workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType())
        && isEmpty(envId)) {
      throw new InvalidRequestException("Environment is not provided in the workflow", USER);
    }
    User user = UserThreadLocal.get();

    // The workflow execution is direct workWorkflowExecutionServiceTestflow execution and not in Pipeline or trigger.
    boolean isDirectExecution = trigger == null && user != null && isEmpty(pipelineExecutionId);
    if (isDirectExecution) {
      deploymentAuthHandler.authorizeWorkflowExecution(appId, workflowId);
      //      if (featureFlagService.isEnabled(PIPELINE_PER_ENV_DEPLOYMENT_PERMISSION, accountId)) {
      // enable when it is ready
      //        deploymentAuthHandler.authorizeExecutableDeployableInEnv(
      //            Collections.singleton(envId), appId, workflowId, ExecutableElementsFilter.FilterType.WORKFLOW);
      //      }
      authService.checkIfUserAllowedToDeployWorkflowToEnv(appId, envId);
    }

    if (featureFlagService.isEnabled(WEBHOOK_TRIGGER_AUTHORIZATION, accountId)) {
      if (trigger != null && user != null && trigger.getCondition().getConditionType() == TriggerConditionType.WEBHOOK
          && isEmpty(pipelineExecutionId)) {
        deploymentAuthHandler.authorizeWorkflowExecution(appId, workflowId);
        authService.checkIfUserAllowedToDeployWorkflowToEnv(appId, envId);
      }
    }

    // Doing this check here so that workflow is already fetched from databae.
    preDeploymentChecks.checkIfWorkflowUsingRestrictedFeatures(workflow);

    PreDeploymentChecker deploymentFreezeChecker = new DeploymentFreezeChecker(governanceConfigService,
        new DeploymentCtx(
            appId, isNotEmpty(envId) ? Collections.singletonList(envId) : emptyList(), resolvedServiceIds),
        environmentService, featureFlagService);
    // Check deployment freeze conditions for both direct workflow or pipeline executions
    // Freeze can be override only for manual deployments, trigger based deployments are rejected when freeze active
    boolean canOverrideFreeze = false;
    if (executionArgs.isContinueRunningPipelinesDuringMigration()) {
      canOverrideFreeze = true;
    } else {
      if (featureFlagService.isEnabled(NEW_DEPLOYMENT_FREEZE, accountId)) {
        if (isNotEmpty(pipelineExecutionId)) {
          WorkflowExecution pipelineExecution = wingsPersistence.createQuery(WorkflowExecution.class)
                                                    .project(WorkflowExecutionKeys.canOverrideFreeze, true)
                                                    .filter(WorkflowExecutionKeys.uuid, pipelineExecutionId)
                                                    .filter(WorkflowExecutionKeys.accountId, accountId)
                                                    .get();
          canOverrideFreeze = pipelineExecution.isCanOverrideFreeze();
        } else {
          canOverrideFreeze = user != null && checkIfOverrideFreeze();
        }
      }
    }
    if (!canOverrideFreeze && !featureFlagService.isEnabled(SPG_SAVE_REJECTED_BY_FREEZE_WINDOWS, accountId)) {
      deploymentFreezeChecker.check(accountId);
    }

    checkPreDeploymentConditions(accountId, appId);

    workflow.setOrchestrationWorkflow(
        workflowConcurrencyHelper.enhanceWithConcurrencySteps(workflow, executionArgs.getWorkflowVariables()));

    List<ArtifactInput> artifactInputs = null;
    List<Service> services = workflowService.getResolvedServices(workflow, executionArgs.getWorkflowVariables());
    if (containArtifactInputs(executionArgs, accountId)) {
      artifactInputs = getArtifactInputsForWorkflow(executionArgs, services);
    }

    List<ManifestInput> manifestInputs = null;
    if (containManifestInputs(executionArgs, accountId)) {
      manifestInputs = getManifestInputsForWorkflow(executionArgs, services, appId);
    }

    if (isNotEmpty(artifactInputs) || isNotEmpty(manifestInputs)) {
      workflow.setOrchestrationWorkflow(
          updateWorkflowWithArtifactCollectionSteps(workflow, artifactInputs, manifestInputs));
    }

    if (isEmpty(workflow.getAccountId())) {
      workflow.setAccountId(accountId);
    }
    StateMachine stateMachine = new StateMachine(workflow, workflow.getDefaultVersion(),
        ((CustomOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).getGraph(),
        workflowService.stencilMap(appId), false);

    // TODO: this is workaround for a side effect in the state machine generation that mangles with the original
    //       workflow object.
    workflow = workflowService.readWorkflow(appId, workflowId);

    stateMachine.setOrchestrationWorkflow(null);

    // pipelineExecutionId
    WorkflowExecution workflowExecution = workflowExecutionServiceHelper.obtainExecution(
        workflow, stateMachine, resolveEnvId, pipelineExecutionId, executionArgs);

    validateExecutionArgsHosts(executionArgs.getHosts(), workflowExecution, workflow);
    validateWorkflowTypeAndService(workflow, executionArgs);

    WorkflowStandardParams stdParams =
        workflowExecutionServiceHelper.obtainWorkflowStandardParams(appId, envId, executionArgs, workflow);

    if (isNotEmpty(artifactInputs)) {
      stdParams.setArtifactInputs(artifactInputs);
    }

    if (isNotEmpty(manifestInputs)) {
      stdParams.setManifestInputs(manifestInputs);
    }

    return triggerExecution(workflowExecution, stateMachine, new CanaryWorkflowExecutionAdvisor(),
        workflowExecutionUpdate, stdParams, trigger, null, workflow, canOverrideFreeze, deploymentFreezeChecker);
  }

  @VisibleForTesting
  OrchestrationWorkflow updateWorkflowWithArtifactCollectionSteps(
      Workflow workflow, List<ArtifactInput> artifactInputs, List<ManifestInput> manifestInputs) {
    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();

    PhaseStep preDeploymentSteps = canaryOrchestrationWorkflow.getPreDeploymentSteps();
    if (preDeploymentSteps == null) {
      preDeploymentSteps = new PhaseStep();
      canaryOrchestrationWorkflow.setPreDeploymentSteps(preDeploymentSteps);
    }
    if (preDeploymentSteps.getSteps() == null) {
      preDeploymentSteps.setSteps(new ArrayList<>());
    }

    preDeploymentSteps.getSteps().add(0, getArtifactCollectionStep(artifactInputs, manifestInputs));
    canaryOrchestrationWorkflow.setGraph(canaryOrchestrationWorkflow.generateGraph());
    return canaryOrchestrationWorkflow;
  }

  private GraphNode getArtifactCollectionStep(List<ArtifactInput> artifactInputs, List<ManifestInput> manifestInputs) {
    if (manifestInputs == null) {
      manifestInputs = new ArrayList<>();
    }
    if (artifactInputs == null) {
      artifactInputs = new ArrayList<>();
    }

    return GraphNode.builder()
        .type(ARTIFACT_COLLECT_LOOP_STATE.getType())
        .name("Artifact/Manifest Collection")
        .properties(ImmutableMap.<String, Object>builder()
                        .put(ArtifactCollectLoopStateKeys.artifactInputList, artifactInputs)
                        .put(ArtifactCollectLoopStateKeys.manifestInputList, manifestInputs)
                        .build())
        .build();
  }

  private List<ArtifactInput> getArtifactInputsForWorkflow(
      @NotNull ExecutionArgs executionArgs, List<Service> services) {
    List<ArtifactInput> artifactInputs = new ArrayList<>();
    if (isNotEmpty(services)) {
      Set<String> serviceIds = services.stream().map(Service::getUuid).collect(toSet());
      Set<String> artifactStreamIds = new HashSet<>();
      serviceIds.forEach(serviceId -> {
        List<String> ids = artifactStreamServiceBindingService.listArtifactStreamIds(serviceId);
        if (isNotEmpty(ids)) {
          artifactStreamIds.addAll(ids);
        }
      });
      artifactInputs =
          executionArgs.getArtifactVariables()
              .stream()
              .filter(artifactVariable
                  -> artifactVariable.getArtifactInput() != null
                      && artifactStreamIds.contains(artifactVariable.getArtifactInput().getArtifactStreamId()))
              .map(ArtifactVariable::getArtifactInput)
              .collect(Collectors.toList());
    }
    return artifactInputs;
  }

  private List<ManifestInput> getManifestInputsForWorkflow(
      @NotNull ExecutionArgs executionArgs, List<Service> services, String appId) {
    List<ManifestInput> manifestInputs = new ArrayList<>();
    if (isNotEmpty(services)) {
      Set<String> serviceIds = services.stream().map(Service::getUuid).collect(toSet());
      Set<String> appManifestIds = new HashSet<>();
      serviceIds.forEach(serviceId -> {
        List<ApplicationManifest> appManifests =
            applicationManifestService.getManifestsByServiceId(appId, serviceId, AppManifestKind.K8S_MANIFEST);
        if (isNotEmpty(appManifests)) {
          appManifestIds.addAll(appManifests.stream().map(ApplicationManifest::getUuid).collect(Collectors.toSet()));
        }
      });
      manifestInputs = executionArgs.getManifestVariables()
                           .stream()
                           .filter(manifestVariable
                               -> HelmChartInputType.VERSION.equals(manifestVariable.getInputType())
                                   && appManifestIds.contains(manifestVariable.getAppManifestId()))
                           .map(ManifestVariable::mapManifestVariableToManifestInput)
                           .filter(Objects::nonNull)
                           .collect(Collectors.toList());
    }
    return manifestInputs;
  }

  private boolean containArtifactInputs(ExecutionArgs executionArgs, String accountId) {
    return featureFlagService.isEnabled(ARTIFACT_COLLECTION_CONFIGURABLE, accountId)
        && executionArgs.getArtifactVariables() != null
        && executionArgs.getArtifactVariables().stream().anyMatch(
            artifactVariable -> artifactVariable.getArtifactInput() != null);
  }

  private boolean containManifestInputs(ExecutionArgs executionArgs, String accountId) {
    return featureFlagService.isEnabled(ADD_MANIFEST_COLLECTION_STEP, accountId)
        && executionArgs.getManifestVariables() != null
        && executionArgs.getManifestVariables().stream().anyMatch(
            manifestVariable -> HelmChartInputType.VERSION.equals(manifestVariable.getInputType()));
  }

  private List<String> getWorkflowServiceIds(Workflow workflow) {
    if (isEmpty(workflow.getServices())) {
      return emptyList();
    }
    return workflow.getServices().stream().map(Service::getUuid).collect(toList());
  }

  /*
  Rolling type workflow does not support k8s-v1 type of service
   */
  void validateWorkflowTypeAndService(Workflow workflow, ExecutionArgs executionArgs) {
    List<Service> services = workflowService.getResolvedServices(workflow, executionArgs.getWorkflowVariables());
    if (workflow.getOrchestrationWorkflow() != null
        && OrchestrationWorkflowType.ROLLING == workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType()) {
      for (Service service : emptyIfNull(services)) {
        if (service.getDeploymentType() == DeploymentType.KUBERNETES && !service.isK8sV2()) {
          throw new InvalidRequestException(format("Rolling Type Workflow does not suport k8s-v1 "
                  + "service [%s]",
              service.getName()));
        }
      }
    }
  }

  void validateExecutionArgsHosts(List<String> hosts, WorkflowExecution workflowExecution, Workflow workflow) {
    if (isEmpty(hosts)) {
      return;
    }

    List<String> serviceIds = workflowExecution.getServiceIds();
    if (isEmpty(serviceIds) || serviceIds.size() > 1) {
      throw new InvalidRequestException("Execution Hosts only supported for single service workflow", USER);
    }

    List<DeploymentType> deploymentTypes = workflow.getDeploymentTypes();
    if (isEmpty(deploymentTypes) || deploymentTypes.size() > 1) {
      throw new InvalidRequestException("Execution Hosts only supported for single deployment type", USER);
    }
    DeploymentType deploymentType = deploymentTypes.get(0);
    if (deploymentType != DeploymentType.SSH && deploymentType != DeploymentType.WINRM) {
      throw new InvalidRequestException("Execution Hosts only supported for SSH and WinRM deployment type", USER);
    }
  }

  private WorkflowExecution triggerExecution(WorkflowExecution workflowExecution, StateMachine stateMachine,
      WorkflowExecutionUpdate workflowExecutionUpdate, WorkflowStandardParams stdParams, Trigger trigger,
      Pipeline pipeline, Workflow workflow, Boolean canOverrideFreeze, PreDeploymentChecker deploymentFreezeChecker,
      ContextElement... contextElements) {
    return triggerExecution(workflowExecution, stateMachine, new PipelineStageExecutionAdvisor(),
        workflowExecutionUpdate, stdParams, trigger, pipeline, workflow, canOverrideFreeze, deploymentFreezeChecker,
        contextElements);
  }

  private WorkflowExecution triggerExecution(WorkflowExecution workflowExecution, StateMachine stateMachine,
      ExecutionEventAdvisor workflowExecutionAdvisor, WorkflowExecutionUpdate workflowExecutionUpdate,
      WorkflowStandardParams stdParams, Trigger trigger, Pipeline pipeline, Workflow workflow,
      Boolean canOverrideFreeze, PreDeploymentChecker deploymentFreezeChecker, ContextElement... contextElements) {
    Set<String> keywords = new HashSet<>();
    keywords.add(workflowExecution.normalizedName());
    if (workflowExecution.getWorkflowType() != null) {
      keywords.add(workflowExecution.getWorkflowType().name());
    }
    if (workflowExecution.getOrchestrationType() != null) {
      keywords.add(workflowExecution.getWorkflowType().name());
    }

    ExecutionArgs executionArgs = workflowExecution.getExecutionArgs();

    Application app = appService.get(workflowExecution.getAppId());

    populateArtifactsAndServices(workflowExecution, stdParams, keywords, executionArgs, app.getAccountId());

    List<InfrastructureMapping> infraMappingList =
        workflowExecutionServiceHelper.getInfraMappings(workflow, executionArgs.getWorkflowVariables());
    List<String> infraMappingIds = infraMappingList == null
        ? emptyList()
        : infraMappingList.stream().map(InfrastructureMapping::getUuid).collect(toList());
    boolean isInfraBasedArtifact = shouldUseInfraBasedRollbackArtifact(app.getAccountId(), infraMappingList);

    populateRollbackArtifacts(workflowExecution, infraMappingIds, stdParams, isInfraBasedArtifact);

    populatePipelineSummary(workflowExecution, keywords, executionArgs);

    workflowExecution.setAppName(app.getName());
    keywords.add(workflowExecution.getAppName());

    refreshEnvSummary(workflowExecution, keywords);

    populateCurrentUser(workflowExecution, stdParams, trigger, keywords);

    populateServiceInstances(workflowExecution, keywords, executionArgs);

    workflowExecution.setErrorStrategy(executionArgs.getErrorStrategy());

    workflowExecution.setKeywords(trimmedLowercaseSet(keywords));
    workflowExecution.setStatus(QUEUED);

    EntityVersion entityVersion = entityVersionService.newEntityVersion(workflowExecution.getAppId(), DEPLOYMENT,
        workflowExecution.getWorkflowId(), workflowExecution.displayName(), ChangeType.CREATED);

    workflowExecution.setReleaseNo(String.valueOf(entityVersion.getVersion()));
    workflowExecution.setAccountId(app.getAccountId());
    if (!canOverrideFreeze && featureFlagService.isEnabled(SPG_SAVE_REJECTED_BY_FREEZE_WINDOWS, app.getAccountId())) {
      checkDeploymentFreezeRejectedExecution(app.getAccountId(), deploymentFreezeChecker, workflowExecution);
    }
    wingsPersistence.save(workflowExecution);
    sendEvent(app, executionArgs, workflowExecution);
    log.info("Created workflow execution {}", workflowExecution.getUuid());
    WorkflowExecution finalWorkflowExecution = workflowExecution;
    if (parameterizedArtifactStreamsPresent(executionArgs.getArtifactVariables())) {
      boolean allArtifactsAlreadyCollected = parameterizedArtifactsCollectedInWorkflowExecution(
          workflowExecution.getArtifacts(), executionArgs.getArtifactVariables());
      if (!executionArgs.isTriggeredFromPipeline() || executionArgs.getWorkflowType() != ORCHESTRATION
          || !allArtifactsAlreadyCollected) {
        workflowExecution.setStatus(PREPARING);
        updateStatus(
            workflowExecution.getAppId(), workflowExecution.getUuid(), PREPARING, "Starting artifact collection");
        executorService.submit(
            ()
                -> collectArtifactsAndStartExecution(finalWorkflowExecution, stateMachine, workflowExecutionAdvisor,
                    workflowExecutionUpdate, stdParams, app, workflow, pipeline, executionArgs, contextElements));
      } else if (executionArgs.isTriggeredFromPipeline() && executionArgs.getWorkflowType() == ORCHESTRATION) {
        workflowExecution = continueWorkflowExecution(workflowExecution, stateMachine, workflowExecutionAdvisor,
            workflowExecutionUpdate, stdParams, app, workflow, pipeline, executionArgs, contextElements);
      }
    } else {
      workflowExecution = continueWorkflowExecution(workflowExecution, stateMachine, workflowExecutionAdvisor,
          workflowExecutionUpdate, stdParams, app, workflow, pipeline, executionArgs, contextElements);
    }
    return workflowExecution;
  }

  @VisibleForTesting
  boolean shouldUseInfraBasedRollbackArtifact(String accountId, List<InfrastructureMapping> infraMappingList) {
    if (isEmpty(infraMappingList)) {
      return false;
    }
    return featureFlagService.isEnabled(INFRA_MAPPING_BASED_ROLLBACK_ARTIFACT, accountId)
        && infraMappingList.stream()
               .map(InfrastructureMapping::getDeploymentType)
               .collect(toSet())
               .contains(DeploymentType.KUBERNETES.name());
  }

  private PipelineEventData getPipelineEventData(PipelineSummary summary) {
    if (summary == null) {
      return null;
    }
    return PipelineEventData.builder().id(summary.getPipelineId()).name(summary.getPipelineName()).build();
  }

  private CgWorkflowStartPayload getEventPayloadData(
      Application app, ExecutionArgs executionArgs, WorkflowExecution execution, PipelineSummary summary) {
    return CgWorkflowStartPayload.builder()
        .application(ApplicationEventData.builder().id(app.getAppId()).name(app.getName()).build())
        .services(isEmpty(execution.getServiceIds()) ? emptyList()
                                                     : execution.getServiceIds()
                                                           .stream()
                                                           .map(id -> ServiceEntity.builder().id(id).build())
                                                           .collect(toList()))
        .infraDefinitions(isEmpty(execution.getInfraDefinitionIds())
                ? emptyList()
                : execution.getInfraDefinitionIds()
                      .stream()
                      .map(id -> InfraDefinitionEntity.builder().id(id).build())
                      .collect(toList()))
        .environments(isEmpty(execution.getEnvIds()) ? emptyList()
                                                     : execution.getEnvIds()
                                                           .stream()
                                                           .map(id -> EnvironmentEntity.builder().id(id).build())
                                                           .collect(toList()))
        .pipeline(getPipelineEventData(summary))
        .workflow(WorkflowEventData.builder()
                      .id(execution.getWorkflowId())
                      .name(workflowService.fetchWorkflowName(app.getUuid(), execution.getWorkflowId()))
                      .build())
        .startedAt(execution.getCreatedAt())
        .triggeredByType(execution.getCreatedByType())
        .triggeredBy(execution.getCreatedBy())
        .executionArgs(ExecutionArgsEventData.builder().notes(executionArgs.getNotes()).build())
        .pipelineExecution(PipelineExecData.builder().id(execution.getPipelineExecutionId()).build())
        .workflowExecution(WorkflowExecData.builder().id(execution.getUuid()).build())
        .build();
  }

  private void sendEvent(Application app, ExecutionArgs executionArgs, WorkflowExecution execution) {
    if (PIPELINE.equals(executionArgs.getWorkflowType())) {
      PipelineSummary summary = execution.getPipelineSummary();
      if (summary != null) {
        eventService.deliverEvent(app.getAccountId(), app.getUuid(),
            EventPayload.builder()
                .eventType(EventType.PIPELINE_START.getEventValue())
                .data(CgPipelineStartPayload.builder()
                          .application(ApplicationEventData.builder().id(app.getAppId()).name(app.getName()).build())
                          .executionId(execution.getUuid())
                          .pipelineExecution(PipelineExecData.builder().id(execution.getUuid()).build())
                          .services(isEmpty(execution.getServiceIds())
                                  ? emptyList()
                                  : execution.getServiceIds()
                                        .stream()
                                        .map(id -> ServiceEntity.builder().id(id).build())
                                        .collect(Collectors.toList()))
                          .infraDefinitions(isEmpty(execution.getInfraDefinitionIds())
                                  ? emptyList()
                                  : execution.getInfraDefinitionIds()
                                        .stream()
                                        .map(id -> InfraDefinitionEntity.builder().id(id).build())
                                        .collect(Collectors.toList()))
                          .environments(isEmpty(execution.getEnvIds())
                                  ? emptyList()
                                  : execution.getEnvIds()
                                        .stream()
                                        .map(id -> EnvironmentEntity.builder().id(id).build())
                                        .collect(Collectors.toList()))
                          .pipeline(PipelineEventData.builder()
                                        .id(summary.getPipelineId())
                                        .name(summary.getPipelineName())
                                        .build())
                          .startedAt(execution.getCreatedAt())
                          .triggeredByType(execution.getCreatedByType())
                          .triggeredBy(execution.getCreatedBy())
                          .executionArgs(ExecutionArgsEventData.builder().notes(executionArgs.getNotes()).build())
                          .build())
                .build());
      }
    } else {
      PipelineSummary summary = execution.getPipelineSummary();
      eventService.deliverEvent(app.getAccountId(), app.getUuid(),
          EventPayload.builder()
              .eventType(EventType.WORKFLOW_START.getEventValue())
              .data(getEventPayloadData(app, executionArgs, execution, summary))
              .build());
    }
  }

  @VisibleForTesting
  boolean parameterizedArtifactsCollectedInWorkflowExecution(
      List<Artifact> artifacts, List<ArtifactVariable> artifactVariables) {
    if (isEmpty(artifacts)) {
      return false;
    }
    List<String> parameterizedArtifactStreamIds = artifactVariables.stream()
                                                      .map(ArtifactVariable::getArtifactStreamMetadata)
                                                      .filter(Objects::nonNull)
                                                      .map(ArtifactStreamMetadata::getArtifactStreamId)
                                                      .collect(toList());
    List<String> artifactStreamIdsInExecution = artifacts.stream().map(Artifact::getArtifactStreamId).collect(toList());
    return artifactStreamIdsInExecution.containsAll(parameterizedArtifactStreamIds);
  }

  private boolean parameterizedArtifactStreamsPresent(List<ArtifactVariable> artifactVariables) {
    boolean foundParameterized = false;
    if (isNotEmpty(artifactVariables)) {
      for (ArtifactVariable artifactVariable : artifactVariables) {
        if (artifactVariable.getArtifactStreamMetadata() != null) {
          foundParameterized = true;
          break;
        }
      }
    }
    return foundParameterized;
  }

  @SuppressWarnings("squid:S00107")
  @VisibleForTesting
  protected void collectArtifactsAndStartExecution(WorkflowExecution workflowExecution, StateMachine stateMachine,
      ExecutionEventAdvisor workflowExecutionAdvisor, WorkflowExecutionUpdate workflowExecutionUpdate,
      WorkflowStandardParams stdParams, Application app, Workflow workflow, Pipeline pipeline,
      ExecutionArgs executionArgs, ContextElement... contextElements) {
    // Resolve artifact source parameters and collect artifacts before starting execution
    List<Artifact> artifacts = collectArtifacts(workflowExecution, executionArgs.getArtifactVariables(), app.getUuid());
    if (isNotEmpty(artifacts)) {
      addArtifactsToWorkflowExecution(workflowExecution, stdParams, executionArgs, artifacts);
      updateWorkflowExecutionArtifacts(workflowExecution.getAppId(), workflowExecution.getUuid(),
          workflowExecution.getArtifacts(), executionArgs.getArtifacts());
      if (workflowExecution.getPipelineExecutionId() != null) {
        WorkflowExecution pipelineExecution =
            getWorkflowExecution(workflowExecution.getAppId(), workflowExecution.getPipelineExecutionId(),
                WorkflowExecutionKeys.artifacts, WorkflowExecutionKeys.executionArgs);
        addArtifactsToExecutionAndExecutionArgs(pipelineExecution, pipelineExecution.getExecutionArgs(), artifacts);
        updateWorkflowExecutionArtifacts(workflowExecution.getAppId(), workflowExecution.getPipelineExecutionId(),
            pipelineExecution.getArtifacts(), pipelineExecution.getExecutionArgs().getArtifacts());
      }
    }
    WorkflowExecution savedWorkflowExecution = wingsPersistence.getWithAppId(
        WorkflowExecution.class, workflowExecution.getAppId(), workflowExecution.getUuid());
    if (savedWorkflowExecution.getStatus() != FAILED) {
      // artifact collection succeeded - unset the WorkflowExecution#message
      unsetWorkflowExecutionMessage(savedWorkflowExecution.getAppId(), savedWorkflowExecution.getUuid());
      // check for ABORT_ALL interrupt and abort the execution
      if (abortInterruptsFoundForExecution(workflowExecution)) {
        abortWorkflowExecution(workflowExecution.getAppId(), workflowExecution.getUuid());
        return;
      }
      continueWorkflowExecution(savedWorkflowExecution, stateMachine, workflowExecutionAdvisor, workflowExecutionUpdate,
          stdParams, app, workflow, pipeline, executionArgs, contextElements);
    }
  }

  private boolean abortInterruptsFoundForExecution(WorkflowExecution workflowExecution) {
    boolean interruptRegistered = false;
    List<ExecutionInterrupt> executionInterrupts =
        wingsPersistence.createQuery(ExecutionInterrupt.class, excludeAuthority)
            .filter(ExecutionInterruptKeys.appId, workflowExecution.getAppId())
            .filter(ExecutionInterruptKeys.executionUuid, workflowExecution.getUuid())
            .filter(ExecutionInterruptKeys.executionInterruptType, ABORT_ALL)
            .asList();
    if (isNotEmpty(executionInterrupts)) {
      interruptRegistered = true;
    }
    return interruptRegistered;
  }

  private void abortWorkflowExecution(String appId, String workflowExecutionId) {
    Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                         .filter(WorkflowExecutionKeys.appId, appId)
                                         .filter(WorkflowExecutionKeys.uuid, workflowExecutionId);

    UpdateOperations<WorkflowExecution> updateOps = wingsPersistence.createUpdateOperations(WorkflowExecution.class)
                                                        .set(WorkflowExecutionKeys.startTs, System.currentTimeMillis())
                                                        .set(WorkflowExecutionKeys.status, ABORTED);
    wingsPersistence.update(query, updateOps);
  }

  @SuppressWarnings("squid:S00107")
  private WorkflowExecution continueWorkflowExecution(WorkflowExecution workflowExecution, StateMachine stateMachine,
      ExecutionEventAdvisor workflowExecutionAdvisor, WorkflowExecutionUpdate workflowExecutionUpdate,
      WorkflowStandardParams stdParams, Application app, Workflow workflow, Pipeline pipeline,
      ExecutionArgs executionArgs, ContextElement... contextElements) {
    updateWorkflowElement(workflowExecution, stdParams, workflow);

    LinkedList<ContextElement> elements = new LinkedList<>();
    elements.push(stdParams);
    if (contextElements != null) {
      for (ContextElement contextElement : contextElements) {
        elements.push(contextElement);
      }
    }
    StateExecutionInstance stateExecutionInstance = new StateExecutionInstance();
    stateExecutionInstance.setAppId(workflowExecution.getAppId());
    stateExecutionInstance.setExecutionName(workflowExecution.normalizedName());
    stateExecutionInstance.setExecutionUuid(workflowExecution.getUuid());
    stateExecutionInstance.setExecutionType(workflowExecution.getWorkflowType());
    stateExecutionInstance.setOrchestrationWorkflowType(workflowExecution.getOrchestrationType());
    stateExecutionInstance.setWorkflowId(workflowExecution.getWorkflowId());
    stateExecutionInstance.setPipelineStageElementId(executionArgs.getPipelinePhaseElementId());
    stateExecutionInstance.setPipelineStageParallelIndex(executionArgs.getPipelinePhaseParallelIndex());
    stateExecutionInstance.setIsOnDemandRollback(workflowExecution.isOnDemandRollback());
    if (workflowExecutionUpdate == null) {
      workflowExecutionUpdate = new WorkflowExecutionUpdate();
    }
    workflowExecutionUpdate.setAppId(workflowExecution.getAppId());
    workflowExecutionUpdate.setWorkflowExecutionId(workflowExecution.getUuid());
    workflowExecutionUpdate.setNeedToNotifyPipeline(executionArgs.isTriggeredFromPipeline());

    stateExecutionInstance.setCallback(workflowExecutionUpdate);
    if (workflowExecutionAdvisor != null) {
      stateExecutionInstance.setExecutionEventAdvisors(asList(workflowExecutionAdvisor));
    }
    stateExecutionInstance.setContextElements(elements);
    stateExecutionInstance.setSubGraphFilterId("dummy");

    stateExecutionInstance = stateMachineExecutor.queue(stateMachine, stateExecutionInstance);

    WorkflowExecution savedWorkflowExecution;
    // set startTs before starting the actual execution, because after the starting event
    // we can have FAILED status in stateExecutionInstance (ex.: ApprovalState)
    setWorkflowExecutionStartTs(workflowExecution);
    // Multiple calls because of ISSUE - https://harness.atlassian.net/browse/CDC-9129
    savedWorkflowExecution = wingsPersistence.getWithAppId(
        WorkflowExecution.class, workflowExecution.getAppId(), workflowExecution.getUuid());
    if (workflowExecution.getWorkflowType() == PIPELINE) {
      savePipelineSweepingOutPut(workflowExecution, pipeline, savedWorkflowExecution);
    }
    if (featureFlagService.isEnabled(RESOLVE_DEPLOYMENT_TAGS_BEFORE_EXECUTION, app.getAccountId())) {
      ExecutionContextImpl context = new ExecutionContextImpl(stateExecutionInstance, stateMachine, injector);
      injector.injectMembers(workflowExecutionUpdate);
      // workflowId or pipelineId
      final String workflowId = context.getWorkflowId();
      List<NameValuePair> tags = workflowExecutionUpdate.resolveDeploymentTags(context, workflowId);
      workflowExecutionUpdate.addTagsToWorkflowExecution(tags);
    }
    stateMachineExecutor.startExecution(stateMachine, stateExecutionInstance);
    updateStartStatus(workflowExecution.getAppId(), workflowExecution.getUuid(), RUNNING, false);
    savedWorkflowExecution = wingsPersistence.getWithAppId(
        WorkflowExecution.class, workflowExecution.getAppId(), workflowExecution.getUuid());
    executionUpdate.publish(savedWorkflowExecution);
    return savedWorkflowExecution;
  }

  private void setWorkflowExecutionStartTs(WorkflowExecution workflowExecution) {
    Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                         .filter(WorkflowExecutionKeys.appId, workflowExecution.getAppId())
                                         .filter(WorkflowExecutionKeys.uuid, workflowExecution.getUuid())
                                         .field(WorkflowExecutionKeys.status)
                                         .in(ImmutableList.of(NEW, QUEUED, PREPARING));

    UpdateOperations<WorkflowExecution> updateOps = wingsPersistence.createUpdateOperations(WorkflowExecution.class)
                                                        .set(WorkflowExecutionKeys.startTs, System.currentTimeMillis());

    wingsPersistence.update(query, updateOps);
  }

  /**
   * Sets the artifacts and artifactVariables in Execution Args and Updates the artifacts in workflowexecution.
   * The method merges worklflow execution artifacts with the newArtifacts passed and sets these are the values
   * @param workflowExecution
   * @param stdParams
   * @param executionArgs
   * @param newArtifacts
   */
  private void addArtifactsToRuntimeWorkflowExecution(WorkflowExecution workflowExecution,
      WorkflowStandardParams stdParams, ExecutionArgs executionArgs, List<Artifact> newArtifacts) {
    newArtifacts = isEmpty(newArtifacts) ? new ArrayList<>() : newArtifacts;
    List<Artifact> existingArtifacts = isNotEmpty(workflowExecution.getArtifacts())
        ? new ArrayList<>(workflowExecution.getArtifacts())
        : new ArrayList<>();

    List<Artifact> artifacts = new ArrayList<>(existingArtifacts);
    newArtifacts.forEach(newArtifact -> {
      if (newArtifact.getUuid() == null) {
        artifacts.add(newArtifact);
      } else {
        if (artifacts.stream().map(Artifact::getUuid).noneMatch(newArtifact.getUuid()::equals)) {
          artifacts.add(newArtifact);
        }
      }
    });

    workflowExecution.setArtifacts(artifacts);
    executionArgs.setArtifacts(artifacts);
    List<String> artifactIds = artifacts.stream().map(Artifact::getUuid).collect(toList());
    stdParams.setArtifactIds(artifactIds);
  }

  @VisibleForTesting
  List<ArtifactVariable> getMergedArtifactVariableList(
      WorkflowExecution workflowExecution, List<ArtifactVariable> newArtifactVariables) {
    List<ArtifactVariable> existingArtifactVariables =
        isNotEmpty(workflowExecution.getExecutionArgs().getArtifactVariables())
        ? new ArrayList<>(workflowExecution.getExecutionArgs().getArtifactVariables())
        : new ArrayList<>();
    newArtifactVariables = isEmpty(newArtifactVariables) ? new ArrayList<>() : newArtifactVariables;
    List<ArtifactVariable> artifactVariables = new ArrayList<>(existingArtifactVariables);
    artifactVariables.addAll(newArtifactVariables);
    return artifactVariables;
  }

  private void addArtifactsToWorkflowExecution(WorkflowExecution workflowExecution, WorkflowStandardParams stdParams,
      ExecutionArgs executionArgs, List<Artifact> artifacts) {
    if (isNotEmpty(artifacts)) {
      addArtifactsToExecutionAndExecutionArgs(workflowExecution, executionArgs, artifacts);
      List<String> artifactIds = new ArrayList<>();
      if (isNotEmpty(artifacts)) {
        for (Artifact artifact : artifacts) {
          artifactIds.add(artifact.getUuid());
        }
      }
      if (isNotEmpty(stdParams.getArtifactIds())) {
        List<String> stdParamsArtifactIds = stdParams.getArtifactIds();
        stdParamsArtifactIds.addAll(artifactIds);
        stdParams.setArtifactIds(stdParamsArtifactIds);
      } else {
        stdParams.setArtifactIds(artifactIds);
      }
    }
  }

  private void addArtifactsToExecutionAndExecutionArgs(
      WorkflowExecution workflowExecution, ExecutionArgs executionArgs, List<Artifact> artifacts) {
    if (isNotEmpty(workflowExecution.getArtifacts())) {
      List<Artifact> workflowExecutionArtifacts = workflowExecution.getArtifacts();
      workflowExecutionArtifacts.addAll(artifacts);
      workflowExecution.setArtifacts(workflowExecutionArtifacts);
    } else {
      workflowExecution.setArtifacts(artifacts);
    }
    if (isNotEmpty(executionArgs.getArtifacts())) {
      List<Artifact> executionArgsArtifacts = executionArgs.getArtifacts();
      executionArgsArtifacts.addAll(artifacts);
      executionArgs.setArtifacts(executionArgsArtifacts);
    } else {
      executionArgs.setArtifacts(artifacts);
    }
  }

  public List<Artifact> collectArtifacts(
      WorkflowExecution workflowExecution, List<ArtifactVariable> artifactVariables, String appId) {
    List<Artifact> artifacts = new ArrayList<>();
    Queue<Future<BuildDetails>> futures = new ConcurrentLinkedQueue<>();
    List<ArtifactStream> artifactStreams = new ArrayList<>();
    if (isNotEmpty(artifactVariables)) {
      for (ArtifactVariable artifactVariable : artifactVariables) {
        if (artifactVariable.getArtifactStreamMetadata() != null) {
          String artifactStreamId = artifactVariable.getArtifactStreamMetadata().getArtifactStreamId();
          ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
          String finalSettingId = artifactStream.getSettingId();
          // fix parameter values in artifact stream
          artifactStreamHelper.resolveArtifactStreamRuntimeValues(
              artifactStream, artifactVariable.getArtifactStreamMetadata().getRuntimeValues());
          // update source name as well with runtime values
          artifactStream.setSourceName(artifactStream.generateSourceName());
          artifactStreams.add(artifactStream);
          futures.add(executorService.submit(()
                                                 -> buildSourceService.getBuild(appId, artifactStreamId, finalSettingId,
                                                     artifactVariable.getArtifactStreamMetadata().getRuntimeValues())));
        }
      }
      StringBuilder stringBuilder = new StringBuilder();
      int i = 0;
      while (!futures.isEmpty()) {
        ArtifactStream artifactStream = artifactStreams.get(i);
        try {
          BuildDetails buildDetails = futures.poll().get();
          i++;
          if (buildDetails == null) {
            stringBuilder.append(format("Error collecting build for artifact source %s.", artifactStream.getName()));
            stringBuilder.append('\n');
          } else {
            artifacts.add(artifactService.create(
                artifactCollectionUtils.getArtifact(artifactStream, buildDetails), artifactStream, false));
          }
          quietSleep(ofMillis(10));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          log.error(format("Error collecting build for artifact source %s.", artifactStream.getName()), e);
          stringBuilder.append(format("Error collecting build for artifact source %s.", artifactStream.getName()));
          stringBuilder.append('\n');
        } catch (ExecutionException e) {
          log.error(format("Error collecting build for artifact source %s.", artifactStream.getName()), e);
          stringBuilder.append(format("Error collecting build for artifact source %s.", artifactStream.getName()));
          stringBuilder.append('\n');
        }
      }
      String message = stringBuilder.toString();
      if (isNotEmpty(message)) {
        workflowExecution.setStatus(FAILED);
        workflowExecution.setMessage(message);
        updateStatus(workflowExecution.getAppId(), workflowExecution.getUuid(), FAILED, message);
        if (workflowExecution.getPipelineExecutionId() != null) {
          markRunningFailed(workflowExecution.getAppId(), workflowExecution.getPipelineExecutionId());
        }
      }
    }
    return artifacts;
  }

  private void savePipelineSweepingOutPut(
      WorkflowExecution workflowExecution, Pipeline pipeline, WorkflowExecution savedWorkflowExecution) {
    PipelineElement pipelineElement = PipelineElement.builder()
                                          .displayName(workflowExecution.displayName())
                                          .name(pipeline.getName())
                                          .description(pipeline.getDescription())
                                          .startTs(savedWorkflowExecution.getStartTs())
                                          .build();
    sweepingOutputService.save(SweepingOutputServiceImpl
                                   .prepareSweepingOutputBuilder(workflowExecution.getAppId(),
                                       workflowExecution.getUuid(), null, null, null, Scope.PIPELINE)
                                   .name("pipeline")
                                   .output(kryoSerializer.asDeflatedBytes(pipelineElement))
                                   .build());
  }

  private void updateWorkflowElement(
      WorkflowExecution workflowExecution, WorkflowStandardParams stdParams, Workflow workflow) {
    stdParams.setErrorStrategy(workflowExecution.getErrorStrategy());
    String workflowUrl = mainConfiguration.getPortal().getUrl() + "/"
        + format(mainConfiguration.getPortal().getExecutionUrlPattern(), workflowExecution.getAppId(),
            workflowExecution.getEnvId(), workflowExecution.getUuid());

    if (stdParams.getWorkflowElement() == null) {
      WorkflowElementBuilder workflowElementBuilder = WorkflowElement.builder()
                                                          .uuid(workflowExecution.getUuid())
                                                          .name(workflowExecution.normalizedName())
                                                          .url(workflowUrl)
                                                          .displayName(workflowExecution.displayName())
                                                          .releaseNo(workflowExecution.getReleaseNo());
      stdParams.setWorkflowElement(workflowElementBuilder.build());
    } else {
      stdParams.getWorkflowElement().setName(workflowExecution.normalizedName());
      stdParams.getWorkflowElement().setUuid(workflowExecution.getUuid());
      stdParams.getWorkflowElement().setUrl(workflowUrl);
      stdParams.getWorkflowElement().setDisplayName(workflowExecution.displayName());
      stdParams.getWorkflowElement().setReleaseNo(workflowExecution.getReleaseNo());
    }
    // set pipeline variables
    if (workflowExecution.getWorkflowType() == PIPELINE) {
      Map<String, String> workflowVariables = workflowExecution.getExecutionArgs().getWorkflowVariables();
      if (isNotEmpty(workflowVariables)) {
        stdParams.getWorkflowElement().setVariables(new HashMap<>(workflowVariables));
      }
    }
    stdParams.getWorkflowElement().setPipelineDeploymentUuid(workflowExecution.getWorkflowType() == PIPELINE
            ? workflowExecution.getUuid()
            : workflowExecution.getPipelineExecutionId());

    if (stdParams.getWorkflowElement().getPipelineResumeUuid() == null
        && workflowExecution.getPipelineExecutionId() != null) {
      WorkflowExecution pipelineExecution = getWorkflowExecution(workflowExecution.getAppId(),
          workflowExecution.getPipelineExecutionId(), WorkflowExecutionKeys.pipelineResumeId);
      stdParams.getWorkflowElement().setPipelineResumeUuid(pipelineExecution.getPipelineResumeId() != null
              ? pipelineExecution.getPipelineResumeId()
              : stdParams.getWorkflowElement().getPipelineDeploymentUuid());
    }

    lastGoodReleaseInfo(stdParams.getWorkflowElement(), workflowExecution);
    stdParams.getWorkflowElement().setDescription(workflow != null ? workflow.getDescription() : null);
  }

  private void populateServiceInstances(
      WorkflowExecution workflowExecution, Set<String> keywords, ExecutionArgs executionArgs) {
    if (executionArgs.getServiceInstances() != null) {
      List<String> serviceInstanceIds =
          executionArgs.getServiceInstances().stream().map(ServiceInstance::getUuid).collect(toList());
      PageRequest<ServiceInstance> pageRequest = aPageRequest()
                                                     .addFilter("appId", EQ, workflowExecution.getAppId())
                                                     .addFilter("uuid", IN, serviceInstanceIds.toArray())
                                                     .build();
      List<ServiceInstance> serviceInstances = serviceInstanceService.list(pageRequest).getResponse();

      if (serviceInstances == null || serviceInstances.size() != serviceInstanceIds.size()) {
        log.error("Service instances argument and valid service instance retrieved size not matching");
        throw new InvalidRequestException("Invalid service instances");
      }
      executionArgs.setServiceInstanceIdNames(serviceInstances.stream().collect(toMap(ServiceInstance::getUuid,
          serviceInstance -> serviceInstance.getHostName() + ":" + serviceInstance.getServiceName())));

      keywords.addAll(serviceInstances.stream().map(ServiceInstance::getHostName).collect(toList()));
      keywords.addAll(serviceInstances.stream().map(ServiceInstance::getServiceName).collect(toList()));
    }
  }

  private void populateCurrentUser(
      WorkflowExecution workflowExecution, WorkflowStandardParams stdParams, Trigger trigger, Set<String> keywords) {
    // otherwise the all these fields are inherited from pipeline already
    if (workflowExecution.getPipelineExecutionId() == null) {
      if (trigger != null) {
        // Triggered by Auto Trigger
        workflowExecution.setTriggeredBy(
            EmbeddedUser.builder().name(trigger.getName() + " (Deployment Trigger)").build());
        workflowExecution.setCreatedBy(
            EmbeddedUser.builder().name(trigger.getName() + " (Deployment Trigger)").build());
        workflowExecution.setDeploymentTriggerId(trigger.getUuid());
        workflowExecution.setCreatedByType(CreatedByType.TRIGGER);
      } else if (workflowExecution.getExecutionArgs() != null
          && CreatedByType.API_KEY == workflowExecution.getExecutionArgs().getCreatedByType()) {
        String apiKeyId = workflowExecution.getExecutionArgs().getTriggeringApiKeyId();
        String accountId = appService.getAccountIdByAppId(workflowExecution.getAppId());
        ApiKeyEntry apiKeyEntry = apiKeyService.get(apiKeyId, accountId);
        if (apiKeyEntry != null) {
          ApiKeyInfo apiKeyInfo =
              ApiKeyInfo.builder().appKeyId(apiKeyEntry.getUuid()).apiKeyName(apiKeyEntry.getName()).build();
          workflowExecution.setTriggeringApiKeyInfo(apiKeyInfo);
          EmbeddedUser triggeredBy = getEmbeddedUserFromApiKey(apiKeyInfo);
          workflowExecution.setTriggeredBy(triggeredBy);
          workflowExecution.setCreatedByType(CreatedByType.API_KEY);
          workflowExecution.setCreatedBy(triggeredBy);
        }
      } else {
        User user = UserThreadLocal.get();
        if (user != null) {
          EmbeddedUser triggeredBy =
              EmbeddedUser.builder().uuid(user.getUuid()).email(user.getEmail()).name(user.getName()).build();
          workflowExecution.setTriggeredBy(triggeredBy);
          workflowExecution.setCreatedByType(CreatedByType.USER);
          workflowExecution.setCreatedBy(triggeredBy);
        }
      }
    }
    if (workflowExecution.getCreatedBy() != null) {
      keywords.add(workflowExecution.getCreatedBy().getName());
      keywords.add(workflowExecution.getCreatedBy().getEmail());
    }
    stdParams.setCurrentUser(workflowExecution.getCreatedBy());
  }

  private void refreshEnvSummary(WorkflowExecution workflowExecution, Set<String> keywords) {
    if (isNotEmpty(workflowExecution.getEnvIds())) {
      List<EnvSummary> environmentSummaries =
          environmentService.obtainEnvironmentSummaries(workflowExecution.getAppId(), workflowExecution.getEnvIds());
      if (isNotEmpty(environmentSummaries)) {
        for (EnvSummary envSummary : environmentSummaries) {
          workflowExecution.setEnvironments(environmentSummaries);
          if (envSummary.getUuid().equals(workflowExecution.getEnvId())) {
            workflowExecution.setEnvName(envSummary.getName());
            workflowExecution.setEnvType(envSummary.getEnvironmentType());
          }
          if (workflowExecution.getEnvType() != null) {
            keywords.add(workflowExecution.getEnvType().name());
          }
          keywords.add(workflowExecution.getEnvName());
        }
      }
    }
  }

  private void populatePipelineSummary(
      WorkflowExecution workflowExecution, Set<String> keywords, ExecutionArgs executionArgs) {
    if (executionArgs.isTriggeredFromPipeline()) {
      if (executionArgs.getPipelineId() != null) {
        Pipeline pipeline =
            wingsPersistence.getWithAppId(Pipeline.class, workflowExecution.getAppId(), executionArgs.getPipelineId());
        workflowExecution.setPipelineSummary(
            PipelineSummary.builder().pipelineId(pipeline.getUuid()).pipelineName(pipeline.getName()).build());
        keywords.add(pipeline.getName());
      }
      if (workflowExecution.getPipelineExecutionId() != null) {
        WorkflowExecution pipelineExecution =
            wingsPersistence.createQuery(WorkflowExecution.class)
                .project(WorkflowExecutionKeys.triggeredBy, true)
                .project(WorkflowExecutionKeys.createdBy, true)
                .project(WorkflowExecutionKeys.deploymentTriggerId, true)
                .project(WorkflowExecutionKeys.createdByType, true)
                .project(WorkflowExecutionKeys.triggeringApiKeyInfo, true)
                .project(WorkflowExecutionKeys.artifacts, true)
                .filter(WorkflowExecutionKeys.appId, workflowExecution.getAppId())
                .filter(WorkflowExecutionKeys.uuid, workflowExecution.getPipelineExecutionId())
                .get();
        if (pipelineExecution != null) {
          workflowExecution.setTriggeredBy(pipelineExecution.getTriggeredBy());
          workflowExecution.setDeploymentTriggerId(pipelineExecution.getDeploymentTriggerId());
          workflowExecution.setCreatedBy(pipelineExecution.getCreatedBy());
          workflowExecution.setTriggeringApiKeyInfo(pipelineExecution.getTriggeringApiKeyInfo());
        }
      }
    }
  }

  public void populateArtifactsAndServices(WorkflowExecution workflowExecution, WorkflowStandardParams stdParams,
      Set<String> keywords, ExecutionArgs executionArgs, String accountId) {
    boolean shouldCollectArtifactVariablesData =
        featureFlagService.isEnabled(SPG_ENABLE_POPULATE_USING_ARTIFACT_VARIABLE, accountId);

    if (featureFlagService.isEnabled(HELM_CHART_AS_ARTIFACT, accountId)) {
      populateHelmChartsInWorkflowExecution(workflowExecution, keywords, executionArgs, accountId);
    }

    List<Artifact> artifacts;
    if (isNotEmpty(executionArgs.getArtifacts())) {
      List<String> artifactIds = executionArgs.getArtifacts()
                                     .stream()
                                     .map(Artifact::getUuid)
                                     .filter(Objects::nonNull)
                                     .distinct()
                                     .collect(toList());
      if (isEmpty(artifactIds)) {
        return;
      }

      artifacts = artifactService.listByIds(appService.getAccountIdByAppId(workflowExecution.getAppId()), artifactIds);
      if (artifacts == null || artifacts.size() != artifactIds.size()) {
        log.error("artifactIds from executionArgs contains invalid artifacts");
        throw new InvalidRequestException("Invalid artifact");
      }
    } else if (shouldCollectArtifactVariablesData && isNotEmpty(executionArgs.getArtifactVariables())) {
      List<List<Artifact>> tempArts = executionArgs.getArtifactVariables()
                                          .stream()
                                          .map(artifactVar
                                              -> artifactService.listArtifactsByArtifactStreamId(accountId,
                                                  artifactVar.getArtifactInput().getArtifactStreamId(),
                                                  artifactVar.getArtifactInput().getBuildNo()))
                                          .collect(toList());
      artifacts = tempArts.stream().collect(ArrayList::new, List::addAll, List::addAll);
    } else {
      log.info("artifact info from executionArgs doesnt contains valid artifacts");
      return;
    }

    // Filter out the artifacts that do not belong to the workflow.
    List<String> serviceIds =
        isEmpty(workflowExecution.getServiceIds()) ? new ArrayList<>() : workflowExecution.getServiceIds();
    Set<String> artifactStreamIds = new HashSet<>();
    serviceIds.forEach(serviceId -> {
      List<String> ids = artifactStreamServiceBindingService.listArtifactStreamIds(serviceId);
      if (isNotEmpty(ids)) {
        artifactStreamIds.addAll(ids);
      }
    });

    List<Artifact> filteredArtifacts = new ArrayList<>();
    for (Artifact artifact : artifacts) {
      if (artifactStreamIds.contains(artifact.getArtifactStreamId())) {
        filteredArtifacts.add(artifact);
      } else {
        log.warn("Artifact stream: [{}] is not available in services: {}", artifact.getArtifactStreamId(), serviceIds);
      }
    }

    if (shouldCollectArtifactVariablesData) {
      executionArgs.setArtifactIdNames(filteredArtifacts.stream().collect(
          toMap(Artifact::getUuid, Artifact::getDisplayName, (artifact1, artifact2) -> artifact1)));
    } else {
      executionArgs.setArtifactIdNames(
          filteredArtifacts.stream().collect(toMap(Artifact::getUuid, Artifact::getDisplayName)));
    }
    filteredArtifacts.forEach(artifact -> {
      artifact.setArtifactFiles(null);
      artifact.setCreatedBy(null);
      artifact.setLastUpdatedBy(null);
      keywords.add(artifact.getArtifactSourceName());
      keywords.add(artifact.getDescription());
      keywords.add(artifact.getRevision());
      keywords.addAll(artifact.getMetadata().values());
    });

    executionArgs.setArtifacts(artifacts);
    workflowExecution.setArtifacts(filteredArtifacts);

    Set<String> serviceIdsSet = new HashSet<>();
    List<ServiceElement> services = new ArrayList<>();
    filteredArtifacts.forEach(artifact -> {
      List<Service> relatedServices = artifactStreamServiceBindingService.listServices(artifact.getArtifactStreamId());
      if (isEmpty(relatedServices)) {
        return;
      }

      for (Service relatedService : relatedServices) {
        if (serviceIdsSet.contains(relatedService.getUuid())) {
          continue;
        }

        serviceIdsSet.add(relatedService.getUuid());
        ServiceElement se = ServiceElement.builder().build();
        MapperUtils.mapObject(relatedService, se);
        services.add(se);
        keywords.add(se.getName());
      }
    });

    // Set the services in the context.
    stdParams.setServices(services);
  }

  public void populateRollbackArtifacts(WorkflowExecution workflowExecution, List<String> infraMappingList,
      WorkflowStandardParams stdParams, boolean isInfraBasedArtifact) {
    if (EmptyPredicate.isNotEmpty(infraMappingList)) {
      List<Artifact> rollbackArtifacts =
          obtainLastGoodDeployedArtifacts(workflowExecution, infraMappingList, isInfraBasedArtifact);
      stdParams.setRollbackArtifactIds(rollbackArtifacts.stream().map(Artifact::getUuid).collect(toList()));
      workflowExecution.setRollbackArtifacts(rollbackArtifacts);
    }
  }

  private void populateHelmChartsInWorkflowExecution(
      WorkflowExecution workflowExecution, Set<String> keywords, ExecutionArgs executionArgs, String accountId) {
    if (isEmpty(executionArgs.getHelmCharts())) {
      return;
    }
    List<String> helmChartIds = executionArgs.getHelmCharts()
                                    .stream()
                                    .map(HelmChart::getUuid)
                                    .filter(Objects::nonNull)
                                    .distinct()
                                    .collect(toList());

    List<HelmChart> helmCharts = helmChartService.listByIds(accountId, helmChartIds);
    helmCharts.forEach(helmChart
        -> helmChart.setMetadata(applicationManifestService.fetchAppManifestProperties(
            helmChart.getAppId(), helmChart.getApplicationManifestId())));

    if (helmCharts == null || helmChartIds.size() != helmCharts.size()) {
      log.error("helmChartIds from executionArgs contains invalid helmCharts");
      throw new InvalidRequestException("Helm charts provided doesn't exist");
    }

    List<String> serviceIds =
        isEmpty(workflowExecution.getServiceIds()) ? new ArrayList<>() : workflowExecution.getServiceIds();

    List<HelmChart> filteredHelmCharts =
        helmCharts.stream().filter(chart -> serviceIds.contains(chart.getServiceId())).collect(toList());

    filteredHelmCharts.forEach(helmChart -> {
      keywords.addAll(Arrays.asList(helmChart.getName(), helmChart.getVersion(), helmChart.getDescription()));
      if (isNotEmpty(helmChart.getMetadata())) {
        keywords.addAll(helmChart.getMetadata().values());
      }
    });

    executionArgs.setHelmCharts(helmCharts);
    workflowExecution.setHelmCharts(filteredHelmCharts);
  }

  private static void removeDuplicates(List<Artifact> artifacts) {
    if (isEmpty(artifacts)) {
      return;
    }

    Map<String, Artifact> map = new LinkedHashMap<>();
    for (Artifact artifact : artifacts) {
      map.put(artifact.getUuid(), artifact);
    }

    artifacts.clear();
    artifacts.addAll(map.values());
  }

  private void lastGoodReleaseInfo(WorkflowElement workflowElement, WorkflowExecution workflowExecution) {
    WorkflowExecution workflowExecutionLast = fetchLastSuccessDeployment(workflowExecution);
    if (workflowExecutionLast != null) {
      workflowElement.setLastGoodDeploymentDisplayName(workflowExecutionLast.displayName());
      workflowElement.setLastGoodDeploymentUuid(workflowExecutionLast.getUuid());
      workflowElement.setLastGoodReleaseNo(workflowExecutionLast.getReleaseNo());
    }
  }

  @Override
  public void updateWorkflowElementWithLastGoodReleaseInfo(
      String appId, WorkflowElement workflowElement, String workflowExecutionId) {
    String[] fields = {WorkflowExecutionKeys.appId, WorkflowExecutionKeys.uuid, WorkflowExecutionKeys.status,
        WorkflowExecutionKeys.onDemandRollback, WorkflowExecutionKeys.infraMappingIds};
    WorkflowExecution workflowExecution = getWorkflowExecution(appId, workflowExecutionId, fields);
    lastGoodReleaseInfo(workflowElement, workflowExecution);
  }
  @Override
  public void updateStartStatus(
      String appId, String workflowExecutionId, ExecutionStatus status, boolean shouldUpdateStartTs) {
    Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                         .filter(WorkflowExecutionKeys.appId, appId)
                                         .filter(WorkflowExecutionKeys.uuid, workflowExecutionId)
                                         .field(WorkflowExecutionKeys.status)
                                         .in(asList(NEW, QUEUED, PREPARING));

    UpdateOperations<WorkflowExecution> updateOps;
    if (shouldUpdateStartTs) {
      updateOps = wingsPersistence.createUpdateOperations(WorkflowExecution.class)
                      .set(WorkflowExecutionKeys.status, status)
                      .set(WorkflowExecutionKeys.startTs, System.currentTimeMillis());
    } else {
      updateOps =
          wingsPersistence.createUpdateOperations(WorkflowExecution.class).set(WorkflowExecutionKeys.status, status);
    }

    wingsPersistence.update(query, updateOps);
  }

  private void updateStatus(String appId, String workflowExecutionId, ExecutionStatus status, String message) {
    Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                         .filter(WorkflowExecutionKeys.appId, appId)
                                         .filter(WorkflowExecutionKeys.uuid, workflowExecutionId)
                                         .field(WorkflowExecutionKeys.status)
                                         .in(asList(NEW, QUEUED, PREPARING, PAUSED));

    UpdateOperations<WorkflowExecution> updateOps = wingsPersistence.createUpdateOperations(WorkflowExecution.class)
                                                        .set(WorkflowExecutionKeys.status, status)
                                                        .set(WorkflowExecutionKeys.startTs, System.currentTimeMillis())
                                                        .set(WorkflowExecutionKeys.message, message);

    wingsPersistence.update(query, updateOps);
  }

  private void markRunningFailed(String appId, String workflowExecutionId) {
    Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                         .filter(WorkflowExecutionKeys.appId, appId)
                                         .filter(WorkflowExecutionKeys.uuid, workflowExecutionId)
                                         .field(WorkflowExecutionKeys.status)
                                         .in(asList(RUNNING));

    UpdateOperations<WorkflowExecution> updateOps = wingsPersistence.createUpdateOperations(WorkflowExecution.class)
                                                        .set(WorkflowExecutionKeys.status, FAILED)
                                                        .set(WorkflowExecutionKeys.endTs, System.currentTimeMillis());

    wingsPersistence.update(query, updateOps);
  }

  private void updateWorkflowExecutionArtifacts(String appId, String workflowExecutionId,
      List<Artifact> workflowExecutionArtifacts, List<Artifact> executionArgsArtifacts) {
    Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                         .filter(WorkflowExecutionKeys.appId, appId)
                                         .filter(WorkflowExecutionKeys.uuid, workflowExecutionId);

    UpdateOperations<WorkflowExecution> updateOps =
        wingsPersistence.createUpdateOperations(WorkflowExecution.class)
            .set(WorkflowExecutionKeys.startTs, System.currentTimeMillis())
            .set(WorkflowExecutionKeys.artifacts, workflowExecutionArtifacts)
            .set(WorkflowExecutionKeys.executionArgs_artifacts, executionArgsArtifacts);

    wingsPersistence.update(query, updateOps);
  }

  private void updateOverrideFreeze(String pipelineExecutionId, boolean canOverrideFreeze) {
    Query<WorkflowExecution> query =
        wingsPersistence.createQuery(WorkflowExecution.class).filter(WorkflowExecutionKeys.uuid, pipelineExecutionId);

    UpdateOperations<WorkflowExecution> updateOps =
        wingsPersistence.createUpdateOperations(WorkflowExecution.class)
            .set(WorkflowExecutionKeys.canOverrideFreeze, canOverrideFreeze);

    wingsPersistence.update(query, updateOps);
  }

  private void updateWorkflowExecutionArtifactsAndArtifactVariables(String appId, String workflowExecutionId,
      List<Artifact> workflowExecutionArtifacts, List<Artifact> executionArgsArtifacts,
      List<ArtifactVariable> executionArgsArtifactVariables) {
    Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                         .filter(WorkflowExecutionKeys.appId, appId)
                                         .filter(WorkflowExecutionKeys.uuid, workflowExecutionId);

    UpdateOperations<WorkflowExecution> updateOps =
        wingsPersistence.createUpdateOperations(WorkflowExecution.class)
            .set(WorkflowExecutionKeys.startTs, System.currentTimeMillis())
            .set(WorkflowExecutionKeys.executionArgs_artifact_variables, executionArgsArtifactVariables);

    if (isNotEmpty(workflowExecutionArtifacts)) {
      updateOps = updateOps.set(WorkflowExecutionKeys.artifacts, workflowExecutionArtifacts);
    }

    if (isNotEmpty(executionArgsArtifacts)) {
      updateOps = updateOps.set(WorkflowExecutionKeys.executionArgs_artifacts, executionArgsArtifacts);
    }

    wingsPersistence.update(query, updateOps);
  }

  private void unsetWorkflowExecutionMessage(String appId, String workflowExecutionId) {
    Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                         .filter(WorkflowExecutionKeys.appId, appId)
                                         .filter(WorkflowExecutionKeys.uuid, workflowExecutionId);

    UpdateOperations<WorkflowExecution> updateOps = wingsPersistence.createUpdateOperations(WorkflowExecution.class)
                                                        .set(WorkflowExecutionKeys.startTs, System.currentTimeMillis())
                                                        .unset(WorkflowExecutionKeys.message);

    wingsPersistence.update(query, updateOps);
  }

  private void updateStartStatusAndUnsetMessage(String appId, String workflowExecutionId, ExecutionStatus status) {
    Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                         .filter(WorkflowExecutionKeys.appId, appId)
                                         .filter(WorkflowExecutionKeys.uuid, workflowExecutionId)
                                         .filter(WorkflowExecutionKeys.status, PREPARING);

    UpdateOperations<WorkflowExecution> updateOps = wingsPersistence.createUpdateOperations(WorkflowExecution.class)
                                                        .set(WorkflowExecutionKeys.status, status)
                                                        .set(WorkflowExecutionKeys.startTs, System.currentTimeMillis())
                                                        .unset(WorkflowExecutionKeys.message);

    wingsPersistence.findAndModify(query, updateOps, HPersistence.returnNewOptions);
  }

  @Override
  public WorkflowExecution triggerEnvExecution(
      String appId, String envId, ExecutionArgs executionArgs, Trigger trigger) {
    String accountId = appService.getAccountIdByAppId(appId);

    validateExecutionArgsHosts(executionArgs, trigger);
    executionArgs.setHosts(trimExecutionArgsHosts(executionArgs.getHosts()));

    try {
      WorkflowExecution execution = triggerEnvExecution(appId, envId, executionArgs, null, trigger);
      alertService.closeAlertsOfType(accountId, appId, AlertType.USAGE_LIMIT_EXCEEDED);
      return execution;
    } catch (UsageLimitExceededException e) {
      String errMsg;
      if (e.getErrorType() != null) {
        errMsg = "Deployment rate limit reached. " + e.getErrorType().getErrorMessage();
      } else {
        errMsg = "Deployment rate limit reached. Some deployments may not be allowed. Please contact Harness support.";
      }
      log.info("Exception: {}, accountId={}", e, accountId);

      // open alert for triggers
      if (executionArgs.getWorkflowType() == WorkflowType.ORCHESTRATION && null != trigger) {
        alertService.openAlert(accountId, appId, AlertType.USAGE_LIMIT_EXCEEDED,
            new UsageLimitExceededAlert(e.getAccountId(), e.getLimit(), errMsg));
      }

      throw new WingsException(ErrorCode.USAGE_LIMITS_EXCEEDED, errMsg, USER);
    }
  }

  List<String> trimExecutionArgsHosts(List<String> hosts) {
    if (isEmpty(hosts)) {
      return emptyList();
    }
    return hosts.stream().map(StringUtils::trim).collect(Collectors.toList());
  }

  void validateExecutionArgsHosts(ExecutionArgs executionArgs, Trigger trigger) {
    if (!executionArgs.isTargetToSpecificHosts()) {
      // Hack as later validations are on empty host list, not on target flag
      executionArgs.setHosts(emptyList());
      return;
    }
    if (isEmpty(executionArgs.getHosts())) {
      throw new InvalidRequestException(
          "Host list can't be empty when Target To Specific Hosts option is enabled", USER);
    }

    if (executionArgs.getWorkflowType() == WorkflowType.PIPELINE) {
      throw new InvalidRequestException("Hosts can't be overridden for pipeline", USER);
    }

    if (trigger != null) {
      throw new InvalidRequestException("Hosts can't be overridden for triggers", USER);
    }
  }

  @Override
  public WorkflowExecution triggerPipelineResumeExecution(
      String appId, int parallelIndexToResume, WorkflowExecution prevWorkflowExecution) {
    String accountId = prevWorkflowExecution.getAccountId();
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AppLogContext(appId, OVERRIDE_ERROR)) {
      ImmutableMap<String, StateExecutionInstance> stateExecutionInstanceMap =
          getStateExecutionInstanceMap(prevWorkflowExecution);

      return triggerPipelineResumeExecution(
          appId, parallelIndexToResume, prevWorkflowExecution, accountId, stateExecutionInstanceMap);
    }
  }
  @Override
  public WorkflowExecution triggerPipelineResumeExecution(
      String appId, String stageName, WorkflowExecution prevWorkflowExecution) {
    String accountId = prevWorkflowExecution.getAccountId();
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AppLogContext(appId, OVERRIDE_ERROR)) {
      ImmutableMap<String, StateExecutionInstance> stateExecutionInstanceMap =
          getStateExecutionInstanceMap(prevWorkflowExecution);
      Pipeline pipeline = pipelineResumeUtils.getPipelineFromWorkflowExecution(prevWorkflowExecution, appId);
      int parallelIndexToResume = pipelineResumeUtils.getParallelIndexFromPipelineStageName(stageName, pipeline);
      return triggerPipelineResumeExecution(
          appId, parallelIndexToResume, prevWorkflowExecution, accountId, stateExecutionInstanceMap);
    }
  }

  private WorkflowExecution triggerPipelineResumeExecution(String appId, int parallelIndexToResume,
      WorkflowExecution prevWorkflowExecution, String accountId,
      ImmutableMap<String, StateExecutionInstance> stateExecutionInstanceMap) {
    Pipeline pipeline = pipelineResumeUtils.getPipelineForResume(
        appId, parallelIndexToResume, prevWorkflowExecution, stateExecutionInstanceMap);
    PreDeploymentChecker deploymentFreezeChecker = new DeploymentFreezeChecker(governanceConfigService,
        new DeploymentCtx(appId,
            featureFlagService.isEnabled(NEW_DEPLOYMENT_FREEZE, accountId)
                ? PipelineServiceHelper.getEnvironmentIdsForParallelIndex(pipeline, parallelIndexToResume)
                : pipeline.getEnvIds(),
            getPipelineServiceIds(pipeline)),
        environmentService, featureFlagService);

    User user = UserThreadLocal.get();
    boolean canOverrideFreeze = user != null && checkIfOverrideFreeze();
    if (!canOverrideFreeze) {
      deploymentFreezeChecker.check(accountId);
    }

    WorkflowExecution currWorkflowExecution =
        triggerPipelineExecution(appId, pipeline, prevWorkflowExecution.getExecutionArgs(), null, null,
            prevWorkflowExecution.getPipelineResumeId() != null ? prevWorkflowExecution.getPipelineResumeId()
                                                                : prevWorkflowExecution.getUuid());
    pipelineResumeUtils.updatePipelineExecutionsAfterResume(currWorkflowExecution, prevWorkflowExecution);
    return currWorkflowExecution;
  }

  @Override
  public List<PipelineStageGroupedInfo> getResumeStages(String appId, WorkflowExecution prevWorkflowExecution) {
    String accountId = prevWorkflowExecution.getAccountId();
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AppLogContext(appId, OVERRIDE_ERROR)) {
      return pipelineResumeUtils.getResumeStages(appId, prevWorkflowExecution);
    }
  }

  @Override
  public List<WorkflowExecution> getResumeHistory(String appId, WorkflowExecution prevWorkflowExecution) {
    String accountId = prevWorkflowExecution.getAccountId();
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AppLogContext(appId, OVERRIDE_ERROR)) {
      return pipelineResumeUtils.getResumeHistory(appId, prevWorkflowExecution);
    }
  }

  @Override
  public WorkflowExecution triggerRollbackExecutionWorkflow(
      String appId, WorkflowExecution workflowExecution, boolean fromPipe) {
    try (AutoLogContext ignore1 = new AccountLogContext(workflowExecution.getAccountId(), OVERRIDE_ERROR)) {
      if (fromPipe) {
        log.info("Triggering rollback from pipeline strategy");
      }

      if (!getOnDemandRollbackAvailable(appId, workflowExecution, fromPipe)) {
        throw new InvalidRequestException("On demand rollback should not be available for this execution");
      }

      WorkflowExecution activeWorkflowExecution = getRunningExecutions(workflowExecution);
      if (activeWorkflowExecution != null) {
        throw new InvalidRequestException("Cannot trigger Rollback, active execution found");
      }

      List<Artifact> previousArtifacts = validateAndGetPreviousArtifacts(workflowExecution, fromPipe);
      if (isNotEmpty(workflowExecution.getArtifacts()) && isEmpty(previousArtifacts)) {
        throw new InvalidRequestException("No previous artifact found to rollback to");
      }

      ExecutionArgs oldExecutionArgs = workflowExecution.getExecutionArgs();
      oldExecutionArgs.setArtifacts(previousArtifacts);
      oldExecutionArgs.setArtifactVariables(null);
      oldExecutionArgs.setTriggeredFromPipeline(fromPipe);
      return triggerRollbackExecution(
          appId, workflowExecution.getEnvId(), oldExecutionArgs, workflowExecution, fromPipe);
    }
  }

  @Override
  public RollbackConfirmation getOnDemandRollbackConfirmation(String appId, WorkflowExecution workflowExecution) {
    String accountId = workflowExecution.getAccountId();
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      if (alreadyRolledBack(workflowExecution)) {
        throw new InvalidRequestException("Rollback Execution is not available as already Rolled back");
      }

      if (!getOnDemandRollbackAvailable(appId, workflowExecution, false)) {
        throw new InvalidRequestException("On demand rollback should not be available for this execution");
      }

      if (!checkIfUsingSweepingOutputs(appId, workflowExecution)) {
        throw new InvalidRequestException("Rollback is not available for older deployment");
      }

      if (isEmpty(workflowExecution.getServiceIds()) || workflowExecution.getServiceIds().size() != 1) {
        throw new InvalidRequestException("Rollback Execution is not available for multi Service workflow");
      }

      if (workflowExecution.isOnDemandRollback()) {
        return RollbackConfirmation.builder()
            .valid(false)
            .validationMessage("Cannot trigger Rollback for RolledBack execution")
            .build();
      }

      String workflowId = workflowExecution.getWorkflowId();
      Workflow workflow = workflowService.readWorkflow(appId, workflowId);
      notNullCheck("Error reading workflow associated with this workflowExecution. Might be deleted", workflow);
      notNullCheck("Error reading workflow associated with this workflowExecution. Might be deleted",
          workflow.getOrchestrationWorkflow());

      WorkflowExecution activeWorkflowExecution = getRunningExecutions(workflowExecution);
      if (activeWorkflowExecution != null) {
        return RollbackConfirmation.builder()
            .valid(false)
            .validationMessage("Cannot trigger Rollback, active execution found")
            .activeWorkflowExecution(activeWorkflowExecution)
            .workflowId(workflowId)
            .build();
      }

      List<Artifact> previousArtifacts = validateAndGetPreviousArtifacts(workflowExecution, false);
      if (isNotEmpty(workflowExecution.getArtifacts()) && isEmpty(previousArtifacts)) {
        throw new InvalidRequestException("No artifact found in previous execution");
      }

      return RollbackConfirmation.builder().artifacts(previousArtifacts).workflowId(workflowId).valid(true).build();
    }
  }

  private boolean checkIfUsingSweepingOutputs(String appId, WorkflowExecution workflowExecution) {
    List<String> infraDefId = workflowExecution.getInfraDefinitionIds();
    InfrastructureDefinition infrastructureDefinition = infrastructureDefinitionService.get(appId, infraDefId.get(0));
    if (infrastructureDefinition.getDeploymentType() == DeploymentType.SSH
        || infrastructureDefinition.getDeploymentType() == DeploymentType.WINRM) {
      return workflowExecution.isUseSweepingOutputs();
    } else {
      return true;
    }
  }

  private boolean alreadyRolledBack(WorkflowExecution workflowExecution) {
    WorkflowExecution execution =
        wingsPersistence.createQuery(WorkflowExecution.class)
            .filter(WorkflowExecutionKeys.accountId, workflowExecution.getAccountId())
            .filter(WorkflowExecutionKeys.appId, workflowExecution.getAppId())
            .filter(WorkflowExecutionKeys.status, SUCCESS)
            .filter(WorkflowExecutionKeys.onDemandRollback, true)
            .filter(WorkflowExecutionKeys.originalExecution_executionId, workflowExecution.getUuid())
            .get();
    return execution != null;
  }

  private WorkflowExecution getRunningExecutions(WorkflowExecution workflowExecution) {
    final Query<WorkflowExecution> query =
        wingsPersistence.createQuery(WorkflowExecution.class)
            .filter(WorkflowExecutionKeys.appId, workflowExecution.getAppId())
            .filter(WorkflowExecutionKeys.infraMappingIds, workflowExecution.getInfraMappingIds())
            .field(WorkflowExecutionKeys.status)
            .in(ExecutionStatus.activeStatuses());

    return query.get();
  }

  private List<Artifact> validateAndGetPreviousArtifacts(WorkflowExecution workflowExecution, boolean fromPipe) {
    Query<WorkflowExecution> query =
        wingsPersistence.createQuery(WorkflowExecution.class)
            .filter(WorkflowExecutionKeys.appId, workflowExecution.getAppId())
            .filter(WorkflowExecutionKeys.workflowType, ORCHESTRATION)
            .filter(WorkflowExecutionKeys.infraMappingIds, workflowExecution.getInfraMappingIds())
            .order(Sort.descending(WorkflowExecutionKeys.createdAt));
    if (fromPipe && featureFlagService.isEnabled(FeatureName.SPG_PIPELINE_ROLLBACK, workflowExecution.getAccountId())) {
      query.field(WorkflowExecutionKeys.status).in(List.of(SUCCESS, FAILED));
    } else {
      query.filter(WorkflowExecutionKeys.status, SUCCESS);
    }

    List<WorkflowExecution> workflowExecutionList = new ArrayList<>();
    if (featureFlagService.isEnabled(
            FeatureName.ON_DEMAND_ROLLBACK_WITH_DIFFERENT_ARTIFACT, workflowExecution.getAccountId())) {
      FindOptions findOptions = new FindOptions();

      findOptions.hint(BasicDBUtils.getIndexObject(WorkflowExecution.mongoIndexes(), LAST_INFRAMAPPING_SEARCH_2));
      Query<WorkflowExecution> deploymentQuery = query.cloneQuery();
      deploymentQuery.filter(WorkflowExecutionKeys.deployment, true);
      WorkflowExecution existingWorkflow = deploymentQuery.get(findOptions);
      // this logic is used because deployment field is not populated on all executions
      // maybe in the future we should remove this and use only deployment query
      if (existingWorkflow != null) {
        query = deploymentQuery;
      } else {
        query.field(WorkflowExecutionKeys.serviceExecutionSummaries_instanceStatusSummaries_instanceElement_uuid)
            .exists();
      }

      boolean firstEntry = true;
      try (HIterator<WorkflowExecution> iterator = new HIterator<>(query.limit(NO_LIMIT).fetch(findOptions))) {
        for (WorkflowExecution wfExecution : iterator) {
          if (firstEntry) {
            firstEntry = false;
            workflowExecutionList.add(wfExecution);
            continue;
          }
          if (isArtifactsListDifferent(wfExecution.getArtifacts(), workflowExecutionList.get(0).getArtifacts())) {
            workflowExecutionList.add(wfExecution);
            break;
          }
        }
      }
    } else {
      workflowExecutionList = query.asList(new FindOptions().limit(2));
    }

    if (isEmpty(workflowExecutionList)) {
      throw new InvalidRequestException(
          "Not able to find previous successful workflowExecutions for workflowExecution: "
          + workflowExecution.getName());
    }

    if (!fromPipe) {
      if (!workflowExecutionList.get(0).getUuid().equals(workflowExecution.getUuid())) {
        log.info("Last successful execution found: {} ", workflowExecutionList.get(0));
        throw new InvalidRequestException(
            "This is not the latest successful Workflow Execution: " + workflowExecution.getName());
      }
    }

    if (workflowExecutionList.size() < 2) {
      throw new InvalidRequestException(
          "No previous execution found to rollback, workflowExecution: " + workflowExecution.getName());
    }

    WorkflowExecution lastSecondSuccessfulWE = workflowExecutionList.get(1);
    log.info("Fetching artifact from execution: {}", lastSecondSuccessfulWE);
    return lastSecondSuccessfulWE.getArtifacts();
  }

  boolean isArtifactsListDifferent(List<Artifact> artifactList1, List<Artifact> artifactList2) {
    // If both artifact list are empty, we should consider them different because artifact must be coming from swimlane.
    if (isEmpty(artifactList1) && isEmpty(artifactList2)) {
      return true;
    }

    if ((isEmpty(artifactList1) && isNotEmpty(artifactList2))
        || (isNotEmpty(artifactList1) && isEmpty(artifactList2))) {
      return true;
    }

    Set<String> artifactBuildNumberList1 = artifactList1.stream().map(Artifact::getUuid).collect(Collectors.toSet());
    Set<String> artifactBuildNumberList2 = artifactList2.stream().map(Artifact::getUuid).collect(Collectors.toSet());
    return !artifactBuildNumberList1.equals(artifactBuildNumberList2);
  }

  @Override
  public void incrementInProgressCount(String appId, String workflowExecutionId, int inc) {
    UpdateOperations<WorkflowExecution> ops = wingsPersistence.createUpdateOperations(WorkflowExecution.class);
    ops.inc("breakdown.inprogress", inc);
    wingsPersistence.update(wingsPersistence.createQuery(WorkflowExecution.class)
                                .filter(WorkflowExecutionKeys.appId, appId)
                                .filter(ID_KEY, workflowExecutionId),
        ops);
  }

  @Override
  public void incrementSuccess(String appId, String workflowExecutionId, int inc) {
    UpdateOperations<WorkflowExecution> ops = wingsPersistence.createUpdateOperations(WorkflowExecution.class);
    ops.inc("breakdown.success", inc);
    ops.inc("breakdown.inprogress", -1 * inc);
    wingsPersistence.update(wingsPersistence.createQuery(WorkflowExecution.class)
                                .filter(WorkflowExecutionKeys.appId, appId)
                                .filter(ID_KEY, workflowExecutionId),
        ops);
  }

  @Override
  public void incrementFailed(String appId, String workflowExecutionId, Integer inc) {
    UpdateOperations<WorkflowExecution> ops = wingsPersistence.createUpdateOperations(WorkflowExecution.class);
    ops.inc("breakdown.failed", inc);
    ops.inc("breakdown.inprogress", -1 * inc);
    wingsPersistence.update(wingsPersistence.createQuery(WorkflowExecution.class)
                                .filter(WorkflowExecutionKeys.appId, appId)
                                .filter(ID_KEY, workflowExecutionId),
        ops);
  }

  /**
   * Trigger env execution workflow execution.
   *
   * @param appId                   the app id
   * @param envId                   the env id
   * @param executionArgs           the execution args
   * @param workflowExecutionUpdate the workflow execution update
   * @return the workflow execution
   */
  WorkflowExecution triggerEnvExecution(String appId, String envId, ExecutionArgs executionArgs,
      WorkflowExecutionUpdate workflowExecutionUpdate, Trigger trigger) {
    String accountId = this.appService.getAccountIdByAppId(appId);
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Execution Triggered. Type: {}", executionArgs.getWorkflowType());
    }

    checkDeploymentRateLimit(accountId, appId);

    setArtifactsFromArtifactVariables(executionArgs);

    if (!featureFlagService.isEnabled(ADD_MANIFEST_COLLECTION_STEP, accountId)) {
      setManifestsFromManifestVariables(appId, executionArgs, accountId);
    } else {
      populateManifestVariablesFromHelmCharts(executionArgs);
    }

    switch (executionArgs.getWorkflowType()) {
      case PIPELINE: {
        log.debug("Received an pipeline execution request");
        if (executionArgs.getPipelineId() == null) {
          log.error("pipelineId is null for an pipeline execution");
          throw new InvalidRequestException("pipelineId is null for an pipeline execution");
        }
        return triggerPipelineExecution(appId, executionArgs.getPipelineId(), executionArgs, trigger);
      }

      case ORCHESTRATION: {
        log.debug("Received an orchestrated execution request");
        if (executionArgs.getOrchestrationId() == null) {
          log.error("workflowId is null for an orchestrated execution");
          throw new InvalidRequestException("workflowId is null for an orchestrated execution");
        }
        return triggerOrchestrationExecution(appId, envId, executionArgs.getOrchestrationId(), executionArgs, trigger);
      }

      default:
        throw new WingsException(ErrorCode.INVALID_ARGUMENT).addParam("args", "workflowType");
    }
  }

  private void setManifestsFromManifestVariables(String appId, ExecutionArgs executionArgs, String accountId) {
    if (isEmpty(executionArgs.getHelmCharts()) && isNotEmpty(executionArgs.getManifestVariables())) {
      List<HelmChart> manifests =
          executionArgs.getManifestVariables()
              .stream()
              .filter(manifestVariable -> HelmChartInputType.ID.equals(manifestVariable.getInputType()))
              .map(manifestVariable -> HelmChart.builder().uuid(manifestVariable.getValue()).build())
              .collect(toList());
      manifests.addAll(
          getHelmChartsForVersionManifestVariables(appId, executionArgs.getManifestVariables(), accountId));
      executionArgs.setHelmCharts(manifests);
    }
  }

  private void populateManifestVariablesFromHelmCharts(ExecutionArgs executionArgs) {
    if (isNotEmpty(executionArgs.getHelmCharts()) && isEmpty(executionArgs.getManifestVariables())) {
      List<ManifestVariable> manifestVariables = executionArgs.getHelmCharts()
                                                     .stream()
                                                     .map(helmChart
                                                         -> ManifestVariable.builder()
                                                                .appManifestId(helmChart.getApplicationManifestId())
                                                                .value(helmChart.getVersion())
                                                                .inputType(HelmChartInputType.VERSION)
                                                                .build())
                                                     .collect(toList());
      executionArgs.setManifestVariables(manifestVariables);
      executionArgs.setHelmCharts(new ArrayList<>());
    }
  }

  private List<HelmChart> getHelmChartsForVersionManifestVariables(
      String appId, List<ManifestVariable> manifestVariables, String accountId) {
    List<HelmChart> helmCharts = new ArrayList<>();
    manifestVariables.stream()
        .filter(mv -> HelmChartInputType.VERSION.equals(mv.getInputType()))
        .forEach(manifestVariable -> {
          if (isEmpty(manifestVariable.getAppManifestId())) {
            throw new InvalidRequestException("AppManifest Id not provided in manifest variables");
          }
          HelmChart helmChart = helmChartService.getManifestByVersionNumber(
              accountId, manifestVariable.getAppManifestId(), manifestVariable.getValue());
          if (helmChart != null) {
            helmCharts.add(helmChart);
          } else {
            Map<String, String> properties =
                applicationManifestService.fetchAppManifestProperties(appId, manifestVariable.getAppManifestId());
            HelmChart helmChartToSave = HelmChart.builder()
                                            .name(properties.get(CHART_NAME))
                                            .appId(appId)
                                            .accountId(accountId)
                                            .serviceId(manifestVariable.getServiceId())
                                            .applicationManifestId(manifestVariable.getAppManifestId())
                                            .version(manifestVariable.getValue())
                                            .build();
            helmChartToSave.setDisplayName(helmChartToSave.getName() != null
                    ? helmChartToSave.getName() + "-" + helmChartToSave.getVersion()
                    : helmChartToSave.getVersion());
            helmCharts.add(helmChartService.create(helmChartToSave));
          }
        });
    return helmCharts;
  }

  private void setArtifactsFromArtifactVariables(ExecutionArgs executionArgs) {
    if (isEmpty(executionArgs.getArtifacts()) && isNotEmpty(executionArgs.getArtifactVariables())) {
      // Artifact Variables are passed but not artifacts. For backwards compatibility, set artifacts in executionArgs.
      List<Artifact> artifacts = executionArgs.getArtifactVariables()
                                     .stream()
                                     .map(ArtifactVariable::getValue)
                                     .filter(Objects::nonNull)
                                     .distinct()
                                     .map(artifactId -> Artifact.Builder.anArtifact().withUuid(artifactId).build())
                                     .collect(toList());
      executionArgs.setArtifacts(artifacts);
    }
  }

  WorkflowExecution triggerRollbackExecution(String appId, String envId, ExecutionArgs executionArgs,
      WorkflowExecution previousWorkflowExecution, boolean fromPipe) {
    String accountId = appService.getAccountIdByAppId(appId);
    if (PIPELINE == executionArgs.getWorkflowType()) {
      throw new InvalidRequestException("Emergency rollback not supported for pipelines");
    }

    log.debug("Received an emergency rollback  execution request");
    if (executionArgs.getOrchestrationId() == null) {
      log.error("workflowId is null for an orchestrated execution");
      throw new InvalidRequestException("workflowId is null for an orchestrated execution");
    }

    log.info("Execution Triggered. Type: {}, accountId={}", executionArgs.getWorkflowType(), accountId);

    String workflowId = executionArgs.getOrchestrationId();
    Workflow workflow = workflowExecutionServiceHelper.obtainWorkflow(appId, workflowId);

    // Doing this check here so that workflow is already fetched from database.
    preDeploymentChecks.checkIfWorkflowUsingRestrictedFeatures(workflow);

    PreDeploymentChecker deploymentFreezeChecker = new DeploymentFreezeChecker(governanceConfigService,
        new DeploymentCtx(appId, Collections.singletonList(envId), getWorkflowServiceIds(workflow)), environmentService,
        featureFlagService);
    User user = UserThreadLocal.get();
    boolean canOverrideFreeze = user != null && checkIfOverrideFreeze();
    if (featureFlagService.isEnabled(FeatureName.FREEZE_DURING_MIGRATION, accountId)) {
      canOverrideFreeze = true;
    }
    if (!canOverrideFreeze && !featureFlagService.isEnabled(SPG_SAVE_REJECTED_BY_FREEZE_WINDOWS, accountId)) {
      deploymentFreezeChecker.check(accountId);
    }

    // Not including instance limit and deployment limit check as it is a emergency rollback
    accountExpirationChecker.check(accountId);
    workflow.setOrchestrationWorkflow(
        workflowConcurrencyHelper.enhanceWithConcurrencySteps(workflow, executionArgs.getWorkflowVariables()));

    StateMachine stateMachine =
        rollbackStateMachineGenerator.generateForRollbackExecution(appId, previousWorkflowExecution.getUuid());

    // This is workaround for a side effect in the state machine generation that mangles with the original
    //       workflow object.
    workflow = workflowService.readWorkflow(appId, workflowId);

    stateMachine.setOrchestrationWorkflow(null);

    WorkflowExecution workflowExecution = workflowExecutionServiceHelper.obtainExecution(workflow, stateMachine, envId,
        fromPipe ? previousWorkflowExecution.getPipelineExecutionId() : null, executionArgs);
    workflowExecution.setOnDemandRollback(true);
    workflowExecution.setOriginalExecution(WorkflowExecutionInfo.builder()
                                               .name(previousWorkflowExecution.getName())
                                               .startTs(previousWorkflowExecution.getStartTs())
                                               .executionId(previousWorkflowExecution.getUuid())
                                               .build());

    WorkflowStandardParams stdParams =
        workflowExecutionServiceHelper.obtainWorkflowStandardParams(appId, envId, executionArgs, workflow);

    return triggerExecution(workflowExecution, stateMachine, new CanaryWorkflowExecutionAdvisor(), null, stdParams,
        null, null, workflow, canOverrideFreeze, deploymentFreezeChecker);
  }

  private void checkDeploymentRateLimit(String accountId, String appId) {
    try {
      deployLimitChecker.check(accountId);
      alertService.closeAlertsOfType(accountId, GLOBAL_APP_ID, AlertType.DEPLOYMENT_RATE_APPROACHING_LIMIT);
    } catch (LimitApproachingException e) {
      String errMsg = e.getPercent()
          + "% of Deployment Rate Limit reached. Some deployments may not be allowed beyond 100% usage. Please contact Harness support.";
      log.info("Approaching Limit Message: {}", e.getMessage());
      AlertData alertData = new DeploymentRateApproachingLimitAlert(e.getLimit(), accountId, e.getPercent(), errMsg);
      alertService.openAlert(accountId, GLOBAL_APP_ID, AlertType.DEPLOYMENT_RATE_APPROACHING_LIMIT, alertData);
    } catch (UsageLimitExceededException e) {
      throw e;
    } catch (Exception e) {
      log.error("Error checking deployment rate limit. accountId={}", accountId, e);
    }
  }

  private void checkInstanceUsageLimit(String accountId, String appId) {
    try {
      siUsageChecker.check(accountId);
    } catch (InstanceUsageExceededLimitException e) {
      throw new WingsException(ErrorCode.USAGE_LIMITS_EXCEEDED,
          "You have reached your service instance limits. Deployments will be blocked.", USER);
    } catch (Exception e) {
      log.error("Error while checking SI usage limit. accountId={}", accountId, e);
    }
  }

  private List<WorkflowExecution> getRunningWorkflowExecutions(
      WorkflowType workflowType, String appId, String workflowId) {
    PageRequest<WorkflowExecution> pageRequest =
        aPageRequest()
            .addFilter(WorkflowExecutionKeys.appId, EQ, appId)
            .addFilter(WorkflowExecutionKeys.workflowId, EQ, workflowId)
            .addFilter(WorkflowExecutionKeys.workflowType, EQ, workflowType)
            .addFilter(WorkflowExecutionKeys.status, IN, NEW, QUEUED, RUNNING, PAUSED)
            .addFieldsIncluded(WorkflowExecutionKeys.status)
            .build();
    pageRequest.setOptions(Collections.singletonList(PageRequest.Option.SKIPCOUNT));
    PageResponse<WorkflowExecution> pageResponse = wingsPersistence.query(WorkflowExecution.class, pageRequest);
    if (pageResponse == null) {
      return null;
    }
    return pageResponse.getResponse();
  }

  @Override
  public ExecutionInterrupt triggerExecutionInterrupt(ExecutionInterrupt executionInterrupt) {
    // TODO: @deepakputhraya Handle events
    String executionUuid = executionInterrupt.getExecutionUuid();
    WorkflowExecution workflowExecution =
        wingsPersistence.getWithAppId(WorkflowExecution.class, executionInterrupt.getAppId(), executionUuid);
    if (workflowExecution == null) {
      throw new InvalidRequestException("Workflow execution does not exist.");
    }

    // handling abort_all interrupt explicitly when execution in preparing state.
    if (workflowExecution.getStatus() == PREPARING && executionInterrupt.getExecutionInterruptType() == ABORT_ALL) {
      updateStartStatusAndUnsetMessage(workflowExecution.getAppId(), workflowExecution.getUuid(), ABORTED);
      wingsPersistence.save(executionInterrupt);
      return executionInterrupt;
    }

    if (ExecutionStatus.isFinalStatus(workflowExecution.getStatus())) {
      // There is a race between the workflow progress and request coming from the user.
      // It is completely normal the workflow to finish while interrupt request is coming.
      // Therefore there is nothing alarming when this occurs.
      throw new InvalidRequestException(
          "Workflow execution [" + workflowExecution.getName() + "] already completed.", USER);
    }

    if (workflowExecution.getWorkflowType() != PIPELINE) {
      if (executionInterrupt.getExecutionInterruptType() == PAUSE && workflowExecution.getStatus() == PREPARING) {
        throw new InvalidRequestException("Cannot pause Workflow in Preparing state");
      }
      executionInterruptManager.registerExecutionInterrupt(executionInterrupt);
      invalidateCache(workflowExecution, executionInterrupt);
      return executionInterrupt;
    }

    if (executionInterrupt.getExecutionInterruptType() == PAUSE_ALL && workflowExecution.getStatus() == PREPARING) {
      throw new InvalidRequestException("Cannot pause pipeline in Preparing state");
    }

    if (!(executionInterrupt.getExecutionInterruptType() == PAUSE_ALL
            || executionInterrupt.getExecutionInterruptType() == RESUME_ALL
            || executionInterrupt.getExecutionInterruptType() == ABORT_ALL)) {
      throw new InvalidRequestException("Invalid ExecutionInterrupt: " + executionInterrupt);
    }

    try {
      executionInterruptManager.registerExecutionInterrupt(executionInterrupt);
    } catch (WingsException exception) {
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (RuntimeException exception) {
      log.error("Error in interrupting workflowExecution - uuid: {}, executionInterruptType: {}",
          workflowExecution.getUuid(), executionInterrupt.getExecutionInterruptType(), exception);
    }

    List<StateExecutionInstance> stateExecutionInstances = getStateExecutionInstances(workflowExecution);
    for (StateExecutionInstance stateExecutionInstance : stateExecutionInstances) {
      StateExecutionData stateExecutionData = stateExecutionInstance.fetchStateExecutionData();
      if (!(stateExecutionData instanceof EnvStateExecutionData)) {
        continue;
      }
      EnvStateExecutionData envStateExecutionData = (EnvStateExecutionData) stateExecutionData;
      if (isEmpty(envStateExecutionData.getWorkflowExecutionId())) {
        continue;
      }
      String[] fields = {WorkflowExecutionKeys.uuid, WorkflowExecutionKeys.status};
      WorkflowExecution workflowExecution2 =
          getWorkflowExecution(workflowExecution.getAppId(), envStateExecutionData.getWorkflowExecutionId(), fields);

      if (workflowExecution2 == null
          || (workflowExecution2.getStatus() != null
              && ExecutionStatus.isFinalStatus(workflowExecution2.getStatus()))) {
        continue;
      }

      try {
        ExecutionInterrupt executionInterruptClone = kryoSerializer.clone(executionInterrupt);
        executionInterruptClone.setUuid(generateUuid());
        executionInterruptClone.setExecutionUuid(workflowExecution2.getUuid());
        executionInterruptManager.registerExecutionInterrupt(executionInterruptClone);
      } catch (WingsException exception) {
        ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
      } catch (RuntimeException exception) {
        log.error("Error in interrupting workflowExecution - uuid: {}, executionInterruptType: {}",
            workflowExecution.getUuid(), executionInterrupt.getExecutionInterruptType(), exception);
      }
    }

    invalidateCache(workflowExecution, executionInterrupt);
    return executionInterrupt;
  }

  @Override
  public RequiredExecutionArgs getRequiredExecutionArgs(String appId, String envId, ExecutionArgs executionArgs) {
    notNullCheck("workflowType", executionArgs.getWorkflowType());

    if (executionArgs.getWorkflowType() == ORCHESTRATION) {
      log.debug("Received an orchestrated execution request");
      notNullCheck("orchestrationId", executionArgs.getOrchestrationId());

      Workflow workflow = workflowService.readWorkflow(appId, executionArgs.getOrchestrationId());
      if (workflow == null || workflow.getOrchestrationWorkflow() == null) {
        throw new InvalidRequestException("OrchestrationWorkflow not found");
      }

      StateMachine stateMachine =
          workflowService.readStateMachine(appId, executionArgs.getOrchestrationId(), workflow.getDefaultVersion());
      if (stateMachine == null) {
        throw new InvalidRequestException("Associated state machine not found");
      }

      RequiredExecutionArgs requiredExecutionArgs = new RequiredExecutionArgs();
      requiredExecutionArgs.setEntityTypes(workflow.getOrchestrationWorkflow().getRequiredEntityTypes());
      return requiredExecutionArgs;
    }

    return null;
  }

  @Override
  public WorkflowVariablesMetadata fetchWorkflowVariables(
      String appId, ExecutionArgs executionArgs, String workflowExecutionId, String pipelineStageElementId) {
    if (isNotEmpty(pipelineStageElementId)) {
      return workflowExecutionServiceHelper.fetchWorkflowVariablesForRunningExecution(
          appId, workflowExecutionId, pipelineStageElementId);
    }
    return workflowExecutionServiceHelper.fetchWorkflowVariables(appId, executionArgs, workflowExecutionId);
  }

  @Override
  public DeploymentMetadata fetchDeploymentMetadataRunningPipeline(String appId, Map<String, String> workflowVariables,
      boolean withDefaultArtifact, String workflowExecutionId, String pipelineStageElementId) {
    if (isEmpty(workflowExecutionId) || isEmpty(pipelineStageElementId)) {
      throw new InvalidRequestException(
          "ExecutionId and PipelineStageElementId is required to check Inputs for a running execution");
    }
    if (workflowVariables == null) {
      workflowVariables = new HashMap<>();
    }

    String[] fields = {
        WorkflowExecutionKeys.executionArgs, WorkflowExecutionKeys.pipelineExecution, WorkflowExecutionKeys.workflowId};
    WorkflowExecution workflowExecution = getWorkflowExecution(appId, workflowExecutionId, fields);
    if (workflowExecution.getPipelineExecution() == null) {
      throw new InvalidRequestException("The given ExecutionId is invalid. Please give a valid PipelineExecutionId");
    }

    String pipelineId = workflowExecution.getWorkflowId();
    Pipeline pipeline = pipelineService.readPipelineResolvedVariablesLoopedInfo(
        appId, pipelineId, workflowExecution.getExecutionArgs().getWorkflowVariables());
    notNullCheck("Couldnt load a pipeline associated with given executionId: " + workflowExecutionId, pipeline);

    List<PipelineStage> pipelineStages = pipeline.getPipelineStages();
    if (isEmpty(pipelineStages)) {
      throw new InvalidRequestException("The given pipeline does not contain any stage");
    }

    Include[] includes = new Include[] {ENVIRONMENT, ARTIFACT_SERVICE, DEPLOYMENT_TYPE};

    for (PipelineStage pipelineStage : pipelineStages) {
      PipelineStageElement pipelineStageElement = pipelineStage.getPipelineStageElements().get(0);
      if (pipelineStageElement.getUuid().equals(pipelineStageElementId)) {
        if (pipelineStageElement.getProperties().get("workflowId") == null) {
          throw new InvalidRequestException(
              "Could find a workflow associated with given PipelineStage: " + pipelineStageElementId);
        }
        if (pipeline.isHasBuildWorkflow()) {
          return DeploymentMetadata.builder().build();
        }
        String workflowId = (String) pipelineStageElement.getProperties().get("workflowId");
        Workflow workflow = workflowService.readWorkflow(appId, workflowId);
        notNullCheck(
            "NOt able to load workflow associated with given PipelineStageElementId: " + pipelineStageElementId,
            workflow);
        notNullCheck(
            "NOt able to load workflow associated with given PipelineStageElementId: " + pipelineStageElementId,
            workflow.getOrchestrationWorkflow());
        Map<String, String> wfVars =
            getWFVarFromPipelineVar(workflowVariables, workflowExecution, pipeline, workflow, pipelineStageElementId);
        return workflowService.fetchDeploymentMetadata(
            appId, workflow, wfVars, null, null, withDefaultArtifact, workflowExecution, includes);
      }
    }

    throw new InvalidRequestException(
        " No PipelineStage found for given PipelineStageElementId: " + pipelineStageElementId);
  }

  @Override
  public DeploymentMetadata fetchDeploymentMetadata(String appId, ExecutionArgs executionArgs,
      boolean withDefaultArtifact, String workflowExecutionId, boolean withLastDeployedInfo) {
    notNullCheck("Workflow type is required", executionArgs.getWorkflowType());
    WorkflowExecution workflowExecution = null;
    if (withDefaultArtifact && workflowExecutionId != null) {
      workflowExecution = getWorkflowExecution(
          appId, workflowExecutionId, WorkflowExecutionKeys.executionArgs, WorkflowExecutionKeys.helmCharts);
    }

    Include[] includes =
        withLastDeployedInfo ? Include.values() : new Include[] {ENVIRONMENT, ARTIFACT_SERVICE, DEPLOYMENT_TYPE};
    DeploymentMetadata finalDeploymentMetadata;
    if (executionArgs.getWorkflowType() == ORCHESTRATION) {
      Workflow workflow = workflowService.readWorkflow(appId, executionArgs.getOrchestrationId());
      finalDeploymentMetadata = workflowService.fetchDeploymentMetadata(appId, workflow,
          executionArgs.getWorkflowVariables(), null, null, withDefaultArtifact, workflowExecution, includes);
    } else {
      finalDeploymentMetadata = pipelineService.fetchDeploymentMetadata(appId, executionArgs.getPipelineId(),
          executionArgs.getWorkflowVariables(), null, null, withDefaultArtifact, workflowExecution, includes);
    }

    if (finalDeploymentMetadata != null) {
      // Set environments.
      finalDeploymentMetadata.setEnvSummaries(
          environmentService.obtainEnvironmentSummaries(appId, finalDeploymentMetadata.getEnvIds()));
    }

    return finalDeploymentMetadata;
  }

  @Override
  public DeploymentMetadata fetchDeploymentMetadata(String appId, ExecutionArgs executionArgs) {
    return fetchDeploymentMetadata(appId, executionArgs, false, null, false);
  }

  @Override
  public boolean workflowExecutionsRunning(WorkflowType workflowType, String appId, String workflowId) {
    PageRequest<WorkflowExecution> pageRequest = aPageRequest()
                                                     .addFilter("appId", EQ, appId)
                                                     .addFilter("workflowId", EQ, workflowId)
                                                     .addFilter("workflowType", EQ, workflowType)
                                                     .addFilter("status", IN, activeStatuses())
                                                     .addFieldsIncluded("uuid")
                                                     .build();
    pageRequest.setOptions(Collections.singletonList(PageRequest.Option.SKIPCOUNT));
    PageResponse<WorkflowExecution> pageResponse = wingsPersistence.query(WorkflowExecution.class, pageRequest);
    if (isEmpty(pageResponse)) {
      return false;
    }
    return true;
  }

  @Override
  public List<String> runningExecutionsForEnvironment(String appId, String environmentId) {
    List<WorkflowExecution> executions = wingsPersistence.createQuery(WorkflowExecution.class)
                                             .filter(WorkflowExecutionKeys.appId, appId)
                                             .filter(WorkflowExecutionKeys.envIds, environmentId)
                                             .field(WorkflowExecutionKeys.status)
                                             .in(ExecutionStatus.activeStatuses())
                                             .project(WorkflowExecutionKeys.uuid, true)
                                             .project(WorkflowExecutionKeys.name, true)
                                             .limit(NO_LIMIT)
                                             .asList();
    if (isEmpty(executions)) {
      return emptyList();
    }
    return executions.stream().map(WorkflowExecution::getName).collect(Collectors.toList());
  }

  @Override
  public List<String> runningExecutionsForApplication(String appId) {
    List<WorkflowExecution> executions = wingsPersistence.createQuery(WorkflowExecution.class)
                                             .filter(WorkflowExecutionKeys.appId, appId)
                                             .field(WorkflowExecutionKeys.status)
                                             .in(ExecutionStatus.activeStatuses())
                                             .project(WorkflowExecutionKeys.uuid, true)
                                             .project(WorkflowExecutionKeys.name, true)
                                             .limit(NO_LIMIT)
                                             .asList();
    if (isEmpty(executions)) {
      return emptyList();
    }
    return executions.stream().map(WorkflowExecution::getName).collect(Collectors.toList());
  }

  @Override
  public List<String> runningExecutionsForService(String appId, String serviceId) {
    List<WorkflowExecution> executions = wingsPersistence.createQuery(WorkflowExecution.class)
                                             .filter(WorkflowExecutionKeys.appId, appId)
                                             .filter(WorkflowExecutionKeys.serviceIds, serviceId)
                                             .field(WorkflowExecutionKeys.status)
                                             .in(ExecutionStatus.activeStatuses())
                                             .project(WorkflowExecutionKeys.uuid, true)
                                             .project(WorkflowExecutionKeys.name, true)
                                             .limit(NO_LIMIT)
                                             .asList();
    if (isEmpty(executions)) {
      return emptyList();
    }
    return executions.stream().map(WorkflowExecution::getName).collect(Collectors.toList());
  }

  @Override
  public List<WorkflowExecution> getRunningExecutionsForInfraDef(String appId, String infraDefinitionId) {
    return wingsPersistence.createQuery(WorkflowExecution.class)
        .filter(WorkflowExecutionKeys.appId, appId)
        .field(WorkflowExecutionKeys.infraDefinitionIds)
        .equal(infraDefinitionId)
        .field(WorkflowExecutionKeys.status)
        .in(ExecutionStatus.activeStatuses())
        .project(WorkflowExecutionKeys.uuid, true)
        .project(WorkflowExecutionKeys.name, true)
        .project(WorkflowExecutionKeys.status, true)
        .limit(NO_LIMIT)
        .asList();
  }

  @Override
  public List<HelmChart> obtainLastGoodDeployedHelmCharts(String appId, String workflowId) {
    WorkflowExecution workflowExecution = fetchLastSuccessDeployment(appId, workflowId);
    if (workflowExecution != null && workflowExecution.getExecutionArgs() != null) {
      return workflowExecution.getExecutionArgs().getHelmCharts();
    }
    return new ArrayList<>();
  }

  public boolean continuePipelineStage(
      String appId, String pipelineExecutionId, String pipelineStageElementId, ExecutionArgs executionArgs) {
    setArtifactsFromArtifactVariables(executionArgs);

    WorkflowExecution pipelineExecution =
        wingsPersistence.getWithAppId(WorkflowExecution.class, appId, pipelineExecutionId);

    // check triggered continue
    notNullCheck("Invalid executionId: " + pipelineExecutionId, pipelineExecution);
    // Map WF variables from pipeline variables
    if (isNotEmpty(executionArgs.getArtifacts())) {
      executionArgs.setArtifacts(executionArgs.getArtifacts()
                                     .stream()
                                     .map(t -> artifactService.get(pipelineExecution.getAccountId(), t.getUuid()))
                                     .filter(Objects::nonNull)
                                     .collect(toList()));
    }
    Map<String, String> wfVariables =
        validateContinuePipeline(appId, pipelineExecution, pipelineStageElementId, executionArgs);

    // Update User permission who submits the runtime inputs
    updateOverrideFreeze(pipelineExecutionId, pipelineExecution.isCanOverrideFreeze());

    StateExecutionInstance stateExecutionInstance =
        getStateExecutionInstancePipelineStage(appId, pipelineExecutionId, pipelineStageElementId);
    if (stateExecutionInstance.getStatus() != PAUSED) {
      throw new InvalidRequestException("Pipeline stage is not in paused state");
    }
    ExecutionContextImpl context =
        stateMachineExecutor.getExecutionContext(appId, pipelineExecutionId, stateExecutionInstance.getUuid());

    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    notNullCheck("Couldn't continue this pipelineStage, might be expired", workflowStandardParams);
    List<Artifact> artifacts = executionArgs.getArtifacts();
    List<ArtifactVariable> artifactVariables = executionArgs.getArtifactVariables();
    if (isNotEmpty(artifactVariables)) {
      if (isNotEmpty(artifacts)) {
        addArtifactsToRuntimeWorkflowExecution(pipelineExecution, workflowStandardParams, executionArgs, artifacts);
      }
      executionArgs.setArtifactVariables(getMergedArtifactVariableList(pipelineExecution, artifactVariables));
      updateWorkflowExecutionArtifactsAndArtifactVariables(appId, pipelineExecutionId, pipelineExecution.getArtifacts(),
          executionArgs.getArtifacts(), executionArgs.getArtifactVariables());
    }
    // executionArg.artifactVariables has merged artifact variable list (old + new). Consider the artifact for a service
    // cannot be overridden once provided, even from runtime inputs screen.
    addParameterizedArtifactVariableToContext(executionArgs.getArtifactVariables(), workflowStandardParams);
    addArtifactInputsToContext(executionArgs.getArtifactVariables(), workflowStandardParams);

    LinkedList<ContextElement> contextElements = stateExecutionInstance.getContextElements();

    if (featureFlagService.isEnabled(RESOLVE_DEPLOYMENT_TAGS_BEFORE_EXECUTION, pipelineExecution.getAccountId())
        && workflowStandardParams.getWorkflowElement() != null) {
      Map<String, Object> pipelineVars = workflowStandardParams.getWorkflowElement().getVariables();
      Map<String, String> stagePipelineVars = executionArgs.getWorkflowVariables();

      for (Map.Entry<String, String> entry : stagePipelineVars.entrySet()) {
        if (pipelineVars == null) {
          pipelineVars = new HashMap<>();
        }
        pipelineVars.put(entry.getKey(), entry.getValue());
      }
      workflowStandardParams.getWorkflowElement().setVariables(pipelineVars);
    }
    contextElements.push(workflowStandardParams);

    UpdateOperations<StateExecutionInstance> ops =
        wingsPersistence.createUpdateOperations(StateExecutionInstance.class);
    ops.set(StateExecutionInstanceKeys.contextElements, contextElements);
    wingsPersistence.findAndModify(
        wingsPersistence.createQuery(StateExecutionInstance.class).filter("_id", stateExecutionInstance.getUuid()), ops,
        HPersistence.returnNewOptions);

    UpdateOperations<WorkflowExecution> executionUpdates =
        wingsPersistence.createUpdateOperations(WorkflowExecution.class);
    boolean shouldUpdatePipelineExecution = false;
    if (pipelineExecution.getEnvIds() != null) {
      executionUpdates.set(WorkflowExecutionKeys.envIds, pipelineExecution.getEnvIds());
      shouldUpdatePipelineExecution = true;
    }
    if (pipelineExecution.getServiceIds() != null) {
      executionUpdates.set(WorkflowExecutionKeys.serviceIds, pipelineExecution.getServiceIds());
      shouldUpdatePipelineExecution = true;
    }
    if (pipelineExecution.getInfraDefinitionIds() != null) {
      executionUpdates.set(WorkflowExecutionKeys.infraDefinitionIds, pipelineExecution.getInfraDefinitionIds());
      shouldUpdatePipelineExecution = true;
    }

    if (shouldUpdatePipelineExecution) {
      wingsPersistence.findAndModify(
          wingsPersistence.createQuery(WorkflowExecution.class).filter("_id", pipelineExecutionId), executionUpdates,
          HPersistence.returnNewOptions);
    }

    // Replace with WF variables and not pipeline Vars.
    ResponseData responseData = new ContinuePipelineResponseData(wfVariables, null);
    waitNotifyEngine.doneWith(
        StateMachineExecutor.getContinuePipelineWaitId(
            stateExecutionInstance.getPipelineStageElementId(), stateExecutionInstance.getExecutionUuid()),
        responseData);
    return true;
  }

  @VisibleForTesting
  void addArtifactInputsToContext(
      List<ArtifactVariable> artifactVariables, WorkflowStandardParams workflowStandardParams) {
    List<ArtifactInput> artifactInputsFromArtifactVariables =
        artifactVariables.stream()
            .filter(artifactVariable -> artifactVariable.getArtifactInput() != null)
            .map(ArtifactVariable::getArtifactInput)
            .collect(toList());
    if (isEmpty(artifactInputsFromArtifactVariables)) {
      return;
    }
    workflowStandardParams.setArtifactInputs(artifactInputsFromArtifactVariables);
  }

  @VisibleForTesting
  void addParameterizedArtifactVariableToContext(
      List<ArtifactVariable> artifactVariables, WorkflowStandardParams stdParams) {
    List<ArtifactVariable> parameterizedArtifactVariables =
        artifactVariables.stream()
            .filter(artifactVariable -> artifactVariable.getArtifactStreamMetadata() != null)
            .collect(toList());
    if (isEmpty(parameterizedArtifactVariables)) {
      return;
    }
    if (stdParams.getWorkflowElement() != null) {
      if (isNotEmpty(stdParams.getWorkflowElement().getArtifactVariables())) {
        stdParams.getWorkflowElement().getArtifactVariables().addAll(parameterizedArtifactVariables);
      } else {
        stdParams.getWorkflowElement().setArtifactVariables(parameterizedArtifactVariables);
      }
    }
  }

  private List<String> getWFVarNamesFromPipelineVar(
      Map<String, String> pipelineVars, Pipeline pipeline, String pipelineStageElementId) {
    List<String> result = new ArrayList<>();
    if (pipelineVars == null) {
      pipelineVars = new HashMap<>();
    }
    PipelineStageElement stageElement = pipeline.getPipelineStages()
                                            .stream()
                                            .map(stage -> stage.getPipelineStageElements().get(0))
                                            .filter(stageEl -> stageEl.getUuid().equals(pipelineStageElementId))
                                            .filter(stageEl -> stageEl.getProperties().get("workflowId") != null)
                                            .findFirst()
                                            .orElse(null);
    if (stageElement != null) {
      Map<String, String> wfVars = stageElement.getWorkflowVariables();
      for (Entry<String, String> entry : wfVars.entrySet()) {
        if (ExpressionEvaluator.matchesVariablePattern(entry.getValue())) {
          String value = ExpressionEvaluator.getName(entry.getValue());
          if (pipelineVars.containsKey(value)) {
            result.add(entry.getKey());
          }
        }
      }
    }
    return result;
  }

  @VisibleForTesting
  protected Map<String, String> getWFVarFromPipelineVar(Map<String, String> pipelineVars, WorkflowExecution execution,
      Pipeline pipeline, Workflow workflow, String pipelineStageElementId) {
    String appId = pipeline.getAppId();
    Pipeline newPipeline = pipelineService.getPipeline(appId, pipeline.getUuid());
    Map<String, String> pipelineExecWFVars = execution.getExecutionArgs().getWorkflowVariables();
    if (pipelineExecWFVars == null) {
      pipelineExecWFVars = new HashMap<>();
    }
    Map<String, String> mappedWFVars = new HashMap<>();
    if (pipelineVars == null) {
      pipelineVars = new HashMap<>();
    }
    PipelineStageElement stageElement = newPipeline.getPipelineStages()
                                            .stream()
                                            .map(stage -> stage.getPipelineStageElements().get(0))
                                            .filter(stageEl -> stageEl.getUuid().equals(pipelineStageElementId))
                                            .filter(stageEl -> stageEl.getProperties().get("workflowId") != null)
                                            .findFirst()
                                            .orElse(null);
    if (stageElement != null) {
      // handle default value
      for (Variable var : workflow.getOrchestrationWorkflow().getUserVariables()) {
        mappedWFVars.put(var.getName(), var.getValue());
        String value = stageElement.getWorkflowVariables().get(var.getName());
        if (ExpressionEvaluator.matchesVariablePattern(value)) {
          value = ExpressionEvaluator.getName(value);
          if (pipelineExecWFVars.containsKey(value)) {
            mappedWFVars.put(var.getName(), pipelineExecWFVars.get(value));
          }
          if (pipelineVars.containsKey(value)) {
            mappedWFVars.put(var.getName(), pipelineVars.get(value));
          }
        } else {
          mappedWFVars.put(var.getName(), value);
          if (pipelineExecWFVars.containsKey(var.getName())) {
            mappedWFVars.put(var.getName(), pipelineExecWFVars.get(var.getName()));
          }
          if (pipelineVars.containsKey(var.getName())) {
            mappedWFVars.put(var.getName(), pipelineVars.get(var.getName()));
          }
        }
      }
    }
    return mappedWFVars;
  }

  private Map<String, String> validateContinuePipeline(
      String appId, WorkflowExecution pipelineExecution, String pipelineStageElementId, ExecutionArgs executionArgs) {
    validatePipelineExecution(pipelineExecution.getUuid(), pipelineExecution);

    if (featureFlagService.isEnabled(
            SPG_ALLOW_REFRESH_PIPELINE_EXECUTION_BEFORE_CONTINUE_PIPELINE, pipelineExecution.getAccountId())) {
      refreshPipelineExecution(pipelineExecution);
    }

    PipelineStageExecution pipelineStageExecution =
        pipelineExecution.getPipelineExecution()
            .getPipelineStageExecutions()
            .stream()
            .filter(t -> t.getPipelineStageElementId().equals(pipelineStageElementId))
            .findFirst()
            .orElse(null);
    validatePipelineStageExecution(pipelineStageElementId, pipelineStageExecution);

    String pipelineId = pipelineExecution.getWorkflowId();
    Pipeline pipeline = pipelineService.readPipelineWithVariables(appId, pipelineId);
    validateRBAC(appId, pipelineId, pipeline);

    List<PipelineStage> pipelineStages = pipeline.getPipelineStages();
    notNullCheck("Pipeline doesnt contain any stages. PipelineID: " + pipelineId, pipelineStages);

    String workflowId = null;
    RuntimeInputsConfig runtimeInputsConfig = null;
    String envIdInStage = null;
    for (PipelineStage pipelineStage : pipelineStages) {
      PipelineStageElement pipelineStageElement = pipelineStage.getPipelineStageElements().get(0);
      if (pipelineStageElement.getUuid().equals(pipelineStageElementId)
          && pipelineStageElement.getProperties().get(EnvStateKeys.workflowId) != null) {
        if (pipelineStageElement.getProperties().containsKey(EnvStateKeys.envId)) {
          envIdInStage = (String) pipelineStageElement.getProperties().get(EnvStateKeys.envId);
        }
        workflowId = (String) pipelineStageElement.getProperties().get(EnvStateKeys.workflowId);
        runtimeInputsConfig = pipelineStageElement.getRuntimeInputsConfig();
        break;
      }
    }

    notNullCheck("Cannot find workflow associated with given PipelineStage: " + pipelineStageElementId, workflowId);
    notNullCheck("No Runtime Input Vars for the given PipelineStage: " + pipelineStageElementId, runtimeInputsConfig);
    Workflow workflow = workflowService.readWorkflow(appId, workflowId);
    notNullCheck("Cannot find workflow associated with given PipelineStage: " + pipelineStageElementId, workflow);
    notNullCheck("Cannot find workflow associated with given PipelineStage: " + pipelineStageElementId,
        workflow.getOrchestrationWorkflow());

    Map<String, String> wfVariables = getWFVarFromPipelineVar(
        executionArgs.getWorkflowVariables(), pipelineExecution, pipeline, workflow, pipelineStageElementId);
    if (isEmpty(runtimeInputsConfig.getRuntimeInputVariables())) {
      throw new InvalidRequestException(
          "NO Runtime Input Vars for the given PipelineStage: \" + pipelineStageElementId");
    }

    List<Variable> workflowVariables = workflow.getOrchestrationWorkflow().getUserVariables();

    Variable envVarInStage = workflowVariables.stream()
                                 .filter(t -> EntityType.ENVIRONMENT.equals(t.obtainEntityType()))
                                 .findFirst()
                                 .orElse(null);
    List<String> runtimeVarsInStage = runtimeInputsConfig.getRuntimeInputVariables();

    if (envVarInStage != null && runtimeVarsInStage.contains(envVarInStage.getName())) {
      String envValueInStage = wfVariables.get(envVarInStage.getName());
      authService.checkIfUserAllowedToDeployPipelineToEnv(appId, envValueInStage);
    }

    if (featureFlagService.isEnabled(NEW_DEPLOYMENT_FREEZE, pipelineExecution.getAccountId())) {
      if (ExpressionEvaluator.matchesVariablePattern(envIdInStage) && envVarInStage != null) {
        envIdInStage = wfVariables.get(envVarInStage.getName());
      }
      PreDeploymentChecker deploymentFreezeChecker = new DeploymentFreezeChecker(governanceConfigService,
          new DeploymentCtx(appId, envIdInStage != null ? Collections.singletonList(envIdInStage) : emptyList(),
              getPipelineServiceIds(pipeline)),
          environmentService, featureFlagService);
      User user = UserThreadLocal.get();
      // update user permissions when pipeline continues with runtime inputs
      boolean canOverrideFreeze = user != null && checkIfOverrideFreeze();
      pipelineExecution.setCanOverrideFreeze(canOverrideFreeze);
      if (!canOverrideFreeze) {
        deploymentFreezeChecker.check(pipelineExecution.getAccountId());
      }
    }
    List<String> extraVars = new ArrayList<>();
    List<String> runtimeKeys =
        getWFVarNamesFromPipelineVar(executionArgs.getWorkflowVariables(), pipeline, pipelineStageElementId);
    for (String key : runtimeKeys) {
      if (!runtimeVarsInStage.contains(key)) {
        extraVars.add(key);
      }
    }

    if (isNotEmpty(extraVars)) {
      throw new InvalidRequestException(
          "Cannot override value for variables: " + extraVars.toString() + " These are not marked runtime in stage");
    }

    validateRequiredVariables(workflowVariables, wfVariables, runtimeVarsInStage);

    List<Artifact> existingArtifacts = pipelineExecution.getArtifacts();
    List<Artifact> newArtifacts = executionArgs.getArtifacts();
    validateArtifactOverrides(existingArtifacts, newArtifacts);

    updateServiceInfraEnvInPipelineExecution(pipelineExecution, wfVariables, workflowVariables, envIdInStage);
    return wfVariables;
  }

  private void updateServiceInfraEnvInPipelineExecution(WorkflowExecution pipelineExecution,
      Map<String, String> wfVariables, List<Variable> workflowVariables, String envIdInStage) {
    if (envIdInStage != null) {
      if (pipelineExecution.getEnvIds() == null) {
        List<String> envIds = new ArrayList<>();
        envIds.add(envIdInStage);
        pipelineExecution.setEnvIds(envIds);
      } else {
        if (!pipelineExecution.getEnvIds().contains(envIdInStage)) {
          pipelineExecution.getEnvIds().add(envIdInStage);
        }
      }
    }

    updateServiceIdInExecution(pipelineExecution, wfVariables, workflowVariables);

    updateInfraIdInExecution(pipelineExecution, wfVariables, workflowVariables);
  }

  private void updateInfraIdInExecution(
      WorkflowExecution pipelineExecution, Map<String, String> wfVariables, List<Variable> workflowVariables) {
    List<String> infraVariableNames =
        workflowVariables.stream()
            .filter(v -> EntityType.INFRASTRUCTURE_DEFINITION.equals(v.obtainEntityType()))
            .map(Variable::getName)
            .collect(toList());

    List<String> infraIdsFromRuntime = infraVariableNames.stream().map(wfVariables::get).collect(toList());
    List<String> infraIdsSeparated = infraIdsFromRuntime.stream()
                                         .filter(Objects::nonNull)
                                         .flatMap(infraId -> Stream.of(infraId.split(",")))
                                         .collect(toList());

    if (isNotEmpty(infraIdsSeparated)) {
      if (pipelineExecution.getInfraDefinitionIds() == null) {
        pipelineExecution.setInfraDefinitionIds(infraIdsSeparated);
      } else {
        infraIdsSeparated.forEach(infraId -> {
          if (!pipelineExecution.getInfraDefinitionIds().contains(infraId)) {
            pipelineExecution.getInfraDefinitionIds().add(infraId);
          }
        });
      }
    }
  }

  private void updateServiceIdInExecution(
      WorkflowExecution pipelineExecution, Map<String, String> wfVariables, List<Variable> workflowVariables) {
    List<String> serviceVariableNames = workflowVariables.stream()
                                            .filter(v -> EntityType.SERVICE.equals(v.obtainEntityType()))
                                            .map(Variable::getName)
                                            .collect(toList());

    List<String> serviceIdsFromRuntime = serviceVariableNames.stream().map(wfVariables::get).collect(toList());

    if (isNotEmpty(serviceIdsFromRuntime)) {
      if (pipelineExecution.getServiceIds() == null) {
        pipelineExecution.setServiceIds(serviceIdsFromRuntime);
      } else {
        serviceIdsFromRuntime.forEach(serviceId -> {
          if (!pipelineExecution.getServiceIds().contains(serviceId)) {
            pipelineExecution.getServiceIds().add(serviceId);
          }
        });
      }
    }
  }

  private void validateArtifactOverrides(List<Artifact> existingArtifacts, List<Artifact> newArtifacts) {
    if (isEmpty(existingArtifacts) || isEmpty(newArtifacts)) {
      return;
    }
    if (existingArtifacts.stream()
            .map(Artifact::getUuid)
            .collect(toSet())
            .containsAll(newArtifacts.stream().map(Artifact::getUuid).collect(toSet()))) {
      return;
    }
    // Read from DB
    for (Artifact newArtifact : newArtifacts) {
      if (existingArtifacts.stream().anyMatch(
              t -> t.getServiceIds().get(0).equals(newArtifact.getServiceIds().get(0)))) {
        throw new InvalidRequestException(format(
            "Cannot override artifact %s for service, artifact for this service is already present in the pipeline",
            newArtifact.getBuildNo()));
      }
    }
  }

  private void validateRequiredVariables(
      List<Variable> workflowVariables, Map<String, String> runtimeVariableValues, List<String> runtimeVarsInStage) {
    List<Variable> requiredRuntimeWorkflowVars = workflowVariables.stream()
                                                     .filter(Variable::isMandatory)
                                                     .filter(t -> runtimeVarsInStage.contains(t.getName()))
                                                     .collect(toList());
    List<String> missingVars = requiredRuntimeWorkflowVars.stream()
                                   .map(Variable::getName)
                                   .filter(t -> !runtimeVariableValues.containsKey(t))
                                   .collect(toList());
    if (isNotEmpty(missingVars)) {
      throw new InvalidRequestException(
          "Please provide value for required runtime variables: " + missingVars.toString());
    }
  }

  private void validateRBAC(String appId, String pipelineId, Pipeline pipeline) {
    User user = UserThreadLocal.get();
    if (user != null) {
      deploymentAuthHandler.authorizePipelineExecution(appId, pipelineId);
      if (isNotEmpty(pipeline.getEnvIds())) {
        pipeline.getEnvIds().forEach(s -> authService.checkIfUserAllowedToDeployPipelineToEnv(appId, s));
      }
    }
  }

  private void validatePipelineStageExecution(
      String pipelineStageElementId, PipelineStageExecution pipelineStageExecution) {
    if (pipelineStageExecution == null) {
      throw new InvalidRequestException(
          "Cannot continue Pipeline stage, PipelineStageExecution not found for Id: " + pipelineStageElementId);
    }

    if (pipelineStageExecution.getStatus() != PAUSED) {
      throw new InvalidRequestException(
          "Cannot continue Pipeline stage, PipelineStageExecution is not Paused. current status: "
          + pipelineStageExecution.getStatus());
    }

    if (!pipelineStageExecution.getStateType().equals(ENV_STATE.name())
        && !pipelineStageExecution.getStateType().equals(ENV_LOOP_STATE.name())) {
      throw new InvalidRequestException(
          "Cannot continue Pipeline stage, PipelineStageExecution is not a valid workflow state. Statetype = "
          + pipelineStageExecution.getStateType());
    }
  }

  private void validatePipelineExecution(String pipelineExecutionId, WorkflowExecution pipelineExecution) {
    if (pipelineExecution.getPipelineExecution() == null) {
      throw new InvalidRequestException("Cannot continue Pipeline, Not a valid executionId: " + pipelineExecutionId);
    }

    if (!isActiveStatus(pipelineExecution.getStatus())) {
      throw new InvalidRequestException(
          "Cannot continue Pipeline stage, Pipeline is not active anymore. PipelineExecutionId: "
          + pipelineExecutionId);
    }
  }

  public StateExecutionInstance getStateExecutionInstancePipelineStage(
      String appId, String pipelineExecutionId, String pipelineStageElementId) {
    StateExecutionInstance stateExecutionInstances =
        wingsPersistence.createQuery(StateExecutionInstance.class)
            .filter(StateExecutionInstanceKeys.appId, appId)
            .filter(StateExecutionInstanceKeys.executionUuid, pipelineExecutionId)
            .filter(StateExecutionInstanceKeys.pipelineStageElementId, pipelineStageElementId)
            .get();

    if (stateExecutionInstances == null) {
      throw new InvalidRequestException("Couldnt find any StateExecutionInstance fot given executionId: "
          + pipelineExecutionId + " and pipelineStageElementId: " + pipelineStageElementId);
    }
    return stateExecutionInstances;
  }

  @Override
  public boolean runningExecutionsPresent(String appId, String workflowId) {
    List<WorkflowExecution> runningExecutions = wingsPersistence.createQuery(WorkflowExecution.class)
                                                    .filter(WorkflowExecutionKeys.appId, appId)
                                                    .filter(WorkflowExecutionKeys.workflowId, workflowId)
                                                    .field(WorkflowExecutionKeys.status)
                                                    .in(ExecutionStatus.activeStatuses())
                                                    .project(WorkflowExecutionKeys.uuid, true)
                                                    .limit(NO_LIMIT)
                                                    .asList();
    return !isEmpty(runningExecutions);
  }

  @Override
  public CountsByStatuses getBreakdown(String appId, String workflowExecutionId) {
    WorkflowExecution workflowExecution =
        wingsPersistence.getWithAppId(WorkflowExecution.class, appId, workflowExecutionId);
    refreshBreakdown(workflowExecution);
    return workflowExecution.getBreakdown();
  }

  @Override
  public GraphNode getExecutionDetailsForNode(
      String appId, String workflowExecutionId, String stateExecutionInstanceId) {
    StateExecutionInstance stateExecutionInstance =
        wingsPersistence.getWithAppId(StateExecutionInstance.class, appId, stateExecutionInstanceId);
    notNullCheck("The state details are not available", stateExecutionInstance);
    return graphRenderer.convertToNode(stateExecutionInstance);
  }

  @Override
  public List<StateExecutionData> getExecutionHistory(
      String appId, String workflowExecutionId, String stateExecutionInstanceId) {
    StateExecutionInstance stateExecutionInstance =
        wingsPersistence.getWithAppId(StateExecutionInstance.class, appId, stateExecutionInstanceId);
    notNullCheck("The state history is not available", stateExecutionInstance);
    return stateExecutionInstance.getStateExecutionDataHistory();
  }

  @Override
  public int getExecutionInterruptCount(String stateExecutionInstanceId) {
    return (int) wingsPersistence.createQuery(ExecutionInterrupt.class)
        .filter(ExecutionInterruptKeys.stateExecutionInstanceId, stateExecutionInstanceId)
        .count();
  }

  @Override
  public List<StateExecutionInterrupt> getExecutionInterrupts(String appId, String stateExecutionInstanceId) {
    StateExecutionInstance stateExecutionInstance =
        wingsPersistence.getWithAppId(StateExecutionInstance.class, appId, stateExecutionInstanceId);
    notNullCheck("stateExecutionInstance", stateExecutionInstance);

    Map<String, ExecutionInterruptEffect> map = new HashMap<>();
    stateExecutionInstance.getInterruptHistory().forEach(effect -> map.put(effect.getInterruptId(), effect));

    List<StateExecutionInterrupt> interrupts =
        wingsPersistence.createQuery(ExecutionInterrupt.class)
            .filter(ExecutionInterruptKeys.appId, appId)
            .filter(ExecutionInterruptKeys.stateExecutionInstanceId, stateExecutionInstanceId)
            .asList()
            .stream()
            .map(interrupt
                -> StateExecutionInterrupt.builder()
                       .interrupt(interrupt)
                       .tookAffectAt(new Date(interrupt.getCreatedAt()))
                       .build())
            .collect(toList());

    if (isNotEmpty(stateExecutionInstance.getInterruptHistory())) {
      wingsPersistence.createQuery(ExecutionInterrupt.class)
          .filter(ExecutionInterruptKeys.appId, appId)
          .field(ID_KEY)
          .in(map.keySet())
          .asList()
          .forEach(interrupt -> {
            ExecutionInterruptEffect effect = map.get(interrupt.getUuid());
            interrupts.add(
                StateExecutionInterrupt.builder().interrupt(interrupt).tookAffectAt(effect.getTookEffectAt()).build());
          });
    }

    return interrupts;
  }

  @Override
  public List<StateExecutionElement> getExecutionElements(String appId, String stateExecutionInstanceId) {
    StateExecutionInstance stateExecutionInstance =
        wingsPersistence.getWithAppId(StateExecutionInstance.class, appId, stateExecutionInstanceId);
    notNullCheck("stateExecutionInstance", stateExecutionInstance);

    StateExecutionData stateExecutionData = stateExecutionInstance.fetchStateExecutionData();
    notNullCheck("stateExecutionData", stateExecutionData);
    if (!(stateExecutionData instanceof RepeatStateExecutionData)) {
      throw new InvalidRequestException("Request for elements of instance that is not repeated", USER);
    }

    RepeatStateExecutionData repeatStateExecutionData = (RepeatStateExecutionData) stateExecutionData;

    Map<String, StateExecutionElement> elementMap =
        repeatStateExecutionData.getRepeatElements()
            .stream()
            .map(element
                -> StateExecutionElement.builder()
                       .executionContextElementId(element.getUuid())
                       .name(element.getName())
                       .progress(0)
                       .status(STARTING)
                       .build())
            .collect(toMap(StateExecutionElement::getName, identity(), (o1, o2) -> o1));

    StateMachine stateMachine = stateExecutionService.obtainStateMachine(stateExecutionInstance);

    int subStates = (int) stateMachine.getChildStateMachines()
                        .get(stateExecutionInstance.getChildStateMachineId())
                        .getStates()
                        .stream()
                        .filter(t -> !t.getStateType().equals("REPEAT") && !t.getStateType().equals("FORK"))
                        .count();

    @Data
    @NoArgsConstructor
    class Stat {
      String element;
      String prevInstanceId;
      ExecutionStatus status;

      int children;
      List<ExecutionStatus> allStatuses = new ArrayList<>();
    }

    Map<String, Stat> stats = new HashMap<>();
    try (HIterator<StateExecutionInstance> stateExecutionInstances = new HIterator<>(
             wingsPersistence.createQuery(StateExecutionInstance.class)
                 .filter(StateExecutionInstanceKeys.appId, appId)
                 .filter(StateExecutionInstanceKeys.executionUuid, stateExecutionInstance.getExecutionUuid())
                 .filter(StateExecutionInstanceKeys.parentInstanceId, stateExecutionInstanceId)
                 .project(StateExecutionInstanceKeys.uuid, true)
                 .project(StateExecutionInstanceKeys.status, true)
                 .project(StateExecutionInstanceKeys.prevInstanceId, true)
                 .project(StateExecutionInstanceKeys.contextElement, true)
                 .fetch())) {
      while (stateExecutionInstances.hasNext()) {
        StateExecutionInstance instance = stateExecutionInstances.next();
        Stat stat = stats.computeIfAbsent(instance.getUuid(), x -> new Stat());
        stat.setElement(instance.getContextElement().getName());
        stat.setPrevInstanceId(instance.getPrevInstanceId());
        stat.setStatus(instance.getStatus());

        stat.setChildren(stat.getChildren() + 1);
        stat.allStatuses.add(instance.getStatus());

        // update previous aggregates
        while (stat.getPrevInstanceId() != null) {
          Stat previousStat = stats.get(stat.getPrevInstanceId());
          if (previousStat == null) {
            break;
          }
          previousStat.setChildren(stat.getChildren() + 1);
          List<ExecutionStatus> statuses = previousStat.getAllStatuses();
          statuses.clear();
          statuses.add(previousStat.getStatus());
          statuses.addAll(stat.getAllStatuses());
          stat = previousStat;
        }

        if (stat.getElement() != null) {
          StateExecutionElement stateExecutionElement = elementMap.get(stat.getElement());
          elementMap.put(stat.getElement(),
              StateExecutionElement.builder()
                  .executionContextElementId(stateExecutionElement.getExecutionContextElementId())
                  .name(stat.getElement())
                  .progress(100 * stat.getChildren() / subStates)
                  .status(GraphRenderer.aggregateStatus(stat.getAllStatuses()))
                  .build());
        }
      }
    }

    return elementMap.values().stream().collect(toList());
  }

  @Override
  public Map<String, GraphGroup> getNodeSubGraphs(
      String appId, String workflowExecutionId, Map<String, List<String>> selectedNodes) {
    Map<String, GraphGroup> nodeSubGraphs = new HashMap<>();
    for (Entry<String, List<String>> entry : selectedNodes.entrySet()) {
      String repeaterId = entry.getKey();
      List<String> selectedInstances = entry.getValue();
      StateExecutionInstance repeatInstance =
          wingsPersistence.getWithAppId(StateExecutionInstance.class, appId, repeaterId);
      notNullCheck("Couldn't find Instance for Id:" + repeaterId, repeatInstance);
      // Instance Id to its stateExecutionInstanceIds.
      LinkedHashMap<String, Map<String, StateExecutionInstance>> nodesInstancesIdMap = new LinkedHashMap<>();

      // initializing map
      for (String instance : selectedInstances) {
        nodesInstancesIdMap.put(instance, new HashMap<>());
      }

      Query<StateExecutionInstance> query =
          getQueryForNodeSubgraphs(appId, workflowExecutionId, selectedInstances, repeaterId);
      try (HIterator<StateExecutionInstance> stateExecutionInstances = new HIterator<>(query.fetch())) {
        for (StateExecutionInstance stateExecutionInstance : stateExecutionInstances) {
          // for older executions stored in DB
          if (isEmpty(stateExecutionInstance.getSubGraphFilterId())) {
            populateNodeInstanceIdMapOlderExecutions(
                selectedNodes, repeaterId, nodesInstancesIdMap, stateExecutionInstance);
          } else {
            populateNodeInstanceIdMap(nodesInstancesIdMap, stateExecutionInstance);
          }
        }
      }

      nodeSubGraphs.put(repeaterId, graphRenderer.generateNodeSubGraph(nodesInstancesIdMap, repeatInstance));
    }
    return nodeSubGraphs;
  }

  private void populateNodeInstanceIdMap(Map<String, Map<String, StateExecutionInstance>> nodesInstancesIdMap,
      StateExecutionInstance stateExecutionInstance) {
    if (nodesInstancesIdMap.get(stateExecutionInstance.getSubGraphFilterId()) != null) {
      stateExecutionInstance.getStateExecutionMap().entrySet().removeIf(
          entry -> !entry.getKey().equals(stateExecutionInstance.getDisplayName()));
      nodesInstancesIdMap.get(stateExecutionInstance.getSubGraphFilterId())
          .put(stateExecutionInstance.getUuid(), stateExecutionInstance);
    }
  }

  private Query<StateExecutionInstance> getQueryForNodeSubgraphs(
      String appId, String workflowExecutionId, List<String> selectedInstances, String repeaterId) {
    Query<StateExecutionInstance> query = wingsPersistence.createQuery(StateExecutionInstance.class)
                                              .filter(StateExecutionInstanceKeys.appId, appId)
                                              .filter(StateExecutionInstanceKeys.executionUuid, workflowExecutionId)
                                              .filter(StateExecutionInstanceKeys.parentInstanceId, repeaterId)
                                              .project(StateExecutionInstanceKeys.contextElement, true)
                                              .project(StateExecutionInstanceKeys.subGraphFilterId, true)
                                              .project(StateExecutionInstanceKeys.contextTransition, true)
                                              .project(StateExecutionInstanceKeys.dedicatedInterruptCount, true)
                                              .project(StateExecutionInstanceKeys.displayName, true)
                                              .project(StateExecutionInstanceKeys.executionType, true)
                                              .project(StateExecutionInstanceKeys.uuid, true)
                                              .project(StateExecutionInstanceKeys.interruptHistory, true)
                                              .project(StateExecutionInstanceKeys.lastUpdatedAt, true)
                                              .project(StateExecutionInstanceKeys.parentInstanceId, true)
                                              .project(StateExecutionInstanceKeys.prevInstanceId, true)
                                              .project(StateExecutionInstanceKeys.stateExecutionDataHistory, true)
                                              .project(StateExecutionInstanceKeys.stateExecutionMap, true)
                                              .project(StateExecutionInstanceKeys.stateName, true)
                                              .project(StateExecutionInstanceKeys.stateType, true)
                                              .project(StateExecutionInstanceKeys.status, true)
                                              .project(StateExecutionInstanceKeys.hasInspection, true)
                                              .project(StateExecutionInstanceKeys.appId, true);

    // SubGraphFilterId is the instance (host element) Id.
    // For older execution this will be null.
    CriteriaContainer nullCriteria = query.criteria(StateExecutionInstanceKeys.subGraphFilterId).doesNotExist();
    CriteriaContainer existsCriteria =
        query.criteria(StateExecutionInstanceKeys.subGraphFilterId).in(selectedInstances);
    query.or(nullCriteria, existsCriteria);
    return query;
  }

  private void populateNodeInstanceIdMapOlderExecutions(Map<String, List<String>> selectedNodes, String repeaterId,
      Map<String, Map<String, StateExecutionInstance>> nodesInstancesIdMap,
      StateExecutionInstance stateExecutionInstance) {
    if (stateExecutionInstance.getContextElement() instanceof InstanceElement) {
      InstanceElement contextInstanceElement = (InstanceElement) stateExecutionInstance.getContextElement();
      if (selectedNodes.get(repeaterId).contains(contextInstanceElement.getUuid())) {
        stateExecutionInstance.getStateExecutionMap().entrySet().removeIf(
            entry -> !entry.getKey().equals(stateExecutionInstance.getDisplayName()));
        if (nodesInstancesIdMap.get(contextInstanceElement.getUuid()) != null) {
          nodesInstancesIdMap.get(contextInstanceElement.getUuid())
              .put(stateExecutionInstance.getUuid(), stateExecutionInstance);
        }
      }
    }
  }

  @Override
  public StateExecutionInstance getStateExecutionData(String appId, String stateExecutionInstanceId) {
    return wingsPersistence.getWithAppId(StateExecutionInstance.class, appId, stateExecutionInstanceId);
  }

  @Override
  public List<StateExecutionInstance> getStateExecutionData(String appId, String executionUuid, String serviceId,
      String infraMappingId, Optional<String> infrastructureDefinitionId, StateType stateType, String stateName) {
    log.info(
        "for execution {} looking for following appId: {} executionUuid: {} , stateType: {}, displayName: {}, contextElement.serviceElement.uuid:  {}, contextElement.infraMappingId: {}",
        executionUuid, appId, executionUuid, stateType, stateName, serviceId, infraMappingId);
    Query<StateExecutionInstance> stateExecutionInstanceQuery =
        wingsPersistence.createQuery(StateExecutionInstance.class, excludeAuthority)
            .disableValidation()
            .filter(StateExecutionInstanceKeys.appId, appId)
            .filter(StateExecutionInstanceKeys.executionUuid, executionUuid)
            .filter(StateExecutionInstanceKeys.stateType, stateType)
            .filter(StateExecutionInstanceKeys.displayName, stateName)
            .filter("contextElement.serviceElement.uuid", serviceId);
    if (infrastructureDefinitionId.isPresent()) {
      stateExecutionInstanceQuery.filter("contextElement.infraDefinitionId", infrastructureDefinitionId.get());
    } else {
      // once all the infra mappings are moved to infra mapping definition, we can get rid of this else block
      stateExecutionInstanceQuery.filter("contextElement.infraMappingId", infraMappingId);
    }
    List<StateExecutionInstance> rv = new ArrayList<>();
    try (HIterator<StateExecutionInstance> stateExecutionInstances =
             new HIterator<>(stateExecutionInstanceQuery.fetch())) {
      while (stateExecutionInstances.hasNext()) {
        rv.add(stateExecutionInstances.next());
      }
    }

    return rv;
  }

  private void refreshSummaries(WorkflowExecution workflowExecution) {
    if (workflowExecution.getServiceExecutionSummaries() != null) {
      return;
    }
    List<ElementExecutionSummary> serviceExecutionSummaries = new ArrayList<>();
    // TODO : version should also be captured as part of the WorkflowExecution
    Workflow workflow = workflowService.readWorkflow(workflowExecution.getAppId(), workflowExecution.getWorkflowId());
    if (workflow != null && workflow.getOrchestrationWorkflow() != null) {
      List<Service> services = getResolvedServices(workflow, workflowExecution);
      String envId = workflowService.resolveEnvironmentId(workflow,
          workflowExecution.getExecutionArgs() != null ? workflowExecution.getExecutionArgs().getWorkflowVariables()
                                                       : null);

      List<InfrastructureDefinition> infrastructureDefinitions = null;
      infrastructureDefinitions = getResolvedInfraDefinitions(workflow, workflowExecution, envId);

      if (services != null) {
        List<InfrastructureDefinition> finalInfrastructureDefinitions = infrastructureDefinitions;
        services.forEach(service -> {
          ServiceElement serviceElement =
              ServiceElement.builder().uuid(service.getUuid()).name(service.getName()).build();
          ElementExecutionSummary elementSummary =
              anElementExecutionSummary().withContextElement(serviceElement).withStatus(ExecutionStatus.QUEUED).build();

          List<InfraDefinitionSummary> infraDefinitionSummaries = new ArrayList<>();
          if (finalInfrastructureDefinitions != null) {
            for (InfrastructureDefinition infrastructureDefinition : finalInfrastructureDefinitions) {
              infraDefinitionSummaries.add(
                  InfraDefinitionSummary.builder()
                      .infraDefinitionId(infrastructureDefinition.getUuid())
                      .deploymentType(infrastructureDefinition.getDeploymentType())
                      .displayName(infrastructureDefinition.getName())
                      .cloudProviderType(infrastructureDefinition.getInfrastructure().getCloudProviderType())
                      .cloudProviderName(
                          infrastructureDefinitionService.cloudProviderNameForDefinition(infrastructureDefinition))
                      .build());
            }
            elementSummary.setInfraDefinitionSummaries(infraDefinitionSummaries);
          }
          serviceExecutionSummaries.add(elementSummary);
        });
      }
    }
    Map<String, ElementExecutionSummary> serviceExecutionSummaryMap =
        serviceExecutionSummaries.stream().collect(toMap(summary -> summary.getContextElement().getUuid(), identity()));

    populateServiceSummary(serviceExecutionSummaryMap, workflowExecution);

    if (!serviceExecutionSummaryMap.isEmpty()) {
      Collections.sort(serviceExecutionSummaries, ElementExecutionSummary.startTsComparator);
      workflowExecution.setServiceExecutionSummaries(serviceExecutionSummaries);
      if (ExecutionStatus.isFinalStatus(workflowExecution.getStatus())) {
        Map<String, Object> fieldsToUpdate = new HashMap<>();
        Optional<InstanceElement> optionalInstanceElement =
            serviceExecutionSummaries.stream()
                .map(ElementExecutionSummary::getInstanceStatusSummaries)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(InstanceStatusSummary::getInstanceElement)
                .filter(Objects::nonNull)
                .findAny();
        fieldsToUpdate.put(
            WorkflowExecutionKeys.serviceExecutionSummaries, workflowExecution.getServiceExecutionSummaries());
        if (optionalInstanceElement.isPresent() && optionalInstanceElement.get().getUuid() != null) {
          workflowExecution.setDeployment(true);
          fieldsToUpdate.put(WorkflowExecutionKeys.deployment, workflowExecution.getDeployment());
        }
        wingsPersistence.updateFields(WorkflowExecution.class, workflowExecution.getUuid(), fieldsToUpdate);
      }
    }
  }

  private List<Service> getResolvedServices(Workflow workflow, WorkflowExecution workflowExecution) {
    ExecutionArgs executionArgs = workflowExecution.getExecutionArgs();
    Map<String, String> workflowVariables = executionArgs != null ? executionArgs.getWorkflowVariables() : null;
    return workflowService.getResolvedServices(workflow, workflowVariables);
  }

  @Override
  public List<InfrastructureMapping> getResolvedInfraMappings(Workflow workflow, WorkflowExecution workflowExecution) {
    Map<String, String> workflowVariables = workflowExecution.getExecutionArgs() != null
        ? workflowExecution.getExecutionArgs().getWorkflowVariables()
        : null;
    return workflowService.getResolvedInfraMappings(workflow, workflowVariables);
  }

  @Override
  public List<InfrastructureDefinition> getResolvedInfraDefinitions(
      Workflow workflow, WorkflowExecution workflowExecution, String envId) {
    Map<String, String> workflowVariables = workflowExecution.getExecutionArgs() != null
        ? workflowExecution.getExecutionArgs().getWorkflowVariables()
        : null;
    return workflowService.getResolvedInfraDefinitions(workflow, workflowVariables, envId);
  }

  private void populateServiceSummary(
      Map<String, ElementExecutionSummary> serviceSummaryMap, WorkflowExecution workflowExecution) {
    PageRequest<StateExecutionInstance> pageRequest =
        aPageRequest()
            .withLimit(UNLIMITED)
            .addFilter(StateExecutionInstanceKeys.appId, EQ, workflowExecution.getAppId())
            .addFilter(StateExecutionInstanceKeys.executionUuid, EQ, workflowExecution.getUuid())
            .addFilter(StateExecutionInstanceKeys.stateType, IN, StateType.REPEAT.name(), StateType.FORK.name(),
                StateType.SUB_WORKFLOW.name(), StateType.PHASE.name(), PHASE_STEP.name())
            .addFilter(StateExecutionInstanceKeys.parentInstanceId, NOT_EXISTS)
            .addOrder(StateExecutionInstanceKeys.createdAt, OrderType.ASC)
            .build();

    PageResponse<StateExecutionInstance> pageResponse =
        wingsPersistence.query(StateExecutionInstance.class, pageRequest);

    if (isEmpty(pageResponse)) {
      return;
    }

    for (StateExecutionInstance stateExecutionInstance : pageResponse.getResponse()) {
      if (!(stateExecutionInstance.fetchStateExecutionData() instanceof ElementStateExecutionData)) {
        continue;
      }
      if (stateExecutionInstance.isRollback()
          && !checkIfOnDemand(workflowExecution.getAppId(), workflowExecution.getUuid())) {
        continue;
      }

      ElementStateExecutionData elementStateExecutionData =
          (ElementStateExecutionData) stateExecutionInstance.fetchStateExecutionData();
      if (isEmpty(elementStateExecutionData.getElementStatusSummary())) {
        continue;
      }
      for (ElementExecutionSummary summary : elementStateExecutionData.getElementStatusSummary()) {
        ServiceElement serviceElement = getServiceElement(summary.getContextElement());
        if (serviceElement == null) {
          continue;
        }
        ElementExecutionSummary serviceSummary = serviceSummaryMap.get(serviceElement.getUuid());
        if (serviceSummary == null) {
          serviceSummary =
              anElementExecutionSummary().withContextElement(serviceElement).withStatus(ExecutionStatus.QUEUED).build();
          serviceSummaryMap.put(serviceElement.getUuid(), serviceSummary);
        }
        if (serviceSummary.getStartTs() == null
            || (summary.getStartTs() != null && serviceSummary.getStartTs() > summary.getStartTs())) {
          serviceSummary.setStartTs(summary.getStartTs());
        }
        if (serviceSummary.getEndTs() == null
            || (summary.getEndTs() != null && serviceSummary.getEndTs() < summary.getEndTs())) {
          serviceSummary.setEndTs(summary.getEndTs());
        }
        if (serviceSummary.getInstanceStatusSummaries() == null) {
          serviceSummary.setInstanceStatusSummaries(new ArrayList<>());
        }
        if (summary.getInstanceStatusSummaries() != null) {
          serviceSummary.getInstanceStatusSummaries().addAll(summary.getInstanceStatusSummaries());
        }
        serviceSummary.setStatus(summary.getStatus());
      }
    }
  }

  private ServiceElement getServiceElement(ContextElement contextElement) {
    if (contextElement == null) {
      return null;
    }
    ContextElementType elementType = contextElement.getElementType();
    switch (elementType) {
      case SERVICE: {
        return (ServiceElement) contextElement;
      }
      case SERVICE_TEMPLATE: {
        return ((ServiceTemplateElement) contextElement).getServiceElement();
      }
      case INSTANCE: {
        return ((InstanceElement) contextElement).getServiceTemplateElement().getServiceElement();
      }
      case PARAM: {
        if (PhaseElement.PHASE_PARAM.equals(contextElement.getName())) {
          return ((PhaseElement) contextElement).getServiceElement();
        }
        break;
      }
      default:
        unhandled(elementType);
    }
    return null;
  }

  private void refreshBreakdown(WorkflowExecution workflowExecution) {
    if (ExecutionStatus.isFinalStatus(workflowExecution.getStatus()) && workflowExecution.getBreakdown() != null) {
      return;
    }
    CountsByStatuses breakdown;
    int total;

    if (workflowExecution.getOrchestrationType() == OrchestrationWorkflowType.ROLLING
        && !workflowServiceHelper.isExecutionForK8sV2Service(workflowExecution)) {
      log.info("Calculating the breakdown for workflowExecutionId {} and workflowId {} ", workflowExecution.getUuid(),
          workflowExecution.getWorkflowId());
      total = workflowExecution.getTotal();
      if (total == 0) {
        total = (int) refreshTotal(workflowExecution);
      }
      breakdown = getBreakdownFromPhases(workflowExecution);
      breakdown.setQueued(total - (breakdown.getFailed() + breakdown.getSuccess() + breakdown.getInprogress()));
    } else {
      StateMachine sm = obtainStateMachine(workflowExecution);
      if (sm == null) {
        return;
      }

      Map<String, ExecutionStatus> stateExecutionStatuses = new HashMap<>();
      try (HIterator<StateExecutionInstance> iterator =
               new HIterator<>(wingsPersistence.createQuery(StateExecutionInstance.class)
                                   .filter(StateExecutionInstanceKeys.appId, workflowExecution.getAppId())
                                   .filter(StateExecutionInstanceKeys.executionUuid, workflowExecution.getUuid())
                                   .project(StateExecutionInstanceKeys.contextElement, true)
                                   .project(StateExecutionInstanceKeys.displayName, true)
                                   .project(StateExecutionInstanceKeys.uuid, true)
                                   .project(StateExecutionInstanceKeys.parentInstanceId, true)
                                   .project(StateExecutionInstanceKeys.status, true)
                                   .fetch())) {
        stateMachineExecutionSimulator.prepareStateExecutionInstanceMap(iterator, stateExecutionStatuses);
      }

      breakdown = stateMachineExecutionSimulator.getStatusBreakdown(
          workflowExecution.getAppId(), workflowExecution.getEnvId(), sm, stateExecutionStatuses);
      total = breakdown.getFailed() + breakdown.getSuccess() + breakdown.getInprogress() + breakdown.getQueued();
    }

    workflowExecution.setBreakdown(breakdown);
    workflowExecution.setTotal(total);
    log.info("Got the breakdown status: {}, breakdown: {}", workflowExecution.getStatus(), breakdown);

    if (ExecutionStatus.isFinalStatus(workflowExecution.getStatus())) {
      log.info(
          "Set the breakdown of the completed status: {}, breakdown: {}", workflowExecution.getStatus(), breakdown);

      Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                           .filter(WorkflowExecutionKeys.appId, workflowExecution.getAppId())
                                           .filter(ID_KEY, workflowExecution.getUuid());

      UpdateOperations<WorkflowExecution> updateOps = wingsPersistence.createUpdateOperations(WorkflowExecution.class);

      try {
        updateOps.set("breakdown", breakdown).set("total", total);
        UpdateResults updated = wingsPersistence.update(query, updateOps);
        log.info("Updated : {} row", updated.getWriteResult().getN());
      } catch (Exception e) {
        log.error("Error occurred while updating with breakdown summary", e);
      }
    }
  }

  private CountsByStatuses getBreakdownFromPhases(WorkflowExecution workflowExecution) {
    CountsByStatuses breakdown = new CountsByStatuses();

    List<StateExecutionInstance> allStateExecutionInstances =
        wingsPersistence.createQuery(StateExecutionInstance.class)
            .filter(StateExecutionInstanceKeys.appId, workflowExecution.getAppId())
            .filter(StateExecutionInstanceKeys.executionUuid, workflowExecution.getUuid())
            .filter(StateExecutionInstanceKeys.stateType, PHASE.name())
            .filter(StateExecutionInstanceKeys.rollback, workflowExecution.isOnDemandRollback())
            .field(StateExecutionInstanceKeys.createdAt)
            .greaterThanOrEq(workflowExecution.getCreatedAt())
            .asList();

    if (isEmpty(allStateExecutionInstances)) {
      breakdown.setSuccess(0);
    }
    for (StateExecutionInstance stateExecutionInstance : allStateExecutionInstances) {
      StateExecutionData stateExecutionData = stateExecutionInstance.fetchStateExecutionData();
      if (!(stateExecutionData instanceof PhaseExecutionData)) {
        continue;
      }
      PhaseExecutionData phaseExecutionData = (PhaseExecutionData) stateExecutionData;
      List<ElementExecutionSummary> elementStatusSummary = phaseExecutionData.getElementStatusSummary();
      if (isEmpty(elementStatusSummary)) {
        continue;
      }
      for (ElementExecutionSummary elementExecutionSummary : elementStatusSummary) {
        if (elementExecutionSummary == null || elementExecutionSummary.getInstanceStatusSummaries() == null) {
          continue;
        }
        for (InstanceStatusSummary instanceStatusSummary : elementExecutionSummary.getInstanceStatusSummaries()) {
          switch (instanceStatusSummary.getStatus()) {
            case SUCCESS: {
              breakdown.setSuccess(breakdown.getSuccess() + 1);
              break;
            }
            case ERROR:
            case FAILED: {
              breakdown.setFailed(breakdown.getFailed() + 1);
              break;
            }
            case STARTING:
            case RUNNING: {
              breakdown.setInprogress(breakdown.getInprogress() + 1);
              break;
            }
            default: {
              breakdown.setQueued(breakdown.getQueued() + 1);
              break;
            }
          }
        }
      }
    }
    return breakdown;
  }

  private long refreshTotal(WorkflowExecution workflowExecution) {
    Workflow workflow = workflowService.readWorkflow(workflowExecution.getAppId(), workflowExecution.getWorkflowId());
    if (workflow == null || workflow.getOrchestrationWorkflow() == null) {
      log.info("Workflow was deleted. Skipping the refresh total");
      return 0;
    }

    List<String> resolvedInfraMappingIds;
    resolvedInfraMappingIds = workflowExecution.getInfraMappingIds();
    if (isEmpty(resolvedInfraMappingIds)) {
      return 0;
    }
    try {
      return hostService.getHostsCountByInfraMappingIds(workflow.getAppId(), resolvedInfraMappingIds);
    } catch (Exception e) {
      log.error(
          "Error occurred while calculating Refresh total for workflow execution {}", workflowExecution.getUuid(), e);
    }

    return 0;
  }

  // @TODO_PCF
  @Override
  public List<ElementExecutionSummary> getElementsSummary(
      String appId, String executionUuid, String parentStateExecutionInstanceId) {
    List<StateExecutionInstance> allStateExecutionInstances =
        wingsPersistence.createQuery(StateExecutionInstance.class)
            .filter(StateExecutionInstanceKeys.appId, appId)
            .filter(StateExecutionInstanceKeys.executionUuid, executionUuid)
            .filter(StateExecutionInstanceKeys.parentInstanceId, parentStateExecutionInstanceId)
            .order(Sort.ascending(StateExecutionInstanceKeys.createdAt))
            .project(StateExecutionInstanceKeys.contextElements, false)
            .asList();

    if (isEmpty(allStateExecutionInstances)) {
      return null;
    }

    List<StateExecutionInstance> contextTransitionInstances =
        allStateExecutionInstances.stream().filter(StateExecutionInstance::isContextTransition).collect(toList());
    Map<String, StateExecutionInstance> prevInstanceIdMap =
        allStateExecutionInstances.stream()
            .filter(instance -> instance.getPrevInstanceId() != null)
            .collect(toMap(StateExecutionInstance::getPrevInstanceId, identity()));

    List<ElementExecutionSummary> elementExecutionSummaries = new ArrayList<>();
    for (StateExecutionInstance stateExecutionInstance : contextTransitionInstances) {
      ContextElement contextElement = stateExecutionInstance.getContextElement();
      ElementExecutionSummary elementExecutionSummary = anElementExecutionSummary()
                                                            .withContextElement(contextElement)
                                                            .withStartTs(stateExecutionInstance.getStartTs())
                                                            .build();

      List<InstanceStatusSummary> instanceStatusSummaries = new ArrayList<>();

      StateExecutionInstance last = stateExecutionInstance;
      for (StateExecutionInstance next = stateExecutionInstance; next != null;
           next = prevInstanceIdMap.get(next.getUuid())) {
        StateType nextStateType = StateType.valueOf(next.getStateType());
        if (next.fetchStateExecutionData() instanceof SkipStateExecutionData) {
          instanceStatusSummaries.addAll(new ArrayList<>());
        } else if ((nextStateType == StateType.REPEAT || nextStateType == StateType.FORK
                       || nextStateType == StateType.PHASE || nextStateType == PHASE_STEP
                       || nextStateType == StateType.SUB_WORKFLOW)
            && next.fetchStateExecutionData() instanceof ElementStateExecutionData) {
          ElementStateExecutionData elementStateExecutionData =
              (ElementStateExecutionData) next.fetchStateExecutionData();
          instanceStatusSummaries.addAll(elementStateExecutionData.getElementStatusSummary()
                                             .stream()
                                             .filter(e -> e.getInstanceStatusSummaries() != null)
                                             .flatMap(l -> l.getInstanceStatusSummaries().stream())
                                             .collect(toList()));
        } else if ((nextStateType == StateType.ECS_SERVICE_DEPLOY || nextStateType == StateType.KUBERNETES_DEPLOY
                       || nextStateType == StateType.AWS_CODEDEPLOY_STATE)
            && next.fetchStateExecutionData() instanceof CommandStateExecutionData) {
          CommandStateExecutionData commandStateExecutionData =
              (CommandStateExecutionData) next.fetchStateExecutionData();
          instanceStatusSummaries.addAll(commandStateExecutionData.getNewInstanceStatusSummaries());
        } else if (isAsgAmiDeployment(nextStateType)
            && next.fetchStateExecutionData() instanceof AwsAmiDeployStateExecutionData) {
          AwsAmiDeployStateExecutionData awsAmiDeployStateExecutionData =
              (AwsAmiDeployStateExecutionData) next.fetchStateExecutionData();
          instanceStatusSummaries.addAll(awsAmiDeployStateExecutionData.getNewInstanceStatusSummaries());
        } else if (nextStateType == StateType.SPOTINST_DEPLOY
            && next.fetchStateExecutionData() instanceof SpotInstDeployStateExecutionData) {
          SpotInstDeployStateExecutionData spotInstDeployStateExecutionData =
              (SpotInstDeployStateExecutionData) next.fetchStateExecutionData();
          instanceStatusSummaries.addAll(spotInstDeployStateExecutionData.getNewInstanceStatusSummaries());
        } else if (nextStateType == StateType.HELM_DEPLOY) {
          StateExecutionData stateExecutionData = next.fetchStateExecutionData();
          if (stateExecutionData instanceof HelmDeployStateExecutionData) {
            HelmDeployStateExecutionData helmDeployStateExecutionData =
                (HelmDeployStateExecutionData) stateExecutionData;
            if (isNotEmpty(helmDeployStateExecutionData.getNewInstanceStatusSummaries())) {
              instanceStatusSummaries.addAll(helmDeployStateExecutionData.getNewInstanceStatusSummaries());
            }
          }
        } else if (nextStateType == StateType.AZURE_VMSS_DEPLOY
            && next.fetchStateExecutionData() instanceof AzureVMSSDeployStateExecutionData) {
          AzureVMSSDeployStateExecutionData azureVMSSDeployStateExecutionData =
              (AzureVMSSDeployStateExecutionData) next.fetchStateExecutionData();
          instanceStatusSummaries.addAll(azureVMSSDeployStateExecutionData.getNewInstanceStatusSummaries());
        } else if (nextStateType == StateType.KUBERNETES_STEADY_STATE_CHECK) {
          KubernetesSteadyStateCheckExecutionData kubernetesSteadyStateCheckExecutionData =
              (KubernetesSteadyStateCheckExecutionData) next.fetchStateExecutionData();
          if (isNotEmpty(kubernetesSteadyStateCheckExecutionData.getNewInstanceStatusSummaries())) {
            instanceStatusSummaries.addAll(kubernetesSteadyStateCheckExecutionData.getNewInstanceStatusSummaries());
          }
        } else if (nextStateType == StateType.K8S_DEPLOYMENT_ROLLING || nextStateType == StateType.K8S_CANARY_DEPLOY
            || nextStateType == StateType.K8S_BLUE_GREEN_DEPLOY || nextStateType == StateType.K8S_SCALE
            || nextStateType == StateType.K8S_DEPLOYMENT_ROLLING_ROLLBACK) {
          StateExecutionData stateExecutionData = next.fetchStateExecutionData();
          if (stateExecutionData instanceof K8sStateExecutionData) {
            K8sStateExecutionData k8sStateExecutionData = (K8sStateExecutionData) stateExecutionData;
            if (isNotEmpty(k8sStateExecutionData.getNewInstanceStatusSummaries())) {
              instanceStatusSummaries.addAll(k8sStateExecutionData.getNewInstanceStatusSummaries());
            }
          }
        } else if (nextStateType == PCF_RESIZE) {
          PcfDeployStateExecutionData pcfDeployStateExecutionData =
              (PcfDeployStateExecutionData) next.fetchStateExecutionData();
          if (isNotEmpty(pcfDeployStateExecutionData.getNewInstanceStatusSummaries())) {
            instanceStatusSummaries.addAll(pcfDeployStateExecutionData.getNewInstanceStatusSummaries());
          }
        } else if (nextStateType == CUSTOM_DEPLOYMENT_FETCH_INSTANCES) {
          StateExecutionData stateExecutionData = next.fetchStateExecutionData();
          if (stateExecutionData instanceof InstanceFetchStateExecutionData) {
            InstanceFetchStateExecutionData instanceFetchStateExecutionData =
                (InstanceFetchStateExecutionData) stateExecutionData;
            if (isNotEmpty(instanceFetchStateExecutionData.getNewInstanceStatusSummaries())) {
              instanceStatusSummaries.addAll(instanceFetchStateExecutionData.getNewInstanceStatusSummaries());
            }
          }
        } else if (nextStateType == AZURE_WEBAPP_SLOT_SETUP
            && next.fetchStateExecutionData() instanceof AzureAppServiceSlotSetupExecutionData) {
          AzureAppServiceSlotSetupExecutionData appServiceSetupStateExecutionData =
              (AzureAppServiceSlotSetupExecutionData) next.fetchStateExecutionData();
          if (isNotEmpty(appServiceSetupStateExecutionData.getNewInstanceStatusSummaries())) {
            instanceStatusSummaries.addAll(appServiceSetupStateExecutionData.getNewInstanceStatusSummaries());
          }
        }
        last = next;
      }

      if (elementExecutionSummary.getEndTs() == null || elementExecutionSummary.getEndTs() < last.getEndTs()) {
        elementExecutionSummary.setEndTs(last.getEndTs());
      }
      if (contextElement != null && contextElement.getElementType() == ContextElementType.INSTANCE) {
        instanceStatusSummaries.add(anInstanceStatusSummary()
                                        .withInstanceElement((InstanceElement) contextElement.cloneMin())
                                        .withStatus(last.getStatus())
                                        .build());
      }

      instanceStatusSummaries = instanceStatusSummaries.stream()
                                    .filter(instanceStatusSummary -> instanceStatusSummary.getInstanceElement() != null)
                                    .collect(toList());
      instanceStatusSummaries =
          instanceStatusSummaries.stream()
              .filter(distinctByKey(instanceStatusSummary -> instanceStatusSummary.getInstanceElement().getUuid()))
              .collect(toList());

      elementExecutionSummary.setStatus(last.getStatus());
      elementExecutionSummary.setInstanceStatusSummaries(instanceStatusSummaries);
      elementExecutionSummaries.add(elementExecutionSummary);
    }

    return elementExecutionSummaries;
  }

  private boolean isAsgAmiDeployment(StateType nextStateType) {
    return nextStateType == StateType.AWS_AMI_SERVICE_DEPLOY
        || nextStateType == StateType.ASG_AMI_SERVICE_ALB_SHIFT_DEPLOY;
  }

  @Override
  public PhaseExecutionSummary getPhaseExecutionSummary(
      String appId, String executionUuid, String stateExecutionInstanceId) {
    List<StateExecutionInstance> allStateExecutionInstances =
        wingsPersistence.createQuery(StateExecutionInstance.class)
            .filter(StateExecutionInstanceKeys.appId, appId)
            .filter(StateExecutionInstanceKeys.executionUuid, executionUuid)
            .filter(StateExecutionInstanceKeys.parentInstanceId, stateExecutionInstanceId)
            .filter(StateExecutionInstanceKeys.stateType, PHASE_STEP.name())
            .project(StateExecutionInstanceKeys.contextElement, true)
            .project(StateExecutionInstanceKeys.displayName, true)
            .project(StateExecutionInstanceKeys.uuid, true)
            .project(StateExecutionInstanceKeys.parentInstanceId, true)
            .project(StateExecutionInstanceKeys.stateExecutionMap, true)
            .project(StateExecutionInstanceKeys.stateType, true)
            .project(StateExecutionInstanceKeys.status, true)
            .asList();

    PhaseExecutionSummary phaseExecutionSummary = new PhaseExecutionSummary();
    if (isEmpty(allStateExecutionInstances)) {
      return null;
    }

    allStateExecutionInstances.forEach(instance -> {
      StateExecutionData stateExecutionData = instance.fetchStateExecutionData();
      if (stateExecutionData instanceof PhaseStepExecutionData) {
        PhaseStepExecutionData phaseStepExecutionData = (PhaseStepExecutionData) stateExecutionData;
        phaseExecutionSummary.getPhaseStepExecutionSummaryMap().put(
            instance.getDisplayName(), phaseStepExecutionData.getPhaseStepExecutionSummary());
      }
    });

    return phaseExecutionSummary;
  }

  @Override
  public PhaseStepExecutionSummary getPhaseStepExecutionSummary(
      String appId, String executionUuid, String stateExecutionInstanceId) {
    PhaseStepExecutionSummary phaseStepExecutionSummary = new PhaseStepExecutionSummary();
    List<StepExecutionSummary> stepExecutionSummaryList = phaseStepExecutionSummary.getStepExecutionSummaryList();

    List<String> parentInstanceIds = new ArrayList<>();
    parentInstanceIds.add(stateExecutionInstanceId);

    while (isNotEmpty(parentInstanceIds)) {
      try (HIterator<StateExecutionInstance> iterator =
               new HIterator<>(wingsPersistence.createQuery(StateExecutionInstance.class)
                                   .filter(StateExecutionInstanceKeys.appId, appId)
                                   .filter(StateExecutionInstanceKeys.executionUuid, executionUuid)
                                   .field(StateExecutionInstanceKeys.parentInstanceId)
                                   .in(parentInstanceIds)
                                   .project(StateExecutionInstanceKeys.contextElement, true)
                                   .project(StateExecutionInstanceKeys.displayName, true)
                                   .project(StateExecutionInstanceKeys.uuid, true)
                                   .project(StateExecutionInstanceKeys.parentInstanceId, true)
                                   .project(StateExecutionInstanceKeys.stateExecutionMap, true)
                                   .project(StateExecutionInstanceKeys.stateType, true)
                                   .project(StateExecutionInstanceKeys.status, true)
                                   .fetch())) {
        if (!iterator.hasNext()) {
          return null;
        }

        parentInstanceIds.clear();
        while (iterator.hasNext()) {
          StateExecutionInstance instance = iterator.next();
          if (StateType.REPEAT.name().equals(instance.getStateType())
              || StateType.FORK.name().equals(instance.getStateType())) {
            parentInstanceIds.add(instance.getUuid());
          } else {
            stepExecutionSummaryList.add(instance.fetchStateExecutionData().getStepExecutionSummary());
          }
        }
      }
    }

    return phaseStepExecutionSummary;
  }

  public <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Map<Object, Boolean> seen = new ConcurrentHashMap<>();
    return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
  }

  @Override
  public List<HelmChart> getManifestsCollected(String appId, String executionUuid) {
    List<StateExecutionInstance> allStateExecutionInstances =
        wingsPersistence.createQuery(StateExecutionInstance.class)
            .filter(StateExecutionInstanceKeys.appId, appId)
            .filter(StateExecutionInstanceKeys.executionUuid, executionUuid)
            .filter(StateExecutionInstanceKeys.stateType, ARTIFACT_COLLECTION.name())
            .filter(StateExecutionInstanceKeys.status, SUCCESS)
            .asList();

    if (isEmpty(allStateExecutionInstances)) {
      return null;
    }

    List<HelmChart> helmCharts = new ArrayList<>();
    allStateExecutionInstances.forEach(stateExecutionInstance -> {
      if (!(stateExecutionInstance.fetchStateExecutionData() instanceof AppManifestCollectionExecutionData)) {
        return;
      }

      AppManifestCollectionExecutionData executionData =
          (AppManifestCollectionExecutionData) stateExecutionInstance.fetchStateExecutionData();
      if (EmptyPredicate.isNotEmpty(executionData.getChartId())) {
        HelmChart helmChart = helmChartService.get(appId, executionData.getChartId());
        if (helmChart != null) {
          helmCharts.add(helmChart);
        } else {
          log.warn("StateExecutionData has helm chart id, but helm chart doesn't exist in database. Please check!");
        }
      }
    });
    return helmCharts;
  }

  @Override
  public List<Artifact> getArtifactsCollected(String appId, String executionUuid) {
    List<StateExecutionInstance> allStateExecutionInstances =
        wingsPersistence.createQuery(StateExecutionInstance.class)
            .filter(StateExecutionInstanceKeys.appId, appId)
            .filter(StateExecutionInstanceKeys.executionUuid, executionUuid)
            .filter(StateExecutionInstanceKeys.stateType, ARTIFACT_COLLECTION.name())
            .filter(StateExecutionInstanceKeys.status, SUCCESS)
            .asList();

    if (isEmpty(allStateExecutionInstances)) {
      return null;
    }

    List<Artifact> artifacts = new ArrayList<>();
    allStateExecutionInstances.forEach(stateExecutionInstance -> {
      if (!(stateExecutionInstance.fetchStateExecutionData() instanceof ArtifactCollectionExecutionData)) {
        return;
      }

      ArtifactCollectionExecutionData artifactCollectionExecutionData =
          (ArtifactCollectionExecutionData) stateExecutionInstance.fetchStateExecutionData();
      if (EmptyPredicate.isNotEmpty(artifactCollectionExecutionData.getArtifactId())) {
        Artifact artifact = artifactService.get(artifactCollectionExecutionData.getArtifactId());
        if (artifact != null) {
          artifacts.add(artifact);
        } else {
          log.warn("StateExecutionData has artifact id, but artifact doesn't exist in database. Please check!");
        }
      }
    });
    return artifacts;
  }

  @Override
  public List<StateExecutionInstance> getStateExecutionInstances(String appId, String executionUuid) {
    List<StateExecutionInstance> allStateExecutionInstances =
        wingsPersistence.createQuery(StateExecutionInstance.class)
            .filter(StateExecutionInstanceKeys.appId, appId)
            .filter(StateExecutionInstanceKeys.executionUuid, executionUuid)
            .filter(StateExecutionInstanceKeys.stateType, ARTIFACT_COLLECTION.name())
            .asList();

    if (allStateExecutionInstances == null) {
      return new ArrayList<>();
    }
    return allStateExecutionInstances;
  }

  @Override
  public List<StateExecutionInstance> getStateExecutionInstancesForPhases(String executionUuid) {
    return wingsPersistence.createQuery(StateExecutionInstance.class)
        .filter(StateExecutionInstanceKeys.executionUuid, executionUuid)
        .filter(StateExecutionInstanceKeys.stateType, PHASE.name())
        .asList();
  }

  @Override
  public void refreshBuildExecutionSummary(String workflowExecutionId, BuildExecutionSummary buildExecutionSummary) {
    WorkflowExecution workflowExecution = wingsPersistence.get(WorkflowExecution.class, workflowExecutionId);
    if (workflowExecution == null
        || containsBuildExecutionSummary(workflowExecution.getBuildExecutionSummaries(), buildExecutionSummary)) {
      return;
    }

    wingsPersistence.update(
        wingsPersistence.createQuery(WorkflowExecution.class).filter(WorkflowExecutionKeys.uuid, workflowExecutionId),
        wingsPersistence.createUpdateOperations(WorkflowExecution.class)
            .addToSet(WorkflowExecutionKeys.buildExecutionSummaries, buildExecutionSummary));
  }

  private boolean containsBuildExecutionSummary(
      List<BuildExecutionSummary> buildExecutionSummaries, BuildExecutionSummary buildExecutionSummary) {
    if (isEmpty(buildExecutionSummaries) || buildExecutionSummary == null) {
      return false;
    }

    return buildExecutionSummaries.stream().anyMatch(summary
        -> CollectionEntityType.MANIFEST.name().equals(summary.getSourceType())
            ? summary.getAppManifestId().equals(buildExecutionSummary.getAppManifestId())
            : summary.getArtifactStreamId().equals(buildExecutionSummary.getArtifactStreamId()));
  }

  @Override
  public void refreshHelmExecutionSummary(String workflowExecutionId, HelmExecutionSummary helmExecutionSummary) {
    WorkflowExecution workflowExecution = wingsPersistence.get(WorkflowExecution.class, workflowExecutionId);
    if (workflowExecution == null) {
      return;
    }

    wingsPersistence.updateField(
        WorkflowExecution.class, workflowExecutionId, WorkflowExecutionKeys.helmExecutionSummary, helmExecutionSummary);
  }

  @Override
  public void refreshAwsLambdaExecutionSummary(
      String workflowExecutionId, List<AwsLambdaExecutionSummary> awsLambdaExecutionSummaries) {
    WorkflowExecution workflowExecution = wingsPersistence.get(WorkflowExecution.class, workflowExecutionId);
    if (workflowExecution == null) {
      return;
    }

    wingsPersistence.updateField(WorkflowExecution.class, workflowExecutionId,
        WorkflowExecutionKeys.awsLambdaExecutionSummaries, awsLambdaExecutionSummaries);
  }

  @Override
  public int getInstancesDeployedFromExecution(WorkflowExecution workflowExecution) {
    int instanceCount = 0;
    if ((workflowExecution.getWorkflowType() == ORCHESTRATION)
        && workflowExecution.getServiceExecutionSummaries() != null) {
      instanceCount += workflowExecution.getServiceExecutionSummaries()
                           .stream()
                           .map(ElementExecutionSummary::getInstancesCount)
                           .mapToInt(i -> i)
                           .sum();
    } else if (workflowExecution.getWorkflowType() == PIPELINE && workflowExecution.getPipelineExecution() != null
        && isNotEmpty(workflowExecution.getPipelineExecution().getPipelineStageExecutions())) {
      for (PipelineStageExecution pipelineStageExecution :
          workflowExecution.getPipelineExecution().getPipelineStageExecutions()) {
        if (pipelineStageExecution == null || pipelineStageExecution.getWorkflowExecutions() == null) {
          continue;
        }
        instanceCount += pipelineStageExecution.getWorkflowExecutions()
                             .stream()
                             .filter(workflowExecution1 -> workflowExecution1.getServiceExecutionSummaries() != null)
                             .flatMap(workflowExecution1 -> workflowExecution1.getServiceExecutionSummaries().stream())
                             .map(ElementExecutionSummary::getInstancesCount)
                             .mapToInt(i -> i)
                             .sum();
      }
    }
    return instanceCount;
  }

  @Override
  public Set<WorkflowExecutionBaseline> markBaseline(String appId, String workflowExecutionId, boolean isBaseline) {
    try (WorkflowExecutionLogContext ignored = new WorkflowExecutionLogContext(workflowExecutionId, OVERRIDE_ERROR)) {
      log.info("marking baseline for app {} execution {} value {}", appId, workflowExecutionId, isBaseline);
      WorkflowExecution workflowExecution =
          wingsPersistence.getWithAppId(WorkflowExecution.class, appId, workflowExecutionId);
      if (workflowExecution == null) {
        throw new InvalidBaselineConfigurationException(
            "No workflow execution found with id: " + workflowExecutionId + " appId: " + appId);
      }
      List<WorkflowExecution> workflowExecutions = new ArrayList<>();
      switch (workflowExecution.getWorkflowType()) {
        case PIPELINE:
          PipelineExecution pipelineExecution = workflowExecution.getPipelineExecution();
          if (pipelineExecution == null) {
            throw new InvalidBaselineConfigurationException("Pipeline has not been executed.");
          }

          List<PipelineStageExecution> pipelineStageExecutions = pipelineExecution.getPipelineStageExecutions();
          if (isEmpty(pipelineStageExecutions)) {
            throw new InvalidBaselineConfigurationException("No workflows have been executed for this pipeline.");
          }
          pipelineStageExecutions.forEach(
              pipelineStageExecution -> workflowExecutions.addAll(pipelineStageExecution.getWorkflowExecutions()));
          break;
        case ORCHESTRATION:
          workflowExecutions.add(workflowExecution);
          break;
        default:
          unhandled(workflowExecution.getWorkflowType());
      }

      log.info("workflow executions to proceess {}",
          workflowExecutions.stream().map(execution -> execution.getUuid()).collect(Collectors.toList()));
      Set<WorkflowExecutionBaseline> baselines = new HashSet<>();

      if (!isEmpty(workflowExecutions)) {
        workflowExecutions.forEach(stageExecution -> {
          String executionUuid = stageExecution.getUuid();
          List<StateExecutionInstance> stateExecutionInstances =
              wingsPersistence.createQuery(StateExecutionInstance.class)
                  .filter(StateExecutionInstanceKeys.appId, appId)
                  .filter(StateExecutionInstanceKeys.executionUuid, executionUuid)
                  .asList();

          boolean containsVerificationState = false;
          for (StateExecutionInstance stateExecutionInstance : stateExecutionInstances) {
            StateType stateType = StateType.valueOf(stateExecutionInstance.getStateType());
            if (stateType.isVerificationState()) {
              containsVerificationState = true;
              break;
            }
          }

          if (containsVerificationState) {
            for (String serviceId : stageExecution.getServiceIds()) {
              WorkflowExecutionBaseline executionBaseline = WorkflowExecutionBaseline.builder()
                                                                .workflowId(stageExecution.getWorkflowId())
                                                                .workflowExecutionId(executionUuid)
                                                                .envId(stageExecution.getEnvId())
                                                                .serviceId(serviceId)
                                                                .accountId(workflowExecution.getAccountId())
                                                                .build();
              executionBaseline.setAppId(stageExecution.getAppId());
              if (workflowExecution.getWorkflowType() == WorkflowType.PIPELINE) {
                executionBaseline.setPipelineExecutionId(workflowExecutionId);
              }
              baselines.add(executionBaseline);
            }
          }
        });
      }

      log.info("execution baseline {}", baselines);
      if (isEmpty(baselines)) {
        throw new WingsException(ErrorCode.BASELINE_CONFIGURATION_ERROR,
            "Either there is no workflow execution with verification steps or verification steps haven't been executed for the workflow.")
            .addParam("message",
                "Either there is no workflow execution with verification steps or verification steps haven't been executed for the workflow.");
      }

      workflowExecutionBaselineService.markBaseline(Lists.newArrayList(baselines), workflowExecutionId, isBaseline);
      return baselines;
    }
  }

  @Override
  public WorkflowExecutionBaseline getBaselineDetails(
      String appId, String baselineWorkflowExecutionId, String stateExecutionId, String currentExecId) {
    DeploymentExecutionContext executionContext =
        stateMachineExecutor.getExecutionContext(appId, currentExecId, stateExecutionId);
    if (executionContext == null) {
      log.info("failed to get baseline details for app {}, workflow execution {}, uuid {}", appId, currentExecId,
          stateExecutionId);
      return null;
    }
    WorkflowStandardParams workflowStandardParams = executionContext.fetchWorkflowStandardParamsFromContext();
    String envId = workflowStandardParamsExtensionService.fetchRequiredEnv(workflowStandardParams).getUuid();
    PhaseElement phaseElement = executionContext.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    String serviceId = phaseElement.getServiceElement().getUuid();

    final WorkflowExecutionBaseline workflowExecutionBaseline =
        wingsPersistence.createQuery(WorkflowExecutionBaseline.class, excludeAuthority)
            .filter(WorkflowExecutionBaselineKeys.workflowExecutionId, baselineWorkflowExecutionId)
            .filter(WorkflowExecutionBaselineKeys.envId, envId)
            .filter(WorkflowExecutionBaselineKeys.serviceId, serviceId)
            .get();
    if (workflowExecutionBaseline != null) {
      return workflowExecutionBaseline;
    }

    String[] fields = {WorkflowExecutionKeys.accountId, WorkflowExecutionKeys.envId,
        WorkflowExecutionKeys.pipelineExecutionId, WorkflowExecutionKeys.workflowId};
    final WorkflowExecution baselineWorkflowExecution =
        getWorkflowExecution(appId, baselineWorkflowExecutionId, fields);
    if (baselineWorkflowExecution == null) {
      return null;
    }

    return WorkflowExecutionBaseline.builder()
        .workflowId(baselineWorkflowExecution.getWorkflowId())
        .envId(baselineWorkflowExecution.getEnvId())
        .serviceId(serviceId)
        .accountId(baselineWorkflowExecution.getAccountId())
        .workflowExecutionId(baselineWorkflowExecutionId)
        .pipelineExecutionId(baselineWorkflowExecution.getPipelineExecutionId())
        .build();
  }

  @Override
  public List<WorkflowExecution> obtainWorkflowExecutions(
      String accountId, List<String> appIds, long fromDateEpochMilli, String[] projectedKeys) {
    List<WorkflowExecution> workflowExecutions = new ArrayList<>();
    try (HIterator<WorkflowExecution> iterator =
             obtainWorkflowExecutionIterator(accountId, appIds, fromDateEpochMilli, projectedKeys)) {
      while (iterator.hasNext()) {
        workflowExecutions.add(iterator.next());
      }
    }
    return workflowExecutions;
  }

  @Override
  public List<WorkflowExecution> obtainWorkflowExecutions(
      String accountId, long fromDateEpochMilli, String[] projectedKeys) {
    List<WorkflowExecution> workflowExecutions = new ArrayList<>();

    try (HIterator<WorkflowExecution> iterator =
             obtainWorkflowExecutionIterator(accountId, fromDateEpochMilli, projectedKeys)) {
      while (iterator.hasNext()) {
        workflowExecutions.add(iterator.next());
      }
    }
    return workflowExecutions;
  }

  @Override
  public HIterator<WorkflowExecution> obtainWorkflowExecutionIterator(
      String accountId, List<String> appIds, long epochMilli, String[] projectedKeys) {
    Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                         .filter(WorkflowExecutionKeys.accountId, accountId)
                                         .field(WorkflowExecutionKeys.appId)
                                         .in(appIds)
                                         .field(WorkflowExecutionKeys.createdAt)
                                         .greaterThanOrEq(epochMilli)
                                         .field(WorkflowExecutionKeys.pipelineExecutionId)
                                         .doesNotExist();
    for (String projectedKey : projectedKeys) {
      query.project(projectedKey, true);
    }

    FindOptions findOptions = new FindOptions();
    findOptions.hint(BasicDBUtils.getIndexObject(
        WorkflowExecution.mongoIndexes(), WorkflowExecution.ACCOUNT_ID_PIP_EXECUTIONID_CREATEDAT_APP_ID));
    return new HIterator<>(query.limit(NO_LIMIT).fetch(findOptions));
  }

  private HIterator<WorkflowExecution> obtainWorkflowExecutionIterator(
      String accountId, long epochMilli, String[] projectedKeys) {
    Query<WorkflowExecution> query = wingsPersistence.createAuthorizedQueryOnAnalyticNode(WorkflowExecution.class)
                                         .field(WorkflowExecutionKeys.accountId)
                                         .equal(accountId)
                                         .field(WorkflowExecutionKeys.createdAt)
                                         .greaterThanOrEq(epochMilli)
                                         .field(WorkflowExecutionKeys.pipelineExecutionId)
                                         .doesNotExist();
    for (String projectedKey : projectedKeys) {
      query.project(projectedKey, true);
    }
    FindOptions findOptions = new FindOptions();
    findOptions.hint(BasicDBUtils.getIndexObject(
        WorkflowExecution.mongoIndexes(), WorkflowExecution.ACCOUNT_ID_PIP_EXECUTIONID_CREATEDAT_APP_ID));
    return new HIterator<>(query.limit(NO_LIMIT).fetch(findOptions));
  }

  @Override
  public List<Artifact> obtainLastGoodDeployedArtifacts(String appId, String workflowId, String serviceId) {
    if (serviceId == null) {
      return obtainLastGoodDeployedArtifacts(appId, workflowId);
    }
    WorkflowExecution workflowExecution = fetchLastSuccessDeployment(appId, workflowId, serviceId);
    if (workflowExecution != null) {
      ExecutionArgs executionArgs = workflowExecution.getExecutionArgs();
      if (executionArgs != null) {
        return executionArgs.getArtifacts();
      }
    }
    return new ArrayList<>();
  }

  @Override
  public List<Artifact> obtainLastGoodDeployedArtifacts(String appId, String workflowId) {
    WorkflowExecution workflowExecution = fetchLastSuccessDeployment(appId, workflowId);
    if (workflowExecution != null) {
      ExecutionArgs executionArgs = workflowExecution.getExecutionArgs();
      if (executionArgs != null) {
        return executionArgs.getArtifacts();
      }
    }
    return new ArrayList<>();
  }

  @Override
  public List<Artifact> obtainLastGoodDeployedArtifacts(
      WorkflowExecution workflowExecution, List<String> infraMappingList, boolean isInfraBasedArtifact) {
    WorkflowExecution lastGoodWorkflowExecution =
        fetchLastSuccessDeployment(workflowExecution, infraMappingList, isInfraBasedArtifact);
    if (lastGoodWorkflowExecution != null && lastGoodWorkflowExecution.getArtifacts() != null) {
      return lastGoodWorkflowExecution.getArtifacts();
    } else {
      log.info(
          "Rollback artifacts could be null, not found last good deployed artifacts [workflowExecution={},infraMapping={}]",
          workflowExecution, infraMappingList);
    }
    return new ArrayList<>();
  }

  @Override
  public List<ArtifactVariable> obtainLastGoodDeployedArtifactsVariables(String appId, String workflowId) {
    WorkflowExecution workflowExecution = fetchLastSuccessDeployment(appId, workflowId);
    // Todo call fetchLastSuccessDeployment (fetch infra mapping)
    if (workflowExecution != null) {
      ExecutionArgs executionArgs = workflowExecution.getExecutionArgs();
      if (executionArgs != null) {
        return executionArgs.getArtifactVariables();
      }
    }
    return new ArrayList<>();
  }

  private WorkflowExecution fetchLastSuccessDeployment(String appId, String workflowId) {
    return wingsPersistence.createQuery(WorkflowExecution.class)
        .filter(WorkflowExecutionKeys.appId, appId)
        .filter(WorkflowExecutionKeys.workflowId, workflowId)
        .filter(WorkflowExecutionKeys.status, SUCCESS)
        .order("-createdAt")
        .get();
  }

  private WorkflowExecution fetchLastSuccessDeployment(String appId, String workflowId, String serviceId) {
    return wingsPersistence.createQuery(WorkflowExecution.class)
        .filter(WorkflowExecutionKeys.appId, appId)
        .filter(WorkflowExecutionKeys.workflowId, workflowId)
        .filter(WorkflowExecutionKeys.status, SUCCESS)
        .filter(WorkflowExecutionKeys.deployedServices, serviceId)
        .order("-createdAt")
        .get();
  }

  private WorkflowExecution fetchLastSuccessDeployment(WorkflowExecution workflowExecution) {
    FindOptions findOptions = new FindOptions();
    Query<WorkflowExecution> workflowExecutionQuery = getWorkflowExecutionQuery(workflowExecution, SUCCESS);

    if (workflowExecution.isOnDemandRollback()) {
      findOptions = findOptions.skip(1);
    }

    if (isNotEmpty(workflowExecution.getInfraMappingIds())) {
      workflowExecutionQuery.field(WorkflowExecutionKeys.infraMappingIds).in(workflowExecution.getInfraMappingIds());
    }

    addressInefficientQueries(workflowExecutionQuery);

    if (isNotEmpty(workflowExecution.getInfraMappingIds())) {
      findOptions.hint(BasicDBUtils.getIndexObject(
          WorkflowExecution.mongoIndexes(), APPID_STATUS_WORKFLOWID_INFRAMAPPINGIDS_CREATEDAT));
    } else {
      findOptions.hint(
          BasicDBUtils.getIndexObject(WorkflowExecution.mongoIndexes(), APPID_WORKFLOWID_STATUS_CREATEDAT));
    }
    return workflowExecutionQuery.order("-createdAt").get(findOptions);
  }

  private WorkflowExecution fetchLastSuccessDeployment(
      WorkflowExecution workflowExecution, List<String> infraMappingList, boolean isInfraBasedArtifact) {
    FindOptions findOptions = new FindOptions();
    Query<WorkflowExecution> workflowExecutionQuery = isInfraBasedArtifact
        ? getWfExecutionQueryWithoutWfId(workflowExecution, SUCCESS)
        : getWorkflowExecutionQuery(workflowExecution, SUCCESS);

    /*
     With FF INFRA_MAPPING_BASED_ROLLBACK_ARTIFACT On:
     This assumes InfraDefinition and Service does not change for onDemandRollback.
     Consecutively, last entry in workflowExecution would always be with current workflowId and inframappingId
     */
    if (workflowExecution.isOnDemandRollback()) {
      findOptions = findOptions.skip(1);
    }

    if (isNotEmpty(infraMappingList)) {
      workflowExecutionQuery.field(WorkflowExecutionKeys.infraMappingIds).in(new HashSet<>(infraMappingList));
    }

    addressInefficientQueries(workflowExecutionQuery);

    if (isNotEmpty(infraMappingList)) {
      if (isInfraBasedArtifact) {
        findOptions.hint(BasicDBUtils.getIndexObject(WorkflowExecution.mongoIndexes(), LAST_INFRAMAPPING_SEARCH_2));

        Query<WorkflowExecution> deploymentQuery = workflowExecutionQuery.cloneQuery();
        deploymentQuery.filter(WorkflowExecutionKeys.deployment, true);
        WorkflowExecution existingWorkflow =
            deploymentQuery.order(Sort.descending(WorkflowExecutionKeys.createdAt)).get(findOptions);
        // this logic is used because deployment field is not populated on all executions
        // maybe in the future we should remove this and use only deployment query
        if (existingWorkflow != null) {
          return existingWorkflow;
        } else {
          workflowExecutionQuery
              .field(WorkflowExecutionKeys.serviceExecutionSummaries_instanceStatusSummaries_instanceElement_uuid)
              .exists();
        }
      } else {
        findOptions.hint(BasicDBUtils.getIndexObject(
            WorkflowExecution.mongoIndexes(), APPID_STATUS_WORKFLOWID_INFRAMAPPINGIDS_CREATEDAT));
      }
    } else {
      findOptions.hint(BasicDBUtils.getIndexObject(
          WorkflowExecution.mongoIndexes(), APPID_WORKFLOWID_STATUS_DEPLOYEDSERVICES_CREATEDAT));
    }
    return workflowExecutionQuery.order(Sort.descending(WorkflowExecutionKeys.createdAt)).get(findOptions);
  }

  private Query<WorkflowExecution> getWorkflowExecutionQuery(
      WorkflowExecution workflowExecution, ExecutionStatus status) {
    return wingsPersistence.createQuery(WorkflowExecution.class)
        .filter(WorkflowExecutionKeys.appId, workflowExecution.getAppId())
        .filter(WorkflowExecutionKeys.workflowId, workflowExecution.getWorkflowId())
        .filter(WorkflowExecutionKeys.status, status);
  }

  private Query<WorkflowExecution> getWfExecutionQueryWithoutWfId(
      WorkflowExecution workflowExecution, ExecutionStatus status) {
    return wingsPersistence.createQuery(WorkflowExecution.class)
        .filter(WorkflowExecutionKeys.appId, workflowExecution.getAppId())
        .filter(WorkflowExecutionKeys.workflowType, workflowExecution.getWorkflowType())
        .filter(WorkflowExecutionKeys.status, status);
  }

  private String getAccountId(WorkflowExecution workflowExecution) {
    String accountId = workflowExecution.getAccountId();
    if (accountId == null) {
      accountId = appService.getAccountIdByAppId(workflowExecution.getAppId());
    }
    return accountId;
  }

  private void addressInefficientQueries(Query<WorkflowExecution> workflowExecutionQuery) {
    workflowExecutionQuery.project(WorkflowExecutionKeys.uuid, true)
        .project(WorkflowExecutionKeys.releaseNo, true)
        .project(WorkflowExecutionKeys.name, true)
        .project(WorkflowExecutionKeys.createdAt, true)
        .project(WorkflowExecutionKeys.artifacts, true)
        .project(WorkflowExecutionKeys.executionArgs, true);
  }

  @Override
  public WorkflowExecution fetchWorkflowExecution(
      String appId, List<String> serviceIds, List<String> envIds, String workflowId) {
    return wingsPersistence.createQuery(WorkflowExecution.class)
        .filter("appId", appId)
        .filter(WorkflowExecutionKeys.workflowId, workflowId)
        .filter(WorkflowExecutionKeys.status, SUCCESS)
        .filter(WorkflowExecutionKeys.workflowType, ORCHESTRATION)
        .field("serviceIds")
        .in(serviceIds)
        .field("envIds")
        .in(envIds)
        .order(Sort.descending(WorkflowExecutionKeys.createdAt))
        .get(new FindOptions().skip(1));
  }

  @Override
  public boolean verifyAuthorizedToAcceptOrReject(List<String> userGroupIds, String appId, String workflowId) {
    User user = UserThreadLocal.get();
    if (user == null) {
      return true;
    }

    if (isEmpty(userGroupIds)) {
      try {
        if (isBlank(appId) || isBlank(workflowId)) {
          return true;
        }
        deploymentAuthHandler.authorizeWorkflowOrPipelineForExecution(appId, workflowId);
        return true;
      } catch (WingsException e) {
        return false;
      }
    } else {
      return userGroupService.verifyUserAuthorizedToAcceptOrRejectApproval(null, userGroupIds);
    }
  }

  @Override
  public boolean verifyAuthorizedToAcceptOrReject(
      List<String> userGroupIds, List<String> apiKeysUserGroupIds, String appId, String workflowId) {
    if (isEmpty(apiKeysUserGroupIds)) {
      return true;
    }

    if (isEmpty(userGroupIds)) {
      try {
        if (isBlank(appId) || isBlank(workflowId)) {
          return true;
        }
        deploymentAuthHandler.authorizeWorkflowOrPipelineForExecution(appId, workflowId);
        return true;
      } catch (WingsException e) {
        return false;
      }
    } else {
      return userGroupService.verifyApiKeyAuthorizedToAcceptOrRejectApproval(apiKeysUserGroupIds, userGroupIds);
    }
  }

  @Override
  public List<WorkflowExecution> listWaitingOnDeployments(String appId, String workflowExecutionId) {
    WorkflowExecution workflowExecution =
        wingsPersistence.getWithAppId(WorkflowExecution.class, appId, workflowExecutionId);
    if (workflowExecution == null || ExecutionStatus.isFinalStatus(workflowExecution.getStatus())) {
      return new ArrayList<>();
    }
    PageRequestBuilder pageRequestBuilder =
        aPageRequest()
            .addFieldsIncluded(WorkflowExecutionKeys.appId, WorkflowExecutionKeys.status,
                WorkflowExecutionKeys.workflowId, WorkflowExecutionKeys.createdAt, WorkflowExecutionKeys.uuid,
                WorkflowExecutionKeys.startTs, WorkflowExecutionKeys.endTs, WorkflowExecutionKeys.name)
            .addFilter(WorkflowExecutionKeys.appId, EQ, appId)
            .addFilter(WorkflowExecutionKeys.status, IN, ExecutionStatus.activeStatuses().toArray())
            .addFilter(WorkflowExecutionKeys.createdAt, LT_EQ, workflowExecution.getCreatedAt())
            .addOrder(WorkflowExecutionKeys.createdAt, OrderType.ASC)
            .addFilter(WorkflowExecutionKeys.workflowId, EQ, workflowExecution.getWorkflowId());

    if (isNotEmpty(workflowExecution.getInfraMappingIds())) {
      pageRequestBuilder.addFilter(
          WorkflowExecutionKeys.infraMappingIds, IN, workflowExecution.getInfraMappingIds().toArray());
    }
    PageRequest pageRequest = pageRequestBuilder.build();
    pageRequest.setOptions(Collections.singletonList(PageRequest.Option.SKIPCOUNT));
    return wingsPersistence.query(WorkflowExecution.class, pageRequest);
  }

  @Override
  public Long fetchWorkflowExecutionStartTs(String appId, String workflowExecutionId) {
    WorkflowExecution workflowExecution = wingsPersistence.createQuery(WorkflowExecution.class)
                                              .project(WorkflowExecutionKeys.startTs, true)
                                              .project(WorkflowExecutionKeys.appId, true)
                                              .project(WorkflowExecutionKeys.uuid, true)
                                              .filter(WorkflowExecutionKeys.appId, appId)
                                              .filter(WorkflowExecutionKeys.uuid, workflowExecutionId)
                                              .get();
    return workflowExecution == null ? null : workflowExecution.getStartTs();
  }

  @Override
  public ApprovalAuthorization getApprovalAuthorization(String appId, List<String> userGroupIds) {
    ApprovalAuthorization approvalAuthorization = new ApprovalAuthorization();
    approvalAuthorization.setAuthorized(true);

    if (isNotEmpty(userGroupIds) && !verifyAuthorizedToAcceptOrReject(userGroupIds, appId, null)) {
      approvalAuthorization.setAuthorized(false);
    }

    return approvalAuthorization;
  }

  /**
   * Do
   * @param appId
   * @param workflowExecutionId
   * @return
   */
  @Override
  public WorkflowExecution getWorkflowExecutionSummary(String appId, String workflowExecutionId) {
    return wingsPersistence.createQuery(WorkflowExecution.class)
        .project(WorkflowExecutionKeys.serviceExecutionSummaries, false)
        .project(WorkflowExecutionKeys.executionArgs, false)
        .filter(WorkflowExecutionKeys.appId, appId)
        .filter(WorkflowExecutionKeys.uuid, workflowExecutionId)
        .get();
  }

  @Override
  public WorkflowExecution getWorkflowExecutionForVerificationService(String appId, String workflowExecutionId) {
    return wingsPersistence.createQuery(WorkflowExecution.class)
        .project(WorkflowExecutionKeys.uuid, true)
        .project(WorkflowExecutionKeys.envId, true)
        .filter(WorkflowExecutionKeys.appId, appId)
        .filter(WorkflowExecutionKeys.uuid, workflowExecutionId)
        .get();
  }

  @Override
  public void refreshCollectedArtifacts(String appId, String pipelineExecutionId, String workflowExecutionId) {
    WorkflowExecution pipelineWorkflowExecution = wingsPersistence.createQuery(WorkflowExecution.class)
                                                      .project(WorkflowExecutionKeys.artifacts, true)
                                                      .filter(WorkflowExecutionKeys.appId, appId)
                                                      .filter(WorkflowExecutionKeys.uuid, pipelineExecutionId)
                                                      .get();

    WorkflowExecution workflowExecution = wingsPersistence.createQuery(WorkflowExecution.class)
                                              .project(WorkflowExecutionKeys.artifacts, true)
                                              .filter(WorkflowExecutionKeys.appId, appId)
                                              .filter(WorkflowExecutionKeys.uuid, workflowExecutionId)
                                              .get();

    // Workflow Step Artifacts
    List<Artifact> artifacts = workflowExecution.getArtifacts();
    if (isEmpty(artifacts)) {
      return;
    }

    // Pipeline Execution Artifacts collected so far
    List<Artifact> collectedArtifacts = pipelineWorkflowExecution.getArtifacts();
    if (collectedArtifacts == null) {
      pipelineWorkflowExecution.setArtifacts(new ArrayList<>());
      collectedArtifacts = pipelineWorkflowExecution.getArtifacts();
    }

    Set<String> collectedArtifactIds = collectedArtifacts.stream().map(Artifact::getUuid).collect(toSet());
    Set<String> artifactIds = artifacts.stream().map(Artifact::getUuid).collect(toSet());
    if (collectedArtifactIds.containsAll(artifactIds)) {
      return;
    }

    collectedArtifacts.addAll(artifacts);
    collectedArtifacts = collectedArtifacts.stream().filter(distinctByKey(Artifact::getUuid)).collect(toList());

    Query<WorkflowExecution> updatedQuery = wingsPersistence.createQuery(WorkflowExecution.class)
                                                .project(WorkflowExecutionKeys.artifacts, true)
                                                .filter(WorkflowExecutionKeys.appId, appId)
                                                .filter(WorkflowExecutionKeys.uuid, pipelineExecutionId);

    UpdateOperations<WorkflowExecution> updateOps = wingsPersistence.createUpdateOperations(WorkflowExecution.class)
                                                        .set(WorkflowExecutionKeys.artifacts, collectedArtifacts);

    wingsPersistence.update(updatedQuery, updateOps);
  }

  @Override
  public StateMachine obtainStateMachine(WorkflowExecution workflowExecution) {
    if (workflowExecution.getStateMachine() != null) {
      return workflowExecution.getStateMachine();
    }
    log.warn("Workflow execution {} do not have inline state machine", workflowExecution.getUuid());
    return wingsPersistence.getWithAppId(
        StateMachine.class, workflowExecution.getAppId(), workflowExecution.getStateMachineId());
  }

  @Override
  public WorkflowExecution fetchLastWorkflowExecution(String appId, String workflowId, String serviceId, String envId) {
    Query<WorkflowExecution> workflowExecutionQuery = wingsPersistence.createQuery(WorkflowExecution.class)
                                                          .filter(WorkflowExecutionKeys.appId, appId)
                                                          .filter(WorkflowExecutionKeys.workflowId, workflowId)
                                                          .filter(WorkflowExecutionKeys.workflowType, ORCHESTRATION);

    if (StringUtils.isNotBlank(serviceId)) {
      workflowExecutionQuery.field(WorkflowExecutionKeys.serviceIds).in(Arrays.asList(serviceId));
    }

    if (StringUtils.isNotBlank(envId)) {
      workflowExecutionQuery.field(WorkflowExecutionKeys.envIds).in(Arrays.asList(envId));
    }

    return workflowExecutionQuery.order(Sort.descending(WorkflowExecutionKeys.createdAt)).get();
  }

  @Override
  public PageResponse<WorkflowExecution> fetchWorkflowExecutionList(
      String appId, String workflowId, String envId, int pageOffset, int pageLimit) {
    PageRequest<WorkflowExecution> pageRequest =
        aPageRequest()
            .addFilter(WorkflowExecutionKeys.appId, Operator.EQ, appId)
            .addFilter(WorkflowExecutionKeys.workflowId, Operator.EQ, workflowId)
            .addFilter(WorkflowExecutionKeys.workflowType, Operator.EQ, ORCHESTRATION)
            .addFilter(WorkflowExecutionKeys.envIds, Operator.IN, Arrays.asList(envId))
            .withLimit(String.valueOf(pageLimit))
            .withOffset(String.valueOf(pageOffset))
            .addOrder(aSortOrder().withField(WorkflowExecutionKeys.createdAt, OrderType.DESC).build())
            .build();
    pageRequest.setOptions(Collections.singletonList(PageRequest.Option.SKIPCOUNT));

    return wingsPersistence.query(WorkflowExecution.class, pageRequest);
  }

  @Override
  public String getApplicationIdByExecutionId(String executionId) {
    return wingsPersistence.createQuery(WorkflowExecution.class)
        .filter(WorkflowExecutionKeys.uuid, executionId)
        .project(WorkflowExecutionKeys.appId, true)
        .get()
        .getAppId();
  }

  @Override
  public List<WorkflowExecution> getLastSuccessfulWorkflowExecutions(
      String appId, String workflowId, String serviceId) {
    final PageRequest<WorkflowExecution> pageRequest =
        aPageRequest()
            .addFilter(WorkflowExecutionKeys.appId, Operator.EQ, appId)
            .addFilter(WorkflowExecutionKeys.workflowId, Operator.EQ, workflowId)
            .addFilter(WorkflowExecutionKeys.status, Operator.EQ, ExecutionStatus.SUCCESS)
            .addOrder(WorkflowExecutionKeys.createdAt, OrderType.DESC)
            .build();
    if (!isEmpty(serviceId)) {
      pageRequest.addFilter(WorkflowExecutionKeys.serviceIds, EQ, serviceId);
    }
    pageRequest.setOptions(Collections.singletonList(PageRequest.Option.SKIPCOUNT));

    final PageResponse<WorkflowExecution> workflowExecutions =
        listExecutions(pageRequest, false, true, false, false, false, false);
    if (workflowExecutions != null) {
      return workflowExecutions.getResponse();
    }
    return null;
  }

  @Override
  public boolean appendInfraMappingId(String appId, String workflowExecutionId, String infraMappingId) {
    boolean modified = false;
    String[] fields = {WorkflowExecutionKeys.appId, WorkflowExecutionKeys.infraMappingIds, WorkflowExecutionKeys.uuid};
    WorkflowExecution workflowExecution = getWorkflowExecution(appId, workflowExecutionId, fields);
    List<String> infraMappingIds = workflowExecution.getInfraMappingIds();
    if (isNotEmpty(infraMappingIds)) {
      if (!infraMappingIds.contains(infraMappingId)) {
        infraMappingIds.add(infraMappingId);
        modified = true;
      }
    } else {
      infraMappingIds = new ArrayList<>();
      infraMappingIds.add(infraMappingId);
      modified = true;
    }
    if (modified) {
      try {
        Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                             .filter(WorkflowExecutionKeys.appId, workflowExecution.getAppId())
                                             .filter(ID_KEY, workflowExecution.getUuid());

        UpdateOperations<WorkflowExecution> updateOps =
            wingsPersistence.createUpdateOperations(WorkflowExecution.class).set("infraMappingIds", infraMappingIds);
        UpdateResults updateResults = wingsPersistence.update(query, updateOps);
        return updateResults != null && updateResults.getWriteResult() != null
            && updateResults.getWriteResult().getN() > 0;

      } catch (Exception ex) {
        return false;
      }
    } else {
      return true;
    }
  }

  @Override
  public boolean isTriggerBasedDeployment(ExecutionContext context) {
    WorkflowExecution workflowExecution =
        getWorkflowExecution(context.getAppId(), context.getWorkflowExecutionId(), WorkflowExecutionKeys.triggeredBy);
    if (workflowExecution != null && workflowExecution.getTriggeredBy() != null
        && workflowExecution.getTriggeredBy().getName().contains("Deployment Trigger")) {
      return true;
    }
    return false;
  }

  @Override
  public List<WorkflowExecution> getLatestExecutionsFor(
      String appId, String infraMappingId, int limit, List<String> fieldList, boolean forInclusion) {
    final Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                               .filter(WorkflowExecutionKeys.appId, appId)
                                               .filter(WorkflowExecutionKeys.status, SUCCESS)
                                               .filter(WorkflowExecutionKeys.workflowType, ORCHESTRATION)
                                               .filter(WorkflowExecutionKeys.infraMappingIds, infraMappingId);

    emptyIfNull(fieldList).forEach(field -> query.project(field, forInclusion));

    final List<WorkflowExecution> workflowExecutionList = emptyIfNull(
        query.order(Sort.descending(WorkflowExecutionKeys.createdAt)).asList(new FindOptions().limit(limit)));

    workflowExecutionList.forEach(we -> we.setStateMachine(null));
    return workflowExecutionList;
  }

  @Override
  public ConcurrentExecutionResponse fetchConcurrentExecutions(
      String appId, String workflowExecutionId, String resourceConstraintName, String unit) {
    ResourceConstraint resourceConstraint =
        resourceConstraintService.getByName(appService.getAccountIdByAppId(appId), resourceConstraintName);
    notNullCheck("Resource Constraint not found for name " + resourceConstraintName, resourceConstraint);
    String[] fields = {WorkflowExecutionKeys.accountId, WorkflowExecutionKeys.concurrencyStrategy,
        WorkflowExecutionKeys.infraMappingIds, WorkflowExecutionKeys.status};
    WorkflowExecution execution = getWorkflowExecution(appId, workflowExecutionId, fields);
    notNullCheck("Workflow Execution not found", execution);
    ConcurrentExecutionResponseBuilder responseBuilder = ConcurrentExecutionResponse.builder();
    responseBuilder.unitType(
        execution.getConcurrencyStrategy() != null ? execution.getConcurrencyStrategy().getUnitType() : null);
    responseBuilder.infrastructureDetails(extractServiceInfrastructureDetails(appId, execution));

    // TODO: Write unit tests for this on removing FF.
    ResourceConstraintInstance resourceConstraintInstance = null;
    String accountId = execution.getAccountId();
    if (featureFlagService.isEnabled(FeatureName.RESOURCE_CONSTRAINT_MAX_QUEUE, accountId)) {
      resourceConstraintInstance = resourceConstraintService.fetchResourceConstraintInstanceForUnitAndWFExecution(
          appId, resourceConstraint.getUuid(), unit, workflowExecutionId, HoldingScope.WORKFLOW.name());
    }

    if (ExecutionStatus.isRunningStatus(execution.getStatus()) || resourceConstraintInstance != null) {
      List<ResourceConstraintInstance> instances =
          resourceConstraintService.fetchResourceConstraintInstancesForUnitAndEntityType(
              appId, resourceConstraint.getUuid(), unit, HoldingScope.WORKFLOW.name());
      responseBuilder.state(extractState(workflowExecutionId, instances));
      responseBuilder.executions(fetchWorkflowExecutionsForResourceConstraint(
          instances.stream().map(ResourceConstraintInstance::getReleaseEntityId).collect(Collectors.toList())));
    }
    return responseBuilder.build();
  }

  @Override
  public Map<String, Object> extractServiceInfrastructureDetails(String appId, WorkflowExecution execution) {
    Map<String, Object> infrastructureDetails = new LinkedHashMap<>();

    if (isNotEmpty(execution.getInfraMappingIds())) {
      InfrastructureMapping infrastructureMapping =
          infrastructureMappingService.get(appId, execution.getInfraMappingIds().get(0));

      Service service = serviceResourceService.get(appId, infrastructureMapping.getServiceId());
      infrastructureDetails.put("Service", service.getName());
      infrastructureDetails.put("DeploymentType", service.getDeploymentType());

      infrastructureDetails.put("CloudProvider", infrastructureMapping.getComputeProviderType());
      infrastructureDetails.put("CloudProviderName", infrastructureMapping.getComputeProviderName());
      if (infrastructureMapping instanceof ContainerInfrastructureMapping) {
        String clusterName = ((ContainerInfrastructureMapping) infrastructureMapping).getClusterName();
        if (isNotBlank(clusterName)) {
          infrastructureDetails.put("ClusterName", clusterName);
        }
        String namespace = ((ContainerInfrastructureMapping) infrastructureMapping).getNamespace();
        if (namespace != null) {
          infrastructureDetails.put("namespace", namespace);
        }
      }
    }
    return infrastructureDetails;
  }

  private State extractState(String workflowExecutionId, List<ResourceConstraintInstance> instances) {
    Optional<ResourceConstraintInstance> optionalInstance = CollectionUtils.filterAndGetFirst(
        instances, instance -> workflowExecutionId.equals(instance.getReleaseEntityId()));
    if (optionalInstance.isPresent()) {
      ResourceConstraintInstance executionInstance = optionalInstance.get();
      return Consumer.State.valueOf(executionInstance.getState());
    } else {
      return null;
    }
  }

  @Override
  public boolean checkIfOnDemand(String appId, String workflowExecutionId) {
    WorkflowExecution workflowExecution =
        getWorkflowExecution(appId, workflowExecutionId, WorkflowExecutionKeys.onDemandRollback);
    notNullCheck("Workflow Execution is Null", workflowExecution);
    return workflowExecution.isOnDemandRollback();
  }

  @Override
  public List<WorkflowExecution> fetchWorkflowExecutionsForResourceConstraint(List<String> entityIds) {
    if (EmptyPredicate.isEmpty(entityIds)) {
      return emptyList();
    }
    Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class, excludeAuthority)
                                         .project(WorkflowExecutionKeys.appId, true)
                                         .project(WorkflowExecutionKeys.status, true)
                                         .project(WorkflowExecutionKeys.workflowId, true)
                                         .project(WorkflowExecutionKeys.createdAt, true)
                                         .project(WorkflowExecutionKeys.uuid, true)
                                         .project(WorkflowExecutionKeys.startTs, true)
                                         .project(WorkflowExecutionKeys.endTs, true)
                                         .project(WorkflowExecutionKeys.name, true)
                                         .project(WorkflowExecutionKeys.envId, true)
                                         .project(WorkflowExecutionKeys.pipelineExecutionId, true)
                                         .field("_id")
                                         .in(entityIds);

    List<WorkflowExecution> workflowExecutions = new ArrayList<>();
    try (HIterator<WorkflowExecution> iterator = new HIterator<>(query.limit(NO_LIMIT).fetch())) {
      for (WorkflowExecution workflowExecution : iterator) {
        workflowExecutions.add(workflowExecution);
      }
    }
    workflowExecutions.sort(Comparator.comparing(item -> entityIds.indexOf(item.getUuid())));
    return workflowExecutions;
  }

  /**
   *
   * @param workflowExecution
   * @return only the skeleton of the environment object. Contains only the ID,the type and the name ->
   * Reconstructed from what we see in the pipelineExecution or the workflowExecution
   */
  @Override
  public List<EnvSummary> getEnvironmentsForExecution(WorkflowExecution workflowExecution) {
    Set<EnvSummary> environments = new HashSet<>();
    if (workflowExecution.getPipelineExecution() != null) {
      if (workflowExecution.getPipelineExecution().getPipelineStageExecutions() != null) {
        workflowExecution.getPipelineExecution().getPipelineStageExecutions().forEach(stageExecution
            -> stageExecution.getWorkflowExecutions()
                   .stream()
                   .filter(wfExecution -> wfExecution.getEnvId() != null && wfExecution.getEnvType() != null)
                   .map(wfExecution
                       -> EnvSummary.builder()
                              .uuid(wfExecution.getEnvId())
                              .environmentType(wfExecution.getEnvType())
                              .name(wfExecution.getEnvName())
                              .build())
                   .forEach(environments::add));
      }
    } else {
      if (workflowExecution.getEnvId() != null && workflowExecution.getEnvType() != null) {
        environments.add(EnvSummary.builder()
                             .environmentType(workflowExecution.getEnvType())
                             .uuid(workflowExecution.getEnvId())
                             .name(workflowExecution.getEnvName())
                             .build());
      }
    }
    return environments.stream().collect(Collectors.toList());
  }

  /**
   *
   * @param workflowExecution
   * @return only the serviceIds that have been deployed in the pipeline or workflowExecution ->
   * Reconstructed from what we see in the pipelineExecution or the workflowExecution
   */
  @Override
  public List<String> getServiceIdsForExecution(WorkflowExecution workflowExecution) {
    Set<String> serviceIds = new HashSet<>();
    if (workflowExecution.getPipelineExecution() != null) {
      if (workflowExecution.getPipelineExecution() != null) {
        if (workflowExecution.getPipelineExecution().getPipelineStageExecutions() != null) {
          workflowExecution.getPipelineExecution().getPipelineStageExecutions().forEach(stageExecution
              -> stageExecution.getWorkflowExecutions()
                     .stream()
                     .filter(wfExecution -> wfExecution.getServiceIds() != null)
                     .map(WorkflowExecution::getServiceIds)
                     .forEach(serviceIds::addAll));
        }
      }
    } else {
      if (!isNullOrEmpty(workflowExecution.getServiceIds())) {
        serviceIds.addAll(workflowExecution.getServiceIds());
      }
    }

    return new ArrayList<>(serviceIds);
  }

  /**
   *
   * @param workflowExecution
   * @return only the serviceIds that have been deployed in the pipeline or workflowExecution ->
   * Reconstructed from what we see in the pipelineExecution or the workflowExecution
   */
  @Override
  public List<String> getCloudProviderIdsForExecution(WorkflowExecution workflowExecution) {
    Set<String> cloudProviderIds = new HashSet<>();
    if (workflowExecution.getPipelineExecution() != null) {
      if (workflowExecution.getPipelineExecution() != null) {
        if (workflowExecution.getPipelineExecution().getPipelineStageExecutions() != null) {
          workflowExecution.getPipelineExecution().getPipelineStageExecutions().forEach(stageExecution
              -> stageExecution.getWorkflowExecutions()
                     .stream()
                     .filter(wfExecution -> wfExecution.getCloudProviderIds() != null)
                     .map(WorkflowExecution::getCloudProviderIds)
                     .forEach(cloudProviderIds::addAll));
        }
      }
    } else {
      if (!isNullOrEmpty(workflowExecution.getCloudProviderIds())) {
        cloudProviderIds.addAll(workflowExecution.getCloudProviderIds());
      }
    }

    return new ArrayList<>(cloudProviderIds);
  }

  @Override
  public boolean getOnDemandRollbackAvailable(String appId, WorkflowExecution lastWE, boolean fromPipe) {
    if (lastWE.getStatus() != SUCCESS && !fromPipe) {
      log.info("On demand rollback not available for non successful executions {}", lastWE);
      return false;
    }
    if (lastWE.getWorkflowType() == PIPELINE) {
      log.info("On demand rollback not available for pipeline executions {}", lastWE);
      return false;
    }
    List<String> infraDefId = lastWE.getInfraDefinitionIds();
    if ((isEmpty(infraDefId) || infraDefId.size() != 1) && !fromPipe) {
      // Only allowing on demand rollback for workflow deploying single infra definition.
      log.info("On demand rollback not available, Infra definition size not equal to 1 {}", lastWE);
      return false;
    } else {
      InfrastructureDefinition infrastructureDefinition = infrastructureDefinitionService.get(appId, infraDefId.get(0));
      return infrastructureDefinition != null && rollbackEnabledForDeploymentType(infrastructureDefinition);
    }
  }

  private boolean rollbackEnabledForDeploymentType(InfrastructureDefinition infrastructureDefinition) {
    return DeploymentType.PCF == infrastructureDefinition.getDeploymentType()
        || DeploymentType.SSH == infrastructureDefinition.getDeploymentType()
        || DeploymentType.WINRM == infrastructureDefinition.getDeploymentType()
        || DeploymentType.ECS == infrastructureDefinition.getDeploymentType()
        || DeploymentType.KUBERNETES == infrastructureDefinition.getDeploymentType()
        || isAmiAsg(infrastructureDefinition) || isAmiSpotinst(infrastructureDefinition);
  }

  private boolean isAmiAsg(InfrastructureDefinition infrastructureDefinition) {
    return DeploymentType.AMI == infrastructureDefinition.getDeploymentType()
        && infrastructureDefinition.getInfrastructure() instanceof AwsAmiInfrastructure
        && (((AwsAmiInfrastructure) infrastructureDefinition.getInfrastructure()).getAmiDeploymentType()
            == AmiDeploymentType.AWS_ASG);
  }

  private boolean isAmiSpotinst(InfrastructureDefinition infrastructureDefinition) {
    return DeploymentType.AMI == infrastructureDefinition.getDeploymentType()
        && infrastructureDefinition.getInfrastructure() instanceof AwsAmiInfrastructure
        && (((AwsAmiInfrastructure) infrastructureDefinition.getInfrastructure()).getAmiDeploymentType()
            == AmiDeploymentType.SPOTINST);
  }

  @Override
  public boolean isMultiService(String appId, String workflowExecutionId) {
    WorkflowExecution workflowExecution =
        getWorkflowExecution(appId, workflowExecutionId, WorkflowExecutionKeys.serviceIds);
    notNullCheck("Workflow Execution cannot be null", workflowExecution);
    return isNotEmpty(workflowExecution.getServiceIds()) && workflowExecution.getServiceIds().size() > 1;
  }

  @Override
  public WorkflowExecutionInfo getWorkflowExecutionInfo(String workflowExecutionId) {
    WorkflowExecution workflowExecution = wingsPersistence.get(WorkflowExecution.class, workflowExecutionId);
    if (workflowExecution == null) {
      throw new InvalidRequestException("Couldn't find a workflow Execution with Id: " + workflowExecutionId, USER);
    }

    WorkflowExecutionInfoBuilder workflowExecutionInfoBuilder = WorkflowExecutionInfo.builder()
                                                                    .accountId(workflowExecution.getAccountId())
                                                                    .name(workflowExecution.getName())
                                                                    .appId(workflowExecution.getAppId())
                                                                    .executionId(workflowExecutionId)
                                                                    .workflowId(workflowExecution.getWorkflowId())
                                                                    .startTs(workflowExecution.getStartTs());

    if (workflowExecution.getRollbackStartTs() != null) {
      ExecutionInterrupt executionInterrupt =
          wingsPersistence.createQuery(ExecutionInterrupt.class)
              .filter(ExecutionInterruptKeys.appId, workflowExecution.getAppId())
              .filter(ExecutionInterruptKeys.executionUuid, workflowExecutionId)
              .filter(ExecutionInterruptKeys.executionInterruptType, String.valueOf(ExecutionInterruptType.ROLLBACK))
              .order(Sort.descending(ExecutionInterruptKeys.createdAt))
              .get();

      RollbackType rollbackType;
      String rollbackStateExecutionId = null;
      if (executionInterrupt != null) {
        rollbackType = RollbackType.MANUAL;
        rollbackStateExecutionId = executionInterrupt.getStateExecutionInstanceId();
      } else {
        rollbackType = RollbackType.AUTO;
        Query<StateExecutionInstance> query =
            wingsPersistence.createQuery(StateExecutionInstance.class)
                .filter(StateExecutionInstanceKeys.appId, workflowExecution.getAppId())
                .filter(StateExecutionInstanceKeys.executionUuid, workflowExecutionId)
                .field(StateExecutionInstanceKeys.status)
                .in(Arrays.asList(FAILED, ERROR))
                .order(Sort.descending(StateExecutionInstanceKeys.createdAt))
                .project(StateExecutionInstanceKeys.uuid, true)
                .project(StateExecutionInstanceKeys.stateName, true)
                .project(StateExecutionInstanceKeys.status, true);

        StateExecutionInstance failedInstance = query.get();
        if (failedInstance != null) {
          rollbackStateExecutionId = failedInstance.getUuid();
        }
      }

      RollbackWorkflowExecutionInfo rollbackWorkflowExecutionInfo =
          RollbackWorkflowExecutionInfo.builder()
              .rollbackStartTs(workflowExecution.getRollbackStartTs())
              .rollbackDuration(workflowExecution.getRollbackDuration())
              .rollbackType(rollbackType)
              .rollbackStateExecutionId(rollbackStateExecutionId)
              .build();

      workflowExecutionInfoBuilder.rollbackWorkflowExecutionInfo(rollbackWorkflowExecutionInfo);
    }

    return workflowExecutionInfoBuilder.build();
  }

  public void addTagFilterToPageRequest(PageRequest<WorkflowExecution> pageRequest, String tagFilter) {
    resourceLookupFilterHelper.addResourceLookupFiltersToPageRequest(pageRequest, tagFilter);
  }

  public Map<String, String> getDeploymentTags(String accountId, List<NameValuePair> tags) {
    if (isEmpty(tags)) {
      return null;
    }
    Map<String, String> deploymentTags = new HashMap<>();
    for (NameValuePair nameValuePair : tags) {
      deploymentTags.put(nameValuePair.getName(), nameValuePair.getValue());
    }
    return deploymentTags;
  }

  @Override
  public Set<String> getWorkflowExecutionsWithTag(String accountId, String key, String value) {
    Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                         .filter(WorkflowExecutionKeys.accountId, accountId)
                                         .filter(WorkflowExecutionKeys.tags + ".name", key);
    if (isNotEmpty(value)) {
      query.field(WorkflowExecutionKeys.tags + ".value").equal(value);
    }

    List<WorkflowExecution> workflowExecutions = query.project("_id", true).limit(NO_LIMIT).asList();
    return workflowExecutions.stream().map(WorkflowExecution::getUuid).collect(toSet());
  }

  /**
   * Invalidate cache for the graph by downgrading it context order
   */
  private void invalidateCache(WorkflowExecution workflowExecution, ExecutionInterrupt executionInterrupt) {
    long downgradeValueInMillis = 60_000; // 1 hour
    WorkflowTree tree = mongoStore.get(
        GraphRenderer.algorithmId, WorkflowTree.STRUCTURE_HASH, workflowExecution.getUuid(), getParamsForTree());
    if (tree != null) {
      log.info("Invalidating cache after interrupt {} for workflow {}", executionInterrupt.getUuid(),
          workflowExecution.getUuid());
      WorkflowTree downgradedTree = WorkflowTree.builder()
                                        .contextOrder(System.currentTimeMillis() - downgradeValueInMillis)
                                        .overrideStatus(tree.getOverrideStatus())
                                        .key(tree.getKey())
                                        .wasInvalidated(true)
                                        .params(tree.getParams())
                                        .graph(tree.getGraph())
                                        .build();

      String accountId = workflowExecution.getAccountId();
      executorService.submit(() -> mongoStore.upsert(downgradedTree, ofMinutes(1), true, accountId));
    }
  }

  @Override
  public boolean checkWorkflowExecutionInFinalStatus(String appId, String workflowExecutionId) {
    final WorkflowExecution workflowExecution =
        fetchWorkflowExecution(appId, workflowExecutionId, WorkflowExecutionKeys.status);
    return ExecutionStatus.isFinalStatus(workflowExecution.getStatus());
  }

  @Override
  public ExecutionStatus fetchWorkflowExecutionStatus(String appId, String workflowExecutionId) {
    return fetchWorkflowExecution(appId, workflowExecutionId, WorkflowExecutionKeys.status).getStatus();
  }

  @Override
  public WorkflowExecution fetchWorkflowExecution(String appId, String workflowExecutionId, String... projectedFields) {
    Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                         .filter(WorkflowExecutionKeys.appId, appId)
                                         .filter(WorkflowExecutionKeys.uuid, workflowExecutionId)
                                         .project(WorkflowExecutionKeys.appId, true)
                                         .project(WorkflowExecutionKeys.uuid, true);

    if (projectedFields != null) {
      for (String projectedField : projectedFields) {
        query.project(projectedField, true);
      }
    }
    WorkflowExecution workflowExecution = query.get();
    if (workflowExecution == null) {
      throw new InvalidRequestException("Workflow execution does not exist.");
    }
    return workflowExecution;
  }

  @Override
  public String fetchFailureDetails(String appId, String workflowExecutionId) {
    return workflowExecutionServiceHelper.fetchFailureDetails(appId, workflowExecutionId);
  }

  @Override
  public void populateFailureDetails(WorkflowExecution workflowExecution) {
    if (workflowExecution.getWorkflowType() == ORCHESTRATION && workflowExecution.getStatus() == FAILED) {
      workflowExecution.setFailureDetails(
          fetchFailureDetails(workflowExecution.getAppId(), workflowExecution.getUuid()));
    } else if (workflowExecution.getWorkflowType() == PIPELINE) {
      PipelineExecution pipelineExecution = workflowExecution.getPipelineExecution();
      if (pipelineExecution != null && isNotEmpty(pipelineExecution.getPipelineStageExecutions())) {
        pipelineExecution.getPipelineStageExecutions()
            .stream()
            .flatMap(pipelineStageExecution -> pipelineStageExecution.getWorkflowExecutions().stream())
            .filter(execution -> execution.getStatus() == FAILED || execution.getStatus() == REJECTED)
            .forEach(execution
                -> execution.setFailureDetails(fetchFailureDetails(execution.getAppId(), execution.getUuid())));
      }
    }
  }

  @Override
  public List<WorkflowExecution> getLatestSuccessWorkflowExecutions(String appId, String workflowId,
      List<String> serviceIds, int executionsToSkip, int executionsToIncludeInResponse) {
    return wingsPersistence.createQuery(WorkflowExecution.class)
        .filter("appId", appId)
        .filter(WorkflowExecutionKeys.workflowId, workflowId)
        .filter(WorkflowExecutionKeys.status, SUCCESS)
        .filter(WorkflowExecutionKeys.workflowType, ORCHESTRATION)
        .field(WorkflowExecutionKeys.serviceIds)
        .in(serviceIds)
        .order(Sort.descending(WorkflowExecutionKeys.createdAt))
        .asList(new FindOptions().skip(executionsToSkip).limit(executionsToIncludeInResponse));
  }

  @Override
  public PreviousApprovalDetails getPreviousApprovalDetails(
      String appId, String workflowExecutionId, String workflowId, String approvalId) {
    WorkflowExecution currentExecution = fetchWorkflowExecution(appId, workflowExecutionId,
        WorkflowExecutionKeys.createdAt, WorkflowExecutionKeys.pipelineExecution, WorkflowExecutionKeys.serviceIds,
        WorkflowExecutionKeys.infraDefinitionIds, WorkflowExecutionKeys.workflowType, WorkflowExecutionKeys.appId,
        WorkflowExecutionKeys.pipelineExecutionId);

    List<WorkflowExecution> pausedExecutions = wingsPersistence.createQuery(WorkflowExecution.class)
                                                   .filter(WorkflowExecutionKeys.appId, appId)
                                                   .filter(WorkflowExecutionKeys.workflowId, workflowId)
                                                   .field(WorkflowExecutionKeys.status)
                                                   .in(asList(PAUSED, RUNNING))
                                                   .field(WorkflowExecutionKeys.createdAt)
                                                   .lessThan(currentExecution.getCreatedAt())
                                                   .order(Sort.descending(WorkflowExecutionKeys.createdAt))
                                                   .limit(NO_LIMIT)
                                                   .asList();

    List<String> serviceIds = currentExecution.getServiceIds();
    List<String> infraIds = currentExecution.getInfraDefinitionIds();

    if (currentExecution.getWorkflowType() == ORCHESTRATION) {
      List<String> approvalIds = getPreviousApprovalIdsWithSameServicesAndInfraForWorkflow(
          currentExecution, pausedExecutions, serviceIds, infraIds, approvalId);
      return PreviousApprovalDetails.builder()
          .previousApprovals(approvalIds.stream().map(ApprovalInfo::new).collect(toList()))
          .size(approvalIds.size())
          .build();
    }

    PipelineStageExecution requiredStage =
        currentExecution.getPipelineExecution()
            .getPipelineStageExecutions()
            .stream()
            .filter(pse -> {
              if (pse.getStateType().equals(APPROVAL.name())) {
                ApprovalStateExecutionData stateExecutionData =
                    (ApprovalStateExecutionData) pse.getStateExecutionData();
                return PAUSED.equals(pse.getStatus())
                    && StringUtils.equals(stateExecutionData.getApprovalId(), approvalId);
              }
              return false;
            })
            .findFirst()
            .orElse(null);

    if (requiredStage != null) {
      String pipelineStageElementId = requiredStage.getPipelineStageElementId();
      List<String> approvalIds = getPreviousApprovalIdsWithSameServicesAndInfra(
          pausedExecutions, pipelineStageElementId, serviceIds, infraIds);
      return PreviousApprovalDetails.builder()
          .previousApprovals(approvalIds.stream().map(ApprovalInfo::new).collect(toList()))
          .size(approvalIds.size())
          .build();
    }
    return PreviousApprovalDetails.builder().previousApprovals(emptyList()).size(0).build();
  }

  List<String> getPreviousApprovalIdsWithSameServicesAndInfraForWorkflow(WorkflowExecution currentExecution,
      List<WorkflowExecution> pausedExecutions, List<String> serviceIds, List<String> infraIds,
      String currentApprovalId) {
    List<WorkflowExecution> executionsWithSameServiceAndInfra =
        pausedExecutions.stream()
            .filter(e
                -> CollectionUtils.isEqualCollection(e.getServiceIds(), serviceIds)
                    && CollectionUtils.isEqualCollection(e.getInfraDefinitionIds(), infraIds))
            .collect(toList());
    List<String> approvalIds = new ArrayList<>();

    // Removing workflow executions from the same pipeline execution
    if (currentExecution.getPipelineExecutionId() != null) {
      executionsWithSameServiceAndInfra.removeIf(
          e -> currentExecution.getPipelineExecutionId().equals(e.getPipelineExecutionId()));
    }

    List<ApprovalStateExecutionData> approvalDataForCurrentExecution =
        fetchApprovalStateExecutionsDataFromWorkflowExecution(currentExecution.getAppId(), currentExecution.getUuid());

    ApprovalStateExecutionData currentApprovalData =
        approvalDataForCurrentExecution.stream()
            .filter(approvalStateExecutionData -> currentApprovalId.equals(approvalStateExecutionData.getApprovalId()))
            .findFirst()
            .orElseThrow(() -> { throw new InvalidRequestException("Approval no longer in waiting state"); });

    executionsWithSameServiceAndInfra.forEach(execution -> {
      List<ApprovalStateExecutionData> approvalDataForWorkflow =
          fetchApprovalStateExecutionsDataFromWorkflowExecution(currentExecution.getAppId(), execution.getUuid());
      if (approvalDataForWorkflow != null) {
        approvalIds.addAll(
            approvalDataForWorkflow
                .stream()
                // Getting approval data only from states paused on the same step
                .filter(ad -> currentApprovalData.getApprovalStateIdentifier().equals(ad.getApprovalStateIdentifier()))
                .map(ApprovalStateExecutionData::getApprovalId)
                .collect(toList()));
      }
    });
    return approvalIds;
  }

  @Override
  public Boolean approveAndRejectPreviousExecutions(String accountId, String appId, String workflowExecutionId,
      String stateExecutionId, ApprovalDetails approvalDetails, PreviousApprovalDetails previousApprovalDetails) {
    ApprovalStateExecutionData stateExecutionData = fetchApprovalStateExecutionDataFromWorkflowExecution(
        appId, workflowExecutionId, stateExecutionId, approvalDetails);
    boolean success =
        approveOrRejectExecution(appId, stateExecutionData.getUserGroups(), approvalDetails, (String) null);
    List<String> previousApprovalIds = new ArrayList<>();
    if (previousApprovalDetails.getPreviousApprovals() != null) {
      previousApprovalIds =
          previousApprovalDetails.getPreviousApprovals().stream().map(ApprovalInfo::getApprovalId).collect(toList());
    }
    if (featureFlagService.isEnabled(AUTO_REJECT_PREVIOUS_APPROVALS, accountId)) {
      rejectPreviousDeployments(accountId, appId, workflowExecutionId, approvalDetails, previousApprovalIds);
    }
    return success;
  }

  private void rejectPreviousDeployments(String accountId, String appId, String workflowExecutionId,
      ApprovalDetails approvalDetails, List<String> previousApprovalIds) {
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(accountId);
    String executionUrl = "";
    String[] fields = {
        WorkflowExecutionKeys.workflowType, WorkflowExecutionKeys.orchestrationType, WorkflowExecutionKeys.envId};
    WorkflowExecution workflowExecution = getWorkflowExecution(appId, workflowExecutionId, fields);
    if (baseUrl != null) {
      if (workflowExecution.getWorkflowType() == PIPELINE) {
        executionUrl = generatePipelineExecutionUrl(accountId, appId, workflowExecutionId, baseUrl);
      } else {
        executionUrl = workflowNotificationHelper.calculateWorkflowUrl(workflowExecutionId,
            workflowExecution.getOrchestrationType(), accountId, appId, workflowExecution.getEnvId());
      }
    }

    if (isNotEmpty(previousApprovalIds)) {
      for (String approvalId : previousApprovalIds) {
        ApprovalDetails rejectionDetails = new ApprovalDetails();
        rejectionDetails.setApprovalId(approvalId);
        rejectionDetails.setComments(isEmpty(approvalDetails.getComments())
                ? (workflowExecution.getWorkflowType() == PIPELINE ? "Pipeline" : "Workflow")
                    + " rejected when the following execution was approved: " + executionUrl
                : approvalDetails.getComments());
        rejectionDetails.setAction(REJECT);
        approveOrRejectExecution(appId, rejectionDetails, null);
      }
    }
  }

  @Override
  public void rejectPreviousDeployments(String appId, String workflowExecutionId, ApprovalDetails approvalDetails) {
    WorkflowExecution execution = fetchWorkflowExecution(
        appId, workflowExecutionId, WorkflowExecutionKeys.workflowId, WorkflowExecutionKeys.accountId);
    PreviousApprovalDetails previousApprovalDetails = getPreviousApprovalDetails(
        appId, workflowExecutionId, execution.getWorkflowId(), approvalDetails.getApprovalId());
    List<String> previousApprovalIds = new ArrayList<>();
    if (previousApprovalDetails.getPreviousApprovals() != null) {
      previousApprovalIds =
          previousApprovalDetails.getPreviousApprovals().stream().map(ApprovalInfo::getApprovalId).collect(toList());
    }
    rejectPreviousDeployments(
        execution.getAccountId(), appId, workflowExecutionId, approvalDetails, previousApprovalIds);
  }

  private List<String> getPreviousApprovalIdsWithSameServicesAndInfra(List<WorkflowExecution> pausedExecutions,
      String pipelineStageElementId, List<String> serviceIds, List<String> infraIds) {
    List<WorkflowExecution> executionsWithSameServiceAndInfra =
        pausedExecutions.stream()
            .filter(e
                -> CollectionUtils.isEqualCollection(e.getServiceIds(), serviceIds)
                    && CollectionUtils.isEqualCollection(e.getInfraDefinitionIds(), infraIds))
            .collect(toList());

    List<String> approvalIds = new ArrayList<>();
    executionsWithSameServiceAndInfra.forEach(execution -> {
      Optional<PipelineStageExecution> pausedStage =
          execution.getPipelineExecution()
              .getPipelineStageExecutions()
              .stream()
              .filter(pse
                  -> PAUSED.equals(pse.getStatus()) && pipelineStageElementId.equals(pse.getPipelineStageElementId()))
              .findFirst();

      if (pausedStage.isPresent()) {
        if (pausedStage.get().getStateType().equals(APPROVAL.name())) {
          ApprovalStateExecutionData stateExecutionData =
              (ApprovalStateExecutionData) pausedStage.get().getStateExecutionData();
          approvalIds.add(stateExecutionData.getApprovalId());
        } else {
          String workflowExecutionId = pausedStage.get().getWorkflowExecutions().get(0).getUuid();
          List<ApprovalStateExecutionData> approvalDataForWorkflow =
              fetchApprovalStateExecutionsDataFromWorkflowExecution(execution.getAppId(), workflowExecutionId);
          if (approvalDataForWorkflow != null) {
            approvalIds.addAll(
                approvalDataForWorkflow.stream().map(ApprovalStateExecutionData::getApprovalId).collect(toList()));
          }
        }
      }
    });
    return approvalIds;
  }

  public WorkflowExecution getLastSuccessfulWorkflowExecution(
      String accountId, String appId, String workflowId, String envId, String serviceId, String infraMappingId) {
    if (isEmpty(workflowId)) {
      return null;
    }
    return wingsPersistence.createQuery(WorkflowExecution.class)
        .filter(WorkflowExecutionKeys.accountId, accountId)
        .filter(WorkflowExecutionKeys.appId, appId)
        .filter(WorkflowExecutionKeys.workflowId, workflowId)
        .filter(WorkflowExecutionKeys.envId, envId)
        .filter(WorkflowExecutionKeys.serviceIds, serviceId)
        .filter(WorkflowExecutionKeys.infraMappingIds, infraMappingId)
        .filter(WorkflowExecutionKeys.status, SUCCESS)
        .order(Sort.descending(WorkflowExecutionKeys.createdAt))
        .get();
  }

  public WorkflowExecution getLastWorkflowExecution(
      String accountId, String appId, String workflowId, String envId, String serviceId, String infraMappingId) {
    if (isEmpty(workflowId)) {
      return null;
    }
    return wingsPersistence.createQuery(WorkflowExecution.class)
        .filter(WorkflowExecutionKeys.appId, appId)
        .filter(WorkflowExecutionKeys.workflowId, workflowId)
        .filter(WorkflowExecutionKeys.infraMappingIds, infraMappingId)
        .order(Sort.descending(WorkflowExecutionKeys.createdAt))
        .get();
  }

  @Override
  public WorkflowExecutionInfo getWorkflowExecutionInfo(String appId, String workflowExecutionId) {
    WorkflowExecution workflowExecution =
        wingsPersistence.getWithAppId(WorkflowExecution.class, appId, workflowExecutionId);
    if (workflowExecution == null) {
      throw new InvalidRequestException("Couldn't find a workflow Execution with Id: " + workflowExecutionId, USER);
    }

    return WorkflowExecutionInfo.builder()
        .accountId(workflowExecution.getAccountId())
        .name(workflowExecution.getName())
        .appId(workflowExecution.getAppId())
        .executionId(workflowExecutionId)
        .workflowId(workflowExecution.getWorkflowId())
        .startTs(workflowExecution.getStartTs())
        .build();
  }

  @Override
  public WorkflowExecution getWorkflowExecutionWithFailureDetails(String appId, String workflowExecutionId) {
    // CALLERS READ MULTIPLE FIELDS OF WORKFLOW EXECUTION, LEAVE IT WITHOUT PROJECTION AT FIRST MOMENT.
    WorkflowExecution workflowExecution = getWorkflowExecution(appId, workflowExecutionId);
    workflowExecutionServiceHelper.populateFailureDetailsWithStepInfo(workflowExecution);
    return workflowExecution;
  }

  @Override
  public List<WorkflowExecution> getWorkflowExecutionsWithFailureDetails(
      String appId, List<WorkflowExecution> workflowExecutions) {
    return workflowExecutionServiceHelper.populateFailureDetailsWithStepInfo(appId, workflowExecutions);
  }

  @Override
  public WorkflowExecution getUpdatedWorkflowExecution(String appId, String workflowExecutionId) {
    return wingsPersistence.getWithAppId(WorkflowExecution.class, appId, workflowExecutionId);
  }

  @Override
  public void checkDeploymentFreezeRejectedExecution(
      String accountId, PreDeploymentChecker deploymentFreezeChecker, WorkflowExecution workflowExecution) {
    try {
      deploymentFreezeChecker.check(accountId);
    } catch (DeploymentFreezeException ex) {
      workflowExecution.setStatus(REJECTED);
      workflowExecution.setMessage(ex.getMessage());
      workflowExecution.setRejectedByFreezeWindowIds(ex.getDeploymentFreezeIds());
      workflowExecution.setRejectedByFreezeWindowNames(ex.getDeploymentFreezeNamesList());
      wingsPersistence.save(workflowExecution);
      throw ex;
    }
  }
}
