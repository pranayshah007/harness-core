/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.client;

import io.harness.artifacts.docker.beans.DockerImageManifestResponse;
import io.harness.azure.context.AzureContainerRegistryClientContext;
import io.harness.azure.model.AzureConfig;

import com.azure.resourcemanager.containerregistry.models.Registry;
import com.azure.resourcemanager.containerregistry.models.RegistryCredentials;
import java.util.List;
import java.util.Optional;
import retrofit2.Response;

public interface AzureContainerRegistryClient extends AzureResourceClient {
  /**
   * Get container registry credentials.
   *
   * @param context
   * @return
   */
  Optional<RegistryCredentials> getContainerRegistryCredentials(AzureContainerRegistryClientContext context);

  /**
   * Find registry by name on entire subscription. This is cost operation, try to avoid usage if it's possible.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param registryName
   * @return
   */
  Optional<Registry> findFirstContainerRegistryByNameOnSubscription(
      AzureConfig azureConfig, String subscriptionId, String registryName);

  /**
   * List container registries on subscription
   *
   * @param azureConfig Azure config
   * @param subscriptionId subscription id
   * @return list of container registries on subscription
   */
  List<Registry> listContainerRegistries(AzureConfig azureConfig, String subscriptionId);

  /**
   *  List repository tags
   *
   * @param azureConfig Azure config
   * @param registryLoginServerUrl Registry login server URL
   * @param repositoryName repository name
   * @return list of repository tags
   */
  List<String> listRepositoryTags(AzureConfig azureConfig, String registryLoginServerUrl, String repositoryName);

  /**
   * Returns list of all repositories in container registry
   * @param azureConfig
   * @param subscriptionId
   * @param registryUrl
   * @return list of repositories
   */
  List<String> listRepositories(AzureConfig azureConfig, String subscriptionId, String registryUrl);

  /**
   * Returns ACR refresh token
   * @param registryUrl
   * @param azureAccessToken
   * @return ACR refresh token
   */
  String getAcrRefreshToken(String registryUrl, String azureAccessToken);

  /**
   * Returns ACR access token
   * @param registryUrl
   * @param azureAccessToken
   * @param scope
   * @return ACR access token for specific scope
   */
  String getAcrAccessToken(String registryUrl, String azureAccessToken, String scope);

  Response<DockerImageManifestResponse> getImageManifest(
      AzureConfig azureConfig, String registryHost, String repositoryName, String tag, boolean isV1);
}
