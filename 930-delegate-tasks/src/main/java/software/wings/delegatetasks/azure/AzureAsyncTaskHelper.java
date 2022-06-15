/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifact.ArtifactUtilities;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDescending;
import io.harness.azure.client.AzureAuthorizationClient;
import io.harness.azure.client.AzureComputeClient;
import io.harness.azure.client.AzureContainerRegistryClient;
import io.harness.azure.client.AzureKubernetesClient;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.kube.AzureKubeConfig;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.azure.response.AzureAcrTokenTaskResponse;
import io.harness.delegate.beans.azure.response.AzureClustersResponse;
import io.harness.delegate.beans.azure.response.AzureRegistriesResponse;
import io.harness.delegate.beans.azure.response.AzureRepositoriesResponse;
import io.harness.delegate.beans.azure.response.AzureResourceGroupsResponse;
import io.harness.delegate.beans.azure.response.AzureSubscriptionsResponse;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.task.artifacts.mappers.AcrRequestResponseMapper;
import io.harness.exception.AzureAKSException;
import io.harness.exception.AzureAuthenticationException;
import io.harness.exception.AzureContainerRegistryException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.expression.RegexFunctor;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.microsoft.azure.management.containerregistry.Registry;
import com.microsoft.azure.management.containerservice.KubernetesCluster;
import com.microsoft.azure.management.resources.Subscription;
import com.microsoft.azure.management.resources.fluentcore.arm.models.HasName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class AzureAsyncTaskHelper {
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private AzureAuthorizationClient azureAuthorizationClient;
  @Inject private AzureComputeClient azureComputeClient;
  @Inject private AzureContainerRegistryClient azureContainerRegistryClient;
  @Inject private AzureKubernetesClient azureKubernetesClient;

  private final String TAG_LABEL = "Tag#";

  public ConnectorValidationResult getConnectorValidationResult(
      List<EncryptedDataDetail> encryptedDataDetails, AzureConnectorDTO connectorDTO) {
    String errorMessage;

    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(connectorDTO.getCredential(),
        encryptedDataDetails, connectorDTO.getCredential().getAzureCredentialType(),
        connectorDTO.getAzureEnvironmentType(), secretDecryptionService);

    if (azureAuthorizationClient.validateAzureConnection(azureConfig)) {
      return ConnectorValidationResult.builder()
          .status(ConnectivityStatus.SUCCESS)
          .testedAt(System.currentTimeMillis())
          .build();
    }

    errorMessage = "Testing connection to Azure has timed out.";

    throw NestedExceptionUtils.hintWithExplanationException("Failed to validate connection for Azure connector",
        "Please check you Azure connector configuration.", new AzureAuthenticationException(errorMessage));
  }

  public AzureSubscriptionsResponse listSubscriptions(
      List<EncryptedDataDetail> encryptionDetails, AzureConnectorDTO azureConnector) {
    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(azureConnector.getCredential(),
        encryptionDetails, azureConnector.getCredential().getAzureCredentialType(),
        azureConnector.getAzureEnvironmentType(), secretDecryptionService);

    AzureSubscriptionsResponse response;
    response =
        AzureSubscriptionsResponse.builder()
            .subscriptions(azureComputeClient.listSubscriptions(azureConfig)
                               .stream()
                               .collect(Collectors.toMap(Subscription::subscriptionId, Subscription::displayName)))
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
    return response;
  }

  public AzureResourceGroupsResponse listResourceGroups(
      List<EncryptedDataDetail> encryptionDetails, AzureConnectorDTO azureConnector, String subscriptionId) {
    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(azureConnector.getCredential(),
        encryptionDetails, azureConnector.getCredential().getAzureCredentialType(),
        azureConnector.getAzureEnvironmentType(), secretDecryptionService);
    AzureResourceGroupsResponse response;
    response =
        AzureResourceGroupsResponse.builder()
            .resourceGroups(azureComputeClient.listResourceGroupsNamesBySubscriptionId(azureConfig, subscriptionId))
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
    return response;
  }

  public AzureRegistriesResponse listContainerRegistries(
      List<EncryptedDataDetail> encryptionDetails, AzureConnectorDTO azureConnector, String subscriptionId) {
    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(azureConnector.getCredential(),
        encryptionDetails, azureConnector.getCredential().getAzureCredentialType(),
        azureConnector.getAzureEnvironmentType(), secretDecryptionService);

    AzureRegistriesResponse response;
    response =
        AzureRegistriesResponse.builder()
            .containerRegistries(azureContainerRegistryClient.listContainerRegistries(azureConfig, subscriptionId)
                                     .stream()
                                     .map(Registry::name)
                                     .collect(Collectors.toList()))
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
    return response;
  }

  public AzureClustersResponse listClusters(List<EncryptedDataDetail> encryptionDetails,
      AzureConnectorDTO azureConnector, String subscriptionId, String resourceGroup) {
    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(azureConnector.getCredential(),
        encryptionDetails, azureConnector.getCredential().getAzureCredentialType(),
        azureConnector.getAzureEnvironmentType(), secretDecryptionService);

    AzureClustersResponse response;
    response =
        AzureClustersResponse.builder()
            .clusters(
                azureKubernetesClient.listKubernetesClusters(azureConfig, subscriptionId)
                    .stream()
                    .filter(kubernetesCluster -> kubernetesCluster.resourceGroupName().equalsIgnoreCase(resourceGroup))
                    .map(HasName::name)
                    .collect(Collectors.toList()))
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
    return response;
  }

  public KubernetesConfig getClusterConfig(AzureConnectorDTO azureConnector, String subscriptionId,
      String resourceGroup, String cluster, String namespace, List<EncryptedDataDetail> encryptedDataDetails) {
    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(azureConnector.getCredential(),
        encryptedDataDetails, azureConnector.getCredential().getAzureCredentialType(),
        azureConnector.getAzureEnvironmentType(), secretDecryptionService);

    KubernetesCluster k8sCluster =
        azureKubernetesClient.listKubernetesClusters(azureConfig, subscriptionId)
            .stream()
            .filter(kubernetesCluster
                -> kubernetesCluster.resourceGroupName().equalsIgnoreCase(resourceGroup)
                    && kubernetesCluster.name().equalsIgnoreCase(cluster))
            .findFirst()
            .orElseThrow(()
                             -> new AzureAKSException(String.format(
                                 "AKS Cluster %s has not been found for subscription %s and resourceGroup %s ", cluster,
                                 subscriptionId, resourceGroup)));

    return getKubernetesConfigK8sCluster(namespace, k8sCluster);
  }

  public AzureRepositoriesResponse listRepositories(List<EncryptedDataDetail> encryptionDetails,
      AzureConnectorDTO azureConnector, String subscriptionId, String containerRegistry) {
    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(azureConnector.getCredential(),
        encryptionDetails, azureConnector.getCredential().getAzureCredentialType(),
        azureConnector.getAzureEnvironmentType(), secretDecryptionService);

    AzureRepositoriesResponse response;
    Registry registry =
        azureContainerRegistryClient
            .findFirstContainerRegistryByNameOnSubscription(azureConfig, subscriptionId, containerRegistry)
            .orElseThrow(
                ()
                    -> NestedExceptionUtils.hintWithExplanationException(
                        format("Not found Azure container registry by name: %s, subscription id: %s", containerRegistry,
                            subscriptionId),
                        "Please check if the container registry and subscription values are properly configured.",
                        new AzureAuthenticationException("Failed to retrieve container registry")));

    response = AzureRepositoriesResponse.builder()
                   .repositories(azureContainerRegistryClient.listRepositories(
                       azureConfig, subscriptionId, registry.loginServerUrl()))
                   .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                   .build();
    return response;
  }

  public List<BuildDetailsInternal> getImageTags(
      AzureConfig azureConfig, String subscriptionId, String containerRegistry, String repository) {
    Registry registry =
        azureContainerRegistryClient
            .findFirstContainerRegistryByNameOnSubscription(azureConfig, subscriptionId, containerRegistry)
            .orElseThrow(
                ()
                    -> NestedExceptionUtils.hintWithExplanationException(
                        format("Not found Azure container registry by name: %s, subscription id: %s", containerRegistry,
                            subscriptionId),
                        "Please check if the container registry and subscription values are properly configured.",
                        new AzureAuthenticationException("Failed to retrieve container registry")));

    String registryUrl = registry.loginServerUrl().toLowerCase();
    String imageUrl = registryUrl + "/" + ArtifactUtilities.trimSlashforwardChars(repository);
    List<String> tags = azureContainerRegistryClient.listRepositoryTags(azureConfig, registryUrl, repository);
    return tags.stream()
        .map(tag -> {
          Map<String, String> metadata = new HashMap<>();
          metadata.put(ArtifactMetadataKeys.IMAGE, format("%s:%s", imageUrl, tag));
          metadata.put(ArtifactMetadataKeys.TAG, tag);
          metadata.put(ArtifactMetadataKeys.REGISTRY_HOSTNAME, registryUrl);
          return BuildDetailsInternal.builder()
              .number(tag)
              .buildUrl(format("%s:%s", imageUrl, tag))
              .metadata(metadata)
              .uiDisplayName(format("%s %s", TAG_LABEL, tag))
              .build();
        })
        .collect(Collectors.toList());
  }

  public BuildDetailsInternal getLastSuccessfulBuildFromRegex(
      AzureConfig azureConfig, String subscription, String registry, String repository, String tagRegex) {
    try {
      Pattern.compile(tagRegex);
    } catch (PatternSyntaxException e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check tagRegex field in ACR artifact configuration.",
          String.format("TagRegex field contains an invalid regex value '%s'.", tagRegex),
          new AzureContainerRegistryException(e.getMessage()));
    }

    List<BuildDetailsInternal> builds = getImageTags(azureConfig, subscription, registry, repository);
    builds = builds.stream()
                 .filter(build -> new RegexFunctor().match(tagRegex, build.getNumber()))
                 .sorted(new BuildDetailsInternalComparatorDescending())
                 .collect(Collectors.toList());

    if (builds.isEmpty()) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check tagRegex field in ACR artifact configuration.",
          String.format(
              "Could not find any tags that match regex [%s] for ACR repository [%s] for subscription [%s] in registry [%s].",
              tagRegex, repository, subscription, registry),
          new AzureContainerRegistryException(
              String.format("Could not find an artifact tag that matches tagRegex '%s'", tagRegex)));
    }
    return builds.get(0);
  }

  public BuildDetailsInternal verifyBuildNumber(
      AzureConfig azureConfig, String subscription, String registry, String repository, String tag) {
    List<BuildDetailsInternal> builds = getImageTags(azureConfig, subscription, registry, repository);
    builds = builds.stream().filter(build -> build.getNumber().equals(tag)).collect(Collectors.toList());

    if (builds.isEmpty()) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check your ACR repository for artifact tag existence.",
          String.format(
              "Did not find any artifacts for tag [%s] in ACR repository [%s] for subscription [%s] in registry [%s].",
              tag, repository, subscription, registry),
          new AzureContainerRegistryException(String.format("Artifact tag '%s' not found.", tag)));
    } else if (builds.size() == 1) {
      return builds.get(0);
    }

    throw NestedExceptionUtils.hintWithExplanationException(
        "Please check your ACR repository for artifacts with same tag.",
        String.format(
            "Found multiple artifacts for tag [%s] in Artifactory repository [%s] for subscription [%s] in registry [%s].",
            tag, repository, subscription, registry),
        new AzureContainerRegistryException(
            String.format("Found multiple artifact tags '%s', but expected only one.", tag)));
  }

  private KubernetesConfig getKubernetesConfigK8sCluster(String namespace, KubernetesCluster k8sCluster) {
    try {
      AzureKubeConfig azureKubeConfig =
          new ObjectMapper(new YAMLFactory())
              .readValue(new String(k8sCluster.userKubeConfigContent()), AzureKubeConfig.class);
      return KubernetesConfig.builder()
          .namespace(namespace)
          .masterUrl(azureKubeConfig.getClusters().get(0).getCluster().getServer())
          .caCert(azureKubeConfig.getClusters().get(0).getCluster().getCertificateAuthorityData().toCharArray())
          .username(azureKubeConfig.getUsers().get(0).getName().toCharArray())
          .clientCert(azureKubeConfig.getUsers().get(0).getUser().getClientCertificateData().toCharArray())
          .clientKey(azureKubeConfig.getUsers().get(0).getUser().getClientKeyData().toCharArray())
          .build();
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          format("Kube Config could not be read from cluster %s ", k8sCluster.name()),
          "Please check your Azure permissions", new AzureAKSException(e.getMessage(), WingsException.USER, e));
    }
  }

  public AzureAcrTokenTaskResponse getServicePrincipalCertificateAcrLoginToken(
      String registry, List<EncryptedDataDetail> encryptionDetails, AzureConnectorDTO azureConnector) {
    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(azureConnector.getCredential(),
        encryptionDetails, azureConnector.getCredential().getAzureCredentialType(),
        azureConnector.getAzureEnvironmentType(), secretDecryptionService);

    String azureAccessToken = azureAuthorizationClient.getUserAccessToken(azureConfig).getAccessToken();
    String refreshToken = azureContainerRegistryClient.getAcrRefreshToken(registry, azureAccessToken);

    return AzureAcrTokenTaskResponse.builder()
        .token(refreshToken)
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .build();
  }
}
