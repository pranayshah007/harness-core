/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.manifest.yaml.harness.HarnessStoreConstants.HARNESS_STORE_TYPE;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.acr.mappers.AcrResourceMapper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.harness.HarnessStoreFile;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.common.NGTaskType;
import io.harness.common.ParameterRuntimeFiledHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.utils.ConnectorUtils;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.azure.AcrResponseDTO;
import io.harness.delegate.beans.azure.response.AzureDelegateTaskResponse;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskParams;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.azure.AcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.azure.AcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.encryption.Scope;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import io.harness.utils.IdentifierRefHelper;
import software.wings.beans.TaskType;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;

@Singleton
@OwnedBy(CDP)
public class AzureHelperService {
  @Inject @Named(DEFAULT_CONNECTOR_SERVICE) private ConnectorService connectorService;
  @Inject private SecretManagerClientService secretManagerClientService;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private FileStoreService fileStoreService;
  @VisibleForTesting static final int defaultTimeoutInSecs = 30;

  public AzureConnectorDTO getConnector(IdentifierRef azureConnectorRef) {
    Optional<ConnectorResponseDTO> connectorDTO =
        connectorService.get(azureConnectorRef.getAccountIdentifier(), azureConnectorRef.getOrgIdentifier(),
            azureConnectorRef.getProjectIdentifier(), azureConnectorRef.getIdentifier());

    if (!connectorDTO.isPresent()) {
      throw new InvalidRequestException(String.format("Connector not found for identifier : [%s] with scope: [%s]",
                                            azureConnectorRef.getIdentifier(), azureConnectorRef.getScope()),
          WingsException.USER);
    }

    if (!isAzureConnector(connectorDTO.get())) {
      throw new InvalidRequestException(
          String.format(
              "Connector with identifier [%s] with scope: [%s] is not an Azure connector. Please check you configuration.",
              azureConnectorRef.getIdentifier(), azureConnectorRef.getScope()),
          WingsException.USER);
    }

    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    return (AzureConnectorDTO) connectors.getConnectorConfig();
  }

  public boolean isAzureConnector(@NotNull ConnectorResponseDTO connectorResponseDTO) {
    return ConnectorType.AZURE == (connectorResponseDTO.getConnector().getConnectorType());
  }

  public BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  public List<EncryptedDataDetail> getEncryptionDetails(
      @Nonnull AzureConnectorDTO azureConnectorDTO, @Nonnull NGAccess ngAccess) {
    if (azureConnectorDTO.getCredential() != null
        && azureConnectorDTO.getCredential().getAzureCredentialType() == AzureCredentialType.MANUAL_CREDENTIALS
        && azureConnectorDTO.getCredential().getConfig() != null) {
      return secretManagerClientService.getEncryptionDetails(ngAccess,
          ((AzureManualDetailsDTO) azureConnectorDTO.getCredential().getConfig()).getAuthDTO().getCredentials());
    }
    return new ArrayList<>();
  }

  public DelegateResponseData getResponseData(Ambiance ambiance, BaseNGAccess ngAccess,
      ExecutionCapabilityDemander executionCapabilityDemander, Optional<Integer> customTimeoutInSec) {
    TaskParameters taskParameters = null;
    String taskType = null;
    Collection<? extends String> taskSelectors = null;

    if (executionCapabilityDemander instanceof AzureTaskParams) {
      AzureTaskParams azureTaskParams = (AzureTaskParams) executionCapabilityDemander;
      taskParameters = azureTaskParams;
      taskType = TaskType.NG_AZURE_TASK.name();
      taskSelectors = azureTaskParams.getDelegateSelectors();
    } else if (executionCapabilityDemander instanceof AcrArtifactDelegateRequest) {
      AcrArtifactDelegateRequest acrArtifactDelegateRequest = (AcrArtifactDelegateRequest) executionCapabilityDemander;
      ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                          .accountId(ngAccess.getAccountIdentifier())
                                                          .artifactTaskType(ArtifactTaskType.GET_BUILDS)
                                                          .attributes(acrArtifactDelegateRequest)
                                                          .build();
      taskParameters = artifactTaskParameters;
      taskType = NGTaskType.ACR_ARTIFACT_TASK_NG.name();
      taskSelectors = acrArtifactDelegateRequest.getAzureConnectorDTO().getDelegateSelectors();
    }

    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .executionTimeout(java.time.Duration.ofSeconds(customTimeoutInSec.orElse(defaultTimeoutInSecs)))
            .taskSetupAbstraction(SetupAbstractionKeys.orgIdentifier, ngAccess.getOrgIdentifier())
            .taskSetupAbstraction(SetupAbstractionKeys.ng, "true")
            .taskSetupAbstraction(
                SetupAbstractionKeys.owner, ngAccess.getOrgIdentifier() + "/" + ngAccess.getProjectIdentifier())
            .taskSetupAbstraction(SetupAbstractionKeys.projectIdentifier, ngAccess.getProjectIdentifier())
            .taskParameters(taskParameters)
            .taskType(taskType)
            .taskSelectors(taskSelectors)
            .logStreamingAbstractions(createLogStreamingAbstractions(ngAccess, ambiance))
            .build();

    return delegateGrpcClientWrapper.executeSyncTask(delegateTaskRequest);
  }

  public DelegateResponseData getTaskExecutionResponse(DelegateResponseData responseData, String ifFailedMessage) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new ArtifactServerException(ifFailedMessage + " - " + errorNotifyResponseData.getErrorMessage());
    } else if (responseData instanceof AzureDelegateTaskResponse) {
      AzureDelegateTaskResponse azureListResponse = (AzureDelegateTaskResponse) responseData;
      if (azureListResponse.getCommandExecutionStatus() != SUCCESS) {
        throw new ArtifactServerException(ifFailedMessage + " - " + azureListResponse.getErrorSummary());
      }
    } else if (responseData instanceof ArtifactTaskResponse) {
      ArtifactTaskResponse artifactTaskResponse = (ArtifactTaskResponse) responseData;
      if (artifactTaskResponse.getCommandExecutionStatus() != SUCCESS) {
        throw new ArtifactServerException(ifFailedMessage);
      }
    }
    return responseData;
  }

  public DelegateResponseData executeSyncTask(
      ExecutionCapabilityDemander params, BaseNGAccess ngAccess, String ifFailedMessage) {
    DelegateResponseData responseData = getResponseData(null, ngAccess, params, Optional.empty());
    return getTaskExecutionResponse(responseData, ifFailedMessage);
  }
  public DelegateResponseData executeSyncTask(
      ExecutionCapabilityDemander params, BaseNGAccess ngAccess, String ifFailedMessage, Optional<Integer> timeout) {
    DelegateResponseData responseData = getResponseData(null, ngAccess, params, timeout);
    return getTaskExecutionResponse(responseData, ifFailedMessage);
  }

  public DelegateResponseData executeSyncTask(
      Ambiance ambiance, ExecutionCapabilityDemander params, BaseNGAccess ngAccess, String ifFailedMessage) {
    DelegateResponseData responseData = getResponseData(ambiance, ngAccess, params, Optional.empty());
    return getTaskExecutionResponse(responseData, ifFailedMessage);
  }

  public AcrResponseDTO getAcrResponseDTO(ArtifactTaskExecutionResponse artifactTaskExecutionResponse) {
    List<AcrArtifactDelegateResponse> acrArtifactDelegateResponses =
        artifactTaskExecutionResponse.getArtifactDelegateResponses()
            .stream()
            .map(delegateResponse -> (AcrArtifactDelegateResponse) delegateResponse)
            .collect(Collectors.toList());
    return AcrResourceMapper.toAcrResponse(acrArtifactDelegateResponses);
  }

  public void validateSettingsStoreReferences(StoreConfigWrapper storeConfigWrapper, Ambiance ambiance, String entityType) {
    StoreConfig storeConfig = storeConfigWrapper.getSpec();
    String storeKind = storeConfig.getKind();
    if (HARNESS_STORE_TYPE.equals(storeKind)) {
      validateSettingsFileRefs((HarnessStore) storeConfig, ambiance, entityType);
    } else {
      validateSettingsConnectorByRef(storeConfig, ambiance, entityType);
    }
  }

  private LinkedHashMap<String, String> createLogStreamingAbstractions(BaseNGAccess ngAccess, Ambiance ambiance) {
    LinkedHashMap<String, String> logStreamingAbstractions = new LinkedHashMap<>();
    logStreamingAbstractions.put(SetupAbstractionKeys.accountId, ngAccess.getAccountIdentifier());
    logStreamingAbstractions.put(SetupAbstractionKeys.orgIdentifier, ngAccess.getOrgIdentifier());
    logStreamingAbstractions.put(SetupAbstractionKeys.projectIdentifier, ngAccess.getProjectIdentifier());
    if (ambiance != null) {
      logStreamingAbstractions.put(SetupAbstractionKeys.pipelineId, ambiance.getMetadata().getPipelineIdentifier());
      logStreamingAbstractions.put(
          SetupAbstractionKeys.runSequence, String.valueOf(ambiance.getMetadata().getRunSequence()));

      for (int i = 0; i < ambiance.getLevelsList().size(); i++) {
        logStreamingAbstractions.put("level" + i, ambiance.getLevels(i).getIdentifier());
      }
    }
    return logStreamingAbstractions;
  }

  private void validateSettingsFileRefs(HarnessStore harnessStore, Ambiance ambiance, String entityType) {
    if (harnessStore.getFiles().isExpression()) {
      return;
    }

    List<HarnessStoreFile> fileReferences = harnessStore.getFiles().getValue();
    if (isEmpty(fileReferences)) {
      throw new InvalidRequestException(
              format("Cannot find any file for %s, store kind: %s", entityType, harnessStore.getKind()));
    }
    if (fileReferences.size() > 1) {
      throw new InvalidRequestException(
              format("Only one file should be provided for %s, store kind: %s", entityType, harnessStore.getKind()));
    }

    validateSettingsFileByPath(harnessStore, ambiance, harnessStore.getFiles().getValue().get(0), entityType);
  }

  private void validateSettingsFileByPath(HarnessStore harnessStore, Ambiance ambiance, HarnessStoreFile file, String entityType) {
    if (ParameterField.isNull(file.getPath())) {
      throw new InvalidRequestException(
              format("File path not found for one for %s, store kind: %s", entityType, harnessStore.getKind()));
    }

    if (file.getPath().isExpression()) {
      return;
    }

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    Scope scope =
            ParameterRuntimeFiledHelper.getScopeParameterFieldFinalValue(file.getScope())
                    .orElseThrow(() -> new InvalidRequestException("Config file scope cannot be null or empty"));
    io.harness.beans.Scope fileScope = io.harness.beans.Scope.of(
            ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier(), scope);

    Optional<FileStoreNodeDTO> fileNode = fileStoreService.getByPath(fileScope.getAccountIdentifier(),
            fileScope.getOrgIdentifier(), fileScope.getProjectIdentifier(), file.getPath().getValue(), false);

    if (!fileNode.isPresent()) {
      throw new InvalidRequestException(
              format("%s file not found in File Store with ref : [%s]", entityType, file.getPath().getValue()));
    }
  }

  private void validateSettingsConnectorByRef(StoreConfig storeConfig, Ambiance ambiance, String entityType) {
    if (ParameterField.isNull(storeConfig.getConnectorReference())) {
      throw new InvalidRequestException(
              format("Connector ref field not present in %S, store kind: %s ", entityType, storeConfig.getKind()));
    }

    if (storeConfig.getConnectorReference().isExpression()) {
      return;
    }

    String connectorIdentifierRef = storeConfig.getConnectorReference().getValue();
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(connectorIdentifierRef,
            ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(connectorRef.getAccountIdentifier(),
            connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());
    if (!connectorDTO.isPresent()) {
      throw new InvalidRequestException(format("Connector not found with identifier: [%s]", connectorIdentifierRef));
    }

    ConnectorUtils.checkForConnectorValidityOrThrow(connectorDTO.get());
  }
}
