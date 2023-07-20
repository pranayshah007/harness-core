/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.impl;

import static io.harness.azure.model.AzureConstants.ACR_REGISTRY_NAME_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.REGISTRY_HOST_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.REPOSITORY_NAME_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.SUBSCRIPTION_ID_NULL_VALIDATION_MSG;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.artifacts.docker.beans.DockerImageManifestResponse;
import io.harness.azure.AzureClient;
import io.harness.azure.client.AzureContainerRegistryClient;
import io.harness.azure.client.AzureContainerRegistryRestClient;
import io.harness.azure.context.AzureContainerRegistryClientContext;
import io.harness.azure.model.AzureAuthenticationType;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureConstants;
import io.harness.azure.utility.AzureUtils;
import io.harness.exception.AzureAuthenticationException;
import io.harness.exception.AzureContainerRegistryException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;

import software.wings.helpers.ext.azure.AcrGetRepositoryTagsResponse;
import software.wings.helpers.ext.azure.AcrGetTokenResponse;

import com.azure.core.http.rest.PagedIterable;
import com.azure.identity.implementation.util.ScopeUtil;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.containerregistry.models.Registry;
import com.azure.resourcemanager.containerregistry.models.RegistryCredentials;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Singleton
@Slf4j
public class AzureContainerRegistryClientImpl extends AzureClient implements AzureContainerRegistryClient {
  private static String REGISTRY_SCOPE = "registry:catalog:*";
  private static String REPOSITORY_SCOPE = "repository:%s:metadata_read";
  private static String REPOSITORY_SCOPE_PULL = "repository:%s:pull";

  @Override
  public Optional<RegistryCredentials> getContainerRegistryCredentials(AzureContainerRegistryClientContext context) {
    String registryName = context.getRegistryName();
    String resourceGroupName = context.getResourceGroupName();
    AzureResourceManager azure = getAzureClientByContext(context);
    log.debug("Start getting container registry credentials by registryName: {}, context: {}", registryName, context);
    return Optional.ofNullable(azure.containerRegistries().getCredentials(resourceGroupName, registryName));
  }

  @Override
  public Optional<Registry> findFirstContainerRegistryByNameOnSubscription(
      AzureConfig azureConfig, String subscriptionId, String registryName) {
    if (isBlank(registryName)) {
      throw new IllegalArgumentException(ACR_REGISTRY_NAME_BLANK_VALIDATION_MSG);
    }

    AzureResourceManager azure = getAzureClient(azureConfig, subscriptionId);
    Instant startFilteringRegistries = Instant.now();
    PagedIterable<Registry> registries = azure.containerRegistries().list();
    Optional<Registry> registryOptional =
        registries.stream().filter(registry -> registryName.equalsIgnoreCase(registry.name())).findFirst();
    long elapsedTime = Duration.between(startFilteringRegistries, Instant.now()).toMillis();
    log.info("Obtained container registry by name registryName: {} for elapsed time: {}, subscriptionId: {} ",
        registryName, elapsedTime, subscriptionId);
    return registryOptional;
  }

  @Override
  public List<Registry> listContainerRegistries(AzureConfig azureConfig, final String subscriptionId) {
    if (isBlank(subscriptionId)) {
      throw new IllegalArgumentException(SUBSCRIPTION_ID_NULL_VALIDATION_MSG);
    }
    AzureResourceManager azure = getAzureClient(azureConfig, subscriptionId);

    log.debug("Start listing container registries on subscription, subscriptionId: {}", subscriptionId);
    return azure.containerRegistries().list().stream().collect(Collectors.toList());
  }

  @Override
  public List<String> listRepositoryTags(
      AzureConfig azureConfig, final String registryHost, final String repositoryName) {
    if (isBlank(registryHost)) {
      throw new IllegalArgumentException(REGISTRY_HOST_BLANK_VALIDATION_MSG);
    }
    if (isBlank(repositoryName)) {
      throw new IllegalArgumentException(REPOSITORY_NAME_BLANK_VALIDATION_MSG);
    }

    AzureContainerRegistryRestClient azureContainerRegistryRestClient =
        getAzureContainerRegistryRestClient(registryHost);

    try {
      String authHeader;
      if (azureConfig.getAzureAuthenticationType() == AzureAuthenticationType.SERVICE_PRINCIPAL_CERT
          || azureConfig.getAzureAuthenticationType() == AzureAuthenticationType.MANAGED_IDENTITY_SYSTEM_ASSIGNED
          || azureConfig.getAzureAuthenticationType() == AzureAuthenticationType.MANAGED_IDENTITY_USER_ASSIGNED) {
        String azureAccessToken =
            getAuthenticationTokenCredentials(azureConfig)
                .getToken(AzureUtils.getTokenRequestContext(ScopeUtil.resourceToScopes(
                    AzureUtils.getAzureEnvironment(azureConfig.getAzureEnvironmentType()).getManagementEndpoint())))
                .block()
                .getToken();
        String acrAccessToken =
            getAcrAccessToken(registryHost, azureAccessToken, format(REPOSITORY_SCOPE, repositoryName));

        authHeader = getAzureBearerAuthHeader(acrAccessToken);
      } else {
        authHeader = getAzureBasicAuthHeader(azureConfig.getClientId(), String.valueOf(azureConfig.getKey()));
      }

      log.debug("Start listing repository tags, registryHost: {}, repositoryName: {}", registryHost, repositoryName);
      Response<AcrGetRepositoryTagsResponse> execute =
          azureContainerRegistryRestClient.listRepositoryTags(authHeader, repositoryName).execute();

      if (execute.errorBody() != null) {
        throw new InvalidRequestException("Unable to list repository tags for registryHost:" + registryHost
            + " and repositoryName:" + repositoryName);
      }

      return execute.body().getTags();
    } catch (IOException e) {
      throw new InvalidRequestException(
          "Unable to list repository tags for registryHost:" + registryHost + " and repositoryName:" + repositoryName);
    }
  }

  @Override
  public Response<DockerImageManifestResponse> getImageManifest(
      AzureConfig azureConfig, final String registryHost, final String repositoryName, String tag, boolean isV1) {
    AzureContainerRegistryRestClient azureContainerRegistryRestClient =
        getAzureContainerRegistryRestClient(registryHost);
    try {
      String authHeader;
      if (azureConfig.getAzureAuthenticationType() == AzureAuthenticationType.SERVICE_PRINCIPAL_CERT
          || azureConfig.getAzureAuthenticationType() == AzureAuthenticationType.MANAGED_IDENTITY_SYSTEM_ASSIGNED
          || azureConfig.getAzureAuthenticationType() == AzureAuthenticationType.MANAGED_IDENTITY_USER_ASSIGNED) {
        String azureAccessToken =
            getAuthenticationTokenCredentials(azureConfig)
                .getToken(AzureUtils.getTokenRequestContext(ScopeUtil.resourceToScopes(
                    AzureUtils.getAzureEnvironment(azureConfig.getAzureEnvironmentType()).getManagementEndpoint())))
                .block()
                .getToken();
        String acrAccessToken =
            getAcrAccessToken(registryHost, azureAccessToken, format(REPOSITORY_SCOPE_PULL, repositoryName));

        authHeader = getAzureBearerAuthHeader(acrAccessToken);
      } else {
        authHeader = getAzureBasicAuthHeader(azureConfig.getClientId(), String.valueOf(azureConfig.getKey()));
      }

      Response<DockerImageManifestResponse> execute;
      if (isV1) {
        execute = azureContainerRegistryRestClient.getImageManifestV1(authHeader, repositoryName, tag).execute();
      } else {
        execute = azureContainerRegistryRestClient.getImageManifestV2(authHeader, repositoryName, tag).execute();
      }

      if (execute.errorBody() != null) {
        throw new InvalidRequestException(String.format(
            "Could not fetch manifest for registryHost:%s repository:%s tag:%s", registryHost, repositoryName, tag));
      }

      return execute;
    } catch (IOException e) {
      throw new InvalidRequestException(String.format(
          "Could not fetch manifest for registryHost:%s repository:%s tag:%s", registryHost, repositoryName, tag));
    }
  }

  @Override
  public List<String> listRepositories(AzureConfig azureConfig, String subscriptionId, String registryUrl) {
    try {
      AzureContainerRegistryRestClient acrRestClient = getAzureContainerRegistryRestClient(registryUrl);

      String authHeader = null;
      if (azureConfig.getAzureAuthenticationType() == AzureAuthenticationType.SERVICE_PRINCIPAL_SECRET) {
        authHeader = getAzureBasicAuthHeader(azureConfig.getClientId(), String.valueOf(azureConfig.getKey()));
      } else {
        String azureAccessToken =
            getAuthenticationTokenCredentials(azureConfig)
                .getToken(AzureUtils.getTokenRequestContext(ScopeUtil.resourceToScopes(
                    AzureUtils.getAzureEnvironment(azureConfig.getAzureEnvironmentType()).getManagementEndpoint())))
                .block()
                .getToken();
        String acrAccessToken = getAcrAccessToken(registryUrl, azureAccessToken, REGISTRY_SCOPE);

        authHeader = getAzureBearerAuthHeader(acrAccessToken);
      }
      List<String> allRepositories = new ArrayList<>();
      String last = null;
      List<String> repositories;
      do {
        repositories = acrRestClient.listRepositories(authHeader, last).execute().body().getRepositories();

        if (isNotEmpty(repositories)) {
          allRepositories.addAll(repositories);
          last = repositories.get(repositories.size() - 1);
        }
      } while (isNotEmpty(repositories));
      return allRepositories;
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException("Check Service Principal/Managed Identity permissions",
          String.format("Error occurred while getting repositories for subscriptionId/registry: %s/%s", subscriptionId,
              registryUrl),
          new AzureContainerRegistryException(e.getMessage(), WingsException.USER, e));
    }
  }

  @Override
  public String getAcrRefreshToken(String registryUrl, String azureAccessToken) {
    WingsException we;
    try {
      log.info(format("Fetching ACR refresh token for registry %s", registryUrl));

      AzureContainerRegistryRestClient acrRestClient =
          getAzureRestClient(buildRepositoryHostUrl(registryUrl), AzureContainerRegistryRestClient.class);
      Response<AcrGetTokenResponse> response =
          acrRestClient.getRefreshToken(AzureConstants.ACCESS_TOKEN, azureAccessToken, registryUrl).execute();
      if (response.isSuccessful()) {
        return response.body().getRefreshToken();
      } else {
        we = new AzureAuthenticationException(
            format("Get ACR refresh token in exchange for Azure access token has failed: %s with status code %s",
                response.message(), response.code()));
      }
    } catch (IOException e) {
      we = new AzureAuthenticationException(e.getMessage(), WingsException.USER, e);
    }

    throw NestedExceptionUtils.hintWithExplanationException(
        format("Retrieving ACR refresh token for %s has failed", registryUrl),
        "Please recheck your azure connector config", we);
  }

  @Override
  public String getAcrAccessToken(String registryUrl, String azureAccessToken, String scope) {
    String errMsg;
    try {
      log.info(format("Fetching ACR access token for registry %s and scope %s", registryUrl, scope));

      AzureContainerRegistryRestClient acrRestClient =
          getAzureRestClient(buildRepositoryHostUrl(registryUrl), AzureContainerRegistryRestClient.class);

      String refreshToken = getAcrRefreshToken(registryUrl, azureAccessToken);
      Response<AcrGetTokenResponse> response =
          acrRestClient.getAccessToken(AzureConstants.REFRESH_TOKEN, refreshToken, registryUrl, scope).execute();

      if (response.isSuccessful()) {
        return response.body().getAccessToken();
      } else {
        errMsg =
            format("Get ACR access token request failed: %s with status code %s", response.message(), response.code());
      }
    } catch (IOException e) {
      errMsg = e.getMessage();
    }
    throw NestedExceptionUtils.hintWithExplanationException(
        format("Retrieving ACR access token for %s has failed", registryUrl),
        "Please recheck your azure connector config", new AzureAuthenticationException(errMsg));
  }
}
