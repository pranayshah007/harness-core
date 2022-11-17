package io.harness.delegate.task.artifacts.azuremachineimage;

import static io.harness.delegate.task.artifacts.mappers.AzureMachineImageResponseMapper.toAzureMachineImageResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDescending;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.azure.AcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.mappers.AcrRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.delegatetasks.azure.AzureAsyncTaskHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
public class AzureMachineImageTaskHandler extends DelegateArtifactTaskHandler<AzureMachineImageDelegateRequest> {
  private final SecretDecryptionService secretDecryptionService;
  private final AzureAsyncTaskHelper azureAsyncTaskHelper;
  public ArtifactTaskExecutionResponse getBuilds(AzureMachineImageDelegateRequest attributesRequest) {
    AcrArtifactDelegateRequest acrArtifactDelegateRequest =
        AcrArtifactDelegateRequest.builder()
            .azureConnectorDTO(attributesRequest.getAzureConnectorDTO())
            .encryptedDataDetails(attributesRequest.getEncryptedDataDetails())
            .build();
    AzureConfig azureConfig =
        AcrRequestResponseMapper.toAzureInternalConfig(acrArtifactDelegateRequest, secretDecryptionService);
    List<BuildDetailsInternal> builds = azureAsyncTaskHelper.listImageDefinitionVersions(azureConfig,
        attributesRequest.getSubscriptionId(), attributesRequest.getResourceGroup(), attributesRequest.getGalleryName(),
        attributesRequest.getImageDefinition());

    List<AzureMachineImageDelegateResponse> azureMachineImageDelegateResponses =
        builds.stream()
            .sorted(new BuildDetailsInternalComparatorDescending())
            .map(build -> toAzureMachineImageResponse(build))
            .collect(Collectors.toList());
    return getSuccessTaskExecutionResponse(azureMachineImageDelegateResponses);
  }

  public ArtifactTaskExecutionResponse getLastSuccesfulBuild(AzureMachineImageDelegateRequest attributesRequest) {
    BuildDetailsInternal lastSuccessfulBuild;
    AcrArtifactDelegateRequest acrArtifactDelegateRequest =
        AcrArtifactDelegateRequest.builder()
            .azureConnectorDTO(attributesRequest.getAzureConnectorDTO())
            .encryptedDataDetails(attributesRequest.getEncryptedDataDetails())
            .build();
    AzureConfig azureConfig =
        AcrRequestResponseMapper.toAzureInternalConfig(acrArtifactDelegateRequest, secretDecryptionService);

    if (isRegex(attributesRequest)) {
      lastSuccessfulBuild = azureAsyncTaskHelper.getLastSuccessfulBuildFromRegexMachineImage(azureConfig,
          attributesRequest.getSubscriptionId(), attributesRequest.getResourceGroup(),
          attributesRequest.getGalleryName(), attributesRequest.getImageDefinition(),
          attributesRequest.getVersionRegex());

    } else {
      lastSuccessfulBuild = azureAsyncTaskHelper.verifyBuildNumberMachineImage(azureConfig,
          attributesRequest.getSubscriptionId(), attributesRequest.getResourceGroup(),
          attributesRequest.getGalleryName(), attributesRequest.getImageDefinition(), attributesRequest.getVersion());
    }

    return getSuccessTaskExecutionResponse(Collections.singletonList(toAzureMachineImageResponse(lastSuccessfulBuild)));
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
  boolean isRegex(AzureMachineImageDelegateRequest artifactDelegateRequest) {
    return StringUtils.isBlank(artifactDelegateRequest.getVersion());
  }
}
