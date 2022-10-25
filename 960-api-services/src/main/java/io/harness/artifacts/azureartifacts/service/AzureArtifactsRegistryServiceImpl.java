/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.azureartifacts.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.network.Http.getOkHttpClientBuilder;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.azureartifacts.beans.AzureArtifactsInternalConfig;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.network.Http;

import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.helpers.ext.azure.devops.AzureArtifactsFeed;
import software.wings.helpers.ext.azure.devops.AzureArtifactsFeeds;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackage;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackageVersion;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackageVersions;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackages;
import software.wings.helpers.ext.azure.devops.AzureArtifactsRestClient;
import software.wings.helpers.ext.azure.devops.AzureDevopsProject;
import software.wings.helpers.ext.azure.devops.AzureDevopsProjects;
import software.wings.helpers.ext.azure.devops.AzureDevopsRestClient;
import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class AzureArtifactsRegistryServiceImpl implements AzureArtifactsRegistryService {
  public static final int CONNECT_TIMEOUT = 5;
  public static final int READ_TIMEOUT = 10;

  @Override
  public boolean validateCredentials(AzureArtifactsInternalConfig azureArtifactsInternalConfig) {
    validateAzureDevopsUrl(azureArtifactsInternalConfig.getAzureArtifactsRegistryUrl());

    AzureDevopsRestClient azureArtifactsDevopsRestClient =
        getAzureDevopsRestClient(azureArtifactsInternalConfig.getAzureArtifactsRegistryUrl());

    String authHeader = getAuthHeader(azureArtifactsInternalConfig);

    Response<AzureDevopsProjects> projectResponse;

    try {
      projectResponse = azureArtifactsDevopsRestClient.listProjects(authHeader).execute();
    } catch (IOException e) {
      throw new HintException("Connector test connection failed.");
    }

    return true;
  }

  @Override
  public List<BuildDetails> listPackageVersions(AzureArtifactsInternalConfig azureArtifactsInternalConfig,
      String packageType, String packageName, String versionRegex, String feed, String project) {
    AzureArtifactsRestClient azureArtifactsRegistryRestClient =
        getAzureArtifactsRestClient(azureArtifactsInternalConfig.getAzureArtifactsRegistryUrl(), project);

    String authHeader = getAuthHeader(azureArtifactsInternalConfig);

    List<AzureArtifactsFeed> feeds = listFeeds(azureArtifactsInternalConfig, project);

    String feedId = null;

    for (AzureArtifactsFeed azureArtifactsFeed : feeds) {
      if (azureArtifactsFeed.getName().equals(feed)) {
        feedId = azureArtifactsFeed.getId();

        break;
      }
    }

    List<AzureArtifactsPackage> packages = listPackages(azureArtifactsInternalConfig, project, feed, packageType);

    String packageId = null;

    for (AzureArtifactsPackage azureArtifactsPackage : packages) {
      if (azureArtifactsPackage.getName().equals(packageName)) {
        packageId = azureArtifactsPackage.getId();

        break;
      }
    }

    Response<AzureArtifactsPackageVersions> packageVersionsList;

    try {
      packageVersionsList =
          azureArtifactsRegistryRestClient.listPackageVersions(authHeader, feedId, packageId).execute();
    } catch (IOException e) {
      throw new HintException("Failed to get Builds.");
    }

    if (packageVersionsList == null) {
      return new ArrayList<>();
    }

    AzureArtifactsPackageVersions packageVersions = packageVersionsList.body();

    List<AzureArtifactsPackageVersion> versions = packageVersions.getValue();

    versions = versions.stream()
                   .filter(azureArtifactsPackageVersion
                       -> azureArtifactsPackageVersion != null && isNotBlank(azureArtifactsPackageVersion.getVersion()))
                   .collect(Collectors.toList());

    List<BuildDetails> buildDetails = new ArrayList<>();

    versions.forEach(azureArtifactsPackageVersion -> constructBuildDetails(buildDetails, azureArtifactsPackageVersion));

    if (buildDetails.isEmpty()) {
      log.info("No builds found matching project={}, feed={} and packageId={}",
          azureArtifactsInternalConfig.getProject(), azureArtifactsInternalConfig.getFeed(),
          azureArtifactsInternalConfig.getPackageId());
    } else {
      log.info("Total builds found = {}", buildDetails.size());
    }

    return buildDetails;
  }

  @Override
  public BuildDetails getLastSuccessfulBuildFromRegex(AzureArtifactsInternalConfig azureArtifactsInternalConfig,
      String packageType, String packageName, String versionRegex, String feed, String project, String scope) {
    AzureArtifactsRestClient azureArtifactsRegistryRestClient =
        getAzureArtifactsRestClient(azureArtifactsInternalConfig.getAzureArtifactsRegistryUrl(), project);

    String authHeader = getAuthHeader(azureArtifactsInternalConfig);

    List<AzureArtifactsFeed> feeds = listFeeds(azureArtifactsInternalConfig, project);

    String feedId = null;

    for (AzureArtifactsFeed azureArtifactsFeed : feeds) {
      if (azureArtifactsFeed.getName().equals(feed)) {
        feedId = azureArtifactsFeed.getId();

        break;
      }
    }

    List<AzureArtifactsPackage> packages = listPackages(azureArtifactsInternalConfig, project, feed, packageType);

    String packageId = null;

    for (AzureArtifactsPackage azureArtifactsPackage : packages) {
      if (azureArtifactsPackage.getName().equals(packageName)) {
        packageId = azureArtifactsPackage.getId();

        break;
      }
    }

    Response<AzureArtifactsPackageVersions> packageVersionsList;

    try {
      packageVersionsList =
          azureArtifactsRegistryRestClient.listPackageVersions(authHeader, feedId, packageId).execute();
    } catch (IOException e) {
      throw new HintException("Failed to get the last successful build from regex.");
    }

    if (packageVersionsList == null) {
      return null;
    }

    AzureArtifactsPackageVersions packageVersions = packageVersionsList.body();

    List<AzureArtifactsPackageVersion> versions = packageVersions.getValue();

    versions = versions.stream()
                   .filter(azureArtifactsPackageVersion
                       -> azureArtifactsPackageVersion != null && isNotBlank(azureArtifactsPackageVersion.getVersion()))
                   .collect(Collectors.toList());

    List<BuildDetails> buildDetails = new ArrayList<>();

    versions.forEach(azureArtifactsPackageVersion -> constructBuildDetails(buildDetails, azureArtifactsPackageVersion));

    if (buildDetails.isEmpty()) {
      log.info("No builds found matching project={}, feed={} and packageId={}",
          azureArtifactsInternalConfig.getProject(), azureArtifactsInternalConfig.getFeed(),
          azureArtifactsInternalConfig.getPackageId());
    } else {
      log.info("Total builds found = {}", buildDetails.size());
    }

    return buildDetails.get(0);
  }

  @Override
  public BuildDetails getBuild(AzureArtifactsInternalConfig azureArtifactsInternalConfig, String packageType,
      String packageName, String version, String feed, String project) {
    AzureArtifactsRestClient azureArtifactsRegistryRestClient =
        getAzureArtifactsRestClient(azureArtifactsInternalConfig.getAzureArtifactsRegistryUrl(), project);

    String authHeader = getAuthHeader(azureArtifactsInternalConfig);

    List<AzureArtifactsFeed> feeds = listFeeds(azureArtifactsInternalConfig, project);

    String feedId = null;

    for (AzureArtifactsFeed azureArtifactsFeed : feeds) {
      if (azureArtifactsFeed.getName().equals(feed)) {
        feedId = azureArtifactsFeed.getId();

        break;
      }
    }

    List<AzureArtifactsPackage> packages = listPackages(azureArtifactsInternalConfig, project, feed, packageType);

    String packageId = null;

    for (AzureArtifactsPackage azureArtifactsPackage : packages) {
      if (azureArtifactsPackage.getName().equals(packageName)) {
        packageId = azureArtifactsPackage.getId();
        break;
      }
    }

    Response<AzureArtifactsPackageVersion> packageVersion;

    try {
      packageVersion =
          azureArtifactsRegistryRestClient.getPackageVersion(authHeader, feedId, packageId, version).execute();
    } catch (IOException e) {
      throw new HintException("Failed to get the Build.");
    }

    if (packageVersion == null) {
      return null;
    }

    AzureArtifactsPackageVersion build = packageVersion.body();

    List<BuildDetails> buildDetails = new ArrayList<>();

    constructBuildDetails(buildDetails, build);

    return buildDetails.get(0);
  }

  @Override
  public List<AzureDevopsProject> listProjects(AzureArtifactsInternalConfig azureArtifactsInternalConfig) {
    AzureDevopsRestClient azureArtifactsDevopsRestClient =
        getAzureDevopsRestClient(azureArtifactsInternalConfig.getAzureArtifactsRegistryUrl());

    String authHeader = getAuthHeader(azureArtifactsInternalConfig);

    Response<AzureDevopsProjects> projectResponse;

    try {
      projectResponse = azureArtifactsDevopsRestClient.listProjects(authHeader).execute();
    } catch (IOException e) {
      throw new HintException("Failed to get the Azure Projects list.");
    }

    if (projectResponse == null) {
      return new ArrayList<>();
    }

    AzureDevopsProjects ngAzureArtifactsProjects = projectResponse.body();

    return ngAzureArtifactsProjects.getValue();
  }

  @Override
  public List<AzureArtifactsPackage> listPackages(
      AzureArtifactsInternalConfig azureArtifactsInternalConfig, String project, String feed, String packageType) {
    AzureArtifactsRestClient azureArtifactsRegistryRestClient =
        getAzureArtifactsRestClient(azureArtifactsInternalConfig.getAzureArtifactsRegistryUrl(), project);

    String authHeader = getAuthHeader(azureArtifactsInternalConfig);

    List<AzureArtifactsFeed> feeds = listFeeds(azureArtifactsInternalConfig, project);

    String feedId = null;

    for (AzureArtifactsFeed azureArtifactsFeed : feeds) {
      if (azureArtifactsFeed.getName().equals(feed)) {
        feedId = azureArtifactsFeed.getId();

        break;
      }
    }

    Response<AzureArtifactsPackages> packagesResponse;

    try {
      packagesResponse = azureArtifactsRegistryRestClient.listPackages(authHeader, feedId, packageType).execute();
    } catch (IOException e) {
      throw new HintException("Failed to get the Azure Packages list.");
    }

    if (packagesResponse == null) {
      return new ArrayList<>();
    }

    AzureArtifactsPackages ngAzureArtifactsPackages = packagesResponse.body();

    return ngAzureArtifactsPackages.getValue();
  }

  @Override
  public List<AzureArtifactsFeed> listFeeds(AzureArtifactsInternalConfig azureArtifactsInternalConfig, String project) {
    AzureArtifactsRestClient azureArtifactsRegistryRestClient =
        getAzureArtifactsRestClient(azureArtifactsInternalConfig.getAzureArtifactsRegistryUrl(), project);

    String authHeader = getAuthHeader(azureArtifactsInternalConfig);

    Response<AzureArtifactsFeeds> feedsResponse;

    try {
      feedsResponse = azureArtifactsRegistryRestClient.listFeeds(authHeader).execute();
    } catch (IOException e) {
      throw new HintException("Failed to get the Azure Feeds list.");
    }

    if (feedsResponse == null) {
      return new ArrayList<>();
    }

    AzureArtifactsFeeds azureArtifactsFeeds = feedsResponse.body();

    return azureArtifactsFeeds.getValue();
  }

  private void constructBuildDetails(
      List<BuildDetails> buildDetails, AzureArtifactsPackageVersion azureArtifactsPackageVersion) {
    Map<String, String> metadata = new HashMap<>();

    metadata.put(ArtifactMetadataKeys.version, azureArtifactsPackageVersion.getVersion());
    metadata.put(ArtifactMetadataKeys.versionId, azureArtifactsPackageVersion.getId());
    metadata.put(ArtifactMetadataKeys.publishDate, azureArtifactsPackageVersion.getPublishDate());

    buildDetails.add(aBuildDetails()
                         .withNumber(azureArtifactsPackageVersion.getVersion())
                         .withRevision(azureArtifactsPackageVersion.getVersion())
                         .withMetadata(metadata)
                         .withUiDisplayName("Version# " + azureArtifactsPackageVersion.getVersion())
                         .build());
  }

  public static String getAuthHeader(AzureArtifactsInternalConfig azureArtifactsConfig) {
    return "Basic " + encodeBase64(format(":%s", azureArtifactsConfig.getToken()));
  }

  public static AzureDevopsRestClient getAzureDevopsRestClient(String azureDevopsUrl) {
    String url = ensureTrailingSlash(azureDevopsUrl);
    OkHttpClient okHttpClient = getOkHttpClientBuilder()
                                    .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                                    .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                                    .proxy(Http.checkAndGetNonProxyIfApplicable(url))
                                    .retryOnConnectionFailure(true)
                                    .build();
    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(url)
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(AzureDevopsRestClient.class);
  }

  public static AzureArtifactsRestClient getAzureArtifactsRestClient(String azureDevopsUrl, String project) {
    String url = ensureTrailingSlash(getSubdomainUrl(azureDevopsUrl, "feeds"));
    if (isNotBlank(project)) {
      url += project + "/";
    }
    OkHttpClient okHttpClient = getOkHttpClientBuilder()
                                    .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                                    .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                                    .proxy(Http.checkAndGetNonProxyIfApplicable(url))
                                    .retryOnConnectionFailure(true)
                                    .build();
    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(url)
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(AzureArtifactsRestClient.class);
  }

  private static String ensureTrailingSlash(String azureDevopsUrl) {
    return azureDevopsUrl + (azureDevopsUrl.endsWith("/") ? "" : "/");
  }

  public static String getSubdomainUrl(String azureDevopsUrl, String subdomain) {
    try {
      validateAzureDevopsUrl(azureDevopsUrl);
      if (azureDevopsUrl.startsWith("https://")) {
        return format("https://%s.%s", subdomain, azureDevopsUrl.substring(8));
      } else if (azureDevopsUrl.startsWith("http://")) {
        return format("http://%s.%s", subdomain, azureDevopsUrl.substring(7));
      } else {
        return azureDevopsUrl;
      }
    } catch (InvalidArtifactServerException e) {
      return azureDevopsUrl;
    }
  }

  public static void validateAzureDevopsUrl(String azureDevopsUrl) {
    try {
      URI uri = new URI(azureDevopsUrl);
    } catch (URISyntaxException e) {
      throw new InvalidArtifactServerException("Azure DevOps URL is invalid.");
    }
  }
}
