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
import io.harness.artifacts.docker.DockerImageTagResponse;
import io.harness.artifacts.docker.DockerRegistryRestClient;
import io.harness.artifacts.docker.DockerRegistryToken;
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
import io.harness.exception.runtime.DockerHubServerRuntimeException;
import io.harness.exception.runtime.InvalidDockerHubCredentialsRuntimeException;
import io.harness.expression.RegexFunctor;
import io.harness.globalcontex.ErrorHandlingGlobalContextData;
import io.harness.manage.GlobalContextManager;
import io.harness.network.Http;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
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
  private static final String TAG_REGEX_TO_IGNORE = "\\*";
  private final Retry retry;

  private ExpiringMap<String, String> cachedBearerTokens = ExpiringMap.builder().variableExpiration().build();

  public DockerRegistryServiceImpl() {
    final RetryConfig config =
        RetryConfig.custom().maxAttempts(5).intervalFunction(IntervalFunction.ofExponentialBackoff()).build();
    this.retry = Retry.of("DockerRegistry", config);
  }

  @Override
  public List<BuildDetailsInternal> getBuilds(
      DockerInternalConfig dockerConfig, String imageName, int maxNumberOfBuilds, String tagRegex) {
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
    // Here we ignore tagRegex if it is equal to TAG_REGEX_TO_IGNORE - This is because DockerRegistry triggers are
    // using tagRegex as "\\*" by default. Will remove this when triggers are fixed to have correct regex in place
    if (tagRegex != null && !TAG_REGEX_TO_IGNORE.equals(tagRegex)) {
      buildDetails = buildDetails.stream()
                         .filter(build -> new RegexFunctor().match(tagRegex, build.getNumber()))
                         .collect(Collectors.toList());
    }
    // Sorting at build tag for docker artifacts.
    // Don't change this order.
    return buildDetails.stream().sorted(new BuildDetailsInternalComparatorAscending()).collect(toList());
  }

  private List<BuildDetailsInternal> getBuildDetails(DockerInternalConfig dockerConfig, String imageName)
      throws Exception {
    DockerRegistryRestClient registryRestClient = dockerRestClientFactory.getDockerRegistryRestClient(dockerConfig);
    String basicAuthHeader = getBasicAuthHeader(dockerConfig, true);
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
      response = listImageTagsByUrl(registryRestClient, authHeader, nextPageUrl);
      if (DockerRegistryUtils.fallbackToTokenAuth(response.code(), dockerConfig)) { // unauthorized
        token = getToken(dockerConfig, response.headers(), registryRestClient);
        authHeader = BEARER + token;
        response = listImageTagsByUrl(registryRestClient, authHeader, nextPageUrl);
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

  @VisibleForTesting
  Response<DockerImageTagResponse> listImageTagsByUrl(
      DockerRegistryRestClient registryRestClient, String authHeader, String nextPageUrl) throws Exception {
    return Retry.decorateCallable(retry, () -> registryRestClient.listImageTagsByUrl(authHeader, nextPageUrl).execute())
        .call();
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

  private static BuildDetailsInternal processBuildResponse(String url, String imageName, String tag) {
    String tagUrl = url.endsWith("/") ? url + imageName + "/tags/" : url + "/" + imageName + "/tags/";
    String domainName = Http.getDomainWithPort(url);
    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.IMAGE,
        (domainName == null || domainName.endsWith("/") ? domainName : domainName.concat("/")) + imageName + ":" + tag);
    metadata.put(ArtifactMetadataKeys.TAG, tag);
    return BuildDetailsInternal.builder()
        .number(tag)
        .buildUrl(tagUrl + tag)
        .uiDisplayName("Tag# " + tag)
        .metadata(metadata)
        .build();
  }

  @Override
  public List<Map<String, String>> getLabels(
      DockerInternalConfig dockerConfig, String imageName, List<String> buildNos) {
    if (!dockerConfig.hasCredentials()) {
      return dockerPublicRegistryProcessor.getLabels(dockerConfig, imageName, buildNos);
    }

    DockerRegistryRestClient registryRestClient = dockerRestClientFactory.getDockerRegistryRestClient(dockerConfig);
    String authHeader = getBasicAuthHeader(dockerConfig, true);
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
    List<BuildDetailsInternal> builds = getBuilds(dockerConfig, imageName, MAX_NUMBER_OF_BUILDS, tagRegex);
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
      String basicAuthHeader = getBasicAuthHeader(dockerConfig, true);
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
      DockerRegistryRestClient registryRestClient = dockerRestClientFactory.getDockerRegistryRestClient(dockerConfig);
      String authHeader = getBasicAuthHeader(dockerConfig, true);
      Function<Headers, String> getToken = headers -> getToken(dockerConfig, headers, registryRestClient);
      // Note: We try & fetch the labels. If we cannot fetch the labels that means the image does not exist
      DockerRegistryUtils.getSingleTagLabels(dockerConfig, registryRestClient, getToken, authHeader, imageName, tag);
      return processBuildResponse(dockerConfig.getDockerRegistryUrl(), imageName, tag);
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException("Unable to fetch the given tag for the image",
          "The tag provided for the image may be incorrect.",
          new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER));
    }
  }

  public static String generateConnectivityUrl(String url, DockerRegistryProviderType providerType) {
    if (DockerRegistryProviderType.HARBOR.equals(providerType)) {
      return url.concat(url.endsWith("/") ? "api/v2.0/ping" : "/api/v2.0/ping");
    } else if (!(url.endsWith("/v2") || url.endsWith("/v2/"))) {
      return url.endsWith("/") ? url.concat("v2") : url.concat("/v2");
    }
    return url;
  }

  @Override
  public boolean validateCredentials(DockerInternalConfig dockerConfig) {
    String connectableHttpUrl =
        generateConnectivityUrl(dockerConfig.getDockerRegistryUrl(), dockerConfig.getProviderType());
    if (!connectableHttpUrl(connectableHttpUrl)) {
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
      Response response;
      DockerRegistryToken dockerRegistryToken;
      try {
        registryRestClient = dockerRestClientFactory.getDockerRegistryRestClient(dockerConfig);
        basicAuthHeader = getBasicAuthHeader(dockerConfig, true);
        response = registryRestClient.getApiVersion(basicAuthHeader).execute();
        if (DockerRegistryUtils.fallbackToTokenAuth(response.code(), dockerConfig)) { // unauthorized
          dockerRegistryToken = fetchToken(dockerConfig, registryRestClient, response.headers());
          if (dockerRegistryToken != null) {
            String token = dockerRegistryToken.getToken();
            if (dockerRegistryUtils.isAcrContainerRegistry(dockerConfig) && isEmpty(token)) {
              token = dockerRegistryToken.getAccess_token();
            }
            response = registryRestClient.getApiVersion(BEARER + token).execute();
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
  public ArtifactMetaInfo getArtifactMetaInfo(
      DockerInternalConfig dockerConfig, String imageName, String tag, boolean shouldFetchDockerV2DigestSHA256) {
    if (!dockerConfig.hasCredentials()) {
      return dockerPublicRegistryProcessor.getArtifactMetaInfo(
          dockerConfig, imageName, tag, shouldFetchDockerV2DigestSHA256);
    }
    DockerRegistryRestClient registryRestClient = dockerRestClientFactory.getDockerRegistryRestClient(dockerConfig);
    String authHeader = getBasicAuthHeader(dockerConfig, true);
    Function<Headers, String> getToken = headers -> getToken(dockerConfig, headers, registryRestClient);
    return dockerRegistryUtils.getArtifactMetaInfo(
        dockerConfig, registryRestClient, getToken, authHeader, imageName, tag, shouldFetchDockerV2DigestSHA256);
  }

  @VisibleForTesting
  boolean handleValidateCredentialsEndingWithSlash(
      DockerRegistryRestClient registryRestClient, DockerInternalConfig dockerConfig) {
    try {
      // This is special case for repositories that require "/v2/" path for getting API version . Eg. Harbor docker
      // registry We get an IO exception with '/v2' path so we are retrying with forward slash API
      String basicAuthHeader = getBasicAuthHeader(dockerConfig, true);
      Response response = registryRestClient.getApiVersionEndingWithForwardSlash(basicAuthHeader).execute();
      if (DockerRegistryUtils.fallbackToTokenAuth(response.code(), dockerConfig)) { // unauthorized
        DockerRegistryToken dockerRegistryToken = fetchToken(dockerConfig, registryRestClient, response.headers());
        if (dockerRegistryToken != null) {
          String token = dockerRegistryToken.getToken();
          if (dockerRegistryUtils.isAcrContainerRegistry(dockerConfig) && isEmpty(token)) {
            token = dockerRegistryToken.getAccess_token();
          }
          response = registryRestClient.getApiVersionEndingWithForwardSlash(BEARER + token).execute();
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
          "Check if the provided credentials, provider type are correct",
          new InvalidArtifactServerException(ExceptionUtils.getMessage(exception), USER));
    }
  }

  public String getToken(
      DockerInternalConfig dockerConfig, Headers headers, DockerRegistryRestClient registryRestClient) {
    String authHeaderValue = headers.get(AUTHENTICATE_HEADER);
    if (!cachedBearerTokens.containsKey(authHeaderValue)) {
      DockerRegistryToken dockerRegistryToken = fetchToken(dockerConfig, registryRestClient, headers);
      if (dockerRegistryToken != null) {
        String token = dockerRegistryToken.getToken();
        if (dockerRegistryUtils.isAcrContainerRegistry(dockerConfig) && isEmpty(token)) {
          token = dockerRegistryToken.getAccess_token();
        }
        if (dockerRegistryToken.getExpires_in() != null) {
          cachedBearerTokens.put(
              authHeaderValue, token, ExpirationPolicy.CREATED, dockerRegistryToken.getExpires_in(), TimeUnit.SECONDS);
        } else {
          return token;
        }
      }
    }
    return cachedBearerTokens.get(authHeaderValue);
  }

  private DockerRegistryToken fetchToken(
      DockerInternalConfig config, DockerRegistryRestClient registryRestClient, Headers headers) {
    String basicAuthHeader = getBasicAuthHeader(config, false);
    String authHeaderValue = headers.get(AUTHENTICATE_HEADER);
    try {
      Map<String, String> tokens = DockerRegistryUtils.extractAuthChallengeTokens(authHeaderValue);
      if (tokens != null) {
        DockerRegistryToken registryToken = fetchTokenWithRetry(registryRestClient, basicAuthHeader, tokens);
        if (registryToken != null) {
          if (dockerRegistryUtils.isAcrContainerRegistry(config) && isEmpty(registryToken.getToken())) {
            tokens.putIfAbsent(authHeaderValue, registryToken.getAccess_token());
          } else {
            tokens.putIfAbsent(authHeaderValue, registryToken.getToken());
          }
          return registryToken;
        }
      } else {
        // Handle GitHub Container Registry. Refer to https://harness.atlassian.net/browse/CDC-14595 for more details
        if (DockerRegistryUtils.isGithubContainerRegistry(config)) {
          DockerRegistryToken registryToken =
              registryRestClient.getGithubContainerRegistryToken(basicAuthHeader).execute().body();
          if (registryToken != null) {
            return registryToken;
          }
        }
      }
    } catch (Exception e) {
      log.warn("Exception occurred while fetching token", e);
    }
    return null;
  }

  @VisibleForTesting
  DockerRegistryToken fetchTokenWithRetry(DockerRegistryRestClient registryRestClient, String basicAuthHeader,
      Map<String, String> tokens) throws Exception {
    final Callable<DockerRegistryToken> callable = Retry.decorateCallable(retry,
        ()
            -> registryRestClient
                   .getToken(basicAuthHeader, tokens.get("realm"), tokens.get("service"), tokens.get("scope"))
                   .execute()
                   .body());
    return callable.call();
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
      case 403:
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
    /*
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

  private String getBasicAuthHeader(DockerInternalConfig config, boolean firstAttempt) {
    // ACR does not return full Www-Authenticate header with auth instructions if you pass Basic auth header to its API
    // So we return basic auth header as null in case this is a first attempt to call ACR
    if (firstAttempt && dockerRegistryUtils.isAcrContainerRegistry(config)) {
      return null;
    }
    return Credentials.basic(config.getUsername(), config.getPassword());
  }
}
