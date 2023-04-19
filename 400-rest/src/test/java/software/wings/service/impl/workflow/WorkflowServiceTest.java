/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.RUSHABH;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.SHUBHAM_MAHESHWARI;
import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.api.DeploymentType.AMI;
import static software.wings.api.DeploymentType.ECS;
import static software.wings.api.DeploymentType.KUBERNETES;
import static software.wings.api.DeploymentType.SSH;
import static software.wings.beans.AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping;
import static software.wings.beans.EntityType.APPDYNAMICS_CONFIGID;
import static software.wings.beans.EntityType.ELK_CONFIGID;
import static software.wings.beans.EntityType.ELK_INDICES;
import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.EntityType.INFRASTRUCTURE_DEFINITION;
import static software.wings.beans.EntityType.INFRASTRUCTURE_MAPPING;
import static software.wings.beans.EntityType.PROVISIONER;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.EntityType.WORKFLOW;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.GraphLink.Builder.aLink;
import static software.wings.beans.NotificationGroup.NotificationGroupBuilder.aNotificationGroup;
import static software.wings.beans.NotificationRule.NotificationRuleBuilder.aNotificationRule;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.AMI_DEPLOY_AUTOSCALING_GROUP;
import static software.wings.beans.PhaseStepType.AMI_SWITCH_AUTOSCALING_GROUP_ROUTES;
import static software.wings.beans.PhaseStepType.CLUSTER_SETUP;
import static software.wings.beans.PhaseStepType.COLLECT_ARTIFACT;
import static software.wings.beans.PhaseStepType.CONTAINER_DEPLOY;
import static software.wings.beans.PhaseStepType.CONTAINER_SETUP;
import static software.wings.beans.PhaseStepType.DEPLOY_AWS_LAMBDA;
import static software.wings.beans.PhaseStepType.DEPLOY_SERVICE;
import static software.wings.beans.PhaseStepType.DISABLE_SERVICE;
import static software.wings.beans.PhaseStepType.ENABLE_SERVICE;
import static software.wings.beans.PhaseStepType.INFRASTRUCTURE_NODE;
import static software.wings.beans.PhaseStepType.K8S_PHASE_STEP;
import static software.wings.beans.PhaseStepType.POST_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.PREPARE_STEPS;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.ROUTE_UPDATE;
import static software.wings.beans.PhaseStepType.SELECT_NODE;
import static software.wings.beans.PhaseStepType.VERIFY_SERVICE;
import static software.wings.beans.PhaseStepType.WRAP_UP;
import static software.wings.beans.PhysicalInfrastructureMapping.Builder.aPhysicalInfrastructureMapping;
import static software.wings.beans.Role.Builder.aRole;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.beans.workflow.StepSkipStrategy.Scope.SPECIFIC_STEPS;
import static software.wings.common.TemplateConstants.LATEST_TAG;
import static software.wings.common.WorkflowConstants.PHASE_NAME_PREFIX;
import static software.wings.common.WorkflowConstants.PHASE_STEP_VALIDATION_MESSAGE;
import static software.wings.common.WorkflowConstants.STEP_VALIDATION_MESSAGE;
import static software.wings.common.WorkflowConstants.WORKFLOW_VALIDATION_MESSAGE;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.DEPLOY_CONTAINERS;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.KUBERNETES_SWAP_SERVICES_PRIMARY_STAGE;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.UPGRADE_CONTAINERS;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.assertClonedWorkflowAcrossApps;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.assertLinkedPhaseStep;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.assertOrchestrationWorkflow;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.assertPhaseNode;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.assertPostDeployTemplateStep;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.assertPreDeployTemplateStep;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.assertTemplateStep;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.assertTemplateWorkflowPhase;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.assertTemplatizedOrchestrationWorkflow;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.assertTemplatizedWorkflow;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.assertWorkflowPhaseTemplateExpressions;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.assertWorkflowPhaseTemplateStep;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructAmiBGWorkflow;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructAmiInfraDef;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructAmiWorkflow;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructAppDVerifyStep;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructAppdTemplateExpressions;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructApprovalStep;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructAwsLambdaInfraDef;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructBasicDeploymentTemplateWorkflow;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructBasicWorkflow;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructBasicWorkflowWithInfraNodeDeployServicePhaseStep;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructBasicWorkflowWithInfraNodeDeployServicePhaseStepAndWinRmDeployment;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructBasicWorkflowWithInfraNodeDeployServicePhaseStepWithInfraDefinitionId;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructBasicWorkflowWithPhase;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructBasicWorkflowWithPhaseSteps;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructBlueGreenHelmWorkflow;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructBlueGreenWorkflow;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructBuildWorkflow;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructBuildWorkflowWithPhase;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructCanaryHttpAsPostDeploymentStep;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructCanaryWithApprovalPhaseStep;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructCanaryWithApprovalStep;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructCanaryWithHttpPhaseStep;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructCanaryWithHttpStep;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructCanaryWorkflow;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructCanaryWorkflowWithConcurrencyStrategy;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructCanaryWorkflowWithPhase;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructCanaryWorkflowWithTwoPhases;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructCloneMetadata;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructCommandTemplateStep;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructCustomWorkflow;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructDirectKubernetesInfraDef;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructEcsInfraDef;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructEcsWorkflow;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructElkTemplateExpressions;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructGKInfraDef;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructHELMInfra;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructHELMInfraDef;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructHelmWorkflowWithProperties;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructHttpTemplateStep;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructK8SBlueGreenWorkflow;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructK8SWorkflow;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructLinkedTemplate;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructMulitServiceTemplateWorkflow;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructMultiServiceWorkflow;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructMultiServiceWorkflowWithPhase;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructPhysicalInfraMapping;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructPipeline;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructServiceCommand;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructShellScriptTemplateStep;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructTemplatizedCanaryWorkflow;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructWfWithTrafficShiftSteps;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructWorkflowWithParam;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.getEnvTemplateExpression;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.getServiceTemplateExpression;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.prepareInfraDefTemplateExpression;
import static software.wings.sm.StateType.APPROVAL_RESUME;
import static software.wings.sm.StateType.ARTIFACT_COLLECT_LOOP_STATE;
import static software.wings.sm.StateType.AWS_CODEDEPLOY_STATE;
import static software.wings.sm.StateType.AWS_LAMBDA_VERIFICATION;
import static software.wings.sm.StateType.AWS_NODE_SELECT;
import static software.wings.sm.StateType.COLLECT_REMAINING_INSTANCES;
import static software.wings.sm.StateType.DC_NODE_SELECT;
import static software.wings.sm.StateType.ECS_SERVICE_DEPLOY;
import static software.wings.sm.StateType.ECS_SERVICE_SETUP;
import static software.wings.sm.StateType.ENV_LOOP_RESUME_STATE;
import static software.wings.sm.StateType.ENV_LOOP_STATE;
import static software.wings.sm.StateType.ENV_RESUME_STATE;
import static software.wings.sm.StateType.ENV_ROLLBACK_STATE;
import static software.wings.sm.StateType.ENV_STATE;
import static software.wings.sm.StateType.FORK;
import static software.wings.sm.StateType.HTTP;
import static software.wings.sm.StateType.JENKINS;
import static software.wings.sm.StateType.KUBERNETES_DEPLOY;
import static software.wings.sm.StateType.KUBERNETES_SETUP;
import static software.wings.sm.StateType.KUBERNETES_SETUP_ROLLBACK;
import static software.wings.sm.StateType.PAUSE;
import static software.wings.sm.StateType.PHASE;
import static software.wings.sm.StateType.PHASE_STEP;
import static software.wings.sm.StateType.REPEAT;
import static software.wings.sm.StateType.SCALYR;
import static software.wings.sm.StateType.SHELL_SCRIPT;
import static software.wings.sm.StateType.STAGING_ORIGINAL_EXECUTION;
import static software.wings.sm.StateType.SUB_WORKFLOW;
import static software.wings.sm.StateType.TEMPLATIZED_SECRET_MANAGER;
import static software.wings.sm.StateType.WAIT;
import static software.wings.sm.StepType.APPROVAL;
import static software.wings.sm.StepType.ARTIFACT_COLLECTION;
import static software.wings.sm.StepType.AZURE_NODE_SELECT;
import static software.wings.sm.StepType.BAMBOO;
import static software.wings.sm.StepType.BARRIER;
import static software.wings.sm.StepType.CLOUD_FORMATION_CREATE_STACK;
import static software.wings.sm.StepType.CLOUD_FORMATION_DELETE_STACK;
import static software.wings.sm.StepType.ECS_RUN_TASK;
import static software.wings.sm.StepType.ECS_STEADY_STATE_CHECK;
import static software.wings.sm.StepType.EMAIL;
import static software.wings.sm.StepType.GCB;
import static software.wings.sm.StepType.HELM_DEPLOY;
import static software.wings.sm.StepType.JIRA_CREATE_UPDATE;
import static software.wings.sm.StepType.K8S_APPLY;
import static software.wings.sm.StepType.K8S_BLUE_GREEN_DEPLOY;
import static software.wings.sm.StepType.K8S_CANARY_DEPLOY;
import static software.wings.sm.StepType.K8S_DELETE;
import static software.wings.sm.StepType.K8S_DEPLOYMENT_ROLLING;
import static software.wings.sm.StepType.K8S_SCALE;
import static software.wings.sm.StepType.K8S_TRAFFIC_SPLIT;
import static software.wings.sm.StepType.KUBERNETES_SWAP_SERVICE_SELECTORS;
import static software.wings.sm.StepType.NEW_RELIC_DEPLOYMENT_MARKER;
import static software.wings.sm.StepType.RANCHER_K8S_CANARY_DEPLOY;
import static software.wings.sm.StepType.RANCHER_K8S_DELETE;
import static software.wings.sm.StepType.RANCHER_K8S_DEPLOYMENT_ROLLING;
import static software.wings.sm.StepType.RANCHER_KUBERNETES_SWAP_SERVICE_SELECTORS;
import static software.wings.sm.StepType.RANCHER_RESOLVE;
import static software.wings.sm.StepType.RESOURCE_CONSTRAINT;
import static software.wings.sm.StepType.SERVICENOW_CREATE_UPDATE;
import static software.wings.sm.StepType.TERRAFORM_APPLY;
import static software.wings.sm.StepType.TERRAGRUNT_DESTROY;
import static software.wings.sm.StepType.TERRAGRUNT_PROVISION;
import static software.wings.sm.states.AwsCodeDeployState.ARTIFACT_S3_BUCKET_EXPRESSION;
import static software.wings.sm.states.AwsCodeDeployState.ARTIFACT_S3_KEY_EXPRESSION;
import static software.wings.stencils.WorkflowStepType.APM;
import static software.wings.stencils.WorkflowStepType.ARTIFACT;
import static software.wings.stencils.WorkflowStepType.AWS_AMI;
import static software.wings.stencils.WorkflowStepType.AWS_SSH;
import static software.wings.stencils.WorkflowStepType.CI_SYSTEM;
import static software.wings.stencils.WorkflowStepType.DC_SSH;
import static software.wings.stencils.WorkflowStepType.FLOW_CONTROL;
import static software.wings.stencils.WorkflowStepType.ISSUE_TRACKING;
import static software.wings.stencils.WorkflowStepType.LOG;
import static software.wings.stencils.WorkflowStepType.NOTIFICATION;
import static software.wings.stencils.WorkflowStepType.SERVICE_COMMAND;
import static software.wings.stencils.WorkflowStepType.UTILITY;
import static software.wings.utils.ArtifactType.DOCKER;
import static software.wings.utils.ArtifactType.WAR;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID_ARTIFACTORY;
import static software.wings.utils.WingsTestConstants.BUILD_NO;
import static software.wings.utils.WingsTestConstants.COMPUTE_PROVIDER_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID_CHANGED;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID_CHANGED;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.INFRA_NAME;
import static software.wings.utils.WingsTestConstants.JENKINS_URL;
import static software.wings.utils.WingsTestConstants.NOTIFICATION_GROUP_ID;
import static software.wings.utils.WingsTestConstants.PASSWORD;
import static software.wings.utils.WingsTestConstants.PHASE_VALIDATION_MESSAGE;
import static software.wings.utils.WingsTestConstants.ROLE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID_CHANGED;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.TARGET_APP_ID;
import static software.wings.utils.WingsTestConstants.TARGET_SERVICE_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.USER_GROUP_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;
import static software.wings.utils.WingsTestConstants.UUID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.groups.Tuple.tuple;
import static org.assertj.core.util.Lists.newArrayList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.exception.FailureType;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.limits.ActionType;
import io.harness.limits.LimitCheckerFactory;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import software.wings.WingsBaseTest;
import software.wings.api.CloudProviderType;
import software.wings.api.DeploymentType;
import software.wings.app.GeneralNotifyEventListener;
import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.ArtifactStreamMetadata;
import software.wings.beans.ArtifactVariable;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.BasicOrchestrationWorkflow;
import software.wings.beans.BlueGreenOrchestrationWorkflow;
import software.wings.beans.BuildWorkflow;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.CustomOrchestrationWorkflow;
import software.wings.beans.EntityType;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.FailureCriteria;
import software.wings.beans.FailureStrategy;
import software.wings.beans.Graph;
import software.wings.beans.GraphNode;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.MultiServiceOrchestrationWorkflow;
import software.wings.beans.NameValuePair;
import software.wings.beans.NotificationGroup;
import software.wings.beans.NotificationRule;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.PhaseStep;
import software.wings.beans.PhaseStepType;
import software.wings.beans.Pipeline;
import software.wings.beans.Role;
import software.wings.beans.RoleType;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.TemplateExpression;
import software.wings.beans.TrafficShiftMetadata;
import software.wings.beans.User;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowCategorySteps;
import software.wings.beans.WorkflowCategoryStepsMeta;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.artifact.ArtifactInput;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamSummary;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.artifact.ArtifactSummary;
import software.wings.beans.artifact.ArtifactoryArtifactStream;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.beans.command.Command;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.concurrency.ConcurrencyStrategy;
import software.wings.beans.concurrency.ConcurrencyStrategy.UnitType;
import software.wings.beans.peronalization.Personalization;
import software.wings.beans.peronalization.PersonalizationSteps;
import software.wings.beans.security.UserGroup;
import software.wings.beans.stats.CloneMetadata;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateType;
import software.wings.beans.template.command.HttpTemplate;
import software.wings.beans.workflow.StepSkipStrategy;
import software.wings.dl.WingsPersistence;
import software.wings.infra.AwsAmiInfrastructure;
import software.wings.infra.AwsEcsInfrastructure;
import software.wings.infra.AwsInstanceInfrastructure;
import software.wings.infra.DirectKubernetesInfrastructure;
import software.wings.infra.GoogleKubernetesEngine;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.PhysicalInfra;
import software.wings.persistence.artifact.Artifact;
import software.wings.rules.Listeners;
import software.wings.service.StaticMap;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.workflow.creation.K8V2BlueGreenWorkflowCreator;
import software.wings.service.impl.workflow.creation.abstractfactories.AbstractWorkflowFactory;
import software.wings.service.impl.workflow.creation.abstractfactories.K8sV2WorkflowFactory;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.EntityVersionService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.HarnessTagService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.NotificationSetupService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.personalization.PersonalizationService;
import software.wings.service.intfc.template.TemplateService;
import software.wings.service.intfc.yaml.EntityUpdateService;
import software.wings.service.intfc.yaml.YamlDirectoryService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.settings.SettingVariableTypes;
import software.wings.sm.State;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateMachine;
import software.wings.sm.StateMachineTestBase.StateSync;
import software.wings.sm.StateType;
import software.wings.sm.StateTypeScope;
import software.wings.sm.StepType;
import software.wings.sm.Transition;
import software.wings.sm.TransitionType;
import software.wings.stencils.Stencil;
import software.wings.stencils.StencilPostProcessor;
import software.wings.stencils.WorkflowStepType;
import software.wings.utils.WingsTestConstants.MockChecker;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscaler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

/**
 * The Class WorkflowServiceTest.
 *
 * @author Rishi
 */
@Slf4j
@OwnedBy(CDC)
@Listeners(GeneralNotifyEventListener.class)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class WorkflowServiceTest extends WingsBaseTest {
  private static final String CLONE = " - (clone)";
  private static String envId = generateUuid();

  @Inject private HPersistence persistence;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private InfrastructureDefinitionService infrastructureDefinitionService;
  @Mock private AppService appService;
  @Mock private AccountService accountService;
  @Mock private NotificationSetupService notificationSetupService;
  @Mock private Application application;
  @Mock private Account account;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private PipelineService pipelineService;
  @Mock private EntityUpdateService entityUpdateService;
  @Mock private YamlDirectoryService yamlDirectoryService;
  @Mock private YamlPushService yamlPushService;
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private ArtifactService artifactService;
  @Mock private ArtifactStream artifactStream;
  @Mock private TriggerService triggerService;
  @Mock private EnvironmentService environmentService;
  @Mock private TemplateService templateService;
  @Mock private UserGroupService userGroupService;
  @Mock FeatureFlagService featureFlagService;
  @Mock private ServiceTemplateService serviceTemplateService;
  @Mock private PersonalizationService personalizationService;
  @Mock private AuditServiceHelper auditServiceHelper;
  @Mock private HarnessTagService harnessTagService;
  @Spy @Inject private AbstractWorkflowFactory abstractWorkflowFactory;

  @InjectMocks @Inject private WorkflowServiceHelper workflowServiceHelper;
  @InjectMocks @Inject private WorkflowServiceTemplateHelper workflowServiceTemplateHelper;
  @InjectMocks @Inject private K8V2BlueGreenWorkflowCreator k8V2BlueGreenWorkflowCreator;

  private StencilPostProcessor stencilPostProcessor = mock(StencilPostProcessor.class,
      (Answer<List<Stencil>>) invocationOnMock -> (List<Stencil>) invocationOnMock.getArguments()[0]);

  @Mock private UpdateOperations<Workflow> updateOperations;
  @Mock private LimitCheckerFactory limitCheckerFactory;

  @Inject @InjectMocks private EntityVersionService entityVersionService;

  @InjectMocks @Inject private WorkflowService workflowService;
  @Mock private FieldEnd fieldEnd;

  private Service service = Service.builder().name(SERVICE_NAME).uuid(SERVICE_ID).artifactType(WAR).build();
  private User user = User.Builder.anUser().uuid("invalid").email("invalid@abcd.com").build();

  /**
   * Sets mocks.
   */
  @Before
  public void setupMocks() {
    when(limitCheckerFactory.getInstance(Mockito.any(io.harness.limits.Action.class)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_WORKFLOW));

    Mockito.doNothing().when(yamlPushService).pushYamlChangeSet(any(), any(), any(), any(), anyBoolean());

    when(appService.get(APP_ID)).thenReturn(application);
    when(accountService.get(any())).thenReturn(account);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(workflowExecutionService.workflowExecutionsRunning(WorkflowType.ORCHESTRATION, APP_ID, WORKFLOW_ID))
        .thenReturn(false);
    when(appService.get(TARGET_APP_ID)).thenReturn(Application.Builder.anApplication().accountId(ACCOUNT_ID).build());

    when(environmentService.get(APP_ID, ENV_ID, false))
        .thenReturn(anEnvironment().uuid(ENV_ID).name(ENV_NAME).appId(APP_ID).build());

    when(environmentService.exist(APP_ID, ENV_ID)).thenReturn(true);

    when(environmentService.get(APP_ID, ENV_ID))
        .thenReturn(anEnvironment().uuid(ENV_ID).name(ENV_NAME).appId(APP_ID).build());

    when(serviceResourceService.get(APP_ID, SERVICE_ID, false)).thenReturn(service);
    when(serviceResourceService.get(SERVICE_ID)).thenReturn(service);
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(service);
    when(serviceResourceService.fetchServicesByUuids(APP_ID, java.util.Arrays.asList(SERVICE_ID)))
        .thenReturn(java.util.Arrays.asList(service));
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID))
        .thenReturn(anAwsInfrastructureMapping()
                        .withUuid(INFRA_MAPPING_ID)
                        .withServiceId(SERVICE_ID)
                        .withDeploymentType(SSH.name())
                        .withInfraMappingType(InfrastructureMappingType.AWS_SSH.name())
                        .withComputeProviderType(SettingVariableTypes.AWS.name())
                        .build());
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(InfrastructureDefinition.builder()
                        .appId(APP_ID)
                        .uuid(INFRA_DEFINITION_ID)
                        .deploymentType(SSH)
                        .infrastructure(PhysicalInfra.builder().build())
                        .cloudProviderType(CloudProviderType.PHYSICAL_DATA_CENTER)
                        .build());
    when(userGroupService.getDefaultUserGroup(Mockito.any()))
        .thenReturn(UserGroup.builder().uuid("some-user-group-id").build());
    when(featureFlagService.isEnabled(eq(FeatureName.DEFAULT_ARTIFACT), any())).thenReturn(true);
    Role role = aRole()
                    .withRoleType(RoleType.ACCOUNT_ADMIN)
                    .withUuid(ROLE_ID)
                    .withAccountId(application.getAccountId())
                    .build();
    List<NotificationGroup> notificationGroups = java.util.Arrays.asList(aNotificationGroup()
                                                                             .withUuid(NOTIFICATION_GROUP_ID)
                                                                             .withAccountId(application.getAccountId())
                                                                             .withRole(role)
                                                                             .build());
    when(notificationSetupService.listNotificationGroups(
             application.getAccountId(), RoleType.ACCOUNT_ADMIN.getDisplayName()))
        .thenReturn(notificationGroups);
    when(personalizationService.fetch(any(), any(), any())).thenReturn(null);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldReadWorkflow() {
    Workflow workflow = workflowService.createWorkflow(constructBasicWorkflow());
    assertThat(workflow).isNotNull();
    assertThat(workflowService.readWorkflowWithoutOrchestration(APP_ID, workflow.getUuid())).isNotNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldSaveAndRead() {
    StateMachine sm = new StateMachine();
    sm.setAppId(APP_ID);
    State stateA = new StateSync("stateA" + StaticMap.getUnique());
    sm.addState(stateA);
    StateSync stateB = new StateSync("stateB" + StaticMap.getUnique());
    sm.addState(stateB);
    StateSync stateC = new StateSync("stateC" + StaticMap.getUnique());
    sm.addState(stateC);
    sm.setInitialStateName(stateA.getName());

    sm.addTransition(Transition.Builder.aTransition()
                         .withFromState(stateA)
                         .withTransitionType(TransitionType.SUCCESS)
                         .withToState(stateB)
                         .build());
    sm.addTransition(Transition.Builder.aTransition()
                         .withFromState(stateB)
                         .withTransitionType(TransitionType.SUCCESS)
                         .withToState(stateC)
                         .build());

    sm = workflowService.createStateMachine(sm);
    assertThat(sm).isNotNull().extracting(StateMachine::getUuid).isNotNull();
    String smId = sm.getUuid();
    sm = persistence.get(StateMachine.class, smId);
    assertThat(sm).isNotNull().extracting(StateMachine::getUuid).isNotNull();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void shouldReturnCorrectExecutionStatus() {
    String stateExecutionId = generateUuid();
    StateExecutionInstance stateExecutionInstance =
        StateExecutionInstance.Builder.aStateExecutionInstance().status(ExecutionStatus.QUEUED).build();
    when(workflowExecutionService.getStateExecutionData(eq(APP_ID), eq(stateExecutionId)))
        .thenReturn(stateExecutionInstance);
    ExecutionStatus executionStatus = workflowService.getExecutionStatus(APP_ID, stateExecutionId);
    assertThat(executionStatus).isEqualTo(ExecutionStatus.QUEUED);
  }

  /**
   * Should create workflow.
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCreateCustomWorkflow() {
    createCustomWorkflow();
  }

  /**
   * Clone workflow within the same application
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCloneWorkflow() {
    Workflow workflow2 = workflowService.createWorkflow(constructCanaryWorkflowWithPhase());
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid");

    Workflow clonedWorkflow = workflowService.cloneWorkflow(APP_ID, workflow2,
        CloneMetadata.builder().workflow(aWorkflow().name(WORKFLOW_NAME + "-clone").build()).build());

    assertClonedWorkflow(workflow2, clonedWorkflow);
  }

  private void assertClonedWorkflow(Workflow workflow2, Workflow clonedWorkflow) {
    assertThat(clonedWorkflow).isNotNull();
    assertThat(clonedWorkflow.getUuid()).isNotEqualTo(workflow2.getUuid());
    assertThat(clonedWorkflow.getAppId()).isEqualTo(workflow2.getAppId());
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow();
    CanaryOrchestrationWorkflow clonedOrchestrationWorkflow =
        (CanaryOrchestrationWorkflow) clonedWorkflow.getOrchestrationWorkflow();
    assertThat(clonedOrchestrationWorkflow).isNotNull();
    assertThat(clonedOrchestrationWorkflow.getOrchestrationWorkflowType())
        .isEqualTo(orchestrationWorkflow.getOrchestrationWorkflowType());

    assertThat(clonedOrchestrationWorkflow.getWorkflowPhases()).isNotNull().hasSize(1);
  }

  /**
   * Clone workflow within the same application
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCloneWorkflowAcrossApps() {
    when(serviceResourceService.getWithDetails(TARGET_APP_ID, TARGET_SERVICE_ID))
        .thenReturn(Service.builder().uuid(TARGET_SERVICE_ID).artifactType(WAR).build());
    when(serviceResourceService.get(TARGET_APP_ID, TARGET_SERVICE_ID, false))
        .thenReturn(Service.builder().uuid(TARGET_SERVICE_ID).artifactType(WAR).build());

    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID))
        .thenReturn(anAwsInfrastructureMapping()
                        .withUuid(INFRA_MAPPING_ID)
                        .withDeploymentType(SSH.name())
                        .withComputeProviderType(SettingVariableTypes.AWS.name())
                        .build());

    Workflow workflow1 = constructCanaryWorkflowWithPhase();

    Workflow workflow2 = workflowService.createWorkflow(workflow1);
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid");

    CloneMetadata cloneMetadata = constructCloneMetadata(workflow2);

    Workflow clonedWorkflow = workflowService.cloneWorkflow(APP_ID, workflow2, cloneMetadata);
    assertClonedWorkflowAcrossApps(workflow2, clonedWorkflow);
  }

  /**
   * Clone workflow within the same application
   */
  @Test(expected = WingsException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCloneWorkflowAcrossAppsDifferentArtifactType() {
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(service);
    when(serviceResourceService.get(APP_ID, SERVICE_ID, false))
        .thenReturn(Service.builder().uuid(SERVICE_ID).artifactType(DOCKER).build());
    when(serviceResourceService.getWithDetails(TARGET_APP_ID, TARGET_SERVICE_ID))
        .thenReturn(Service.builder().uuid(TARGET_SERVICE_ID).artifactType(WAR).build());
    when(serviceResourceService.get(TARGET_APP_ID, TARGET_SERVICE_ID, false))
        .thenReturn(Service.builder().uuid(TARGET_SERVICE_ID).artifactType(WAR).build());

    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID))
        .thenReturn(anAwsInfrastructureMapping()
                        .withUuid(INFRA_MAPPING_ID)
                        .withDeploymentType(SSH.name())
                        .withComputeProviderType(SettingVariableTypes.AWS.name())
                        .build());

    Workflow workflow1 = constructCanaryWorkflow();

    Workflow workflow2 = workflowService.createWorkflow(workflow1);
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid");

    CloneMetadata cloneMetadata = constructCloneMetadata(workflow2);
    workflowService.cloneWorkflow(APP_ID, workflow2, cloneMetadata);
  }

  /**
   * Should fail Clone workflow with invalid name within the same application
   */
  @Test(expected = ConstraintViolationException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotCloneWorkflowIfInvalidName() {
    Workflow workflow2 = workflowService.createWorkflow(constructCanaryWorkflowWithPhase());
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid");

    workflowService.cloneWorkflow(
        APP_ID, workflow2, CloneMetadata.builder().workflow(aWorkflow().name(WORKFLOW_NAME + CLONE).build()).build());
  }

  /**
   * Should fail Clone workflow with invalid name across applications
   */
  @Test(expected = ConstraintViolationException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotCloneWorkflowAcrossAppsIfInvalidName() {
    when(serviceResourceService.getWithDetails(TARGET_APP_ID, TARGET_SERVICE_ID))
        .thenReturn(Service.builder().uuid(TARGET_SERVICE_ID).artifactType(WAR).build());
    when(serviceResourceService.get(TARGET_APP_ID, TARGET_SERVICE_ID, false))
        .thenReturn(Service.builder().uuid(TARGET_SERVICE_ID).artifactType(WAR).build());

    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID))
        .thenReturn(anAwsInfrastructureMapping()
                        .withUuid(INFRA_MAPPING_ID)
                        .withDeploymentType(SSH.name())
                        .withComputeProviderType(SettingVariableTypes.AWS.name())
                        .build());

    Workflow workflow1 = constructCanaryWorkflowWithPhase();

    Workflow workflow2 = workflowService.createWorkflow(workflow1);
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid");
    workflow2.setName(WORKFLOW_NAME + CLONE);
    CloneMetadata cloneMetadata = constructCloneMetadata(workflow2);

    workflowService.cloneWorkflow(APP_ID, workflow2, cloneMetadata);
  }

  /**
   * Should update workflow.
   */
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldUpdateCustomWorkflow() {
    Workflow workflow = createCustomWorkflow();

    workflow.setName("workflow2");
    workflow.setDescription(null);

    Graph graph2 =
        JsonUtils.clone(((CustomOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).getGraph(), Graph.class);
    graph2.addNode(GraphNode.builder().id("n5").name("http").type(HTTP.name()).build());
    graph2.getLinks().add(aLink().withId("l3").withFrom("n3").withTo("n5").withType("success").build());

    Workflow updatedWorkflow = workflowService.updateWorkflow(workflow, false);
    assertThat(updatedWorkflow)
        .isNotNull()
        .isEqualToComparingOnlyGivenFields(workflow, "uuid", "name", "description", "orchestrationWorkflow")
        .hasFieldOrPropertyWithValue("defaultVersion", 2);

    PageResponse<StateMachine> res = findStateMachine(workflow);

    assertThat(res).isNotNull().hasSize(2);
    assertThat(res.get(0))
        .isNotNull()
        .hasFieldOrPropertyWithValue("orchestrationWorkflow", updatedWorkflow.getOrchestrationWorkflow())
        .hasFieldOrPropertyWithValue("originId", workflow.getUuid())
        .hasFieldOrPropertyWithValue("originVersion", 2);
    assertThat(res.get(1))
        .isNotNull()
        .hasFieldOrPropertyWithValue("orchestrationWorkflow", workflow.getOrchestrationWorkflow())
        .hasFieldOrPropertyWithValue("originId", workflow.getUuid())
        .hasFieldOrPropertyWithValue("originVersion", 1);
  }

  /**
   * Should delete workflow.
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldDeleteWorkflow() {
    Workflow workflow = createCustomWorkflow();
    String uuid = workflow.getUuid();
    when(pipelineService.listPipelines(any(PageRequest.class))).thenReturn(aPageResponse().build());
    workflowService.deleteWorkflow(APP_ID, uuid);
    workflow = workflowService.readWorkflow(APP_ID, uuid, null);
    assertThat(workflow).isNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldPruneDescendingObjects() {
    workflowService.pruneDescendingEntities(APP_ID, WORKFLOW_ID);
    InOrder inOrder = inOrder(triggerService);
    inOrder.verify(triggerService).pruneByWorkflow(APP_ID, WORKFLOW_ID);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnReferencedWorkflowDelete() {
    Workflow workflow = createCustomWorkflow();
    String workflowId = workflow.getUuid();
    Pipeline pipeline = constructPipeline(workflowId);

    when(pipelineService.listPipelines(any(PageRequest.class)))
        .thenReturn(aPageResponse().withResponse(java.util.Arrays.asList(pipeline)).build());
    assertThatThrownBy(() -> workflowService.deleteWorkflow(APP_ID, workflowId))
        .isInstanceOf(WingsException.class)
        .hasMessage("Workflow is referenced by 1 pipeline [PIPELINE_NAME].");
  }

  /**
   * Should delete workflow.
   */
  @Test(expected = WingsException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void deleteWorkflowExecutionInProgress() {
    Workflow workflow = createCustomWorkflow();
    String uuid = workflow.getUuid();
    when(workflowExecutionService.runningExecutionsPresent(APP_ID, uuid)).thenReturn(true);
    when(pipelineService.listPipelines(any(PageRequest.class))).thenReturn(aPageResponse().build());
    workflowService.deleteWorkflow(APP_ID, uuid);
    workflow = workflowService.readWorkflow(APP_ID, uuid, null);
    assertThat(workflow).isNull();
  }

  private Workflow createCustomWorkflow() {
    Workflow workflow = workflowService.createWorkflow(constructCustomWorkflow());
    assertThat(workflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("defaultVersion", 1);

    PageResponse<StateMachine> res = findStateMachine(workflow);

    assertThat(res).isNotNull().hasSize(1);
    assertThat(res.get(0))
        .isNotNull()
        .hasFieldOrPropertyWithValue("orchestrationWorkflow", workflow.getOrchestrationWorkflow())
        .hasFieldOrPropertyWithValue("originId", workflow.getUuid())
        .hasFieldOrPropertyWithValue("originVersion", 1);
    return workflow;
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void stencils() throws IllegalArgumentException {
    Map<StateTypeScope, List<Stencil>> stencils = workflowService.stencils(APP_ID, null, null);
    log.debug(JsonUtils.asJson(stencils));
    assertThat(stencils).isNotNull().hasSize(4).containsKeys(StateTypeScope.ORCHESTRATION_STENCILS,
        StateTypeScope.PIPELINE_STENCILS, StateTypeScope.NONE, StateTypeScope.COMMON);
    assertThat(stencils.get(StateTypeScope.ORCHESTRATION_STENCILS))
        .extracting(Stencil::getType)
        .contains("REPEAT", "FORK");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void stencilsForPipeline() throws IllegalArgumentException {
    Map<StateTypeScope, List<Stencil>> stencils =
        workflowService.stencils(APP_ID, null, null, StateTypeScope.PIPELINE_STENCILS);
    log.debug(JsonUtils.asJson(stencils));
    assertThat(stencils).isNotNull().hasSize(1).containsKeys(StateTypeScope.PIPELINE_STENCILS);
    assertThat(stencils.get(StateTypeScope.PIPELINE_STENCILS))
        .extracting(Stencil::getType)
        .contains("APPROVAL", "ENV_STATE")
        .doesNotContain("REPEAT", "FORK");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void stencilsForOrchestration() throws IllegalArgumentException {
    Map<StateTypeScope, List<Stencil>> stencils =
        workflowService.stencils(APP_ID, null, null, StateTypeScope.ORCHESTRATION_STENCILS);
    log.debug(JsonUtils.asJson(stencils));
    assertThat(stencils).isNotNull().hasSize(1).containsKeys(StateTypeScope.ORCHESTRATION_STENCILS);
    assertThat(stencils.get(StateTypeScope.ORCHESTRATION_STENCILS))
        .extracting(Stencil::getType)
        .doesNotContain("BUILD", "ENV_STATE")
        .contains("REPEAT", "FORK");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void stencilsForOrchestrationFilterWorkflow() throws IllegalArgumentException {
    Workflow workflow2 = workflowService.createWorkflow(constructCanaryWorkflow());
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);
    Map<StateTypeScope, List<Stencil>> stencils =
        workflowService.stencils(APP_ID, workflow2.getUuid(), null, StateTypeScope.ORCHESTRATION_STENCILS);

    log.debug(JsonUtils.asJson(stencils));
    assertThat(stencils).isNotNull().hasSize(1).containsKeys(StateTypeScope.ORCHESTRATION_STENCILS);
    assertThat(stencils.get(StateTypeScope.ORCHESTRATION_STENCILS))
        .extracting(Stencil::getType)
        .doesNotContain("BUILD", "ENV_STATE")
        .contains("REPEAT", "FORK", "HTTP");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void stencilsForBuildWorkflow() throws IllegalArgumentException {
    Workflow workflow = workflowService.createWorkflow(constructBuildWorkflow());
    assertThat(workflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);
    Map<StateTypeScope, List<Stencil>> stencils =
        workflowService.stencils(APP_ID, workflow.getUuid(), null, StateTypeScope.ORCHESTRATION_STENCILS);

    assertThat(stencils).isNotNull().hasSize(1).containsKeys(StateTypeScope.ORCHESTRATION_STENCILS);
    assertThat(stencils.get(StateTypeScope.ORCHESTRATION_STENCILS))
        .extracting(Stencil::getType)
        .doesNotContain("BUILD", "ENV_STATE")
        .contains(REPEAT.name(), FORK.name(), HTTP.name(), StateType.ARTIFACT_COLLECTION.name());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void stencilsForOrchestrationFilterWorkflowPhase() throws IllegalArgumentException {
    mockAwsInfraDef(INFRA_DEFINITION_ID);
    Map<StateTypeScope, List<Stencil>> stencils = getStateTypeScopeListMap();

    assertThat(stencils).isNotNull().hasSize(1).containsKeys(StateTypeScope.ORCHESTRATION_STENCILS);
    List<Stencil> stencilList = stencils.get(StateTypeScope.ORCHESTRATION_STENCILS);
    assertThat(stencilList)
        .extracting(Stencil::getType)
        .doesNotContain("BUILD", "ENV_STATE", StateType.ARTIFACT_COLLECTION.name())
        .contains("REPEAT", "FORK", "HTTP", AWS_NODE_SELECT.name(), AWS_LAMBDA_VERIFICATION.name());
  }

  private Map<StateTypeScope, List<Stencil>> getStateTypeScopeListMap() {
    ServiceCommand serviceCommand = constructServiceCommand();

    when(serviceResourceService.get(APP_ID, SERVICE_ID, true))
        .thenReturn(Service.builder().uuid(SERVICE_ID).serviceCommands(ImmutableList.of(serviceCommand)).build());

    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID))
        .thenReturn(Service.builder().uuid(SERVICE_ID).serviceCommands(ImmutableList.of(serviceCommand)).build());

    Workflow workflow2 = workflowService.createWorkflow(constructBasicWorkflow());
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);

    BasicOrchestrationWorkflow basicOrchestrationWorkflow =
        (BasicOrchestrationWorkflow) workflow2.getOrchestrationWorkflow();
    return workflowService.stencils(APP_ID, workflow2.getUuid(),
        basicOrchestrationWorkflow.getWorkflowPhases().get(0).getUuid(), StateTypeScope.ORCHESTRATION_STENCILS);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void stencilsForOrchestrationFilterGKInfra() throws IllegalArgumentException {
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(InfrastructureDefinition.builder()
                        .uuid(INFRA_DEFINITION_ID)
                        .deploymentType(KUBERNETES)
                        .cloudProviderType(CloudProviderType.GCP)
                        .infrastructure(GoogleKubernetesEngine.builder().build())
                        .build());

    Map<StateTypeScope, List<Stencil>> stencils = getStateTypeScopeListMap();

    assertThat(stencils).isNotNull().hasSize(1).containsKeys(StateTypeScope.ORCHESTRATION_STENCILS);
    List<Stencil> stencilList = stencils.get(StateTypeScope.ORCHESTRATION_STENCILS);
    assertThat(stencilList)
        .extracting(Stencil::getType)
        .doesNotContain("BUILD", "ENV_STATE", StateType.ARTIFACT_COLLECTION.name(), ECS_SERVICE_SETUP.name(),
            ECS_SERVICE_DEPLOY.name(), StateType.ECS_STEADY_STATE_CHECK.name())
        .contains("REPEAT", "FORK", "HTTP", KUBERNETES_SETUP.name(), KUBERNETES_SETUP_ROLLBACK.name(),
            KUBERNETES_DEPLOY.name(), StateType.KUBERNETES_STEADY_STATE_CHECK.name());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void stencilsForOrchestrationFilterECSInfra() throws IllegalArgumentException {
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(InfrastructureDefinition.builder()
                        .uuid(INFRA_DEFINITION_ID)
                        .cloudProviderType(CloudProviderType.AWS)
                        .deploymentType(ECS)
                        .infrastructure(AwsEcsInfrastructure.builder().build())
                        .build());

    Map<StateTypeScope, List<Stencil>> stencils = getStateTypeScopeListMap();

    assertThat(stencils).isNotNull().hasSize(1).containsKeys(StateTypeScope.ORCHESTRATION_STENCILS);
    List<Stencil> stencilList = stencils.get(StateTypeScope.ORCHESTRATION_STENCILS);
    assertThat(stencilList)
        .extracting(Stencil::getType)
        .doesNotContain("BUILD", "ENV_STATE", StateType.ARTIFACT_COLLECTION.name(), KUBERNETES_SETUP.name(),
            KUBERNETES_SETUP_ROLLBACK.name(), KUBERNETES_DEPLOY.name(), StateType.KUBERNETES_STEADY_STATE_CHECK.name())
        .contains("REPEAT", "FORK", "HTTP", ECS_SERVICE_SETUP.name(), ECS_SERVICE_DEPLOY.name(),
            StateType.ECS_STEADY_STATE_CHECK.name());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void stencilsForOrchestrationFilterPhysicalInfra() throws IllegalArgumentException {
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID))
        .thenReturn(aPhysicalInfrastructureMapping()
                        .withUuid(INFRA_MAPPING_ID)
                        .withServiceId(SERVICE_ID)
                        .withDeploymentType(SSH.name())
                        .withInfraMappingType(InfrastructureMappingType.PHYSICAL_DATA_CENTER_SSH.name())
                        .withComputeProviderType(SettingVariableTypes.PHYSICAL_DATA_CENTER.name())
                        .build());

    Map<StateTypeScope, List<Stencil>> stencils = getStateTypeScopeListMap();

    assertThat(stencils).isNotNull().hasSize(1).containsKeys(StateTypeScope.ORCHESTRATION_STENCILS);
    List<Stencil> stencilList = stencils.get(StateTypeScope.ORCHESTRATION_STENCILS);
    assertThat(stencilList)
        .extracting(Stencil::getType)
        .doesNotContain("BUILD", "ENV_STATE", StateType.ARTIFACT_COLLECTION.name(), KUBERNETES_SETUP.name(),
            KUBERNETES_SETUP_ROLLBACK.name(), KUBERNETES_DEPLOY.name(), StateType.KUBERNETES_STEADY_STATE_CHECK.name())
        .contains("REPEAT", "FORK", "HTTP", DC_NODE_SELECT.name(), StateType.SHELL_SCRIPT.name());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCreateCanaryWorkflow() {
    Workflow workflow = workflowService.createWorkflow(constructCanaryWorkflow());
    assertThat(workflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);

    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();

    assertOrchestrationWorkflow(orchestrationWorkflow);

    PageResponse<StateMachine> res = findStateMachine(workflow);

    assertThat(res).isNotNull().hasSize(1);
    assertThat(res.get(0)).isNotNull().hasFieldOrPropertyWithValue("orchestrationWorkflow", orchestrationWorkflow);

    assertThat(workflow.getKeywords())
        .isNotNull()
        .contains(workflow.getName().toLowerCase())
        .contains(WorkflowType.ORCHESTRATION.name().toLowerCase());

    workflow = workflowService.readWorkflow(workflow.getAppId(), workflow.getUuid());
    assertThat(workflow.getKeywords())
        .isNotNull()
        .contains(workflow.getName().toLowerCase())
        .contains(WorkflowType.ORCHESTRATION.name().toLowerCase())
        .contains(OrchestrationWorkflowType.CANARY.name().toLowerCase());
  }

  private PageResponse findStateMachine(Workflow workflow) {
    return persistence.query(StateMachine.class,
        aPageRequest()
            .addFilter("appId", Operator.EQ, APP_ID)
            .addFilter("originId", Operator.EQ, workflow.getUuid())
            .build());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCreateBasicDeploymentWorkflow() {
    Workflow workflow = constructBasicWorkflowWithPhase();

    Workflow workflow2 = workflowService.createWorkflow(workflow);
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);

    BasicOrchestrationWorkflow orchestrationWorkflow =
        (BasicOrchestrationWorkflow) workflow2.getOrchestrationWorkflow();
    assertOrchestrationWorkflow(orchestrationWorkflow);

    PageResponse<StateMachine> res = findStateMachine(workflow);

    assertThat(res).isNotNull().hasSize(1);
    assertThat(res.get(0)).isNotNull().hasFieldOrPropertyWithValue("orchestrationWorkflow", orchestrationWorkflow);

    workflow2 = workflowService.readWorkflow(workflow2.getAppId(), workflow2.getUuid());
    assertThat(workflow2.getKeywords())
        .isNotNull()
        .isNotNull()
        .contains(workflow.getName().toLowerCase())
        .contains(WorkflowType.ORCHESTRATION.name().toLowerCase())
        .contains(OrchestrationWorkflowType.BASIC.name().toLowerCase())
        .contains(ENV_NAME.toLowerCase())
        .contains(SERVICE_NAME.toLowerCase());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCreateBasicDirectKubernetesDeploymentWorkflow() {
    Workflow workflow = constructBasicWorkflow();
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(constructDirectKubernetesInfraDef());
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(DeploymentType.KUBERNETES);

    Workflow savedWorkflow = workflowService.createWorkflow(workflow);
    assertThat(savedWorkflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);

    BasicOrchestrationWorkflow orchestrationWorkflow =
        (BasicOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    assertOrchestrationWorkflow(orchestrationWorkflow);

    savedWorkflow = workflowService.readWorkflow(savedWorkflow.getAppId(), savedWorkflow.getUuid());

    orchestrationWorkflow = (BasicOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    assertWorkflowPhase(orchestrationWorkflow);
    WorkflowPhase workflowPhase = orchestrationWorkflow.getWorkflowPhases().get(0);
    assertThat(workflowPhase).isNotNull();
    assertThat(workflowPhase).isNotNull().hasFieldOrPropertyWithValue("valid", true);

    assertThat(workflowPhase.getPhaseSteps())
        .isNotEmpty()
        .extracting(PhaseStep::getPhaseStepType)
        .contains(CONTAINER_SETUP, CONTAINER_DEPLOY, VERIFY_SERVICE, WRAP_UP);

    assertThat(workflowService.fetchRequiredEntityTypes(APP_ID, workflow).contains(EntityType.ARTIFACT)).isTrue();
    assertThat(workflowService.fetchDeploymentMetadata(APP_ID, savedWorkflow, null, null, null)
                   .getArtifactRequiredServiceIds()
                   .contains(SERVICE_ID))
        .isTrue();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCreateBasicGCPKubernetesDeploymentWorkflow() {
    Workflow workflow = constructBasicWorkflow();
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(constructGKInfraDef());
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(DeploymentType.KUBERNETES);

    Workflow savedWorkflow = workflowService.createWorkflow(workflow);
    assertThat(savedWorkflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);

    BasicOrchestrationWorkflow orchestrationWorkflow =
        (BasicOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    assertOrchestrationWorkflow(orchestrationWorkflow);

    savedWorkflow = workflowService.readWorkflow(savedWorkflow.getAppId(), savedWorkflow.getUuid());

    orchestrationWorkflow = (BasicOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    assertWorkflowPhase(orchestrationWorkflow);
    WorkflowPhase workflowPhase = orchestrationWorkflow.getWorkflowPhases().get(0);
    assertThat(workflowPhase).isNotNull();
    assertThat(workflowPhase).isNotNull().hasFieldOrPropertyWithValue("valid", true);

    assertThat(workflowPhase.getPhaseSteps())
        .isNotEmpty()
        .extracting(PhaseStep::getPhaseStepType)
        .contains(CLUSTER_SETUP, CONTAINER_SETUP, CONTAINER_DEPLOY, VERIFY_SERVICE, WRAP_UP);

    assertThat(workflowService.fetchRequiredEntityTypes(APP_ID, workflow).contains(EntityType.ARTIFACT)).isTrue();
    assertThat(workflowService.fetchDeploymentMetadata(APP_ID, savedWorkflow, null, null, null)
                   .getArtifactRequiredServiceIds())
        .contains(SERVICE_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCreateBasicPhysicalInfraDeploymentWorkflow() {
    Workflow workflow = constructBasicWorkflow();
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(constructPhysicalInfraMapping());

    Workflow savedWorkflow = workflowService.createWorkflow(workflow);
    assertThat(savedWorkflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);

    BasicOrchestrationWorkflow orchestrationWorkflow =
        (BasicOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    assertOrchestrationWorkflow(orchestrationWorkflow);

    savedWorkflow = workflowService.readWorkflow(savedWorkflow.getAppId(), savedWorkflow.getUuid());

    orchestrationWorkflow = (BasicOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    assertWorkflowPhase(orchestrationWorkflow);
    WorkflowPhase workflowPhase = orchestrationWorkflow.getWorkflowPhases().get(0);
    assertThat(workflowPhase.getPhaseSteps())
        .isNotEmpty()
        .extracting(PhaseStep::getPhaseStepType)
        .contains(INFRASTRUCTURE_NODE, DISABLE_SERVICE, DEPLOY_SERVICE, ENABLE_SERVICE, VERIFY_SERVICE, WRAP_UP);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCreateBlueGreenCPKubernetesDeploymentWorkflow() {
    Workflow workflow = constructBlueGreenWorkflow();
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(constructGKInfraDef());
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(DeploymentType.KUBERNETES);

    Workflow savedWorkflow = workflowService.createWorkflow(workflow);
    assertThat(savedWorkflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);

    BlueGreenOrchestrationWorkflow orchestrationWorkflow =
        (BlueGreenOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    assertOrchestrationWorkflow(orchestrationWorkflow);

    savedWorkflow = workflowService.readWorkflow(savedWorkflow.getAppId(), savedWorkflow.getUuid());

    orchestrationWorkflow = (BlueGreenOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow).isNotNull();
    assertThat(orchestrationWorkflow.getWorkflowPhases()).isNotEmpty().hasSize(1);
    WorkflowPhase workflowPhase = orchestrationWorkflow.getWorkflowPhases().get(0);
    assertThat(workflowPhase.getPhaseSteps())
        .isNotEmpty()
        .extracting(PhaseStep::getPhaseStepType)
        .contains(CONTAINER_SETUP, CONTAINER_DEPLOY, VERIFY_SERVICE, ROUTE_UPDATE, WRAP_UP);

    assertThat(workflowService.fetchRequiredEntityTypes(APP_ID, workflow)).contains(EntityType.ARTIFACT);
    assertThat(workflowService.fetchDeploymentMetadata(APP_ID, savedWorkflow, null, null, null)
                   .getArtifactRequiredServiceIds()
                   .contains(SERVICE_ID))
        .isTrue();

    Set<Entry<String, WorkflowPhase>> entries = orchestrationWorkflow.getRollbackWorkflowPhaseIdMap().entrySet();
    Entry<String, WorkflowPhase> entry = (Entry<String, WorkflowPhase>) entries.toArray()[0];
    PhaseStep phaseStep = entry.getValue().getPhaseSteps().get(0);
    boolean rollback = phaseStep.getSteps().get(0).isRollback();
    assertThat(rollback).isTrue();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCreateBasicEcsDeploymentWorkflow() {
    Workflow workflow = constructBasicWorkflow();
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(constructEcsInfraDef());
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(DeploymentType.ECS);

    Workflow savedWorkflow = workflowService.createWorkflow(workflow);
    assertThat(savedWorkflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);

    BasicOrchestrationWorkflow orchestrationWorkflow =
        (BasicOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    assertOrchestrationWorkflow(orchestrationWorkflow);

    savedWorkflow = workflowService.readWorkflow(savedWorkflow.getAppId(), savedWorkflow.getUuid());

    orchestrationWorkflow = (BasicOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    assertWorkflowPhase(orchestrationWorkflow);
    WorkflowPhase workflowPhase = orchestrationWorkflow.getWorkflowPhases().get(0);
    assertThat(workflowPhase.getPhaseSteps())
        .isNotEmpty()
        .extracting(PhaseStep::getPhaseStepType)
        .contains(CONTAINER_SETUP, CONTAINER_DEPLOY, VERIFY_SERVICE, WRAP_UP);

    assertThat(workflowService.fetchRequiredEntityTypes(APP_ID, workflow)).contains(EntityType.ARTIFACT);
    assertThat(workflowService.fetchDeploymentMetadata(APP_ID, savedWorkflow, null, null, null)
                   .getArtifactRequiredServiceIds())
        .contains(SERVICE_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCreateBasicAwsAmiDeploymentWorkflow() {
    Workflow workflow = constructBasicWorkflow();
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(constructAmiInfraDef());
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(AMI);

    Workflow savedWorkflow = workflowService.createWorkflow(workflow);
    assertThat(savedWorkflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);

    BasicOrchestrationWorkflow orchestrationWorkflow =
        (BasicOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    assertOrchestrationWorkflow(orchestrationWorkflow);

    savedWorkflow = workflowService.readWorkflow(savedWorkflow.getAppId(), savedWorkflow.getUuid());

    orchestrationWorkflow = (BasicOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    assertWorkflowPhase(orchestrationWorkflow);
    WorkflowPhase workflowPhase = orchestrationWorkflow.getWorkflowPhases().get(0);
    assertThat(workflowPhase.getPhaseSteps())
        .isNotEmpty()
        .extracting(PhaseStep::getPhaseStepType)
        .contains(AMI_DEPLOY_AUTOSCALING_GROUP, VERIFY_SERVICE, WRAP_UP);

    assertThat(workflowService.fetchRequiredEntityTypes(APP_ID, workflow)).contains(EntityType.ARTIFACT);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCreateBasicAwsLambdaDeploymentWorkflow() {
    Workflow workflow = constructBasicWorkflow();
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(constructAwsLambdaInfraDef());
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(DeploymentType.AWS_LAMBDA);

    Workflow savedWorkflow = workflowService.createWorkflow(workflow);
    assertThat(savedWorkflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);

    BasicOrchestrationWorkflow orchestrationWorkflow =
        (BasicOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    assertOrchestrationWorkflow(orchestrationWorkflow);

    savedWorkflow = workflowService.readWorkflow(savedWorkflow.getAppId(), savedWorkflow.getUuid());

    orchestrationWorkflow = (BasicOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    assertWorkflowPhase(orchestrationWorkflow);
    WorkflowPhase workflowPhase = orchestrationWorkflow.getWorkflowPhases().get(0);
    assertThat(workflowPhase.getPhaseSteps())
        .isNotEmpty()
        .extracting(PhaseStep::getPhaseStepType)
        .contains(PREPARE_STEPS, DEPLOY_AWS_LAMBDA, VERIFY_SERVICE, WRAP_UP);

    assertThat(workflowService.fetchRequiredEntityTypes(APP_ID, workflow)).contains(EntityType.ARTIFACT);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCreateCanaryHelmDeploymentWorkflow() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("helmReleaseNamePrefix", "defaultValue");
    Workflow workflow = constructHelmWorkflowWithProperties(properties);
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(constructHELMInfraDef());
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(DeploymentType.HELM);
    when(serviceTemplateService.get(APP_ID, SERVICE_ID, ENV_ID))
        .thenReturn(ServiceTemplate.Builder.aServiceTemplate().withUuid(SERVICE_TEMPLATE_ID).build());
    when(serviceResourceService.checkArtifactNeededForHelm(APP_ID, SERVICE_TEMPLATE_ID)).thenReturn(true);

    Workflow savedWorkflow = workflowService.createWorkflow(workflow);
    assertThat(savedWorkflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);

    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    assertOrchestrationWorkflow(orchestrationWorkflow);

    savedWorkflow = workflowService.readWorkflow(savedWorkflow.getAppId(), savedWorkflow.getUuid());

    orchestrationWorkflow = (CanaryOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    assertWorkflowPhase(orchestrationWorkflow);
    WorkflowPhase workflowPhase = orchestrationWorkflow.getWorkflowPhases().get(0);
    assertThat(workflowPhase.getPhaseSteps())
        .isNotEmpty()
        .extracting(PhaseStep::getPhaseStepType)
        .contains(PhaseStepType.HELM_DEPLOY);

    assertThat(workflowService.fetchRequiredEntityTypes(APP_ID, workflow)).contains(EntityType.ARTIFACT);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCreateBGHelmDeploymentWorkflow() {
    Workflow workflow = constructBlueGreenHelmWorkflow();
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(constructHELMInfra());
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(InfrastructureDefinition.builder()
                        .appId(APP_ID)
                        .uuid(INFRA_DEFINITION_ID)
                        .deploymentType(DeploymentType.HELM)
                        .infrastructure(DirectKubernetesInfrastructure.builder().build())
                        .cloudProviderType(CloudProviderType.KUBERNETES_CLUSTER)
                        .build());
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(DeploymentType.HELM);
    try {
      workflowService.createWorkflow(workflow);
      fail("Should not reach here.");
    } catch (InvalidRequestException ex) {
      assertThat(ex.getMessage()).isEqualTo("Workflow type BLUE_GREEN is not supported for deployment type Helm");
    }

    when(serviceResourceService.get(APP_ID, SERVICE_ID, false)).thenReturn(null);
    try {
      workflowService.createWorkflow(workflow);
      fail("Should not reach here.");
    } catch (InvalidRequestException ex) {
      assertThat(ex.getMessage()).isEqualTo("Workflow type BLUE_GREEN is not supported for deployment type Helm");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testAddHelmDeploymentWorkflowPhase() {
    Workflow workflow = constructCanaryWorkflow();
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(constructHELMInfra());
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(InfrastructureDefinition.builder()
                        .appId(APP_ID)
                        .uuid(INFRA_DEFINITION_ID)
                        .deploymentType(DeploymentType.HELM)
                        .infrastructure(DirectKubernetesInfrastructure.builder().build())
                        .cloudProviderType(CloudProviderType.KUBERNETES_CLUSTER)
                        .build());
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(DeploymentType.HELM);
    workflow = workflowService.createWorkflow(workflow);
    WorkflowPhase workflowPhase = aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();

    try {
      workflowService.createWorkflowPhase(workflow.getAppId(), workflow.getUuid(), workflowPhase);
      fail("Should not reach here.");
    } catch (InvalidRequestException ex) {
      assertThat(ex.getMessage()).isEqualTo("Workflow type CANARY is not supported for deployment type Helm");
    }
  }

  private void assertWorkflowPhase(CanaryOrchestrationWorkflow orchestrationWorkflow) {
    assertThat(orchestrationWorkflow).isNotNull();
    assertThat(orchestrationWorkflow.getWorkflowPhases()).isNotEmpty().hasSize(1);
  }

  private Workflow createBasicWorkflow() {
    Workflow workflow = constructBasicWorkflowWithPhase();
    Workflow workflow2 = workflowService.createWorkflow(workflow);
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid");
    assertThat(workflow2.getOrchestrationWorkflow())
        .isNotNull()
        .hasFieldOrProperty("preDeploymentSteps")
        .hasFieldOrProperty("postDeploymentSteps")
        .hasFieldOrProperty("graph");
    return workflow2;
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCreateMultiServiceWorkflow() {
    Workflow workflow = constructMultiServiceWorkflow();
    Workflow workflow2 = workflowService.createWorkflow(workflow);
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);
    MultiServiceOrchestrationWorkflow orchestrationWorkflow =
        (MultiServiceOrchestrationWorkflow) workflow2.getOrchestrationWorkflow();

    assertOrchestrationWorkflow(orchestrationWorkflow);

    PageResponse<StateMachine> res = findStateMachine(workflow);
    assertThat(res).isNotNull().hasSize(1);
    assertThat(res.get(0)).isNotNull().hasFieldOrPropertyWithValue("orchestrationWorkflow", orchestrationWorkflow);

    assertThat(workflow2.getKeywords())
        .isNotNull()
        .isNotNull()
        .contains(workflow.getName().toLowerCase())
        .contains(WorkflowType.ORCHESTRATION.name().toLowerCase());

    log.info(JsonUtils.asJson(workflow2));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldValidateWorkflow() {
    Workflow workflow2 = workflowService.createWorkflow(constructEcsWorkflow());
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);

    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow)
        .isNotNull()
        .hasFieldOrProperty("preDeploymentSteps")
        .hasFieldOrProperty("postDeploymentSteps")
        .hasFieldOrProperty("graph")
        .hasFieldOrPropertyWithValue("validationMessage", format(WORKFLOW_VALIDATION_MESSAGE, "[Phase 1]"));
    assertThat(orchestrationWorkflow.getWorkflowPhases().get(0))
        .isNotNull()
        .hasFieldOrPropertyWithValue("valid", false)
        .hasFieldOrPropertyWithValue(
            "validationMessage", format(PHASE_VALIDATION_MESSAGE, java.util.Arrays.asList(DEPLOY_CONTAINERS)));
    assertThat(orchestrationWorkflow.getWorkflowPhases().get(0).getPhaseSteps().get(0))
        .isNotNull()
        .hasFieldOrPropertyWithValue("valid", false)
        .hasFieldOrPropertyWithValue(
            "validationMessage", format(PHASE_STEP_VALIDATION_MESSAGE, java.util.Arrays.asList(UPGRADE_CONTAINERS)));
    assertThat(orchestrationWorkflow.getWorkflowPhases()
                   .get(0)
                   .getPhaseSteps()
                   .get(0)
                   .getSteps()
                   .stream()
                   .filter(n -> n.getName().equals(UPGRADE_CONTAINERS))
                   .findFirst()
                   .get())
        .isNotNull()
        .hasFieldOrPropertyWithValue("valid", false)
        .hasFieldOrPropertyWithValue(
            "validationMessage", format(STEP_VALIDATION_MESSAGE, java.util.Arrays.asList("instanceCount")));
    assertThat(orchestrationWorkflow.getWorkflowPhases()
                   .get(0)
                   .getPhaseSteps()
                   .get(0)
                   .getSteps()
                   .get(0)
                   .getInValidFieldMessages())
        .isNotNull()
        .hasSize(1)
        .containsKeys("instanceCount");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateCanary() {
    Workflow workflow1 = createCanaryWorkflow();
    String name2 = "Name2";

    Workflow workflow2 = aWorkflow().appId(APP_ID).uuid(workflow1.getUuid()).name(name2).build();

    workflowService.updateWorkflow(workflow2, null, false);

    Workflow orchestrationWorkflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(orchestrationWorkflow3).isNotNull().hasFieldOrPropertyWithValue("name", name2);
    assertThat(orchestrationWorkflow3.getKeywords())
        .isNotNull()
        .isNotNull()
        .contains(orchestrationWorkflow3.getName().toLowerCase())
        .contains(WorkflowType.ORCHESTRATION.name().toLowerCase())
        .contains(OrchestrationWorkflowType.CANARY.name().toLowerCase())
        .contains(ENV_NAME.toLowerCase());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateBasicDeploymentEnvironment() {
    Workflow workflow1 = createBasicWorkflow();
    String name2 = "Name2";

    Workflow workflow2 = aWorkflow().appId(APP_ID).envId(ENV_ID_CHANGED).uuid(workflow1.getUuid()).name(name2).build();

    workflowService.updateWorkflow(workflow2, null, false);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow3).isNotNull().hasFieldOrPropertyWithValue("envId", ENV_ID_CHANGED);
    OrchestrationWorkflow orchestrationWorkflow = workflow3.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow.isValid()).isFalse();
    assertThat(orchestrationWorkflow)
        .hasFieldOrPropertyWithValue(
            "validationMessage", "Some phases [Phase 1] Infrastructure Definition are found to be invalid/incomplete.");

    List<WorkflowPhase> workflowPhases =
        ((BasicOrchestrationWorkflow) workflow3.getOrchestrationWorkflow()).getWorkflowPhases();
    assertThat(workflowPhases).isNotNull().hasSize(1);

    WorkflowPhase workflowPhase = workflowPhases.get(0);
    assertThat(workflowPhase).isNotNull().hasFieldOrPropertyWithValue("name", PHASE_NAME_PREFIX + 1);
    assertThat(workflowPhase.getInfraDefinitionId()).isNull();
    assertThat(workflowPhase.getComputeProviderId()).isNull();
    assertThat(workflowPhase.getInfraMappingName()).isNull();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateBasicDeploymentEnvironmentServiceInfraMapping() {
    Workflow workflow1 = createBasicWorkflow();
    String name2 = "Name2";

    when(serviceResourceService.get(APP_ID, SERVICE_ID, false))
        .thenReturn(Service.builder().uuid(SERVICE_ID).artifactType(DOCKER).build());
    when(serviceResourceService.get(APP_ID, SERVICE_ID_CHANGED, false))
        .thenReturn(Service.builder().artifactType(DOCKER).uuid(SERVICE_ID_CHANGED).build());

    //    when(infrastructureMappingService.get(APP_ID, INFRA_DEFINITION_ID_CHANGED))
    //        .thenReturn(anAwsInfrastructureMapping()
    //                        .withName("NAME")
    //                        .withServiceId(SERVICE_ID_CHANGED)
    //                        .withUuid(INFRA_DEFINITION_ID_CHANGED)
    //                        .withDeploymentType(SSH.name())
    //                        .withComputeProviderSettingId(COMPUTE_PROVIDER_ID)
    //                        .withComputeProviderType(SettingVariableTypes.AWS.name())
    //                        .build());
    mockAwsInfraDef(INFRA_DEFINITION_ID_CHANGED);

    Workflow workflow2 = aWorkflow()
                             .appId(APP_ID)
                             .envId(ENV_ID_CHANGED)
                             .infraDefinitionId(INFRA_DEFINITION_ID_CHANGED)
                             .serviceId(SERVICE_ID_CHANGED)
                             .uuid(workflow1.getUuid())
                             .name(name2)
                             .build();

    workflowService.updateWorkflow(workflow2, null, false);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow3).isNotNull().hasFieldOrPropertyWithValue("envId", "ENV_ID_CHANGED");
    OrchestrationWorkflow orchestrationWorkflow = workflow3.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow.isValid()).isTrue();

    List<WorkflowPhase> workflowPhases =
        ((BasicOrchestrationWorkflow) workflow3.getOrchestrationWorkflow()).getWorkflowPhases();
    assertThat(workflowPhases).isNotNull().hasSize(1);

    WorkflowPhase workflowPhase = workflowPhases.get(0);
    assertThat(workflowPhase).isNotNull().hasFieldOrPropertyWithValue("name", PHASE_NAME_PREFIX + 1);
    assertThat(workflowPhase).hasFieldOrPropertyWithValue("infraDefinitionId", INFRA_DEFINITION_ID_CHANGED);
    assertThat(workflowPhase).hasFieldOrPropertyWithValue("serviceId", SERVICE_ID_CHANGED);
    assertThat(workflowPhase.getComputeProviderId()).isNotNull();
  }

  @Test(expected = WingsException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateBasicEnvironmentServiceInfraMappingIncompatible() {
    Workflow workflow1 = createBasicWorkflow();
    String name2 = "Name2";

    when(serviceResourceService.get(APP_ID, SERVICE_ID, false))
        .thenReturn(Service.builder().uuid(SERVICE_ID).artifactType(DOCKER).build());
    when(serviceResourceService.get(APP_ID, SERVICE_ID_CHANGED, false))
        .thenReturn(Service.builder().artifactType(DOCKER).uuid(SERVICE_ID_CHANGED).build());

    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(buildAwsSshInfraDef(INFRA_DEFINITION_ID_CHANGED));
    Workflow workflow2 = aWorkflow()
                             .appId(APP_ID)
                             .envId(ENV_ID_CHANGED)
                             .infraDefinitionId(INFRA_DEFINITION_ID_CHANGED)
                             .serviceId(SERVICE_ID_CHANGED)
                             .uuid(workflow1.getUuid())
                             .name(name2)
                             .build();

    workflowService.updateWorkflow(workflow2, null, false);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateBasicDeploymentInCompatibleService() {
    Workflow workflow1 = createBasicWorkflow();
    String name2 = "Name2";

    when(serviceResourceService.get(APP_ID, SERVICE_ID, false))
        .thenReturn(Service.builder().uuid(SERVICE_ID).name(SERVICE_NAME).artifactType(DOCKER).build());
    when(serviceResourceService.get(APP_ID, SERVICE_ID_CHANGED, false))
        .thenReturn(Service.builder().name(SERVICE_NAME).artifactType(WAR).uuid(SERVICE_ID_CHANGED).build());

    mockAwsInfraDef(INFRA_DEFINITION_ID_CHANGED);
    Workflow workflow2 = aWorkflow()
                             .appId(APP_ID)
                             .envId(ENV_ID_CHANGED)
                             .infraDefinitionId(INFRA_DEFINITION_ID_CHANGED)
                             .serviceId(SERVICE_ID_CHANGED)
                             .uuid(workflow1.getUuid())
                             .name(name2)
                             .build();

    try {
      workflowService.updateWorkflow(workflow2, null, false);
    } catch (WingsException e) {
      assertThat(e.getMessage()).isNotNull();
      assertThat(e.getParams().get("message"))
          .isEqualTo("Service [SERVICE_NAME] is not compatible with the service [SERVICE_NAME]");
    }
  }

  private void mockAwsInfraDef(String infraDefId) {
    when(infrastructureDefinitionService.get(APP_ID, infraDefId)).thenReturn(buildAwsSshInfraDef(infraDefId));
  }

  private InfrastructureDefinition buildAwsSshInfraDef(String infraDefId) {
    return InfrastructureDefinition.builder()
        .uuid(infraDefId)
        .name(INFRA_NAME)
        .deploymentType(SSH)
        .cloudProviderType(CloudProviderType.AWS)
        .infrastructure(AwsInstanceInfrastructure.builder().cloudProviderId(COMPUTE_PROVIDER_ID).build())
        .build();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateMultiServiceDeploymentEnvironmentServiceInfraMapping() {
    when(serviceResourceService.get(APP_ID, SERVICE_ID_CHANGED, false))
        .thenReturn(Service.builder().name(SERVICE_NAME).artifactType(WAR).uuid(SERVICE_ID_CHANGED).build());

    mockAwsInfraDef(INFRA_DEFINITION_ID_CHANGED);
    Workflow workflow1 = createMultiServiceWorkflow();

    WorkflowPhase workflowPhase = aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase);

    WorkflowPhase workflowPhase2 =
        aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase2);

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();

    List<WorkflowPhase> workflowPhases2 =
        ((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getWorkflowPhases();
    workflowPhase2 = workflowPhases2.get(workflowPhases2.size() - 1);
    workflowPhase2.setName("phase2-changed");
    workflowPhase2.setServiceId(SERVICE_ID_CHANGED);
    workflowPhase2.setInfraDefinitionId(INFRA_DEFINITION_ID_CHANGED);

    workflowService.updateWorkflowPhase(workflow2.getAppId(), workflow2.getUuid(), workflowPhase2);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    List<WorkflowPhase> workflowPhases3 =
        ((CanaryOrchestrationWorkflow) workflow3.getOrchestrationWorkflow()).getWorkflowPhases();
    WorkflowPhase workflowPhase3 = workflowPhases3.get(workflowPhases3.size() - 1);
    assertThat(workflowPhase3).isEqualToComparingOnlyGivenFields(workflowPhase2, "uuid", "name");

    assertThat(workflowPhase3).hasFieldOrPropertyWithValue("infraDefinitionId", INFRA_DEFINITION_ID_CHANGED);
    assertThat(workflowPhase3).hasFieldOrPropertyWithValue("serviceId", SERVICE_ID_CHANGED);
    assertThat(workflowPhase3.getComputeProviderId()).isNotNull();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateMultiServiceDeploymentInCompatibleService() {
    when(serviceResourceService.get(APP_ID, SERVICE_ID_CHANGED, false))
        .thenReturn(Service.builder().name(SERVICE_NAME).artifactType(WAR).uuid(SERVICE_ID_CHANGED).build());

    mockAwsInfraDef(INFRA_DEFINITION_ID_CHANGED);
    Workflow workflow1 = createMultiServiceWorkflow();

    WorkflowPhase workflowPhase = aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase);

    WorkflowPhase workflowPhase2 =
        aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase2);

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();

    List<WorkflowPhase> workflowPhases2 =
        ((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getWorkflowPhases();
    workflowPhase2 = workflowPhases2.get(workflowPhases2.size() - 1);
    workflowPhase2.setName("phase2-changed");
    workflowPhase2.setServiceId(SERVICE_ID_CHANGED);
    workflowPhase2.setInfraDefinitionId(INFRA_DEFINITION_ID_CHANGED);

    try {
      workflowService.updateWorkflowPhase(workflow2.getAppId(), workflow2.getUuid(), workflowPhase2);
    } catch (WingsException e) {
      assertThat(e.getMessage()).isNotNull();
      assertThat(e.getParams().get("message"))
          .isEqualTo("Workflow is not compatible with service [" + SERVICE_NAME + "]");
    }
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateCanaryDeploymentEnvironmentNoPhases() {
    Workflow workflow1 = createCanaryWorkflow();
    String name2 = "Name2";

    Workflow workflow2 = aWorkflow().appId(APP_ID).envId(ENV_ID_CHANGED).uuid(workflow1.getUuid()).name(name2).build();

    workflowService.updateWorkflow(workflow2, null, false);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow3).isNotNull().hasFieldOrPropertyWithValue("envId", ENV_ID_CHANGED);
    OrchestrationWorkflow orchestrationWorkflow = workflow3.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow.isValid()).isTrue();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateCanaryDeploymentEnvironment() {
    Workflow workflow1 = workflowService.createWorkflow(constructCanaryWorkflowWithPhase());

    String name2 = "Name2";

    Workflow workflow2 = aWorkflow().appId(APP_ID).envId(ENV_ID_CHANGED).uuid(workflow1.getUuid()).name(name2).build();

    workflowService.updateWorkflow(workflow2, null, false);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow3).isNotNull().hasFieldOrPropertyWithValue("envId", ENV_ID_CHANGED);
    OrchestrationWorkflow orchestrationWorkflow = workflow3.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow.isValid()).isFalse();

    List<WorkflowPhase> workflowPhases =
        ((CanaryOrchestrationWorkflow) workflow3.getOrchestrationWorkflow()).getWorkflowPhases();
    assertThat(workflowPhases).isNotNull().hasSize(1);

    WorkflowPhase workflowPhase = workflowPhases.get(0);
    assertThat(workflowPhase).isNotNull().hasFieldOrPropertyWithValue("name", PHASE_NAME_PREFIX + 1);
    assertThat(workflowPhase.getInfraDefinitionId()).isNull();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateCanaryDeploymentEnvironmentServiceInfraMapping() {
    when(serviceResourceService.get(APP_ID, SERVICE_ID_CHANGED, false))
        .thenReturn(Service.builder().name(SERVICE_NAME).artifactType(WAR).uuid(SERVICE_ID_CHANGED).build());

    mockAwsInfraDef(INFRA_DEFINITION_ID_CHANGED);
    Workflow workflow1 = createCanaryWorkflow();

    WorkflowPhase workflowPhase = aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase);

    WorkflowPhase workflowPhase2 =
        aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase2);

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();

    List<WorkflowPhase> workflowPhases2 =
        ((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getWorkflowPhases();
    workflowPhase2 = workflowPhases2.get(workflowPhases2.size() - 1);
    workflowPhase2.setName("phase2-changed");
    workflowPhase2.setServiceId(SERVICE_ID_CHANGED);
    workflowPhase2.setInfraDefinitionId(INFRA_DEFINITION_ID_CHANGED);

    workflowService.updateWorkflowPhase(workflow2.getAppId(), workflow2.getUuid(), workflowPhase2);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    List<WorkflowPhase> workflowPhases3 =
        ((CanaryOrchestrationWorkflow) workflow3.getOrchestrationWorkflow()).getWorkflowPhases();
    WorkflowPhase workflowPhase3 = workflowPhases3.get(workflowPhases3.size() - 1);
    assertThat(workflowPhase3).isEqualToComparingOnlyGivenFields(workflowPhase2, "uuid", "name");

    assertThat(workflowPhase3).hasFieldOrPropertyWithValue("infraDefinitionId", INFRA_DEFINITION_ID_CHANGED);
    assertThat(workflowPhase3).hasFieldOrPropertyWithValue("serviceId", SERVICE_ID_CHANGED);
    assertThat(workflowPhase3.getComputeProviderId()).isNotNull();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateCanaryInCompatibleService() {
    when(serviceResourceService.get(APP_ID, SERVICE_ID_CHANGED, false))
        .thenReturn(Service.builder().name(SERVICE_NAME).artifactType(WAR).uuid(SERVICE_ID_CHANGED).build());

    mockAwsInfraDef(INFRA_DEFINITION_ID_CHANGED);
    Workflow workflow1 = createCanaryWorkflow();

    WorkflowPhase workflowPhase = aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase);

    WorkflowPhase workflowPhase2 =
        aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase2);

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();

    List<WorkflowPhase> workflowPhases2 =
        ((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getWorkflowPhases();
    workflowPhase2 = workflowPhases2.get(workflowPhases2.size() - 1);
    workflowPhase2.setName("phase2-changed");
    workflowPhase2.setServiceId(SERVICE_ID_CHANGED);
    workflowPhase2.setInfraDefinitionId(INFRA_DEFINITION_ID_CHANGED);

    try {
      workflowService.updateWorkflowPhase(workflow2.getAppId(), workflow2.getUuid(), workflowPhase2);
    } catch (WingsException e) {
      assertThat(e.getMessage()).isNotNull();
      assertThat(e.getParams().get("message"))
          .isEqualTo("Workflow is not compatible with service [" + SERVICE_NAME + "]");
    }
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldUpdatePreDeployment() {
    Workflow workflow1 = createCanaryWorkflow();

    PhaseStep phaseStep = aPhaseStep(PRE_DEPLOYMENT).withStepsInParallel(true).build();
    workflowService.updatePreDeployment(workflow1.getAppId(), workflow1.getUuid(), phaseStep);

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull().hasFieldOrPropertyWithValue("uuid", workflow1.getUuid());
    assertThat(workflow2.getOrchestrationWorkflow()).isNotNull().isInstanceOf(CanaryOrchestrationWorkflow.class);
    assertThat(((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getPreDeploymentSteps())
        .isNotNull()
        .isEqualTo(phaseStep);
    assertThat(((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getGraph()).isNotNull();
    assertThat(((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getGraph().getNodes())
        .isNotNull()
        .extracting("id")
        .contains(phaseStep.getUuid());
    assertThat(((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getGraph().getSubworkflows())
        .isNotNull()
        .containsKeys(phaseStep.getUuid());
  }

  public Workflow createCanaryWorkflow() {
    Workflow workflow2 = workflowService.createWorkflow(constructCanaryWorkflow());
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid");
    assertOrchestrationWorkflow((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow());
    return workflow2;
  }

  public Workflow createCanaryWorkflowWithName(String workflowName) {
    Workflow workflow = constructCanaryWorkflow();
    workflow.setName(workflowName);

    workflow = workflowService.createWorkflow(workflow);
    assertThat(workflow).isNotNull().hasFieldOrProperty("uuid");
    assertOrchestrationWorkflow((CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow());
    return workflow;
  }

  public Workflow createMultiServiceWorkflow() {
    Workflow workflow2 = workflowService.createWorkflow(constructMultiServiceWorkflow());
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid");
    assertOrchestrationWorkflow((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow());
    return workflow2;
  }

  public Workflow createMultiServiceWorkflowWithPhase() {
    Workflow workflow2 = workflowService.createWorkflow(constructMultiServiceWorkflowWithPhase());
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid");
    assertOrchestrationWorkflow((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow());
    return workflow2;
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldUpdatePostDeployment() {
    Workflow workflow1 = createCanaryWorkflow();

    PhaseStep phaseStep = aPhaseStep(POST_DEPLOYMENT).withStepsInParallel(true).build();
    workflowService.updatePostDeployment(workflow1.getAppId(), workflow1.getUuid(), phaseStep);

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull().hasFieldOrPropertyWithValue("uuid", workflow1.getUuid());
    assertThat(workflow2.getOrchestrationWorkflow()).isNotNull().isInstanceOf(CanaryOrchestrationWorkflow.class);
    assertThat(((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getPostDeploymentSteps())
        .isNotNull()
        .isEqualTo(phaseStep);
    assertThat(((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getGraph()).isNotNull();
    assertThat(((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getGraph().getNodes())
        .isNotNull()
        .extracting("id")
        .contains(phaseStep.getUuid());
    assertThat(((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getGraph().getSubworkflows())
        .isNotNull()
        .containsKeys(phaseStep.getUuid());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldCreateWorkflowPhase() {
    Workflow workflow1 = createCanaryWorkflow();
    WorkflowPhase workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).infraDefinitionId(INFRA_DEFINITION_ID).build();

    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase);
    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();

    assertThat(workflow2).isNotNull().hasFieldOrPropertyWithValue("uuid", workflow1.getUuid());
    assertThat(workflow2.getOrchestrationWorkflow()).isNotNull().isInstanceOf(CanaryOrchestrationWorkflow.class);
    assertThat(((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getGraph()).isNotNull();
    assertThat(((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getGraph().getNodes())
        .isNotNull()
        .extracting("id")
        .contains(workflowPhase.getUuid());
    assertThat(((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getGraph().getSubworkflows())
        .isNotNull()
        .containsKeys(workflowPhase.getUuid());

    List<WorkflowPhase> workflowPhases =
        ((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getWorkflowPhases();
    assertThat(workflowPhases)
        .isNotNull()
        .hasSize(((CanaryOrchestrationWorkflow) workflow1.getOrchestrationWorkflow()).getWorkflowPhases().size() + 1);

    WorkflowPhase workflowPhase2 = workflowPhases.get(workflowPhases.size() - 1);
    assertThat(workflowPhase2).isNotNull().hasFieldOrPropertyWithValue("name", PHASE_NAME_PREFIX + 1);
    assertThat(workflowPhase2.getPhaseSteps()).isNotNull().hasSize(6);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldUpdateWorkflowPhase() {
    Workflow workflow1 = createCanaryWorkflow();

    WorkflowPhase workflowPhase = aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase);

    WorkflowPhase workflowPhase2 =
        aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase2);

    Workflow workflow1Refresh = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow1Refresh).isNotNull();

    List<WorkflowPhase> workflowPhases2 =
        ((CanaryOrchestrationWorkflow) workflow1Refresh.getOrchestrationWorkflow()).getWorkflowPhases();
    workflowPhase2 = workflowPhases2.get(workflowPhases2.size() - 1);
    workflowPhase2.setName("abcd");

    workflowService.updateWorkflowPhase(workflow1Refresh.getAppId(), workflow1Refresh.getUuid(), workflowPhase2);

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    List<WorkflowPhase> workflowPhases =
        ((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getWorkflowPhases();
    WorkflowPhase workflowPhases2Changed = workflowPhases.get(workflowPhases.size() - 1);
    assertThat(workflowPhases2Changed).isEqualToComparingOnlyGivenFields(workflowPhase2, "uuid", "name");

    WorkflowPhase workflowPhase3 =
        aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase3);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    workflowPhases = ((CanaryOrchestrationWorkflow) workflow3.getOrchestrationWorkflow()).getWorkflowPhases();
    WorkflowPhase workflowPhases3Refreshed = workflowPhases.get(workflowPhases.size() - 1);
    assertThat(workflowPhases3Refreshed).isNotNull().hasFieldOrPropertyWithValue("name", "Phase 3");
  }

  @Test(expected = WingsException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCreateWorkflowPhaseInvalidServiceandInframapping() {
    mockAwsInfraDef(INFRA_DEFINITION_ID);
    Workflow workflow1 = createCanaryWorkflow();

    WorkflowPhase workflowPhase =
        aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID_CHANGED).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateWorkflowPhaseInvalidServiceandInfra() {
    when(serviceResourceService.get(APP_ID, SERVICE_ID_CHANGED, false))
        .thenReturn(Service.builder().uuid(SERVICE_ID_CHANGED).build());
    mockAwsInfraDef(INFRA_DEFINITION_ID_CHANGED);
    Workflow workflow1 = createCanaryWorkflow();

    WorkflowPhase workflowPhase = aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase);

    WorkflowPhase workflowPhase2 =
        aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase2);

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();

    List<WorkflowPhase> workflowPhases2 =
        ((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getWorkflowPhases();
    workflowPhase2 = workflowPhases2.get(workflowPhases2.size() - 1);
    workflowPhase2.setName("phase2-changed");
    workflowPhase2.setServiceId(SERVICE_ID_CHANGED);
    workflowPhase2.setInfraDefinitionId(INFRA_DEFINITION_ID_CHANGED);

    workflowService.updateWorkflowPhase(workflow2.getAppId(), workflow2.getUuid(), workflowPhase2);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCloneWorkflowPhase() {
    Workflow workflow1 = createCanaryWorkflow();

    WorkflowPhase workflowPhase = aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase);

    List<NameValuePair> phaseOverrides = new ArrayList<>();
    phaseOverrides.add(NameValuePair.builder().name("Var1").value("Val1").build());
    WorkflowPhase workflowPhase2 = aWorkflowPhase()
                                       .infraDefinitionId(INFRA_DEFINITION_ID)
                                       .serviceId(SERVICE_ID)
                                       .variableOverrides(phaseOverrides)
                                       .build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase2);

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();

    List<WorkflowPhase> workflowPhases2 =
        ((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getWorkflowPhases();
    workflowPhase2 = workflowPhases2.get(workflowPhases2.size() - 1);
    workflowPhase2.setName("phase 2-clone");

    workflowService.cloneWorkflowPhase(workflow2.getAppId(), workflow2.getUuid(), workflowPhase2);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    List<WorkflowPhase> workflowPhases3 =
        ((CanaryOrchestrationWorkflow) workflow3.getOrchestrationWorkflow()).getWorkflowPhases();
    WorkflowPhase clonedWorkflowPhase = workflowPhases3.get(workflowPhases3.size() - 1);
    assertThat(clonedWorkflowPhase).isNotNull();
    assertThat(clonedWorkflowPhase.getUuid()).isNotEqualTo(workflowPhase2.getUuid());
    assertThat(clonedWorkflowPhase.getName()).isEqualTo("phase 2-clone");
    assertThat(clonedWorkflowPhase)
        .isEqualToComparingOnlyGivenFields(workflowPhase2, "infraDefinitionId", "serviceId", "computeProviderId");
    assertThat(clonedWorkflowPhase.getPhaseSteps()).isNotNull().size().isEqualTo(workflowPhase2.getPhaseSteps().size());
    assertThat(clonedWorkflowPhase.getVariableOverrides())
        .isNotNull()
        .extracting(NameValuePair::getName)
        .contains("Var1");
    assertThat(clonedWorkflowPhase.getVariableOverrides()).extracting(NameValuePair::getValue).contains("Val1");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCreateMultiServiceWorkflowPhase() {
    Workflow workflow1 = createMultiServiceWorkflow();
    WorkflowPhase workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).infraDefinitionId(INFRA_DEFINITION_ID).build();

    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase);
    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();

    assertThat(workflow2).isNotNull().hasFieldOrPropertyWithValue("uuid", workflow1.getUuid());
    assertThat(workflow2.getOrchestrationWorkflow()).isNotNull().isInstanceOf(CanaryOrchestrationWorkflow.class);
    assertThat(((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getGraph()).isNotNull();
    assertThat(((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getGraph().getNodes())
        .isNotNull()
        .extracting("id")
        .contains(workflowPhase.getUuid());
    assertThat(((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getGraph().getSubworkflows())
        .isNotNull()
        .containsKeys(workflowPhase.getUuid());

    List<WorkflowPhase> workflowPhases =
        ((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getWorkflowPhases();
    assertThat(workflowPhases)
        .isNotNull()
        .hasSize(((CanaryOrchestrationWorkflow) workflow1.getOrchestrationWorkflow()).getWorkflowPhases().size() + 1);

    WorkflowPhase workflowPhase2 = workflowPhases.get(workflowPhases.size() - 1);
    assertThat(workflowPhase2).isNotNull().hasFieldOrPropertyWithValue("name", PHASE_NAME_PREFIX + 1);
    assertThat(workflowPhase2.getPhaseSteps()).isNotNull().hasSize(6);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateMultiServiceWorkflowPhase() {
    Workflow workflow1 = createMultiServiceWorkflow();

    WorkflowPhase workflowPhase = aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase);

    WorkflowPhase workflowPhase2 =
        aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase2);

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();

    List<WorkflowPhase> workflowPhases2 =
        ((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getWorkflowPhases();
    workflowPhase2 = workflowPhases2.get(workflowPhases2.size() - 1);
    workflowPhase2.setName("phase2-changed");

    workflowService.updateWorkflowPhase(workflow2.getAppId(), workflow2.getUuid(), workflowPhase2);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    List<WorkflowPhase> workflowPhases3 =
        ((CanaryOrchestrationWorkflow) workflow3.getOrchestrationWorkflow()).getWorkflowPhases();
    WorkflowPhase workflowPhase3 = workflowPhases3.get(workflowPhases3.size() - 1);
    assertThat(workflowPhase3).isEqualToComparingOnlyGivenFields(workflowPhase2, "uuid", "name");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldDeleteWorkflowPhase() {
    Workflow workflow1 = workflowService.createWorkflow(constructCanaryWorkflowWithTwoPhases());
    assertThat(workflow1).isNotNull().hasFieldOrProperty("uuid");

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();

    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow();
    List<WorkflowPhase> workflowPhases2 = orchestrationWorkflow.getWorkflowPhases();
    WorkflowPhase workflowPhase = workflowPhases2.get(workflowPhases2.size() - 2);

    assertThat(orchestrationWorkflow.getGraph().getSubworkflows()).isNotNull().containsKeys(workflowPhase.getUuid());
    workflowPhase.getPhaseSteps().forEach(phaseStep -> {
      assertThat(orchestrationWorkflow.getGraph().getSubworkflows()).containsKeys(phaseStep.getUuid());
    });

    workflowService.deleteWorkflowPhase(APP_ID, workflow1.getUuid(), workflowPhase.getUuid());

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow3).isNotNull();

    CanaryOrchestrationWorkflow orchestrationWorkflow3 =
        (CanaryOrchestrationWorkflow) workflow3.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow3.getGraph().getSubworkflows())
        .isNotNull()
        .doesNotContainKeys(workflowPhase.getUuid());
    workflowPhase.getPhaseSteps().forEach(
        (PhaseStep phaseStep)
            -> assertThat(orchestrationWorkflow3.getGraph().getSubworkflows()).doesNotContainKeys(phaseStep.getUuid()));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldUpdateWorkflowPhaseRollback() {
    Workflow workflow1 = createCanaryWorkflow();

    WorkflowPhase workflowPhase = aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase);

    WorkflowPhase workflowPhase2 =
        aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase2);

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();

    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow).isNotNull();

    assertThat(orchestrationWorkflow.getRollbackWorkflowPhaseIdMap())
        .isNotNull()
        .containsKeys(workflowPhase2.getUuid());

    WorkflowPhase rollbackPhase = orchestrationWorkflow.getRollbackWorkflowPhaseIdMap().get(workflowPhase2.getUuid());
    assertThat(rollbackPhase).isNotNull();

    int size = rollbackPhase.getPhaseSteps().size();
    rollbackPhase.getPhaseSteps().remove(0);

    workflowService.updateWorkflowPhaseRollback(APP_ID, workflow2.getUuid(), workflowPhase2.getUuid(), rollbackPhase);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    CanaryOrchestrationWorkflow orchestrationWorkflow3 =
        (CanaryOrchestrationWorkflow) workflow3.getOrchestrationWorkflow();

    assertThat(orchestrationWorkflow3.getRollbackWorkflowPhaseIdMap())
        .isNotNull()
        .containsKeys(orchestrationWorkflow3.getWorkflowPhases().get(0).getUuid());
    WorkflowPhase rollbackPhase2 = orchestrationWorkflow3.getRollbackWorkflowPhaseIdMap().get(workflowPhase2.getUuid());
    assertThat(rollbackPhase2).isNotNull().hasFieldOrPropertyWithValue("uuid", rollbackPhase.getUuid());
    assertThat(rollbackPhase2.getPhaseSteps()).hasSize(size - 1);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldUpdateNode() {
    Workflow workflow = constructCanaryWithHttpStep();

    Workflow workflow2 = workflowService.createWorkflow(workflow);
    assertThat(workflow2).isNotNull();
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow).isNotNull();
    assertThat(orchestrationWorkflow.getGraph()).isNotNull();

    Graph graph =
        orchestrationWorkflow.getGraph().getSubworkflows().get(orchestrationWorkflow.getPreDeploymentSteps().getUuid());
    assertThat(graph).isNotNull();
    assertThat(graph.getNodes()).isNotNull().hasSize(1);
    GraphNode node = graph.getNodes().get(0);
    assertThat(node).isNotNull().hasFieldOrProperty("id").hasFieldOrPropertyWithValue("type", "HTTP");
    assertThat(node.getProperties()).isNotNull().containsKey("url").containsValue("http://www.google.com");
    node.getProperties().put("url", "http://www.yahoo.com");

    workflowService.updateGraphNode(
        workflow2.getAppId(), workflow2.getUuid(), orchestrationWorkflow.getPreDeploymentSteps().getUuid(), node);

    Workflow workflow3 = workflowService.readWorkflow(workflow2.getAppId(), workflow2.getUuid());
    assertThat(workflow3).isNotNull().hasFieldOrPropertyWithValue("uuid", workflow2.getUuid());
    CanaryOrchestrationWorkflow orchestrationWorkflow3 =
        (CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow3)
        .hasFieldOrProperty("graph")
        .hasFieldOrProperty("preDeploymentSteps")
        .hasFieldOrProperty("postDeploymentSteps");

    assertThat(orchestrationWorkflow3.getGraph().getSubworkflows())
        .isNotNull()
        .containsKeys(orchestrationWorkflow.getPreDeploymentSteps().getUuid())
        .containsKeys(orchestrationWorkflow.getPostDeploymentSteps().getUuid());

    graph = orchestrationWorkflow3.getGraph().getSubworkflows().get(
        orchestrationWorkflow.getPreDeploymentSteps().getUuid());
    assertThat(graph).isNotNull();
    assertThat(graph.getNodes()).isNotNull().hasSize(1);
    node = graph.getNodes().get(0);
    assertThat(node).isNotNull().hasFieldOrProperty("id").hasFieldOrPropertyWithValue("type", "HTTP");
    assertThat(node.getProperties()).isNotNull().containsKey("url").containsValue("http://www.yahoo.com");
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldHaveGraph() {
    Workflow workflow1 = createCanaryWorkflow();

    WorkflowPhase workflowPhase = aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase);

    WorkflowPhase workflowPhase2 =
        aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase2);

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();
    CanaryOrchestrationWorkflow orchestrationWorkflow2 =
        (CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow();

    assertThat(orchestrationWorkflow2.getWorkflowPhases()).isNotNull().hasSize(2);

    workflowPhase2 = orchestrationWorkflow2.getWorkflowPhases().get(1);

    workflowService.updateWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase2);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow3).isNotNull();
    CanaryOrchestrationWorkflow orchestrationWorkflow3 =
        (CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow();

    assertThat(orchestrationWorkflow3.getWorkflowPhases()).isNotNull().hasSize(2);

    Graph graph = orchestrationWorkflow3.getGraph();
    assertThat(graph).isNotNull();
    assertThat(graph.getNodes()).isNotNull().hasSize(6).doesNotContainNull();
    assertThat(graph.getLinks()).isNotNull().hasSize(3).doesNotContainNull();
    assertThat(graph.getNodes().get(0).getId()).isEqualTo(orchestrationWorkflow3.getPreDeploymentSteps().getUuid());
    assertThat(graph.getNodes().get(1).getId()).isEqualTo(orchestrationWorkflow3.getWorkflowPhaseIds().get(0));
    assertThat(graph.getNodes().get(3).getId()).isEqualTo(orchestrationWorkflow3.getWorkflowPhaseIds().get(1));
    assertThat(graph.getNodes().get(5).getId()).isEqualTo(orchestrationWorkflow3.getPostDeploymentSteps().getUuid());

    assertThat(graph.getSubworkflows())
        .isNotNull()
        .containsKeys(orchestrationWorkflow3.getPreDeploymentSteps().getUuid(),
            orchestrationWorkflow3.getWorkflowPhaseIds().get(0), orchestrationWorkflow3.getWorkflowPhaseIds().get(1),
            orchestrationWorkflow3.getPostDeploymentSteps().getUuid());

    for (WorkflowPhase phase : orchestrationWorkflow3.getWorkflowPhases()) {
      for (PhaseStep phaseStep : phase.getPhaseSteps()) {
        if (SELECT_NODE == phaseStep.getPhaseStepType() || INFRASTRUCTURE_NODE == phaseStep.getPhaseStepType()) {
          for (GraphNode node : phaseStep.getSteps()) {
            if (AWS_NODE_SELECT.name().equals(node.getType()) || DC_NODE_SELECT.name().equals(node.getType())) {
              Map<String, Object> properties = node.getProperties();
              assertThat(properties.get("specificHosts")).isEqualTo(false);
              assertThat(properties.get("instanceCount")).isEqualTo(1);
              assertThat(properties.get("excludeSelectedHostsFromFuturePhases")).isEqualTo(true);
            }
          }
        }
      }
    }
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateNotificationRules() {
    Workflow workflow1 = createCanaryWorkflow();
    List<NotificationRule> notificationRules = newArrayList(aNotificationRule().build());
    List<NotificationRule> updatedNotificationRules =
        workflowService.updateNotificationRules(workflow1.getAppId(), workflow1.getUuid(), notificationRules);

    assertThat(updatedNotificationRules).isNotEmpty();
    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow).isNotNull().hasFieldOrPropertyWithValue("notificationRules", notificationRules);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldUpdateNotificationRulesAsUserGroupExpression() {
    Workflow workflow1 = createCanaryWorkflow();
    List<NotificationRule> notificationRules =
        newArrayList(aNotificationRule()
                         .withUserGroupAsExpression(true)
                         .withUserGroupExpression("${serviceVariable.slack_user_group}")
                         .build());

    List<NotificationRule> updatedNotificationRules =
        workflowService.updateNotificationRules(workflow1.getAppId(), workflow1.getUuid(), notificationRules);

    assertThat(updatedNotificationRules).isNotEmpty();
    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow).isNotNull().hasFieldOrPropertyWithValue("notificationRules", notificationRules);
    persistence.save(workflow2);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldUpdateFailureStrategies() {
    Workflow workflow1 = createCanaryWorkflow();

    List<FailureStrategy> failureStrategies = newArrayList(
        FailureStrategy.builder().failureTypes(java.util.Arrays.asList(FailureType.VERIFICATION_FAILURE)).build());
    List<FailureStrategy> updated =
        workflowService.updateFailureStrategies(workflow1.getAppId(), workflow1.getUuid(), failureStrategies);

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow).isNotNull().hasFieldOrPropertyWithValue("failureStrategies", failureStrategies);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testValidationFailuresForUpdateFailureStrategies() {
    try {
      Workflow workflow1 = createCanaryWorkflowWithName(WORKFLOW_NAME + "-1");

      List<FailureStrategy> failureStrategies = newArrayList(FailureStrategy.builder().build());
      List<FailureStrategy> updated =
          workflowService.updateFailureStrategies(workflow1.getAppId(), workflow1.getUuid(), failureStrategies);
      fail("No Constraint violation detected");
    } catch (ConstraintViolationException e) {
      log.info("Expected constraintViolationException", e);
    }

    try {
      Workflow workflow1 = createCanaryWorkflowWithName(WORKFLOW_NAME + "-2");

      List<FailureStrategy> failureStrategies =
          newArrayList(FailureStrategy.builder()
                           .failureTypes(java.util.Arrays.asList(FailureType.VERIFICATION_FAILURE))
                           .failureCriteria(FailureCriteria.builder().failureThresholdPercentage(-1).build())
                           .build());

      workflowService.updateFailureStrategies(workflow1.getAppId(), workflow1.getUuid(), failureStrategies);
      fail("No Constraint violation detected");
    } catch (ConstraintViolationException e) {
      log.info("Expected constraintViolationException", e);
    }

    try {
      Workflow workflow1 = createCanaryWorkflowWithName(WORKFLOW_NAME + "-3");

      List<FailureStrategy> failureStrategies =
          newArrayList(FailureStrategy.builder()
                           .failureTypes(java.util.Arrays.asList(FailureType.VERIFICATION_FAILURE))
                           .failureCriteria(FailureCriteria.builder().failureThresholdPercentage(101).build())
                           .build());
      List<FailureStrategy> updated =
          workflowService.updateFailureStrategies(workflow1.getAppId(), workflow1.getUuid(), failureStrategies);
      fail("No Constraint violation detected");
    } catch (ConstraintViolationException e) {
      log.info("Expected constraintViolationException", e);
    }

    try {
      Workflow workflow1 = createCanaryWorkflow();

      List<FailureStrategy> failureStrategies =
          newArrayList(FailureStrategy.builder()
                           .failureTypes(java.util.Arrays.asList(FailureType.VERIFICATION_FAILURE))
                           .failureCriteria(FailureCriteria.builder().failureThresholdPercentage(100).build())
                           .build());
      List<FailureStrategy> updated =
          workflowService.updateFailureStrategies(workflow1.getAppId(), workflow1.getUuid(), failureStrategies);

      failureStrategies =
          newArrayList(FailureStrategy.builder()
                           .failureTypes(java.util.Arrays.asList(FailureType.VERIFICATION_FAILURE))
                           .failureCriteria(FailureCriteria.builder().failureThresholdPercentage(100).build())
                           .build());
      workflowService.updateFailureStrategies(workflow1.getAppId(), workflow1.getUuid(), failureStrategies);
    } catch (Exception e) {
      fail("Unexpected exception", e);
    }
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldUpdateUserVariables() {
    Workflow workflow1 = createCanaryWorkflow();
    List<Variable> userVariables = newArrayList(aVariable().name("name1").value("value1").build());

    workflowService.updateUserVariables(workflow1.getAppId(), workflow1.getUuid(), userVariables);

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow).isNotNull().hasFieldOrPropertyWithValue("userVariables", userVariables);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldFetchInfraDefVariables() {
    List<Variable> variables =
        newArrayList(aVariable().entityType(INFRASTRUCTURE_DEFINITION).name("name1").value("value1").build(),
            aVariable().entityType(SERVICE).name("name2").value("value2").build());

    List<Variable> infraDefVariables = workflowServiceTemplateHelper.getInfraDefCompleteWorkflowVariables(variables);

    assertThat(infraDefVariables).hasSize(1);
    assertThat(infraDefVariables.get(0).getName()).isEqualTo("name1");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateUserVariablesValidationFixedEmptyValue() {
    Workflow workflow1 = createCanaryWorkflow();
    List<Variable> userVariables = newArrayList(aVariable().name("name1").value("").fixed(true).build());

    workflowService.updateUserVariables(workflow1.getAppId(), workflow1.getUuid(), userVariables);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateUserVariablesValidationDuplicateNames() {
    Workflow workflow1 = createCanaryWorkflow();
    List<Variable> userVariables = newArrayList(aVariable().name("name1").value("value").fixed(true).build(),
        aVariable().name("name1").value("value").fixed(true).build());

    workflowService.updateUserVariables(workflow1.getAppId(), workflow1.getUuid(), userVariables);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldNotUpdateUserVariablesWithDashInName() {
    Workflow workflow1 = createCanaryWorkflow();
    List<Variable> userVariables = newArrayList(aVariable().name("name-1").value("value").fixed(true).build());

    workflowService.updateUserVariables(workflow1.getAppId(), workflow1.getUuid(), userVariables);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldCreateComplexWorkflow() {
    Workflow workflow1 = constructCanaryWorkflowWithPhase();

    Workflow workflow2 = workflowService.createWorkflow(workflow1);
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid");

    Workflow workflow3 = workflowService.readWorkflow(workflow2.getAppId(), workflow2.getUuid());
    assertThat(workflow3).isNotNull();
    CanaryOrchestrationWorkflow orchestrationWorkflow3 =
        (CanaryOrchestrationWorkflow) workflow3.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow3.getWorkflowPhases()).isNotNull().hasSize(1);

    WorkflowPhase workflowPhase = orchestrationWorkflow3.getWorkflowPhases().get(0);
    PhaseStep deployPhaseStep = workflowPhase.getPhaseSteps()
                                    .stream()
                                    .filter(ps -> ps.getPhaseStepType() == DEPLOY_SERVICE)
                                    .collect(toList())
                                    .get(0);

    deployPhaseStep.getSteps().add(
        GraphNode.builder()
            .type("HTTP")
            .name("http")
            .properties(ImmutableMap.<String, Object>builder().put("url", "www.google.com").build())
            .build());

    workflowService.updateWorkflowPhase(workflow2.getAppId(), workflow2.getUuid(), workflowPhase);

    Workflow workflow4 = workflowService.readWorkflow(workflow2.getAppId(), workflow2.getUuid());
    workflowService.deleteWorkflowPhase(workflow4.getAppId(), workflow4.getUuid(), workflowPhase.getUuid());

    Workflow workflow5 = workflowService.readWorkflow(workflow2.getAppId(), workflow2.getUuid());
    assertThat(workflow5).isNotNull();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTemplatizeBasicDeploymentOnCreation() {
    Workflow workflow2 = workflowService.createWorkflow(constructBasicDeploymentTemplateWorkflow());

    assertThat(workflow2.getKeywords())
        .isNotNull()
        .contains(workflow2.getName().toLowerCase())
        .contains(OrchestrationWorkflowType.BASIC.name().toLowerCase())
        .contains(ENV_NAME.toLowerCase())
        .contains(SERVICE_NAME.toLowerCase())
        .contains("template");

    assertTemplatizedWorkflow(workflow2);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTemplatizeBasicDeployment() {
    Workflow workflow1 = createBasicWorkflow();
    String name2 = "Name2";

    Workflow workflow2 = aWorkflow().appId(APP_ID).envId(ENV_ID).uuid(workflow1.getUuid()).name(name2).build();

    workflow2.setTemplateExpressions(
        asList(getEnvTemplateExpression(), prepareInfraDefTemplateExpression(), getServiceTemplateExpression()));

    workflowService.updateWorkflow(workflow2, null, false);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());

    assertTemplatizedWorkflow(workflow3);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotValidateServiceCompatibilityForTemplatized() {
    Workflow workflow1 = createBasicWorkflow();
    String name2 = "Name2";

    final String serviceId = generateUuid();
    Workflow workflow2 =
        aWorkflow().appId(APP_ID).envId(ENV_ID).serviceId(serviceId).uuid(workflow1.getUuid()).name(name2).build();

    workflow2.setTemplateExpressions(
        asList(getEnvTemplateExpression(), prepareInfraDefTemplateExpression(), getServiceTemplateExpression()));

    try {
      when(serviceResourceService.get(serviceId)).thenReturn(service);
      workflowService.updateWorkflow(workflow2, null, false);

    } catch (Exception ex) {
      fail("Should not call Service Compatiblity for the templated workflow");
    }

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());

    assertTemplatizedWorkflow(workflow3);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTemplatizeMultiServiceEnvThenTemplatizeInfra() {
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(DeploymentType.SSH);
    Workflow workflow1 = constructMulitServiceTemplateWorkflow();

    Workflow workflow2 = workflowService.createWorkflow(workflow1);
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid");

    workflow2.setTemplateExpressions(java.util.Arrays.asList(getEnvTemplateExpression()));

    workflowService.updateWorkflow(workflow2, null, false);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());

    assertThat(workflow3.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("envId");
    OrchestrationWorkflow orchestrationWorkflow = workflow3.getOrchestrationWorkflow();
    List<WorkflowPhase> workflowPhases =
        ((MultiServiceOrchestrationWorkflow) orchestrationWorkflow).getWorkflowPhases();

    assertTemplatizedOrchestrationWorkflow(orchestrationWorkflow);

    assertThat(workflowPhases).isNotNull().hasSize(2);

    WorkflowPhase workflowPhase = workflowPhases.get(0);
    assertThat(workflowPhase.getInfraDefinitionId()).isNotNull();
    assertThat(workflowPhase.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("infraDefinitionId");
    assertThat(orchestrationWorkflow.getUserVariables())
        .extracting(Variable::obtainEntityType)
        .containsSequence(ENVIRONMENT, INFRASTRUCTURE_DEFINITION);

    workflowPhase = workflowPhases.get(1);
    assertThat(workflowPhase.getInfraDefinitionId()).isNotNull();
    assertThat(workflowPhase.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("infraDefinitionId");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTemplatizeCanaryEnvThenTemplatizeInfra() {
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(DeploymentType.SSH);

    Workflow workflow1 = constructTemplatizedCanaryWorkflow();

    Workflow workflow2 = workflowService.createWorkflow(workflow1);
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid");

    workflow2.setTemplateExpressions(java.util.Arrays.asList(getEnvTemplateExpression()));

    workflowService.updateWorkflow(workflow2, null, false);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());

    assertThat(workflow3.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("envId");
    OrchestrationWorkflow orchestrationWorkflow = workflow3.getOrchestrationWorkflow();

    assertTemplatizedOrchestrationWorkflow(orchestrationWorkflow);

    List<WorkflowPhase> workflowPhases = ((CanaryOrchestrationWorkflow) orchestrationWorkflow).getWorkflowPhases();
    assertThat(workflowPhases).isNotNull().hasSize(2);

    WorkflowPhase workflowPhase = workflowPhases.get(0);
    assertThat(workflowPhase).isNotNull().hasFieldOrPropertyWithValue("name", PHASE_NAME_PREFIX + 1);
    assertThat(workflowPhase.getInfraDefinitionId()).isNotNull();
    assertThat(workflowPhase.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("infraDefinitionId");
    assertThat(orchestrationWorkflow.getUserVariables())
        .extracting(Variable::obtainEntityType)
        .containsSequence(ENVIRONMENT, INFRASTRUCTURE_DEFINITION);

    // Assert Service Infra variable has metadata with the associated service infrastructure
    final Variable infraVariable1 = orchestrationWorkflow.getUserVariables()
                                        .stream()
                                        .filter(variable -> variable.obtainEntityType() == INFRASTRUCTURE_DEFINITION)
                                        .findFirst()
                                        .orElse(null);

    assertThat(infraVariable1.getMetadata().get("serviceId")).isEqualTo(SERVICE_ID);

    workflowPhase = workflowPhases.get(1);
    assertThat(workflowPhase.getInfraDefinitionId()).isNotNull();
    assertThat(workflowPhase.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("infraDefinitionId");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTemplatizeCanaryPhase() {
    Workflow workflow1 = createCanaryWorkflow();

    WorkflowPhase workflowPhase = aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase);

    WorkflowPhase workflowPhase2 =
        aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase2);

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();

    List<WorkflowPhase> workflowPhases2 =
        ((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getWorkflowPhases();
    workflowPhase2 = workflowPhases2.get(workflowPhases2.size() - 1);
    workflowPhase2.setName("phase2-changed");

    workflowPhase2.setTemplateExpressions(asList(getServiceTemplateExpression(), prepareInfraDefTemplateExpression()));

    workflowService.updateWorkflowPhase(workflow2.getAppId(), workflow2.getUuid(), workflowPhase2);
    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertWorkflowPhaseTemplateExpressions(workflow3, workflowPhase2);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateTemplatizeExpressionsBasicDeployment() {
    Workflow workflow1 = createBasicWorkflow();
    String name2 = "Name2";

    Workflow workflow2 = aWorkflow().appId(APP_ID).envId(ENV_ID).uuid(workflow1.getUuid()).name(name2).build();

    workflow2.setTemplateExpressions(
        asList(getEnvTemplateExpression(), prepareInfraDefTemplateExpression(), getServiceTemplateExpression()));

    workflowService.updateWorkflow(workflow2, null, false);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());

    assertTemplatizedWorkflow(workflow3);
    OrchestrationWorkflow orchestrationWorkflow;
    List<WorkflowPhase> workflowPhases;
    WorkflowPhase workflowPhase;

    Map<String, Object> serviceMetadata = new HashMap<>();
    serviceMetadata.put("entityType", "SERVICE");
    // Now update template expressions with different names
    workflow2.setTemplateExpressions(asList(TemplateExpression.builder()
                                                .fieldName("envId")
                                                .expression("${Environment_Changed}")
                                                .metadata(ImmutableMap.of("entityType", "ENVIRONMENT"))
                                                .build(),
        TemplateExpression.builder()
            .fieldName("infraDefinitionId")
            .expression("${InfraDef_SSH_Changed}")
            .metadata(ImmutableMap.of("entityType", "INFRASTRUCTURE_MAPPING"))
            .build(),
        TemplateExpression.builder()
            .fieldName("serviceId")
            .expression("${Service_Changed}")
            .metadata(serviceMetadata)
            .build()));

    workflowService.updateWorkflow(workflow2, null, false);

    Workflow templatizedWorkflow = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());

    assertThat(workflow3.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("envId");
    orchestrationWorkflow = templatizedWorkflow.getOrchestrationWorkflow();
    workflowPhases = ((BasicOrchestrationWorkflow) orchestrationWorkflow).getWorkflowPhases();
    assertThat(orchestrationWorkflow.getTemplatizedServiceIds()).isNotNull().contains(SERVICE_ID);
    assertThat(orchestrationWorkflow.getTemplatizedInfraDefinitionIds()).isNotNull().contains(INFRA_DEFINITION_ID);
    assertThat(orchestrationWorkflow).extracting("userVariables").isNotNull();
    assertThat(
        orchestrationWorkflow.getUserVariables().stream().anyMatch(variable -> variable.getName().equals("Service")))
        .isFalse();
    assertThat(orchestrationWorkflow.getUserVariables().stream().anyMatch(
                   variable -> variable.getName().equals("Service_Changed")))
        .isTrue();
    assertThat(orchestrationWorkflow.getUserVariables().stream().anyMatch(
                   variable -> variable.getName().equals("InfraDef_SSH")))
        .isFalse();
    assertThat(orchestrationWorkflow.getUserVariables().stream().anyMatch(
                   variable -> variable.getName().equals("InfraDef_SSH_Changed")))
        .isTrue();
    assertThat(orchestrationWorkflow.getUserVariables().stream().anyMatch(
                   variable -> variable.getName().equals("Environment")))
        .isFalse();
    assertThat(orchestrationWorkflow.getUserVariables().stream().anyMatch(
                   variable -> variable.getName().equals("Environment_Changed")))
        .isTrue();

    assertThat(workflowPhases).isNotNull().hasSize(1);

    workflowPhase = workflowPhases.get(0);
    assertThat(workflowPhase).isNotNull().hasFieldOrPropertyWithValue("name", PHASE_NAME_PREFIX + 1);
    assertThat(workflowPhase.getInfraDefinitionId()).isNotNull();
    assertThat(workflowPhase.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("infraDefinitionId");
    assertThat(workflowPhase.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("serviceId");
    assertThat(orchestrationWorkflow.getUserVariables())
        .extracting(Variable::obtainEntityType)
        .containsSequence(ENVIRONMENT, SERVICE, INFRASTRUCTURE_MAPPING);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateTemplatizeExpressionsCanary() {
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(DeploymentType.SSH);
    Workflow workflow1 = constructCanaryWorkflowWithPhase();

    Workflow workflow2 = workflowService.createWorkflow(workflow1);
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid");

    workflow2.setTemplateExpressions(java.util.Arrays.asList(getEnvTemplateExpression()));

    workflowService.updateWorkflow(workflow2, null, false);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());

    assertThat(workflow3.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("envId");
    OrchestrationWorkflow orchestrationWorkflow = workflow3.getOrchestrationWorkflow();
    List<WorkflowPhase> workflowPhases = ((CanaryOrchestrationWorkflow) orchestrationWorkflow).getWorkflowPhases();
    assertThat(orchestrationWorkflow.getTemplatizedServiceIds()).isNotNull().doesNotContain(SERVICE_ID);
    assertThat(orchestrationWorkflow.getTemplatizedInfraDefinitionIds()).isNotNull().contains(INFRA_DEFINITION_ID);
    assertThat(orchestrationWorkflow).extracting("userVariables").isNotNull();
    assertThat(orchestrationWorkflow.getUserVariables().stream().anyMatch(
                   variable -> variable.getName().equals("InfraDefinition_SSH")))
        .isTrue();

    assertThat(workflowPhases).isNotNull().hasSize(1);

    WorkflowPhase workflowPhase = workflowPhases.get(0);
    assertThat(workflowPhase).isNotNull().hasFieldOrPropertyWithValue("name", PHASE_NAME_PREFIX + 1);
    assertThat(workflowPhase.getInfraDefinitionId()).isNotNull();
    assertThat(workflowPhase.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("infraDefinitionId");
    assertThat(orchestrationWorkflow.getUserVariables())
        .extracting(Variable::obtainEntityType)
        .containsSequence(ENVIRONMENT, INFRASTRUCTURE_DEFINITION);

    TemplateExpression envExpression = TemplateExpression.builder()
                                           .fieldName("envId")
                                           .expression("${Environment_Changed}")
                                           .metadata(ImmutableMap.of("entityType", "ENVIRONMENT"))
                                           .build();
    workflow3.setTemplateExpressions(java.util.Arrays.asList(envExpression));

    workflow3 = workflowService.updateWorkflow(workflow3, null, false);

    assertThat(workflow3.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("envId");
    orchestrationWorkflow = workflow3.getOrchestrationWorkflow();
    workflowPhases = ((CanaryOrchestrationWorkflow) orchestrationWorkflow).getWorkflowPhases();
    assertThat(orchestrationWorkflow.getTemplatizedServiceIds()).isNotNull().doesNotContain(SERVICE_ID);
    assertThat(orchestrationWorkflow.getTemplatizedInfraDefinitionIds()).isNotNull().contains(INFRA_DEFINITION_ID);
    assertThat(orchestrationWorkflow).extracting("userVariables").isNotNull();
    assertThat(orchestrationWorkflow.getUserVariables().stream().anyMatch(
                   variable -> variable.getName().equals("InfraDefinition_SSH")))
        .isTrue();
    assertThat(orchestrationWorkflow.getUserVariables().stream().anyMatch(
                   variable -> variable.getName().equals("Environment")))
        .isFalse();
    assertThat(orchestrationWorkflow.getUserVariables().stream().anyMatch(
                   variable -> variable.getName().equals("Environment_Changed")))
        .isTrue();

    assertThat(workflowPhases).isNotNull().hasSize(1);

    workflowPhase = workflowPhases.get(0);
    assertThat(workflowPhase).isNotNull().hasFieldOrPropertyWithValue("name", PHASE_NAME_PREFIX + 1);
    assertThat(workflowPhase.getInfraDefinitionId()).isNotNull();
    assertThat(workflowPhase.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("infraDefinitionId");
    assertThat(orchestrationWorkflow.getUserVariables())
        .extracting(Variable::obtainEntityType)
        .containsSequence(ENVIRONMENT, INFRASTRUCTURE_DEFINITION);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTemplatizeBasicPhase() {
    Workflow workflow = createBasicWorkflow();
    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow).isNotNull();
    List<WorkflowPhase> workflowPhases = ((BasicOrchestrationWorkflow) orchestrationWorkflow).getWorkflowPhases();
    WorkflowPhase workflowPhase = workflowPhases.get(workflowPhases.size() - 1);

    workflowPhase.setName("phase2-changed");

    workflowPhase.setTemplateExpressions(asList(prepareInfraDefTemplateExpression(), getServiceTemplateExpression()));

    workflowService.updateWorkflowPhase(workflow.getAppId(), workflow.getUuid(), workflowPhase);

    Workflow workflow3 = workflowService.readWorkflow(workflow.getAppId(), workflow.getUuid());

    List<WorkflowPhase> workflowPhases2 =
        ((BasicOrchestrationWorkflow) workflow3.getOrchestrationWorkflow()).getWorkflowPhases();
    WorkflowPhase workflowPhase3 = workflowPhases2.get(workflowPhases2.size() - 1);
    assertThat(workflowPhase3).isEqualToComparingOnlyGivenFields(workflowPhase, "uuid", "name");

    assertThat(workflowPhase3.getInfraDefinitionId()).isNotNull();
    assertThat(workflowPhase3.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("infraDefinitionId");
    assertThat(workflow3.getOrchestrationWorkflow().getUserVariables())
        .extracting(Variable::obtainEntityType)
        .containsSequence(SERVICE, INFRASTRUCTURE_DEFINITION);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldDeTemplatizeBasicDeployment() {
    Workflow workflow1 = createBasicWorkflow();
    String name2 = "Name2";

    Workflow workflow2 = aWorkflow().appId(APP_ID).envId(ENV_ID).uuid(workflow1.getUuid()).name(name2).build();

    workflow2.setTemplateExpressions(
        asList(getEnvTemplateExpression(), prepareInfraDefTemplateExpression(), getServiceTemplateExpression()));

    workflowService.updateWorkflow(workflow2, null, false);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());

    assertTemplatizedWorkflow(workflow3);

    // Detemplatize service Infra
    workflow2.setTemplateExpressions(asList(getEnvTemplateExpression(), prepareInfraDefTemplateExpression()));

    workflowService.updateWorkflow(workflow2, null, false);

    Workflow templatizedWorkflow = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());

    assertThat(workflow3.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("envId");

    OrchestrationWorkflow orchestrationWorkflow = templatizedWorkflow.getOrchestrationWorkflow();
    List<WorkflowPhase> workflowPhases = ((BasicOrchestrationWorkflow) orchestrationWorkflow).getWorkflowPhases();
    assertTemplateWorkflowPhase(orchestrationWorkflow, workflowPhases);

    WorkflowPhase workflowPhase = workflowPhases.get(0);
    assertThat(workflowPhase).isNotNull().hasFieldOrPropertyWithValue("name", PHASE_NAME_PREFIX + 1);
    assertThat(workflowPhase.getInfraDefinitionId()).isNotNull();
    assertThat(workflowPhase.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("infraDefinitionId");
    assertThat(workflowPhase.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .doesNotContain("serviceId");
    assertThat(orchestrationWorkflow.getUserVariables())
        .extracting(Variable::obtainEntityType)
        .containsSequence(ENVIRONMENT, INFRASTRUCTURE_DEFINITION);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldDeTemplatizeOnlyServiceandInfraCanaryPhase() {
    Workflow workflow1 = createCanaryWorkflow();

    WorkflowPhase workflowPhase = aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase);

    WorkflowPhase workflowPhase2 =
        aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase2);

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();

    List<WorkflowPhase> workflowPhases2 =
        ((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getWorkflowPhases();
    workflowPhase2 = workflowPhases2.get(workflowPhases2.size() - 1);
    workflowPhase2.setName("phase2-changed");

    workflowPhase2.setTemplateExpressions(asList(getServiceTemplateExpression(), prepareInfraDefTemplateExpression()));

    workflowService.updateWorkflowPhase(workflow2.getAppId(), workflow2.getUuid(), workflowPhase2);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    List<WorkflowPhase> workflowPhases3 =
        ((CanaryOrchestrationWorkflow) workflow3.getOrchestrationWorkflow()).getWorkflowPhases();
    WorkflowPhase workflowPhase3 = workflowPhases3.get(workflowPhases3.size() - 1);
    assertThat(workflowPhase3).isEqualToComparingOnlyGivenFields(workflowPhase2, "uuid", "name");

    assertThat(workflowPhase3.getInfraDefinitionId()).isNotNull();
    assertThat(workflowPhase3.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("infraDefinitionId");
    assertThat(workflow3.getOrchestrationWorkflow().getUserVariables())
        .extracting(Variable::obtainEntityType)
        .containsSequence(SERVICE, INFRASTRUCTURE_DEFINITION);

    workflowPhase3.setTemplateExpressions(null);
    workflowService.updateWorkflowPhase(workflow2.getAppId(), workflow2.getUuid(), workflowPhase3);

    Workflow workflow4 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    List<WorkflowPhase> workflowPhases4 =
        ((CanaryOrchestrationWorkflow) workflow4.getOrchestrationWorkflow()).getWorkflowPhases();
    WorkflowPhase workflowPhase4 = workflowPhases3.get(workflowPhases4.size() - 1);
    assertThat(workflowPhase4).isEqualToComparingOnlyGivenFields(workflowPhase2, "uuid", "name");

    assertThat(workflowPhase4.getInfraDefinitionId()).isNotNull();
    assertThat(workflowPhase4.getTemplateExpressions()).isEmpty();
    assertThat(workflow4.getOrchestrationWorkflow().getUserVariables()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCreateBuildDeploymentWorkflow() {
    Workflow workflow2 = workflowService.createWorkflow(constructBuildWorkflow());
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);

    BuildWorkflow orchestrationWorkflow = (BuildWorkflow) workflow2.getOrchestrationWorkflow();
    assertOrchestrationWorkflow(orchestrationWorkflow);
    assertThat(orchestrationWorkflow.getFailureStrategies()).isEmpty();

    PageResponse<StateMachine> res = findStateMachine(workflow2);
    assertThat(res).isNotNull().hasSize(1);
    assertThat(res.get(0)).isNotNull().hasFieldOrPropertyWithValue("orchestrationWorkflow", orchestrationWorkflow);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAwsCodeDeployStateDefaults() {
    when(artifactStreamService.fetchArtifactStreamsForService(APP_ID, SERVICE_ID))
        .thenReturn(java.util.Arrays.asList(artifactStream));
    when(artifactStream.getArtifactStreamType()).thenReturn(ArtifactStreamType.AMAZON_S3.name());
    Map<String, String> defaults = workflowService.getStateDefaults(APP_ID, SERVICE_ID, AWS_CODEDEPLOY_STATE);
    assertThat(defaults).isNotEmpty();
    assertThat(defaults).containsKeys("bucket", "key", "bundleType");
    assertThat(defaults).containsValues(ARTIFACT_S3_BUCKET_EXPRESSION, ARTIFACT_S3_KEY_EXPRESSION, "zip");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAwsCodeDeployNoStateDefaults() {
    assertThat(workflowService.getStateDefaults(APP_ID, SERVICE_ID, AWS_CODEDEPLOY_STATE)).isEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTestWorkflowHasSshInfraMapping() {
    when(serviceResourceService.get(APP_ID, SERVICE_ID, false)).thenReturn(Service.builder().uuid(SERVICE_ID).build());
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID))
        .thenReturn(Service.builder().uuid(SERVICE_ID).build());
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(DeploymentType.SSH);
    Workflow workflow1 = createCanaryWorkflow();

    WorkflowPhase workflowPhase = aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase);

    WorkflowPhase workflowPhase2 =
        aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase2);

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();

    assertThat(workflowService.workflowHasSshDeploymentPhase(workflow2.getAppId(), workflow2.getUuid())).isTrue();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTemplatizeAppDElkState() {
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID))
        .thenReturn(Service.builder().uuid(SERVICE_ID).build());
    when(serviceResourceService.get(APP_ID, SERVICE_ID, false)).thenReturn(Service.builder().uuid(SERVICE_ID).build());

    Workflow workflow1 = constructCanaryWorkflowWithPhase();

    Workflow workflow2 = workflowService.createWorkflow(workflow1);
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid");

    Workflow workflow3 = workflowService.readWorkflow(workflow2.getAppId(), workflow2.getUuid());
    assertThat(workflow3).isNotNull();
    CanaryOrchestrationWorkflow orchestrationWorkflow3 =
        (CanaryOrchestrationWorkflow) workflow3.getOrchestrationWorkflow();

    assertThat(orchestrationWorkflow3.getWorkflowPhases()).isNotNull().hasSize(1);

    WorkflowPhase workflowPhase = orchestrationWorkflow3.getWorkflowPhases().get(0);

    PhaseStep verifyPhaseStep = constructAppDVerifyStep(workflowPhase);

    workflowService.updateWorkflowPhase(workflow2.getAppId(), workflow2.getUuid(), workflowPhase);

    Workflow workflow4 = workflowService.readWorkflow(workflow2.getAppId(), workflow2.getUuid());
    CanaryOrchestrationWorkflow orchestrationWorkflow4 =
        (CanaryOrchestrationWorkflow) workflow4.getOrchestrationWorkflow();

    workflowPhase = orchestrationWorkflow4.getWorkflowPhases().get(0);
    verifyPhaseStep = workflowPhase.getPhaseSteps()
                          .stream()
                          .filter(ps -> ps.getPhaseStepType() == PhaseStepType.VERIFY_SERVICE)
                          .collect(toList())
                          .get(0);

    List<TemplateExpression> appDTemplateExpressions = constructAppdTemplateExpressions();
    List<TemplateExpression> elkTemplateExpressions = constructElkTemplateExpressions();

    verifyPhaseStep.getSteps().get(0).setTemplateExpressions(appDTemplateExpressions);
    verifyPhaseStep.getSteps().get(1).setTemplateExpressions(elkTemplateExpressions);

    workflowService.updateWorkflowPhase(workflow2.getAppId(), workflow2.getUuid(), workflowPhase);

    Workflow workflow5 = workflowService.readWorkflow(workflow2.getAppId(), workflow2.getUuid());
    assertThat(workflow5).isNotNull();
    CanaryOrchestrationWorkflow orchestrationWorkflow5 =
        (CanaryOrchestrationWorkflow) workflow5.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow5).extracting("userVariables").isNotNull();
    assertThat(orchestrationWorkflow5.getUserVariables())
        .extracting(Variable::obtainEntityType)
        .containsSequence(APPDYNAMICS_CONFIGID, EntityType.APPDYNAMICS_APPID, EntityType.APPDYNAMICS_TIERID,
            ELK_CONFIGID, ELK_INDICES);
  }

  /**
   * Test custom metric yaml generation
   * @throws Exception
   */
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void testGetHPAYamlStringWithCustomMetric() throws Exception {
    Integer minAutoscaleInstances = 2;
    Integer maxAutoscaleInstances = 10;
    Integer targetCpuUtilizationPercentage = 60;

    String yamlHPA = workflowService.getHPAYamlStringWithCustomMetric(
        minAutoscaleInstances, maxAutoscaleInstances, targetCpuUtilizationPercentage);

    HorizontalPodAutoscaler horizontalPodAutoscaler = KubernetesHelper.loadYaml(yamlHPA);
    assertThat(horizontalPodAutoscaler.getApiVersion()).isEqualTo("autoscaling/v2beta1");
    assertThat(horizontalPodAutoscaler.getKind()).isEqualTo("HorizontalPodAutoscaler");
    assertThat(horizontalPodAutoscaler.getSpec()).isNotNull();
    assertThat(horizontalPodAutoscaler.getMetadata()).isNotNull();
    assertThat(horizontalPodAutoscaler.getSpec().getMinReplicas()).isEqualTo(Integer.valueOf(2));
    assertThat(horizontalPodAutoscaler.getSpec().getMaxReplicas()).isEqualTo(Integer.valueOf(10));
    assertThat(horizontalPodAutoscaler.getSpec().getAdditionalProperties()).isNotNull();
    assertThat(horizontalPodAutoscaler.getSpec().getAdditionalProperties()).hasSize(1);
    assertThat(horizontalPodAutoscaler.getSpec().getAdditionalProperties().keySet().iterator().next())
        .isEqualTo("metrics");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetResolvedServices() {
    Workflow workflow1 = constructCanaryWorkflowWithPhase();

    Workflow savedWorkflow = workflowService.createWorkflow(workflow1);
    assertThat(savedWorkflow).isNotNull().hasFieldOrProperty("uuid");
    List<Service> resolvedServices = workflowService.getResolvedServices(savedWorkflow, null);
    assertThat(resolvedServices).isNotEmpty().extracting(Service::getName).contains(SERVICE_NAME);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetResolvedTemplatizedServices() {
    Workflow workflow1 = createCanaryWorkflow();

    WorkflowPhase workflowPhase = aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase);

    WorkflowPhase workflowPhase2 =
        aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase2);

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();

    List<WorkflowPhase> workflowPhases2 =
        ((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getWorkflowPhases();
    workflowPhase2 = workflowPhases2.get(workflowPhases2.size() - 1);
    workflowPhase2.setName("phase2-changed");

    workflowPhase2.setTemplateExpressions(asList(getServiceTemplateExpression(), prepareInfraDefTemplateExpression()));

    workflowService.updateWorkflowPhase(workflow2.getAppId(), workflow2.getUuid(), workflowPhase2);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    List<WorkflowPhase> workflowPhases3 =
        ((CanaryOrchestrationWorkflow) workflow3.getOrchestrationWorkflow()).getWorkflowPhases();
    WorkflowPhase workflowPhase3 = workflowPhases3.get(workflowPhases3.size() - 1);
    assertThat(workflowPhase3).isEqualToComparingOnlyGivenFields(workflowPhase2, "uuid", "name");

    assertThat(workflowPhase3.getInfraDefinitionId()).isNotNull();
    assertThat(workflowPhase3.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("infraDefinitionId");
    assertThat(workflow3.getOrchestrationWorkflow().getUserVariables())
        .extracting(Variable::obtainEntityType)
        .containsSequence(SERVICE, INFRASTRUCTURE_DEFINITION);

    when(serviceResourceService.fetchServicesByUuids(APP_ID, java.util.Arrays.asList(SERVICE_ID)))
        .thenReturn(java.util.Arrays.asList(service));
    List<Service> resolvedServices =
        workflowService.getResolvedServices(workflow3, ImmutableMap.of("Service", SERVICE_ID));
    assertThat(resolvedServices).isNotEmpty().extracting(Service::getName).contains(SERVICE_NAME);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetResolvedInfraDef() {
    InfrastructureDefinition infrastructureDefinition = buildAwsSshInfraDef(INFRA_DEFINITION_ID);
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(infrastructureDefinition);
    when(infrastructureDefinitionService.getInfraStructureDefinitionByUuids(
             APP_ID, java.util.Arrays.asList(INFRA_DEFINITION_ID)))
        .thenReturn(java.util.Arrays.asList(infrastructureDefinition));
    Workflow workflow1 = constructCanaryWorkflowWithPhase();

    Workflow savedWorkflow = workflowService.createWorkflow(workflow1);
    assertThat(savedWorkflow).isNotNull().hasFieldOrProperty("uuid");
    List<InfrastructureDefinition> resolvedInfraDefinitions =
        workflowService.getResolvedInfraDefinitions(savedWorkflow, null, null);
    assertThat(resolvedInfraDefinitions)
        .isNotEmpty()
        .extracting(InfrastructureDefinition::getUuid)
        .contains(INFRA_DEFINITION_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetResolvedTemplatizedInfraMappings() {
    mockAwsInfraDef(INFRA_DEFINITION_ID);
    when(infrastructureDefinitionService.getInfraStructureDefinitionByUuids(
             APP_ID, java.util.Arrays.asList(INFRA_DEFINITION_ID)))
        .thenReturn(java.util.Arrays.asList(buildAwsSshInfraDef(INFRA_DEFINITION_ID)));

    Workflow workflow1 = createCanaryWorkflow();
    WorkflowPhase workflowPhase = aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase);

    WorkflowPhase workflowPhase2 =
        aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase2);

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();

    List<WorkflowPhase> workflowPhases2 =
        ((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getWorkflowPhases();
    workflowPhase2 = workflowPhases2.get(workflowPhases2.size() - 1);
    workflowPhase2.setName("phase2-changed");

    workflowPhase2.setTemplateExpressions(asList(getServiceTemplateExpression(), prepareInfraDefTemplateExpression()));

    workflowService.updateWorkflowPhase(workflow2.getAppId(), workflow2.getUuid(), workflowPhase2);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    List<WorkflowPhase> workflowPhases3 =
        ((CanaryOrchestrationWorkflow) workflow3.getOrchestrationWorkflow()).getWorkflowPhases();
    WorkflowPhase workflowPhase3 = workflowPhases3.get(workflowPhases3.size() - 1);
    assertThat(workflowPhase3).isEqualToComparingOnlyGivenFields(workflowPhase2, "uuid", "name");

    assertThat(workflowPhase3.getInfraDefinitionId()).isNotNull();
    assertThat(workflowPhase3.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("infraDefinitionId");
    assertThat(workflow3.getOrchestrationWorkflow().getUserVariables())
        .extracting(Variable::obtainEntityType)
        .containsSequence(SERVICE, INFRASTRUCTURE_DEFINITION);

    when(serviceResourceService.fetchServicesByUuids(APP_ID, java.util.Arrays.asList(SERVICE_ID)))
        .thenReturn(java.util.Arrays.asList(service));

    when(infrastructureDefinitionService.getInfraDefById(workflow3.getAccountId(), INFRA_DEFINITION_ID))
        .thenReturn(InfrastructureDefinition.builder().uuid(INFRA_DEFINITION_ID).build());

    List<InfrastructureDefinition> resolvedInfraDefinitions = workflowService.getResolvedInfraDefinitions(
        workflow3, ImmutableMap.of("Service", SERVICE_ID, "InfraDef_SSH", INFRA_DEFINITION_ID), null);

    assertThat(resolvedInfraDefinitions)
        .isNotEmpty()
        .extracting(InfrastructureDefinition::getUuid)
        .contains(INFRA_DEFINITION_ID);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldObtainEnvironmentIdWithoutOrchestration() {
    Workflow workflow2 = workflowService.createWorkflow(constructBasicWorkflowWithPhase());
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);
    assertThat(workflowService.obtainTemplatedEnvironmentId(workflow2, null)).isEqualTo(ENV_ID);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldObtainEnvironmentIdWithoutOrchestrationForTemplatizedWorkflow() {
    Workflow workflow = constructBasicWorkflowWithPhase();
    workflow.setTemplateExpressions(Collections.singletonList(getEnvTemplateExpression()));
    Workflow workflow2 = workflowService.createWorkflow(workflow);
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);
    assertThat(
        workflowService.obtainEnvIdWithoutOrchestration(workflow2, ImmutableMap.of("Environment", ENV_ID_CHANGED)))
        .isEqualTo(ENV_ID_CHANGED);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetResolvedEnvironmentId() {
    Workflow workflow2 = workflowService.createWorkflow(constructBasicWorkflowWithPhase());
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);

    assertThat(workflowService.resolveEnvironmentId(workflow2, ImmutableMap.of("Environment", ENV_ID)))
        .isNotNull()
        .isEqualTo(ENV_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetResolvedEnvironmentIdForTemplatizedWorkflow() {
    Workflow workflow = constructBasicWorkflowWithPhase();
    workflow.setTemplateExpressions(java.util.Arrays.asList(getEnvTemplateExpression()));

    Workflow workflow2 = workflowService.createWorkflow(workflow);
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);

    assertThat(workflowService.resolveEnvironmentId(workflow2, ImmutableMap.of("Environment", ENV_ID_CHANGED)))
        .isNotNull()
        .isEqualTo(ENV_ID_CHANGED);
  }

  private PhaseStep createPhaseStep(String uuid) {
    return aPhaseStep(PhaseStepType.CONTAINER_DEPLOY, DEPLOY_CONTAINERS)
        .addStep(GraphNode.builder()
                     .id(generateUuid())
                     .type(JENKINS.getName())
                     .name(UPGRADE_CONTAINERS)
                     .properties(ImmutableMap.<String, Object>builder().put(JENKINS.getName(), uuid).build())
                     .build())
        .build();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testSettingsServiceDeleting() {
    String uuid = generateUuid();

    SettingAttribute settingAttribute = aSettingAttribute()
                                            .withAppId("APP_ID")
                                            .withAccountId("ACCOUNT_ID")
                                            .withName("SETTING_NAME")
                                            .withCategory(SettingCategory.CONNECTOR)
                                            .withUuid(uuid)
                                            .withValue(JenkinsConfig.builder()
                                                           .jenkinsUrl(JENKINS_URL)
                                                           .password(PASSWORD)
                                                           .username(USER_NAME)
                                                           .accountId("ACCOUNT_ID")
                                                           .build())
                                            .build();

    // Create a workflow with a random Jenkins Id
    PhaseStep phaseStep = createPhaseStep(generateUuid());
    Workflow workflow = constructWorkflowWithParam(phaseStep);
    workflowService.createWorkflow(workflow);
    assertThat(workflowService.settingsServiceDeleting(settingAttribute)).isNull();

    // Create a workflow with a specific Jenkins Id
    phaseStep = createPhaseStep(uuid);
    workflow = constructWorkflowWithParam(phaseStep);
    workflow.setName(WORKFLOW_NAME + "1");
    workflowService.createWorkflow(workflow);
    assertThat(workflowService.settingsServiceDeleting(settingAttribute).message()).isNotNull();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCreateWorkflowLinkHttpTemplate() {
    Workflow savedWorkflow = createLinkedTemplateWorkflow();

    assertThat(savedWorkflow).isNotNull();
    assertThat(savedWorkflow.getLinkedTemplateUuids()).isNotEmpty().contains(TEMPLATE_ID);

    OrchestrationWorkflow orchestrationWorkflow = savedWorkflow.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow).isNotNull();
    assertThat(orchestrationWorkflow).isInstanceOf(CanaryOrchestrationWorkflow.class);

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow = (CanaryOrchestrationWorkflow) orchestrationWorkflow;

    assertThat(canaryOrchestrationWorkflow.getPreDeploymentSteps()).isNotNull();

    PhaseStep phaseStep = canaryOrchestrationWorkflow.getPreDeploymentSteps();
    assertThat(phaseStep).isNotNull();
    GraphNode preDeploymentStep = phaseStep.getSteps().stream().findFirst().orElse(null);
    assertPreDeployTemplateStep(preDeploymentStep);

    PhaseStep postPhaseStep = canaryOrchestrationWorkflow.getPostDeploymentSteps();
    assertThat(postPhaseStep).isNotNull();
    GraphNode postDeploymentStep = phaseStep.getSteps().stream().findFirst().orElse(null);
    assertPostDeployTemplateStep(postDeploymentStep);

    WorkflowPhase workflowPhase = canaryOrchestrationWorkflow.getWorkflowPhases().get(0);
    assertThat(workflowPhase.getPhaseSteps()).isNotEmpty();

    PhaseStep phaseStep1 = workflowPhase.getPhaseSteps().stream().findFirst().orElse(null);
    assertThat(phaseStep1).isNotNull();
    GraphNode phaseNode = phaseStep1.getSteps().stream().findFirst().orElse(null);
    assertWorkflowPhaseTemplateStep(phaseNode);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCloneWorkflowPhaseWithLinkedTemplate() {
    Workflow savedWorkflow = createLinkedTemplateWorkflow();
    assertThat(savedWorkflow).isNotNull();
    assertThat(savedWorkflow.getLinkedTemplateUuids()).isNotEmpty().contains(TEMPLATE_ID);
    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        (CanaryOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    WorkflowPhase clonedWorkflowPhase = workflowService.cloneWorkflowPhase(
        savedWorkflow.getAppId(), savedWorkflow.getUuid(), canaryOrchestrationWorkflow.getWorkflowPhases().get(0));
    assertThat(clonedWorkflowPhase).isNotNull();
    assertThat(clonedWorkflowPhase.getPhaseSteps()).isNotEmpty();

    PhaseStep phaseStep1 = clonedWorkflowPhase.getPhaseSteps().stream().findFirst().orElse(null);
    assertThat(phaseStep1).isNotNull();
    GraphNode phaseNode = phaseStep1.getSteps().stream().findFirst().orElse(null);
    assertWorkflowPhaseTemplateStep(phaseNode);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateLinkedWorkflowVariables() {
    Workflow savedWorkflow = createLinkedTemplateWorkflow();

    assertThat(savedWorkflow).isNotNull();
    assertThat(savedWorkflow.getLinkedTemplateUuids()).isNotEmpty().contains(TEMPLATE_ID);

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        (CanaryOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    assertThat(canaryOrchestrationWorkflow.getPreDeploymentSteps()).isNotNull();

    PhaseStep phaseStep = canaryOrchestrationWorkflow.getPreDeploymentSteps();
    assertThat(phaseStep).isNotNull();
    GraphNode preDeploymentStep = phaseStep.getSteps().stream().findFirst().orElse(null);
    assertThat(preDeploymentStep).isNotNull();
    assertThat(preDeploymentStep.getTemplateUuid()).isNotEmpty().isEqualTo(TEMPLATE_ID);
    assertThat(preDeploymentStep.getTemplateVersion()).isNotEmpty().isEqualTo(LATEST_TAG);

    List<Variable> templateVariables = preDeploymentStep.getTemplateVariables();
    assertThat(templateVariables).isNotEmpty();

    preDeploymentStep.setTemplateVariables(
        java.util.Arrays.asList(aVariable().name("url").value("https://google.com").build()));

    Workflow oldWorkflow = workflowService.readWorkflow(savedWorkflow.getAppId(), savedWorkflow.getUuid());

    Workflow workflow = workflowService.updateLinkedWorkflow(savedWorkflow, oldWorkflow, true);

    CanaryOrchestrationWorkflow updatedCanaryWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    assertThat(canaryOrchestrationWorkflow.getPreDeploymentSteps()).isNotNull();

    PhaseStep updatedPhaseStep = updatedCanaryWorkflow.getPreDeploymentSteps();
    assertThat(updatedPhaseStep).isNotNull();
    GraphNode updatedPreStep = phaseStep.getSteps().stream().findFirst().orElse(null);
    assertThat(updatedPreStep).isNotNull();
    assertThat(updatedPreStep.getTemplateUuid()).isNotEmpty().isEqualTo(TEMPLATE_ID);
    assertThat(updatedPreStep.getTemplateVersion()).isNotEmpty().isEqualTo(LATEST_TAG);

    assertThat(updatedPreStep.getTemplateVariables()).isNotEmpty();

    assertThat(updatedPreStep.getTemplateVariables()).isNotEmpty().extracting(Variable::getName).contains("url");
    assertThat(updatedPreStep.getTemplateVariables())
        .isNotEmpty()
        .extracting(Variable::getValue)
        .contains("https://google.com");
    assertThat(preDeploymentStep.getProperties()).isNotEmpty().containsKeys("url", "method", "assertion");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateLinkedPreDeploymentVersionChange() {
    Workflow savedWorkflow = createLinkedTemplateWorkflow();

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        (CanaryOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    assertThat(canaryOrchestrationWorkflow.getPreDeploymentSteps()).isNotNull();

    PhaseStep phaseStep = canaryOrchestrationWorkflow.getPreDeploymentSteps();
    assertThat(phaseStep).isNotNull();
    GraphNode preDeploymentStep = phaseStep.getSteps().stream().findFirst().orElse(null);
    assertTemplateStep(preDeploymentStep);

    GraphNode templateStep = constructHttpTemplateStep();

    final Template httpTemplate = Template.builder().templateObject(HttpTemplate.builder().build()).build();
    when(templateService.get(TEMPLATE_ID, "1")).thenReturn(httpTemplate);
    when(templateService.constructEntityFromTemplate(httpTemplate, EntityType.WORKFLOW)).thenReturn(templateStep);
    when(templateService.constructEntityFromTemplate(preDeploymentStep.getTemplateUuid(), "1", EntityType.WORKFLOW))
        .thenReturn(templateStep);

    PhaseStep updatedPhaseStep =
        workflowService.updatePreDeployment(savedWorkflow.getAppId(), savedWorkflow.getUuid(), phaseStep);

    assertLinkedPhaseStep(phaseStep, preDeploymentStep, updatedPhaseStep);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateLinkedPostDeploymentVersionChange() {
    Workflow savedWorkflow = createLinkedTemplateWorkflow();

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        (CanaryOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    assertThat(canaryOrchestrationWorkflow.getPostDeploymentSteps()).isNotNull();

    PhaseStep phaseStep = canaryOrchestrationWorkflow.getPostDeploymentSteps();
    assertThat(phaseStep).isNotNull();
    GraphNode postDeploymentStep = phaseStep.getSteps().stream().findFirst().orElse(null);

    assertTemplateStep(postDeploymentStep);

    GraphNode templateStep = constructHttpTemplateStep();

    final Template httpTemplate =
        Template.builder()
            .templateObject(HttpTemplate.builder().url("MyUrl").method("MyMethod").assertion("Assertion").build())
            .build();
    when(templateService.get(TEMPLATE_ID, "1")).thenReturn(httpTemplate);
    when(templateService.constructEntityFromTemplate(httpTemplate, EntityType.WORKFLOW)).thenReturn(templateStep);

    PhaseStep updatedPhaseStep =
        workflowService.updatePostDeployment(savedWorkflow.getAppId(), savedWorkflow.getUuid(), phaseStep);

    assertLinkedPhaseStep(phaseStep, postDeploymentStep, updatedPhaseStep);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateLinkedWorkflowPhaseVersionChange() {
    Workflow savedWorkflow = createLinkedTemplateWorkflow();

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        (CanaryOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    assertThat(canaryOrchestrationWorkflow.getPostDeploymentSteps()).isNotNull();

    List<WorkflowPhase> workflowPhases = canaryOrchestrationWorkflow.getWorkflowPhases();
    assertThat(workflowPhases).isNotEmpty().size().isGreaterThan(0);
    WorkflowPhase workflowPhase = workflowPhases.get(0);

    assertThat(workflowPhase.getPhaseSteps()).isNotEmpty().size().isGreaterThan(0);
    PhaseStep phaseStep = workflowPhase.getPhaseSteps().get(0);
    assertThat(phaseStep).isNotNull();

    GraphNode phaseNode = phaseStep.getSteps().stream().findFirst().orElse(null);
    assertTemplateStep(phaseNode);

    GraphNode templateStep = constructHttpTemplateStep();

    final Template httpTemplate = Template.builder().templateObject(HttpTemplate.builder().build()).build();
    when(templateService.get(TEMPLATE_ID, "1")).thenReturn(httpTemplate);
    when(templateService.constructEntityFromTemplate(httpTemplate, EntityType.WORKFLOW)).thenReturn(templateStep);

    WorkflowPhase updateWorkflowPhase =
        workflowService.updateWorkflowPhase(savedWorkflow.getAppId(), savedWorkflow.getUuid(), workflowPhase);

    assertThat(updateWorkflowPhase).isNotNull();
    PhaseStep workflowPhaseStep = updateWorkflowPhase.getPhaseSteps().stream().findFirst().orElse(null);
    GraphNode updatedPhaseNode = workflowPhaseStep.getSteps().stream().findFirst().orElse(null);
    assertPhaseNode(updatedPhaseNode);
    assertThat(phaseNode.getProperties()).isNotEmpty().containsKeys("url", "method");
    assertThat(phaseNode.getProperties()).isNotEmpty().doesNotContainValue("200 OK");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateLinkedWorkflowVersionChange() {
    Workflow savedWorkflow = createLinkedTemplateWorkflow();

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        (CanaryOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    assertThat(canaryOrchestrationWorkflow.getPostDeploymentSteps()).isNotNull();

    List<WorkflowPhase> workflowPhases = canaryOrchestrationWorkflow.getWorkflowPhases();
    assertThat(workflowPhases).isNotEmpty().size().isGreaterThan(0);
    WorkflowPhase workflowPhase = workflowPhases.get(0);

    assertThat(workflowPhase.getPhaseSteps()).isNotEmpty().size().isGreaterThan(0);
    PhaseStep phaseStep = workflowPhase.getPhaseSteps().get(0);
    assertThat(phaseStep).isNotNull();
    GraphNode phaseNode = phaseStep.getSteps().stream().findFirst().orElse(null);

    assertTemplateStep(phaseNode);

    GraphNode templateStep = constructHttpTemplateStep();

    final Template httpTemplate = Template.builder().templateObject(HttpTemplate.builder().build()).build();
    when(templateService.get(TEMPLATE_ID, "1")).thenReturn(httpTemplate);
    when(templateService.constructEntityFromTemplate(httpTemplate, EntityType.WORKFLOW)).thenReturn(templateStep);

    Workflow oldWorkflow = workflowService.readWorkflow(savedWorkflow.getAppId(), savedWorkflow.getUuid());

    Workflow updatedWorkflow = workflowService.updateLinkedWorkflow(savedWorkflow, oldWorkflow, true);
    assertThat(updatedWorkflow).isNotNull();

    CanaryOrchestrationWorkflow updatedCanaryWorkflow =
        (CanaryOrchestrationWorkflow) updatedWorkflow.getOrchestrationWorkflow();
    assertThat(updatedCanaryWorkflow).isNotNull();
    assertThat(updatedCanaryWorkflow.getWorkflowPhases()).isNotEmpty();

    WorkflowPhase updateWorkflowPhase = updatedCanaryWorkflow.getWorkflowPhases().get(0);
    assertThat(updateWorkflowPhase).isNotNull();

    PhaseStep workflowPhaseStep = updateWorkflowPhase.getPhaseSteps().stream().findFirst().orElse(null);
    GraphNode updatedPhaseNode = workflowPhaseStep.getSteps().stream().findFirst().orElse(null);
    assertPhaseNode(updatedPhaseNode);
    assertThat(phaseNode.getProperties()).isNotEmpty().containsKeys("url", "method");
    assertThat(phaseNode.getProperties()).isNotEmpty().doesNotContainValue("200 OK");
  }

  private Workflow createLinkedTemplateWorkflow() {
    GraphNode step = GraphNode.builder()
                         .templateUuid(TEMPLATE_ID)
                         .templateVersion(LATEST_TAG)
                         .name("Ping Response")
                         .type(HTTP.name())
                         .build();

    GraphNode templateStep = constructHttpTemplateStep();

    final Template httpTemplate =
        Template.builder()
            .templateObject(HttpTemplate.builder().url("MyUrl").method("MyMethod").assertion("Assertion").build())
            .build();
    when(templateService.get(TEMPLATE_ID, LATEST_TAG)).thenReturn(httpTemplate);
    when(templateService.constructEntityFromTemplate(httpTemplate, EntityType.WORKFLOW)).thenReturn(templateStep);

    Workflow workflow = constructLinkedTemplate(step);

    return workflowService.createWorkflow(workflow);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTestGetGraphNodeInPredeploymentStep() {
    Workflow workflow = workflowService.createWorkflow(constructCanaryWithHttpStep());
    assertThat(workflow).isNotNull();

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    assertOrchestrationWorkflow(canaryOrchestrationWorkflow);

    String nodeId = canaryOrchestrationWorkflow.getPreDeploymentSteps().getSteps().get(0).getId();

    GraphNode graphNode = workflowService.readGraphNode(workflow.getAppId(), workflow.getUuid(), nodeId);
    assertThat(graphNode).isNotNull();
    assertThat(graphNode.getProperties()).isNotEmpty().containsKeys("url");
    assertThat(graphNode.getProperties()).isNotEmpty().containsValues("http://www.google.com");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTestGetGraphNodeInPostdeploymentStep() {
    Workflow workflow = workflowService.createWorkflow(constructCanaryHttpAsPostDeploymentStep());
    assertThat(workflow).isNotNull();

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    assertOrchestrationWorkflow(canaryOrchestrationWorkflow);

    String nodeId = canaryOrchestrationWorkflow.getPostDeploymentSteps().getSteps().get(0).getId();

    GraphNode graphNode = workflowService.readGraphNode(workflow.getAppId(), workflow.getUuid(), nodeId);
    assertThat(graphNode).isNotNull();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTestGetGraphNodeInPhaseStep() {
    Workflow workflow = workflowService.createWorkflow(constructCanaryWithHttpPhaseStep());
    assertThat(workflow).isNotNull();

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    assertOrchestrationWorkflow(canaryOrchestrationWorkflow);

    String nodeId =
        canaryOrchestrationWorkflow.getWorkflowPhases().get(0).getPhaseSteps().get(0).getSteps().get(0).getId();
    GraphNode graphNode = workflowService.readGraphNode(workflow.getAppId(), workflow.getUuid(), nodeId);
    assertThat(graphNode).isNotNull();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCheckEnvironmentServiceOrInfraReferenced() {
    Workflow workflow = workflowService.createWorkflow(constructCanaryWorkflowWithPhase());
    assertThat(workflow).isNotNull();

    assertThat(workflowService.obtainWorkflowNamesReferencedByEnvironment(workflow.getAppId(), workflow.getEnvId()))
        .isNotEmpty()
        .contains(workflow.getName());

    assertThat(workflowService.obtainWorkflowNamesReferencedByService(workflow.getAppId(), SERVICE_ID))
        .isNotEmpty()
        .contains(workflow.getName());

    assertThat(workflowService.obtainWorkflowNamesReferencedByInfrastructureDefinition(
                   workflow.getAppId(), INFRA_DEFINITION_ID))
        .isNotEmpty()
        .contains(workflow.getName());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetDeploymentMetadataForLinkedHttpWorkflow() {
    Workflow workflow = createLinkedWorkflow(TemplateType.HTTP);
    assertThat(
        workflowService.fetchDeploymentMetadata(APP_ID, workflow, null, null, null).getArtifactRequiredServiceIds())
        .contains(SERVICE_ID);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetDeploymentMetadataForLinkedShellScriptWorkflow() {
    Workflow workflow = createLinkedWorkflow(TemplateType.SHELL_SCRIPT);
    assertThat(
        workflowService.fetchDeploymentMetadata(APP_ID, workflow, null, null, null).getArtifactRequiredServiceIds())
        .contains(SERVICE_ID);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetDeploymentMetadataForLinkedCommandWorkflow() {
    Workflow workflow = createLinkedWorkflow(TemplateType.SSH);
    assertThat(
        workflowService.fetchDeploymentMetadata(APP_ID, workflow, null, null, null).getArtifactRequiredServiceIds())
        .contains(SERVICE_ID);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldGetDeploymentMetadataWithDeploymentFFTurnOn() {
    Workflow workflow = createLinkedWorkflow(TemplateType.SSH);
    when(appService.get(APP_ID)).thenReturn(application);
    when(accountService.get(any())).thenReturn(account);
    assertThat(workflowService.fetchDeploymentMetadata(APP_ID, workflow, null, null, null).getArtifactVariables())
        .isNotNull();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldUpdateArtifactVariablesWithoutDefaultArtifact() {
    when(serviceResourceService.get(APP_ID, SERVICE_ID)).thenReturn(Service.builder().name(SERVICE_NAME).build());
    ArtifactVariable artifactVariable = ArtifactVariable.builder().entityType(SERVICE).entityId(SERVICE_ID).build();
    workflowService.updateArtifactVariables(APP_ID, null, Collections.singletonList(artifactVariable), false, null);
    assertThat(artifactVariable.getDisplayInfo()).isNotNull();
    assertThat(artifactVariable.getDisplayInfo()).containsKeys("services");
    assertThat(artifactVariable.getDisplayInfo().get("services")).contains(SERVICE_NAME);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldUpdateArtifactVariablesWithDefaultArtifact() {
    when(serviceResourceService.get(APP_ID, SERVICE_ID)).thenReturn(Service.builder().name(SERVICE_NAME).build());

    ArtifactStream artifactStream = DockerArtifactStream.builder().uuid(ARTIFACT_STREAM_ID).build();
    ArtifactStream artifactStreamArtifactory =
        ArtifactoryArtifactStream.builder().uuid(ARTIFACT_STREAM_ID_ARTIFACTORY).build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactStream);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID_ARTIFACTORY)).thenReturn(artifactStreamArtifactory);
    when(artifactService.fetchLastCollectedApprovedArtifactSorted(artifactStream))
        .thenReturn(anArtifact().withUuid(ARTIFACT_ID).build());
    when(artifactService.fetchLastCollectedApprovedArtifactSorted(artifactStreamArtifactory)).thenReturn(null);
    when(appService.getAccountIdByAppId(any())).thenReturn(ACCOUNT_ID);

    ArtifactVariable artifactVariable1 = ArtifactVariable.builder().entityType(SERVICE).entityId(SERVICE_ID).build();
    ArtifactVariable artifactVariable2 = ArtifactVariable.builder()
                                             .entityType(SERVICE)
                                             .entityId(SERVICE_ID)
                                             .allowedList(Collections.singletonList(ARTIFACT_STREAM_ID))
                                             .build();
    ArtifactVariable artifactVariable3 = ArtifactVariable.builder()
                                             .entityType(SERVICE)
                                             .entityId(SERVICE_ID)
                                             .allowedList(Collections.singletonList(ARTIFACT_STREAM_ID_ARTIFACTORY))
                                             .build();
    ArtifactVariable artifactVariable4 = ArtifactVariable.builder()
                                             .entityType(SERVICE)
                                             .entityId(SERVICE_ID)
                                             .allowedList(asList("stream1", "stream2"))
                                             .build();
    workflowService.updateArtifactVariables(
        APP_ID, null, asList(artifactVariable1, artifactVariable2, artifactVariable3, artifactVariable4), true, null);

    assertThat(artifactVariable1.getArtifactStreamSummaries()).isNullOrEmpty();

    assertThat(artifactVariable2.getArtifactStreamSummaries()).isNotNull();
    assertThat(artifactVariable2.getArtifactStreamSummaries().size()).isEqualTo(1);
    assertThat(artifactVariable2.getArtifactStreamSummaries().get(0).getArtifactStreamId())
        .isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(artifactVariable2.getArtifactStreamSummaries().get(0).getDefaultArtifact().getUuid())
        .isEqualTo(ARTIFACT_ID);

    assertThat(artifactVariable3.getArtifactStreamSummaries()).isNotNull();
    assertThat(artifactVariable3.getArtifactStreamSummaries().size()).isEqualTo(1);
    assertThat(artifactVariable3.getArtifactStreamSummaries().get(0).getArtifactStreamId())
        .isEqualTo(ARTIFACT_STREAM_ID_ARTIFACTORY);
    assertThat(artifactVariable3.getArtifactStreamSummaries().get(0).getDefaultArtifact()).isNull();

    assertThat(artifactVariable4.getArtifactStreamSummaries()).isNotNull();
    assertThat(artifactVariable4.getArtifactStreamSummaries().size()).isEqualTo(2);
    assertThat(artifactVariable4.getArtifactStreamSummaries().get(0).getArtifactStreamId()).isEqualTo("stream1");
    assertThat(artifactVariable4.getArtifactStreamSummaries().get(0).getDefaultArtifact()).isNull();
    assertThat(artifactVariable4.getArtifactStreamSummaries().get(1).getArtifactStreamId()).isEqualTo("stream2");
    assertThat(artifactVariable4.getArtifactStreamSummaries().get(1).getDefaultArtifact()).isNull();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotUpdateDefaultArtifactWithFFOff() {
    when(serviceResourceService.get(APP_ID, SERVICE_ID)).thenReturn(Service.builder().name(SERVICE_NAME).build());

    ArtifactStream artifactStream = DockerArtifactStream.builder().uuid(ARTIFACT_STREAM_ID).build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactStream);
    when(artifactService.fetchLastCollectedApprovedArtifactSorted(artifactStream))
        .thenReturn(anArtifact().withUuid(ARTIFACT_ID).build());
    when(appService.getAccountIdByAppId(any())).thenReturn(ACCOUNT_ID);

    ArtifactVariable artifactVariable2 = ArtifactVariable.builder()
                                             .entityType(SERVICE)
                                             .entityId(SERVICE_ID)
                                             .allowedList(Collections.singletonList(ARTIFACT_STREAM_ID))
                                             .build();
    when(featureFlagService.isEnabled(FeatureName.DEFAULT_ARTIFACT, ACCOUNT_ID)).thenReturn(false);
    workflowService.updateArtifactVariables(APP_ID, null, java.util.Arrays.asList(artifactVariable2), true, null);

    assertThat(artifactVariable2.getArtifactStreamSummaries()).isNotNull();
    assertThat(artifactVariable2.getArtifactStreamSummaries().size()).isEqualTo(1);
    assertThat(artifactVariable2.getArtifactStreamSummaries().get(0).getArtifactStreamId())
        .isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(artifactVariable2.getArtifactStreamSummaries().get(0).getDefaultArtifact()).isEqualTo(null);
  }
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldUpdateArtifactVariablesWithDefaultArtifactAndExecution() {
    WorkflowExecution workflowExecution = prepareWorkflowExecutionWithArtifactVariables(false);
    ArtifactVariable artifactVariable1 = ArtifactVariable.builder()
                                             .entityType(SERVICE)
                                             .entityId(SERVICE_ID)
                                             .name("art_srv")
                                             .allowedList(Collections.singletonList(ARTIFACT_STREAM_ID))
                                             .build();
    ArtifactVariable artifactVariable2 = ArtifactVariable.builder()
                                             .entityType(SERVICE)
                                             .entityId(SERVICE_ID)
                                             .name("art_invalid")
                                             .allowedList(Collections.singletonList(ARTIFACT_STREAM_ID))
                                             .build();
    when(artifactService.get("art1"))
        .thenReturn(anArtifact().withUuid("art1").withArtifactStreamId(ARTIFACT_STREAM_ID).build());
    workflowService.updateArtifactVariables(
        APP_ID, null, asList(artifactVariable1, artifactVariable2), true, workflowExecution);

    assertThat(artifactVariable1.getArtifactStreamSummaries()).isNotNull();
    assertThat(artifactVariable1.getArtifactStreamSummaries().size()).isEqualTo(1);
    assertThat(artifactVariable1.getArtifactStreamSummaries().get(0).getArtifactStreamId())
        .isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(artifactVariable1.getArtifactStreamSummaries().get(0).getDefaultArtifact().getUuid()).isEqualTo("art1");

    assertThat(artifactVariable2.getArtifactStreamSummaries()).isNotNull();
    assertThat(artifactVariable2.getArtifactStreamSummaries().size()).isEqualTo(1);
    assertThat(artifactVariable2.getArtifactStreamSummaries().get(0).getArtifactStreamId())
        .isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(artifactVariable2.getArtifactStreamSummaries().get(0).getDefaultArtifact()).isNull();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetArtifactVariableDefaultArtifactForService() {
    WorkflowExecution workflowExecution = prepareWorkflowExecutionWithArtifactVariables(false);
    when(artifactService.get("art1"))
        .thenReturn(anArtifact().withUuid("art1").withArtifactStreamId(ARTIFACT_STREAM_ID).build());
    Artifact artifact = workflowService.getArtifactVariableDefaultArtifact(
        ArtifactVariable.builder()
            .entityType(SERVICE)
            .entityId(SERVICE_ID)
            .name("art_srv")
            .allowedList(Collections.singletonList(ARTIFACT_STREAM_ID))
            .build(),
        workflowExecution);
    assertThat(artifact).isNotNull();
    assertThat(artifact.getUuid()).isEqualTo("art1");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetArtifactVariableDefaultArtifactForEnv() {
    WorkflowExecution workflowExecution = prepareWorkflowExecutionWithArtifactVariables(false);
    when(artifactService.get("art2"))
        .thenReturn(anArtifact().withUuid("art2").withArtifactStreamId(ARTIFACT_STREAM_ID).build());
    Artifact artifact = workflowService.getArtifactVariableDefaultArtifact(
        ArtifactVariable.builder()
            .entityType(ENVIRONMENT)
            .entityId(ENV_ID)
            .name("art_env")
            .allowedList(Collections.singletonList(ARTIFACT_STREAM_ID))
            .build(),
        workflowExecution);
    assertThat(artifact).isNotNull();
    assertThat(artifact.getUuid()).isEqualTo("art2");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetArtifactVariableDefaultArtifactForWorkflow() {
    WorkflowExecution workflowExecution = prepareWorkflowExecutionWithArtifactVariables(false);
    when(artifactService.get("art3"))
        .thenReturn(anArtifact().withUuid("art3").withArtifactStreamId(ARTIFACT_STREAM_ID).build());
    Artifact artifact = workflowService.getArtifactVariableDefaultArtifact(
        ArtifactVariable.builder()
            .entityType(WORKFLOW)
            .entityId(WORKFLOW_ID)
            .name("art_wrk")
            .allowedList(Collections.singletonList(ARTIFACT_STREAM_ID))
            .build(),
        workflowExecution);
    assertThat(artifact).isNotNull();
    assertThat(artifact.getUuid()).isEqualTo("art3");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetArtifactVariableDefaultArtifactForInvalidArtifactVariable() {
    WorkflowExecution workflowExecution = prepareWorkflowExecutionWithArtifactVariables(false);
    Artifact artifact = workflowService.getArtifactVariableDefaultArtifact(
        ArtifactVariable.builder()
            .entityType(SERVICE)
            .entityId(SERVICE_ID)
            .name("art_random")
            .allowedList(Collections.singletonList(ARTIFACT_STREAM_ID))
            .build(),
        workflowExecution);
    assertThat(artifact).isNull();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetArtifactVariableDefaultArtifactForInvalidArtifactId() {
    WorkflowExecution workflowExecution = prepareWorkflowExecutionWithArtifactVariables(false);
    Artifact artifact = workflowService.getArtifactVariableDefaultArtifact(
        ArtifactVariable.builder()
            .entityType(SERVICE)
            .entityId(SERVICE_ID)
            .name("art_invalid")
            .allowedList(Collections.singletonList(ARTIFACT_STREAM_ID))
            .build(),
        workflowExecution);
    assertThat(artifact).isNull();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetArtifactVariableDefaultArtifactForEmptyArtifacts() {
    WorkflowExecution workflowExecution = prepareWorkflowExecutionWithArtifactVariables(true);
    Artifact artifact = workflowService.getArtifactVariableDefaultArtifact(
        ArtifactVariable.builder().entityType(SERVICE).entityId(SERVICE_ID).name("art_srv").build(), workflowExecution);
    assertThat(artifact).isNull();
  }

  private WorkflowExecution prepareWorkflowExecutionWithArtifactVariables(boolean emptyArtifacts) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifactVariables(asList(
        ArtifactVariable.builder().entityType(SERVICE).entityId(SERVICE_ID).name("art_srv").value("art1").build(),
        ArtifactVariable.builder().entityType(ENVIRONMENT).entityId(ENV_ID).name("art_env").value("art2").build(),
        ArtifactVariable.builder().entityType(WORKFLOW).entityId(WORKFLOW_ID).name("art_wrk").value("art3").build(),
        ArtifactVariable.builder().entityType(SERVICE).entityId(SERVICE_ID).name("art_invalid").build()));
    if (!emptyArtifacts) {
      executionArgs.setArtifacts(asList(anArtifact().withUuid("art1").build(), anArtifact().withUuid("art2").build(),
          anArtifact().withUuid("art3").build()));
    }
    return WorkflowExecution.builder().executionArgs(executionArgs).build();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetArtifactVariableDefaultArtifactWithoutVariables() {
    WorkflowExecution workflowExecution = prepareWorkflowExecutionWithoutArtifactVariables(false);
    when(artifactService.get("art1"))
        .thenReturn(anArtifact().withUuid("art1").withArtifactStreamId(ARTIFACT_STREAM_ID).build());
    Artifact artifact = workflowService.getArtifactVariableDefaultArtifact(
        ArtifactVariable.builder().allowedList(Collections.singletonList(ARTIFACT_STREAM_ID)).build(),
        workflowExecution);
    assertThat(artifact).isNotNull();
    assertThat(artifact.getUuid()).isEqualTo("art1");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetArtifactVariableDefaultArtifactNoArtifacts() {
    WorkflowExecution workflowExecution = prepareWorkflowExecutionWithoutArtifactVariables(true);
    when(artifactService.get("art1"))
        .thenReturn(anArtifact().withUuid("art1").withArtifactStreamId(ARTIFACT_STREAM_ID).build());
    Artifact artifact = workflowService.getArtifactVariableDefaultArtifact(
        ArtifactVariable.builder().allowedList(Collections.singletonList(ARTIFACT_STREAM_ID)).build(),
        workflowExecution);
    assertThat(artifact).isNull();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetArtifactVariableDefaultArtifactNoAllowedList() {
    WorkflowExecution workflowExecution = prepareWorkflowExecutionWithoutArtifactVariables(false);
    when(artifactService.get("art1"))
        .thenReturn(anArtifact().withUuid("art1").withArtifactStreamId(ARTIFACT_STREAM_ID).build());
    Artifact artifact = workflowService.getArtifactVariableDefaultArtifact(
        ArtifactVariable.builder().allowedList(Collections.emptyList()).build(), workflowExecution);
    assertThat(artifact).isNull();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetArtifactVariableDefaultArtifactInvalidArtifactStreamId() {
    WorkflowExecution workflowExecution = prepareWorkflowExecutionWithoutArtifactVariables(false);
    when(artifactService.get("art1")).thenReturn(anArtifact().withUuid("art1").withArtifactStreamId("random").build());
    Artifact artifact = workflowService.getArtifactVariableDefaultArtifact(
        ArtifactVariable.builder().allowedList(Collections.singletonList(ARTIFACT_STREAM_ID)).build(),
        workflowExecution);
    assertThat(artifact).isNull();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetArtifactVariableDefaultArtifactDeletedArtifact() {
    WorkflowExecution workflowExecution = prepareWorkflowExecutionWithoutArtifactVariables(false);
    when(artifactService.get("art1")).thenReturn(null);
    Artifact artifact = workflowService.getArtifactVariableDefaultArtifact(
        ArtifactVariable.builder().allowedList(Collections.singletonList(ARTIFACT_STREAM_ID)).build(),
        workflowExecution);
    assertThat(artifact).isNull();
  }

  private WorkflowExecution prepareWorkflowExecutionWithoutArtifactVariables(boolean empty) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    if (!empty) {
      executionArgs.setArtifacts(asList(anArtifact().withUuid("art1").withArtifactStreamId(ARTIFACT_STREAM_ID).build(),
          anArtifact().withUuid("art2").build()));
    }
    return WorkflowExecution.builder().executionArgs(executionArgs).build();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetDisplayInfoForService() {
    when(serviceResourceService.get(APP_ID, SERVICE_ID)).thenReturn(Service.builder().name(SERVICE_NAME).build());
    Map<String, List<String>> displayInfo = workflowService.getDisplayInfo(
        APP_ID, null, ArtifactVariable.builder().entityType(SERVICE).entityId(SERVICE_ID).build());
    assertThat(displayInfo).isNotNull();
    assertThat(displayInfo).containsKeys("services");
    assertThat(displayInfo.get("services")).contains(SERVICE_NAME);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetDisplayInfoForInvalidService() {
    when(serviceResourceService.get(APP_ID, SERVICE_ID)).thenReturn(null);
    Map<String, List<String>> displayInfo = workflowService.getDisplayInfo(
        APP_ID, null, ArtifactVariable.builder().entityType(SERVICE).entityId(SERVICE_ID).build());
    assertThat(displayInfo).isNotNull();
    assertThat(displayInfo).doesNotContainKeys("services");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetDisplayInfoForEnv() {
    String serviceId1 = "SERVICE_ID_1";
    String serviceId2 = "SERVICE_ID_2";
    String serviceName1 = "SVC_1";
    String serviceName2 = "SVC_2";
    when(environmentService.get(APP_ID, ENV_ID)).thenReturn(anEnvironment().name(ENV_NAME).build());
    when(serviceResourceService.get(APP_ID, serviceId1)).thenReturn(Service.builder().name(serviceName1).build());
    when(serviceResourceService.get(APP_ID, serviceId2)).thenReturn(Service.builder().name(serviceName2).build());
    Map<String, List<String>> displayInfo = workflowService.getDisplayInfo(APP_ID, null,
        ArtifactVariable.builder()
            .entityType(ENVIRONMENT)
            .entityId(ENV_ID)
            .overriddenArtifactVariables(
                asList(ArtifactVariable.builder().entityType(SERVICE).entityId(serviceId1).build(),
                    ArtifactVariable.builder().entityType(SERVICE).entityId(serviceId2).build()))
            .build());
    assertThat(displayInfo).isNotNull();
    assertThat(displayInfo).containsKeys("environments");
    assertThat(displayInfo.get("environments")).contains(ENV_NAME);
    assertThat(displayInfo).containsKeys("services");
    assertThat(displayInfo.get("services")).contains(serviceName1, serviceName2);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetDisplayInfoForInvalidEnv() {
    when(environmentService.get(APP_ID, ENV_ID)).thenReturn(null);
    Map<String, List<String>> displayInfo = workflowService.getDisplayInfo(
        APP_ID, null, ArtifactVariable.builder().entityType(ENVIRONMENT).entityId(ENV_ID).build());
    assertThat(displayInfo).isNotNull();
    assertThat(displayInfo).doesNotContainKeys("environments");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetDisplayInfoForWorkflow() {
    String serviceId1 = "SERVICE_ID_1";
    String serviceId2 = "SERVICE_ID_2";
    String serviceName1 = "SVC_1";
    String serviceName2 = "SVC_2";
    when(environmentService.get(APP_ID, ENV_ID)).thenReturn(anEnvironment().name(ENV_NAME).build());
    when(serviceResourceService.get(APP_ID, serviceId1)).thenReturn(Service.builder().name(serviceName1).build());
    when(serviceResourceService.get(APP_ID, serviceId2)).thenReturn(Service.builder().name(serviceName2).build());
    Map<String, List<String>> displayInfo =
        workflowService.getDisplayInfo(APP_ID, aWorkflow().name(WORKFLOW_NAME).build(),
            ArtifactVariable.builder()
                .entityType(WORKFLOW)
                .entityId(WORKFLOW_ID)
                .overriddenArtifactVariables(Collections.singletonList(
                    ArtifactVariable.builder()
                        .entityType(ENVIRONMENT)
                        .entityId(ENV_ID)
                        .overriddenArtifactVariables(
                            asList(ArtifactVariable.builder().entityType(SERVICE).entityId(serviceId1).build(),
                                ArtifactVariable.builder().entityType(SERVICE).entityId(serviceId2).build()))
                        .build()))
                .build());
    assertThat(displayInfo).isNotNull();
    assertThat(displayInfo).containsKeys("workflows");
    assertThat(displayInfo.get("workflows")).contains(WORKFLOW_NAME);
    assertThat(displayInfo).containsKeys("environments");
    assertThat(displayInfo.get("environments")).contains(ENV_NAME);
    assertThat(displayInfo).containsKeys("services");
    assertThat(displayInfo.get("services")).contains(serviceName1, serviceName2);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetDisplayInfoForInvalidWorkflow() {
    Map<String, List<String>> displayInfo = workflowService.getDisplayInfo(
        APP_ID, null, ArtifactVariable.builder().entityType(WORKFLOW).entityId(WORKFLOW_ID).build());
    assertThat(displayInfo).isNotNull();
    assertThat(displayInfo).doesNotContainKeys("workflows");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetDisplayInfoForInvalidEntityType() {
    Map<String, List<String>> displayInfo =
        workflowService.getDisplayInfo(APP_ID, null, ArtifactVariable.builder().entityType(PROVISIONER).build());
    assertThat(displayInfo).isNullOrEmpty();
  }

  private Workflow createLinkedWorkflow(TemplateType templateType) {
    GraphNode templateStep = null;

    if (templateType == TemplateType.HTTP) {
      templateStep = constructHttpTemplateStep();
    } else if (templateType == TemplateType.SHELL_SCRIPT) {
      templateStep = constructShellScriptTemplateStep();
    } else if (templateType == TemplateType.SSH) {
      templateStep = constructCommandTemplateStep();
    }
    final Template httpTemplate =
        Template.builder().name("Linked Template").templateObject(HttpTemplate.builder().build()).build();
    when(templateService.get(TEMPLATE_ID, LATEST_TAG)).thenReturn(httpTemplate);
    when(templateService.constructEntityFromTemplate(httpTemplate, EntityType.WORKFLOW)).thenReturn(templateStep);
    Workflow workflow = constructLinkedTemplate(templateStep);

    return workflowService.createWorkflow(workflow);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldFetchDefaultArtifactFromExecutionArgsIfArtifactVariableAbsent() {
    WorkflowExecution workflowExecution = prepareWorkflowExecutionWithoutArtifactVariables(false);
    workflowExecution.getExecutionArgs().setArtifactVariables(
        Collections.singletonList(ArtifactVariable.builder().name("name").build()));
    when(artifactService.get("art1"))
        .thenReturn(anArtifact().withUuid("art1").withArtifactStreamId(ARTIFACT_STREAM_ID).build());
    Artifact artifact = workflowService.getArtifactVariableDefaultArtifact(
        ArtifactVariable.builder().name("name2").allowedList(Collections.singletonList(ARTIFACT_STREAM_ID)).build(),
        workflowExecution);
    assertThat(artifact).isNotNull();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCloneWorkflowWithSameName() {
    try {
      Workflow workflow = workflowService.createWorkflow(constructCanaryWorkflowWithPhase());
      assertThat(workflow).isNotNull().hasFieldOrProperty("uuid");

      workflowService.cloneWorkflow(
          APP_ID, workflow, CloneMetadata.builder().workflow(aWorkflow().name(WORKFLOW_NAME).build()).build());
      fail("Expected an InvalidRequestException to be thrown");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message")).isEqualTo("Duplicate name WORKFLOW_NAME");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCreateWorkflowWithSameName() {
    try {
      Workflow workflow = workflowService.createWorkflow(constructCanaryWorkflowWithPhase());
      assertThat(workflow).isNotNull().hasFieldOrProperty("uuid");

      workflowService.createWorkflow(constructCanaryWorkflowWithPhase());
      fail("Expected an InvalidRequestException to be thrown");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message")).isEqualTo("Duplicate name WORKFLOW_NAME");
    }
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void categoriesForBuildWorkflow() throws IllegalArgumentException {
    Workflow workflow = workflowService.createWorkflow(constructBuildWorkflowWithPhase());
    String phaseId =
        ((CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).getWorkflowPhases().get(0).getUuid();
    assertThat(workflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);

    WorkflowCategorySteps workflowCategorySteps =
        workflowService.calculateCategorySteps(workflow, user.getUuid(), phaseId, COLLECT_ARTIFACT.name(), 0, false);
    assertThat(workflowCategorySteps.getCategories()).isNotEmpty();

    assertThat(workflowCategorySteps.getCategories())
        .extracting(
            WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName, WorkflowCategoryStepsMeta::getStepIds)
        .contains(
            tuple(ARTIFACT.name(), ARTIFACT.getDisplayName(), java.util.Arrays.asList(ARTIFACT_COLLECTION.name())));

    validateCommonCategories(workflowCategorySteps);

    assertThat(workflowCategorySteps.getCategories())
        .extracting(WorkflowCategoryStepsMeta::getId)
        .doesNotContain(WorkflowStepType.KUBERNETES.name(), AWS_SSH.name());
  }

  private void validateCommonCategories(WorkflowCategorySteps workflowCategorySteps) {
    validateCommonCategories(workflowCategorySteps, false, false, false);
  }

  private void validateCommonCategories(WorkflowCategorySteps workflowCategorySteps, boolean isK8sPhaseStep,
      boolean isHelmPhaseStep, boolean isRollback) {
    assertThat(workflowCategorySteps.getCategories())
        .extracting(
            WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName, WorkflowCategoryStepsMeta::getStepIds)
        .contains(tuple(ISSUE_TRACKING.name(), ISSUE_TRACKING.getDisplayName(),
            asList(JIRA_CREATE_UPDATE.name(), SERVICENOW_CREATE_UPDATE.name())));
    assertThat(workflowCategorySteps.getCategories())
        .extracting(
            WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName, WorkflowCategoryStepsMeta::getStepIds)
        .contains(tuple(NOTIFICATION.name(), NOTIFICATION.getDisplayName(), java.util.Arrays.asList(EMAIL.name())));
    assertThat(workflowCategorySteps.getCategories())
        .extracting(
            WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName, WorkflowCategoryStepsMeta::getStepIds)
        .contains(tuple(FLOW_CONTROL.name(), FLOW_CONTROL.getDisplayName(),
            asList(BARRIER.name(), RESOURCE_CONSTRAINT.name(), APPROVAL.name())));
    assertThat(workflowCategorySteps.getCategories())
        .extracting(
            WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName, WorkflowCategoryStepsMeta::getStepIds)
        .contains(tuple(
            CI_SYSTEM.name(), CI_SYSTEM.getDisplayName(), asList(StepType.JENKINS.name(), GCB.name(), BAMBOO.name())));
    if (isHelmPhaseStep) {
      assertThat(workflowCategorySteps.getCategories())
          .extracting(WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName,
              WorkflowCategoryStepsMeta::getStepIds)
          .contains(tuple(UTILITY.name(), UTILITY.getDisplayName(),
              asList(SHELL_SCRIPT.name(), HTTP.name(), TEMPLATIZED_SECRET_MANAGER.name())));
      assertThat(workflowCategorySteps.getCategories())
          .extracting(WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName,
              WorkflowCategoryStepsMeta::getStepIds)
          .contains(tuple(WorkflowStepType.INFRASTRUCTURE_PROVISIONER.name(),
              WorkflowStepType.INFRASTRUCTURE_PROVISIONER.getDisplayName(),
              java.util.Arrays.asList(TERRAFORM_APPLY.name(), TERRAGRUNT_PROVISION.name())));
    } else if (isK8sPhaseStep) {
      assertThat(workflowCategorySteps.getCategories())
          .extracting(WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName,
              WorkflowCategoryStepsMeta::getStepIds)
          .contains(tuple(UTILITY.name(), UTILITY.getDisplayName(),
              asList(SHELL_SCRIPT.name(), HTTP.name(), NEW_RELIC_DEPLOYMENT_MARKER.name(),
                  StepType.TEMPLATIZED_SECRET_MANAGER.name())));
      assertThat(workflowCategorySteps.getCategories())
          .extracting(WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName,
              WorkflowCategoryStepsMeta::getStepIds)
          .contains(tuple(WorkflowStepType.INFRASTRUCTURE_PROVISIONER.name(),
              WorkflowStepType.INFRASTRUCTURE_PROVISIONER.getDisplayName(),
              asList(TERRAFORM_APPLY.name(), StepType.TERRAFORM_DESTROY.name(), TERRAGRUNT_PROVISION.name(),
                  TERRAGRUNT_DESTROY.name())));
    } else {
      assertThat(workflowCategorySteps.getCategories())
          .extracting(WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName,
              WorkflowCategoryStepsMeta::getStepIds)
          .contains(tuple(UTILITY.name(), UTILITY.getDisplayName(),
              asList(SHELL_SCRIPT.name(), HTTP.name(), TEMPLATIZED_SECRET_MANAGER.name())));
      assertThat(workflowCategorySteps.getCategories())
          .extracting(WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName,
              WorkflowCategoryStepsMeta::getStepIds)
          .contains(tuple(WorkflowStepType.INFRASTRUCTURE_PROVISIONER.name(),
              WorkflowStepType.INFRASTRUCTURE_PROVISIONER.getDisplayName(),
              asList(CLOUD_FORMATION_CREATE_STACK.name(), CLOUD_FORMATION_DELETE_STACK.name(), TERRAFORM_APPLY.name(),
                  TERRAGRUNT_PROVISION.name())));
    }
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void categoriesForBasicWorkflow() throws IllegalArgumentException {
    Workflow workflow = workflowService.createWorkflow(constructBasicWorkflowWithInfraNodeDeployServicePhaseStep());
    String phaseId =
        ((CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).getWorkflowPhases().get(0).getUuid();
    assertThat(workflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);
    when(serviceResourceService.getServiceCommands(workflow.getAppId(), SERVICE_ID))
        .thenReturn(asList(ServiceCommand.Builder.aServiceCommand()
                               .withCommand(Command.Builder.aCommand().withName("Install").build())
                               .build(),
            ServiceCommand.Builder.aServiceCommand()
                .withCommand(Command.Builder.aCommand().withName("MyCommand").build())
                .build()));
    when(serviceResourceService.exist(any(), any())).thenReturn(true);
    WorkflowCategorySteps workflowCategorySteps =
        workflowService.calculateCategorySteps(workflow, user.getUuid(), phaseId, DEPLOY_SERVICE.name(), 0, false);
    assertThat(workflowCategorySteps.getCategories()).isNotEmpty();

    assertThat(workflowCategorySteps.getSteps().keySet()).contains("INSTALL", "MYCOMMAND");
    assertThat(workflowCategorySteps.getSteps().get("INSTALL").getName()).isEqualTo("Install");
    assertThat(workflowCategorySteps.getCategories())
        .extracting(
            WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName, WorkflowCategoryStepsMeta::getStepIds)
        .contains(tuple(SERVICE_COMMAND.name(), SERVICE_COMMAND.getDisplayName(), asList("INSTALL", "MYCOMMAND")));
    validateCommonCategories(workflowCategorySteps);
    assertThat(workflowCategorySteps.getCategories())
        .extracting(WorkflowCategoryStepsMeta::getId)
        .doesNotContain(ARTIFACT.name(), APM.name(), LOG.name());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void categoriesForEcsWorkflow() throws IllegalArgumentException {
    doReturn(constructEcsInfraDef()).when(infrastructureDefinitionService).get(APP_ID, INFRA_DEFINITION_ID);
    Workflow workflow = workflowService.createWorkflow(constructEcsWorkflow());
    String phaseId =
        ((CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).getWorkflowPhases().get(0).getUuid();
    assertThat(workflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);
    WorkflowCategorySteps workflowCategorySteps =
        workflowService.calculateCategorySteps(workflow, user.getUuid(), phaseId, CONTAINER_DEPLOY.name(), 0, false);
    assertThat(workflowCategorySteps.getCategories()).isNotEmpty();

    assertThat(workflowCategorySteps.getCategories())
        .extracting(
            WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName, WorkflowCategoryStepsMeta::getStepIds)
        .contains(tuple(WorkflowStepType.ECS.name(), WorkflowStepType.ECS.getDisplayName(),
            asList(ECS_RUN_TASK.name(), ECS_SERVICE_DEPLOY.name(), ECS_STEADY_STATE_CHECK.name())));

    validateCommonCategories(workflowCategorySteps);

    assertThat(workflowCategorySteps.getCategories())
        .extracting(WorkflowCategoryStepsMeta::getId)
        .doesNotContain(APM.name(), LOG.name(), ARTIFACT.name());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void categoriesForHelmWorkflow() throws IllegalArgumentException {
    Map<String, Object> properties = new HashMap<>();
    properties.put("helmReleaseNamePrefix", "defaultValue");
    Workflow workflow = constructHelmWorkflowWithProperties(properties);
    String phaseId =
        ((CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).getWorkflowPhases().get(0).getUuid();
    assertThat(workflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);

    WorkflowCategorySteps workflowCategorySteps =
        workflowService.calculateCategorySteps(workflow, user.getUuid(), phaseId, HELM_DEPLOY.name(), 0, false);
    assertThat(workflowCategorySteps.getCategories()).isNotEmpty();

    assertThat(workflowCategorySteps.getCategories())
        .extracting(
            WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName, WorkflowCategoryStepsMeta::getStepIds)
        .contains(tuple(WorkflowStepType.HELM.name(), WorkflowStepType.HELM.getDisplayName(),
            java.util.Arrays.asList(HELM_DEPLOY.name())));

    validateCommonCategories(workflowCategorySteps, false, true, false);

    assertThat(workflowCategorySteps.getCategories())
        .extracting(WorkflowCategoryStepsMeta::getId)
        .doesNotContain(APM.name(), LOG.name(), ARTIFACT.name(), WorkflowStepType.KUBERNETES.name());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void categoriesForK8SWorkflow() throws IllegalArgumentException {
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(KUBERNETES);
    Workflow workflow = workflowService.createWorkflow(constructK8SWorkflow());
    String phaseId =
        ((CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).getWorkflowPhases().get(0).getUuid();
    assertThat(workflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);
    when(serviceResourceService.exist(any(), any())).thenReturn(true);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(featureFlagService.isNotEnabled(FeatureName.RANCHER_SUPPORT, ACCOUNT_ID)).thenReturn(true);
    WorkflowCategorySteps workflowCategorySteps =
        workflowService.calculateCategorySteps(workflow, user.getUuid(), phaseId, K8S_PHASE_STEP.name(), 0, false);
    assertThat(workflowCategorySteps.getCategories()).isNotEmpty();

    assertThat(workflowCategorySteps.getCategories())
        .extracting(
            WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName, WorkflowCategoryStepsMeta::getStepIds)
        .contains(tuple(WorkflowStepType.KUBERNETES.name(), WorkflowStepType.KUBERNETES.getDisplayName(),
            asList(K8S_CANARY_DEPLOY.name(), K8S_DEPLOYMENT_ROLLING.name(), KUBERNETES_SWAP_SERVICE_SELECTORS.name(),
                K8S_TRAFFIC_SPLIT.name(), K8S_SCALE.name(), K8S_DELETE.name(), K8S_APPLY.name())));
    validateCommonCategories(workflowCategorySteps, true, false, false);

    assertThat(workflowCategorySteps.getCategories())
        .extracting(WorkflowCategoryStepsMeta::getId)
        .doesNotContain(ARTIFACT.name());
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void categoriesForRancherK8SWorkflow() throws IllegalArgumentException {
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(KUBERNETES);
    Workflow workflow = workflowService.createWorkflow(constructK8SWorkflow());
    String phaseId =
        ((CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).getWorkflowPhases().get(0).getUuid();
    assertThat(workflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);
    when(serviceResourceService.exist(any(), any())).thenReturn(true);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(featureFlagService.isNotEnabled(FeatureName.RANCHER_SUPPORT, ACCOUNT_ID)).thenReturn(false);
    WorkflowCategorySteps workflowCategorySteps =
        workflowService.calculateCategorySteps(workflow, user.getUuid(), phaseId, K8S_PHASE_STEP.name(), 0, false);
    assertThat(workflowCategorySteps.getCategories()).isNotEmpty();

    assertThat(workflowCategorySteps.getCategories())
        .extracting(
            WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName, WorkflowCategoryStepsMeta::getStepIds)
        .contains(tuple(WorkflowStepType.KUBERNETES.name(), WorkflowStepType.KUBERNETES.getDisplayName(),
            asList(K8S_CANARY_DEPLOY.name(), K8S_DEPLOYMENT_ROLLING.name(), KUBERNETES_SWAP_SERVICE_SELECTORS.name(),
                K8S_TRAFFIC_SPLIT.name(), K8S_SCALE.name(), K8S_DELETE.name(), K8S_APPLY.name(), RANCHER_RESOLVE.name(),
                RANCHER_K8S_DEPLOYMENT_ROLLING.name(), RANCHER_K8S_CANARY_DEPLOY.name(),
                RANCHER_KUBERNETES_SWAP_SERVICE_SELECTORS.name(), RANCHER_K8S_DELETE.name())));
    validateCommonCategories(workflowCategorySteps, true, false, false);

    assertThat(workflowCategorySteps.getCategories())
        .extracting(WorkflowCategoryStepsMeta::getId)
        .doesNotContain(ARTIFACT.name());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCategoriesForBuildWorkflow() throws IllegalArgumentException {
    Workflow workflow = workflowService.createWorkflow(constructBuildWorkflow());
    assertThat(workflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);
    String phaseId =
        ((CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).getWorkflowPhases().get(0).getUuid();

    WorkflowCategorySteps workflowCategorySteps =
        workflowService.calculateCategorySteps(workflow, user.getUuid(), phaseId, WRAP_UP.name(), 1, false);
    assertThat(workflowCategorySteps.getCategories()).isNotEmpty();
    assertThat(workflowCategorySteps.getCategories())
        .extracting(WorkflowCategoryStepsMeta::getName)
        .doesNotContain(WorkflowStepType.KUBERNETES.name());

    assertThat(workflowCategorySteps.getSteps().keySet())
        .doesNotContain(K8S_BLUE_GREEN_DEPLOY.name(), K8S_DEPLOYMENT_ROLLING.name(),
            KUBERNETES_SWAP_SERVICE_SELECTORS.name(), K8S_TRAFFIC_SPLIT.name(), K8S_SCALE.name(), K8S_DELETE.name(),
            K8S_APPLY.name());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldGetWorkflowWithAppId() {
    Workflow workflow = workflowService.createWorkflow(constructBuildWorkflow());

    Workflow workflowFetched = workflowService.getWorkflow(workflow.getAppId(), workflow.getUuid());
    assertThat(workflowFetched).isNotNull();
    assertThat(workflowFetched.getUuid()).isEqualTo(workflow.getUuid());
    assertThat(workflowFetched.getAppId()).isEqualTo(workflow.getAppId());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldGetPipelineWithAppIdNull() {
    Workflow workflow = new Workflow();
    workflow.setAppId(generateUuid());
    workflow.setUuid(generateUuid());

    Workflow workflowFetched = workflowService.getWorkflow(workflow.getAppId(), workflow.getUuid());
    assertThat(workflowFetched).isNull();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void categoriesForK8sBlueGreenWorkflow() throws IllegalArgumentException {
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(InfrastructureDefinition.builder()
                        .uuid(INFRA_DEFINITION_ID)
                        .deploymentType(KUBERNETES)
                        .cloudProviderType(CloudProviderType.GCP)
                        .infrastructure(GoogleKubernetesEngine.builder().build())
                        .build());
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(featureFlagService.isNotEnabled(FeatureName.RANCHER_SUPPORT, ACCOUNT_ID)).thenReturn(true);

    Workflow workflow = workflowService.createWorkflow(constructK8SBlueGreenWorkflow());
    String phaseId =
        ((CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).getWorkflowPhases().get(0).getUuid();
    assertThat(workflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);
    when(serviceResourceService.exist(any(), any())).thenReturn(true);
    WorkflowCategorySteps workflowCategorySteps =
        workflowService.calculateCategorySteps(workflow, user.getUuid(), phaseId, K8S_PHASE_STEP.name(), 0, false);
    assertThat(workflowCategorySteps.getCategories()).isNotEmpty();

    assertThat(workflowCategorySteps.getCategories())
        .extracting(
            WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName, WorkflowCategoryStepsMeta::getStepIds)
        .contains(tuple(WorkflowStepType.KUBERNETES.name(), WorkflowStepType.KUBERNETES.getDisplayName(),
            asList(K8S_BLUE_GREEN_DEPLOY.name(), KUBERNETES_SWAP_SERVICE_SELECTORS.name(), K8S_TRAFFIC_SPLIT.name(),
                K8S_SCALE.name(), K8S_DELETE.name(), K8S_APPLY.name())));
    validateCommonCategories(workflowCategorySteps, true, false, false);

    assertThat(workflowCategorySteps.getCategories())
        .extracting(WorkflowCategoryStepsMeta::getId)
        .doesNotContain(ARTIFACT.name());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void categoriesForAmiWorkflowRollbackSection() {
    Workflow workflow = workflowService.createWorkflow(constructAmiWorkflow());
    String phaseId =
        ((CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).getWorkflowPhases().get(0).getUuid();
    assertThat(workflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);

    WorkflowCategorySteps workflowCategorySteps = workflowService.calculateCategorySteps(
        workflow, user.getUuid(), phaseId, AMI_DEPLOY_AUTOSCALING_GROUP.name(), 0, true);
    assertThat(workflowCategorySteps.getCategories()).isNotEmpty();

    assertThat(workflowCategorySteps.getCategories())
        .extracting(
            WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName, WorkflowCategoryStepsMeta::getStepIds)
        .contains(tuple(AWS_AMI.name(), AWS_AMI.getDisplayName(),
            java.util.Arrays.asList(StepType.AWS_AMI_SERVICE_ROLLBACK.name())));

    validateCommonCategories(workflowCategorySteps, false, false, true);

    assertThat(workflowCategorySteps.getCategories())
        .extracting(WorkflowCategoryStepsMeta::getId)
        .doesNotContain(APM.name(), LOG.name(), ARTIFACT.name()); // TODO: should this contain APM and LOG
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void categoriesForAmiBGWorkflowRollbackSection() {
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID))
        .thenReturn(AwsAmiInfrastructureMapping.Builder.anAwsAmiInfrastructureMapping()
                        .withUuid(INFRA_MAPPING_ID)
                        .withServiceId(SERVICE_ID)
                        .withDeploymentType(AMI.name())
                        .withInfraMappingType(InfrastructureMappingType.AWS_AMI.name())
                        .withComputeProviderType(SettingVariableTypes.AWS.name())
                        .build());
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(InfrastructureDefinition.builder()
                        .uuid(INFRA_DEFINITION_ID)
                        .cloudProviderType(CloudProviderType.AWS)
                        .deploymentType(AMI)
                        .infrastructure(AwsAmiInfrastructure.builder().build())
                        .build());
    Workflow workflow = workflowService.createWorkflow(constructAmiBGWorkflow());
    String phaseId =
        ((CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).getWorkflowPhases().get(0).getUuid();
    assertThat(workflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);

    WorkflowCategorySteps workflowCategorySteps = workflowService.calculateCategorySteps(
        workflow, user.getUuid(), phaseId, AMI_SWITCH_AUTOSCALING_GROUP_ROUTES.name(), 0, true);
    assertThat(workflowCategorySteps.getCategories()).isNotEmpty();

    assertThat(workflowCategorySteps.getCategories())
        .extracting(
            WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName, WorkflowCategoryStepsMeta::getStepIds)
        .contains(tuple(AWS_AMI.name(), AWS_AMI.getDisplayName(),
            asList(StepType.AWS_AMI_SWITCH_ROUTES.name(), StepType.ASG_AMI_ALB_SHIFT_SWITCH_ROUTES.name(),
                StepType.AWS_AMI_ROLLBACK_SWITCH_ROUTES.name(),
                StepType.ASG_AMI_ROLLBACK_ALB_SHIFT_SWITCH_ROUTES.name())));

    validateCommonCategories(workflowCategorySteps, false, false, true);

    assertThat(workflowCategorySteps.getCategories())
        .extracting(WorkflowCategoryStepsMeta::getId)
        .doesNotContain(APM.name(), LOG.name(), ARTIFACT.name());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testAllStateTypesDefinedInStepTypes() {
    List<StateType> excludedStateTypes = asList(SUB_WORKFLOW, REPEAT, FORK, WAIT, PAUSE, ENV_STATE, PHASE, PHASE_STEP,
        AWS_LAMBDA_VERIFICATION, STAGING_ORIGINAL_EXECUTION, SCALYR, ENV_RESUME_STATE, ENV_LOOP_RESUME_STATE,
        APPROVAL_RESUME, ENV_LOOP_STATE, ARTIFACT_COLLECT_LOOP_STATE, COLLECT_REMAINING_INSTANCES, ENV_ROLLBACK_STATE);

    Set<String> stateTypes = new HashSet<>();
    for (StateType stateType : StateType.values()) {
      if (!excludedStateTypes.contains(stateType)) {
        stateTypes.add(stateType.getType());
      }
    }

    Set<String> stepTypes = new HashSet<>();
    for (StepType stepType : StepType.values()) {
      stepTypes.add(stepType.getType());
    }

    assertThat(stateTypes.size()).as(getMessage(stateTypes, stepTypes)).isEqualTo(stepTypes.size());
    assertThat(stateTypes).containsAll(stepTypes);
  }

  private String getMessage(Set<String> stateTypes, Set<String> stepTypes) {
    Set<String> stateTypesCopy = new HashSet<>(stateTypes);
    Set<String> stepTypesCopy = new HashSet<>(stepTypes);
    if (stateTypesCopy.size() > stepTypesCopy.size()) {
      stateTypesCopy.removeAll(stepTypesCopy);
      return "StateType(s): " + Joiner.on(", ").join(stateTypesCopy) + " should be added to StepType as well.";
    } else {
      stepTypesCopy.removeAll(stateTypesCopy);
      return "StepType(s): " + Joiner.on(", ").join(stepTypesCopy) + " should be added to StateType as well.";
    }
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testSelectNodesForAwsSshWorkflow() {
    mockAwsInfraDef(INFRA_DEFINITION_ID);
    Workflow workflow = workflowService.createWorkflow(constructBasicWorkflowWithInfraNodeDeployServicePhaseStep());
    String phaseId =
        ((CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).getWorkflowPhases().get(0).getUuid();
    assertThat(workflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);
    WorkflowCategorySteps workflowCategorySteps =
        workflowService.calculateCategorySteps(workflow, user.getUuid(), phaseId, INFRASTRUCTURE_NODE.name(), 0, false);
    assertThat(workflowCategorySteps.getCategories()).isNotEmpty();
    assertThat(workflowCategorySteps.getCategories())
        .extracting(
            WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName, WorkflowCategoryStepsMeta::getStepIds)
        .contains(
            tuple(AWS_SSH.name(), AWS_SSH.getDisplayName(), java.util.Arrays.asList(StepType.AWS_NODE_SELECT.name())));
    assertThat(workflowCategorySteps.getCategories())
        .extracting(WorkflowCategoryStepsMeta::getId)
        .doesNotContain(DC_SSH.name(), AZURE_NODE_SELECT.name());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testSelectNodesForAwsWinRmWorkflow() {
    mockAwsInfraDef(INFRA_DEFINITION_ID);
    Workflow workflow =
        workflowService.createWorkflow(constructBasicWorkflowWithInfraNodeDeployServicePhaseStepAndWinRmDeployment());
    String phaseId =
        ((CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).getWorkflowPhases().get(0).getUuid();
    assertThat(workflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);
    WorkflowCategorySteps workflowCategorySteps =
        workflowService.calculateCategorySteps(workflow, user.getUuid(), phaseId, INFRASTRUCTURE_NODE.name(), 0, false);
    assertThat(workflowCategorySteps.getCategories()).isNotEmpty();
    assertThat(workflowCategorySteps.getCategories())
        .extracting(
            WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName, WorkflowCategoryStepsMeta::getStepIds)
        .contains(
            tuple(AWS_SSH.name(), AWS_SSH.getDisplayName(), java.util.Arrays.asList(StepType.AWS_NODE_SELECT.name())));
    assertThat(workflowCategorySteps.getCategories())
        .extracting(WorkflowCategoryStepsMeta::getId)
        .doesNotContain(DC_SSH.name(), AZURE_NODE_SELECT.name());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testSelectNodesForAwsSshWorkflowWithInfraDefinition() {
    Set<String> favorites = new HashSet<>();
    favorites.add(StepType.AWS_NODE_SELECT.getType());
    LinkedList<String> recents = new LinkedList<>();
    recents.add(StepType.EMAIL.getType());
    when(personalizationService.fetch(any(), any(), any()))
        .thenReturn(Personalization.builder()
                        .steps(PersonalizationSteps.builder().favorites(favorites).recent(recents).build())
                        .build());
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(InfrastructureDefinition.builder()
                        .name("def1")
                        .infrastructure(AwsInstanceInfrastructure.builder().build())
                        .deploymentType(SSH)
                        .build());
    Workflow workflow = workflowService.createWorkflow(
        constructBasicWorkflowWithInfraNodeDeployServicePhaseStepWithInfraDefinitionId());
    String phaseId =
        ((CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).getWorkflowPhases().get(0).getUuid();
    assertThat(workflow).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);
    WorkflowCategorySteps workflowCategorySteps =
        workflowService.calculateCategorySteps(workflow, user.getUuid(), phaseId, INFRASTRUCTURE_NODE.name(), 0, false);
    assertThat(workflowCategorySteps.getCategories()).isNotEmpty();
    assertThat(workflowCategorySteps.getCategories())
        .extracting(
            WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName, WorkflowCategoryStepsMeta::getStepIds)
        .contains(tuple("RECENTLY_USED", "Recently Used", java.util.Arrays.asList(EMAIL.name())));
    assertThat(workflowCategorySteps.getCategories())
        .extracting(
            WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName, WorkflowCategoryStepsMeta::getStepIds)
        .contains(tuple("MY_FAVORITES", "My Favorites", java.util.Arrays.asList(StepType.AWS_NODE_SELECT.name())));
    assertThat(workflowCategorySteps.getCategories())
        .extracting(
            WorkflowCategoryStepsMeta::getId, WorkflowCategoryStepsMeta::getName, WorkflowCategoryStepsMeta::getStepIds)
        .contains(
            tuple(AWS_SSH.name(), AWS_SSH.getDisplayName(), java.util.Arrays.asList(StepType.AWS_NODE_SELECT.name())));
    assertThat(workflowCategorySteps.getCategories())
        .extracting(WorkflowCategoryStepsMeta::getId)
        .doesNotContain(DC_SSH.name(), AZURE_NODE_SELECT.name());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testUpdateConcurrencyStrategy() {
    Workflow workflow = workflowService.createWorkflow(constructCanaryWorkflowWithConcurrencyStrategy());
    ConcurrencyStrategy newConcurrencyStrategy = ConcurrencyStrategy.builder().unitType(UnitType.NONE).build();
    workflowService.updateConcurrencyStrategy(workflow.getAppId(), workflow.getUuid(), newConcurrencyStrategy);

    Workflow workflow1 = workflowService.readWorkflow(workflow.getAppId(), workflow.getUuid());
    OrchestrationWorkflow orchestrationWorkflow = workflow1.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow).isNotNull();
    assertThat(orchestrationWorkflow.getConcurrencyStrategy()).isNotNull();
    assertThat(orchestrationWorkflow.getConcurrencyStrategy().getUnitType()).isEqualTo(UnitType.NONE);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testLoadTrafficShiftMetadata() {
    Workflow workflow = workflowService.createWorkflow(constructWfWithTrafficShiftSteps());
    TrafficShiftMetadata trafficShiftMetadata =
        workflowService.readWorkflowTrafficShiftMetadata(workflow.getAppId(), workflow.getUuid());
    assertThat(trafficShiftMetadata).isNotNull();
    List<String> phaseIds = trafficShiftMetadata.getPhaseIdsWithTrafficShift();
    assertThat(phaseIds).isNotNull();
    assertThat(phaseIds.size()).isEqualTo(1);
    assertThat(phaseIds.get(0))
        .isEqualTo(
            ((CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).getWorkflowPhases().get(0).getUuid());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testK8sV2BGWorkflowHasRouteUpdateStepInRollbackPhase() {
    Workflow workflow = constructBlueGreenWorkflow();
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(constructGKInfraDef());
    when(infrastructureDefinitionService.getInfraDefById(any(), any())).thenReturn(constructGKInfraDef());

    K8sV2WorkflowFactory k8sV2WorkflowFactory = mock(K8sV2WorkflowFactory.class);
    when(abstractWorkflowFactory.getWorkflowCreatorFactory(AbstractWorkflowFactory.Category.K8S_V2))
        .thenReturn(k8sV2WorkflowFactory);
    when(k8sV2WorkflowFactory.getWorkflowCreator(any())).thenReturn(k8V2BlueGreenWorkflowCreator);
    when(serviceResourceService.get(APP_ID, SERVICE_ID, false))
        .thenReturn(Service.builder()
                        .name(SERVICE_NAME)
                        .uuid(SERVICE_ID)
                        .deploymentType(DeploymentType.KUBERNETES)
                        .isK8sV2(true)
                        .build());

    Workflow savedWorkflow = workflowService.createWorkflow(workflow);

    BlueGreenOrchestrationWorkflow orchestrationWorkflow =
        (BlueGreenOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    Set<Entry<String, WorkflowPhase>> entries = orchestrationWorkflow.getRollbackWorkflowPhaseIdMap().entrySet();

    Entry<String, WorkflowPhase> entry = (Entry<String, WorkflowPhase>) entries.toArray()[0];
    PhaseStep phaseStep = entry.getValue().getPhaseSteps().get(0);
    assertThat(phaseStep.getPhaseStepNameForRollback()).isEqualTo(WorkflowServiceHelper.ROUTE_UPDATE);
    assertThat(phaseStep.isRollback()).isTrue();
    phaseStep.getSteps().get(0).getName();
    assertThat(phaseStep.getSteps().get(0).getName()).isEqualTo(KUBERNETES_SWAP_SERVICES_PRIMARY_STAGE);
    assertThat(phaseStep.getSteps().get(0).isRollback()).isTrue();

    List<PhaseStep> phaseSteps = orchestrationWorkflow.getWorkflowPhases().get(0).getPhaseSteps();

    assertThat(phaseSteps.get(0).getSteps().get(0).getType()).isEqualTo(K8S_BLUE_GREEN_DEPLOY.name());
    assertThat(phaseSteps.get(0).getSteps().get(0).isRollback()).isFalse();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldUpdateArtifactVariablesWithArtifactStreamMetadata() {
    List<ArtifactVariable> artifactVariables = new ArrayList<>();
    artifactVariables.add(ArtifactVariable.builder().entityType(SERVICE).entityId(SERVICE_ID).name("art_srv").build());
    artifactVariables.add(ArtifactVariable.builder()
                              .entityType(SERVICE)
                              .entityId("SERVICE_ID_1")
                              .name("art_parameterized")
                              .allowedList(Collections.singletonList(ARTIFACT_STREAM_ID))
                              .build());
    ExecutionArgs executionArgs = new ExecutionArgs();
    Map<String, Object> map1 = new HashMap<>();
    map1.put("repo", "myrepo");
    map1.put("buildNo", "1.0");
    executionArgs.setArtifactVariables(asList(
        ArtifactVariable.builder().entityType(SERVICE).entityId(SERVICE_ID).name("art_srv").value("art1").build(),
        ArtifactVariable.builder().entityType(ENVIRONMENT).entityId(ENV_ID).name("art_env").value("art2").build(),
        ArtifactVariable.builder().entityType(WORKFLOW).entityId(WORKFLOW_ID).name("art_wrk").value("art3").build(),
        ArtifactVariable.builder()
            .entityType(SERVICE)
            .entityId("SERVICE_ID_1")
            .name("art_parameterized")
            .uiDisplayName("art_parameterized (requires values)")
            .artifactStreamMetadata(
                ArtifactStreamMetadata.builder().artifactStreamId(ARTIFACT_STREAM_ID).runtimeValues(map1).build())
            .build()));
    WorkflowExecution workflowExecution = WorkflowExecution.builder().executionArgs(executionArgs).build();
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("${repo}")
                                                  .groupId("mygroup")
                                                  .artifactPaths(java.util.Arrays.asList("todolist"))
                                                  .autoPopulate(false)
                                                  .serviceId(SERVICE_ID)
                                                  .name("art_parameterized")
                                                  .uuid(ARTIFACT_STREAM_ID)
                                                  .build();
    nexusArtifactStream.setArtifactStreamParameterized(true);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);
    when(artifactStreamService.getArtifactStreamParameters(ARTIFACT_STREAM_ID))
        .thenReturn(java.util.Arrays.asList("repo"));
    workflowService.resolveArtifactStreamMetadata(APP_ID, artifactVariables, workflowExecution);
    assertThat(artifactVariables.get(0).getArtifactStreamMetadata()).isNull();
    assertThat(artifactVariables.get(1).getArtifactStreamMetadata()).isNotNull();
    assertThat(artifactVariables.get(1).getUiDisplayName()).isEqualTo("art_parameterized (requires values)");
    assertThat(artifactVariables.get(1).getArtifactStreamMetadata().getRuntimeValues())
        .isNotEmpty()
        .contains(entry("repo", "myrepo"), entry("buildNo", "1.0"));
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void fetchDeploymentMetadataWhenArtifactStreamParametersUpdated() {
    List<ArtifactVariable> artifactVariables = new ArrayList<>();
    artifactVariables.add(ArtifactVariable.builder()
                              .entityType(SERVICE)
                              .entityId("SERVICE_ID_1")
                              .name("art_parameterized")
                              .allowedList(Collections.singletonList(ARTIFACT_STREAM_ID))
                              .build());
    ExecutionArgs executionArgs = new ExecutionArgs();
    Map<String, Object> map1 = new HashMap<>();
    map1.put("repo", "myrepo");
    map1.put("buildNo", "1.0");
    executionArgs.setArtifactVariables(java.util.Arrays.asList(
        ArtifactVariable.builder()
            .entityType(SERVICE)
            .entityId("SERVICE_ID_1")
            .name("art_parameterized")
            .uiDisplayName("art_parameterized (requires values)")
            .artifactStreamMetadata(
                ArtifactStreamMetadata.builder().artifactStreamId(ARTIFACT_STREAM_ID).runtimeValues(map1).build())
            .build()));
    WorkflowExecution workflowExecution = WorkflowExecution.builder().executionArgs(executionArgs).build();
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("${repo}")
                                                  .groupId("${group}")
                                                  .artifactPaths(java.util.Arrays.asList("todolist"))
                                                  .autoPopulate(false)
                                                  .serviceId(SERVICE_ID)
                                                  .name("art_parameterized")
                                                  .uuid(ARTIFACT_STREAM_ID)
                                                  .build();
    nexusArtifactStream.setArtifactStreamParameterized(true);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);
    when(artifactStreamService.getArtifactStreamParameters(ARTIFACT_STREAM_ID)).thenReturn(asList("repo", "group"));
    workflowService.resolveArtifactStreamMetadata(APP_ID, artifactVariables, workflowExecution);
    assertThat(artifactVariables.get(0).getArtifactStreamMetadata()).isNotNull();
    assertThat(artifactVariables.get(0).getUiDisplayName()).isEqualTo("art_parameterized (requires values)");
    assertThat(artifactVariables.get(0).getArtifactStreamMetadata().getRuntimeValues())
        .isNotEmpty()
        .contains(entry("repo", "myrepo"), entry("group", ""), entry("buildNo", "1.0"));
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void fetchDeploymentMetadataWhenArtifactStreamUpdatedToNonParameterized() {
    List<ArtifactVariable> artifactVariables = new ArrayList<>();
    artifactVariables.add(ArtifactVariable.builder()
                              .entityType(SERVICE)
                              .entityId("SERVICE_ID_1")
                              .name("art_parameterized")
                              .allowedList(Collections.singletonList(ARTIFACT_STREAM_ID))
                              .build());
    ExecutionArgs executionArgs = new ExecutionArgs();
    Map<String, Object> map1 = new HashMap<>();
    map1.put("repo", "myrepo");
    map1.put("buildNo", "1.0");
    executionArgs.setArtifactVariables(java.util.Arrays.asList(
        ArtifactVariable.builder()
            .entityType(SERVICE)
            .entityId("SERVICE_ID_1")
            .name("art_parameterized")
            .uiDisplayName("art_parameterized (requires values)")
            .artifactStreamMetadata(
                ArtifactStreamMetadata.builder().artifactStreamId(ARTIFACT_STREAM_ID).runtimeValues(map1).build())
            .build()));
    WorkflowExecution workflowExecution = WorkflowExecution.builder().executionArgs(executionArgs).build();
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("releases")
                                                  .groupId("mygroup")
                                                  .artifactPaths(java.util.Arrays.asList("todolist"))
                                                  .autoPopulate(false)
                                                  .serviceId(SERVICE_ID)
                                                  .name("art_parameterized")
                                                  .uuid(ARTIFACT_STREAM_ID)
                                                  .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);
    workflowService.resolveArtifactStreamMetadata(APP_ID, artifactVariables, workflowExecution);
    assertThat(artifactVariables.get(0).getArtifactStreamMetadata()).isNull();
  }

  private GraphNode prepareGraphNode(int idx) {
    return GraphNode.builder().id("id" + idx).build();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldCloneWorkflowPhaseWithSkipSTepStrategies() {
    Workflow workflow1 = createCanaryWorkflow();
    PhaseStep phaseStep =
        aPhaseStep(PRE_DEPLOYMENT)
            .addStep(prepareGraphNode(1))
            .addStep(prepareGraphNode(2))
            .withStepSkipStrategies(singletonList(new StepSkipStrategy(SPECIFIC_STEPS, asList("id1", "id2"), "true")))
            .build();
    WorkflowPhase workflowPhase = aWorkflowPhase()
                                      .infraMappingId(INFRA_MAPPING_ID)
                                      .serviceId(SERVICE_ID)
                                      .phaseSteps(singletonList(phaseStep))
                                      .build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase);

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();

    List<WorkflowPhase> workflowPhases2 =
        ((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getWorkflowPhases();
    WorkflowPhase workflowPhase2 = workflowPhases2.get(workflowPhases2.size() - 1);
    workflowPhase2.setName("phase 2-clone");

    workflowService.cloneWorkflowPhase(workflow2.getAppId(), workflow2.getUuid(), workflowPhase2);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    List<WorkflowPhase> workflowPhases3 =
        ((CanaryOrchestrationWorkflow) workflow3.getOrchestrationWorkflow()).getWorkflowPhases();
    WorkflowPhase clonedWorkflowPhase = workflowPhases3.get(workflowPhases3.size() - 1);
    assertThat(clonedWorkflowPhase).isNotNull();
    assertThat(clonedWorkflowPhase.getUuid()).isNotEqualTo(workflowPhase2.getUuid());
    assertThat(clonedWorkflowPhase.getName()).isEqualTo("phase 2-clone");
    assertThat(clonedWorkflowPhase)
        .isEqualToComparingOnlyGivenFields(workflowPhase2, "infraMappingId", "serviceId", "computeProviderId");
    assertThat(clonedWorkflowPhase.getPhaseSteps()).isNotNull().size().isEqualTo(workflowPhase2.getPhaseSteps().size());

    assertThat(clonedWorkflowPhase.getPhaseSteps().get(0).getStepSkipStrategies())
        .isNotNull()
        .size()
        .isEqualTo(workflowPhase2.getPhaseSteps().get(0).getStepSkipStrategies().size());
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void shouldFailOnNegativeWaitIntervalOnUpdatingWorkflow() {
    Workflow workflow = constructBasicWorkflowWithPhaseSteps();
    Workflow savedWorkflow = workflowService.createWorkflow(workflow);

    BasicOrchestrationWorkflow basicOrchestrationWorkflow =
        (BasicOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();

    Map<String, WorkflowPhase> workflowPhaseIdMap = basicOrchestrationWorkflow.getWorkflowPhaseIdMap();
    List<String> workflowPhaseIds = basicOrchestrationWorkflow.getWorkflowPhaseIds();
    assertThat(workflowPhaseIdMap).isNotEmpty().size().isGreaterThan(0);
    assertThat(workflowPhaseIds).isNotEmpty().size().isGreaterThan(0);
    WorkflowPhase workflowPhase = workflowPhaseIdMap.get(workflowPhaseIds.get(0));

    assertThat(workflowPhase.getPhaseSteps()).isNotEmpty().size().isGreaterThan(0);
    PhaseStep phaseStep = workflowPhase.getPhaseSteps().get(0);
    assertThat(phaseStep).isNotNull();

    phaseStep.setWaitInterval(-1);

    assertThatThrownBy(
        () -> workflowService.updateWorkflowPhase(savedWorkflow.getAppId(), savedWorkflow.getUuid(), workflowPhase))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGetArtifactVariableDefaultArtifactForParameterizedSource() {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifactVariables(
        asList(ArtifactVariable.builder()
                   .entityType(SERVICE)
                   .entityId(SERVICE_ID)
                   .name("art_srv")
                   .value("art_stream1")
                   .artifactStreamMetadata(ArtifactStreamMetadata.builder()
                                               .artifactStreamId(ARTIFACT_STREAM_ID)
                                               .runtimeValues(Collections.singletonMap("buildNo", "1"))
                                               .build())
                   .build()));
    executionArgs.setArtifacts(asList(anArtifact()
                                          .withUuid("art1")
                                          .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                          .withMetadata(new ArtifactMetadata(Collections.singletonMap("buildNo", "1")))
                                          .build(),
        anArtifact().withUuid("art2").build(), anArtifact().withUuid("art3").build()));
    WorkflowExecution workflowExecution = WorkflowExecution.builder().executionArgs(executionArgs).build();
    when(artifactService.get("art1"))
        .thenReturn(anArtifact().withUuid("art1").withArtifactStreamId(ARTIFACT_STREAM_ID).build());
    Artifact artifact = workflowService.getArtifactVariableDefaultArtifact(
        ArtifactVariable.builder()
            .entityType(SERVICE)
            .entityId(SERVICE_ID)
            .name("art_srv")
            .value("art1")
            .allowedList(Collections.singletonList(ARTIFACT_STREAM_ID))
            .artifactStreamMetadata(ArtifactStreamMetadata.builder()
                                        .artifactStreamId(ARTIFACT_STREAM_ID)
                                        .runtimeValues(Collections.singletonMap("buildNo", "1"))
                                        .build())
            .build(),
        workflowExecution);
    assertThat(artifact).isNotNull();
    assertThat(artifact.getUuid()).isEqualTo("art1");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testPruneByApplication() throws IllegalAccessException {
    WingsPersistence wingsPersistence = mock(WingsPersistence.class);
    FieldUtils.writeField(workflowService, "wingsPersistence", wingsPersistence, true);

    Query query = mock(Query.class);
    when(wingsPersistence.createQuery(Workflow.class)).thenReturn(query);
    when(wingsPersistence.createQuery(StateMachine.class)).thenReturn(query);
    when(wingsPersistence.delete(eq(Workflow.class), any(), any())).thenReturn(true);
    when(query.filter(any(), any())).thenReturn(query);
    when(query.project(any(), anyBoolean())).thenReturn(query);
    Workflow workflow = aWorkflow().uuid(UUID).accountId(ACCOUNT_ID).appId(APP_ID).build();
    when(query.asList()).thenReturn(Collections.singletonList(workflow));
    workflowService.pruneByApplication(APP_ID);
    verify(auditServiceHelper).reportDeleteForAuditing(APP_ID, workflow);
    verify(harnessTagService).pruneTagLinks(ACCOUNT_ID, UUID);
    verify(wingsPersistence).delete(eq(Workflow.class), any(), any());
    verify(wingsPersistence).delete(any(Query.class));
    verify(wingsPersistence).createQuery(StateMachine.class);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void shouldUpdateMultiServiceWorkflowPhaseWithoutServiceId() {
    // multi service workflow with phase
    Workflow workflow1 = createMultiServiceWorkflowWithPhase();

    // Remove the serviceId from phase (simulated like YAML) and templatize service
    WorkflowPhase templatizedWorkflowPhase =
        ((CanaryOrchestrationWorkflow) workflow1.getOrchestrationWorkflow()).getWorkflowPhases().get(0);
    templatizedWorkflowPhase.setServiceId(null);
    templatizedWorkflowPhase.setTemplateExpressions(singletonList(getServiceTemplateExpression()));
    workflowService.updateWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), templatizedWorkflowPhase);

    // Add a new phase
    WorkflowPhase workflowPhase = aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase);

    Workflow workflow2 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());
    assertThat(workflow2).isNotNull();

    List<WorkflowPhase> workflowPhases2 =
        ((CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow()).getWorkflowPhases();
    // verify attach
    assertThat(workflowPhases2.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldUpdateUserGroupsFromApprovalStep() {
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(DeploymentType.SSH);
    doNothing().when(userGroupService).addParentsReference(any(), any(), any(), any(), any());
    Workflow workflow1 = constructCanaryWithApprovalStep();

    Workflow workflow2 = workflowService.createWorkflow(workflow1);
    verify(userGroupService)
        .addParentsReference(USER_GROUP_ID, ACCOUNT_ID, APP_ID, workflow1.getUuid(), WORKFLOW.name());
    verify(userGroupService)
        .addParentsReference(USER_GROUP_ID + 2, ACCOUNT_ID, APP_ID, workflow1.getUuid(), WORKFLOW.name());

    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow();
    orchestrationWorkflow.getPreDeploymentSteps().getSteps().add(
        constructApprovalStep(asList(USER_GROUP_ID + 3, USER_GROUP_ID + 4)));
    Workflow workflow3 = workflowService.updateWorkflow(workflow2, false);
    verify(userGroupService)
        .addParentsReference(USER_GROUP_ID + 3, ACCOUNT_ID, APP_ID, workflow2.getUuid(), WORKFLOW.name());
    verify(userGroupService)
        .addParentsReference(USER_GROUP_ID + 4, ACCOUNT_ID, APP_ID, workflow2.getUuid(), WORKFLOW.name());

    CanaryOrchestrationWorkflow orchestrationWorkflow2 =
        (CanaryOrchestrationWorkflow) workflow3.getOrchestrationWorkflow();
    orchestrationWorkflow2.getPreDeploymentSteps().getSteps().remove(1);
    workflowService.updateWorkflow(workflow3, false);
    verify(userGroupService)
        .removeParentsReference(USER_GROUP_ID + 3, ACCOUNT_ID, APP_ID, workflow3.getUuid(), WORKFLOW.name());
    verify(userGroupService)
        .removeParentsReference(USER_GROUP_ID + 4, ACCOUNT_ID, APP_ID, workflow3.getUuid(), WORKFLOW.name());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldUpdateUserGroupsFromPhaseApprovalStep() {
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(DeploymentType.SSH);
    doNothing().when(userGroupService).addParentsReference(any(), any(), any(), any(), any());
    Workflow workflow1 = constructCanaryWithApprovalPhaseStep();

    Workflow workflow2 = workflowService.createWorkflow(workflow1);

    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow();
    orchestrationWorkflow.getPostDeploymentSteps().getSteps().add(
        constructApprovalStep(asList(USER_GROUP_ID, USER_GROUP_ID + 2)));
    workflowService.updateWorkflow(workflow2, false);
    verify(userGroupService)
        .addParentsReference(USER_GROUP_ID, ACCOUNT_ID, APP_ID, workflow1.getUuid(), WORKFLOW.name());
    verify(userGroupService)
        .addParentsReference(USER_GROUP_ID + 2, ACCOUNT_ID, APP_ID, workflow1.getUuid(), WORKFLOW.name());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldRemoveUserGroupsFromPhaseApprovalStep() {
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(DeploymentType.SSH);
    doNothing().when(userGroupService).addParentsReference(any(), any(), any(), any(), any());
    Workflow workflow1 = constructCanaryWithApprovalPhaseStep();

    Workflow workflow2 = workflowService.createWorkflow(workflow1);

    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow();
    orchestrationWorkflow.setWorkflowPhases(Collections.emptyList());
    workflowService.updateWorkflow(workflow2, false);
    verify(userGroupService)
        .addParentsReference(USER_GROUP_ID, ACCOUNT_ID, APP_ID, workflow1.getUuid(), WORKFLOW.name());
    verify(userGroupService)
        .addParentsReference(USER_GROUP_ID + 2, ACCOUNT_ID, APP_ID, workflow1.getUuid(), WORKFLOW.name());
    verify(userGroupService)
        .removeParentsReference(USER_GROUP_ID, ACCOUNT_ID, APP_ID, workflow2.getUuid(), WORKFLOW.name());
    verify(userGroupService)
        .removeParentsReference(USER_GROUP_ID + 2, ACCOUNT_ID, APP_ID, workflow2.getUuid(), WORKFLOW.name());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldRemoveUserGroupsWhenWorkflowDeleted() {
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(DeploymentType.SSH);
    doNothing().when(userGroupService).addParentsReference(any(), any(), any(), any(), any());
    Workflow workflow1 = constructCanaryWithApprovalPhaseStep();

    Workflow workflow2 = workflowService.createWorkflow(workflow1);
    Workflow clonedWorkflow = workflow2.cloneInternal();
    clonedWorkflow.setName(workflow2.getName() + 2);

    Workflow workflow3 = workflowService.cloneWorkflow(APP_ID, workflow2, clonedWorkflow);

    workflowService.deleteWorkflow(APP_ID, workflow3.getUuid());
    verify(userGroupService)
        .addParentsReference(USER_GROUP_ID, ACCOUNT_ID, APP_ID, workflow1.getUuid(), WORKFLOW.name());
    verify(userGroupService)
        .addParentsReference(USER_GROUP_ID + 2, ACCOUNT_ID, APP_ID, workflow1.getUuid(), WORKFLOW.name());
    verify(userGroupService)
        .addParentsReference(USER_GROUP_ID, ACCOUNT_ID, APP_ID, workflow3.getUuid(), WORKFLOW.name());
    verify(userGroupService)
        .addParentsReference(USER_GROUP_ID + 2, ACCOUNT_ID, APP_ID, workflow3.getUuid(), WORKFLOW.name());
    verify(userGroupService)
        .removeParentsReference(USER_GROUP_ID, ACCOUNT_ID, APP_ID, workflow3.getUuid(), WORKFLOW.name());
    verify(userGroupService)
        .removeParentsReference(USER_GROUP_ID + 2, ACCOUNT_ID, APP_ID, workflow3.getUuid(), WORKFLOW.name());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCreateBasicDeploymentWorkflowFromGit() {
    Workflow workflow = constructBasicWorkflowWithPhase();
    workflow.setSyncFromGit(true);

    Workflow workflow2 = workflowService.createWorkflow(workflow);
    assertThat(workflow2).isNotNull().hasFieldOrProperty("uuid").hasFieldOrPropertyWithValue("appId", APP_ID);

    BasicOrchestrationWorkflow orchestrationWorkflow =
        (BasicOrchestrationWorkflow) workflow2.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow.getFailureStrategies()).isEmpty();
    assertThat(orchestrationWorkflow.getNotificationRules()).isEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldAddArtifactInputToArtifactVariables() {
    ArtifactVariable artifactVariable1 =
        ArtifactVariable.builder()
            .artifactStreamSummaries(singletonList(
                ArtifactStreamSummary.builder()
                    .defaultArtifact(
                        ArtifactSummary.builder().buildNo(BUILD_NO).artifactStreamId(ARTIFACT_STREAM_ID).build())
                    .build()))
            .build();
    ArtifactVariable artifactVariable2 =
        ArtifactVariable.builder().name("docker").entityType(SERVICE).entityId(SERVICE_ID).build();

    ArtifactVariable wfExecutionArtifactVariable =
        ArtifactVariable.builder()
            .name("docker")
            .entityType(SERVICE)
            .entityId(SERVICE_ID)
            .artifactInput(
                ArtifactInput.builder().buildNo(BUILD_NO + "1").artifactStreamId(ARTIFACT_STREAM_ID + "1").build())
            .build();

    WorkflowExecution workflowExecution =
        WorkflowExecution.builder()
            .executionArgs(ExecutionArgs.builder().artifactVariables(asList(wfExecutionArtifactVariable)).build())
            .build();

    WorkflowServiceImpl workflowServiceImpl = (WorkflowServiceImpl) workflowService;
    workflowServiceImpl.addArtifactInputToArtifactVariables(
        asList(artifactVariable1, artifactVariable2), workflowExecution);
    assertThat(artifactVariable1.getArtifactInput()).isNotNull();
    assertThat(artifactVariable1.getArtifactInput())
        .isEqualTo(ArtifactInput.builder().buildNo(BUILD_NO).artifactStreamId(ARTIFACT_STREAM_ID).build());
    assertThat(artifactVariable2.getArtifactInput()).isNotNull();
    assertThat(artifactVariable2.getArtifactInput())
        .isEqualTo(ArtifactInput.builder().buildNo(BUILD_NO + "1").artifactStreamId(ARTIFACT_STREAM_ID + "1").build());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenResolvedTemplatizedInfraInvalid() {
    mockAwsInfraDef(INFRA_DEFINITION_ID);
    when(infrastructureDefinitionService.getInfraStructureDefinitionByUuids(
             APP_ID, java.util.Arrays.asList(INFRA_DEFINITION_ID)))
        .thenReturn(java.util.Arrays.asList(buildAwsSshInfraDef(INFRA_DEFINITION_ID)));

    Workflow workflow1 = createCanaryWorkflow();
    WorkflowPhase workflowPhase = aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).serviceId(SERVICE_ID).build();
    workflowPhase.setTemplateExpressions(asList(getServiceTemplateExpression(), prepareInfraDefTemplateExpression()));
    workflowService.createWorkflowPhase(workflow1.getAppId(), workflow1.getUuid(), workflowPhase);

    Workflow workflow3 = workflowService.readWorkflow(workflow1.getAppId(), workflow1.getUuid());

    when(serviceResourceService.fetchServicesByUuids(APP_ID, java.util.Arrays.asList(SERVICE_ID)))
        .thenReturn(java.util.Arrays.asList(service));

    assertThatThrownBy(()
                           -> workflowService.getResolvedInfraDefinitions(workflow3,
                               ImmutableMap.of("Service", SERVICE_ID, "InfraDef_SSH", INFRA_DEFINITION_ID), null))
        .isInstanceOf(GeneralException.class)
        .hasMessage("Infra definition INFRA_DEFINITION_ID is invalid");

    when(infrastructureDefinitionService.getInfraByName(workflow3.getAccountId(), INFRA_DEFINITION_ID, null))
        .thenReturn(InfrastructureDefinition.builder().uuid(INFRA_DEFINITION_ID).build());

    List<InfrastructureDefinition> resolvedInfraDefinitions = workflowService.getResolvedInfraDefinitions(
        workflow3, ImmutableMap.of("Service", SERVICE_ID, "InfraDef_SSH", INFRA_DEFINITION_ID), null);

    assertThat(resolvedInfraDefinitions)
        .isNotEmpty()
        .extracting(InfrastructureDefinition::getUuid)
        .contains(INFRA_DEFINITION_ID);
  }
}
