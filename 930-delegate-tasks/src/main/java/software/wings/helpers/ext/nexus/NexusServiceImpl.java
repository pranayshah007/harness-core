/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.nexus;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.nexus.NexusHelper.handleException;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.WingsException;
import io.harness.network.Http;
import io.harness.nexus.NexusHelper;
import io.harness.nexus.NexusRequest;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.utils.ArtifactType;
import software.wings.utils.RepositoryFormat;
import software.wings.utils.RepositoryType;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import javax.xml.stream.XMLStreamException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import retrofit2.Converter;
import retrofit2.Retrofit;

/**
 * Created by srinivas on 3/28/17.
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@OwnedBy(CDC)
@TargetModule(HarnessModule._960_API_SERVICES)
@Singleton
@Slf4j
public class NexusServiceImpl implements NexusService {
  @Inject private NexusThreeServiceImpl nexusThreeService;
  @Inject private NexusTwoServiceImpl nexusTwoService;
  @Inject private TimeLimiter timeLimiter;

  public static Retrofit getRetrofit(NexusRequest nexusConfig, Converter.Factory converterFactory) {
    String baseUrl = NexusHelper.getBaseUrl(nexusConfig);
    return new Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(converterFactory)
        .client(Http.getOkHttpClient(baseUrl, nexusConfig.isCertValidationRequired()))
        .build();
  }

  @Override
  public List<String> getGroupIdPaths(NexusRequest nexusConfig, String repoId, String repositoryFormat) {
    List<String> groupIds = new ArrayList<>();
    try {
      boolean isNexusTwo = nexusConfig.getVersion() == null || nexusConfig.getVersion().equalsIgnoreCase("2.x");
      if (isNexusTwo) {
        HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(20),
            () -> nexusTwoService.collectGroupIds(nexusConfig, repoId, groupIds, repositoryFormat));
      } else {
        if (repositoryFormat != null) {
          switch (repositoryFormat) {
            case "raw":
            case "nuget":
            case "npm":
              return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(20),
                  () -> nexusThreeService.getPackageNames(nexusConfig, repoId, repositoryFormat, groupIds));
            case "maven":
              return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(20),
                  () -> nexusThreeService.getGroupIds(nexusConfig, repoId, repositoryFormat, groupIds));
            case "docker":
              return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(20),
                  () -> nexusThreeService.getDockerImages(nexusConfig, repoId, groupIds));
            default:
              throw new InvalidArtifactServerException("Unsupported repositoryFormat for Nexus 3.x");
          }
        } else {
          // for backward compatibility  with old UI when repositoryFormat is null
          return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(20),
              () -> nexusThreeService.getDockerImages(nexusConfig, repoId, groupIds));
        }
      }
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      log.error(
          "Failed to fetch images/groups from Nexus server " + nexusConfig.getNexusUrl() + " under repo " + repoId, e);
      if (e.getCause() instanceof XMLStreamException) {
        throw new InvalidArtifactServerException("Nexus may not be running", e);
      } else if (e.getCause() instanceof SocketTimeoutException || e instanceof SocketTimeoutException) {
        throw new ArtifactServerException(
            "Timed out while connecting to the nexus server " + nexusConfig.getNexusUrl() + " under repo " + repoId,
            e.getCause(), USER);
      } else if (e.getCause() instanceof TimeoutException || e instanceof TimeoutException) {
        throw new ArtifactServerException("Timed out while fetching images/groups from Nexus server "
                + nexusConfig.getNexusUrl() + " under repo " + repoId,
            e.getCause(), USER);
      } else {
        throw new ArtifactServerException(
            "Failed to fetch images/groups from Nexus server: " + e.getMessage(), e.getCause(), USER);
      }
    }
    return groupIds;
  }

  @Override
  public List<String> getArtifactPaths(NexusRequest nexusConfig, String repoId) {
    try {
      return nexusTwoService.getArtifactPaths(nexusConfig, repoId);
    } catch (final IOException e) {
      log.error("Error occurred while retrieving repository contents from Nexus Server " + nexusConfig.getNexusUrl()
              + " for repository " + repoId,
          e);
      handleException(e);
    }
    return new ArrayList<>();
  }

  @Override
  public List<String> getArtifactPaths(NexusRequest nexusConfig, String repoId, String name) {
    try {
      return nexusTwoService.getArtifactPaths(nexusConfig, repoId, name);
    } catch (final IOException e) {
      log.error("Error occurred while retrieving Artifact paths from Nexus server " + nexusConfig.getNexusUrl()
              + " for Repository " + repoId,
          e);
      handleException(e);
    }
    return new ArrayList<>();
  }

  @Override
  public Pair<String, InputStream> downloadArtifacts(NexusRequest nexusConfig,
      ArtifactStreamAttributes artifactStreamAttributes, Map<String, String> artifactMetadata, String delegateId,
      String taskId, String accountId, ListNotifyResponseData notifyResponseData) {
    try {
      boolean isNexusTwo = nexusConfig.getVersion() == null || nexusConfig.getVersion().equalsIgnoreCase("2.x");
      if (isNexusTwo) {
        return nexusTwoService.downloadArtifact(
            nexusConfig, artifactStreamAttributes, artifactMetadata, delegateId, taskId, accountId, notifyResponseData);
      } else {
        return nexusThreeService.downloadArtifact(
            nexusConfig, artifactStreamAttributes, artifactMetadata, delegateId, taskId, accountId, notifyResponseData);
      }
    } catch (IOException e) {
      log.error("Error occurred while downloading the artifact", e);
      throw new ArtifactServerException(ExceptionUtils.getMessage(e), USER);
    }
  }

  @Override
  public List<String> getArtifactNames(NexusRequest nexusConfig, String repoId, String path) {
    try {
      return nexusTwoService.getArtifactNames(nexusConfig, repoId, path);
    } catch (final IOException e) {
      log.error(
          format("Error occurred while retrieving artifact names from Nexus server %s for Repository %s under path %s",
              nexusConfig.getNexusUrl(), repoId, path),
          e);
      handleException(e);
    }
    return new ArrayList<>();
  }

  @Override
  public List<String> getArtifactNames(NexusRequest nexusConfig, String repoId, String path, String repositoryFormat) {
    try {
      if (repositoryFormat.equals(RepositoryFormat.maven.name())) {
        return nexusThreeService.getArtifactNames(nexusConfig, repoId, path);
      }
    } catch (final IOException e) {
      log.error(
          format("Error occurred while retrieving artifact names from Nexus server %s for Repository %s under path %s",
              nexusConfig.getNexusUrl(), repoId, path),
          e);
      handleException(e);
    }
    return new ArrayList<>();
  }

  @Override
  public List<BuildDetails> getPackageNames(
      NexusRequest nexusConfig, String repoId, String packageName, String repositoryFormat) {
    try {
      if (repositoryFormat.equals(RepositoryFormat.raw.name())) {
        return nexusThreeService.getPackageNamesBuildDetails(nexusConfig, repoId, packageName);
      }
    } catch (final IOException e) {
      log.error(
          format(
              "Error occurred while retrieving package names from Nexus server %s for Repository %s and package name %s",
              nexusConfig.getNexusUrl(), repoId, packageName),
          e);
      handleException(e);
    }
    return new ArrayList<>();
  }

  @Override
  public List<BuildDetails> getVersions(NexusRequest nexusConfig, String repoId, String groupId, String artifactName,
      String extension, String classifier) {
    try {
      boolean isNexusTwo = nexusConfig.getVersion() == null || nexusConfig.getVersion().equalsIgnoreCase("2.x");
      if (isNexusTwo) {
        return nexusTwoService.getVersions(nexusConfig, repoId, groupId, artifactName, extension, classifier);
      } else {
        return nexusThreeService.getVersions(nexusConfig, repoId, groupId, artifactName, extension, classifier);
      }
    } catch (final IOException e) {
      log.error(
          format(
              "Error occurred while retrieving versions from Nexus server %s for Repository %s under group id %s and artifact name %s",
              nexusConfig.getNexusUrl(), repoId, groupId, artifactName),
          e);
      handleException(e);
    }
    return new ArrayList<>();
  }

  @Override
  public boolean existsVersion(NexusRequest nexusConfig, String repoId, String groupId, String artifactName,
      String extension, String classifier) {
    if (isEmpty(extension) && isEmpty(classifier)) {
      return true;
    }
    try {
      boolean isNexusTwo = nexusConfig.getVersion() == null || nexusConfig.getVersion().equalsIgnoreCase("2.x");
      if (isNexusTwo) {
        return nexusTwoService.existsVersion(nexusConfig, repoId, groupId, artifactName, extension, classifier);
      } else {
        return nexusThreeService.existsVersion(nexusConfig, repoId, groupId, artifactName, extension, classifier);
      }
    } catch (final IOException e) {
      log.error(
          format(
              "Error occurred while retrieving versions from Nexus server %s for Repository %s under group id %s and artifact name %s",
              nexusConfig.getNexusUrl(), repoId, groupId, artifactName),
          e);
      handleException(e);
    }
    return true;
  }

  @Override
  public List<BuildDetails> getVersions(String repositoryFormat, NexusRequest nexusConfig, String repoId,
      String packageName, Set<String> collectedBuilds) {
    try {
      boolean isNexusTwo = nexusConfig.getVersion() == null || nexusConfig.getVersion().equalsIgnoreCase("2.x");
      if (isNexusTwo) {
        return nexusTwoService.getVersions(repositoryFormat, nexusConfig, repoId, packageName, collectedBuilds);
      } else {
        return nexusThreeService.getPackageVersions(nexusConfig, repoId, packageName);
      }
    } catch (final IOException e) {
      log.error(
          format("Error occurred while retrieving versions from Nexus server %s for Repository %s under package %s",
              nexusConfig.getNexusUrl(), repoId, packageName),
          e);
      handleException(e);
    }
    return new ArrayList<>();
  }

  @Override
  @SuppressWarnings("squid:S00107")
  public List<BuildDetails> getVersion(NexusRequest nexusConfig, String repoId, String groupId, String artifactName,
      String extension, String classifier, String buildNo) {
    boolean isNexusTwo = nexusConfig.getVersion() == null || nexusConfig.getVersion().equalsIgnoreCase("2.x");
    if (isNexusTwo) {
      try {
        return nexusTwoService.getVersion(nexusConfig, repoId, groupId, artifactName, extension, classifier, buildNo);
      } catch (final IOException e) {
        log.error(
            format(
                "Error occurred while retrieving versions from Nexus server %s for Repository %s under group id %s and artifact name %s",
                nexusConfig.getNexusUrl(), repoId, groupId, artifactName),
            e);
        handleException(e);
      }
    } else {
      throw new UnsupportedOperationException(
          "Nexus 3.x does not support getVersion for parameterized artifact stream");
    }
    return new ArrayList<>();
  }

  @Override
  public List<BuildDetails> getVersion(
      String repositoryFormat, NexusRequest nexusConfig, String repoId, String packageName, String buildNo) {
    boolean isNexusTwo = nexusConfig.getVersion() == null || nexusConfig.getVersion().equalsIgnoreCase("2.x");
    if (isNexusTwo) {
      try {
        return Collections.singletonList(
            nexusTwoService.getVersion(repositoryFormat, nexusConfig, repoId, packageName, buildNo));
      } catch (final IOException e) {
        log.error(
            format("Error occurred while retrieving version %s from Nexus server %s for Repository %s under package %s",
                buildNo, nexusConfig.getNexusUrl(), repoId, packageName),
            e);
        handleException(e);
      }
    } else {
      throw new UnsupportedOperationException(
          "Nexus 3.x does not support getVersion for parameterized artifact stream");
    }
    return new ArrayList<>();
  }

  @Override
  public BuildDetails getLatestVersion(NexusRequest nexusConfig, String repoId, String groupId, String artifactName) {
    return nexusTwoService.getLatestVersion(nexusConfig, repoId, groupId, artifactName);
  }

  @Override
  public List<BuildDetails> getBuilds(
      NexusRequest nexusConfig, ArtifactStreamAttributes artifactStreamAttributes, int maxNumberOfBuilds) {
    try {
      if ((artifactStreamAttributes.getArtifactType() != null
              && artifactStreamAttributes.getArtifactType() == ArtifactType.DOCKER)
          || (artifactStreamAttributes.getRepositoryType() != null
              && artifactStreamAttributes.getRepositoryType().equals(RepositoryType.docker.name()))) {
        return nexusThreeService.getDockerTags(nexusConfig, artifactStreamAttributes);
      }
    } catch (IOException e) {
      log.error("Error occurred while retrieving tags from Nexus server {} for repository {} under image {}",
          nexusConfig.getNexusUrl(), artifactStreamAttributes.getJobName(), artifactStreamAttributes.getImageName(), e);
      handleException(e);
    }
    return new ArrayList<>();
  }

  @Override
  public Pair<String, InputStream> downloadArtifactByUrl(
      NexusRequest nexusConfig, String artifactName, String artifactUrl) {
    boolean isNexusTwo = nexusConfig.getVersion() == null || nexusConfig.getVersion().equalsIgnoreCase("2.x");
    if (isNexusTwo) {
      return nexusTwoService.downloadArtifactByUrl(nexusConfig, artifactName, artifactUrl);
    } else {
      return nexusThreeService.downloadArtifactByUrl(nexusConfig, artifactName, artifactUrl);
    }
  }

  @Override
  public long getFileSize(NexusRequest nexusConfig, String artifactName, String artifactUrl) {
    boolean isNexusTwo = nexusConfig.getVersion() == null || nexusConfig.getVersion().equalsIgnoreCase("2.x");
    if (isNexusTwo) {
      return nexusTwoService.getFileSize(nexusConfig, artifactName, artifactUrl);
    } else {
      return nexusThreeService.getFileSize(nexusConfig, artifactName, artifactUrl);
    }
  }
}
