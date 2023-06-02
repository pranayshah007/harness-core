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

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorAscending;
import io.harness.artifacts.docker.DockerRegistryRestClient;
import io.harness.artifacts.docker.DockerRegistryToken;
import io.harness.artifacts.docker.beans.DockerInternalConfig;
import io.harness.artifacts.docker.beans.DockerPublicImageTagResponse;
import io.harness.artifacts.docker.client.DockerRestClientFactory;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.expression.RegexFunctor;
import io.harness.network.Http;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import retrofit2.Response;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class DockerPublicRegistryProcessor {
  @Inject private DockerRestClientFactory dockerRestClientFactory;
  @Inject private DockerRegistryUtils dockerRegistryUtils;

  private ExpiringMap<String, String> cachedBearerTokens = ExpiringMap.builder().variableExpiration().build();

  public BuildDetailsInternal verifyBuildNumber(DockerInternalConfig dockerConfig, String imageName, String tag)
      throws IOException {
    DockerRegistryRestClient registryRestClient = dockerRestClientFactory.getDockerRegistryRestClient(dockerConfig);
    if (tag == null) {
      tag = "";
    }
    Response<DockerPublicImageTagResponse.Result> response =
        registryRestClient.getPublicImageTag(imageName, tag).execute();
    if (!isSuccessful(response)) {
      throw NestedExceptionUtils.hintWithExplanationException("Unable to fetch the given tag for the image",
          "The tag provided for the image may be incorrect.",
          new InvalidArtifactServerException(response.message(), USER));
    }
    return processSingleResultResponse(response.body(), imageName, dockerConfig);
  }

  public BuildDetailsInternal getLastSuccessfulBuildFromRegex(
      DockerInternalConfig dockerConfig, String imageName, String tagRegex, int maxNumberOfBuilds) throws IOException {
    List<BuildDetailsInternal> builds = getBuilds(dockerConfig, imageName, maxNumberOfBuilds);
    builds = builds.stream()
                 .filter(build -> new RegexFunctor().match(tagRegex, build.getNumber()))
                 .sorted(new BuildDetailsInternalComparatorAscending())
                 .collect(Collectors.toList());

    if (builds.isEmpty()) {
      throw NestedExceptionUtils.hintWithExplanationException("Could not get the last successful build",
          "There are probably no successful builds for this image & check if the tag filter regex is correct",
          new InvalidArtifactServerException("Didn't get last successful build", USER));
    }
    return builds.get(0);
  }

  public boolean verifyImageName(DockerInternalConfig dockerConfig, String imageName) {
    try {
      DockerRegistryRestClient registryRestClient = dockerRestClientFactory.getDockerRegistryRestClient(dockerConfig);
      Response<DockerPublicImageTagResponse> response =
          registryRestClient.listPublicImageTags(imageName, null, 1).execute();
      if (!isSuccessful(response)) {
        // image not found or user doesn't have permission to list image tags
        throw DockerRegistryUtils.imageNotFoundException(imageName);
      }
    } catch (IOException e) {
      Exception exception = new Exception(e);
      throw NestedExceptionUtils.hintWithExplanationException("The Image was not found.",
          "Check if the image exists and if the permissions are scoped for the authenticated user",
          new InvalidArtifactServerException(ExceptionUtils.getMessage(exception), USER));
    }
    return true;
  }

  public List<BuildDetailsInternal> getBuilds(
      DockerInternalConfig dockerConfig, String imageName, int maxNumberOfBuilds) throws IOException {
    DockerRegistryRestClient registryRestClient = dockerRestClientFactory.getDockerRegistryRestClient(dockerConfig);
    Response<DockerPublicImageTagResponse> response =
        registryRestClient.listPublicImageTags(imageName, null, maxNumberOfBuilds).execute();

    if (!isSuccessful(response)) {
      throw NestedExceptionUtils.hintWithExplanationException("Unable to fetch the tags for the image",
          "Check if the image exists and if the permissions are scoped for the authenticated user",
          new InvalidArtifactServerException(response.message(), USER));
    }

    return paginate(response.body(), dockerConfig, imageName, registryRestClient, maxNumberOfBuilds);
  }

  /**
   * Paginates through the results of tags and accumulates them in one list.
   *
   * @param tagsPage response page from the APO
   * @param dockerConfig
   * @param limit maximum build to paginate upto. A repo like library/node has 1500+ builds,
   *              we might not want to show all of them on UI.
   * @throws IOException
   * @return
   */

  @VisibleForTesting
  List<BuildDetailsInternal> paginate(DockerPublicImageTagResponse tagsPage, DockerInternalConfig dockerConfig,
      String imageName, DockerRegistryRestClient registryRestClient, int limit) throws IOException {
    // process first page
    List<BuildDetailsInternal> details = processPage(tagsPage, dockerConfig, imageName);

    if (details.size() >= limit || tagsPage == null || tagsPage.getNext() == null) {
      return details.stream().limit(limit).collect(Collectors.toList());
    }

    HttpUrl nextPageUrl = HttpUrl.parse(tagsPage.getNext());
    String nextPageNum = nextPageUrl == null ? null : nextPageUrl.queryParameter("page");

    // process rest of pages
    while (EmptyPredicate.isNotEmpty(nextPageNum)) {
      Response<DockerPublicImageTagResponse> pageResponse =
          registryRestClient.listPublicImageTags(imageName, Integer.valueOf(nextPageNum), limit).execute();

      if (!isSuccessful(pageResponse)) {
        throw NestedExceptionUtils.hintWithExplanationException("Unable to fetch the tags for the image",
            "Check if the image exists and if the permissions are scoped for the authenticated user",
            new InvalidArtifactServerException(pageResponse.message(), USER));
      }

      DockerPublicImageTagResponse page = pageResponse.body();
      List<BuildDetailsInternal> pageDetails = processPage(page, dockerConfig, imageName);
      details.addAll(pageDetails);

      if (details.size() >= limit || page == null || page.getNext() == null) {
        break;
      }

      nextPageUrl = HttpUrl.parse(page.getNext());
      nextPageNum = nextPageUrl == null ? null : nextPageUrl.queryParameter("page");
    }

    return details.stream().limit(limit).collect(Collectors.toList());
  }

  private List<BuildDetailsInternal> processPage(
      DockerPublicImageTagResponse publicImageTags, DockerInternalConfig dockerConfig, String imageName) {
    if (publicImageTags != null && EmptyPredicate.isNotEmpty(publicImageTags.getResults())) {
      return publicImageTags.getResults()
          .stream()
          .map(tag -> processSingleResultResponse(tag, imageName, dockerConfig))
          .collect(Collectors.toList());

    } else {
      if (publicImageTags == null) {
        log.warn("Docker public image tag response was null.");
      } else {
        log.warn("Docker public image tag response had an empty or missing tag list.");
      }
      return Collections.emptyList();
    }
  }

  private BuildDetailsInternal processSingleResultResponse(
      DockerPublicImageTagResponse.Result publicImageTag, String imageName, DockerInternalConfig dockerConfig) {
    String tagUrl = dockerConfig.getDockerRegistryUrl().endsWith("/")
        ? dockerConfig.getDockerRegistryUrl() + imageName + "/tags/"
        : dockerConfig.getDockerRegistryUrl() + "/" + imageName + "/tags/";

    String domainName = Http.getDomainWithPort(dockerConfig.getDockerRegistryUrl());
    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.IMAGE,
        (domainName == null || domainName.endsWith("/") ? domainName : domainName.concat("/")) + imageName + ":"
            + publicImageTag.getName());
    metadata.put(ArtifactMetadataKeys.TAG, publicImageTag.getName());
    return BuildDetailsInternal.builder()
        .number(publicImageTag.getName())
        .buildUrl(tagUrl + publicImageTag.getName())
        .uiDisplayName("Tag# " + publicImageTag.getName())
        .metadata(metadata)
        .build();
  }

  public ArtifactMetaInfo getArtifactMetaInfo(
      DockerInternalConfig dockerConfig, String imageName, String buildNo, boolean shouldFetchDockerV2DigestSHA256) {
    DockerRegistryRestClient registryRestClient = dockerRestClientFactory.getDockerRegistryRestClient(dockerConfig);
    Function<Headers, String> getToken = headers -> getToken(headers, registryRestClient);
    return dockerRegistryUtils.getArtifactMetaInfo(
        dockerConfig, registryRestClient, getToken, "", imageName, buildNo, shouldFetchDockerV2DigestSHA256);
  }

  public List<Map<String, String>> getLabels(
      DockerInternalConfig dockerConfig, String imageName, List<String> buildNos) {
    DockerRegistryRestClient registryRestClient = dockerRestClientFactory.getDockerRegistryRestClient(dockerConfig);
    Function<Headers, String> getToken = headers -> getToken(headers, registryRestClient);
    return dockerRegistryUtils.getLabels(dockerConfig, registryRestClient, getToken, "", imageName, buildNos);
  }

  private String getToken(Headers headers, DockerRegistryRestClient registryRestClient) {
    String authHeaderValue = headers.get("Www-Authenticate");
    if (!cachedBearerTokens.containsKey(authHeaderValue)) {
      DockerRegistryToken dockerRegistryToken = fetchPublicToken(registryRestClient, authHeaderValue);
      if (dockerRegistryToken != null) {
        if (dockerRegistryToken.getExpires_in() != null) {
          cachedBearerTokens.put(authHeaderValue, dockerRegistryToken.getToken(), ExpirationPolicy.CREATED,
              dockerRegistryToken.getExpires_in(), TimeUnit.SECONDS);
        } else {
          return dockerRegistryToken.getToken();
        }
      }
    }
    return cachedBearerTokens.get(authHeaderValue);
  }

  private DockerRegistryToken fetchPublicToken(DockerRegistryRestClient registryRestClient, String authHeaderValue) {
    try {
      Map<String, String> tokens = DockerRegistryUtils.extractAuthChallengeTokens(authHeaderValue);
      if (tokens != null) {
        DockerRegistryToken registryToken =
            registryRestClient.getPublicToken(tokens.get("realm"), tokens.get("service"), tokens.get("scope"))
                .execute()
                .body();
        if (registryToken != null) {
          tokens.putIfAbsent(authHeaderValue, registryToken.getToken());
          return registryToken;
        }
      }
    } catch (IOException e) {
      log.warn("Exception occurred while fetching public token", e);
    }
    return null;
  }
}
