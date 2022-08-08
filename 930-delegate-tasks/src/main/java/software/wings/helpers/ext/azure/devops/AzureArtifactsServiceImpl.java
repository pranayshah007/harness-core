/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.azure.devops;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.helpers.ext.azure.devops.AzureArtifactsServiceHelper.execute;
import static software.wings.helpers.ext.azure.devops.AzureArtifactsServiceHelper.getAuthHeader;
import static software.wings.helpers.ext.azure.devops.AzureArtifactsServiceHelper.getAzureArtifactsDownloadClient;
import static software.wings.helpers.ext.azure.devops.AzureArtifactsServiceHelper.getAzureArtifactsRestClient;
import static software.wings.helpers.ext.azure.devops.AzureArtifactsServiceHelper.getAzureDevopsRestClient;
import static software.wings.helpers.ext.azure.devops.AzureArtifactsServiceHelper.getInputStreamSize;
import static software.wings.helpers.ext.azure.devops.AzureArtifactsServiceHelper.getMavenDownloadUrl;
import static software.wings.helpers.ext.azure.devops.AzureArtifactsServiceHelper.getNuGetDownloadUrl;
import static software.wings.helpers.ext.azure.devops.AzureArtifactsServiceHelper.shouldDownloadFile;
import static software.wings.helpers.ext.azure.devops.AzureArtifactsServiceHelper.validateAzureDevopsUrl;
import static software.wings.helpers.ext.azure.devops.AzureArtifactsServiceHelper.validateRawResponse;
import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;
import static software.wings.service.intfc.BuildService.ARTIFACT_RETENTION_SIZE;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.AzureArtifactsArtifactStreamProtocolType;
import software.wings.beans.settings.azureartifacts.AzureArtifactsConfig;
import software.wings.delegatetasks.collect.artifacts.ArtifactCollectionCommonTaskHelper;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.utils.ArtifactType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class AzureArtifactsServiceImpl implements AzureArtifactsService {
  private static final int AZURE_ARTIFACTS_MAX_BUILDS_METADATA_ONLY = 1000;
  private static final String INVALID_VERSION_MESSAGE = "version is invalid";
  private static final String INVALID_VERSION_ID_MESSAGE = "versionId is invalid";
  private static final String INVALID_PROTOCOL_TYPE_MESSAGE = "protocolType is invalid";

  @Inject private EncryptionService encryptionService;
  @Inject private ArtifactCollectionCommonTaskHelper artifactCollectionCommonTaskHelper;

  @Override
  public boolean validateArtifactServer(
      AzureArtifactsConfig azureArtifactsConfig, List<EncryptedDataDetail> encryptionDetails, boolean validateUrl) {
    encryptionService.decrypt(azureArtifactsConfig, encryptionDetails, false);
    if (validateUrl) {
      validateAzureDevopsUrl(azureArtifactsConfig.getAzureDevopsUrl());
    }

    execute(getAzureDevopsRestClient(azureArtifactsConfig.getAzureDevopsUrl())
                .listProjects(getAuthHeader(azureArtifactsConfig)));
    return true;
  }

  @Override
  public boolean validateArtifactSource(AzureArtifactsConfig azureArtifactsConfig,
      List<EncryptedDataDetail> encryptionDetails, ArtifactStreamAttributes artifactStreamAttributes) {
    AzureArtifactsPackage azureArtifactsPackage =
        getPackage(azureArtifactsConfig, encryptionDetails, artifactStreamAttributes.getProject(),
            artifactStreamAttributes.getFeed(), artifactStreamAttributes.getPackageId());
    return Objects.equals(artifactStreamAttributes.getPackageId(), azureArtifactsPackage.getId())
        && Objects.equals(artifactStreamAttributes.getProtocolType(), azureArtifactsPackage.getProtocolType());
  }

  @Override
  public List<BuildDetails> getBuilds(ArtifactStreamAttributes artifactStreamAttributes,
      AzureArtifactsConfig azureArtifactsConfig, List<EncryptedDataDetail> encryptionDetails) {
    List<AzureArtifactsPackageVersion> azureArtifactsPackageVersions =
        listPackageVersions(azureArtifactsConfig, encryptionDetails, artifactStreamAttributes.getProject(),
            artifactStreamAttributes.getFeed(), artifactStreamAttributes.getPackageId());
    if (EmptyPredicate.isEmpty(azureArtifactsPackageVersions)) {
      return Collections.emptyList();
    }

    int maxBuilds =
        artifactStreamAttributes.isMetadataOnly() ? AZURE_ARTIFACTS_MAX_BUILDS_METADATA_ONLY : ARTIFACT_RETENTION_SIZE;
    if (azureArtifactsPackageVersions.size() > maxBuilds) {
      log.info("Fetching top {} (out of {}) versions only", maxBuilds, azureArtifactsPackageVersions.size());
    }
    azureArtifactsPackageVersions.sort(
        Collections.reverseOrder(Comparator.comparing(AzureArtifactsPackageVersion::getPublishDate)));
    azureArtifactsPackageVersions =
        azureArtifactsPackageVersions.stream()
            .filter(azureArtifactsPackageVersion
                -> azureArtifactsPackageVersion != null && isNotBlank(azureArtifactsPackageVersion.getVersion()))
            .limit(maxBuilds)
            .collect(Collectors.toList());
    Collections.reverse(azureArtifactsPackageVersions);

    List<BuildDetails> buildDetails = new ArrayList<>();
    azureArtifactsPackageVersions.forEach(
        azureArtifactsPackageVersion -> constructBuildDetails(buildDetails, azureArtifactsPackageVersion));
    if (buildDetails.isEmpty()) {
      log.info("No builds found matching project={}, feed={} and packageId={}", artifactStreamAttributes.getProject(),
          artifactStreamAttributes.getFeed(), artifactStreamAttributes.getPackageId());
    } else {
      log.info("Total builds found = {}", buildDetails.size());
    }
    return buildDetails;
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

  @Override
  public List<AzureDevopsProject> listProjects(
      AzureArtifactsConfig azureArtifactsConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(azureArtifactsConfig, encryptionDetails, false);
    return execute(getAzureDevopsRestClient(azureArtifactsConfig.getAzureDevopsUrl())
                       .listProjects(getAuthHeader(azureArtifactsConfig)))
        .getValue();
  }

  @Override
  public List<AzureArtifactsFeed> listFeeds(
      AzureArtifactsConfig azureArtifactsConfig, List<EncryptedDataDetail> encryptionDetails, String project) {
    encryptionService.decrypt(azureArtifactsConfig, encryptionDetails, false);
    List<AzureArtifactsFeed> azureArtifactsFeeds =
        execute(getAzureArtifactsRestClient(azureArtifactsConfig.getAzureDevopsUrl(), project)
                    .listFeeds(getAuthHeader(azureArtifactsConfig)))
            .getValue();
    if (isBlank(project) && EmptyPredicate.isNotEmpty(azureArtifactsFeeds)) {
      // Filter out feeds that belong to a project.
      azureArtifactsFeeds =
          azureArtifactsFeeds.stream().filter(feed -> feed.getProject() == null).collect(Collectors.toList());
    }
    return azureArtifactsFeeds;
  }

  @Override
  public List<AzureArtifactsPackage> listPackages(AzureArtifactsConfig azureArtifactsConfig,
      List<EncryptedDataDetail> encryptionDetails, String project, String feed, String protocolType) {
    encryptionService.decrypt(azureArtifactsConfig, encryptionDetails, false);
    return execute(getAzureArtifactsRestClient(azureArtifactsConfig.getAzureDevopsUrl(), project)
                       .listPackages(getAuthHeader(azureArtifactsConfig), feed, protocolType))
        .getValue();
  }

  @Override
  public List<AzureArtifactsPackageFileInfo> listFiles(AzureArtifactsConfig azureArtifactsConfig,
      List<EncryptedDataDetail> encryptionDetails, ArtifactStreamAttributes artifactStreamAttributes,
      Map<String, String> artifactMetadata, boolean nameOnly) {
    encryptionService.decrypt(azureArtifactsConfig, encryptionDetails, false);
    String protocolType = artifactStreamAttributes.getProtocolType();
    String version = artifactMetadata.getOrDefault(ArtifactMetadataKeys.version, null);
    if (isBlank(version)) {
      throw new InvalidRequestException(INVALID_VERSION_MESSAGE);
    }

    if (AzureArtifactsArtifactStreamProtocolType.nuget.name().equals(protocolType)) {
      String packageName = artifactStreamAttributes.getPackageName();
      if (nameOnly) {
        return Collections.singletonList(new AzureArtifactsPackageFileInfo(packageName, -1));
      }

      long size;
      try {
        InputStream inputStream = downloadArtifactByUrl(
            getNuGetDownloadUrl(azureArtifactsConfig.getAzureDevopsUrl(), artifactStreamAttributes, version),
            getAuthHeader(azureArtifactsConfig));
        size = getInputStreamSize(inputStream);
        inputStream.close();
      } catch (IOException e) {
        throw new InvalidArtifactServerException(ExceptionUtils.getMessage(e), e);
      }
      return Collections.singletonList(new AzureArtifactsPackageFileInfo(packageName, size));
    } else if (AzureArtifactsArtifactStreamProtocolType.maven.name().equals(protocolType)) {
      String project = artifactStreamAttributes.getProject();
      String feed = artifactStreamAttributes.getFeed();
      String packageId = artifactStreamAttributes.getPackageId();
      String versionId = artifactMetadata.getOrDefault(ArtifactMetadataKeys.versionId, null);
      if (isBlank(versionId)) {
        throw new InvalidRequestException(INVALID_VERSION_ID_MESSAGE);
      }

      AzureArtifactsPackageVersion packageVersion =
          execute(getAzureArtifactsRestClient(azureArtifactsConfig.getAzureDevopsUrl(), project)
                      .getPackageVersion(getAuthHeader(azureArtifactsConfig), feed, packageId, versionId));
      if (packageVersion == null || EmptyPredicate.isEmpty(packageVersion.getFiles())) {
        return Collections.emptyList();
      }

      return packageVersion.getFiles()
          .stream()
          .filter(packageFile -> {
            String artifactFileName = packageFile.getName();
            if (!shouldDownloadFile(artifactFileName)) {
              return false;
            }

            long artifactFileSize =
                packageFile.getProtocolMetadata() == null || packageFile.getProtocolMetadata().getData() == null
                ? 0
                : packageFile.getProtocolMetadata().getData().getSize();
            return artifactFileSize > 0;
          })
          .map(packageFile
              -> new AzureArtifactsPackageFileInfo(
                  packageFile.getName(), packageFile.getProtocolMetadata().getData().getSize()))
          .collect(Collectors.toList());
    }

    return Collections.emptyList();
  }

  @Override
  public void downloadArtifact(AzureArtifactsConfig azureArtifactsConfig, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes, Map<String, String> artifactMetadata, String delegateId,
      String taskId, String accountId, ListNotifyResponseData notifyResponseData) {
    String protocolType = artifactStreamAttributes.getProtocolType();
    String project = artifactStreamAttributes.getProject();
    String feed = artifactStreamAttributes.getFeed();
    String version = artifactMetadata.getOrDefault(ArtifactMetadataKeys.version, null);
    if (isBlank(version)) {
      throw new InvalidRequestException(INVALID_VERSION_MESSAGE);
    }
    String versionId = artifactMetadata.getOrDefault(ArtifactMetadataKeys.versionId, null);
    if (isBlank(versionId)) {
      throw new InvalidRequestException(INVALID_VERSION_ID_MESSAGE);
    }

    encryptionService.decrypt(azureArtifactsConfig, encryptionDetails, false);
    try {
      String authHeader = getAuthHeader(azureArtifactsConfig);
      if (AzureArtifactsArtifactStreamProtocolType.maven.name().equals(protocolType)) {
        String packageId = artifactStreamAttributes.getPackageId();

        AzureArtifactsPackageVersion packageVersion =
            execute(getAzureArtifactsRestClient(azureArtifactsConfig.getAzureDevopsUrl(), project)
                        .getPackageVersion(authHeader, feed, packageId, versionId));
        if (packageVersion == null || EmptyPredicate.isEmpty(packageVersion.getFiles())) {
          return;
        }

        packageVersion.getFiles().forEach(packageFile -> {
          String artifactFileName = packageFile.getName();
          if (!shouldDownloadFile(artifactFileName)) {
            return;
          }

          String url = getMavenDownloadUrl(
              azureArtifactsConfig.getAzureDevopsUrl(), artifactStreamAttributes, version, artifactFileName);
          downloadArtifactByUrl(delegateId, taskId, accountId, notifyResponseData, artifactFileName, url, authHeader);
        });
      } else if (AzureArtifactsArtifactStreamProtocolType.nuget.name().equals(protocolType)) {
        String packageName = artifactStreamAttributes.getPackageName();
        String url = getNuGetDownloadUrl(azureArtifactsConfig.getAzureDevopsUrl(), artifactStreamAttributes, version);
        downloadArtifactByUrl(delegateId, taskId, accountId, notifyResponseData, packageName, url, authHeader);
      }
    } catch (Exception e) {
      throw new InvalidArtifactServerException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public Pair<String, InputStream> downloadArtifact(AzureArtifactsConfig azureArtifactsConfig,
      List<EncryptedDataDetail> encryptionDetails, ArtifactStreamAttributes artifactStreamAttributes,
      Map<String, String> artifactMetadata) {
    String protocolType = artifactStreamAttributes.getProtocolType();
    String version = artifactMetadata.getOrDefault(ArtifactMetadataKeys.version, null);
    if (isBlank(version)) {
      throw new InvalidRequestException(INVALID_VERSION_MESSAGE);
    }
    String artifactFileName = artifactMetadata.getOrDefault(ArtifactMetadataKeys.artifactFileName, null);
    if (isBlank(artifactFileName)) {
      throw new InvalidRequestException("artifactFileName is invalid");
    }

    encryptionService.decrypt(azureArtifactsConfig, encryptionDetails, false);
    try {
      if (AzureArtifactsArtifactStreamProtocolType.maven.name().equals(protocolType)) {
        List<AzureArtifactsPackageFileInfo> files =
            listFiles(azureArtifactsConfig, encryptionDetails, artifactStreamAttributes, artifactMetadata, true);
        String fileName = files.stream()
                              .map(AzureArtifactsPackageFileInfo::getName)
                              .filter(AzureArtifactsServiceHelper::shouldDownloadFile)
                              .filter(name -> filterExtension(artifactStreamAttributes.getArtifactType(), name))
                              .findFirst()
                              .orElse(null);
        if (isBlank(fileName)) {
          throw new InvalidRequestException(
              "No file available for download for type " + artifactStreamAttributes.getArtifactType());
        }
        return ImmutablePair.of(artifactFileName,
            downloadArtifactByUrl(getMavenDownloadUrl(azureArtifactsConfig.getAzureDevopsUrl(),
                                      artifactStreamAttributes, version, fileName),
                getAuthHeader(azureArtifactsConfig)));
      } else if (AzureArtifactsArtifactStreamProtocolType.nuget.name().equals(protocolType)) {
        return ImmutablePair.of(artifactFileName,
            downloadArtifactByUrl(
                getNuGetDownloadUrl(azureArtifactsConfig.getAzureDevopsUrl(), artifactStreamAttributes, version),
                getAuthHeader(azureArtifactsConfig)));
      } else {
        throw new InvalidRequestException(INVALID_PROTOCOL_TYPE_MESSAGE);
      }
    } catch (IOException e) {
      throw new InvalidArtifactServerException(ExceptionUtils.getMessage(e), e);
    }
  }

  private boolean filterExtension(ArtifactType artifactType, String fileName) {
    if (artifactType == null) {
      return false;
    }

    if (artifactType == ArtifactType.WAR) {
      return fileName.endsWith("war");
    }

    if (artifactType == ArtifactType.ZIP) {
      return fileName.endsWith("zip");
    }

    if (artifactType == ArtifactType.JAR) {
      return fileName.endsWith("jar");
    }

    return false;
  }

  private void downloadArtifactByUrl(String delegateId, String taskId, String accountId,
      ListNotifyResponseData notifyResponseData, String artifactFileName, String artifactDownloadUrl,
      String authHeader) {
    try {
      artifactCollectionCommonTaskHelper.addDataToResponse(
          ImmutablePair.of(artifactFileName, downloadArtifactByUrl(artifactDownloadUrl, authHeader)),
          artifactDownloadUrl, notifyResponseData, delegateId, taskId, accountId);
    } catch (IOException e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  private InputStream downloadArtifactByUrl(String artifactDownloadUrl, String authHeader) throws IOException {
    OkHttpClient okHttpClient = getAzureArtifactsDownloadClient(artifactDownloadUrl);
    Request request = new Request.Builder().url(artifactDownloadUrl).addHeader("Authorization", authHeader).build();
    Response response = okHttpClient.newCall(request).execute();
    validateRawResponse(response);
    ResponseBody responseBody = response.body();
    if (responseBody == null) {
      throw new InvalidArtifactServerException(format("Unable to download artifact: %s", artifactDownloadUrl));
    }
    return responseBody.byteStream();
  }

  private AzureArtifactsPackage getPackage(AzureArtifactsConfig azureArtifactsConfig,
      List<EncryptedDataDetail> encryptionDetails, String project, String feed, String packageId) {
    encryptionService.decrypt(azureArtifactsConfig, encryptionDetails, false);
    return execute(getAzureArtifactsRestClient(azureArtifactsConfig.getAzureDevopsUrl(), project)
                       .getPackage(getAuthHeader(azureArtifactsConfig), feed, packageId));
  }

  private List<AzureArtifactsPackageVersion> listPackageVersions(AzureArtifactsConfig azureArtifactsConfig,
      List<EncryptedDataDetail> encryptionDetails, String project, String feed, String packageId) {
    encryptionService.decrypt(azureArtifactsConfig, encryptionDetails, false);
    return execute(getAzureArtifactsRestClient(azureArtifactsConfig.getAzureDevopsUrl(), project)
                       .listPackageVersions(getAuthHeader(azureArtifactsConfig), feed, packageId))
        .getValue();
  }
}
