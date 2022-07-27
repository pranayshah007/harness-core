/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure;

import static io.harness.azure.model.AzureConstants.AZURE_WEBAPP_SLOT_SETUP_ACTIVITY_COMMAND_NAME;
import static io.harness.azure.model.AzureConstants.STEADY_STATE_TIMEOUT_REGEX;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.OrchestrationWorkflowType.BASIC;
import static io.harness.beans.OrchestrationWorkflowType.BLUE_GREEN;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.azure.AzureVMAuthType.SSH_PUBLIC_KEY;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.Log.Builder.aLog;
import static software.wings.beans.ServiceVariableType.ENCRYPTED_TEXT;
import static software.wings.service.impl.artifact.ArtifactServiceImpl.metadataOnlyBehindFlag;
import static software.wings.sm.InstanceStatusSummary.InstanceStatusSummaryBuilder.anInstanceStatusSummary;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.beans.EncryptedData;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.TriggeredBy;
import io.harness.context.ContextElementType;
import io.harness.data.encoding.EncodingUtils;
import io.harness.delegate.beans.azure.AzureConfigDTO;
import io.harness.delegate.beans.azure.AzureMachineImageArtifactDTO;
import io.harness.delegate.beans.azure.AzureVMAuthDTO;
import io.harness.delegate.beans.azure.AzureVMAuthType;
import io.harness.delegate.beans.azure.GalleryImageDefinitionDTO;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.response.AzureVMInstanceData;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.deployment.InstanceDetails;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.Misc;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.api.InstanceElement;
import software.wings.api.PhaseElement;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Application;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureVMSSInfrastructureMapping;
import software.wings.beans.AzureWebAppInfrastructureMapping;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Log;
import software.wings.beans.Service;
import software.wings.beans.ServiceVariable;
import software.wings.beans.SettingAttribute;
import software.wings.beans.VMSSAuthType;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitDetails;
import software.wings.beans.container.UserDataSpecification;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContext;
import software.wings.sm.InstanceStatusSummary;
import software.wings.sm.StateMachineExecutor;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.ManagerExecutionLogCallback;
import software.wings.sm.states.azure.appservices.AzureAppServiceStateData;
import software.wings.sm.states.azure.artifact.ArtifactConnectorMapper;
import software.wings.utils.ArtifactType;
import software.wings.utils.ServiceVersionConvention;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Ints;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class AzureVMSSStateHelper {
  public static final String VIRTUAL_MACHINE_SCALE_SET_ID_PATTERN =
      "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Compute/virtualMachineScaleSets/%s";
  protected static final List<ArtifactStreamType> METADATA_ONLY_ARTIFACT_STREAM_TYPES =
      Arrays.asList(ArtifactStreamType.JENKINS, ArtifactStreamType.BAMBOO, ArtifactStreamType.ARTIFACTORY,
          ArtifactStreamType.NEXUS, ArtifactStreamType.AMAZON_S3, ArtifactStreamType.AZURE_ARTIFACTS);

  @Inject private ServiceResourceService serviceResourceService;
  @Inject private ActivityService activityService;
  @Inject private AzureSweepingOutputServiceHelper azureSweepingOutputServiceHelper;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private SettingsService settingsService;
  @Inject private SecretManager secretManager;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private LogService logService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private StateMachineExecutor stateMachineExecutor;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private ArtifactService artifactService;
  @Inject private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  public boolean isBlueGreenWorkflow(ExecutionContext context) {
    return BLUE_GREEN == context.getOrchestrationWorkflowType();
  }

  public AzureMachineImageArtifactDTO getAzureMachineImageArtifactDTO(
      DeploymentExecutionContext context, String serviceId) {
    Artifact artifact = getArtifact(context, serviceId);
    ArtifactStream artifactStream = getArtifactStream(artifact.getArtifactStreamId());
    ArtifactStreamAttributes artifactStreamAttributes =
        artifactStream.fetchArtifactStreamAttributes(featureFlagService);

    String subscriptionId = artifactStreamAttributes.getSubscriptionId();
    String resourceGroupName = artifactStreamAttributes.getAzureResourceGroup();
    String osType = artifactStreamAttributes.getOsType();
    String imageType = artifactStreamAttributes.getImageType();
    String galleryName = artifactStreamAttributes.getAzureImageGalleryName();
    String imageDefinitionName = artifactStreamAttributes.getAzureImageDefinition();
    String imageVersion = artifact.getRevision();

    return AzureMachineImageArtifactDTO.builder()
        .imageOSType(AzureMachineImageArtifactDTO.OSType.valueOf(osType))
        .imageType(AzureMachineImageArtifactDTO.ImageType.valueOf(imageType))
        .imageDefinition(GalleryImageDefinitionDTO.builder()
                             .subscriptionId(subscriptionId)
                             .resourceGroupName(resourceGroupName)
                             .definitionName(imageDefinitionName)
                             .galleryName(galleryName)
                             .version(imageVersion)
                             .build())
        .build();
  }

  public Artifact getArtifact(DeploymentExecutionContext context, String serviceId) {
    return Optional.ofNullable(context.getDefaultArtifactForService(serviceId))
        .orElseThrow(
            () -> new InvalidRequestException(format("Unable to find artifact for service id: %s", serviceId)));
  }

  public ArtifactStream getArtifactStream(String artifactStreamId) {
    return Optional.ofNullable(artifactStreamService.get(artifactStreamId))
        .orElseThrow(()
                         -> new InvalidRequestException(
                             format("Unable to find artifact stream for artifact stream id: %s", artifactStreamId)));
  }

  public ManagerExecutionLogCallback getExecutionLogCallback(Activity activity) {
    String commandUnitName = activity.getCommandUnits().get(0).getName();
    Log.Builder logBuilder =
        aLog().appId(activity.getAppId()).activityId(activity.getUuid()).commandUnitName(commandUnitName);
    return new ManagerExecutionLogCallback(logService, logBuilder, activity.getUuid());
  }

  public Service getServiceByAppId(ExecutionContext context, String appId) {
    String serviceId = getServiceId(context);
    return serviceResourceService.getWithDetails(appId, serviceId);
  }

  public String getServiceId(ExecutionContext context) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    return phaseElement.getServiceElement().getUuid();
  }

  public Artifact getWebAppPackageArtifact(ExecutionContext context) {
    String serviceId = getServiceId(context);
    Artifact artifact = getArtifact((DeploymentExecutionContext) context, serviceId);

    log.info(format(
        "Found artifact for WebAppNC deployment, artifactId: %s, executionId: %s, appId: %s, workflowId: %s, serviceId: %s",
        artifact.getUuid(), context.getWorkflowExecutionId(), context.getAppId(), context.getWorkflowId(), serviceId));
    return artifact;
  }

  public Artifact getWebAppPackageArtifactForRollbackExceptionally(ExecutionContext context) {
    String serviceId = getServiceId(context);
    return getWebAppPackageArtifactForRollback(context, serviceId)
        .orElseThrow(
            () -> new InvalidArgumentsException(format("Not found artifact for rollback, serviceId: %s", serviceId)));
  }

  public Optional<Artifact> getWebAppPackageArtifactForRollback(ExecutionContext context) {
    return getWebAppPackageArtifactForRollback(context, getServiceId(context));
  }

  @VisibleForTesting
  Optional<Artifact> getWebAppPackageArtifactForRollback(ExecutionContext context, final String serviceId) {
    if (workflowExecutionService.checkIfOnDemand(context.getAppId(), context.getWorkflowExecutionId())) {
      return serviceResourceService.findArtifactForOnDemandWorkflow(
          context.getAppId(), context.getWorkflowExecutionId());
    }

    Optional<Activity> rollbackActivity = getWebAppPackageRollbackActivity(context, serviceId);
    return rollbackActivity.map(activity -> artifactService.getWithSource(activity.getArtifactId()));
  }

  // One workflow tied with specific service might have more workflow executions. One workflow execution has more
  // activities. Artifact is deployed on stage slot in AZURE_WEBAPP_SLOT_SETUP step activity.
  // - get success workflow execution by app id, workflow id and service id,
  // - get success workflow execution activities
  // - get AZURE_WEBAPP_SLOT_SETUP step activity
  public Optional<Activity> getWebAppPackageRollbackActivity(ExecutionContext context, final String serviceId) {
    String appId = context.getAppId();
    String workflowId = context.getWorkflowId();
    int executionsToSkip = context.getOrchestrationWorkflowType().equals(BASIC) ? 0 : 1;
    int executionsToIncludeInResponse = 1;
    // get success workflow execution by app id, workflow id and service id
    List<WorkflowExecution> successWorkflowExecutions = workflowExecutionService.getLatestSuccessWorkflowExecutions(
        appId, workflowId, singletonList(serviceId), executionsToSkip, executionsToIncludeInResponse);

    if (successWorkflowExecutions.isEmpty()) {
      return Optional.empty();
    }

    WorkflowExecution successWorkflowExecution = successWorkflowExecutions.get(0);
    log.info(format(
        "Found success workflow execution for WebAppNC rollback, executionId: %s, appId: %s, workflowId: %s, serviceId: %s",
        successWorkflowExecution.getUuid(), appId, workflowId, serviceId));

    // get success workflow execution activities
    List<Activity> successWorkflowExecutionActivities = activityService.listWorkflowExecutionActivitiesArtifactIdExists(
        appId, serviceId, workflowId, successWorkflowExecution.getUuid());

    if (successWorkflowExecutionActivities.isEmpty()) {
      return Optional.empty();
    }

    // get AZURE_WEBAPP_SLOT_SETUP step activity
    return successWorkflowExecutionActivities.stream()
        .filter(activity
            -> activity.getCommandName().equals(AZURE_WEBAPP_SLOT_SETUP_ACTIVITY_COMMAND_NAME)
                && activity.getStatus().equals(SUCCESS))
        .findFirst();
  }

  public boolean isWebAppDockerDeployment(ExecutionContext context) {
    Service service = getServiceByAppId(context, context.getAppId());
    ArtifactType artifactType = service.getArtifactType();
    return ArtifactType.DOCKER.equals(artifactType);
  }

  public ExecutionContext getExecutionContext(
      String appId, String workflowExecutionId, String stateExecutionInstanceId) {
    return stateMachineExecutor.getExecutionContext(appId, workflowExecutionId, stateExecutionInstanceId);
  }

  public Application getApplication(ExecutionContext context) {
    return Optional.of(getWorkflowStandardParams(context))
        .map(standardParams -> this.workflowStandardParamsExtensionService.getApp(standardParams))
        .orElseThrow(
            ()
                -> new InvalidRequestException(
                    format("Application can't be null or empty, accountId: %s", context.getAccountId()), USER));
  }

  public Environment getEnvironment(ExecutionContext context) {
    return Optional.of(getWorkflowStandardParams(context))
        .map(standardParams -> this.workflowStandardParamsExtensionService.getEnv(standardParams))
        .orElseThrow(()
                         -> new InvalidRequestException(
                             format("Env can't be null or empty, accountId: %s", context.getAccountId()), USER));
  }

  @NotNull
  public WorkflowStandardParams getWorkflowStandardParams(ExecutionContext context) {
    return Optional.ofNullable((WorkflowStandardParams) context.getContextElement(ContextElementType.STANDARD))
        .orElseThrow(
            ()
                -> new InvalidRequestException(
                    format("WorkflowStandardParams can't be null or empty, accountId: %s", context.getAccountId()),
                    USER));
  }

  public Activity createAndSaveActivity(ExecutionContext context, Artifact artifact, String commandName,
      String commandType, CommandUnitDetails.CommandUnitType commandUnitType, List<CommandUnit> commandUnits) {
    WorkflowStandardParams workflowStandardParams = getWorkflowStandardParams(context);
    Application app = getApplication(context);
    Environment env = getEnvironment(context);
    Service service = getServiceByAppId(context, app.getUuid());

    ActivityBuilder activityBuilder = Activity.builder()
                                          .applicationName(app.getName())
                                          .appId(app.getAppId())
                                          .environmentId(env.getUuid())
                                          .environmentName(env.getName())
                                          .environmentType(env.getEnvironmentType())
                                          .serviceId(service.getUuid())
                                          .serviceName(service.getName())
                                          .commandName(commandName)
                                          .commandType(commandType)
                                          .type(Activity.Type.Command)
                                          .workflowExecutionId(context.getWorkflowExecutionId())
                                          .workflowId(context.getWorkflowId())
                                          .workflowType(context.getWorkflowType())
                                          .workflowExecutionName(context.getWorkflowExecutionName())
                                          .stateExecutionInstanceId(context.getStateExecutionInstanceId())
                                          .stateExecutionInstanceName(context.getStateExecutionInstanceName())
                                          .commandUnitType(commandUnitType)
                                          .commandUnits(commandUnits)
                                          .status(ExecutionStatus.RUNNING)
                                          .triggeredBy(TriggeredBy.builder()
                                                           .email(workflowStandardParams.getCurrentUser().getEmail())
                                                           .name(workflowStandardParams.getCurrentUser().getName())
                                                           .build());

    if (artifact != null) {
      activityBuilder.artifactName(artifact.getDisplayName()).artifactId(artifact.getUuid());
      activityBuilder.artifactStreamName(artifact.getDisplayName()).artifactStreamId(artifact.getUuid());
    }

    return activityService.save(activityBuilder.build());
  }

  public Command getCommand(String appId, String serviceId, String envId, String commandName) {
    return serviceResourceService.getCommandByName(appId, serviceId, envId, commandName).getCommand();
  }

  public List<CommandUnit> getCommandUnitList(String appId, String serviceId, String envId, String commandName) {
    return serviceResourceService.getFlattenCommandUnitList(appId, serviceId, envId, commandName);
  }

  public void updateActivityStatus(String appId, String activityId, ExecutionStatus executionStatus) {
    activityService.updateStatus(activityId, appId, executionStatus);
  }

  public int renderTimeoutExpressionOrGetDefault(String timeout, ExecutionContext context, int defaultValue) {
    timeout = timeout.replaceAll(STEADY_STATE_TIMEOUT_REGEX, EMPTY);
    int value = renderExpressionOrGetDefault(timeout, context, defaultValue);
    return value <= 0 ? defaultValue : value;
  }

  public int renderExpressionOrGetDefault(String expr, ExecutionContext context, int defaultValue) {
    int retVal = defaultValue;
    if (isNotEmpty(expr)) {
      try {
        retVal = Integer.parseInt(context.renderExpression(expr));
      } catch (NumberFormatException e) {
        log.error(format("Number format Exception while evaluating: [%s]", expr), e);
        retVal = defaultValue;
      }
    }
    return retVal;
  }

  public double renderDoubleExpression(String expr, ExecutionContext context, double defaultValue) {
    double retVal = defaultValue;
    if (isNotEmpty(expr)) {
      try {
        retVal = Double.parseDouble(context.renderExpression(expr));
      } catch (NumberFormatException e) {
        log.error(format("Number format Exception while evaluating: [%s]", expr), e);
        retVal = defaultValue;
      }
    }
    return retVal;
  }

  public String getBase64EncodedUserData(ExecutionContext context, String appId, String serviceId) {
    return Optional.ofNullable(serviceResourceService.getUserDataSpecification(appId, serviceId))
        .map(UserDataSpecification::getData)
        .map(context::renderExpression)
        .map(EncodingUtils::encodeBase64)
        .orElse(null);
  }

  public String fixNamePrefix(ExecutionContext context, final String name, final String appName,
      final String serviceName, final String envName) {
    return isBlank(name) ? Misc.normalizeExpression(ServiceVersionConvention.getPrefix(appName, serviceName, envName))
                         : Misc.normalizeExpression(context.renderExpression(name));
  }

  public AzureVMSSInfrastructureMapping getAzureVMSSInfrastructureMapping(String infraMappingId, String appId) {
    notNullCheck("Infrastructure Mapping id is null or empty", infraMappingId);
    notNullCheck("Application id is null or empty", appId);
    InfrastructureMapping infrastructureMapping = infrastructureMappingService.get(appId, infraMappingId);
    if (!(infrastructureMapping instanceof AzureVMSSInfrastructureMapping)) {
      throw new InvalidRequestException(
          format("Infrastructure Mapping is not instance of AzureVMSSInfrastructureMapping, infrastructureMapping: %s",
              infrastructureMapping));
    }
    return (AzureVMSSInfrastructureMapping) infrastructureMapping;
  }

  public AzureWebAppInfrastructureMapping getAzureWebAppInfrastructureMapping(String infraMappingId, String appId) {
    notNullCheck("Infrastructure Mapping id is null or empty", infraMappingId);
    notNullCheck("Application id is null or empty", appId);
    InfrastructureMapping infrastructureMapping = infrastructureMappingService.get(appId, infraMappingId);
    if (!(infrastructureMapping instanceof AzureWebAppInfrastructureMapping)) {
      throw new InvalidRequestException(
          format("Infrastructure Mapping is not instance of AzureVMSSInfrastructureMapping, infrastructureMapping: %s",
              infrastructureMapping));
    }
    return (AzureWebAppInfrastructureMapping) infrastructureMapping;
  }

  public AzureConfig getAzureConfig(final String computeProviderSettingId) {
    SettingAttribute settingAttribute = settingsService.get(computeProviderSettingId);
    if (settingAttribute == null) {
      throw new InvalidRequestException(format("Cloud provider not found with id: %s", computeProviderSettingId));
    }

    return (AzureConfig) settingAttribute.getValue();
  }

  public Integer getStateTimeOutFromContext(ExecutionContext context, ContextElementType elementType) {
    AzureVMSSSetupContextElement azureVMSSSetupContextElement = context.getContextElement(elementType);
    return Optional.ofNullable(azureVMSSSetupContextElement)
        .map(AzureVMSSSetupContextElement::getAutoScalingSteadyStateVMSSTimeout)
        .filter(timeout -> timeout > 0)
        .map(timeout -> Ints.checkedCast(TimeUnit.MINUTES.toMillis(timeout.longValue())))
        .orElse(null);
  }

  public Integer getAzureVMSSStateTimeoutFromContext(ExecutionContext context) {
    AzureVMSSSetupContextElement azureVMSSSetupContextElement =
        context.getContextElement(ContextElementType.AZURE_VMSS_SETUP);
    return Optional.ofNullable(azureVMSSSetupContextElement)
        .map(AzureVMSSSetupContextElement::getAutoScalingSteadyStateVMSSTimeout)
        .filter(autoScalingSteadyStateVMSSTimeout -> autoScalingSteadyStateVMSSTimeout > 0)
        .map(autoScalingSteadyStateVMSSTimeout
            -> Ints.checkedCast(TimeUnit.MINUTES.toMillis(autoScalingSteadyStateVMSSTimeout.longValue())))
        .orElse(null);
  }

  public void setNewInstance(List<InstanceElement> newInstanceElements, boolean newInstance) {
    newInstanceElements.forEach(instanceElement -> instanceElement.setNewInstance(newInstance));
  }

  @NotNull
  public List<InstanceStatusSummary> getInstanceStatusSummaries(
      ExecutionStatus executionStatus, List<InstanceElement> newInstanceElements) {
    return newInstanceElements.stream()
        .map(instanceElement
            -> anInstanceStatusSummary().withInstanceElement(instanceElement).withStatus(executionStatus).build())
        .collect(toList());
  }

  public List<InstanceElement> generateInstanceElements(ExecutionContext context,
      AzureVMSSInfrastructureMapping azureVMSSInfrastructureMapping, List<AzureVMInstanceData> vmInstances) {
    return azureSweepingOutputServiceHelper.generateInstanceElements(
        context, azureVMSSInfrastructureMapping, vmInstances);
  }

  public void saveInstanceInfoToSweepingOutput(ExecutionContext context, List<InstanceElement> instanceElements) {
    if (isNotEmpty(instanceElements)) {
      // This sweeping element will be used by verification or other consumers.
      List<InstanceDetails> instanceDetails =
          azureSweepingOutputServiceHelper.generateAzureVMSSInstanceDetails(instanceElements);
      azureSweepingOutputServiceHelper.saveInstanceDetails(context, instanceElements, instanceDetails);
    }
  }

  public void saveAzureAppInfoToSweepingOutput(ExecutionContext context, List<InstanceElement> instanceElements,
      List<AzureAppDeploymentData> appDeploymentData) {
    if (isNotEmpty(appDeploymentData)) {
      // This sweeping element will be used by verification or other consumers.
      List<InstanceDetails> instanceDetails =
          azureSweepingOutputServiceHelper.generateAzureAppServiceInstanceDetails(appDeploymentData);
      azureSweepingOutputServiceHelper.saveInstanceDetails(context, instanceElements, instanceDetails);
    }
  }

  public ExecutionStatus getExecutionStatus(AzureVMSSTaskExecutionResponse executionResponse) {
    return executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS ? SUCCESS
                                                                                           : ExecutionStatus.FAILED;
  }

  public ExecutionStatus getAppServiceExecutionStatus(AzureTaskExecutionResponse executionResponse) {
    return executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS ? SUCCESS
                                                                                           : ExecutionStatus.FAILED;
  }

  public AzureVMSSStateData populateStateData(ExecutionContext context) {
    Application application = getApplication(context);
    Service service = getServiceByAppId(context, application.getUuid());
    String serviceId = getServiceId(context);
    Artifact artifact = getArtifact((DeploymentExecutionContext) context, service.getUuid());
    Environment environment = getEnvironment(context);
    AzureVMSSInfrastructureMapping azureVMSSInfrastructureMapping =
        getAzureVMSSInfrastructureMapping(context.fetchInfraMappingId(), application.getUuid());
    AzureConfig azureConfig = getAzureConfig(azureVMSSInfrastructureMapping.getComputeProviderSettingId());
    List<EncryptedDataDetail> encryptedDataDetails =
        getEncryptedDataDetails(context, azureVMSSInfrastructureMapping.getComputeProviderSettingId());

    return AzureVMSSStateData.builder()
        .application(application)
        .service(service)
        .serviceId(serviceId)
        .artifact(artifact)
        .environment(environment)
        .infrastructureMapping(azureVMSSInfrastructureMapping)
        .azureConfig(azureConfig)
        .azureEncryptedDataDetails(encryptedDataDetails)
        .build();
  }

  public AzureConfigDTO createAzureConfigDTO(AzureConfig azureConfig) {
    return AzureConfigDTO.builder()
        .clientId(azureConfig.getClientId())
        .key(SecretRefHelper.createSecretRef(azureConfig.getEncryptedKey(), Scope.ACCOUNT, null))
        .tenantId(azureConfig.getTenantId())
        .azureEnvironmentType(azureConfig.getAzureEnvironmentType())
        .build();
  }

  public AzureVMAuthDTO createVMAuthDTO(AzureVMSSInfrastructureMapping azureVMSSInfrastructureMapping) {
    VMSSAuthType vmssAuthType = azureVMSSInfrastructureMapping.getVmssAuthType();
    if (VMSSAuthType.PASSWORD != vmssAuthType && VMSSAuthType.SSH_PUBLIC_KEY != vmssAuthType) {
      throw new InvalidRequestException(
          format("Unsupported Azure VMSS Auth type, %s", azureVMSSInfrastructureMapping.getVmssAuthType()));
    }

    String passwordSecretTextName = azureVMSSInfrastructureMapping.getPasswordSecretTextName();
    String hostConnectionAttrs = azureVMSSInfrastructureMapping.getHostConnectionAttrs();
    String userName = azureVMSSInfrastructureMapping.getUserName();
    String secretRefIdentifier = vmssAuthType == VMSSAuthType.PASSWORD ? passwordSecretTextName : hostConnectionAttrs;

    return AzureVMAuthDTO.builder()
        .userName(userName)
        .azureVmAuthType(AzureVMAuthType.valueOf(vmssAuthType.name()))
        .secretRef(SecretRefHelper.createSecretRef(secretRefIdentifier, Scope.ACCOUNT, null))
        .build();
  }

  public List<EncryptedDataDetail> getVMAuthDTOEncryptionDetails(
      ExecutionContext context, AzureVMAuthDTO azureVmAuthDTO, String accountId, String envId) {
    AzureVMAuthType azureVmAuthType = azureVmAuthDTO.getAzureVmAuthType();
    String secretIdentifier = azureVmAuthDTO.getSecretRef().getIdentifier();

    return SSH_PUBLIC_KEY == azureVmAuthType
        ? getEncryptedDataDetailsBySettingsName(context, accountId, secretIdentifier)
        : getServiceVariableEncryptedDataDetails(context, secretIdentifier, envId);
  }

  public List<EncryptedDataDetail> getEncryptedDataDetailsBySettingsName(
      ExecutionContext context, final String accountId, final String settingsName) {
    SettingAttribute settingAttribute =
        Optional.ofNullable(settingsService.getSettingAttributeByName(accountId, settingsName))
            .orElseThrow(
                ()
                    -> new InvalidRequestException(
                        format("Unable to find setting by accountId: %s, settingsName: %s", accountId, settingsName)));
    return secretManager.getEncryptionDetails(
        (EncryptableSetting) settingAttribute.getValue(), context.getAppId(), context.getWorkflowExecutionId());
  }

  public List<EncryptedDataDetail> getEncryptedDataDetails(ExecutionContext context, final String settingId) {
    SettingAttribute settingAttribute =
        Optional.ofNullable(settingsService.get(settingId))
            .orElseThrow(
                () -> new InvalidRequestException(format("Unable to find setting by settingsId: %s", settingId)));
    return secretManager.getEncryptionDetails(
        (EncryptableSetting) settingAttribute.getValue(), context.getAppId(), context.getWorkflowExecutionId());
  }

  public List<EncryptedDataDetail> getEncryptedDataDetails(ExecutionContext context, EncryptableSetting setting) {
    return secretManager.getEncryptionDetails(setting, context.getAppId(), context.getWorkflowExecutionId());
  }

  private List<EncryptedDataDetail> getServiceVariableEncryptedDataDetails(
      ExecutionContext context, final String passwordSecretTextName, final String envId) {
    String appId = context.getAppId();
    ServiceVariable serviceVariable =
        buildEncryptedServiceVariable(context.getAccountId(), appId, envId, passwordSecretTextName);
    return secretManager.getEncryptionDetails(serviceVariable, appId, context.getWorkflowExecutionId());
  }

  private ServiceVariable buildEncryptedServiceVariable(
      final String accountId, final String appId, final String envId, final String secretTextName) {
    EncryptedData encryptedData = secretManager.getSecretMappedToAppByName(accountId, appId, envId, secretTextName);
    if (encryptedData == null) {
      throw new InvalidRequestException(format("No secret found with name: %s", secretTextName), USER);
    }
    return ServiceVariable.builder()
        .accountId(accountId)
        .type(ENCRYPTED_TEXT)
        .encryptedValue(encryptedData.getUuid())
        .secretTextName(secretTextName)
        .build();
  }

  public void updateEncryptedDataDetailSecretFieldName(
      AzureVMAuthDTO azureVmAuthDTO, List<EncryptedDataDetail> vmAuthDTOEncryptionDetails) {
    String secretRefFieldName = azureVmAuthDTO.getSecretRefFieldName();
    for (EncryptedDataDetail encryptedDataDetail : vmAuthDTOEncryptionDetails) {
      encryptedDataDetail.setFieldName(secretRefFieldName);
    }
  }

  public void updateEncryptedDataDetailSecretFieldName(
      List<EncryptedDataDetail> encryptedDataDetails, String secretFieldName) {
    for (EncryptedDataDetail encryptedDataDetail : encryptedDataDetails) {
      encryptedDataDetail.setFieldName(secretFieldName);
    }
  }

  public String getVMSSIdFromName(
      String subscriptionId, String resourceGroupName, String newVirtualMachineScaleSetName) {
    return isNotBlank(newVirtualMachineScaleSetName)
        ? format(VIRTUAL_MACHINE_SCALE_SET_ID_PATTERN, subscriptionId, resourceGroupName, newVirtualMachineScaleSetName)
        : EMPTY;
  }

  public AzureAppServiceStateData populateAzureAppServiceData(ExecutionContext context) {
    Application application = getApplication(context);
    Service service = getServiceByAppId(context, application.getUuid());
    Artifact artifact = getArtifact((DeploymentExecutionContext) context, service.getUuid());
    return populateAzureAppServiceData(context, artifact);
  }

  public AzureAppServiceStateData populateAzureAppServiceData(ExecutionContext context, Artifact artifact) {
    WorkflowStandardParams workflowStandardParams = getWorkflowStandardParams(context);
    Application application = getApplication(context);
    Service service = getServiceByAppId(context, application.getUuid());
    String serviceId = getServiceId(context);
    Environment environment = getEnvironment(context);
    AzureWebAppInfrastructureMapping infrastructureMapping =
        getAzureWebAppInfrastructureMapping(context.fetchInfraMappingId(), application.getUuid());
    AzureConfig azureConfig = getAzureConfig(infrastructureMapping.getComputeProviderSettingId());
    List<EncryptedDataDetail> encryptionDetails =
        getEncryptedDataDetails(context, infrastructureMapping.getComputeProviderSettingId());

    return AzureAppServiceStateData.builder()
        .artifact(artifact)
        .application(application)
        .service(service)
        .serviceId(serviceId)
        .environment(environment)
        .azureConfig(azureConfig)
        .resourceGroup(context.renderExpression(infrastructureMapping.getResourceGroup()))
        .subscriptionId(context.renderExpression(infrastructureMapping.getSubscriptionId()))
        .infrastructureMapping(infrastructureMapping)
        .azureEncryptedDataDetails(encryptionDetails)
        .currentUser(workflowStandardParams.getCurrentUser())
        .build();
  }

  public ArtifactConnectorMapper getConnectorMapper(ExecutionContext context, Artifact artifact) {
    String artifactStreamId = artifact.getArtifactStreamId();
    ArtifactStream artifactStream = getArtifactStream(artifactStreamId);
    ArtifactStreamAttributes artifactStreamAttributes =
        artifactStream.fetchArtifactStreamAttributes(featureFlagService);
    Service service = getServiceByAppId(context, context.getAppId());

    artifactStreamAttributes.setMetadata(artifact.getMetadata());
    artifactStreamAttributes.setArtifactName(artifact.getDisplayName());
    artifactStreamAttributes.setArtifactStreamId(artifactStream.getUuid());
    artifactStreamAttributes.setServerSetting(settingsService.get(artifactStream.getSettingId()));
    artifactStreamAttributes.setMetadataOnly(onlyMetaForArtifactType(artifactStream));
    artifactStreamAttributes.setArtifactStreamType(artifactStream.getArtifactStreamType());
    artifactStreamAttributes.setArtifactType(service.getArtifactType());
    return ArtifactConnectorMapper.getArtifactConnectorMapper(artifact, artifactStreamAttributes);
  }

  private boolean onlyMetaForArtifactType(ArtifactStream artifactStream) {
    return METADATA_ONLY_ARTIFACT_STREAM_TYPES.stream().anyMatch(
               streamType -> streamType.name().equals(artifactStream.getArtifactStreamType()))
        && metadataOnlyBehindFlag(featureFlagService, artifactStream.getAccountId(), artifactStream.isMetadataOnly());
  }

  public void validateAppSettings(List<AzureAppServiceApplicationSetting> appSettings) {
    if (isEmpty(appSettings)) {
      return;
    }
    List<String> appSettingNames =
        appSettings.stream().map(AzureAppServiceApplicationSetting::getName).collect(Collectors.toList());
    Set<String> duplicateAppSettingNames = getDuplicateItems(appSettingNames);

    if (isNotEmpty(duplicateAppSettingNames)) {
      String duplicateAppSettingNamesStr = String.join(",", duplicateAppSettingNames);
      throw new InvalidRequestException(format("Duplicate application string names [%s]", duplicateAppSettingNamesStr));
    }

    appSettings.forEach(appSetting -> {
      String name = appSetting.getName();
      if (isBlank(name)) {
        throw new InvalidRequestException("Application setting name cannot be empty or null");
      }
      if (isBlank(appSetting.getValue())) {
        throw new InvalidRequestException(format("Application setting value cannot be empty or null for [%s]", name));
      }
    });
  }

  public void validateConnStrings(List<AzureAppServiceConnectionString> connStrings) {
    if (isEmpty(connStrings)) {
      return;
    }
    List<String> connStringNames =
        connStrings.stream().map(AzureAppServiceConnectionString::getName).collect(Collectors.toList());
    Set<String> duplicateConnStringNames = getDuplicateItems(connStringNames);

    if (isNotEmpty(duplicateConnStringNames)) {
      String duplicateConnStringNamesStr = String.join(",", duplicateConnStringNames);
      throw new InvalidRequestException(format("Duplicate connection string names [%s]", duplicateConnStringNamesStr));
    }

    connStrings.forEach(connString -> {
      String name = connString.getName();
      if (isBlank(name)) {
        throw new InvalidRequestException("Connection string name cannot be empty or null");
      }

      if (isBlank(connString.getValue())) {
        throw new InvalidRequestException(format("Connection string value cannot be empty or null for [%s]", name));
      }

      if (connString.getType() == null) {
        throw new InvalidRequestException("Connection string type cannot be null");
      }
    });
  }

  @NotNull
  private <T> Set<T> getDuplicateItems(List<T> listItems) {
    Set<T> tempItemsSet = new HashSet<>();
    return listItems.stream().filter(item -> !tempItemsSet.add(item)).collect(Collectors.toSet());
  }

  public Optional<UserDataSpecification> getUserDataSpecification(ExecutionContext context) {
    String appId = context.getAppId();
    Service service = getServiceByAppId(context, appId);
    return Optional.ofNullable(serviceResourceService.getUserDataSpecification(appId, service.getUuid()));
  }
}
