/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactUtilities;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.task.ssh.artifact.NexusArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.nexus.NexusRequest;

import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;

@UtilityClass
@Slf4j
@OwnedBy(CDP)
public class NexusUtils {
  private static final String MAVEN_REPOSITORY_FORMAT = "maven";
  private static final String NUGET_REPOSITORY_FORMAT = "nuget";
  private static final String METADATA_URL = "url";
  private static final String METADATA_PACKAGE = "package";
  private static final String METADATA_VERSION = "version";

  public static NexusArtifactDelegateConfig getNexusArtifactDelegateConfig(
      SshWinRmArtifactDelegateConfig artifactDelegateConfig) {
    if (!(artifactDelegateConfig instanceof NexusArtifactDelegateConfig)) {
      log.error(
          "Wrong artifact delegate config submitted. Expecting nexus delegate config, artifactDelegateConfigClass: {}",
          artifactDelegateConfig.getClass());
      throw new InvalidRequestException("Invalid artifact delegate config submitted, expected Nexus config");
    }

    return (NexusArtifactDelegateConfig) artifactDelegateConfig;
  }

  public static String getBasicAuthHeader(NexusRequest nexusRequest) {
    return ArtifactUtilities.getBasicAuthHeader(
        nexusRequest.isHasCredentials(), nexusRequest.getUsername(), nexusRequest.getPassword());
  }

  public static String getNexusArtifactFileName(
      NexusVersion nexusVersion, final String repositoryFormat, Map<String, String> metadata) {
    final String artifactUrl = metadata.get(METADATA_URL);
    if (isEmpty(artifactUrl)) {
      throw new InvalidArgumentsException("Nexus metadata url cannot ne null or empty");
    }

    // NEXUS2 mvn
    if (NexusVersion.NEXUS2 == nexusVersion && MAVEN_REPOSITORY_FORMAT.equals(repositoryFormat)) {
      return buildNexus2MvnArtifactFileName(artifactUrl);
    }

    // NEXUS3 nuget, NEXUS2 nuget
    if (NUGET_REPOSITORY_FORMAT.equals(repositoryFormat)) {
      return buildNexusNugetArtifactFileName(metadata);
    }

    // NEXUS3 mvn npm, NEXUS2 npm
    return ArtifactUtilities.getArtifactName(artifactUrl);
  }

  public static String buildNexus2MvnArtifactFileName(final String mvnArtifactUrl) {
    HttpUrl httpUrl = HttpUrl.parse(mvnArtifactUrl);
    if (httpUrl == null) {
      throw new InvalidRequestException(format("Unable to parse Nexus2 maven artifact url, %s", mvnArtifactUrl));
    }

    String artifactId = httpUrl.queryParameter("a");
    if (isEmpty(artifactId)) {
      throw new InvalidRequestException(
          format("Unable to found artifact Id from Nexus artifact url, artifactUrl: %s", mvnArtifactUrl));
    }

    StringBuilder artifactName = new StringBuilder(artifactId);
    String version = httpUrl.queryParameter("v");
    if (isNotEmpty(version)) {
      artifactName.append('-').append(version);
    }
    String classifier = httpUrl.queryParameter("c");
    if (isNotEmpty(classifier)) {
      artifactName.append('-').append(classifier);
    }
    String extension = httpUrl.queryParameter("e");
    return artifactName.append('.').append(extension).toString();
  }

  private static String buildNexusNugetArtifactFileName(Map<String, String> metadata) {
    final String packageName = metadata.get(METADATA_PACKAGE);
    final String version = metadata.get(METADATA_VERSION);
    if (isEmpty(packageName)) {
      throw new InvalidArgumentsException("Nexus metadata package cannot be null or empty");
    }
    if (isEmpty(version)) {
      throw new InvalidArgumentsException("Nexus metadata version cannot be null or empty");
    }

    return format("%s-%s.nupkg", packageName, version);
  }

  public static NexusVersion getNexusVersion(NexusArtifactDelegateConfig nexusArtifactDelegateConfig) {
    NexusConnectorDTO nexusConnectorDTO =
        (NexusConnectorDTO) nexusArtifactDelegateConfig.getConnectorDTO().getConnectorConfig();
    return getNexusVersion(nexusConnectorDTO);
  }

  public static NexusVersion getNexusVersion(NexusConnectorDTO nexusConnectorDTO) {
    String version = nexusConnectorDTO.getVersion();

    if (isEmpty(version)) {
      throw new InvalidRequestException("Nexus version cannot be null or empty");
    }

    char firstVersionChar = version.charAt(0);
    if (firstVersionChar == '2') {
      return NexusVersion.NEXUS2;
    } else if (firstVersionChar == '3') {
      return NexusVersion.NEXUS3;
    }
    throw new InvalidRequestException(format("Unsupported Nexus version, %s", version));
  }
}
