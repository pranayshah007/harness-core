/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifacts.docker.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.network.Http.connectableHttpUrl;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorAscending;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDescending;
import io.harness.artifacts.docker.DockerRegistryRestClient;
import io.harness.artifacts.docker.HarborRestClient;
import io.harness.artifacts.docker.beans.DockerInternalConfig;
import io.harness.artifacts.docker.client.DockerRestClientFactory;
import io.harness.artifacts.docker.client.DockerRestClientFactoryImpl;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.context.MdcGlobalContextData;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.docker.DockerRegistryProviderType;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionMetadataKeys;
import io.harness.exception.runtime.DockerHubInvalidImageRuntimeRuntimeException;
import io.harness.exception.runtime.DockerHubInvalidTagRuntimeRuntimeException;
import io.harness.exception.runtime.DockerHubServerRuntimeException;
import io.harness.exception.runtime.InvalidDockerHubCredentialsRuntimeException;
import io.harness.expression.RegexFunctor;
import io.harness.globalcontex.ErrorHandlingGlobalContextData;
import io.harness.manage.GlobalContextManager;
import io.harness.network.Http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import okhttp3.Credentials;
import okhttp3.Headers;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;

/**
 * Created by anubhaw on 1/6/17.
 */
@OwnedBy(CDC)
@Singleton
@Slf4j
public class DockerRegistryServiceImpl implements DockerRegistryService {
  public static final String BEARER = "Bearer ";
  @Inject private DockerPublicRegistryProcessor dockerPublicRegistryProcessor;
  @Inject private DockerRegistryUtils dockerRegistryUtils;
  @Inject private DockerRestClientFactory dockerRestClientFactory;
  private static final String AUTHENTICATE_HEADER = "Www-Authenticate";
  private static final int MAX_NUMBER_OF_BUILDS = 250;

  private ExpiringMap<String, String> cachedBearerTokens = ExpiringMap.builder().variableExpiration().build();

  @Override
  public List<BuildDetailsInternal> getBuilds(
      DockerInternalConfig dockerConfig, String imageName, int maxNumberOfBuilds) {
    List<BuildDetailsInternal> buildDetails;
    try {
      if (dockerConfig.hasCredentials()) {
        buildDetails = getBuildDetails(dockerConfig, imageName);
      } else {
        buildDetails = dockerPublicRegistryProcessor.getBuilds(dockerConfig, imageName, maxNumberOfBuilds);
      }
    } catch (DockerHubServerRuntimeException ex) {
      throw ex;
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException("Could not fetch tags for the image",
          "Check if the image exists and if the permissions are scoped for the authenticated user",
          new ArtifactServerException(ExceptionUtils.getMessage(e), e, WingsException.USER));
    }
    // Sorting at build tag for docker artifacts.
    // Don't change this order.
    return buildDetails.stream().sorted(new BuildDetailsInternalComparatorAscending()).collect(toList());
  }

  private List<BuildDetailsInternal> getBuildDetails(DockerInternalConfig dockerConfig, String imageName)
      throws IOException {
    DockerRegistryRestClient registryRestClient = dockerRestClientFactory.getDockerRegistryRestClient(dockerConfig);
    String basicAuthHeader = Credentials.basic(dockerConfig.getUsername(), dockerConfig.getPassword());
    List<BuildDetailsInternal> buildDetails = new ArrayList<>();
    String token = null;
    String authHeader = basicAuthHeader;
    Response<DockerImageTagResponse> response = registryRestClient.listImageTags(authHeader, imageName).execute();
    if (DockerRegistryUtils.fallbackToTokenAuth(response.code(), dockerConfig)) { // unauthorized
      token = getToken(dockerConfig, response.headers(), registryRestClient);
      ErrorHandlingGlobalContextData globalContextData =
          GlobalContextManager.get(ErrorHandlingGlobalContextData.IS_SUPPORTED_ERROR_FRAMEWORK);
      if (token == null) {
        if (globalContextData != null && globalContextData.isSupportedErrorFramework()) {
          throw new InvalidDockerHubCredentialsRuntimeException(
              "Unable to validate with given credentials. invalid username or password");
        }
      }
      authHeader = BEARER + token;
      response = registryRestClient.listImageTags(authHeader, imageName).execute();
      if (response.code() == 401) {
        if (globalContextData != null && globalContextData.isSupportedErrorFramework()) {
          Map<String, String> imageDataMap = new HashMap<>();
          imageDataMap.put(ExceptionMetadataKeys.IMAGE_NAME.name(), imageName);
          imageDataMap.put(ExceptionMetadataKeys.URL.name(), dockerConfig.getDockerRegistryUrl() + imageName);
          MdcGlobalContextData mdcGlobalContextData = MdcGlobalContextData.builder().map(imageDataMap).build();
          GlobalContextManager.upsertGlobalContextRecord(mdcGlobalContextData);
          throw new DockerHubInvalidImageRuntimeRuntimeException(
              "Docker image [" + imageName + "] not found in registry [" + dockerConfig.getDockerRegistryUrl() + "]");
        }
        throw DockerRegistryUtils.unauthorizedException();
      }
    }

    if (!isSuccessful(response)) {
      throw NestedExceptionUtils.hintWithExplanationException("Unable to fetch the tags for the image",
          "Check if the image exists and if the permissions are scoped for the authenticated user",
          new InvalidArtifactServerException(response.message(), USER));
    }

    DockerImageTagResponse dockerImageTagResponse = response.body();
    if (dockerImageTagResponse == null || isEmpty(dockerImageTagResponse.getTags())) {
      log.warn("There are no tags available for the imageName {}", imageName);
      return buildDetails;
    }
    buildDetails.addAll(processBuildResponse(dockerImageTagResponse, dockerConfig, imageName));
    // TODO: Limit the no of tags
    String baseUrl = response.raw().request().url().toString();
    while (true) {
      String nextLink = findNextLink(response.headers());
      if (nextLink == null) {
        if (buildDetails.size() > MAX_NO_OF_TAGS_PER_IMAGE) {
          buildDetails.subList(0, buildDetails.size() - MAX_NO_OF_TAGS_PER_IMAGE).clear();
        }
        return buildDetails;
      } else {
        log.info("Using pagination to fetch all the builds. The no of builds fetched so far {}", buildDetails.size());
      }
      int queryParamIndex = nextLink.indexOf('?');
      String nextPageUrl =
          queryParamIndex == -1 ? baseUrl.concat(nextLink) : baseUrl.concat(nextLink.substring(queryParamIndex));
      response = registryRestClient.listImageTagsByUrl(authHeader, nextPageUrl).execute();
      if (DockerRegistryUtils.fallbackToTokenAuth(response.code(), dockerConfig)) { // unauthorized
        token = getToken(dockerConfig, response.headers(), registryRestClient);
        authHeader = BEARER + token;
        response = registryRestClient.listImageTagsByUrl(authHeader, nextPageUrl).execute();
      }
      dockerImageTagResponse = response.body();
      if (dockerImageTagResponse == null || isEmpty(dockerImageTagResponse.getTags())) {
        log.info("There are no more tags available for the imageName {}. Returning tags", imageName);
        return buildDetails;
      }
      buildDetails.addAll(processBuildResponse(dockerImageTagResponse, dockerConfig, imageName));
      if (buildDetails.size() > MAX_NO_OF_TAGS_PER_IMAGE) {
        log.warn(
            "Image name {} has more than {} tags. We might miss some new tags", imageName, MAX_NO_OF_TAGS_PER_IMAGE);
        buildDetails.subList(0, buildDetails.size() - MAX_NO_OF_TAGS_PER_IMAGE).clear();
        break;
      }
    }
    return buildDetails;
  }

  private List<BuildDetailsInternal> processBuildResponse(
      DockerImageTagResponse dockerImageTagResponse, DockerInternalConfig dockerConfig, String imageName) {
    String tagUrl = dockerConfig.getDockerRegistryUrl().endsWith("/")
        ? dockerConfig.getDockerRegistryUrl() + imageName + "/tags/"
        : dockerConfig.getDockerRegistryUrl() + "/" + imageName + "/tags/";

    String domainName = Http.getDomainWithPort(dockerConfig.getDockerRegistryUrl());

    return dockerImageTagResponse.getTags()
        .stream()
        .map(tag -> {
          Map<String, String> metadata = new HashMap<>();
          metadata.put(ArtifactMetadataKeys.IMAGE,
              (domainName == null || domainName.endsWith("/") ? domainName : domainName.concat("/")) + imageName + ":"
                  + tag);
          metadata.put(ArtifactMetadataKeys.TAG, tag);
          return BuildDetailsInternal.builder()
              .number(tag)
              .buildUrl(tagUrl + tag)
              .uiDisplayName("Tag# " + tag)
              .metadata(metadata)
              .build();
        })
        .collect(toList());
  }

  @Override
  public List<Map<String, String>> getLabels(
      DockerInternalConfig dockerConfig, String imageName, List<String> buildNos) {
    if (!dockerConfig.hasCredentials()) {
      return dockerPublicRegistryProcessor.getLabels(dockerConfig, imageName, buildNos);
    }

    DockerRegistryRestClient registryRestClient = dockerRestClientFactory.getDockerRegistryRestClient(dockerConfig);
    String authHeader = Credentials.basic(dockerConfig.getUsername(), dockerConfig.getPassword());
    Function<Headers, String> getToken = headers -> getToken(dockerConfig, headers, registryRestClient);
    return dockerRegistryUtils.getLabels(dockerConfig, registryRestClient, getToken, authHeader, imageName, buildNos);
  }

  @Override
  public BuildDetailsInternal getLastSuccessfulBuild(DockerInternalConfig dockerConfig, String imageName) {
    return null;
  }

  @Override
  public BuildDetailsInternal getLastSuccessfulBuildFromRegex(
      DockerInternalConfig dockerConfig, String imageName, String tagRegex) {
    List<BuildDetailsInternal> builds = getBuilds(dockerConfig, imageName, MAX_NUMBER_OF_BUILDS);
    builds = builds.stream()
                 .filter(build -> new RegexFunctor().match(tagRegex, build.getNumber()))
                 .sorted(new BuildDetailsInternalComparatorDescending())
                 .collect(Collectors.toList());

    if (builds.isEmpty()) {
      throw NestedExceptionUtils.hintWithExplanationException("Could not get the last successful build",
          "There are probably no successful builds for this image & check if the tag filter regex is correct",
          new InvalidArtifactServerException(
              "There are no builds for this image: " + imageName + " and tagRegex: " + tagRegex, USER));
    }
    return builds.get(0);
  }

  @Override
  public boolean verifyImageName(DockerInternalConfig dockerConfig, String imageName) {
    if (dockerConfig.hasCredentials()) {
      return checkImageName(dockerConfig, imageName);
    }
    return dockerPublicRegistryProcessor.verifyImageName(dockerConfig, imageName);
  }

  @Override
  public BuildDetailsInternal verifyBuildNumber(DockerInternalConfig dockerConfig, String imageName, String tag) {
    try {
      if (!dockerConfig.hasCredentials()) {
        return dockerPublicRegistryProcessor.verifyBuildNumber(dockerConfig, imageName, tag);
      }
      return getBuildNumber(dockerConfig, imageName, tag);
    } catch (IOException e) {
      throw NestedExceptionUtils.hintWithExplanationException("Unable to fetch the given tag for the image",
          "The tag provided for the image may be incorrect.",
          new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER));
    }
  }

  private boolean checkImageName(DockerInternalConfig dockerConfig, String imageName) {
    try {
      DockerRegistryRestClient registryRestClient = dockerRestClientFactory.getDockerRegistryRestClient(dockerConfig);
      String basicAuthHeader = Credentials.basic(dockerConfig.getUsername(), dockerConfig.getPassword());
      Response<DockerImageTagResponse> response =
          registryRestClient.listImageTags(basicAuthHeader, imageName).execute();
      if (DockerRegistryUtils.fallbackToTokenAuth(response.code(), dockerConfig)) { // unauthorized
        String token = getToken(dockerConfig, response.headers(), registryRestClient);
        response = registryRestClient.listImageTags(BEARER + token, imageName).execute();
      }
      if (!isSuccessful(response)) {
        // Image not found or user doesn't have permission to list image tags.
        throw DockerRegistryUtils.imageNotFoundException(imageName);
      }
    } catch (IOException e) {
      throw NestedExceptionUtils.hintWithExplanationException("The Image was not found.",
          "Check if the image exists and if the permissions are scoped for the authenticated user",
          new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER));
    }
    return true;
  }

  private BuildDetailsInternal getBuildNumber(DockerInternalConfig dockerConfig, String imageName, String tag) {
    try {
      List<BuildDetailsInternal> builds = getBuildDetails(dockerConfig, imageName);
      builds = builds.stream().filter(build -> build.getNumber().equals(tag)).collect(Collectors.toList());

      if (builds.size() != 1) {
        Map<String, String> imageDataMap = new HashMap<>();
        imageDataMap.put(ExceptionMetadataKeys.IMAGE_NAME.name(), imageName);
        imageDataMap.put(ExceptionMetadataKeys.IMAGE_TAG.name(), tag);
        String url = dockerConfig.getDockerRegistryUrl() + "/v2/" + imageName + "/" + tag;
        imageDataMap.put(ExceptionMetadataKeys.URL.name(), url);
        MdcGlobalContextData mdcGlobalContextData = MdcGlobalContextData.builder().map(imageDataMap).build();
        GlobalContextManager.upsertGlobalContextRecord(mdcGlobalContextData);
        throw new DockerHubInvalidTagRuntimeRuntimeException("Could not find tag [" + tag + "] for Docker image ["
            + imageName + "] on registry [" + dockerConfig.getDockerRegistryUrl() + "]");
      }
      return builds.get(0);
    } catch (IOException e) {
      throw NestedExceptionUtils.hintWithExplanationException("Unable to fetch the given tag for the image",
          "The tag provided for the image may be incorrect.",
          new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER));
    }
  }

  @Override
  public boolean validateCredentials(DockerInternalConfig dockerConfig) {
    String dockerRegistryUrl = dockerConfig.getDockerRegistryUrl();
    if (!(dockerRegistryUrl.endsWith("/v2") || dockerRegistryUrl.endsWith("/v2/"))) {
      dockerRegistryUrl =
          dockerRegistryUrl.endsWith("/") ? dockerRegistryUrl.concat("v2") : dockerRegistryUrl.concat("/v2");
    }
    if (!connectableHttpUrl(dockerRegistryUrl)) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Check if the Docker Registry URL is correct & reachable from your delegate(s)",
          "The given Docker Registry URL may be incorrect or not reachable from your delegate(s)",
          new InvalidArtifactServerException(
              "Could not reach Docker Registry at : " + dockerConfig.getDockerRegistryUrl(), USER));
    }
    if (dockerConfig.hasCredentials()) {
      if (isEmpty(dockerConfig.getPassword())) {
        throw NestedExceptionUtils.hintWithExplanationException("Invalid Docker Credentials",
            "Password field value cannot be empty if username field is not empty",
            new InvalidArtifactServerException("Password is a required field along with Username", USER));
      }
      if (DockerRegistryProviderType.HARBOR.equals(dockerConfig.getProviderType())) {
        return validateHarborConnector(dockerConfig);
      }
      DockerRegistryRestClient registryRestClient = null;
      String basicAuthHeader;
      String authHeaderValue;
      Response response;
      DockerRegistryToken dockerRegistryToken;
      try {
        registryRestClient = dockerRestClientFactory.getDockerRegistryRestClient(dockerConfig);
        basicAuthHeader = Credentials.basic(dockerConfig.getUsername(), dockerConfig.getPassword());
        response = registryRestClient.getApiVersion(basicAuthHeader).execute();
        if (DockerRegistryUtils.fallbackToTokenAuth(response.code(),
                dockerConfig)) { // unauthorized
          authHeaderValue = response.headers().get(AUTHENTICATE_HEADER);
          dockerRegistryToken = fetchToken(dockerConfig, registryRestClient, basicAuthHeader, authHeaderValue);
          if (dockerRegistryToken != null) {
            response = registryRestClient.getApiVersion(BEARER + dockerRegistryToken.getToken()).execute();
          }
        }
        if (response.code() == 404) { // https://harness.atlassian.net/browse/CDC-11979
          return handleValidateCredentialsEndingWithSlash(registryRestClient, dockerConfig);
        }
        return isSuccessful(response);
      } catch (IOException e) {
        log.warn("Failed to fetch apiversion with credentials" + e);
        return handleValidateCredentialsEndingWithSlash(registryRestClient, dockerConfig);
      }
    }
    return true;
  }

  private boolean validateHarborConnector(DockerInternalConfig dockerConfig) {
    HarborRestClient harborRestClient = DockerRestClientFactoryImpl.getHarborRestClient(dockerConfig);
    String basicAuthHeader = Credentials.basic(dockerConfig.getUsername(), dockerConfig.getPassword());
    try {
      Response<List<Object>> response = harborRestClient.getProjects(basicAuthHeader).execute();
      boolean isSuccess = isSuccessful(response);
      if (!isSuccess) {
        return false;
      }
      if (EmptyPredicate.isEmpty(response.body())) {
        throw NestedExceptionUtils.hintWithExplanationException("No Harbor projects found.",
            "Check if the provided credentials are correct & has access to projects",
            new InvalidArtifactServerException("No Harbor projects associated with given user", USER));
      }
    } catch (IOException e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Unable to fetch Harbor projects to validate the connector", "Check if the provided credentials are correct",
          new InvalidArtifactServerException(ExceptionUtils.getMessage(e), USER));
    }
    return true;
  }

  @Override
  public ArtifactMetaInfo getArtifactMetaInfo(DockerInternalConfig dockerConfig, String imageName, String tag) {
    if (!dockerConfig.hasCredentials()) {
      return dockerPublicRegistryProcessor.getArtifactMetaInfo(dockerConfig, imageName, tag);
    }
    DockerRegistryRestClient registryRestClient = dockerRestClientFactory.getDockerRegistryRestClient(dockerConfig);
    String authHeader = Credentials.basic(dockerConfig.getUsername(), dockerConfig.getPassword());
    Function<Headers, String> getToken = headers -> getToken(dockerConfig, headers, registryRestClient);
    return dockerRegistryUtils.getArtifactMetaInfo(
        dockerConfig, registryRestClient, getToken, authHeader, imageName, tag);
  }

  private boolean handleValidateCredentialsEndingWithSlash(
      DockerRegistryRestClient registryRestClient, DockerInternalConfig dockerConfig) {
    try {
      // This is special case for repositories that require "/v2/" path for getting API version . Eg. Harbor docker
      // registry We get an IO exception with '/v2' path so we are retrying with forward slash API
      String basicAuthHeader = Credentials.basic(dockerConfig.getUsername(), dockerConfig.getPassword());
      Response response = registryRestClient.getApiVersionEndingWithForwardSlash(basicAuthHeader).execute();
      if (DockerRegistryUtils.fallbackToTokenAuth(response.code(), dockerConfig)) { // unauthorized
        String authHeaderValue = response.headers().get(AUTHENTICATE_HEADER);
        DockerRegistryToken dockerRegistryToken =
            fetchToken(dockerConfig, registryRestClient, basicAuthHeader, authHeaderValue);
        if (dockerRegistryToken != null) {
          response =
              registryRestClient.getApiVersionEndingWithForwardSlash(BEARER + dockerRegistryToken.getToken()).execute();
        }
      }
      boolean isSuccess = isSuccessful(response);
      if (!isSuccess && response.code() == 404) {
        throw NestedExceptionUtils.hintWithExplanationException(
            "Check with you registry provider for a Docker v2 compliant URL",
            "Provided Docker Registry URL is incorrect",
            new InvalidArtifactServerException("Invalid Docker Registry URL", USER));
      }
      return isSuccess;
    } catch (IOException ioException) {
      Exception exception = new Exception(ioException);
      throw NestedExceptionUtils.hintWithExplanationException("Invalid Credentials",
          "Check if the provided credentials are correct",
          new InvalidArtifactServerException(ExceptionUtils.getMessage(exception), USER));
    }
  }

  private String getToken(
      DockerInternalConfig dockerConfig, Headers headers, DockerRegistryRestClient registryRestClient) {
    String basicAuthHeader = Credentials.basic(dockerConfig.getUsername(), dockerConfig.getPassword());
    String authHeaderValue = headers.get(AUTHENTICATE_HEADER);
    if (!cachedBearerTokens.containsKey(authHeaderValue)) {
      DockerRegistryToken dockerRegistryToken =
          fetchToken(dockerConfig, registryRestClient, basicAuthHeader, authHeaderValue);
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

  private DockerRegistryToken fetchToken(DockerInternalConfig config, DockerRegistryRestClient registryRestClient,
      String basicAuthHeader, String authHeaderValue) {
    try {
      Map<String, String> tokens = DockerRegistryUtils.extractAuthChallengeTokens(authHeaderValue);
      if (tokens != null) {
        DockerRegistryToken registryToken =
            registryRestClient
                .getToken(basicAuthHeader, tokens.get("realm"), tokens.get("service"), tokens.get("scope"))
                .execute()
                .body();
        if (registryToken != null) {
          tokens.putIfAbsent(authHeaderValue, registryToken.getToken());
          return registryToken;
        }
      } else {
        // Handle Github Container Registry. Refer to https://harness.atlassian.net/browse/CDC-14595 for more details
        if (DockerRegistryUtils.isGithubContainerRegistry(config)) {
          DockerRegistryToken registryToken =
              registryRestClient.getGithubContainerRegistryToken(basicAuthHeader).execute().body();
          if (registryToken != null) {
            return registryToken;
          }
        }
      }
    } catch (IOException e) {
      log.warn("Exception occurred while fetching token", e);
    }
    return null;
  }

  public static boolean isSuccessful(Response<?> response) {
    if (response == null) {
      throw new InvalidArtifactServerException("Null response found", USER);
    }

    if (response.isSuccessful()) {
      return true;
    }

    log.error("Request not successful. Reason: {}", response);
    int code = response.code();
    switch (code) {
      case 404:
      case 400:
        return false;
      case 401:
        throw DockerRegistryUtils.unauthorizedException();
      default:
        throw new InvalidArtifactServerException(StringUtils.isNotBlank(response.message())
                ? response.message()
                : String.format("Server responded with the following error code - %d", code),
            USER);
    }
  }

  public static String parseLink(String headerLink) {
    /**
     * Traversing with the pagination e.g.
     * Link:
     * "</v2/myAccount/myfirstrepo/tags/list?next_page=gAAAAABbuZsLNl9W6tAycol_oLvcYeti2w53XnoV3FYyFBkd-TQV3OBiWNJLqp2m8isy3SWusAqA4Y32dHJ7tGi0br18kXEt6nTW306QUFexaXrAGq8KeSc%3D&n=25>;
     * rel="next""
     */
    if (headerLink == null) {
      return null;
    }
    List<String> links = Arrays.stream(headerLink.split(";")).map(String::trim).collect(toList());

    // Replace space with empty string
    links.stream().map(s -> s.replace(" ", "")).collect(toList());
    if (!links.contains("rel=\"next\"")) {
      return null;
    }
    String path = null;
    for (String s : links) {
      if (s.charAt(0) == '<' && s.charAt(s.length() - 1) == '>') {
        path = s;
        break;
      }
    }
    if (path == null || path.length() <= 1) {
      return path;
    }

    String link = path.substring(1, path.length() - 1);

    try {
      URL url = new URL(link);
      link = url.getFile().substring(1);
    } catch (Exception e) {
      // In the case where the link isn't a valid URL, we were passed with the just relative path
    }
    return link.charAt(0) == '/' ? link.replaceFirst("/", "") : link;
  }

  public static String findNextLink(Headers headers) {
    if (headers == null || headers.size() == 0) {
      return null;
    }
    if (headers.get("link") == null) {
      return null;
    }

    return parseLink(headers.get("link"));
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DockerImageTagResponse {
    private String name;
    private List<String> tags;
    private String link;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DockerRegistryToken {
    private String token;
    private String access_token;
    private Integer expires_in;
    private String issued_at;
  }
}
