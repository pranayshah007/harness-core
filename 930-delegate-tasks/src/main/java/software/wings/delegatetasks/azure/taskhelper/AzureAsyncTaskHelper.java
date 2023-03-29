/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure;

import static io.harness.azure.model.AzureConstants.AZURE_AUTH_PLUGIN_INSTALL_HINT;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_SLOT_FULL_NAME_PATTERN;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_SLOT_NON_PRODUCTION_TYPE;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_SLOT_PRODUCTION_TYPE;
import static io.harness.azure.model.AzureConstants.KUBECFG_ARGS_AZURE_CLI;
import static io.harness.azure.model.AzureConstants.KUBECFG_ARGS_CLIENT_ID;
import static io.harness.azure.model.AzureConstants.KUBECFG_ARGS_CLIENT_SECRET;
import static io.harness.azure.model.AzureConstants.KUBECFG_ARGS_ENV;
import static io.harness.azure.model.AzureConstants.KUBECFG_ARGS_GET_TOKEN;
import static io.harness.azure.model.AzureConstants.KUBECFG_ARGS_LOGIN;
import static io.harness.azure.model.AzureConstants.KUBECFG_ARGS_MSI;
import static io.harness.azure.model.AzureConstants.KUBECFG_ARGS_SERVER_ID;
import static io.harness.azure.model.AzureConstants.KUBECFG_ARGS_SPN;
import static io.harness.azure.model.AzureConstants.KUBECFG_ARGS_TENANT_ID;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.k8s.K8sConstants.AZURE_AUTH_PLUGIN_BINARY;
import static io.harness.k8s.model.kubeconfig.KubeConfigAuthPluginHelper.isExecAuthPluginBinaryAvailable;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifact.ArtifactUtilities;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDescending;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.client.AzureAuthorizationClient;
import io.harness.azure.client.AzureComputeClient;
import io.harness.azure.client.AzureContainerRegistryClient;
import io.harness.azure.client.AzureKubernetesClient;
import io.harness.azure.client.AzureManagementClient;
import io.harness.azure.model.AzureAuthenticationType;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureKubeconfigFormat;
import io.harness.azure.model.VirtualMachineData;
import io.harness.azure.model.kube.AzureKubeConfig;
import io.harness.azure.model.kube.UserConfig;
import io.harness.azure.model.tag.TagDetails;
import io.harness.azure.utility.AzureUtils;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.data.encoding.EncodingUtils;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.azure.AzureConfigContext;
import io.harness.delegate.beans.azure.ManagementGroupData;
import io.harness.delegate.beans.azure.response.AzureAcrTokenTaskResponse;
import io.harness.delegate.beans.azure.response.AzureClustersResponse;
import io.harness.delegate.beans.azure.response.AzureDeploymentSlotResponse;
import io.harness.delegate.beans.azure.response.AzureDeploymentSlotsResponse;
import io.harness.delegate.beans.azure.response.AzureHostResponse;
import io.harness.delegate.beans.azure.response.AzureHostsResponse;
import io.harness.delegate.beans.azure.response.AzureImageGalleriesResponse;
import io.harness.delegate.beans.azure.response.AzureLocationsResponse;
import io.harness.delegate.beans.azure.response.AzureMngGroupsResponse;
import io.harness.delegate.beans.azure.response.AzureRegistriesResponse;
import io.harness.delegate.beans.azure.response.AzureRepositoriesResponse;
import io.harness.delegate.beans.azure.response.AzureResourceGroupsResponse;
import io.harness.delegate.beans.azure.response.AzureSubscriptionsResponse;
import io.harness.delegate.beans.azure.response.AzureTagsResponse;
import io.harness.delegate.beans.azure.response.AzureWebAppNamesResponse;
import io.harness.delegate.task.artifacts.mappers.AcrRequestResponseMapper;
import io.harness.exception.AzureAKSException;
import io.harness.exception.AzureAuthenticationException;
import io.harness.exception.AzureContainerRegistryException;
import io.harness.exception.HintException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.expression.RegexFunctor;
import io.harness.k8s.model.KubernetesAzureConfig;
import io.harness.k8s.model.KubernetesClusterAuthType;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.kubeconfig.Exec;
import io.harness.k8s.model.kubeconfig.InteractiveMode;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.azure.AzureIdentityAccessTokenResponse;

import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.appservice.models.WebDeploymentSlotBasic;
import com.azure.resourcemanager.containerregistry.models.Registry;
import com.azure.resourcemanager.resources.fluentcore.arm.models.HasName;
import com.azure.resourcemanager.resources.models.Subscription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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

  public ConnectorValidationResult getConnectorValidationResult(AzureConfigContext azureConfigContext) {
    String errorMessage;

    AzureConfig azureConfig = null;

    try {
      azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(
          azureConfigContext.getAzureConnector().getCredential(), azureConfigContext.getEncryptedDataDetails(),
          azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType(),
          azureConfigContext.getAzureConnector().getAzureEnvironmentType(), secretDecryptionService,
          azureConfigContext.getCertificateWorkingDirectory());
    } catch (IOException ioe) {
      throw NestedExceptionUtils.hintWithExplanationException("Failed to validate connection for Azure connector",
          "Please check you Azure connector configuration or delegate filesystem permissions.",
          new AzureAuthenticationException(ioe.getMessage()));
    }

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

  public AzureSubscriptionsResponse listSubscriptions(AzureConfigContext azureConfigContext) throws IOException {
    log.info(format("Fetching Azure subscriptions for %s user type",
        azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType().getDisplayName()));
    log.trace(format("User: \n%s", azureConfigContext.getAzureConnector().toString()));

    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(
        azureConfigContext.getAzureConnector().getCredential(), azureConfigContext.getEncryptedDataDetails(),
        azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType(),
        azureConfigContext.getAzureConnector().getAzureEnvironmentType(), secretDecryptionService,
        azureConfigContext.getCertificateWorkingDirectory());

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

  public AzureResourceGroupsResponse listResourceGroups(AzureConfigContext azureConfigContext) throws IOException {
    log.info(format("Fetching Azure resource groups for subscription %s for %s user type",
        azureConfigContext.getSubscriptionId(),
        azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType().getDisplayName()));

    log.trace(format("User: \n%s", azureConfigContext.getAzureConnector().toString()));

    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(
        azureConfigContext.getAzureConnector().getCredential(), azureConfigContext.getEncryptedDataDetails(),
        azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType(),
        azureConfigContext.getAzureConnector().getAzureEnvironmentType(), secretDecryptionService,
        azureConfigContext.getCertificateWorkingDirectory());
    AzureResourceGroupsResponse response;
    response = AzureResourceGroupsResponse.builder()
                   .resourceGroups(azureComputeClient.listResourceGroupsNamesBySubscriptionId(
                       azureConfig, azureConfigContext.getSubscriptionId()))
                   .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                   .build();

    log.info(format("Retrieved %d resource groups (listing first %d only): %s", response.getResourceGroups().size(),
        ITEM_LOG_LIMIT,
        response.getResourceGroups().stream().limit(ITEM_LOG_LIMIT).collect(Collectors.toList()).toString()));

    return response;
  }
  public AzureImageGalleriesResponse listImageGalleries(AzureConfigContext azureConfigContext) throws IOException {
    log.info(format("Fetching Azure image galleries for subscription %s for %s user type",
        azureConfigContext.getSubscriptionId(),
        azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType().getDisplayName()));

    log.trace(format("User: \n%s", azureConfigContext.getAzureConnector().toString()));
    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(
        azureConfigContext.getAzureConnector().getCredential(), azureConfigContext.getEncryptedDataDetails(),
        azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType(),
        azureConfigContext.getAzureConnector().getAzureEnvironmentType(), secretDecryptionService,
        azureConfigContext.getCertificateWorkingDirectory());
    AzureImageGalleriesResponse response;
    response = AzureImageGalleriesResponse.builder()
                   .azureImageGalleries(azureComputeClient.listImageGalleries(
                       azureConfig, azureConfigContext.getSubscriptionId(), azureConfigContext.getResourceGroup()))
                   .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                   .build();

    return response;
  }
  public AzureWebAppNamesResponse listWebAppNames(AzureConfigContext azureConfigContext) throws IOException {
    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(
        azureConfigContext.getAzureConnector().getCredential(), azureConfigContext.getEncryptedDataDetails(),
        azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType(),
        azureConfigContext.getAzureConnector().getAzureEnvironmentType(), secretDecryptionService,
        azureConfigContext.getCertificateWorkingDirectory());

    AzureWebAppNamesResponse response;
    response = AzureWebAppNamesResponse.builder()
                   .webAppNames(azureComputeClient.listWebAppNamesBySubscriptionIdAndResourceGroup(
                       azureConfig, azureConfigContext.getSubscriptionId(), azureConfigContext.getResourceGroup()))
                   .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                   .build();
    return response;
  }

  public AzureDeploymentSlotsResponse listDeploymentSlots(AzureConfigContext azureConfigContext)
      throws IOException, ManagementException {
    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(
        azureConfigContext.getAzureConnector().getCredential(), azureConfigContext.getEncryptedDataDetails(),
        azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType(),
        azureConfigContext.getAzureConnector().getAzureEnvironmentType(), secretDecryptionService,
        azureConfigContext.getCertificateWorkingDirectory());

    AzureDeploymentSlotsResponse response;
    List<WebDeploymentSlotBasic> webAppDeploymentSlots =
        azureComputeClient.listWebAppDeploymentSlots(azureConfig, azureConfigContext.getSubscriptionId(),
            azureConfigContext.getResourceGroup(), azureConfigContext.getWebAppName());
    List<AzureDeploymentSlotResponse> deploymentSlotsData =
        toDeploymentSlotData(webAppDeploymentSlots, azureConfigContext.getWebAppName());

    response =
        AzureDeploymentSlotsResponse.builder()
            .deploymentSlots(addProductionDeploymentSlotData(deploymentSlotsData, azureConfigContext.getWebAppName()))
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
      List<WebDeploymentSlotBasic> deploymentSlots, String webAppName) {
    return deploymentSlots.stream()
        .map(WebDeploymentSlotBasic::name)
        .map(slotName
            -> AzureDeploymentSlotResponse.builder()
                   .name(format(DEPLOYMENT_SLOT_FULL_NAME_PATTERN, webAppName, slotName))
                   .type(DEPLOYMENT_SLOT_NON_PRODUCTION_TYPE)
                   .build())
        .collect(Collectors.toList());
  }

  public AzureRegistriesResponse listContainerRegistries(AzureConfigContext azureConfigContext) throws IOException {
    log.info(format("Fetching Azure Container Registries for subscription %s for %s user type",
        azureConfigContext.getSubscriptionId(),
        azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType().getDisplayName()));

    log.trace(format("User: \n%s", azureConfigContext.getAzureConnector().toString()));

    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(
        azureConfigContext.getAzureConnector().getCredential(), azureConfigContext.getEncryptedDataDetails(),
        azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType(),
        azureConfigContext.getAzureConnector().getAzureEnvironmentType(), secretDecryptionService,
        azureConfigContext.getCertificateWorkingDirectory());

    AzureRegistriesResponse response;
    try {
      response =
          AzureRegistriesResponse.builder()
              .containerRegistries(azureContainerRegistryClient
                                       .listContainerRegistries(azureConfig, azureConfigContext.getSubscriptionId())
                                       .stream()
                                       .map(Registry::name)
                                       .collect(Collectors.toList()))
              .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
              .build();
    } catch (Exception e) {
      throw new HintException("No registry found with given Subscription");
    }

    log.info(format("Retrieved %d container registries (listing first %d only): %s",
        response.getContainerRegistries().size(), ITEM_LOG_LIMIT,
        response.getContainerRegistries().stream().limit(ITEM_LOG_LIMIT).collect(Collectors.toList()).toString()));

    return response;
  }

  public AzureClustersResponse listClusters(AzureConfigContext azureConfigContext) throws IOException {
    log.info(format("Fetching Azure Kubernetes Clusters for subscription %s, for resource group %s, for %s user type",
        azureConfigContext.getSubscriptionId(), azureConfigContext.getResourceGroup(),
        azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType().getDisplayName()));

    log.trace(format("User: \n%s", azureConfigContext.getAzureConnector().toString()));

    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(
        azureConfigContext.getAzureConnector().getCredential(), azureConfigContext.getEncryptedDataDetails(),
        azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType(),
        azureConfigContext.getAzureConnector().getAzureEnvironmentType(), secretDecryptionService,
        azureConfigContext.getCertificateWorkingDirectory());

    AzureClustersResponse response;
    response =
        AzureClustersResponse.builder()
            .clusters(azureKubernetesClient.listKubernetesClusters(azureConfig, azureConfigContext.getSubscriptionId())
                          .stream()
                          .filter(kubernetesCluster
                              -> kubernetesCluster.resourceGroupName().equalsIgnoreCase(
                                  azureConfigContext.getResourceGroup()))
                          .map(HasName::name)
                          .collect(Collectors.toList()))
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();

    log.info(format("Retrieved %d kubernetes clusters (listing first %d only): %s", response.getClusters().size(),
        ITEM_LOG_LIMIT, response.getClusters().stream().limit(ITEM_LOG_LIMIT).collect(Collectors.toList()).toString()));

    return response;
  }

  public AzureTagsResponse listTags(AzureConfigContext azureConfigContext) throws IOException {
    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(
        azureConfigContext.getAzureConnector().getCredential(), azureConfigContext.getEncryptedDataDetails(),
        azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType(),
        azureConfigContext.getAzureConnector().getAzureEnvironmentType(), secretDecryptionService,
        azureConfigContext.getCertificateWorkingDirectory());

    return AzureTagsResponse.builder()
        .tags(azureManagementClient.listTags(azureConfig, azureConfigContext.getSubscriptionId())
                  .toStream()
                  .map(TagDetails::getTagName)
                  .collect(Collectors.toList()))
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .build();
  }

  public AzureHostsResponse listHosts(AzureConfigContext azureConfigContext) throws IOException {
    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(
        azureConfigContext.getAzureConnector().getCredential(), azureConfigContext.getEncryptedDataDetails(),
        azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType(),
        azureConfigContext.getAzureConnector().getAzureEnvironmentType(), secretDecryptionService,
        azureConfigContext.getCertificateWorkingDirectory());

    return AzureHostsResponse.builder()
        .hosts(azureComputeClient
                   .listHosts(azureConfig, azureConfigContext.getSubscriptionId(),
                       azureConfigContext.getResourceGroup(), azureConfigContext.getAzureOSType(),
                       azureConfigContext.getTags(), azureConfigContext.getAzureHostConnectionType())
                   .stream()
                   .map(this::toAzureHost)
                   .collect(Collectors.toList()))
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .build();
  }

  @NotNull
  private AzureHostResponse toAzureHost(VirtualMachineData virtualMachineData) {
    return AzureHostResponse.builder()
        .hostName(virtualMachineData.getHostName())
        .address(virtualMachineData.getAddress())
        .privateIp(virtualMachineData.getPrivateIp())
        .publicIp(virtualMachineData.getPublicIp())
        .build();
  }

  public KubernetesConfig getClusterConfig(AzureConfigContext azureConfigContext, LogCallback logCallback)
      throws IOException {
    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(
        azureConfigContext.getAzureConnector().getCredential(), azureConfigContext.getEncryptedDataDetails(),
        azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType(),
        azureConfigContext.getAzureConnector().getAzureEnvironmentType(), secretDecryptionService,
        azureConfigContext.getCertificateWorkingDirectory());

    return getKubernetesConfigK8sCluster(azureConfig, azureConfigContext.getSubscriptionId(),
        azureConfigContext.getResourceGroup(), azureConfigContext.getCluster(), azureConfigContext.getNamespace(),
        azureConfigContext.isUseClusterAdminCredentials(),
        getAzureKubeconfigFormat(azureConfig.getAzureAuthenticationType(), logCallback));
  }

  public AzureRepositoriesResponse listRepositories(AzureConfigContext azureConfigContext) throws IOException {
    log.info(
        format("Fetching Azure Container Registry repositories for subscription %s, for registry %s, for %s user type",
            azureConfigContext.getSubscriptionId(), azureConfigContext.getContainerRegistry(),
            azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType().getDisplayName()));

    log.trace(format("User: \n%s", azureConfigContext.getAzureConnector().toString()));

    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(
        azureConfigContext.getAzureConnector().getCredential(), azureConfigContext.getEncryptedDataDetails(),
        azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType(),
        azureConfigContext.getAzureConnector().getAzureEnvironmentType(), secretDecryptionService,
        azureConfigContext.getCertificateWorkingDirectory());

    AzureRepositoriesResponse response;
    Registry registry =
        azureContainerRegistryClient
            .findFirstContainerRegistryByNameOnSubscription(
                azureConfig, azureConfigContext.getSubscriptionId(), azureConfigContext.getContainerRegistry())
            .orElseThrow(
                ()
                    -> NestedExceptionUtils.hintWithExplanationException(
                        format("Not found Azure container registry by name: %s, subscription id: %s",
                            azureConfigContext.getContainerRegistry(), azureConfigContext.getSubscriptionId()),
                        "Please check if the container registry and subscription values are properly configured.",
                        new AzureAuthenticationException("Failed to retrieve container registry")));

    response = AzureRepositoriesResponse.builder()
                   .repositories(azureContainerRegistryClient.listRepositories(
                       azureConfig, azureConfigContext.getSubscriptionId(), registry.loginServerUrl()))
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
      String resourceGroup, String cluster, String namespace, boolean shouldGetAdminCredentials,
      AzureKubeconfigFormat azureKubeconfigFormat) {
    try {
      log.info(format(
          "Getting AKS kube config [subscription: %s] [resourceGroup: %s] [cluster: %s] [namespace: %s] [credentials: %s]",
          subscriptionId, resourceGroup, cluster, namespace, shouldGetAdminCredentials ? "admin" : "user"));

      String kubeConfigContent = getKubeConfigContent(
          azureConfig, subscriptionId, resourceGroup, cluster, shouldGetAdminCredentials, azureKubeconfigFormat);

      log.trace(format("Cluster credentials: \n %s", kubeConfigContent));

      AzureKubeConfig azureKubeConfig = getAzureKubeConfig(kubeConfigContent);
      verifyAzureKubeConfig(azureKubeConfig);
      UserConfig userConfig = azureKubeConfig.getUsers().get(0).getUser();

      if (userConfig.getAuthProvider() != null) {
        azureKubeConfig.setAadToken(
            fetchAksAADToken(azureConfig, userConfig.getAuthProvider().getConfig().getApiServerId()));
      } else if (userConfig.getExec() != null) {
        String apiServerId = Exec.getValueFromArgsList(userConfig.getExec().getArgs(), KUBECFG_ARGS_SERVER_ID);
        azureKubeConfig.setAadToken(fetchAksAADToken(azureConfig, apiServerId));
        userConfig.setExec(updateAzureKubeconfig(userConfig.getExec(), azureConfig, apiServerId));
      }

      return getKubernetesConfig(azureKubeConfig, namespace);
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException(format("Kube Config could not be read from cluster"),
          "Please check your Azure permissions", new AzureAKSException(e.getMessage(), WingsException.USER, e));
    }
  }

  private String fetchAksAADToken(AzureConfig azureConfig, String apiServerId) {
    StringBuilder scope = new StringBuilder(apiServerId);

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
      String clusterName, boolean shouldGetAdminCredentials, AzureKubeconfigFormat azureKubeconfigFormat) {
    String aksClusterCredentialsBase64 = azureKubernetesClient.getClusterCredentials(azureConfig,
        format("Bearer %s", fetchAzureUserAccessToken(azureConfig)), subscription, resourceGroup, clusterName,
        shouldGetAdminCredentials, azureKubeconfigFormat);
    return new String(EncodingUtils.decodeBase64(aksClusterCredentialsBase64));
  }

  private String fetchAzureUserAccessToken(AzureConfig azureConfig) {
    return azureAuthorizationClient
        .getUserAccessToken(azureConfig,
            AzureUtils.convertToScope(
                AzureUtils.getAzureEnvironment(azureConfig.getAzureEnvironmentType()).getManagementEndpoint()))
        .getAccessToken();
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
    } else if (azureKubeConfig.getUsers().get(0).getUser().getExec() != null) {
      if (isEmpty(azureKubeConfig.getClusters().get(0).getName())) {
        throw new AzureAKSException("Cluster name was not found in the kube config content!!!");
      }

      if (isEmpty(azureKubeConfig.getCurrentContext())) {
        throw new AzureAKSException("Current context was not found in the kube config content!!!");
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
    UserConfig userConfig = azureKubeConfig.getUsers().get(0).getUser();
    if (isNotEmpty(azureKubeConfig.getAadToken()) && userConfig.getAuthProvider() != null) {
      KubernetesAzureConfig kubernetesAzureConfig =
          KubernetesAzureConfig.builder()
              .clusterName(azureKubeConfig.getClusters().get(0).getName())
              .clusterUser(azureKubeConfig.getUsers().get(0).getName())
              .currentContext(azureKubeConfig.getCurrentContext())
              .apiServerId(userConfig.getAuthProvider().getConfig().getApiServerId())
              .clientId(userConfig.getAuthProvider().getConfig().getClientId())
              .configMode(userConfig.getAuthProvider().getConfig().getConfigMode())
              .tenantId(userConfig.getAuthProvider().getConfig().getTenantId())
              .environment(userConfig.getAuthProvider().getConfig().getEnvironment())
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
    } else if (isNotEmpty(azureKubeConfig.getAadToken()) && userConfig.getExec() != null) {
      KubernetesAzureConfig kubernetesAzureConfig = KubernetesAzureConfig.builder()
                                                        .clusterName(azureKubeConfig.getClusters().get(0).getName())
                                                        .clusterUser(azureKubeConfig.getUsers().get(0).getName())
                                                        .currentContext(azureKubeConfig.getCurrentContext())
                                                        .aadIdToken(azureKubeConfig.getAadToken())
                                                        .build();
      return KubernetesConfig.builder()
          .namespace(namespace)
          .masterUrl(azureKubeConfig.getClusters().get(0).getCluster().getServer())
          .caCert(azureKubeConfig.getClusters().get(0).getCluster().getCertificateAuthorityData().toCharArray())
          .username(azureKubeConfig.getUsers().get(0).getName().toCharArray())
          .azureConfig(kubernetesAzureConfig)
          .exec(userConfig.getExec())
          .authType(KubernetesClusterAuthType.EXEC_OAUTH)
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

  public AzureAcrTokenTaskResponse getAcrLoginToken(AzureConfigContext azureConfigContext) throws IOException {
    log.info(format("Fetching ACR login token for registry %s", azureConfigContext.getContainerRegistry()));

    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(
        azureConfigContext.getAzureConnector().getCredential(), azureConfigContext.getEncryptedDataDetails(),
        azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType(),
        azureConfigContext.getAzureConnector().getAzureEnvironmentType(), secretDecryptionService,
        azureConfigContext.getCertificateWorkingDirectory());

    String azureAccessToken =
        azureAuthorizationClient
            .getUserAccessToken(azureConfig,
                AzureUtils.convertToScope(
                    AzureUtils.getAzureEnvironment(azureConfig.getAzureEnvironmentType()).getManagementEndpoint()))
            .getAccessToken();

    String refreshToken =
        azureContainerRegistryClient.getAcrRefreshToken(azureConfigContext.getContainerRegistry(), azureAccessToken);

    return AzureAcrTokenTaskResponse.builder()
        .token(refreshToken)
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .build();
  }

  public AzureMngGroupsResponse listMngGroup(AzureConfigContext azureConfigContext) throws IOException {
    log.info("Fetching Azure management groups");
    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(
        azureConfigContext.getAzureConnector().getCredential(), azureConfigContext.getEncryptedDataDetails(),
        azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType(),
        azureConfigContext.getAzureConnector().getAzureEnvironmentType(), secretDecryptionService,
        azureConfigContext.getCertificateWorkingDirectory());

    AzureMngGroupsResponse azureMngGroupsResponse =
        AzureMngGroupsResponse.builder()
            .managementGroups(azureManagementClient.listManagementGroups(azureConfig)
                                  .toStream()
                                  .map(group
                                      -> ManagementGroupData.builder()
                                             .name(group.getName())
                                             .id(group.getId())
                                             .displayName(group.getProperties().getDisplayName())
                                             .build())
                                  .collect(Collectors.toList()))
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();

    log.info(format("Retrieved %d management groups", azureMngGroupsResponse.getManagementGroups().size()));
    return azureMngGroupsResponse;
  }

  public AzureLocationsResponse listSubscriptionLocations(AzureConfigContext azureConfigContext) throws IOException {
    log.info("Fetching Azure locations");
    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(
        azureConfigContext.getAzureConnector().getCredential(), azureConfigContext.getEncryptedDataDetails(),
        azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType(),
        azureConfigContext.getAzureConnector().getAzureEnvironmentType(), secretDecryptionService,
        azureConfigContext.getCertificateWorkingDirectory());
    AzureLocationsResponse azureLocationsResponse =
        AzureLocationsResponse.builder()
            .locations(new ArrayList<>(azureManagementClient.listLocationsBySubscriptionId(
                azureConfig, azureConfigContext.getSubscriptionId())))
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
    log.info(format("Retrieved %d locations", azureLocationsResponse.getLocations().size()));
    return azureLocationsResponse;
  }

  public DelegateResponseData listLocations(AzureConfigContext azureConfigContext) throws IOException {
    log.info("Fetching Azure locations");
    AzureConfig azureConfig = AcrRequestResponseMapper.toAzureInternalConfig(
        azureConfigContext.getAzureConnector().getCredential(), azureConfigContext.getEncryptedDataDetails(),
        azureConfigContext.getAzureConnector().getCredential().getAzureCredentialType(),
        azureConfigContext.getAzureConnector().getAzureEnvironmentType(), secretDecryptionService,
        azureConfigContext.getCertificateWorkingDirectory());
    AzureEnvironmentType azureEnvironmentType = azureConfig.getAzureEnvironmentType();
    AzureLocationsResponse azureLocationsResponse =
        AzureLocationsResponse.builder()
            .locations(Region.values()
                           .stream()
                           .filter(region
                               -> (AzureEnvironmentType.AZURE_US_GOVERNMENT == azureEnvironmentType)
                                   == AzureUtils.AZURE_GOV_REGIONS_NAMES.contains(region.name()))
                           .map(Region::label)
                           .collect(Collectors.toList()))
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
    log.info(format("Retrieved %d locations", azureLocationsResponse.getLocations().size()));
    return azureLocationsResponse;
  }

  private Exec updateAzureKubeconfig(Exec exec, AzureConfig azureConfig, String serverId) {
    return Exec.builder()
        .apiVersion(exec.getApiVersion())
        .args(getArgsForAzureKubeconfig(azureConfig, serverId))
        .command(AZURE_AUTH_PLUGIN_BINARY)
        .env(exec.getEnv())
        .installHint(AZURE_AUTH_PLUGIN_INSTALL_HINT)
        .interactiveMode(InteractiveMode.NEVER)
        .provideClusterInfo(false)
        .build();
  }

  private List<String> getArgsForAzureKubeconfig(AzureConfig azureConfig, String serverId) {
    List<String> args = new ArrayList<>();
    args.add(KUBECFG_ARGS_GET_TOKEN);
    args.add(KUBECFG_ARGS_SERVER_ID);
    args.add(serverId);
    switch (azureConfig.getAzureAuthenticationType()) {
      case MANAGED_IDENTITY_USER_ASSIGNED:
        args.add(KUBECFG_ARGS_CLIENT_ID);
        args.add(azureConfig.getClientId());
        args.add(KUBECFG_ARGS_LOGIN);
        args.add(KUBECFG_ARGS_MSI);
        break;

      case MANAGED_IDENTITY_SYSTEM_ASSIGNED:
        args.add(KUBECFG_ARGS_LOGIN);
        args.add(KUBECFG_ARGS_MSI);
        break;

      case SERVICE_PRINCIPAL_SECRET:
        args.addAll(prepareArgsForSpnAuth(azureConfig));
        args.add(KUBECFG_ARGS_CLIENT_SECRET);
        args.add(String.valueOf(azureConfig.getKey()));
        args.add(KUBECFG_ARGS_LOGIN);
        args.add(KUBECFG_ARGS_SPN);
        break;

      case SERVICE_PRINCIPAL_CERT:
        args.add(KUBECFG_ARGS_LOGIN);
        args.add(KUBECFG_ARGS_AZURE_CLI);
        break;

      default:
        throw new UnsupportedOperationException(
            format("Auth Type %s is not supported for azure kubeconfig with exec plugin",
                azureConfig.getAzureAuthenticationType()));
    }
    return args;
  }

  private List<String> prepareArgsForSpnAuth(AzureConfig azureConfig) {
    return Arrays.asList(KUBECFG_ARGS_ENV, azureConfig.getAzureEnvironmentType().getDisplayName(),
        KUBECFG_ARGS_CLIENT_ID, azureConfig.getClientId(), KUBECFG_ARGS_TENANT_ID, azureConfig.getTenantId());
  }

  private AzureKubeconfigFormat getAzureKubeconfigFormat(
      AzureAuthenticationType azureAuthenticationType, LogCallback logCallback) {
    if (isExecAuthPluginBinaryAvailable(AZURE_AUTH_PLUGIN_BINARY, logCallback)) {
      if (AzureAuthenticationType.SERVICE_PRINCIPAL_CERT == azureAuthenticationType) {
        saveLogs(
            "Certificate authentication is not yet supported for kubelogin credentials plugin. Using authentication provider",
            logCallback, LogLevel.WARN);
        return AzureKubeconfigFormat.AZURE;
      }
      return AzureKubeconfigFormat.EXEC;
    }
    return AzureKubeconfigFormat.AZURE;
  }

  private void saveLogs(String errorMsg, LogCallback logCallback, LogLevel logLevel) {
    if (logCallback != null) {
      logCallback.saveExecutionLog(errorMsg, logLevel);
    } else {
      if (logLevel == LogLevel.INFO) {
        log.info(errorMsg);
      } else {
        log.warn(errorMsg);
      }
    }
  }
}
