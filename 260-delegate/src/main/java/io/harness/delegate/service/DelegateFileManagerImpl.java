/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.network.SafeHttpCall.execute;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.rest.RestResponse;

import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.delegatetasks.collect.artifacts.ArtifactCollectionTaskHelper;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.MultipartBody.Part;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import retrofit2.Response;

@Singleton
@ValidateOnExecution
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class DelegateFileManagerImpl implements DelegateFileManager {
  private static final int DEFAULT_MAX_CACHED_ARTIFACT = 2;
  private static final long ARTIFACT_FILE_SIZE_LIMIT = 4L * 1024L * 1024L * 1024L; // 4GB

  private DelegateAgentManagerClient delegateAgentManagerClient;
  private DelegateConfiguration delegateConfiguration;

  private static final LoadingCache<String, Object> fileIdLocks =
      CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.HOURS).build(CacheLoader.from(Object::new));

  private static final String ARTIFACT_REPO_BASE_DIR = "./repository/artifacts/";
  private static final String ARTIFACT_REPO_TMP_DIR = "./repository/artifacts/tmp/";

  @Inject private ArtifactCollectionTaskHelper artifactCollectionTaskHelper;

  @Inject
  public DelegateFileManagerImpl(
      DelegateAgentManagerClient delegateAgentManagerClient, DelegateConfiguration delegateConfiguration) {
    this.delegateAgentManagerClient = delegateAgentManagerClient;
    this.delegateConfiguration = delegateConfiguration;
    Executors
        .newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("delegate-file-manager").build())
        .scheduleAtFixedRate(this::deleteCachedArtifacts, 1000, 5 * 60 * (long) 1000,
            TimeUnit.MILLISECONDS); // periodic cleanup for cached artifacts
  }

  @Override
  public InputStream downloadArtifactByFileId(FileBucket bucket, String fileId, String accountId)
      throws IOException, ExecutionException {
    log.info("Downloading file:[{}] , bucket:[{}], accountId:[{}]", fileId, bucket, accountId);
    synchronized (fileIdLocks.get(fileId)) { // Block all thread only one gets to enter
      File file = new File(ARTIFACT_REPO_BASE_DIR, fileId);
      log.info("check if file:[{}] exists at location: [{}]", fileId, file.getAbsolutePath());
      if (!file.isDirectory() && file.exists()) {
        log.info("file:[{}] found locally", fileId);
        return new FileInputStream(file);
      }
      log.info("file:[{}] doesn't exist locally. Download from manager", fileId);

      InputStream inputStream = downloadByFileId(bucket, fileId, accountId);

      log.info("Input stream acquired for file:[{}]. Saving locally", fileId);

      File downloadedFile = new File(ARTIFACT_REPO_TMP_DIR, fileId);
      FileUtils.copyInputStreamToFile(inputStream, downloadedFile);
      log.info("file:[{}] saved in tmp location:[{}]", fileId, downloadedFile.getAbsolutePath());

      FileUtils.moveFile(downloadedFile, file);
      log.info("file:[{}] moved to  final destination:[{}]", fileId, file.getAbsolutePath());

      log.info("file:[{}] is ready for read access", fileId);

      log.info("check if downloaded fileId[{}] exists locally", fileId);
      if (!file.isDirectory() && file.exists()) {
        log.info("fileId[{}] found locally", fileId);
        return new FileInputStream(file);
      }

      log.error("fileId[{}] could not be found", fileId);
      throw new InvalidRequestException("File couldn't be downloaded");
    }
  }

  @Override
  public InputStream downloadArtifactAtRuntime(ArtifactStreamAttributes artifactStreamAttributes, String accountId,
      String appId, String activityId, String commandUnitName, String hostName) throws IOException, ExecutionException {
    Map<String, String> metadata = artifactStreamAttributes.getMetadata();
    String artifactFileSize = metadata.get(ArtifactMetadataKeys.artifactFileSize);
    if (null != artifactFileSize && Long.parseLong(artifactFileSize) > ARTIFACT_FILE_SIZE_LIMIT) {
      throw new InvalidRequestException("Artifact file size exceeds 4GB. Not downloading file.");
    }
    String buildNo = metadata.get(ArtifactMetadataKeys.buildNo);
    String key;
    if (ArtifactStreamType.JENKINS.name().equals(artifactStreamAttributes.getArtifactStreamType())
        || ArtifactStreamType.BAMBOO.name().equals(artifactStreamAttributes.getArtifactStreamType())
        || ArtifactStreamType.NEXUS.name().equals(artifactStreamAttributes.getArtifactStreamType())) {
      key = "_" + artifactStreamAttributes.getArtifactStreamId() + "-" + buildNo + "-"
          + metadata.get(ArtifactMetadataKeys.artifactFileName);
    } else {
      key = "_" + artifactStreamAttributes.getArtifactStreamId() + "-" + buildNo;
    }
    synchronized (fileIdLocks.get(key)) {
      File file = new File(ARTIFACT_REPO_BASE_DIR, key);
      log.info("check if artifact:[{}] exists at location: [{}]", key, file.getAbsolutePath());
      if (!file.isDirectory() && file.exists()) {
        log.info("artifact:[{}] found locally", key);
        return new FileInputStream(file);
      }
      log.info("file:[{}] doesn't exist locally. Download from manager", key);

      Pair<String, InputStream> pair = artifactCollectionTaskHelper.downloadArtifactAtRuntime(
          artifactStreamAttributes, accountId, appId, activityId, commandUnitName, hostName);
      if (pair == null) {
        throw new InvalidRequestException("File couldn't be downloaded");
      }
      log.info("Input stream acquired for file:[{}]. Saving locally", key);
      File downloadedFile = new File(ARTIFACT_REPO_TMP_DIR, key);
      FileUtils.copyInputStreamToFile(pair.getRight(), downloadedFile);
      log.info("file:[{}] saved in tmp location:[{}]", key, downloadedFile.getAbsolutePath());

      FileUtils.moveFile(downloadedFile, file);
      log.info("file:[{}] moved to  final destination:[{}]", key, file.getAbsolutePath());

      log.info("file:[{}] is ready for read access", key);

      log.info("check if downloaded file [{}] exists locally", key);
      if (!file.isDirectory() && file.exists()) {
        log.info("file[{}] found locally", key);
        return new FileInputStream(file);
      }

      log.error("file[{}] could not be found", key);
      throw new InvalidRequestException("File couldn't be downloaded");
    }
  }

  @Override
  public Long getArtifactFileSize(ArtifactStreamAttributes artifactStreamAttributes) {
    return artifactCollectionTaskHelper.getArtifactFileSize(artifactStreamAttributes);
  }

  @Override
  public String getFileIdByVersion(FileBucket fileBucket, String entityId, int version, String accountId)
      throws IOException {
    return execute(delegateAgentManagerClient.getFileIdByVersion(entityId, fileBucket, version, accountId))
        .getResource();
  }

  @Override
  public InputStream downloadByFileId(FileBucket bucket, String fileId, String accountId) throws IOException {
    Response<ResponseBody> response = null;
    try {
      response = delegateAgentManagerClient.downloadFile(fileId, bucket, accountId).execute();
      if (response.body() == null) {
        return null;
      }
      return response.body().byteStream();
    } finally {
      if (response != null && !response.isSuccessful()) {
        response.errorBody().close();
      }
    }
  }

  @Override
  public InputStream downloadByConfigFileId(String fileId, String accountId, String appId, String activityId)
      throws IOException {
    Response<ResponseBody> response = null;
    try {
      response = delegateAgentManagerClient.downloadFile(fileId, accountId, appId, activityId).execute();
      return response.body().byteStream();
    } finally {
      if (response != null && !response.isSuccessful()) {
        response.errorBody().close();
      }
    }
  }

  @Override
  public DelegateFile getMetaInfo(FileBucket fileBucket, String fileId, String accountId) throws IOException {
    RestResponse<DelegateFile> restResponse =
        execute(delegateAgentManagerClient.getMetaInfo(fileId, fileBucket, accountId));
    if (restResponse == null) {
      log.error("Unknown error occurred while retrieving metainfo for file {} from bucket {}", fileId, fileBucket);
      throw new WingsException(format(
          "Unknown error occurred while retrieving metainfo for file %s from bucket %s. Please check manager logs.",
          fileId, fileBucket));
    }
    log.info("Got info for file {}", fileId);
    return restResponse.getResource();
  }

  @Override
  public void deleteCachedArtifacts() {
    try {
      File[] files = new File(ARTIFACT_REPO_BASE_DIR).listFiles();
      Integer maxCachedArtifacts = delegateConfiguration.getMaxCachedArtifacts() != null
          ? delegateConfiguration.getMaxCachedArtifacts()
          : DEFAULT_MAX_CACHED_ARTIFACT;
      log.info("Max Cached Artifacts from Delegate Configuration: " + delegateConfiguration.getMaxCachedArtifacts());
      log.info("Max Cached Artifacts set to: " + maxCachedArtifacts);
      maxCachedArtifacts += 1; // adjustment for internal 'temp' directory
      if (files != null && files.length > maxCachedArtifacts) {
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (int idx = 0; idx < files.length - maxCachedArtifacts; idx++) {
          File file = files[idx];
          synchronized (fileIdLocks.get(file.getName())) {
            if (file.exists() && !file.isDirectory()) {
              boolean deleted = file.delete();
              if (deleted) {
                log.info("Successfully deleted Artifact file: [{}]", file.getName());
              }
            }
          }
        }
      }
    } catch (Exception ex) {
      log.error("Error in deleting cached artifact file", ex);
    }
  }

  private void upload(DelegateFile delegateFile, File content) throws IOException {
    log.info("Uploading file name {} ", delegateFile.getLocalFilePath());
    // create RequestBody instance from file
    RequestBody requestFile = RequestBody.create(MediaType.parse(MULTIPART_FORM_DATA), content);

    // MultipartBody.Part is used to send also the actual file name
    Part part = Part.createFormData("file", delegateFile.getFileName(), requestFile);
    Response<RestResponse<String>> response = delegateAgentManagerClient
                                                  .uploadFile(delegateFile.getDelegateId(), delegateFile.getTaskId(),
                                                      delegateFile.getAccountId(), delegateFile.getBucket(), part)
                                                  .execute();
    delegateFile.setFileId(response.body().getResource());
    log.info("Uploaded delegate file id {} ", delegateFile.getFileId());
  }

  @Override
  public DelegateFile upload(DelegateFile delegateFile, InputStream contentSource) {
    File file = new File(delegateConfiguration.getLocalDiskPath(), generateUuid());
    log.info("File local name {} for delegate file created", file.getName());
    try {
      FileOutputStream fout = new FileOutputStream(file);
      IOUtils.copy(contentSource, fout);
      fout.close();
      upload(delegateFile, file);
      log.info("File name {} with file id {} uploaded successfully", file.getName(), delegateFile.getFileId());
    } catch (Exception e) {
      log.warn("Error uploading file: " + file.getName(), e);
    } finally {
      try {
        if (!file.delete()) {
          log.warn("Could not delete file: {}", file.getName());
        }
      } catch (Exception e) {
        log.warn("Error deleting file: " + file.getName(), e);
      }
    }

    return delegateFile;
  }

  @Override
  public DelegateFile uploadAsFile(DelegateFile delegateFile, File file) {
    log.info("File local name {} for delegate file created", file.getName());
    try {
      upload(delegateFile, file);
      log.info("File name {} with file id {} uploaded successfully", file.getName(), delegateFile.getFileId());
    } catch (Exception e) {
      log.warn("Error uploading file: " + file.getName(), e);
    }
    return delegateFile;
  }
}
