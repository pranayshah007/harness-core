/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifacts.gcr.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.govern.Switch.unhandled;
import static io.harness.network.Http.getOkHttpClientBuilder;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorAscending;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDescending;
import io.harness.artifacts.docker.beans.DockerImageManifestResponse;
import io.harness.artifacts.gcr.GcrImageTagResponse;
import io.harness.artifacts.gcr.GcrRestClient;
import io.harness.artifacts.gcr.beans.GcrInternalConfig;
import io.harness.context.MdcGlobalContextData;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GcpServerException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionMetadataKeys;
import io.harness.exception.runtime.GcrConnectRuntimeException;
import io.harness.exception.runtime.GcrImageNotFoundRuntimeException;
import io.harness.expression.RegexFunctor;
import io.harness.globalcontex.ErrorHandlingGlobalContextData;
import io.harness.manage.GlobalContextManager;
import io.harness.network.Http;
import io.harness.serializer.JsonUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class GcrApiServiceImpl implements GcrApiService {
  private static final int TIMEOUT = 60; // TODO:: read from config
  public static final int RETRIES = 10; // TODO:: read from config
  String ERROR_MESSAGE = "There was an error reaching the Google container registry";
  String CONNECTION_ERROR_MESSAGE = "The connector or the artifact source may not be setup correctly.";

  public Retry retry;

  public GcrApiServiceImpl() {
    final RetryConfig config = RetryConfig.custom()
                                   .maxAttempts(RETRIES)
                                   .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(1)))
                                   .build();
    this.retry = Retry.of("GCRRegistry", config);

    // Log-on-retry added here to assist investigation of CDS-55120
    Retry.EventPublisher retryEventPublisher = retry.getEventPublisher();
    retryEventPublisher.onRetry(event -> log.warn("Retrying GCR API call. Event: " + event));
  }

  private GcrRestClient getGcrRestClient(String registryHostName) {
    String url = getUrl(registryHostName);
    OkHttpClient okHttpClient = getOkHttpClientBuilder()
                                    .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
                                    .readTimeout(TIMEOUT, TimeUnit.SECONDS)
                                    .proxy(Http.checkAndGetNonProxyIfApplicable(url))
                                    .build();
    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(url)
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(GcrRestClient.class);
  }

  public String getUrl(String gcrHostName) {
    return "https://" + gcrHostName + (gcrHostName.endsWith("/") ? "" : "/");
  }

  @Override
  public List<BuildDetailsInternal> getBuilds(GcrInternalConfig gcpConfig, String imageName, int maxNumberOfBuilds) {
    try {
      Response<GcrImageTagResponse> response =
          listImageTag(getGcrRestClient(gcpConfig.getRegistryHostname()), gcpConfig.getBasicAuthHeader(), imageName);
      checkValidImage(imageName, response);
      return processBuildResponse(gcpConfig.getRegistryHostname(), imageName, response.body());
    } catch (GcrImageNotFoundRuntimeException ex) {
      Map<String, String> imageDataMap = new HashMap<>();
      imageDataMap.put(ExceptionMetadataKeys.IMAGE_NAME.name(), imageName);
      imageDataMap.put(ExceptionMetadataKeys.URL.name(), gcpConfig.getRegistryHostname() + "/" + imageName);
      MdcGlobalContextData mdcGlobalContextData = MdcGlobalContextData.builder().map(imageDataMap).build();
      GlobalContextManager.upsertGlobalContextRecord(mdcGlobalContextData);
      throw ex;
    } catch (IOException e) {
      log.error("getBuilds has thrown IOException for registryHostname [" + gcpConfig.getRegistryHostname()
              + "], imageName [" + imageName + "]",
          e);
      throw handleIOException(gcpConfig, e);
    } catch (InvalidRequestException e) {
      log.error("getBuilds has thrown InvalidRequestException for registryHostname [" + gcpConfig.getRegistryHostname()
              + "], imageName [" + imageName + "]",
          e);
      throw new HintException(ExceptionUtils.getMessage(e));
    } catch (Exception e) {
      log.error("getBuilds has thrown Exception for registryHostname [" + gcpConfig.getRegistryHostname()
              + "], imageName [" + imageName + "]",
          e);
      throw new HintException(ExceptionUtils.getMessage(e));
    }
  }

  private void checkValidImage(String imageName, Response<GcrImageTagResponse> response) throws IOException {
    if (response.code() >= 400) {
      if (response.code() == 404) {
        ErrorHandlingGlobalContextData globalContextData =
            GlobalContextManager.get(ErrorHandlingGlobalContextData.IS_SUPPORTED_ERROR_FRAMEWORK);
        if (response.body() == null) {
          throw new InvalidRequestException(
              "Image name [" + imageName + "] does not exist in Google Container Registry.");
        }
        if (globalContextData != null && globalContextData.isSupportedErrorFramework()
            && response.body().getTags().size() == 0) {
          throw new GcrImageNotFoundRuntimeException(
              "Image name [" + imageName + "] does not exist in Google Container Registry.");
        }
        throw new WingsException(ErrorCode.INVALID_ARGUMENT, USER)
            .addParam("args", "Image name [" + imageName + "] does not exist in Google Container Registry.");
      } else {
        String responseError = response.errorBody() != null ? response.errorBody().string() : "";
        String errorMessage = isNotEmpty(responseError) && responseError.charAt(0) == '{'
            ? JsonUtils.jsonPath(responseError, "concat($..code, $..message)").toString()
            : responseError;

        throw new GcpServerException(
            "Failed to retrieve [" + imageName + "] from Google Container Registry. " + errorMessage);
      }
    }
  }

  private List<BuildDetailsInternal> processBuildResponse(
      String gcrUrl, String imageName, GcrImageTagResponse dockerImageTagResponse) {
    if (dockerImageTagResponse != null && dockerImageTagResponse.getTags() != null) {
      List<BuildDetailsInternal> buildDetails = dockerImageTagResponse.getTags()
                                                    .stream()
                                                    .map(tag -> getBuildDetailsInternal(gcrUrl, imageName, tag))
                                                    .collect(toList());
      // Sorting at build tag for docker artifacts.
      return buildDetails.stream().sorted(new BuildDetailsInternalComparatorAscending()).collect(toList());
    }
    return emptyList();
  }

  private BuildDetailsInternal getBuildDetailsInternal(String registryUrl, String imageName, String tag) {
    Map<String, String> metadata = new HashMap();
    metadata.put(ArtifactMetadataKeys.IMAGE,
        (registryUrl.endsWith("/") ? registryUrl : registryUrl.concat("/")) + imageName + ":" + tag);
    metadata.put(ArtifactMetadataKeys.TAG, tag);
    return BuildDetailsInternal.builder().uiDisplayName("Tag# " + tag).number(tag).metadata(metadata).build();
  }

  @Override
  public boolean verifyImageName(GcrInternalConfig gcpConfig, String imageName) {
    try {
      Response<GcrImageTagResponse> response = getGcrRestClient(gcpConfig.getRegistryHostname())
                                                   .listImageTags(gcpConfig.getBasicAuthHeader(), imageName)
                                                   .execute();
      if (!isSuccessful(response)) {
        // image not found or user doesn't have permission to list image tags
        log.warn("Image name [" + imageName + "] does not exist in Google Container Registry.");
        throw new WingsException(ErrorCode.INVALID_ARGUMENT, USER)
            .addParam("args", "Image name [" + imageName + "] does not exist in Google Container Registry.");
      }
    } catch (IOException e) {
      log.error(ExceptionUtils.getMessage(e), e);
      throw handleIOException(gcpConfig, e);
    }
    return true;
  }

  @Override
  public boolean validateCredentials(GcrInternalConfig gcpConfig, String imageName) {
    try {
      GcrRestClient registryRestClient = getGcrRestClient(gcpConfig.getRegistryHostname());
      return isSuccessful(listImageTag(registryRestClient, gcpConfig.getBasicAuthHeader(), imageName));
    } catch (IOException e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          ERROR_MESSAGE, CONNECTION_ERROR_MESSAGE, new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER));
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          ERROR_MESSAGE, CONNECTION_ERROR_MESSAGE, new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER));
    }
  }

  @Override
  public BuildDetailsInternal getLastSuccessfulBuildFromRegex(
      GcrInternalConfig gcrInternalConfig, String imageName, String tagRegex) {
    List<BuildDetailsInternal> builds = getBuilds(gcrInternalConfig, imageName, MAX_NO_OF_TAGS_PER_IMAGE);
    builds = builds.stream()
                 .filter(build -> new RegexFunctor().match(tagRegex, build.getNumber()))
                 .sorted(new BuildDetailsInternalComparatorDescending())
                 .collect(toList());
    if (builds.isEmpty()) {
      throw new InvalidArtifactServerException(
          "There are no builds for this image: " + imageName + " and tagRegex: " + tagRegex, USER);
    }
    return builds.get(0);
  }

  @Override
  public BuildDetailsInternal verifyBuildNumber(GcrInternalConfig gcrInternalConfig, String imageName, String tag) {
    try {
      Response<DockerImageManifestResponse> response =
          fetchImage(getGcrRestClient(gcrInternalConfig.getRegistryHostname()), gcrInternalConfig.getBasicAuthHeader(),
              imageName, tag);

      if (!isSuccessful(response)) {
        throw new InvalidRequestException("Please provide a valid ImageName or Tag.");
      }

      return getBuildDetailsInternal(gcrInternalConfig.getRegistryHostname(), imageName, tag);
    } catch (IOException e) {
      log.error("verifyBuildNumber has thrown IOException for registryHostname ["
              + gcrInternalConfig.getRegistryHostname() + "], imageName [" + imageName + "]",
          e);
      throw handleIOException(gcrInternalConfig, e);
    } catch (Exception e) {
      log.error("verifyBuildNumber has thrown Exception for registryHostname ["
              + gcrInternalConfig.getRegistryHostname() + "], imageName [" + imageName + "]",
          e);
      throw NestedExceptionUtils.hintWithExplanationException(
          ERROR_MESSAGE, CONNECTION_ERROR_MESSAGE, new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER));
    }
  }

  @VisibleForTesting
  public Response<DockerImageManifestResponse> fetchImage(
      GcrRestClient gcrRestClient, String basicAuthHeader, String imageName, String tag) throws Exception {
    return Retry
        .decorateCallable(retry, () -> gcrRestClient.getImageManifest(basicAuthHeader, imageName, tag).execute())
        .call();
  }

  @VisibleForTesting
  public Response<GcrImageTagResponse> listImageTag(
      GcrRestClient gcrRestClient, String basicAuthHeader, String imageName) throws Exception {
    return Retry.decorateCallable(retry, () -> gcrRestClient.listImageTags(basicAuthHeader, imageName).execute())
        .call();
  }

  private WingsException handleIOException(GcrInternalConfig gcrInternalConfig, IOException e) {
    log.error(
        "GcrApiService has thrown IOException for registryHostname " + gcrInternalConfig.getRegistryHostname(), e);
    ErrorHandlingGlobalContextData globalContextData =
        GlobalContextManager.get(ErrorHandlingGlobalContextData.IS_SUPPORTED_ERROR_FRAMEWORK);
    if (globalContextData != null && globalContextData.isSupportedErrorFramework()) {
      Map<String, String> imageDataMap = new HashMap<>();
      imageDataMap.put(ExceptionMetadataKeys.URL.name(), gcrInternalConfig.getRegistryHostname());
      MdcGlobalContextData mdcGlobalContextData = MdcGlobalContextData.builder().map(imageDataMap).build();
      GlobalContextManager.upsertGlobalContextRecord(mdcGlobalContextData);
      throw new GcrConnectRuntimeException(e.getMessage(), e.getCause());
    }
    throw NestedExceptionUtils.hintWithExplanationException(
        ERROR_MESSAGE, CONNECTION_ERROR_MESSAGE, new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER));
  }

  private boolean isSuccessful(Response<?> response) {
    int code = response.code();
    switch (code) {
      case 200:
        return true;
      case 404:
        return false;
      case 400:
        log.info("Response code {} received. Mostly with Image does not exist", code);
        return false;
      case 403:
        log.info("Response code {} received. User not authorized to access GCR Storage", code);
        throw new InvalidArtifactServerException("User not authorized to access GCR Storage", USER);
      case 401:
        throw new InvalidArtifactServerException("Invalid Google Container Registry credentials", USER);
      default:
        unhandled(code);
    }
    return true;
  }
}
