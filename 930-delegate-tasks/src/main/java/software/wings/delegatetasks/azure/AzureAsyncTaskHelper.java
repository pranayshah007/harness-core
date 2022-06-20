/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure;

import static io.harness.azure.model.AzureConstants.DEPLOYMENT_SLOT_FULL_NAME_PATTERN;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_SLOT_NON_PRODUCTION_TYPE;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_SLOT_PRODUCTION_TYPE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

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
import io.harness.azure.client.AzureManagementClient;
import io.harness.azure.model.AzureAuthenticationType;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.kube.AzureKubeConfig;
import io.harness.azure.model.tag.TagDetails;
import io.harness.azure.utility.AzureUtils;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.data.encoding.EncodingUtils;
import io.harness.delegate.beans.azure.response.AzureAcrTokenTaskResponse;
import io.harness.delegate.beans.azure.response.AzureClustersResponse;
import io.harness.delegate.beans.azure.response.AzureDeploymentSlotResponse;
import io.harness.delegate.beans.azure.response.AzureDeploymentSlotsResponse;
import io.harness.delegate.beans.azure.response.AzureRegistriesResponse;
import io.harness.delegate.beans.azure.response.AzureRepositoriesResponse;
import io.harness.delegate.beans.azure.response.AzureResourceGroupsResponse;
import io.harness.delegate.beans.azure.response.AzureSubscriptionsResponse;
import io.harness.delegate.beans.azure.response.AzureTagsResponse;
import io.harness.delegate.beans.azure.response.AzureWebAppNamesResponse;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.task.artifacts.mappers.AcrRequestResponseMapper;
import io.harness.exception.AzureAKSException;
import io.harness.exception.AzureAuthenticationException;
import io.harness.exception.AzureContainerRegistryException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.expression.RegexFunctor;
import io.harness.k8s.model.KubernetesAzureConfig;
import io.harness.k8s.model.KubernetesClusterAuthType;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.azure.AzureIdentityAccessTokenResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.microsoft.azure.management.appservice.DeploymentSlot;
import com.microsoft.azure.management.containerregistry.Registry;
import com.microsoft.azure.management.resources.Subscription;
import com.microsoft.azure.management.resources.fluentcore.arm.models.HasName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
@Singleton
public class AzureAsyncTaskHelper {
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private AzureAuthorizationClient azureAuthorizationClient;
  @Inject private AzureComputeClient azureComputeClient;
  @Inject private AzureContainerRegistryClient azureContainerRegistryClient;
  @Inject private AzureKubernetesClient azureKubernetesClient;
  @Inject private AzureManagementClient azureManagementClient;

  private final String TAG_LABEL = "Tag#";
  private final int ITEM_LOG_LIMIT = 30;
  private final String SCOPE_DEFAULT_SUFFIX = "/.default";

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
    log.info(format("Fetching Azure subscriptions for %s user type",
        azureConnector.getCredential().getAzureCredentialType().getDisplayName()));
    log.trace(format("User: \n%s", azureConnector.toString()));

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

    log.info(format("Retrieved %d subscriptions (listing first %d only): %s", response.getSubscriptions().size(),
        ITEM_LOG_LIMIT,
        response.getSubscriptions().keySet().stream().limit(ITEM_LOG_LIMIT).collect(Collectors.toList()).toString()));

    return response;
  }

  public AzureResourceGroupsResponse listResourceGroups(
      List<EncryptedDataDetail> encryptionDetails, AzureConnectorDTO azureConnector, String subscriptionId) {
    log.info(format("Fetching Azure resource groups for subscription %s for %s user type", subscriptionId,
        azureConnector.getCredential().getAzureCredentialType().getDisplayName()));

    log.trace(format("User: \n%s", azureConnector.toString()));

    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(azureConnector.getCredential(),
        encryptionDetails, azureConnector.getCredential().getAzureCredentialType(),
        azureConnector.getAzureEnvironmentType(), secretDecryptionService);
    AzureResourceGroupsResponse response;
    response =
        AzureResourceGroupsResponse.builder()
            .resourceGroups(azureComputeClient.listResourceGroupsNamesBySubscriptionId(azureConfig, subscriptionId))
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();

    log.info(format("Retrieved %d resource groups (listing first %d only): %s", response.getResourceGroups().size(),
        ITEM_LOG_LIMIT,
        response.getResourceGroups().stream().limit(ITEM_LOG_LIMIT).collect(Collectors.toList()).toString()));

    return response;
  }

  public AzureWebAppNamesResponse listWebAppNames(List<EncryptedDataDetail> encryptionDetails,
      AzureConnectorDTO azureConnector, String subscriptionId, String resourceGroup) {
    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(azureConnector.getCredential(),
        encryptionDetails, azureConnector.getCredential().getAzureCredentialType(),
        azureConnector.getAzureEnvironmentType(), secretDecryptionService);

    AzureWebAppNamesResponse response;
    response = AzureWebAppNamesResponse.builder()
                   .webAppNames(azureComputeClient.listWebAppNamesBySubscriptionIdAndResourceGroup(
                       azureConfig, subscriptionId, resourceGroup))
                   .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                   .build();
    return response;
  }

  public AzureDeploymentSlotsResponse listDeploymentSlots(List<EncryptedDataDetail> encryptionDetails,
      AzureConnectorDTO azureConnector, String subscriptionId, String resourceGroup, String webAppName) {
    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(azureConnector.getCredential(),
        encryptionDetails, azureConnector.getCredential().getAzureCredentialType(),
        azureConnector.getAzureEnvironmentType(), secretDecryptionService);

    AzureDeploymentSlotsResponse response;
    List<DeploymentSlot> webAppDeploymentSlots =
        azureComputeClient.listWebAppDeploymentSlots(azureConfig, subscriptionId, resourceGroup, webAppName);
    List<AzureDeploymentSlotResponse> deploymentSlotsData = toDeploymentSlotData(webAppDeploymentSlots, webAppName);

    response = AzureDeploymentSlotsResponse.builder()
                   .deploymentSlots(addProductionDeploymentSlotData(deploymentSlotsData, webAppName))
                   .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                   .build();
    return response;
  }

  private List<AzureDeploymentSlotResponse> addProductionDeploymentSlotData(
      List<AzureDeploymentSlotResponse> deploymentSlots, String webAppName) {
    deploymentSlots.add(
        AzureDeploymentSlotResponse.builder().name(webAppName).type(DEPLOYMENT_SLOT_PRODUCTION_TYPE).build());
    return deploymentSlots;
  }

  @NotNull
  private List<AzureDeploymentSlotResponse> toDeploymentSlotData(
      List<DeploymentSlot> deploymentSlots, String webAppName) {
    return deploymentSlots.stream()
        .map(DeploymentSlot::name)
        .map(slotName
            -> AzureDeploymentSlotResponse.builder()
                   .name(format(DEPLOYMENT_SLOT_FULL_NAME_PATTERN, webAppName, slotName))
                   .type(DEPLOYMENT_SLOT_NON_PRODUCTION_TYPE)
                   .build())
        .collect(Collectors.toList());
  }

  public AzureRegistriesResponse listContainerRegistries(
      List<EncryptedDataDetail> encryptionDetails, AzureConnectorDTO azureConnector, String subscriptionId) {
    log.info(format("Fetching Azure Container Registries for subscription %s for %s user type", subscriptionId,
        azureConnector.getCredential().getAzureCredentialType().getDisplayName()));

    log.trace(format("User: \n%s", azureConnector.toString()));

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

    log.info(format("Retrieved %d container registries (listing first %d only): %s",
        response.getContainerRegistries().size(), ITEM_LOG_LIMIT,
        response.getContainerRegistries().stream().limit(ITEM_LOG_LIMIT).collect(Collectors.toList()).toString()));

    return response;
  }

  public AzureClustersResponse listClusters(List<EncryptedDataDetail> encryptionDetails,
      AzureConnectorDTO azureConnector, String subscriptionId, String resourceGroup) {
    log.info(format("Fetching Azure Kubernetes Clusters for subscription %s, for resource group %s, for %s user type",
        subscriptionId, resourceGroup, azureConnector.getCredential().getAzureCredentialType().getDisplayName()));

    log.trace(format("User: \n%s", azureConnector.toString()));

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

    log.info(format("Retrieved %d kubernetes clusters (listing first %d only): %s", response.getClusters().size(),
        ITEM_LOG_LIMIT, response.getClusters().stream().limit(ITEM_LOG_LIMIT).collect(Collectors.toList()).toString()));

    return response;
  }

  public AzureTagsResponse listTags(
      List<EncryptedDataDetail> encryptionDetails, AzureConnectorDTO azureConnector, String subscriptionId) {
    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(azureConnector.getCredential(),
        encryptionDetails, azureConnector.getCredential().getAzureCredentialType(),
        azureConnector.getAzureEnvironmentType(), secretDecryptionService);

    return AzureTagsResponse.builder()
        .tags(azureManagementClient.listTags(azureConfig, subscriptionId)
                  .stream()
                  .map(TagDetails::getTagName)
                  .collect(Collectors.toList()))
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .build();
  }

  public KubernetesConfig getClusterConfig(AzureConnectorDTO azureConnector, String subscriptionId,
      String resourceGroup, String cluster, String namespace, List<EncryptedDataDetail> encryptedDataDetails,
      boolean useClusterAdminCredentials) {
    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(azureConnector.getCredential(),
        encryptedDataDetails, azureConnector.getCredential().getAzureCredentialType(),
        azureConnector.getAzureEnvironmentType(), secretDecryptionService);

    return getKubernetesConfigK8sCluster(
        azureConfig, subscriptionId, resourceGroup, cluster, namespace, useClusterAdminCredentials);
  }

  public AzureRepositoriesResponse listRepositories(List<EncryptedDataDetail> encryptionDetails,
      AzureConnectorDTO azureConnector, String subscriptionId, String containerRegistry) {
    log.info(format(
        "Fetching Azure Container Registry repositories for subscription %s, for registry %s, for %s user type",
        subscriptionId, containerRegistry, azureConnector.getCredential().getAzureCredentialType().getDisplayName()));

    log.trace(format("User: \n%s", azureConnector.toString()));

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

    log.info(format("Retrieved %d repositories (listing first %d only): %s", response.getRepositories().size(),
        ITEM_LOG_LIMIT,
        response.getRepositories().stream().limit(ITEM_LOG_LIMIT).collect(Collectors.toList()).toString()));

    return response;
  }

  public List<BuildDetailsInternal> getImageTags(
      AzureConfig azureConfig, String subscriptionId, String containerRegistry, String repository) {
    log.info(format(
        "Fetching Azure Container Registry Repository tags for subscription %s, for registry %s, for repository %s, for %s user type",
        subscriptionId, containerRegistry, repository, azureConfig.getAzureAuthenticationType().name()));
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

    log.info(
        format("Registry URL: [%s]. Image URL: [%s]. Found %d tags (listing first %d only): %s", registryUrl, imageUrl,
            tags.size(), ITEM_LOG_LIMIT, tags.stream().limit(ITEM_LOG_LIMIT).collect(Collectors.toList()).toString()));

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
    log.info(format("Fetching image tag from subscription %s registry %s and repository %s based on regex %s",
        subscription, registry, repository, tagRegex));
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
    log.info(format("Fetching image tag from subscription %s registry %s and repository %s based on tag %s",
        subscription, registry, repository, tag));
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

  private KubernetesConfig getKubernetesConfigK8sCluster(AzureConfig azureConfig, String subscriptionId,
      String resourceGroup, String cluster, String namespace, boolean shouldGetAdminCredentials) {
    try {
      log.info(format(
          "Getting AKS kube config [subscription: %s] [resourceGroup: %s] [cluster: %s] [namespace: %s] [credentials: %s]",
          subscriptionId, resourceGroup, cluster, namespace, shouldGetAdminCredentials ? "admin" : "user"));

      String kubeConfigContent =
          getKubeConfigContent(azureConfig, subscriptionId, resourceGroup, cluster, shouldGetAdminCredentials);

      log.trace(format("Cluster credentials: \n %s", kubeConfigContent));

      AzureKubeConfig azureKubeConfig = getAzureKubeConfig(kubeConfigContent);

      verifyAzureKubeConfig(azureKubeConfig);

      if (azureKubeConfig.getUsers().get(0).getUser().getAuthProvider() != null) {
        azureKubeConfig.setAadToken(fetchAksAADToken(azureConfig, azureKubeConfig));
      }

      return getKubernetesConfig(azureKubeConfig, namespace);

    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException(format("Kube Config could not be read from cluster"),
          "Please check your Azure permissions", new AzureAKSException(e.getMessage(), WingsException.USER, e));
    }
  }

  private String fetchAksAADToken(AzureConfig azureConfig, AzureKubeConfig azureKubeConfig) {
    StringBuilder scope =
        new StringBuilder(azureKubeConfig.getUsers().get(0).getUser().getAuthProvider().getConfig().getApiServerId());

    if (azureConfig.getAzureAuthenticationType() == AzureAuthenticationType.SERVICE_PRINCIPAL_SECRET
        || azureConfig.getAzureAuthenticationType() == AzureAuthenticationType.SERVICE_PRINCIPAL_CERT) {
      scope.append(SCOPE_DEFAULT_SUFFIX);
    }

    AzureIdentityAccessTokenResponse azureIdentityAccessTokenResponse =
        azureAuthorizationClient.getUserAccessToken(azureConfig, scope.toString());
    return azureIdentityAccessTokenResponse.getAccessToken();
  }

  private AzureKubeConfig getAzureKubeConfig(String kubeConfigContent) throws JsonProcessingException {
    return new ObjectMapper(new YAMLFactory()).readValue(kubeConfigContent, AzureKubeConfig.class);
  }

  private String getKubeConfigContent(AzureConfig azureConfig, String subscription, String resourceGroup,
      String clusterName, boolean shouldGetAdminCredentials) {
    String aksClusterCredentialsBase64 = azureKubernetesClient.getClusterCredentials(azureConfig,
        format("Bearer %s", fetchAzureUserAccessToken(azureConfig)), subscription, resourceGroup, clusterName,
        shouldGetAdminCredentials);
    return new String(EncodingUtils.decodeBase64(aksClusterCredentialsBase64));
  }

  private String fetchAzureUserAccessToken(AzureConfig azureConfig) {
    String scope =
        null; // for ManagedIdentity we leave scope null as it is then defaulted to what the Azure SDK has defined
    if (azureConfig.getAzureAuthenticationType() == AzureAuthenticationType.SERVICE_PRINCIPAL_SECRET
        || azureConfig.getAzureAuthenticationType() == AzureAuthenticationType.SERVICE_PRINCIPAL_CERT) {
      scope = AzureUtils.AUTH_SCOPE;
    }
    return azureAuthorizationClient.getUserAccessToken(azureConfig, scope).getAccessToken();
  }

  private void verifyAzureKubeConfig(AzureKubeConfig azureKubeConfig) {
    if (isEmpty(azureKubeConfig.getClusters().get(0).getCluster().getServer())) {
      throw new AzureAKSException("Server url was not found in the kube config content!!!");
    }

    if (isEmpty(azureKubeConfig.getClusters().get(0).getCluster().getCertificateAuthorityData())) {
      throw new AzureAKSException("CertificateAuthorityData was not found in the kube config content!!!");
    }

    if (isEmpty(azureKubeConfig.getUsers().get(0).getName())) {
      throw new AzureAKSException("Cluster user name was not found in the kube config content!!!");
    }

    if (azureKubeConfig.getUsers().get(0).getUser().getAuthProvider() != null) {
      if (isEmpty(azureKubeConfig.getClusters().get(0).getName())) {
        throw new AzureAKSException("Cluster name was not found in the kube config content!!!");
      }

      if (isEmpty(azureKubeConfig.getCurrentContext())) {
        throw new AzureAKSException("Current context was not found in the kube config content!!!");
      }

      if (azureKubeConfig.getUsers().get(0).getUser().getAuthProvider().getConfig() == null) {
        throw new AzureAKSException("AuthProvider was not found in the kube config content!!!");
      }

      if (isEmpty(azureKubeConfig.getUsers().get(0).getUser().getAuthProvider().getConfig().getApiServerId())) {
        throw new AzureAKSException("ApiServerId was not found in the kube config content!!!");
      }

      if (isEmpty(azureKubeConfig.getUsers().get(0).getUser().getAuthProvider().getConfig().getClientId())) {
        throw new AzureAKSException("ClientId was not found in the kube config content!!!");
      }

      if (isEmpty(azureKubeConfig.getUsers().get(0).getUser().getAuthProvider().getConfig().getConfigMode())) {
        throw new AzureAKSException("ConfigMode was not found in the kube config content!!!");
      }

      if (isEmpty(azureKubeConfig.getUsers().get(0).getUser().getAuthProvider().getConfig().getTenantId())) {
        throw new AzureAKSException("TenantId was not found in the kube config content!!!");
      }

      if (isEmpty(azureKubeConfig.getUsers().get(0).getUser().getAuthProvider().getConfig().getEnvironment())) {
        throw new AzureAKSException("Environment was not found in the kube config content!!!");
      }
    } else {
      if (isEmpty(azureKubeConfig.getUsers().get(0).getUser().getClientCertificateData())) {
        throw new AzureAKSException("ClientCertificateData was not found in the kube config content!!!");
      }

      if (isEmpty(azureKubeConfig.getUsers().get(0).getUser().getClientKeyData())) {
        throw new AzureAKSException("ClientKeyData was not found in the kube config content!!!");
      }
    }

    log.info("Azure Kube Config is valid.");
  }

  private KubernetesConfig getKubernetesConfig(AzureKubeConfig azureKubeConfig, String namespace) {
    if (isNotEmpty(azureKubeConfig.getAadToken())) {
      KubernetesAzureConfig kubernetesAzureConfig =
          KubernetesAzureConfig.builder()
              .clusterName(azureKubeConfig.getClusters().get(0).getName())
              .clusterUser(azureKubeConfig.getUsers().get(0).getName())
              .currentContext(azureKubeConfig.getCurrentContext())
              .apiServerId(azureKubeConfig.getUsers().get(0).getUser().getAuthProvider().getConfig().getApiServerId())
              .clientId(azureKubeConfig.getUsers().get(0).getUser().getAuthProvider().getConfig().getClientId())
              .configMode(azureKubeConfig.getUsers().get(0).getUser().getAuthProvider().getConfig().getConfigMode())
              .tenantId(azureKubeConfig.getUsers().get(0).getUser().getAuthProvider().getConfig().getTenantId())
              .environment(azureKubeConfig.getUsers().get(0).getUser().getAuthProvider().getConfig().getEnvironment())
              .aadIdToken(azureKubeConfig.getAadToken())
              .build();
      return KubernetesConfig.builder()
          .namespace(namespace)
          .masterUrl(azureKubeConfig.getClusters().get(0).getCluster().getServer())
          .caCert(azureKubeConfig.getClusters().get(0).getCluster().getCertificateAuthorityData().toCharArray())
          .username(azureKubeConfig.getUsers().get(0).getName().toCharArray())
          .azureConfig(kubernetesAzureConfig)
          .authType(KubernetesClusterAuthType.AZURE_OAUTH)
          .build();
    } else {
      return KubernetesConfig.builder()
          .namespace(namespace)
          .masterUrl(azureKubeConfig.getClusters().get(0).getCluster().getServer())
          .caCert(azureKubeConfig.getClusters().get(0).getCluster().getCertificateAuthorityData().toCharArray())
          .username(azureKubeConfig.getUsers().get(0).getName().toCharArray())
          .clientCert(azureKubeConfig.getUsers().get(0).getUser().getClientCertificateData().toCharArray())
          .clientKey(azureKubeConfig.getUsers().get(0).getUser().getClientKeyData().toCharArray())
          .build();
    }
  }

  public AzureAcrTokenTaskResponse getAcrLoginToken(
      String registry, List<EncryptedDataDetail> encryptionDetails, AzureConnectorDTO azureConnector) {
    log.info(format("Fetching ACR login token for registry %s", registry));

    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(azureConnector.getCredential(),
        encryptionDetails, azureConnector.getCredential().getAzureCredentialType(),
        azureConnector.getAzureEnvironmentType(), secretDecryptionService);

    String azureAccessToken;
    if (azureConfig.getAzureAuthenticationType() == AzureAuthenticationType.SERVICE_PRINCIPAL_CERT) {
      azureAccessToken =
          azureAuthorizationClient.getUserAccessToken(azureConfig, AzureUtils.AUTH_SCOPE).getAccessToken();
    } else {
      // only MSI connection will/should reach here
      azureAccessToken = azureAuthorizationClient.getUserAccessToken(azureConfig, null).getAccessToken();
    }

    String refreshToken = azureContainerRegistryClient.getAcrRefreshToken(registry, azureAccessToken);

    return AzureAcrTokenTaskResponse.builder()
        .token(refreshToken)
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .build();
  }
}
