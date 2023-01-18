/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.artifact.ArtifactUtilities.getBaseUrl;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsTokenDTO;
import io.harness.delegate.task.ssh.artifact.AzureArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.SecretDecryptionService;

import java.net.URI;
import java.net.URISyntaxException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(CDP)
public class AzureArtifactsUtils {
  public static AzureArtifactDelegateConfig getAzureArtifactDelegateConfig(
      SshWinRmArtifactDelegateConfig artifactDelegateConfig) {
    if (!(artifactDelegateConfig instanceof AzureArtifactDelegateConfig)) {
      log.error(
          "Wrong artifact delegate config submitted. Expecting Azure delegate config, artifactDelegateConfigClass: {}",
          artifactDelegateConfig.getClass());
      throw new InvalidRequestException("Invalid artifact delegate config submitted, expected Azure config");
    }

    return (AzureArtifactDelegateConfig) artifactDelegateConfig;
  }

  public static String getAuthHeader(String decryptedToken) {
    return "Basic " + encodeBase64(format(":%s", decryptedToken));
  }

  public static String getDecryptedToken(
      AzureArtifactDelegateConfig azureArtifactDelegateConfig, SecretDecryptionService secretDecryptionService) {
    AzureArtifactsConnectorDTO connectorDTO =
        (AzureArtifactsConnectorDTO) azureArtifactDelegateConfig.getConnectorDTO().getConnectorConfig();
    secretDecryptionService.decrypt(connectorDTO.getAuth().getCredentials().getCredentialsSpec(),
        azureArtifactDelegateConfig.getEncryptedDataDetails());
    AzureArtifactsTokenDTO azureArtifactsTokenDTO = connectorDTO.getAuth().getCredentials().getCredentialsSpec();
    return new String(azureArtifactsTokenDTO.getTokenRef().getDecryptedValue());
  }

  public static String getDownloadUrl(
      String artifactFileName, AzureArtifactDelegateConfig azureArtifactDelegateConfig) {
    AzureArtifactsConnectorDTO connectorDTO =
        (AzureArtifactsConnectorDTO) azureArtifactDelegateConfig.getConnectorDTO().getConnectorConfig();
    String azureArtifactsUrl = connectorDTO.getAzureArtifactsUrl();

    String packageType = azureArtifactDelegateConfig.getPackageType();
    if (AzureArtifactPackageType.MAVEN.getValue().equals(packageType)) {
      return getMavenDownloadUrl(
          azureArtifactsUrl, azureArtifactDelegateConfig, azureArtifactDelegateConfig.getVersion(), artifactFileName);
    } else if (AzureArtifactPackageType.NUGET.getValue().equals(packageType)) {
      return getNuGetDownloadUrl(
          azureArtifactsUrl, azureArtifactDelegateConfig, azureArtifactDelegateConfig.getVersion());
    } else {
      throw new InvalidRequestException("Invalid package type submitted, expected maven or nuget.");
    }
  }

  static String getMavenDownloadUrl(String azureDevopsUrl, AzureArtifactDelegateConfig azureArtifactDelegateConfig,
      String version, String artifactFileName) {
    String url = getBaseUrl(getSubdomainUrl(azureDevopsUrl, "pkgs"));
    String project = azureArtifactDelegateConfig.getProject();
    if (isNotBlank(project)) {
      url += project + "/";
    }

    String feed = azureArtifactDelegateConfig.getFeed();
    String packageName = azureArtifactDelegateConfig.getPackageName();
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

  static String getNuGetDownloadUrl(
      String azureDevopsUrl, AzureArtifactDelegateConfig azureArtifactDelegateConfig, String version) {
    String url = getBaseUrl(getSubdomainUrl(azureDevopsUrl, "pkgs"));
    String project = azureArtifactDelegateConfig.getProject();
    if (isNotBlank(project)) {
      url += project + "/";
    }

    String feed = azureArtifactDelegateConfig.getFeed();
    String packageName = azureArtifactDelegateConfig.getPackageName();
    return url
        + format("_apis/packaging/feeds/%s/nuget/packages/%s/versions/%s/content?api-version=5.1-preview.1", feed,
            packageName, version);
  }

  public static String getSubdomainUrl(String azureDevopsUrl, String subdomain) {
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

  public static void validateAzureDevopsUrl(String azureDevopsUrl) {
    try {
      new URI(azureDevopsUrl);
    } catch (URISyntaxException e) {
      throw new InvalidArtifactServerException(format("Azure DevOps URL is invalid: %s", azureDevopsUrl));
    }
  }

  private enum AzureArtifactPackageType {
    MAVEN("maven"),
    NUGET("nuget");

    private String value;
    AzureArtifactPackageType(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }
}
