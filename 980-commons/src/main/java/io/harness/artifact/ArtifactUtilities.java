/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifact;

import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.NestedExceptionUtils;

import java.net.MalformedURLException;
import java.net.URL;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDC)
public class ArtifactUtilities {
  public static String getArtifactoryRegistryUrl(String url, String artifactRepositoryUrl, String jobName) {
    String registryUrl;
    if (isNotEmpty(artifactRepositoryUrl)) {
      registryUrl = format("http%s://%s", url.startsWith("https") ? "s" : "", artifactRepositoryUrl);
    } else {
      int firstDotIndex = url.indexOf('.');
      int slashAfterDomain = url.indexOf('/', firstDotIndex);
      registryUrl = url.substring(0, firstDotIndex) + "-" + jobName
          + url.substring(firstDotIndex, slashAfterDomain > 0 ? slashAfterDomain : url.length());
    }
    return registryUrl;
  }

  public static String getArtifactoryRepositoryName(
      String url, String artifactRepositoryUrl, String jobName, String imageName) {
    String registryName;
    if (isNotEmpty(artifactRepositoryUrl)) {
      registryName = artifactRepositoryUrl + "/" + imageName;
    } else {
      String registryUrl = getArtifactoryRegistryUrl(url, null, jobName);
      String namePrefix = registryUrl.substring(registryUrl.indexOf("://") + 3);
      registryName = namePrefix + "/" + imageName;
    }
    return registryName;
  }

  public static String getNexusRegistryUrl(String nexusUrl, String dockerPort, String dockerRegistryUrl) {
    if (isEmpty(dockerRegistryUrl)) {
      String registryUrl = extractNexusDockerRegistryUrl(nexusUrl);
      if (isNotEmpty(dockerPort)) {
        registryUrl = registryUrl + ":" + dockerPort;
      }
      return registryUrl;
    }
    if (dockerRegistryUrl.startsWith("http") || dockerRegistryUrl.startsWith("https")) {
      // User can input the docker registry with real http or https
      return dockerRegistryUrl;
    }
    return format("http%s://%s", nexusUrl.startsWith("https") ? "s" : "", extractUrl(dockerRegistryUrl));
  }

  public static String getNexusRegistryUrlNG(String nexusUrl, String dockerPort, String dockerRegistryUrl) {
    if (isEmpty(dockerRegistryUrl)) {
      String registryUrl = extractNexusDockerRegistryUrl(nexusUrl);
      registryUrl = trimSlashforwardChars(registryUrl);
      if (isNotEmpty(dockerPort)) {
        registryUrl = registryUrl + ":" + dockerPort;
      }
      return registryUrl;
    }
    if (dockerRegistryUrl.startsWith("http") || dockerRegistryUrl.startsWith("https")) {
      // User can input the docker registry with real http or https
      return trimSlashforwardChars(dockerRegistryUrl);
    }
    return format(
        "http%s://%s", nexusUrl.startsWith("https") ? "s" : "", trimSlashforwardChars(extractUrl(dockerRegistryUrl)));
  }

  private static String extractNexusDockerRegistryUrl(String url) {
    int firstDotIndex = url.indexOf('.');
    int colonIndex = url.indexOf(':', firstDotIndex);
    int endIndex = colonIndex > 0 ? colonIndex : url.length();
    return url.substring(0, endIndex);
  }

  public static String getNexusRepositoryName(
      String nexusUrl, String dockerPort, String dockerRegistryUrl, String imageName) {
    if (isEmpty(dockerRegistryUrl)) {
      String registryUrl = getNexusRegistryUrl(nexusUrl, dockerPort, dockerRegistryUrl);
      String namePrefix = registryUrl.substring(registryUrl.indexOf("://") + 3);
      return namePrefix + "/" + imageName;
    } else {
      return extractUrl(dockerRegistryUrl) + "/" + imageName;
    }
  }

  public static String getNexusRepositoryNameNG(
      String nexusUrl, String repositoryPort, String artifactRepositoryUrl, String imageName) {
    if (isEmpty(artifactRepositoryUrl)) {
      String registryUrl = getNexusRegistryUrlNG(nexusUrl, repositoryPort, artifactRepositoryUrl);
      String namePrefix = registryUrl.substring(registryUrl.indexOf("://") + 3);
      return trimSlashforwardChars(namePrefix) + "/" + trimSlashforwardChars(imageName);
    } else {
      return trimSlashforwardChars(extractUrl(artifactRepositoryUrl)) + "/" + trimSlashforwardChars(imageName);
    }
  }

  public static String extractUrl(String dockerRegistryUrl) {
    try {
      URL url = new URL(dockerRegistryUrl);
      if (url.getPort() != -1) {
        return url.getHost() + ":" + url.getPort();
      }
      return url.getHost();
    } catch (MalformedURLException e) {
      return dockerRegistryUrl;
    }
  }

  public static String extractRegistryHost(String registryUrl) {
    try {
      URL parsedUrl = new URL(registryUrl);
      if (parsedUrl.getPort() != -1) {
        return parsedUrl.getHost() + ":" + parsedUrl.getPort();
      }
      return parsedUrl.getHost();
    } catch (MalformedURLException e) {
      if (isNotEmpty(registryUrl)) {
        int firstDotIndex = registryUrl.indexOf('.');
        int slashforwardIndex = registryUrl.indexOf('/', firstDotIndex);
        int endIndex = slashforwardIndex > 0 ? slashforwardIndex : registryUrl.length();
        return registryUrl.substring(0, endIndex);
      }
    }

    throw NestedExceptionUtils.hintWithExplanationException(
        "Please check ConnectorDisconnectHandler configuration or artifact source configuration",
        "Registry URL must of valid URL format",
        new ArtifactServerException(String.format("Registry URL is not valid [%s]", registryUrl)));
  }

  public static String getBaseUrl(String url) {
    return url.endsWith("/") ? url : url + "/";
  }

  public static String getHostname(String resourceUrl) {
    try {
      URL url = new URL(resourceUrl);
      if (isNotEmpty(url.getProtocol())) {
        return url.getProtocol() + "://" + extractUrl(resourceUrl);
      }
      return "https://" + extractUrl(resourceUrl);
    } catch (MalformedURLException e) {
      return resourceUrl;
    }
  }

  public static String getFileSearchPattern(String artifactPath) {
    int index = artifactPath.lastIndexOf('/');
    if (index == -1) {
      index = artifactPath.lastIndexOf('\\');
    }

    if (index == artifactPath.length() - 1) {
      return "*";
    } else {
      return artifactPath.substring(index + 1);
    }
  }

  public static String getFileParentPath(String artifactPath) {
    int index = artifactPath.lastIndexOf('/');
    if (index == -1) {
      index = artifactPath.lastIndexOf('\\');
    }

    if (index == -1) {
      return "";
    } else {
      return artifactPath.substring(0, index);
    }
  }

  public String trimSlashforwardChars(String stringToTrim) {
    if (isNotEmpty(stringToTrim)) {
      if (stringToTrim.charAt(0) == '/') {
        stringToTrim = stringToTrim.substring(1);
      }
    }

    if (isNotEmpty(stringToTrim)) {
      if (stringToTrim.charAt(stringToTrim.length() - 1) == '/') {
        stringToTrim = stringToTrim.substring(0, stringToTrim.length() - 1);
      }
    }
    return stringToTrim;
  }

  public static String getArtifactName(final String artifactPath) {
    if (isEmpty(artifactPath)) {
      return artifactPath;
    }

    int index = artifactPath.lastIndexOf('/');
    if (index == -1) {
      index = artifactPath.lastIndexOf('\\');
    }

    return index == -1 ? EMPTY : artifactPath.substring(index + 1);
  }

  public static String getBasicAuthHeader(boolean hasCredentials, final String username, char[] password) {
    String pair = username + ":" + new String(password);
    return hasCredentials ? "Basic " + encodeBase64(pair) : null;
  }
}
