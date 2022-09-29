package io.harness.delegate.task.artifacts.azuremachineimage;

import static io.harness.delegate.task.artifacts.mappers.AzureMachineImageResponseMapper.toAzureMachineImageResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.azuremachineimage.service.AzureMachineImageRegistryService;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.mappers.AzureMachineImageResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.beans.AzureResourceGroup;
import software.wings.service.impl.AcrBuildServiceImpl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.CDC)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
public class AzureMachineImageTaskHandler extends DelegateArtifactTaskHandler<AzureMachineImageDelegateRequest> {
  private final SecretDecryptionService secretDecryptionService;
  private final AzureMachineImageRegistryService azureMachineImageRegistryService;
  private final AcrBuildServiceImpl acrBuildService;
  public ArtifactTaskExecutionResponse getResourceGroups(AzureMachineImageDelegateRequest attributesRequest) {
    AzureConfig azureConfig =
        AzureMachineImageResponseMapper.toAzureInternalConfig(attributesRequest, secretDecryptionService);

    software.wings.beans.AzureConfig wingsazureConfig = AzureMachineImageResponseMapper.configMapper(azureConfig);
    List<AzureResourceGroup> azureResourceGroups = acrBuildService.listResourceGroups(
        wingsazureConfig, attributesRequest.getEncryptedDataDetails(), attributesRequest.getSubscriptionId());
    List<AzureMachineImageDelegateResponse> azureMachineImageDelegateResponses =
        azureResourceGroups.stream().map(group -> toAzureMachineImageResponse(group)).collect(Collectors.toList());
    return getSuccessTaskExecutionResponse(azureMachineImageDelegateResponses);
  }
  public void decryptRequestDTOs(AzureMachineImageDelegateRequest azureMachineImageDelegateRequest) {
    if (azureMachineImageDelegateRequest.getAzureConnectorDTO().getCredential().getConfig()
            instanceof AzureManualDetailsDTO) {
      AzureManualDetailsDTO azureManualDetailsDTO =
          (AzureManualDetailsDTO) azureMachineImageDelegateRequest.getAzureConnectorDTO().getCredential().getConfig();
      if (azureManualDetailsDTO != null) {
        secretDecryptionService.decrypt(azureManualDetailsDTO.getAuthDTO().getCredentials(),
            azureMachineImageDelegateRequest.getEncryptedDataDetails());
      }
    }
  }
  private ArtifactTaskExecutionResponse getSuccessTaskExecutionResponse(
      List<AzureMachineImageDelegateResponse> responseList) {
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(responseList)
        .isArtifactSourceValid(true)
        .isArtifactServerValid(true)
        .build();
  }
}
