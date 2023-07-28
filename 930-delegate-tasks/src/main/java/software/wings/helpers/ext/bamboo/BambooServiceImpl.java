/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.bamboo;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.exception.WingsException.USER;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.beans.artifact.ArtifactFileMetadata;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.network.Http;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.stream.StreamUtils;

import software.wings.beans.BambooConfig;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.delegatetasks.collect.artifacts.ArtifactCollectionCommonTaskHelper;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.intfc.security.EncryptionService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Created by anubhaw on 11/29/16.
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(CDC)
@Singleton
@Slf4j
public class BambooServiceImpl implements BambooService {
  @Inject private EncryptionService encryptionService;
  @Inject private TimeLimiter timeLimiter;
  @Inject private ArtifactCollectionCommonTaskHelper artifactCollectionCommonTaskHelper;

  private BambooRestClient getBambooClient(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails) {
    try {
      encryptionService.decrypt(bambooConfig, encryptionDetails, false);
      String bambooUrl = bambooConfig.getBambooUrl();
      if (bambooUrl == null) {
        throw new InvalidArtifactServerException("Invalid Bamboo Server URL");
      }
      if (!bambooUrl.endsWith("/")) {
        bambooUrl = bambooUrl + "/";
      }
      Retrofit retrofit = new Retrofit.Builder()
                              .baseUrl(bambooUrl)
                              .addConverterFactory(JacksonConverterFactory.create())
                              .client(Http.getOkHttpClient(bambooUrl, bambooConfig.isCertValidationRequired()))
                              .build();
      return retrofit.create(BambooRestClient.class);
    } catch (InvalidArtifactServerException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidArtifactServerException("Could not reach Bamboo Server at :" + bambooConfig.getBambooUrl(), e);
    }
  }

  @Override
  public List<String> getJobKeys(
      BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, String planKey) {
    log.info("Retrieving job keys for plan key {}", planKey);
    Call<JsonNode> request =
        getBambooClient(bambooConfig, encryptionDetails)
            .listPlanWithJobDetails(getBasicAuthCredentials(bambooConfig, encryptionDetails), planKey);
    Response<JsonNode> response = null;
    try {
      response = getHttpRequestExecutionResponse(request);
      log.info("Reading job keys for plan key {} success", planKey);
      return extractJobKeyFromNestedProjectResponseJson(response);
    } catch (ArtifactServerException e) {
      throw e;
    } catch (Exception ex) {
      if (response != null && !response.isSuccessful()) {
        IOUtils.closeQuietly(response.errorBody());
      }
      throw new ArtifactServerException(ExceptionUtils.getMessage(ex), ex);
    }
  }

  @Override
  public BuildDetails getLastSuccessfulBuild(
      BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, String planKey) {
    return getLastSuccessfulBuild(bambooConfig, encryptionDetails, planKey, Collections.emptyList());
  }

  @Override
  public BuildDetails getLastSuccessfulBuild(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails,
      String planKey, List<String> artifactPaths) {
    Call<JsonNode> request =
        getBambooClient(bambooConfig, encryptionDetails)
            .lastSuccessfulBuildForJob(getBasicAuthCredentials(bambooConfig, encryptionDetails), planKey);
    Response<JsonNode> response = null;
    try {
      response = getHttpRequestExecutionResponse(request);
      JsonNode resultNode = response.body().at("/results/result");
      if (resultNode != null && resultNode.elements().hasNext()) {
        JsonNode next = resultNode.elements().next();
        String buildUrl = null;
        if (next.get("link") != null) {
          buildUrl = next.get("link").get("href").asText();
        }
        JsonNode buildNumber = next.get("buildNumber");
        if (buildNumber == null) {
          return null;
        }
        List<ArtifactFileMetadata> artifactFileMetadata = new ArrayList<>();
        if (isNotEmpty(artifactPaths)) {
          for (String artifactPath : artifactPaths) {
            artifactFileMetadata.addAll(
                getArtifactFileMetadata(bambooConfig, encryptionDetails, planKey, buildNumber.asText(), artifactPath));
          }
        }
        return aBuildDetails()
            .withNumber(buildNumber.asText())
            .withRevision(next.get("vcsRevisionKey") != null ? next.get("vcsRevisionKey").asText() : null)
            .withBuildUrl(buildUrl)
            .withUiDisplayName("Build# " + buildNumber.asText())
            .withArtifactDownloadMetadata(artifactFileMetadata)
            .build();
      }
    } catch (ArtifactServerException e) {
      throw e;
    } catch (Exception e) {
      if (response != null && !response.isSuccessful()) {
        IOUtils.closeQuietly(response.errorBody());
      }
      throw new ArtifactServerException(ExceptionUtils.getRootCauseMessage(e), e);
    }
    return null;
  }

  /**
   * Gets basic auth credentials.
   *
   * @param bambooConfig the bamboo config
   * @return the basic auth credentials
   */
  String getBasicAuthCredentials(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(bambooConfig, encryptionDetails, false);
    if (isEmpty(bambooConfig.getPassword())) {
      throw new InvalidRequestException("Failed to decrypt password for Bamboo connector");
    }
    return Credentials.basic(bambooConfig.getUsername(), new String(bambooConfig.getPassword()));
  }

  @Override
  public Map<String, String> getPlanKeys(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails) {
    return getPlanKeys(bambooConfig, encryptionDetails, 1000);
  }

  private Map<String, String> getPlanKeys(
      BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, int maxResults) {
    try {
      return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(110), () -> {
        BambooRestClient bambooRestClient = getBambooClient(bambooConfig, encryptionDetails);
        log.info("Retrieving plan keys for bamboo server {}", bambooConfig);
        log.info("Fetching plans starting at index: [0] from bamboo server {}", bambooConfig.getBambooUrl());
        Call<JsonNode> request =
            bambooRestClient.listProjectPlans(getBasicAuthCredentials(bambooConfig, encryptionDetails), maxResults);
        Map<String, String> planNameMap = new HashMap<>();
        Response<JsonNode> response = null;
        int size = 0;
        try {
          response = getHttpRequestExecutionResponse(request);
          if (response.body() != null) {
            JsonNode plansJsonNode = response.body().at("/plans");
            size = plansJsonNode.get("size").intValue();
            JsonNode planJsonNode = response.body().at("/plans/plan");
            planJsonNode.elements().forEachRemaining(jsonNode -> {
              String planKey = jsonNode.get("key").asText();
              String planName = jsonNode.get("shortName").asText();
              planNameMap.put(planKey, planName);
            });
          }
          if (maxResults != 1 && size > maxResults) {
            int maxPlansToFetch = Math.min(size, 10000);
            log.info("Total no. of plans to fetch: [{}]", maxPlansToFetch);
            for (int startIndex = maxResults; startIndex < maxPlansToFetch; startIndex += maxResults) {
              log.info("Fetching plans starting at index: [{}] from bamboo server {}", startIndex,
                  bambooConfig.getBambooUrl());
              request = bambooRestClient.listProjectPlansWithPagination(
                  getBasicAuthCredentials(bambooConfig, encryptionDetails), maxResults, startIndex);

              response = getHttpRequestExecutionResponse(request);
              if (response.body() != null) {
                JsonNode planJsonNode = response.body().at("/plans/plan");
                planJsonNode.elements().forEachRemaining(jsonNode -> {
                  String planKey = jsonNode.get("key").asText();
                  String planName = jsonNode.get("shortName").asText();
                  planNameMap.put(planKey, planName);
                });
              }
            }
          }
        } catch (ArtifactServerException e) {
          throw e;
        } catch (Exception e) {
          if (response != null && !response.isSuccessful()) {
            IOUtils.closeQuietly(response.errorBody());
          }
          throw new ArtifactServerException("Failed to load plans:" + ExceptionUtils.getRootCauseMessage(e), e);
        }
        log.info("Retrieving plan keys for bamboo server {} success", bambooConfig);
        return planNameMap;
      });
    } catch (UncheckedTimeoutException e) {
      throw new InvalidArtifactServerException("Bamboo server took too long to respond", e);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new GeneralException(ExceptionUtils.getMessage(e), e, USER);
    }
  }

  private Response<JsonNode> getHttpRequestExecutionResponse(Call<JsonNode> request) throws IOException {
    Response<JsonNode> response = request.execute();
    handleResponse(response);
    return response;
  }

  private void handleResponse(Response<?> response) throws IOException {
    if (response.isSuccessful()) {
      return;
    }
    if (response.code() == 401) {
      throw new InvalidArtifactServerException("Invalid Bamboo credentials", USER);
    }
    if (response.errorBody() == null) {
      throw new ArtifactServerException(response.message());
    }
    throw new ArtifactServerException(response.errorBody().string());
  }

  @Override
  public List<BuildDetails> getBuilds(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails,
      String planKey, List<String> artifactPaths, int maxNumberOfBuilds) {
    try {
      return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(60), () -> {
        List<BuildDetails> buildDetailsList = new ArrayList<>();
        Call<JsonNode> request =
            getBambooClient(bambooConfig, encryptionDetails)
                .listBuildsForJob(getBasicAuthCredentials(bambooConfig, encryptionDetails), planKey, maxNumberOfBuilds);
        Response<JsonNode> response = null;
        try {
          response = getHttpRequestExecutionResponse(request);
          if (response.body() != null) {
            JsonNode resultNode = response.body().at("/results/result");
            if (resultNode != null) {
              resultNode.elements().forEachRemaining(jsonNode -> {
                List<ArtifactFileMetadata> artifactFileMetadata = new ArrayList<>();
                Map<String, String> metadata = new HashMap<>();
                if (isNotEmpty(artifactPaths)) {
                  for (String artifactPath : artifactPaths) {
                    artifactFileMetadata.addAll(getArtifactFileMetadata(
                        bambooConfig, encryptionDetails, planKey, jsonNode.get("buildNumber").asText(), artifactPath));
                  }
                }
                if (jsonNode.get(ArtifactMetadataKeys.id) != null) {
                  metadata.put(ArtifactMetadataKeys.id, jsonNode.get(ArtifactMetadataKeys.id).asText());
                }
                if (jsonNode.get(ArtifactMetadataKeys.planName) != null) {
                  metadata.put(ArtifactMetadataKeys.planName, jsonNode.get(ArtifactMetadataKeys.planName).asText());
                }
                buildDetailsList.add(
                    aBuildDetails()
                        .withNumber(jsonNode.get("buildNumber").asText())
                        .withRevision(
                            jsonNode.get("vcsRevisionKey") != null ? jsonNode.get("vcsRevisionKey").asText() : null)
                        .withBuildUrl(jsonNode.get("link").get("href").asText())
                        .withUiDisplayName("Build# " + jsonNode.get("buildNumber").asText())
                        .withArtifactDownloadMetadata(artifactFileMetadata)
                        .withMetadata(metadata)
                        .build());
              });
            }
          }
          return buildDetailsList;
        } catch (Exception e) {
          if (response != null && !response.isSuccessful()) {
            IOUtils.closeQuietly(response.errorBody());
          }
          throw new ArtifactServerException("Error in fetching builds from bamboo server", e, USER);
        }
      });
    } catch (UncheckedTimeoutException e) {
      throw new InvalidArtifactServerException("Bamboo server took too long to respond", e);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new WingsException(GENERAL_ERROR, USER, e).addParam("message", ExceptionUtils.getMessage(e));
    }
  }

  @Override
  public List<String> getArtifactPath(
      BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, String planKey) {
    try {
      return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(20), () -> {
        List<String> artifactPaths = new ArrayList<>();
        BuildDetails lastSuccessfulBuild = getLastSuccessfulBuild(bambooConfig, encryptionDetails, planKey);
        if (lastSuccessfulBuild != null) {
          artifactPaths.addAll(getArtifactRelativePaths(
              getBuildArtifactsUrlMap(bambooConfig, encryptionDetails, planKey, lastSuccessfulBuild.getNumber())
                  .values()
                  .stream()
                  .map(Artifact::getLink)
                  .collect(toList())));
        }
        return artifactPaths;
      });
    } catch (UncheckedTimeoutException e) {
      throw new InvalidArtifactServerException("Bamboo server took too long to respond", e);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new GeneralException(ExceptionUtils.getMessage(e), e, USER);
    }
  }

  @Override
  public String triggerPlan(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, String planKey,
      Map<String, String> parameters) {
    log.info("Trigger bamboo plan for Plan Key {} with parameters {}", planKey, parameters);
    Response<JsonNode> response = null;
    String buildResultKey = null;
    try {
      if (parameters == null) {
        parameters = new HashMap<>();
      }
      // Replace all the parameters with
      Call<JsonNode> request =
          getBambooClient(bambooConfig, encryptionDetails)
              .triggerPlan(getBasicAuthCredentials(bambooConfig, encryptionDetails), planKey, parameters);
      response = getHttpRequestExecutionResponse(request);
      if (response.body() != null) {
        if (response.body().findValue("buildResultKey") != null) {
          buildResultKey = response.body().findValue("buildResultKey").asText();
        }
      }
      if (buildResultKey == null) {
        throw new InvalidArtifactServerException(
            "Failed to trigger bamboo plan [" + planKey + "]. Reason: buildResultKey does not exist in response", USER);
      }
    } catch (Exception e) {
      if (response != null && !response.isSuccessful()) {
        IOUtils.closeQuietly(response.errorBody());
      }
      log.error("Failed to trigger bamboo plan [" + planKey + "]", e);
      throw new InvalidArtifactServerException(
          "Failed to trigger bamboo plan [" + planKey + "]. Reason:" + ExceptionUtils.getRootCauseMessage(e), USER);
    }
    log.info("Bamboo plan execution success for Plan Key {} with parameters {}", planKey, parameters);
    return buildResultKey;
  }

  @Override
  public String triggerPlan(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, String planKey,
      Map<String, String> parameters, LogCallback executionLogCallback) {
    log.info("Trigger bamboo plan for Plan Key {} with parameters {}", planKey, parameters);
    executionLogCallback.saveExecutionLog(
        String.format("Trigger bamboo plan for Plan Key %s with parameters %s", planKey, parameters), LogLevel.INFO);
    Response<JsonNode> response = null;
    String buildResultKey = null;
    try {
      if (parameters == null) {
        parameters = new HashMap<>();
      }
      // Replace all the parameters with
      Call<JsonNode> request =
          getBambooClient(bambooConfig, encryptionDetails)
              .triggerPlan(getBasicAuthCredentials(bambooConfig, encryptionDetails), planKey, parameters);
      response = getHttpRequestExecutionResponse(request);
      if (response.body() != null) {
        if (response.body().findValue("buildResultKey") != null) {
          buildResultKey = response.body().findValue("buildResultKey").asText();
        }
      }
      if (buildResultKey == null) {
        executionLogCallback.saveExecutionLog(
            "Failed to trigger bamboo plan [" + planKey + "]. Reason: buildResultKey does not exist in response",
            LogLevel.INFO);
        throw new InvalidArtifactServerException(
            "Failed to trigger bamboo plan [" + planKey + "]. Reason: buildResultKey does not exist in response", USER);
      }
    } catch (Exception e) {
      if (response != null && !response.isSuccessful()) {
        IOUtils.closeQuietly(response.errorBody());
      }
      log.error("Failed to trigger bamboo plan [" + planKey + "]", e);
      executionLogCallback.saveExecutionLog("Failed to trigger bamboo plan [" + planKey + "]", LogLevel.INFO);
      throw new InvalidArtifactServerException(
          "Failed to trigger bamboo plan [" + planKey + "]. Reason:" + ExceptionUtils.getRootCauseMessage(e), USER);
    }
    log.info("Bamboo plan execution success for Plan Key {} with parameters {}", planKey, parameters);
    executionLogCallback.saveExecutionLog(
        String.format("Bamboo plan execution success for Plan Key %s with parameters %s", planKey, parameters),
        LogLevel.INFO);
    return buildResultKey;
  }

  @Override
  public Result getBuildResult(
      BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, String buildResultKey) {
    Response<Result> response = null;
    try {
      Call<Result> request =
          getBambooClient(bambooConfig, encryptionDetails)
              .getBuildResult(getBasicAuthCredentials(bambooConfig, encryptionDetails), buildResultKey);
      response = request.execute();
      handleResponse(response);
      if (response.isSuccessful()) {
        Result result = response.body();
        if (result != null) {
          if (bambooConfig.getBambooUrl().endsWith("/")) {
            result.setBuildUrl(bambooConfig.getBambooUrl() + "browse/" + buildResultKey);
          } else {
            result.setBuildUrl(bambooConfig.getBambooUrl() + "/browse/" + buildResultKey);
          }
        }
        return response.body();
      }
    } catch (Exception e) {
      if (response != null && !response.isSuccessful()) {
        IOUtils.closeQuietly(response.errorBody());
      }
      log.error("BambooService job keys fetch failed with exception", e);
      throw new ArtifactServerException("Failed to retrieve build status for [ " + buildResultKey
              + "]. Reason:" + ExceptionUtils.getRootCauseMessage(e),
          USER);
    }
    return Result.builder().build();
  }

  @Override
  public Status getBuildResultStatus(
      BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, String buildResultKey) {
    Response<Status> response = null;
    try {
      Call<Status> request =
          getBambooClient(bambooConfig, encryptionDetails)
              .getBuildResultStatus(getBasicAuthCredentials(bambooConfig, encryptionDetails), buildResultKey);
      response = request.execute();
      if (!response.isSuccessful()) {
        if (response.code() == 404) {
          return Status.builder().finished(true).build();
        }
      }
      return response.body();
    } catch (Exception e) {
      if (response != null && !response.isSuccessful()) {
        IOUtils.closeQuietly(response.errorBody());
      }
      log.error("BambooService job keys fetch failed with exception", e);
      throw new ArtifactServerException("Failed to trigger bamboo plan " + buildResultKey, e, USER);
    }
  }

  private List<String> getArtifactRelativePaths(Collection<String> paths) {
    return paths.stream().map(this::extractRelativePath).filter(Objects::nonNull).collect(toList());
  }

  private String extractRelativePath(String path) {
    List<String> strings = asList(path.split("/"));
    int artifactIdx = strings.indexOf("artifact");
    if (artifactIdx >= 0 && artifactIdx + 2 < strings.size()) {
      artifactIdx += 2; // skip next path element jobShortId as well: "baseUrl/.../../artifact/jobShortId/{relativePath}
      return Joiner.on("/").join(strings.listIterator(artifactIdx));
    }
    return null;
  }

  private List<ArtifactFileMetadata> getArtifactFileMetadata(BambooConfig bambooConfig,
      List<EncryptedDataDetail> encryptionDetails, String planKey, String buildNumber, String artifactPathRegex) {
    List<ArtifactFileMetadata> artifactFileMetadata = new ArrayList<>();
    if (isNotEmpty(artifactPathRegex.trim())) {
      Map<String, Artifact> artifactPathMap =
          getBuildArtifactsUrlMap(bambooConfig, encryptionDetails, planKey, buildNumber);
      Set<Entry<String, Artifact>> artifactPathSet = artifactPathMap.entrySet();

      String artifactSourcePath = artifactPathRegex;
      Pattern pattern = Pattern.compile(artifactPathRegex.replace(".", "\\.").replace("?", ".?").replace("*", ".*?"));
      Entry<String, Artifact> artifactPath =
          artifactPathSet.stream()
              .filter(entry
                  -> extractRelativePath(entry.getValue().getLink()) != null
                      && pattern.matcher(extractRelativePath(entry.getValue().getLink())).find())
              .findFirst()
              .orElse(null);
      if (artifactPath != null) {
        Artifact value = artifactPath.getValue();
        log.info("Artifact Path regex {} matching with artifact path {}", artifactPathRegex, value);
        String link = value.getLink();
        String artifactFileName = link.substring(link.lastIndexOf('/') + 1);
        if (isNotEmpty(artifactFileName)) {
          artifactFileMetadata.add(ArtifactFileMetadata.builder().fileName(artifactFileName).url(link).build());
        }
      } else { // It is not matching  direct url, so just prepare the url
        String msg =
            "Artifact path  [" + artifactPathRegex + "] not matching with any values: " + artifactPathMap.values();
        log.info(msg);
        log.info("Constructing url path to download");
        Artifact artifactJob = artifactPathMap.values()
                                   .stream()
                                   .filter(artifact -> artifact.getProducerJobKey() != null)
                                   .findFirst()
                                   .orElse(null);
        if (artifactJob != null) {
          String jobName = artifactJob.getProducerJobKey()
                               .replace(planKey, "")
                               .replace(buildNumber, "")
                               .replace("-", ""); // TOD-TOD-JOB1-80;
          String buildKey = planKey + "-" + buildNumber;
          String artifactUrl;
          if (bambooConfig.getBambooUrl().endsWith("/")) {
            artifactUrl = bambooConfig.getBambooUrl() + "browse/" + buildKey + "/artifact";
          } else {
            artifactUrl = bambooConfig.getBambooUrl() + "/browse/" + buildKey + "/artifact";
          }
          artifactUrl = artifactUrl + "/" + jobName + "/" + artifactSourcePath;
          log.info("Constructed url {}", artifactUrl);
          artifactFileMetadata.add(ArtifactFileMetadata.builder()
                                       .fileName(artifactUrl.substring(artifactUrl.lastIndexOf('/') + 1))
                                       .url(artifactUrl)
                                       .build());
        } else {
          throw new InvalidArtifactServerException(msg, USER);
        }
      }
    }
    return artifactFileMetadata;
  }

  @Override
  public Pair<String, InputStream> downloadArtifacts(BambooConfig bambooConfig,
      List<EncryptedDataDetail> encryptionDetails, ArtifactStreamAttributes artifactStreamAttributes,
      String buildNumber, String delegateId, String taskId, String accountId, ListNotifyResponseData res) {
    List<ArtifactFileMetadata> artifactFileMetadata = artifactStreamAttributes.getArtifactFileMetadata();
    if (isEmpty(artifactFileMetadata)) {
      artifactFileMetadata = new ArrayList<>();
      // for backward compatibility, get all artifact paths
      List<String> artifactPaths = artifactStreamAttributes.getArtifactPaths();
      if (isNotEmpty(artifactPaths)) {
        for (String artifactPathRegex : artifactPaths) {
          artifactFileMetadata.addAll(getArtifactFileMetadata(
              bambooConfig, encryptionDetails, artifactStreamAttributes.getJobName(), buildNumber, artifactPathRegex));
        }
      }
    }
    // use artifact file metadata from artifact stream attributes and download
    if (isNotEmpty(artifactFileMetadata)) {
      for (ArtifactFileMetadata fileMetadata : artifactFileMetadata) {
        downloadAnArtifact(bambooConfig, encryptionDetails, fileMetadata, delegateId, taskId, accountId, res);
      }
    }
    return null;
  }

  @Override
  public Pair<String, InputStream> downloadArtifacts(BambooConfig bambooConfig,
      List<EncryptedDataDetail> encryptionDetails, List<String> artifactPaths, String planKey, String buildNumber) {
    List<ArtifactFileMetadata> artifactFileMetadata = new ArrayList<>();
    // for backward compatibility, get all artifact paths
    if (isNotEmpty(artifactPaths)) {
      for (String artifactPathRegex : artifactPaths) {
        artifactFileMetadata.addAll(
            getArtifactFileMetadata(bambooConfig, encryptionDetails, planKey, buildNumber, artifactPathRegex));
      }
    }

    Pair<String, InputStream> inputStreamPair = null;
    // use artifact file metadata from artifact stream attributes and download
    if (isNotEmpty(artifactFileMetadata)) {
      for (ArtifactFileMetadata fileMetadata : artifactFileMetadata) {
        String link = fileMetadata.getUrl();
        inputStreamPair = downloadArtifact(bambooConfig, encryptionDetails, fileMetadata.getFileName(), link);
      }
    }
    return inputStreamPair;
  }

  private void downloadAnArtifact(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails,
      ArtifactFileMetadata artifactFileMetadata, String delegateId, String taskId, String accountId,
      ListNotifyResponseData notifyResponseData) {
    String link = artifactFileMetadata.getUrl();
    try {
      artifactCollectionCommonTaskHelper.addDataToResponse(
          downloadArtifact(bambooConfig, encryptionDetails, artifactFileMetadata.getFileName(), link), link,
          notifyResponseData, delegateId, taskId, accountId);
    } catch (IOException e) {
      String msg = "Failed to download the artifact from url [" + link + "]";
      throw new ArtifactServerException(msg, e);
    }
  }

  public Pair<String, InputStream> downloadArtifact(BambooConfig bambooConfig,
      List<EncryptedDataDetail> encryptionDetails, String artifactFileName, String artifactFilePath) {
    log.info("Downloading artifact from url {}", artifactFilePath);
    try {
      URL url = new URL(artifactFilePath);
      URLConnection uc = url.openConnection();
      uc.setRequestProperty("Authorization", getBasicAuthCredentials(bambooConfig, encryptionDetails));
      return ImmutablePair.of(artifactFileName, uc.getInputStream());
    } catch (IOException e) {
      String msg = "Failed to download the artifact from url [" + artifactFilePath + "]";
      throw new ArtifactServerException(msg, e);
    }
  }

  @Override
  public boolean isRunning(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails) {
    return getPlanKeys(bambooConfig, encryptionDetails, 1) != null; // TODO:: First check use status API
  }

  /**
   * Gets build artifacts url map.
   *
   * @param bambooConfig the bamboo config
   * @param planKey      the job key
   * @param buildNumber  the build number
   * @return the build artifacts url map
   */
  private Map<String, Artifact> getBuildArtifactsUrlMap(
      BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, String planKey, String buildNumber) {
    log.info("Retrieving artifacts from plan {} and build number {}", planKey, buildNumber);
    Call<JsonNode> request =
        getBambooClient(bambooConfig, encryptionDetails)
            .getBuildArtifacts(getBasicAuthCredentials(bambooConfig, encryptionDetails), planKey, buildNumber);
    Map<String, Artifact> artifactPathMap = new HashMap<>();
    Response<JsonNode> response;
    try {
      // stages.stage.results.result.artifacts.artifact
      response = getHttpRequestExecutionResponse(request);
      if (response.body() != null) {
        JsonNode stageNodes = response.body().at("/stages/stage");
        if (stageNodes != null) {
          stageNodes.elements().forEachRemaining(stageNode -> {
            JsonNode resultNodes = stageNode.at("/results/result");
            if (resultNodes != null) {
              resultNodes.elements().forEachRemaining(resultNode -> {
                JsonNode artifactNodes = resultNode.at("/artifacts/artifact");
                if (artifactNodes != null) {
                  artifactNodes.elements().forEachRemaining(artifactNode -> {
                    JsonNode hrefNode = artifactNode.at("/link/href");
                    JsonNode nameNode = artifactNode.get("name");
                    JsonNode producerJobKeyNode = artifactNode.get("producerJobKey");
                    if (hrefNode != null) {
                      Artifact artifact = Artifact.builder().name(nameNode.asText()).link(hrefNode.textValue()).build();
                      if (producerJobKeyNode != null) {
                        artifact.setProducerJobKey(producerJobKeyNode.asText());
                      }
                      artifactPathMap.put(nameNode.asText(), artifact);
                    }
                  });
                }
              });
            }
          });
        }
      }
      log.info("Retrieving artifacts from plan {} and build number {} success", planKey, buildNumber);
      return artifactPathMap;
    } catch (IOException e) {
      throw new ArtifactServerException(
          format("Retrieving artifacts from plan %s and build number %s failed", planKey, buildNumber), e, USER);
    }
  }

  private List<String> extractJobKeyFromNestedProjectResponseJson(Response<JsonNode> response) {
    List<String> jobKeys = new ArrayList<>();
    JsonNode planStages = response.body().at("/stages/stage");
    if (planStages != null) {
      planStages.elements().forEachRemaining(planStage -> {
        JsonNode stagePlans = planStage.at("/plans/plan");
        if (stagePlans != null) {
          stagePlans.elements().forEachRemaining(stagePlan -> jobKeys.add(stagePlan.get("key").asText()));
        }
      });
    }
    return jobKeys;
  }

  public long getFileSize(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails,
      String artifactFileName, String artifactFilePath) {
    log.info("Getting file size for artifact at path {}", artifactFilePath);
    long size;
    Pair<String, InputStream> pair =
        downloadArtifact(bambooConfig, encryptionDetails, artifactFileName, artifactFilePath);
    if (pair == null) {
      throw new InvalidArtifactServerException(format("Failed to get file size for artifact: %s", artifactFilePath));
    }
    try {
      size = StreamUtils.getInputStreamSize(pair.getRight());
      pair.getRight().close();
    } catch (IOException e) {
      throw new InvalidArtifactServerException(getMessage(e), e);
    }
    log.info(format("Computed file size: [%d] bytes for artifact Path: %s", size, artifactFilePath));
    return size;
  }
}
