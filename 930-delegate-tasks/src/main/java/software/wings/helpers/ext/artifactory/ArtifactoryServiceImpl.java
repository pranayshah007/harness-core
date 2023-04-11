/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.artifactory;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.artifactory.ArtifactoryClientImpl.getArtifactoryClient;
import static io.harness.artifactory.ArtifactoryClientImpl.getBaseUrl;
import static io.harness.artifactory.ArtifactoryClientImpl.handleAndRethrow;
import static io.harness.artifactory.ArtifactoryClientImpl.handleErrorResponse;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.ARTIFACT_SERVER_ERROR;
import static io.harness.exception.WingsException.USER;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.jfrog.artifactory.client.ArtifactoryRequest.ContentType.JSON;
import static org.jfrog.artifactory.client.ArtifactoryRequest.ContentType.TEXT;
import static org.jfrog.artifactory.client.ArtifactoryRequest.Method.GET;
import static org.jfrog.artifactory.client.ArtifactoryRequest.Method.POST;
import static org.jfrog.artifactory.client.model.impl.PackageTypeImpl.docker;
import static org.jfrog.artifactory.client.model.impl.PackageTypeImpl.maven;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.artifact.ArtifactUtilities;
import io.harness.artifactory.ArtifactoryClientImpl;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ArtifactoryServerException;
import io.harness.exception.WingsException.ReportTarget;
import io.harness.network.Http;

import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.dto.HelmChart;
import software.wings.common.BuildDetailsComparatorAscending;
import software.wings.delegatetasks.collect.artifacts.ArtifactCollectionCommonTaskHelper;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.utils.RepositoryType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpHost;
import org.jetbrains.annotations.NotNull;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryClientBuilder;
import org.jfrog.artifactory.client.ArtifactoryRequest;
import org.jfrog.artifactory.client.ArtifactoryResponse;
import org.jfrog.artifactory.client.httpClient.http.ProxyConfig;
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl;
import org.jfrog.artifactory.client.model.impl.PackageTypeImpl;

@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._960_API_SERVICES)
@BreakDependencyOn("software.wings.beans.artifact.ArtifactStreamAttributes")
@BreakDependencyOn("io.harness.delegate.task.ListNotifyResponseData")
public class ArtifactoryServiceImpl implements ArtifactoryService {
  private static final String REASON = "Reason:";
  static final String RESULTS = "results";

  private static final String DOWNLOAD_FILE_FOR_GENERIC_REPO = "Downloading the file for generic repo";

  @Inject private ArtifactCollectionCommonTaskHelper artifactCollectionCommonTaskHelper;
  @Inject private ArtifactoryClientImpl artifactoryClient;

  private final Retry retry;

  public ArtifactoryServiceImpl() {
    final RetryConfig config =
        RetryConfig.custom().maxAttempts(1).intervalFunction(IntervalFunction.ofExponentialBackoff()).build();
    this.retry = Retry.of("ArtifactoryRetry", config);
  }

  @Override
  public Map<String, String> getRepositories(ArtifactoryConfigRequest artifactoryConfig) {
    return artifactoryClient.getRepositories(artifactoryConfig, Collections.singletonList(docker));
  }

  @Override
  public Map<String, String> getRepositories(ArtifactoryConfigRequest artifactoryConfig, String packageType) {
    switch (packageType) {
      case "maven":
        return artifactoryClient.getRepositories(artifactoryConfig, Collections.singletonList(maven));
      default:
        return artifactoryClient.getRepositories(artifactoryConfig,
            Arrays.stream(PackageTypeImpl.values()).filter(type -> docker != type).collect(toList()));
    }
  }

  @Override
  public Map<String, String> getRepositories(
      ArtifactoryConfigRequest artifactoryConfig, RepositoryType repositoryType) {
    switch (repositoryType) {
      case docker:
        return getRepositories(artifactoryConfig);
      case maven:
        return artifactoryClient.getRepositories(artifactoryConfig, Arrays.asList(maven));
      case any:
        return artifactoryClient.getRepositories(artifactoryConfig,
            Arrays.stream(PackageTypeImpl.values()).filter(type -> docker != type).collect(toList()));
      default:
        return getRepositories(artifactoryConfig, "");
    }
  }

  @Override
  public List<String> getRepoPaths(ArtifactoryConfigRequest artifactoryConfig, String repoKey) {
    return listDockerImages(getArtifactoryClient(artifactoryConfig), repoKey);
  }

  private List<String> listDockerImages(Artifactory artifactory, String repoKey) {
    List<String> images = new ArrayList<>();
    String errorOnListingDockerimages = "Error occurred while listing docker images from artifactory %s for Repo %s";
    try {
      log.info("Retrieving docker images from artifactory url {} and repo key {}", artifactory.getUri(), repoKey);
      ArtifactoryResponse artifactoryResponse = artifactory.restCall(new ArtifactoryRequestImpl()
                                                                         .apiUrl("api/docker/" + repoKey + "/v2"
                                                                             + "/_catalog")
                                                                         .method(GET)
                                                                         .responseType(JSON));
      handleErrorResponse(artifactoryResponse);
      Map response = artifactoryResponse.parseBody(Map.class);
      if (response != null) {
        images = (List<String>) response.get("repositories");
        if (isEmpty(images)) {
          log.info("No docker images from artifactory url {} and repo key {}", artifactory.getUri(), repoKey);
          images = new ArrayList<>();
        }
        log.info("Retrieving images from artifactory url {} and repo key {} success. Images {}", artifactory.getUri(),
            repoKey, images);
      }
    } catch (SocketTimeoutException e) {
      log.error(format(errorOnListingDockerimages, artifactory, repoKey), e);
      return images;
    } catch (Exception e) {
      log.error(format(errorOnListingDockerimages, artifactory, repoKey), e);
      handleAndRethrow(e, USER);
    }
    return images;
  }

  @Override
  public List<BuildDetails> getBuilds(ArtifactoryConfigRequest artifactoryConfig,
      ArtifactStreamAttributes artifactStreamAttributes, int maxNumberOfBuilds) {
    String repoKey = artifactStreamAttributes.getJobName();
    String imageName = artifactStreamAttributes.getImageName();
    log.info("Retrieving docker tags for repoKey {} imageName {} ", repoKey, imageName);
    List<BuildDetails> buildDetails = new ArrayList<>();
    Artifactory artifactory = getArtifactoryClient(artifactoryConfig);
    try {
      ArtifactoryRequest repositoryRequest = new ArtifactoryRequestImpl()
                                                 .apiUrl("api/docker/" + repoKey + "/v2/" + imageName + "/tags/list")
                                                 .method(GET)
                                                 .responseType(JSON);
      ArtifactoryResponse artifactoryResponse = artifactory.restCall(repositoryRequest);
      handleErrorResponse(artifactoryResponse);
      Map response = artifactoryResponse.parseBody(Map.class);
      if (response != null) {
        List<String> tags = (List<String>) response.get("tags");
        if (isEmpty(tags)) {
          log.info("No  docker tags for repoKey {} imageName {} success ", repoKey, imageName);
          return buildDetails;
        }
        String tagUrl = getBaseUrl(artifactoryConfig) + repoKey + "/" + imageName + "/";
        String repoName = ArtifactUtilities.getArtifactoryRepositoryName(artifactoryConfig.getArtifactoryUrl(),
            artifactStreamAttributes.getArtifactoryDockerRepositoryServer(), artifactStreamAttributes.getJobName(),
            artifactStreamAttributes.getImageName());
        buildDetails = tags.stream()
                           .map(tag -> {
                             Map<String, String> metadata = new HashMap();
                             metadata.put(ArtifactMetadataKeys.image, repoName + ":" + tag);
                             metadata.put(ArtifactMetadataKeys.tag, tag);
                             return aBuildDetails()
                                 .withNumber(tag)
                                 .withBuildUrl(tagUrl + tag)
                                 .withMetadata(metadata)
                                 .withUiDisplayName("Tag# " + tag)
                                 .build();
                           })
                           .collect(toList());
        if (tags.size() < 10) {
          log.info("Retrieving docker tags for repoKey {} imageName {} success. Retrieved tags {}", repoKey, imageName,
              tags);
        } else {
          log.info("Retrieving docker tags for repoKey {} imageName {} success. Retrieved {} tags", repoKey, imageName,
              tags.size());
        }
      }

    } catch (Exception e) {
      log.info("Exception occurred while retrieving the docker docker tags for Image {}", imageName);
      handleAndRethrow(e, USER);
    }

    // Sorting at build tag for docker artifacts.
    return buildDetails.stream().sorted(new BuildDetailsComparatorAscending()).collect(toList());
  }

  @Override
  public List<BuildDetails> getFilePaths(ArtifactoryConfigRequest artifactoryConfig, String repositoryName,
      String artifactPath, String repositoryType, int maxVersions) {
    return artifactoryClient.getBuildDetails(artifactoryConfig, repositoryName, artifactPath, maxVersions);
  }

  @Override
  public ListNotifyResponseData downloadArtifacts(ArtifactoryConfigRequest artifactoryConfig, String repositoryName,
      Map<String, String> metadata, String delegateId, String taskId, String accountId) {
    ListNotifyResponseData res = new ListNotifyResponseData();
    String artifactPath = metadata.get(ArtifactMetadataKeys.artifactPath).replaceFirst(repositoryName, "").substring(1);
    String artifactName = metadata.get(ArtifactMetadataKeys.artifactFileName);
    try {
      log.info(DOWNLOAD_FILE_FOR_GENERIC_REPO);
      InputStream inputStream = downloadArtifacts(artifactoryConfig, repositoryName, metadata);
      artifactCollectionCommonTaskHelper.addDataToResponse(
          new ImmutablePair<>(artifactName, inputStream), artifactPath, res, delegateId, taskId, accountId);
      return res;
    } catch (Exception e) {
      String msg =
          "Failed to download the latest artifacts  of repo [" + repositoryName + "] artifactPath [" + artifactPath;
      prepareAndThrowException(msg + REASON + ExceptionUtils.getRootCauseMessage(e), USER, e);
    }
    return res;
  }

  @Override
  public Pair<String, InputStream> downloadArtifact(
      ArtifactoryConfigRequest artifactoryConfig, String repositoryName, Map<String, String> metadata) {
    Pair<String, InputStream> pair = null;
    String artifactPath = metadata.get(ArtifactMetadataKeys.artifactPath).replaceFirst(repositoryName, "").substring(1);
    String artifactName = metadata.get(ArtifactMetadataKeys.artifactFileName);
    try {
      log.info(DOWNLOAD_FILE_FOR_GENERIC_REPO);
      InputStream inputStream = downloadArtifacts(artifactoryConfig, repositoryName, metadata);
      pair = new ImmutablePair<>(artifactName, inputStream);
    } catch (Exception e) {
      String msg =
          "Failed to download the latest artifacts  of repo [" + repositoryName + "] artifactPath [" + artifactPath;
      prepareAndThrowException(msg + REASON + ExceptionUtils.getRootCauseMessage(e), USER, e);
    }
    return pair;
  }

  @Override
  public boolean validateArtifactPath(
      ArtifactoryConfigRequest artifactoryConfig, String repositoryName, String artifactPath, String repositoryType) {
    log.info("Validating artifact path {} for repository {} and repositoryType {}", artifactPath, repositoryName,
        repositoryType);
    if (isBlank(artifactPath)) {
      throw new ArtifactoryServerException("Artifact Pattern can not be empty", ARTIFACT_SERVER_ERROR, USER);
    }
    List<BuildDetails> filePaths = getFilePaths(artifactoryConfig, repositoryName, artifactPath, repositoryType, 1);

    if (isEmpty(filePaths)) {
      prepareAndThrowException("No artifact files matching with the artifact path [" + artifactPath + "]", USER, null);
    }
    log.info("Validating whether directory exists or not for Generic repository type by fetching file paths");
    return true;
  }

  private void prepareAndThrowException(String message, EnumSet<ReportTarget> reportTargets, Exception e) {
    throw new ArtifactoryServerException(message, ErrorCode.INVALID_ARTIFACT_SERVER, reportTargets, e);
  }

  private InputStream downloadArtifacts(
      ArtifactoryConfigRequest artifactoryConfig, String repoKey, Map<String, String> metadata) {
    return artifactoryClient.downloadArtifacts(
        artifactoryConfig, repoKey, metadata, ArtifactMetadataKeys.artifactPath, ArtifactMetadataKeys.artifactFileName);
  }

  protected void checkIfUseProxyAndAppendConfig(
      ArtifactoryClientBuilder builder, ArtifactoryConfigRequest artifactoryConfig) {
    HttpHost httpProxyHost = Http.getHttpProxyHost(artifactoryConfig.getArtifactoryUrl());
    if (httpProxyHost != null && !Http.shouldUseNonProxy(artifactoryConfig.getArtifactoryUrl())) {
      ProxyConfig proxy = new ProxyConfig();
      proxy.setHost(httpProxyHost.getHostName());
      proxy.setPort(httpProxyHost.getPort());
      proxy.setUsername(Http.getProxyUserName());
      proxy.setPassword(Http.getProxyPassword());
      builder.setProxy(proxy);
      builder.setProxy(proxy);
    }
  }

  @Override
  public Long getFileSize(ArtifactoryConfigRequest artifactoryConfig, Map<String, String> metadata) {
    return artifactoryClient.getFileSize(artifactoryConfig, metadata, ArtifactMetadataKeys.artifactPath);
  }

  @Override
  public List<HelmChart> getHelmCharts(ArtifactoryConfigRequest artifactoryConfig, String repositoryName,
      String chartName, int maxVersions, String version, boolean isRegex) {
    log.info("Retrieving helm charts for repositoryName {} chartName {}", repositoryName, chartName);
    Artifactory artifactory = getArtifactoryClient(artifactoryConfig);
    try {
      if (isBlank(chartName)) {
        throw new ArtifactoryServerException("Chart name can not be empty");
      }
      String aclQuery = "api/search/aql";
      List<String> helmChartNames =
          getHelmChartNames(artifactory, aclQuery, repositoryName, chartName, maxVersions, version, isRegex);
      if (isEmpty(helmChartNames)) {
        return new ArrayList<>();
      }
      return getHelmChartsVersionsForChartNames(artifactory, aclQuery, helmChartNames);
    } catch (Exception e) {
      log.error(format("Error occurred while retrieving File Paths from Artifactory server %s",
                    artifactoryConfig.getArtifactoryUrl()),
          e);
      handleAndRethrow(e, USER);
    }
    return new ArrayList<>();
  }

  private List<String> getHelmChartNamesForVersion(Artifactory artifactory, String aclQuery, String repositoryName,
      String chartName, String version) throws IOException {
    List<String> helmChartNames = new ArrayList<>();
    if (chartName.charAt(0) == '/') {
      chartName = chartName.substring(1);
    }
    String requestBody = "items.find({\"repo\":\"" + repositoryName + "\"}, {\"@chart.name\": \"" + chartName
        + "\"}, {\"@chart.version\": \"" + version + "\"})"
        + ".include(\"name\", \"repo\", \"created\", \"path\").sort({\"$desc\" : [\"created\"]})";

    ArtifactoryResponse artifactoryResponse = getArtifactoryResponse(artifactory, aclQuery, requestBody);
    Map<String, List> response = artifactoryResponse.parseBody(Map.class);
    if (response != null) {
      List<Map<String, Object>> results = response.get(RESULTS);
      for (Map<String, Object> item : results) {
        String name = (String) item.get("name");
        helmChartNames.add(name);
      }
    }
    return helmChartNames;
  }

  private List<HelmChart> getHelmChartsVersionsForChartNames(
      Artifactory artifactory, String aclQuery, List<String> helmChartNames) throws IOException {
    List<String> helmChartNameQueries = new ArrayList<>();
    for (String helmChartName : helmChartNames) {
      String helmChartQuery = "{\"name\": \" " + helmChartName + "\"}";
      helmChartNameQueries.add(helmChartQuery);
    }
    String helmChartNameQuery = String.join(",", helmChartNameQueries);

    String requestBody = "items.find({\"$or\": [ " + helmChartNameQuery
        + " ]}).include(\"name\", \"repo\", \"@chart.version\", \"@chart.appVersion\", \"path\")";
    ArtifactoryResponse artifactoryResponse = getArtifactoryResponse(artifactory, aclQuery, requestBody);
    Map<String, List> response = artifactoryResponse.parseBody(Map.class);
    if (response != null) {
      return getHelmChartDetailsFromResponse(response, helmChartNames);
    }
    return new ArrayList<>();
  }

  public List<String> getHelmChartNames(Artifactory artifactory, String aclQuery, String repositoryName,
      String chartName, int maxVersions, String version, boolean isRegex) throws IOException {
    List<String> helmChartNames = new ArrayList<>();
    if (chartName.charAt(0) == '/') {
      chartName = chartName.substring(1);
    }
    String requestBody = getRequestBody(repositoryName, chartName, maxVersions, version, isRegex);
    ArtifactoryResponse artifactoryResponse = getArtifactoryResponse(artifactory, aclQuery, requestBody);
    Map<String, List> response = artifactoryResponse.parseBody(Map.class);
    if (response != null) {
      List<Map<String, Object>> results = response.get(RESULTS);
      if (results != null) {
        for (Map<String, Object> item : results) {
          String name = (String) item.get("name");
          helmChartNames.add(name);
        }
      }
    }
    return helmChartNames;
  }

  @NotNull
  private String getRequestBody(
      String repositoryName, String chartName, int maxVersions, String version, boolean isRegex) {
    if (isEmpty(version)) {
      return "items.find({\"repo\":\"" + repositoryName + "\"}, {\"@chart.name\": \"" + chartName
          + "\"}).include(\"name\", \"repo\", \"created\", \"path\").sort({\"$desc\" : [\"created\"]}).limit("
          + maxVersions + ")";
    }

    if (!isRegex) {
      return "items.find({\"repo\":\"" + repositoryName + "\"}, {\"@chart.name\": \"" + chartName
          + "\"}, {\"@chart.version\": \"" + version + "\"})"
          + ".include(\"name\", \"repo\", \"created\", \"path\")";
    } else {
      return "items.find({\"repo\":\"" + repositoryName + "\"}, {\"@chart.name\": \"" + chartName
          + "\"}, {\"@chart.version\": {\"$match\": \"" + version + "\"}})"
          + ".include(\"name\", \"repo\", \"created\", \"path\").sort({\"$desc\" : [\"created\"]}).limit(1)";
    }
  }

  private ArtifactoryResponse retryCall(ArtifactoryRequest request, Artifactory artifactory) throws Exception {
    return Retry.decorateCallable(this.retry, () -> artifactory.restCall(request)).call();
  }

  public ArtifactoryResponse getArtifactoryResponse(Artifactory artifactory, String aclQuery, String requestBody)
      throws IOException {
    ArtifactoryRequest repositoryRequest = new ArtifactoryRequestImpl()
                                               .apiUrl(aclQuery)
                                               .method(POST)
                                               .requestBody(requestBody)
                                               .requestType(TEXT)
                                               .responseType(JSON);

    ArtifactoryResponse artifactoryResponse;
    try {
      artifactoryResponse = retryCall(repositoryRequest, artifactory);
    } catch (Exception e) {
      log.warn("An error occurred while getting artifacts of manifests even after retries", e);
      throw new ArtifactoryServerException("Error occurred while get artifacts of manifests in retry");
    }

    if (artifactoryResponse.getStatusLine().getStatusCode() == 403
        || artifactoryResponse.getStatusLine().getStatusCode() == 400) {
      log.warn(
          "User not authorized to perform or using OSS version deep level search. Trying with different search api. Message {}",
          artifactoryResponse.getStatusLine().getReasonPhrase());
      throw new ArtifactoryServerException("User not authorized to search in artifactory. Message "
          + artifactoryResponse.getStatusLine().getReasonPhrase());
    }
    return artifactoryResponse;
  }

  private List<HelmChart> getHelmChartDetailsFromResponse(Map<String, List> response, List<String> helmChartNames) {
    List<Map<String, Object>> results = response.get(RESULTS);
    Map<String, String> helmChartNameToVersionMap = new HashMap<>();
    Map<String, String> helmChartNameToAppVersionMap = new HashMap<>();
    if (results != null) {
      for (Map<String, Object> item : results) {
        String name = (String) item.get("name");
        List<Map<String, String>> properties = (List<Map<String, String>>) item.get("properties");
        if (isEmpty(properties)) {
          continue;
        }
        Map<String, String> versionProperty =
            properties.stream().filter(property -> property.get("key").equals("chart.version")).findAny().orElse(null);
        if (versionProperty == null) {
          continue;
        }
        String version = versionProperty.get("value");
        Map<String, String> appVersionProperty = properties.stream()
                                                     .filter(property -> property.get("key").equals("chart.appVersion"))
                                                     .findAny()
                                                     .orElse(Collections.emptyMap());
        helmChartNameToVersionMap.put(name, version);
        helmChartNameToAppVersionMap.put(name, appVersionProperty.get("value"));
      }
    }

    List<HelmChart> helmChartDetails = new ArrayList<>();
    helmChartNames.forEach(helmChartName -> {
      if (helmChartNameToVersionMap.containsKey(helmChartName)) {
        helmChartDetails.add(HelmChart.builder()
                                 .version(helmChartNameToVersionMap.get(helmChartName))
                                 .appVersion(helmChartNameToAppVersionMap.get(helmChartName))
                                 .displayName(helmChartName)
                                 .build());
      }
    });
    return helmChartDetails;
  }
}
