/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.ARMScopeType.MANAGEMENT_GROUP;
import static io.harness.azure.model.ARMScopeType.RESOURCE_GROUP;
import static io.harness.azure.model.ARMScopeType.SUBSCRIPTION;
import static io.harness.azure.model.ARMScopeType.TENANT;
import static io.harness.azure.model.AzureConstants.FETCH_RESOURCE_GROUP_TEMPLATE;
import static io.harness.cdng.provision.azure.AzureCommonHelper.AZURE_TEMPLATE_TYPE;
import static io.harness.cdng.provision.azure.AzureCommonHelper.DEFAULT_TIMEOUT;
import static io.harness.cdng.provision.azure.AzureCommonHelper.PARAMETERS_FILE_IDENTIFIER;
import static io.harness.cdng.provision.azure.AzureCommonHelper.TEMPLATE_FILE_IDENTIFIER;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.azure.arm.AzureARMTaskNGParameters.AzureARMTaskNGParametersBuilder;
import static io.harness.delegate.task.azure.arm.AzureARMTaskType.ARM_DEPLOYMENT;
import static io.harness.delegate.task.azure.arm.AzureARMTaskType.FETCH_ARM_PRE_DEPLOYMENT_DATA;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.ARMScopeType;
import io.harness.azure.model.AzureConstants;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.azure.webapp.AzureWebAppStepHelper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.provision.ProvisionerOutputHelper;
import io.harness.cdng.provision.azure.beans.AzureARMConfig;
import io.harness.cdng.provision.azure.beans.AzureCreateARMResourcePassThroughData;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.ParameterFieldHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.azure.appservice.settings.AppSettingsFile;
import io.harness.delegate.task.azure.arm.AzureARMPreDeploymentData;
import io.harness.delegate.task.azure.arm.AzureARMTaskNGParameters;
import io.harness.delegate.task.azure.arm.AzureARMTaskNGResponse;
import io.harness.delegate.task.azure.arm.AzureFetchArmPreDeploymentDataTaskParameters;
import io.harness.delegate.task.azure.arm.AzureFetchArmPreDeploymentDataTaskResponse;
import io.harness.delegate.task.azure.arm.AzureResourceCreationTaskNGParameters;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.git.model.FetchFilesResult;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class AzureCreateARMResourceStep extends TaskChainExecutableWithRollbackAndRbac {
  private static final String AZURE_TEMPLATE_SELECTOR = "Azure ARM Template File";
  private static final String AZURE_PARAMETER_SELECTOR = "Azure ARM Parameter File";
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AZURE_CREATE_ARM_RESOURCE.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private AzureWebAppStepHelper azureWebAppStepHelper;
  @Inject private StepHelper stepHelper;
  @Inject private AzureCommonHelper azureCommonHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private AzureARMConfigDAL azureARMConfigDAL;
  @Inject private ProvisionerOutputHelper provisionerOutputHelper;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    List<EntityDetail> entityDetailList = new ArrayList<>();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    AzureCreateARMResourceStepParameters azureCreateARMResourceStepParameters =
        (AzureCreateARMResourceStepParameters) stepParameters.getSpec();
    if (isEmpty(getParameterFieldValue(azureCreateARMResourceStepParameters.getProvisionerIdentifier()))) {
      throw new InvalidRequestException("Provisioner Identifier can't be null or empty");
    }
    // Template file connector
    AzureCreateARMResourceStepConfigurationParameters spec =
        azureCreateARMResourceStepParameters.getConfigurationParameters();

    AzureTemplateFile azureTemplateFile = spec.getTemplateFile();

    if (ManifestStoreType.isInGitSubset(azureTemplateFile.getStore().getSpec().getKind())) {
      String connectorRef = getParameterFieldValue(azureTemplateFile.getStore().getSpec().getConnectorReference());
      IdentifierRef identifierRef =
          IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
      EntityDetail entityDetail = EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(identifierRef).build();
      entityDetailList.add(entityDetail);
    } else if (ManifestStoreType.HARNESS.equals(azureTemplateFile.getStore().getSpec().getKind())) {
      HarnessStore harnessStore = (HarnessStore) azureTemplateFile.getStore().getSpec();

      if (ParameterField.isNull(harnessStore.getFiles())) {
        if (ParameterField.isNull(harnessStore.getSecretFiles())
            || harnessStore.getSecretFiles().getValue().size() != 1) {
          throw new InvalidArgumentsException(
              "The Harness store configuration should be pointing to a single template file");
        }
      } else if (harnessStore.getFiles().getValue().size() != 1) {
        throw new InvalidArgumentsException(
            "The Harness store configuration should be pointing to a single template file");
      }
    }

    if (spec.getParameters() != null
        && ManifestStoreType.isInGitSubset(spec.getParameters().getStore().getSpec().getKind())) {
      String connectorRef = getParameterFieldValue(spec.getParameters().getStore().getSpec().getConnectorReference());
      IdentifierRef identifierRef =
          IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
      EntityDetail entityDetail = EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(identifierRef).build();
      entityDetailList.add(entityDetail);
    }

    // Azure connector
    String connectorRef = spec.getConnectorRef().getValue();
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
    EntityDetail entityDetail = EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(identifierRef).build();
    entityDetailList.add(entityDetail);

    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetailList, true);
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    AzureCreateARMResourceStepConfigurationParameters stepConfigurationParameters =
        ((AzureCreateARMResourceStepParameters) stepParameters.getSpec()).getConfigurationParameters();
    ConnectorInfoDTO connectorDTO =
        cdStepHelper.getConnector(stepConfigurationParameters.getConnectorRef().getValue(), ambiance);
    if (!(connectorDTO.getConnectorConfig() instanceof AzureConnectorDTO)) {
      throw new InvalidRequestException("Invalid connector selected in Azure step. Select Azure connector");
    }

    AzureConnectorDTO connectorConfig = (AzureConnectorDTO) connectorDTO.getConnectorConfig();
    if (RESOURCE_GROUP.equals(fromYamlScopeToInternalScope(stepConfigurationParameters.getScope().getType()))) {
      AzureResourceGroupSpec resourceGroupSpec =
          (AzureResourceGroupSpec) stepConfigurationParameters.getScope().getSpec();
      return executeFetchPreDeploymentDataTask(ambiance, stepParameters, connectorConfig, resourceGroupSpec);
    } else {
      return executeWithPreDeploymentData(ambiance, stepParameters, connectorConfig, null, null);
    }
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    ResponseData responseData = responseSupplier.get();
    if (responseData instanceof AzureFetchArmPreDeploymentDataTaskResponse) {
      AzureFetchArmPreDeploymentDataTaskResponse response = (AzureFetchArmPreDeploymentDataTaskResponse) responseData;
      AzureCreateARMResourceStepConfigurationParameters stepConfigurationParameters =
          ((AzureCreateARMResourceStepParameters) stepParameters.getSpec()).getConfigurationParameters();

      ConnectorInfoDTO connectorDTO =
          cdStepHelper.getConnector(stepConfigurationParameters.getConnectorRef().getValue(), ambiance);
      AzureConnectorDTO connectorConfig = (AzureConnectorDTO) connectorDTO.getConnectorConfig();
      CommandUnitsProgress commandUnitsProgress =
          UnitProgressDataMapper.toCommandUnitsProgress(response.getUnitProgressData());
      return executeWithPreDeploymentData(
          ambiance, stepParameters, connectorConfig, commandUnitsProgress, response.getAzureARMPreDeploymentData());
    } else if (responseData instanceof GitFetchResponse) {
      return handleGitFetchResponse(ambiance, stepParameters, passThroughData, (GitFetchResponse) responseData);
    } else {
      String errorMessage = "Unknown Error";
      return TaskChainResponse.builder()
          .chainEnd(true)
          .passThroughData(StepExceptionPassThroughData.builder()
                               .unitProgressData(UnitProgressData.builder().unitProgresses(new ArrayList<>()).build())
                               .errorMessage(errorMessage)
                               .build())
          .build();
    }
  }

  private TaskChainResponse executeWithPreDeploymentData(Ambiance ambiance, StepElementParameters stepParameters,
      AzureConnectorDTO connectorConfig, CommandUnitsProgress commandUnitsProgress,
      AzureARMPreDeploymentData preDeploymentData) {
    AzureCreateARMResourceStepParameters azureCreateARMResourceStepParameters =
        (AzureCreateARMResourceStepParameters) stepParameters.getSpec();
    AzureCreateARMResourceStepConfigurationParameters stepConfigurationParameters =
        azureCreateARMResourceStepParameters.getConfigurationParameters();
    String connectorRef = getParameterFieldValue(stepConfigurationParameters.getConnectorRef());
    String scopeType = stepConfigurationParameters.getScope().getType();
    saveAzureARMConfig(preDeploymentData,
        getParameterFieldValue(azureCreateARMResourceStepParameters.getProvisionerIdentifier()), ambiance, connectorRef,
        scopeType);
    List<GitFetchFilesConfig> gitFetchFilesConfigs =
        azureCommonHelper.getParametersGitFetchFileConfigs(ambiance, stepConfigurationParameters);
    AzureTemplateFile azureTemplateFile = stepConfigurationParameters.getTemplateFile();
    if (azureCommonHelper.isTemplateStoredOnGit(azureTemplateFile)) {
      gitFetchFilesConfigs.add(getTemplateGitFetchFileConfig(ambiance, stepConfigurationParameters.getTemplateFile()));
    }

    AzureCreateARMResourcePassThroughData passThroughData =
        azureCommonHelper.getAzureCreatePassThroughData(stepConfigurationParameters);
    if (isNotEmpty(gitFetchFilesConfigs)) {
      return azureCommonHelper.getGitFetchFileTaskChainResponse(
          ambiance, gitFetchFilesConfigs, stepParameters, passThroughData, getCommandUnits(true), commandUnitsProgress);
    }

    AppSettingsFile templateBody = null;
    AppSettingsFile parametersBody = null;
    if (ManifestStoreType.HARNESS.equals(azureTemplateFile.getStore().getSpec().getKind())) {
      HarnessStore harnessStore = (HarnessStore) azureTemplateFile.getStore().getSpec();
      templateBody =
          azureWebAppStepHelper.fetchFileContentFromHarnessStore(ambiance, AZURE_TEMPLATE_SELECTOR, harnessStore);
    }

    if (stepConfigurationParameters.getParameters() != null
        && ManifestStoreType.HARNESS.equals(
            stepConfigurationParameters.getParameters().getStore().getSpec().getKind())) {
      HarnessStore harnessStore = (HarnessStore) stepConfigurationParameters.getParameters().getStore().getSpec();
      parametersBody =
          azureWebAppStepHelper.fetchFileContentFromHarnessStore(ambiance, AZURE_PARAMETER_SELECTOR, harnessStore);
    }

    populatePassThroughData(passThroughData, templateBody, parametersBody);
    AzureResourceCreationTaskNGParameters azureARMTaskNGParameters =
        getAzureTaskNGParams(ambiance, stepParameters, connectorConfig, passThroughData, commandUnitsProgress);
    return executeCreateTask(ambiance, stepParameters, azureARMTaskNGParameters, passThroughData);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    if (passThroughData instanceof StepExceptionPassThroughData) {
      StepExceptionPassThroughData stepExceptionPassThroughData = (StepExceptionPassThroughData) passThroughData;
      return cdStepHelper.handleStepExceptionFailure(stepExceptionPassThroughData);
    }
    AzureARMTaskNGResponse azureARMTaskNGResponse;
    try {
      azureARMTaskNGResponse = (AzureARMTaskNGResponse) responseDataSupplier.get();
      if (azureARMTaskNGResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
        return azureCommonHelper.getFailureResponse(
            azureARMTaskNGResponse.getUnitProgressData().getUnitProgresses(), azureARMTaskNGResponse.getErrorMsg());
      }

      AzureCreateARMResourceOutcome azureCreateARMResourceOutcome =
          new AzureCreateARMResourceOutcome(azureCommonHelper.getARMOutputs(azureARMTaskNGResponse.getOutputs()));
      provisionerOutputHelper.saveProvisionerOutputByStepIdentifier(ambiance, azureCreateARMResourceOutcome);
      return StepResponse.builder()
          .unitProgressList(azureARMTaskNGResponse.getUnitProgressData().getUnitProgresses())
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .name(OutcomeExpressionConstants.OUTPUT)
                           .outcome(azureCreateARMResourceOutcome)
                           .build())
          .status(Status.SUCCEEDED)
          .build();
    } catch (TaskNGDataException ex) {
      String errorMsg = String.format(
          "Error while processing Azure Create ARM Resource Task response %s", ex.getCause().getMessage());
      log.error(errorMsg, ex);
      throw ex;
    }
  }

  private void saveAzureARMConfig(AzureARMPreDeploymentData data, String provisionerIdentifier, Ambiance ambiance,
      String connectorRef, String scopeType) {
    AzureARMConfig azureARMConfig = AzureARMConfig.builder()
                                        .accountId(AmbianceUtils.getAccountId(ambiance))
                                        .orgId(AmbianceUtils.getOrgIdentifier(ambiance))
                                        .projectId(AmbianceUtils.getProjectIdentifier(ambiance))
                                        .provisionerIdentifier(provisionerIdentifier)
                                        .stageExecutionId(AmbianceUtils.getStageExecutionIdForExecutionMode(ambiance))
                                        .azureARMPreDeploymentData(data)
                                        .connectorRef(connectorRef)
                                        .scopeType(scopeType)
                                        .build();
    azureARMConfigDAL.saveAzureARMConfig(azureARMConfig);
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  private TaskChainResponse executeCreateTask(Ambiance ambiance, StepElementParameters stepParameters,
      AzureResourceCreationTaskNGParameters parameters, PassThroughData passThroughData) {
    TaskData taskData = TaskData.builder()
                            .async(true)
                            .taskType(TaskType.AZURE_NG_ARM.name())
                            .timeout(StepUtils.getTimeoutMillis(stepParameters.getTimeout(), DEFAULT_TIMEOUT))
                            .parameters(new Object[] {parameters})
                            .build();
    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, getCommandUnits(false), TaskType.AZURE_NG_ARM.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(
            ((AzureCreateARMResourceStepParameters) stepParameters.getSpec()).getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder().taskRequest(taskRequest).passThroughData(passThroughData).chainEnd(true).build();
  }

  private TaskChainResponse executeFetchPreDeploymentDataTask(Ambiance ambiance, StepElementParameters stepParameters,
      AzureConnectorDTO connectorConfig, AzureResourceGroupSpec azureResourceGroupSpec) {
    AzureCreateARMResourceStepConfigurationParameters stepConfigurationParameters =
        ((AzureCreateARMResourceStepParameters) stepParameters.getSpec()).getConfigurationParameters();
    AzureCreateARMResourcePassThroughData passThroughData =
        azureCommonHelper.getAzureCreatePassThroughData(stepConfigurationParameters);

    AzureFetchArmPreDeploymentDataTaskParameters parameters =
        AzureFetchArmPreDeploymentDataTaskParameters.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .connectorDTO(connectorConfig)
            .taskType(FETCH_ARM_PRE_DEPLOYMENT_DATA)
            .resourceGroupName(azureResourceGroupSpec.getResourceGroup().getValue())
            .subscriptionId(azureResourceGroupSpec.getSubscription().getValue())
            .encryptedDataDetails(azureCommonHelper.getAzureEncryptionDetails(ambiance, connectorConfig))
            .timeoutInMs(StepUtils.getTimeoutMillis(stepParameters.getTimeout(), DEFAULT_TIMEOUT))
            .build();

    TaskData taskData = TaskData.builder()
                            .async(true)
                            .taskType(TaskType.AZURE_NG_ARM.name())
                            .timeout(StepUtils.getTimeoutMillis(stepParameters.getTimeout(), DEFAULT_TIMEOUT))
                            .parameters(new Object[] {parameters})
                            .build();

    List<String> commandUnits = new ArrayList<>();
    commandUnits.add(FETCH_RESOURCE_GROUP_TEMPLATE);
    commandUnits.addAll(getCommandUnits(passThroughData.hasGitFiles()));

    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, commandUnits, FETCH_RESOURCE_GROUP_TEMPLATE,
        TaskSelectorYaml.toTaskSelector(
            ((AzureCreateARMResourceStepParameters) stepParameters.getSpec()).getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .passThroughData(passThroughData)
        .chainEnd(false)
        .build();
  }

  private void populatePassThroughData(AzureCreateARMResourcePassThroughData passThroughData,
      AppSettingsFile templateBody, AppSettingsFile parametersBody) {
    passThroughData.setTemplateBody(templateBody);
    passThroughData.setParametersBody(parametersBody);
  }

  private AzureResourceCreationTaskNGParameters getAzureTaskNGParams(Ambiance ambiance,
      StepElementParameters stepElementParameters, AzureConnectorDTO connectorConfig, PassThroughData passThroughData,
      CommandUnitsProgress commandUnitsProgress) {
    AzureCreateARMResourceStepParameters azureCreateStepParameters =
        (AzureCreateARMResourceStepParameters) stepElementParameters.getSpec();
    AzureCreateARMResourcePassThroughData azureCreatePassThroughData =
        (AzureCreateARMResourcePassThroughData) passThroughData;
    AzureCreateARMResourceStepConfigurationParameters stepConfigurationParameters =
        azureCreateStepParameters.getConfigurationParameters();
    AzureARMTaskNGParametersBuilder builder = AzureARMTaskNGParameters.builder();
    azureCreatePassThroughData.getTemplateBody().setFileContent(
        cdExpressionResolver.renderExpression(ambiance, azureCreatePassThroughData.getTemplateBody().getFileContent()));
    azureCreatePassThroughData.getParametersBody().setFileContent(cdExpressionResolver.renderExpression(
        ambiance, azureCreatePassThroughData.getParametersBody().getFileContent()));

    builder.accountId(AmbianceUtils.getAccountId(ambiance))
        .taskType(ARM_DEPLOYMENT)
        .templateBody(azureCreatePassThroughData.getTemplateBody())
        .connectorDTO(connectorConfig)
        .parametersBody(azureCreatePassThroughData.getParametersBody())
        .commandUnitsProgress(commandUnitsProgress)
        .timeoutInMs(StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), DEFAULT_TIMEOUT));

    setScopeTypeValues(builder, stepConfigurationParameters);
    return builder.encryptedDataDetails(azureCommonHelper.getAzureEncryptionDetails(ambiance, connectorConfig)).build();
  }

  private AzureARMTaskNGParametersBuilder setScopeTypeValues(AzureARMTaskNGParametersBuilder builder,
      AzureCreateARMResourceStepConfigurationParameters stepConfigurationParameters) {
    String scopeType = stepConfigurationParameters.getScope().getType();

    switch (scopeType) {
      case AzureScopeTypesNames.ResourceGroup:
        AzureResourceGroupSpec resourceGroupSpec =
            (AzureResourceGroupSpec) stepConfigurationParameters.getScope().getSpec();
        builder.scopeType(fromYamlScopeToInternalScope(stepConfigurationParameters.getScope().getType()))
            .deploymentMode(azureCommonHelper.retrieveDeploymentMode(
                fromYamlScopeToInternalScope(stepConfigurationParameters.getScope().getType()),
                resourceGroupSpec.getMode().toString()))
            .subscriptionId(resourceGroupSpec.getSubscription().getValue())
            .resourceGroupName(resourceGroupSpec.getResourceGroup().getValue());
        break;
      case AzureScopeTypesNames.Subscription:
        AzureSubscriptionSpec subscriptionSpec =
            (AzureSubscriptionSpec) stepConfigurationParameters.getScope().getSpec();
        builder.scopeType(fromYamlScopeToInternalScope(stepConfigurationParameters.getScope().getType()))
            .deploymentDataLocation(subscriptionSpec.getLocation().getValue())
            .deploymentMode(azureCommonHelper.retrieveDeploymentMode(
                fromYamlScopeToInternalScope(stepConfigurationParameters.getScope().getType()), null))
            .subscriptionId(subscriptionSpec.getSubscription().getValue());
        break;
      case AzureScopeTypesNames.ManagementGroup:
        AzureManagementSpec managementSpec = (AzureManagementSpec) stepConfigurationParameters.getScope().getSpec();
        builder.scopeType(fromYamlScopeToInternalScope(stepConfigurationParameters.getScope().getType()))
            .deploymentMode(azureCommonHelper.retrieveDeploymentMode(
                fromYamlScopeToInternalScope(stepConfigurationParameters.getScope().getType()), null))
            .deploymentDataLocation(managementSpec.getLocation().getValue())
            .managementGroupId(managementSpec.getManagementGroupId().getValue());
        break;

      case AzureScopeTypesNames.Tenant:
        AzureTenantSpec tenantSpec = (AzureTenantSpec) stepConfigurationParameters.getScope().getSpec();
        builder.scopeType(fromYamlScopeToInternalScope(stepConfigurationParameters.getScope().getType()))
            .deploymentMode(azureCommonHelper.retrieveDeploymentMode(
                fromYamlScopeToInternalScope(stepConfigurationParameters.getScope().getType()), null))
            .deploymentDataLocation(tenantSpec.getLocation().getValue());
        break;
      default:
        throw new InvalidRequestException(
            "Invalid scope type in Azure step. Select one of the following: ResourceGroup, Subscription, Management, Tenant");
    }
    return builder;
  }

  TaskChainResponse handleGitFetchResponse(Ambiance ambiance, StepElementParameters stepElementParameters,
      PassThroughData passThroughData, GitFetchResponse responseData) {
    Map<String, FetchFilesResult> filesFromMultipleRepo = responseData.getFilesFromMultipleRepo();
    AzureCreateARMResourceStepParameters spec = (AzureCreateARMResourceStepParameters) stepElementParameters.getSpec();
    AppSettingsFile templateBody = null;
    AppSettingsFile parametersBody = null;
    // Retrieve the content from the files from the Git File response, or, if it's not there, try to retrieve it from
    // the Harness store.
    if (filesFromMultipleRepo.get(PARAMETERS_FILE_IDENTIFIER) != null) {
      parametersBody = AppSettingsFile.create(
          filesFromMultipleRepo.get(PARAMETERS_FILE_IDENTIFIER).getFiles().get(0).getFileContent());
    } else if (spec.getConfigurationParameters().getParameters() != null
        && ManifestStoreType.HARNESS.equals(
            spec.getConfigurationParameters().getParameters().getStore().getSpec().getKind())) {
      HarnessStore harnessStore = (HarnessStore) spec.getConfigurationParameters().getParameters().getStore().getSpec();
      parametersBody =
          azureWebAppStepHelper.fetchFileContentFromHarnessStore(ambiance, AZURE_PARAMETER_SELECTOR, harnessStore);
    }

    if (filesFromMultipleRepo.get(TEMPLATE_FILE_IDENTIFIER) != null) {
      templateBody = AppSettingsFile.create(
          filesFromMultipleRepo.get(TEMPLATE_FILE_IDENTIFIER).getFiles().get(0).getFileContent());
    } else if (ManifestStoreType.HARNESS.equals(
                   spec.getConfigurationParameters().getTemplateFile().getStore().getSpec().getKind())) {
      HarnessStore harnessStore =
          (HarnessStore) spec.getConfigurationParameters().getTemplateFile().getStore().getSpec();
      templateBody =
          azureWebAppStepHelper.fetchFileContentFromHarnessStore(ambiance, AZURE_TEMPLATE_SELECTOR, harnessStore);
    }
    populatePassThroughData((AzureCreateARMResourcePassThroughData) passThroughData, templateBody, parametersBody);
    AzureConnectorDTO connectorDTO = azureCommonHelper.getAzureConnectorConfig(
        ambiance, ParameterField.createValueField(spec.getConfigurationParameters().getConnectorRef().getValue()));

    AzureResourceCreationTaskNGParameters azureTaskNGParameters =
        getAzureTaskNGParams(ambiance, stepElementParameters, connectorDTO, passThroughData,
            UnitProgressDataMapper.toCommandUnitsProgress(responseData.getUnitProgressData()));
    return executeCreateTask(ambiance, stepElementParameters, azureTaskNGParameters, passThroughData);
  }

  private static ARMScopeType fromYamlScopeToInternalScope(final String value) {
    switch (value) {
      case AzureScopeTypesNames.ResourceGroup:
        return RESOURCE_GROUP;
      case AzureScopeTypesNames.Subscription:
        return SUBSCRIPTION;
      case AzureScopeTypesNames.ManagementGroup:
        return MANAGEMENT_GROUP;
      case AzureScopeTypesNames.Tenant:
        return TENANT;
      default:
        return null;
    }
  }

  private GitFetchFilesConfig getTemplateGitFetchFileConfig(Ambiance ambiance, AzureTemplateFile azureTemplateFile) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) azureTemplateFile.getStore().getSpec();
    List<String> paths = new ArrayList<>(ParameterFieldHelper.getParameterFieldValue(gitStoreConfig.getPaths()));
    return GitFetchFilesConfig.builder()
        .manifestType(AZURE_TEMPLATE_TYPE)
        .identifier(TEMPLATE_FILE_IDENTIFIER)
        .gitStoreDelegateConfig(
            azureCommonHelper.getGitStoreDelegateConfig(azureTemplateFile.getStore().getSpec(), ambiance, paths))
        .build();
  }

  private List<String> getCommandUnits(boolean shouldFetchFiles) {
    List<String> commandUnits = new ArrayList<>();
    if (shouldFetchFiles) {
      commandUnits.add(K8sCommandUnitConstants.FetchFiles);
    }
    commandUnits.addAll(Arrays.asList(AzureConstants.EXECUTE_ARM_DEPLOYMENT, AzureConstants.ARM_DEPLOYMENT_STEADY_STATE,
        AzureConstants.ARM_DEPLOYMENT_OUTPUTS));

    return commandUnits;
  }
}
