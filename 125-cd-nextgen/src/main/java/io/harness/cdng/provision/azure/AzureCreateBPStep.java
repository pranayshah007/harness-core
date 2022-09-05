/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import static io.harness.cdng.provision.azure.AzureCommonHelper.BLUEPRINT_IDENTIFIER;
import static io.harness.cdng.provision.azure.AzureCommonHelper.BP_TEMPLATE_TYPE;
import static io.harness.cdng.provision.azure.AzureCommonHelper.DEFAULT_TIMEOUT;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.delegate.task.azure.arm.AzureARMTaskType.BLUEPRINT_DEPLOYMENT;

import io.harness.EntityType;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.provision.azure.beans.AzureCreateBPPassThroughData;
import io.harness.common.ParameterFieldHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.azure.arm.AzureBlueprintTaskNGParameters;
import io.harness.delegate.task.azure.arm.AzureResourceCreationTaskNGParameters;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
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
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AzureCreateBPStep extends TaskChainExecutableWithRollbackAndRbac {
  private static final String BLUEPRINT_JSON = "blueprint.json";
  private static final String ASSIGN_JSON = "assign.json";
  private static final String ARTIFACTS = "artifacts/";
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AZURE_CREATE_BP_RESOURCE.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private KryoSerializer kryoSerializer;

  @Inject private StepHelper stepHelper;

  @Inject private AzureCommonHelper azureCommonHelper;
  @Inject private CDStepHelper cdStepHelper;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    if (!cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.AZURE_ARM_BP_NG)) {
      throw new AccessDeniedException(
          "The creation of resources using Azure Blueprint in NG is not enabled for this account."
              + " Please contact harness customer care.",
          ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }
    List<EntityDetail> entityDetailList = new ArrayList<>();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    // Template file connector
    AzureCreateBPStepParameters azureCreateBPStepParameters = (AzureCreateBPStepParameters) stepParameters.getSpec();
    AzureTemplateFile azureCreateTemplateFile = azureCreateBPStepParameters.getConfiguration().getTemplateFile();

    if (ManifestStoreType.isInGitSubset(azureCreateTemplateFile.getStore().getSpec().getKind())) {
      String connectorRef =
          getParameterFieldValue(azureCreateTemplateFile.getStore().getSpec().getConnectorReference());
      IdentifierRef identifierRef =
          IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
      EntityDetail entityDetail = EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(identifierRef).build();
      entityDetailList.add(entityDetail);
    }

    // Azure connector
    String connectorRef = azureCreateBPStepParameters.getConfiguration().getConnectorRef().getValue();
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
    EntityDetail entityDetail = EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(identifierRef).build();
    entityDetailList.add(entityDetail);

    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetailList, true);
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    ResponseData responseData = responseSupplier.get();
    if (responseData instanceof GitFetchResponse) {
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

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    if (passThroughData instanceof StepExceptionPassThroughData) {
      StepExceptionPassThroughData stepExceptionPassThroughData = (StepExceptionPassThroughData) passThroughData;
      return cdStepHelper.handleStepExceptionFailure(stepExceptionPassThroughData);
    }
    // TODO: To implement after the DelegateTask is implemented.
    return null;
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    AzureCreateBPStepConfigurationParameters azureCreateBPStepConfigurationParameters =
        ((AzureCreateBPStepParameters) stepParameters.getSpec()).getConfiguration();
    ConnectorInfoDTO connectorDTO =
        cdStepHelper.getConnector(azureCreateBPStepConfigurationParameters.getConnectorRef().getValue(), ambiance);
    if (!(connectorDTO.getConnectorConfig() instanceof AzureConnectorDTO)) {
      throw new InvalidRequestException("Invalid connector selected in Azure step. Select Azure connector");
    }
    List<GitFetchFilesConfig> gitFetchFilesConfigs = Collections.singletonList(
        getTemplateGitFetchFileConfig(ambiance, azureCreateBPStepConfigurationParameters.getTemplateFile()));

    AzureCreateBPPassThroughData passThroughData = AzureCreateBPPassThroughData.builder().build();
    return azureCommonHelper.getGitFetchFileTaskChainResponse(
        ambiance, gitFetchFilesConfigs, stepParameters, passThroughData);
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  private TaskChainResponse executeCreateTask(Ambiance ambiance, StepElementParameters stepParameters,
      AzureResourceCreationTaskNGParameters parameters, PassThroughData passThroughData) {
    TaskData taskData =
        TaskData.builder()
            .async(true)
            .taskType(TaskType.AZURE_NG_ARM.name())
            .timeout(StepUtils.getTimeoutMillis(stepParameters.getTimeout(), AzureCommonHelper.DEFAULT_TIMEOUT))
            .parameters(new Object[] {parameters})
            .build();
    final TaskRequest taskRequest = StepUtils.prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
        Collections.singletonList(AzureCommandUnit.Create.name()), TaskType.AZURE_NG_ARM.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(
            ((AzureCreateBPStepParameters) stepParameters.getSpec()).getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder().taskRequest(taskRequest).passThroughData(passThroughData).chainEnd(true).build();
  }

  private GitFetchFilesConfig getTemplateGitFetchFileConfig(
      Ambiance ambiance, AzureTemplateFile azureCreateTemplateFile) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) azureCreateTemplateFile.getStore().getSpec();
    List<String> paths = new ArrayList<>();
    paths.add(ParameterFieldHelper.getParameterFieldValue(gitStoreConfig.getFolderPath()));
    return GitFetchFilesConfig.builder()
        .manifestType(BP_TEMPLATE_TYPE)
        .identifier(BLUEPRINT_IDENTIFIER)
        .gitStoreDelegateConfig(
            azureCommonHelper.getGitStoreDelegateConfig(azureCreateTemplateFile.getStore().getSpec(), ambiance, paths))
        .build();
  }

  private AzureResourceCreationTaskNGParameters getAzureTaskNGParams(Ambiance ambiance,
      StepElementParameters stepElementParameters, AzureConnectorDTO connectorConfig, PassThroughData passThroughData) {
    AzureCreateBPStepParameters azureCreateStepParameters =
        (AzureCreateBPStepParameters) stepElementParameters.getSpec();
    AzureCreateBPPassThroughData azureCreateBPPassThroughData = (AzureCreateBPPassThroughData) passThroughData;

    return AzureBlueprintTaskNGParameters.builder()
        .accountId(AmbianceUtils.getAccountId(ambiance))
        .taskType(BLUEPRINT_DEPLOYMENT)
        .connectorDTO(connectorConfig)
        .timeoutInMs(StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), DEFAULT_TIMEOUT))
        .blueprintJson(azureCreateBPPassThroughData.getBlueprintBody())
        .assignmentJson(azureCreateBPPassThroughData.getAssignBody())
        .artifacts(azureCreateBPPassThroughData.getArtifacts())
        .assignmentName(azureCreateStepParameters.getConfiguration().getAssignmentName().getValue())
        .encryptedDataDetailList(azureCommonHelper.getAzureEncryptionDetails(ambiance, connectorConfig))
        .scope(azureCreateStepParameters.getConfiguration().getScope().toString())
        .build();
  }
  private void populatePassThroughData(AzureCreateBPPassThroughData passThroughData, String blueprintBody,
      String assignmentBody, Map<String, String> artifacts) {
    passThroughData.setArtifacts(artifacts);
    passThroughData.setAssignBody(assignmentBody);
    passThroughData.setBlueprintBody(blueprintBody);
  }

  TaskChainResponse handleGitFetchResponse(Ambiance ambiance, StepElementParameters stepElementParameters,
      PassThroughData passThroughData, GitFetchResponse responseData) {
    Map<String, FetchFilesResult> filesFromMultipleRepo = responseData.getFilesFromMultipleRepo();

    String assignBody = null;
    String blueprintBody = null;
    Map<String, String> artifacts = new HashMap<>();
    if (filesFromMultipleRepo.get(BLUEPRINT_IDENTIFIER) != null) {
      List<GitFile> gitFiles = filesFromMultipleRepo.get(BLUEPRINT_IDENTIFIER).getFiles();
      for (GitFile gitFile : gitFiles) {
        if (gitFile.getFilePath().contains(BLUEPRINT_JSON)) {
          blueprintBody = gitFile.getFileContent();
        } else if (gitFile.getFilePath().contains(ASSIGN_JSON)) {
          assignBody = gitFile.getFileContent();
        } else if (gitFile.getFilePath().contains(ARTIFACTS)) {
          artifacts.put(
              gitFile.getFilePath().substring(gitFile.getFilePath().lastIndexOf(ARTIFACTS) + ARTIFACTS.length()),
              gitFile.getFileContent());
        }
      }
    }
    AzureCreateBPStepParameters spec = (AzureCreateBPStepParameters) stepElementParameters.getSpec();

    populatePassThroughData((AzureCreateBPPassThroughData) passThroughData, blueprintBody, assignBody, artifacts);
    AzureConnectorDTO connectorDTO = azureCommonHelper.getAzureConnectorConfig(
        ambiance, ParameterField.createValueField(spec.getConfiguration().getConnectorRef().getValue()));

    AzureResourceCreationTaskNGParameters azureTaskNGParameters =
        getAzureTaskNGParams(ambiance, stepElementParameters, connectorDTO, passThroughData);
    return executeCreateTask(ambiance, stepElementParameters, azureTaskNGParameters, passThroughData);
  }
}
