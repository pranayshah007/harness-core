/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.azureartifacts.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.artifacts.azureartifacts.service.AzureArtifactsRegistryServiceImpl.getAzureArtifactsRestClient;
import static io.harness.azure.utility.AzureUtils.executeRestCall;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.azureartifacts.beans.AzureArtifactsInternalConfig;
import io.harness.azure.utility.AzureUtils;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.azure.devops.AzureArtifactsPackage;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackageVersion;
import software.wings.helpers.ext.azure.devops.AzureArtifactsRestClient;
import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

@OwnedBy(CDP)
@Singleton
public class AzureArtifactsDownloadHelper {
  @Inject private AzureArtifactsRegistryService azureArtifactsRegistryService;
  @Inject private SecretDecryptionService secretDecryptionService;

  static long getInputStreamSize(InputStream inputStream) throws IOException {
    long size = 0;
    int chunk;
    byte[] buffer = new byte[1024];
    while ((chunk = inputStream.read(buffer)) != -1) {
      size += chunk;
      if (size > Integer.MAX_VALUE) {
        return -1;
      }
    }
    return size;
  }

  static void validateRawResponse(okhttp3.Response response) {
    if (response == null) {
      throw new InvalidArtifactServerException("Null response found", USER);
    }
    if (response.code() == 401 || response.code() == 203) {
      throw new InvalidArtifactServerException(
          "Invalid Azure Artifacts credentials. The Personal Access Token might have expired", USER);
    }
    if (!response.isSuccessful()) {
      throw new InvalidArtifactServerException(response.message(), USER);
    }
  }

  String getPackageVersionId(AzureArtifactsInternalConfig azureArtifactsInternalConfig, String protocolType,
      String packageName, String feed, String project, String version) {
    List<BuildDetails> builds = azureArtifactsRegistryService.listPackageVersions(
        azureArtifactsInternalConfig, protocolType, packageName, null, feed, project);
    return builds.stream()
        .filter(buildsDetails -> version.equalsIgnoreCase(buildsDetails.getMetadata().get("version")))
        .map(buildDetails -> buildDetails.getMetadata().get("version"))
        .findFirst()
        .orElse(null);
  }

  String getPackageId(AzureArtifactsInternalConfig azureArtifactsInternalConfig, String project, String feed,
      String packageType, String packageName) {
    List<AzureArtifactsPackage> azureArtifactsPackages =
        azureArtifactsRegistryService.listPackages(azureArtifactsInternalConfig, project, feed, packageType);
    return azureArtifactsPackages.stream()
        .filter(artifactPackage -> packageName.equalsIgnoreCase(artifactPackage.getName()))
        .map(AzureArtifactsPackage::getId)
        .findFirst()
        .orElse(null);
  }

  AzureArtifactsPackageVersion getPackageVersion(
      AzureArtifactsInternalConfig azureArtifactsInternalConfig, String feed, String packageId, String versionId) {
    AzureArtifactsRestClient azureArtifactsRegistryRestClient = getAzureArtifactsRestClient(
        azureArtifactsInternalConfig.getAzureArtifactsRegistryUrl(), azureArtifactsInternalConfig.getProject());
    String authHeader = getAuthHeader(azureArtifactsInternalConfig);

    return executeRestCall(azureArtifactsRegistryRestClient.getPackageVersion(authHeader, feed, packageId, versionId),
        new HintException("Failed to get the Build."));
  }

  public static boolean shouldDownloadFile(String artifactFileName) {
    if (isBlank(artifactFileName)) {
      return false;
    }
    return Lists.newArrayList("pom", "md5", "sha1", "sha256", "sha512").stream().noneMatch(artifactFileName::endsWith);
  }

  public String getMavenDownloadUrl(
      String azureDevopsUrl, String project, String feed, String packageName, String version, String artifactFileName) {
    String url = ensureTrailingSlash(getSubdomainUrl(azureDevopsUrl, "pkgs"));
    if (isNotBlank(project)) {
      url += project + "/";
    }

    String groupId = "";
    String artifactId = "";
    if (isNotBlank(packageName)) {
      String[] parts = packageName.split(":", 2);
      if (parts.length == 2) {
        groupId = parts[0];
        artifactId = parts[1];
      }
    }
    return url
        + format("_apis/packaging/feeds/%s/maven/%s/%s/%s/%s/content?api-version=5.1-preview.1", feed, groupId,
            artifactId, version, artifactFileName);
  }

  public String getNuGetDownloadUrl(
      String azureDevopsUrl, String project, String feed, String packageName, String version) {
    String url = ensureTrailingSlash(getSubdomainUrl(azureDevopsUrl, "pkgs"));
    if (isNotBlank(project)) {
      url += project + "/";
    }

    return url
        + format("_apis/packaging/feeds/%s/nuget/packages/%s/versions/%s/content?api-version=5.1-preview.1", feed,
            packageName, version);
  }

  private static String ensureTrailingSlash(String azureDevopsUrl) {
    return azureDevopsUrl + (azureDevopsUrl.endsWith("/") ? "" : "/");
  }

  private static String getSubdomainUrl(String azureDevopsUrl, String subdomain) {
    // Assuming azureDevopsUrl starts with AZURE_DEVOPS_SERVICES_URL.
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

  private static void validateAzureDevopsUrl(String azureDevopsUrl) {
    try {
      URI uri = new URI(azureDevopsUrl);
    } catch (URISyntaxException e) {
      throw new InvalidArtifactServerException("Azure DevOps URL is invalid");
    }
  }

  public InputStream downloadArtifactByUrl(String artifactDownloadUrl, String authHeader) throws IOException {
    OkHttpClient okHttpClient = getAzureArtifactsDownloadClient(artifactDownloadUrl);
    Request request = new Request.Builder().url(artifactDownloadUrl).addHeader("Authorization", authHeader).build();
    okhttp3.Response response = okHttpClient.newCall(request).execute();
    validateRawResponse(response);
    ResponseBody responseBody = response.body();
    if (responseBody == null) {
      throw new InvalidArtifactServerException(format("Unable to download artifact: %s", artifactDownloadUrl));
    }
    return responseBody.byteStream();
  }

  private static OkHttpClient getAzureArtifactsDownloadClient(String artifactDownloadUrl) {
    return AzureUtils.getOkHtttpClientWithProxy(artifactDownloadUrl);
  }

  static String getAuthHeader(AzureArtifactsInternalConfig azureArtifactsConfig) {
    return "Basic " + encodeBase64(format(":%s", azureArtifactsConfig.getToken()));
  }
}
