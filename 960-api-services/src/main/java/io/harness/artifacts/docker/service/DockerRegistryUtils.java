/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifacts.docker.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.artifacts.docker.service.DockerRegistryServiceImpl.isSuccessful;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.docker.DockerRegistryRestClient;
import io.harness.artifacts.docker.beans.DockerImageManifestResponse;
import io.harness.artifacts.docker.beans.DockerInternalConfig;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.k8s.model.ImageDetails;

import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;
import org.apache.commons.lang3.tuple.ImmutablePair;
import retrofit2.Response;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class DockerRegistryUtils {
  protected static final String GITHUB_CONTAINER_REGISTRY = "ghcr.io";
  protected static final String ACR_CONTAINER_REGISTRY = ".azurecr.io";
  private static final String DOCKER_REGISTRY_CREDENTIAL_TEMPLATE =
      "{\"%s\":{\"username\":\"%s\",\"password\":\"%s\"}}";

  public static String getDockerRegistryCredentials(ImageDetails imageDetails) {
    return format(DOCKER_REGISTRY_CREDENTIAL_TEMPLATE, imageDetails.getRegistryUrl(), imageDetails.getUsername(),
        imageDetails.getPassword().replaceAll("\"", "\\\\\""));
  }

  public ArtifactMetaInfo getArtifactMetaInfo(DockerInternalConfig dockerConfig,
      DockerRegistryRestClient registryRestClient, Function<Headers, String> getTokenFn, String authHeader,
      String imageName, String tag, boolean shouldFetchDockerV2DigestSHA256) {
    try {
      Response<DockerImageManifestResponse> response =
          getImageManifestResponse(dockerConfig, registryRestClient, getTokenFn, authHeader, imageName, tag);
      ArtifactMetaInfo artifactMetaInfo = parseArtifactMetaInfoResponse(response, imageName);
      if (artifactMetaInfo != null && shouldFetchDockerV2DigestSHA256) {
        Response<DockerImageManifestResponse> responseV2 =
            getImageManifestResponseV2(dockerConfig, registryRestClient, getTokenFn, authHeader, imageName, tag);
        ArtifactMetaInfo artifactMetaInfoV2 = parseArtifactMetaInfoResponse(responseV2, imageName);
        if (artifactMetaInfoV2 != null) {
          artifactMetaInfo.setShaV2(artifactMetaInfoV2.getSha());
        }
      }
      return artifactMetaInfo;
    } catch (Exception e) {
      log.error("Unable to fetch artifact metainfo", e);
      return null;
    }
  }

  public List<Map<String, String>> getLabels(DockerInternalConfig dockerConfig,
      DockerRegistryRestClient registryRestClient, Function<Headers, String> getTokenFn, String authHeader,
      String imageName, List<String> tags) {
    Map<Integer, Map<String, String>> labelsMap = new ConcurrentHashMap<>();
    if (EmptyPredicate.isEmpty(tags)) {
      return Collections.emptyList();
    }

    final int size = tags.size();
    try {
      // Get labels for the first tag to get the latest auth header.
      String tag = tags.get(0);
      ImmutablePair<Map<String, String>, String> res =
          getSingleTagLabels(dockerConfig, registryRestClient, getTokenFn, authHeader, imageName, tag);
      String finalAuthHeader = res.getRight();
      if (tags.size() <= 1) {
        return Collections.singletonList(res.getLeft());
      }
      labelsMap.put(0, res.getLeft());

      for (int i = 1; i < size; i++) {
        // Get labels for the next (at most) MAX_GET_LABELS_CONCURRENCY tags.
        String tagInternal = tags.get(i);
        try {
          Map<String, String> newLabels =
              getSingleTagLabels(dockerConfig, registryRestClient, finalAuthHeader, imageName, tagInternal);
          labelsMap.put(i, newLabels);
        } catch (Exception e) {
          log.error("Could not fetch docker labels for {}:{}", imageName, tagInternal, e);
        }
      }

    } catch (Exception e) {
      // Ignore error until we understand why fetching labels is failing sometimes.
      log.error("Failed to fetch docker image labels", e);
      return Collections.emptyList();
    }

    int tagsProcessed = tags.size();
    List<Map<String, String>> labelsList = new ArrayList<>();
    for (int i = 0; i < tagsProcessed; i++) {
      Map<String, String> labels = labelsMap.getOrDefault(i, null);
      if (labels == null) {
        labels = new HashMap<>();
      }
      labelsList.add(labels);
    }
    return labelsList;
  }

  private static Map<String, String> getSingleTagLabels(DockerInternalConfig dockerConfig,
      DockerRegistryRestClient registryRestClient, String authHeader, String imageName, String tag) throws IOException {
    ImmutablePair<Map<String, String>, String> res =
        getSingleTagLabels(dockerConfig, registryRestClient, null, authHeader, imageName, tag);
    return res.getLeft();
  }

  public static ImmutablePair<Map<String, String>, String> getSingleTagLabels(DockerInternalConfig dockerConfig,
      DockerRegistryRestClient registryRestClient, Function<Headers, String> getTokenFn, String authHeader,
      String imageName, String tag) throws IOException {
    Response<DockerImageManifestResponse> response =
        registryRestClient.getImageManifest(authHeader, imageName, tag).execute();
    if (DockerRegistryUtils.fallbackToTokenAuth(response.code(), dockerConfig)) { // unauthorized
      if (getTokenFn == null) {
        // We don't want to retry if getTokenFn is null.
        throw NestedExceptionUtils.hintWithExplanationException("Invalid Credentials",
            "Check if the provided credentials are correct",
            new InvalidArtifactServerException("Invalid Docker Registry credentials", USER));
      }
      String token = getTokenFn.apply(response.headers());
      authHeader = "Bearer " + token;
      response = registryRestClient.getImageManifest(authHeader, imageName, tag).execute();
      if (response.code() == 401) {
        // Unauthorized even after retry.
        throw NestedExceptionUtils.hintWithExplanationException("Invalid Credentials",
            "Check if the provided credentials are correct",
            new InvalidArtifactServerException("Invalid Docker Registry credentials", USER));
      }
    }

    if (!isSuccessful(response)) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Failed to fetch tags for image. Check if the image details are correct",
          "Check if the image exists, the permissions are scoped for the authenticated user & check if the right connector chosen for fetching tags for the image",
          new InvalidArtifactServerException(response.message(), USER));
    }

    checkValidImage(imageName, response);
    DockerImageManifestResponse dockerImageManifestResponse = response.body();
    if (dockerImageManifestResponse == null) {
      return ImmutablePair.of(new HashMap<>(), authHeader);
    }
    return ImmutablePair.of(dockerImageManifestResponse.fetchLabels(), authHeader);
  }

  static void checkValidImage(String imageName, Response response) {
    if (response.code() == 404) { // page not found
      throw imageNotFoundException(imageName);
    }
  }

  static Map<String, String> extractAuthChallengeTokens(String authHeaderValue) {
    // Bearer realm="xxx",service="yyy",scope="zzz"
    if (authHeaderValue != null) {
      String[] headerParts = authHeaderValue.split(" ");
      if (headerParts.length == 2 && "Bearer".equals(headerParts[0])) {
        Map<String, String> tokens =
            Arrays.stream(headerParts[1].split(","))
                .map(token -> token.split("="))
                .collect(Collectors.toMap(s -> s[0], s -> s[1].substring(1, s[1].length() - 1)));
        if ((tokens.size() == 3 && tokens.get("realm") != null && tokens.get("service") != null
                && tokens.get("scope") != null)
            || (tokens.size() == 2 && tokens.get("realm") != null && tokens.get("service") != null)) {
          return tokens;
        }
      }
    }
    return null;
  }

  public static WingsException unauthorizedException() {
    return NestedExceptionUtils.hintWithExplanationException("Update the username & password",
        "Check if the provided credentials are correct",
        new InvalidArtifactServerException("Invalid Docker Registry credentials", USER));
  }

  public static WingsException imageNotFoundException(String imageName) {
    return NestedExceptionUtils.hintWithExplanationException("The Image was not found.",
        "Check if the image exists and if the permissions are scoped for the authenticated user",
        new InvalidArgumentsException(
            ImmutablePair.of("code", "Image name [" + imageName + "] does not exist in Docker Registry."), null, USER));
  }

  public static boolean fallbackToTokenAuth(int status, DockerInternalConfig config) {
    if (status == 401) {
      return true;
    }

    // Handle Github Container Registry. Refer to https://harness.atlassian.net/browse/CDC-14595 for more details
    if (status == 403 && isGithubContainerRegistry(config)) {
      return true;
    }
    return false;
  }

  public static boolean isGithubContainerRegistry(DockerInternalConfig config) {
    return config.getDockerRegistryUrl().contains(GITHUB_CONTAINER_REGISTRY);
  }

  public boolean isAcrContainerRegistry(DockerInternalConfig config) {
    return config.getDockerRegistryUrl().contains(ACR_CONTAINER_REGISTRY);
  }

  private Response<DockerImageManifestResponse> getImageManifestResponse(DockerInternalConfig dockerConfig,
      DockerRegistryRestClient registryRestClient, Function<Headers, String> getTokenFn, String authHeader,
      String imageName, String tag) throws IOException {
    Response<DockerImageManifestResponse> response =
        registryRestClient.getImageManifest(authHeader, imageName, tag).execute();
    if (DockerRegistryUtils.fallbackToTokenAuth(response.code(), dockerConfig)) { // unauthorized
      if (getTokenFn == null) {
        // We don't want to retry if getTokenFn is null.
        throw NestedExceptionUtils.hintWithExplanationException("Invalid Credentials",
            "Check if the provided credentials are correct",
            new InvalidArtifactServerException("Invalid Docker Registry credentials", USER));
      }
      String token = getTokenFn.apply(response.headers());
      authHeader = "Bearer " + token;
      response = registryRestClient.getImageManifest(authHeader, imageName, tag).execute();
      if (response.code() == 401) {
        // Unauthorized even after retry.
        throw NestedExceptionUtils.hintWithExplanationException("Invalid Credentials",
            "Check if the provided credentials are correct",
            new InvalidArtifactServerException("Invalid Docker Registry credentials", USER));
      }
    }
    return response;
  }

  private Response<DockerImageManifestResponse> getImageManifestResponseV2(DockerInternalConfig dockerConfig,
      DockerRegistryRestClient registryRestClient, Function<Headers, String> getTokenFn, String authHeader,
      String imageName, String tag) throws IOException {
    Response<DockerImageManifestResponse> response =
        registryRestClient.getImageManifestV2(authHeader, imageName, tag).execute();
    if (DockerRegistryUtils.fallbackToTokenAuth(response.code(), dockerConfig)) { // unauthorized
      if (getTokenFn == null) {
        // We don't want to retry if getTokenFn is null.
        throw NestedExceptionUtils.hintWithExplanationException("Invalid Credentials",
            "Check if the provided credentials are correct",
            new InvalidArtifactServerException("Invalid Docker Registry credentials", USER));
      }
      String token = getTokenFn.apply(response.headers());
      authHeader = "Bearer " + token;
      response = registryRestClient.getImageManifestV2(authHeader, imageName, tag).execute();
      if (response.code() == 401) {
        // Unauthorized even after retry.
        throw NestedExceptionUtils.hintWithExplanationException("Invalid Credentials",
            "Check if the provided credentials are correct",
            new InvalidArtifactServerException("Invalid Docker Registry credentials", USER));
      }
    }
    return response;
  }

  private ArtifactMetaInfo parseArtifactMetaInfoResponse(
      Response<DockerImageManifestResponse> response, String imageName) {
    if (!isSuccessful(response)) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Failed to fetch tags for image. Check if the image details are correct",
          "Check if the image exists, the permissions are scoped for the authenticated user & check if the right connector chosen for fetching tags for the image",
          new InvalidArtifactServerException(response.message(), USER));
    }
    checkValidImage(imageName, response);
    DockerImageManifestResponse dockerImageManifestResponse = response.body();
    if (dockerImageManifestResponse == null) {
      return null;
    }
    return dockerImageManifestResponse.fetchArtifactMetaInfo(response);
  }
}
