/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.provision.azure.AzureCommonHelper.BLUEPRINT_IDENTIFIER;
import static io.harness.cdng.provision.azure.AzureCommonHelper.BP_TEMPLATE_TYPE;
import static io.harness.cdng.provision.azure.AzureCommonHelper.DEFAULT_TIMEOUT;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.azure.arm.AzureARMTaskType.BLUEPRINT_DEPLOYMENT;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.AzureConstants;
import io.harness.beans.FileReference;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.provision.azure.beans.AzureCreateBPPassThroughData;
import io.harness.common.ParameterFieldHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.azure.arm.AzureBlueprintTaskNGParameters;
import io.harness.delegate.task.azure.arm.AzureBlueprintTaskNGResponse;
import io.harness.delegate.task.azure.arm.AzureResourceCreationTaskNGParameters;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.dto.node.FolderNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.NGAccess;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
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
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @Inject private StepHelper stepHelper;

  @Inject private AzureCommonHelper azureCommonHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private FileStoreService fileStoreService;

  @Inject private EngineExpressionService engineExpressionService;

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {
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
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepBaseParameters stepParameters,
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
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepBaseParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    if (passThroughData instanceof StepExceptionPassThroughData) {
      StepExceptionPassThroughData stepExceptionPassThroughData = (StepExceptionPassThroughData) passThroughData;
      return cdStepHelper.handleStepExceptionFailure(stepExceptionPassThroughData);
    }
    AzureBlueprintTaskNGResponse azureBlueprintTaskNGResponse;
    try {
      azureBlueprintTaskNGResponse = (AzureBlueprintTaskNGResponse) responseDataSupplier.get();
      if (azureBlueprintTaskNGResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
        return azureCommonHelper.getFailureResponse(
            azureBlueprintTaskNGResponse.getUnitProgressData().getUnitProgresses(),
            azureBlueprintTaskNGResponse.getErrorMsg());
      }
      return StepResponse.builder()
          .unitProgressList(azureBlueprintTaskNGResponse.getUnitProgressData().getUnitProgresses())
          .status(Status.SUCCEEDED)
          .build();
    } catch (TaskNGDataException ex) {
      String errorMsg =
          String.format("Error while processing Azure Create ARM Resource Task response %s", ex.getMessage());
      log.error(errorMsg, ex);
      throw ex;
    }
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    AzureCreateBPStepConfigurationParameters azureCreateBPStepConfigurationParameters =
        ((AzureCreateBPStepParameters) stepParameters.getSpec()).getConfiguration();
    ConnectorInfoDTO connectorDTO =
        cdStepHelper.getConnector(azureCreateBPStepConfigurationParameters.getConnectorRef().getValue(), ambiance);
    if (!(connectorDTO.getConnectorConfig() instanceof AzureConnectorDTO)) {
      throw new InvalidRequestException("Invalid connector selected in Azure step. Select Azure connector");
    }
    if (ManifestStoreType.isInGitSubset(
            azureCreateBPStepConfigurationParameters.getTemplateFile().getStore().getSpec().getKind())) {
      List<GitFetchFilesConfig> gitFetchFilesConfigs = Collections.singletonList(
          getTemplateGitFetchFileConfig(ambiance, azureCreateBPStepConfigurationParameters.getTemplateFile()));

      AzureCreateBPPassThroughData passThroughData = AzureCreateBPPassThroughData.builder().build();
      return azureCommonHelper.getGitFetchFileTaskChainResponse(
          ambiance, gitFetchFilesConfigs, stepParameters, passThroughData, getCommandUnits(true), null);
    } else if (ManifestStoreType.HARNESS.equals(
                   azureCreateBPStepConfigurationParameters.getTemplateFile().getStore().getSpec().getKind())) {
      HarnessStore harnessStore =
          (HarnessStore) azureCreateBPStepConfigurationParameters.getTemplateFile().getStore().getSpec();
      Map<String, String> fileContent = fetchFileContentFromHarnessStore(ambiance, harnessStore);

      String blueprint = fileContent.get(BLUEPRINT_JSON);
      fileContent.remove(BLUEPRINT_JSON);
      String assignBody = fileContent.get(ASSIGN_JSON);
      fileContent.remove(ASSIGN_JSON);
      AzureCreateBPPassThroughData passThroughData = AzureCreateBPPassThroughData.builder().build();

      populatePassThroughData(passThroughData, blueprint, assignBody, fileContent);
      AzureConnectorDTO azureConnectorDTO = azureCommonHelper.getAzureConnectorConfig(ambiance,
          ParameterField.createValueField(azureCreateBPStepConfigurationParameters.getConnectorRef().getValue()));

      AzureResourceCreationTaskNGParameters azureTaskNGParameters =
          getAzureTaskNGParams(ambiance, stepParameters, azureConnectorDTO, passThroughData, null);
      return executeCreateTask(ambiance, stepParameters, azureTaskNGParameters, passThroughData);
    }
    throw new InvalidRequestException("Unsupported Store type");
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  private TaskChainResponse executeCreateTask(Ambiance ambiance, StepBaseParameters stepParameters,
      AzureResourceCreationTaskNGParameters parameters, PassThroughData passThroughData) {
    TaskData taskData =
        TaskData.builder()
            .async(true)
            .taskType(TaskType.AZURE_NG_ARM.name())
            .timeout(StepUtils.getTimeoutMillis(stepParameters.getTimeout(), AzureCommonHelper.DEFAULT_TIMEOUT))
            .parameters(new Object[] {parameters})
            .build();
    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, getCommandUnits(false), "Azure Blueprint",
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
      StepBaseParameters stepElementParameters, AzureConnectorDTO connectorConfig, PassThroughData passThroughData,
      CommandUnitsProgress commandUnitProgress) {
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
        .commandUnitsProgress(commandUnitProgress)
        .build();
  }
  private void populatePassThroughData(AzureCreateBPPassThroughData passThroughData, String blueprintBody,
      String assignmentBody, Map<String, String> artifacts) {
    passThroughData.setArtifacts(artifacts);
    passThroughData.setAssignBody(assignmentBody);
    passThroughData.setBlueprintBody(blueprintBody);
  }

  TaskChainResponse handleGitFetchResponse(Ambiance ambiance, StepBaseParameters stepElementParameters,
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
        getAzureTaskNGParams(ambiance, stepElementParameters, connectorDTO, passThroughData,
            UnitProgressDataMapper.toCommandUnitsProgress(responseData.getUnitProgressData()));
    return executeCreateTask(ambiance, stepElementParameters, azureTaskNGParameters, passThroughData);
  }

  /**
   * Retrieve the files from the Harness Store given a Harness Store with a specific path
   * This path should be a folder path. If the FileReference resolves to a file, it will throw an exception
   * @param ambiance object containing project data
   * @param harnessStore Store to retrieve the files from
   *
   *
   * @return Map of strings with the key as the filename and the value as the content of the file.
   * @throws InvalidArgumentsException if there is no store, we can't find any content in the store or if the path
   *     points
   * to a secret file
   * */
  public Map<String, String> fetchFileContentFromHarnessStore(Ambiance ambiance, HarnessStore harnessStore) {
    HarnessStore renderedHarnessStore = (HarnessStore) cdExpressionResolver.updateExpressions(ambiance, harnessStore);
    if (!ParameterField.isNull(renderedHarnessStore.getFiles())
        && isNotEmpty(renderedHarnessStore.getFiles().getValue())) {
      List<String> harnessStoreFiles = renderedHarnessStore.getFiles().getValue();
      String firstFile = harnessStoreFiles.stream().findFirst().orElseThrow(
          () -> new InvalidArgumentsException("No file configured for harness file store"));
      return fetchFileContentFromFileStore(ambiance, firstFile);

    } else if (!ParameterField.isNull(renderedHarnessStore.getSecretFiles())
        && isNotEmpty(renderedHarnessStore.getSecretFiles().getValue())) {
      throw new InvalidArgumentsException("Secrets files are not supported for Blueprints");
    }
    throw new InvalidArgumentsException("The selected path points to an empty Store");
  }

  /**
   * Auxiliary function to retrieve the files and subfolders given a path from a Harness Store. If the
   * @param ambiance object containing project data
   * @param filePath path to the folder to extract the files from
   *
   * @return Map of strings with the key as the filename and the value as the content of the file.
   * @throws  InvalidArgumentsException if the given path doesn't resolve to a FileStoreNode or if
   * it resolves to a FileNodeDTO type
   **/
  private Map<String, String> fetchFileContentFromFileStore(Ambiance ambiance, String filePath) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    FileReference fileReference = FileReference.of(
        filePath, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    FileStoreNodeDTO fileStoreNodeDTO =
        fileStoreService
            .getWithChildrenByPath(fileReference.getAccountIdentifier(), fileReference.getOrgIdentifier(),
                fileReference.getProjectIdentifier(), fileReference.getPath(), true)
            .orElseThrow(
                () -> new InvalidArgumentsException(format("File '%s' doesn't exists", fileReference.getPath())));

    if (fileStoreNodeDTO instanceof FolderNodeDTO) {
      FolderNodeDTO folderNodeDTO = (FolderNodeDTO) fileStoreNodeDTO;
      return retrieveFilesFromFileStore(ambiance, folderNodeDTO.getChildren());
    }

    if (fileStoreNodeDTO instanceof FileNodeDTO) {
      throw new InvalidArgumentsException(
          format("Provided path '%s' is a file, expecting a folder", fileReference.getPath()));
    }

    log.error("Unknown file store node: {}", fileStoreNodeDTO.getClass().getSimpleName());
    throw new InvalidArgumentsException("Unsupported file store node");
  }

  /**
   * Auxiliary function used to Iterate through all the files and sybfolders
   * @param ambiance object containing project data
   * @param filesAndFolders FileStoreNodeDTO to retrieve the files and subfolders from
   * @return Map of strings with the key as the filename and the value as the content of the file.
   */
  private Map<String, String> retrieveFilesFromFileStore(Ambiance ambiance, List<FileStoreNodeDTO> filesAndFolders) {
    Map<String, String> fileMap = new HashMap<>();

    filesAndFolders.forEach(f -> {
      if (f instanceof FileNodeDTO) {
        FileNodeDTO fileNodeDTO = (FileNodeDTO) f;
        if (f.getPath().contains(BLUEPRINT_JSON) || f.getPath().contains(ASSIGN_JSON)) {
          if (fileNodeDTO.getContent() != null) {
            fileMap.put(f.getName(),
                engineExpressionService.renderExpression(ambiance, fileNodeDTO.getContent().replaceAll("\\r", "")));
          }
        } else if (f.getPath().contains(ARTIFACTS)) {
          if (fileNodeDTO.getContent() != null) {
            fileMap.put(f.getName(),
                engineExpressionService.renderExpression(ambiance, fileNodeDTO.getContent()).replaceAll("\\r", ""));
          }
        }
      } else if (f instanceof FolderNodeDTO) {
        FolderNodeDTO folderNodeDTO = (FolderNodeDTO) f;
        fileMap.putAll(retrieveFilesFromFileStore(ambiance, folderNodeDTO.getChildren()));
      }
    });
    return fileMap;
  }

  private List<String> getCommandUnits(boolean shouldFetchFiles) {
    List<String> commandUnits = new ArrayList<>();
    if (shouldFetchFiles) {
      commandUnits.add(K8sCommandUnitConstants.FetchFiles);
    }
    commandUnits.addAll(
        Arrays.asList(AzureConstants.BLUEPRINT_DEPLOYMENT, AzureConstants.BLUEPRINT_DEPLOYMENT_STEADY_STATE));
    return commandUnits;
  }
}
