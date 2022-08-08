/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.artifact.ArtifactConstants.ARTIFACT_FILE_SIZE_LIMIT;
import static io.harness.artifact.ArtifactConstants.ARTIFACT_REPO_BASE_DIR;
import static io.harness.artifact.ArtifactConstants.ARTIFACT_REPO_TMP_DIR;
import static io.harness.artifact.ArtifactConstants.DEFAULT_MAX_CACHED_ARTIFACT;
import static io.harness.logging.LogLevel.ERROR;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.delegate.task.ssh.exception.SshExceptionConstants;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.SshCommandExecutionException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public abstract class ArtifactCommandUnitHandler {
  protected static final LoadingCache<String, Object> fileIdLocks =
      CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.HOURS).build(CacheLoader.from(Object::new));

  public ArtifactCommandUnitHandler() {
    Executors
        .newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("artifact-command-unit-cleanup").build())
        .scheduleAtFixedRate(this::deleteCachedArtifacts, 1000, 5 * 60 * (long) 1000,
            TimeUnit.MILLISECONDS); // periodic cleanup for cached artifacts
  }

  protected abstract InputStream downloadFromRemoteRepo(SshExecutorFactoryContext context, LogCallback logCallback)
      throws IOException;

  public abstract Long getArtifactSize(SshExecutorFactoryContext context, LogCallback logCallback);

  public InputStream downloadToLocal(SshExecutorFactoryContext context, LogCallback logCallback)
      throws IOException, ExecutionException {
    Map<String, String> metadata = context.getArtifactMetadata();
    SshWinRmArtifactDelegateConfig artifactDelegateConfig = context.getArtifactDelegateConfig();
    String artifactFileSize = metadata.get(ArtifactMetadataKeys.artifactFileSize);
    if (null != artifactFileSize && Long.parseLong(artifactFileSize) > ARTIFACT_FILE_SIZE_LIMIT) {
      throw NestedExceptionUtils.hintWithExplanationException(SshExceptionConstants.ARTIFACT_SIZE_EXCEEDED_HINT,
          format(SshExceptionConstants.ARTIFACT_SIZE_EXCEEDED_EXPLANATION, artifactDelegateConfig.getIdentifier(),
              artifactFileSize),
          new SshCommandExecutionException(SshExceptionConstants.ARTIFACT_SIZE_EXCEEDED));
    }

    String key = "_" + artifactDelegateConfig.getIdentifier() + "-" + metadata.get(ArtifactMetadataKeys.artifactName);
    synchronized (fileIdLocks.get(key)) {
      File file = new File(ARTIFACT_REPO_BASE_DIR, key);
      log.info("check if artifact:[{}] exists at location: [{}]", key, file.getAbsolutePath());
      if (!file.isDirectory() && file.exists()) {
        log.info("artifact:[{}] found locally", key);
        logCallback.saveExecutionLog(format("artifact: [%s] found locally", key));
        return new FileInputStream(file);
      }
      log.info("file:[{}] doesn't exist locally. Downloading from artifactory", key);

      InputStream artifact = downloadFromRemoteRepo(context, logCallback);
      if (artifact == null) {
        logCallback.saveExecutionLog(
            "Failure in downloading artifact from artifactory", ERROR, CommandExecutionStatus.FAILURE);
        throw NestedExceptionUtils.hintWithExplanationException(
            format(SshExceptionConstants.ARTIFACT_DOWNLOAD_HINT, artifactDelegateConfig.getArtifactType()),
            format(SshExceptionConstants.ARTIFACT_DOWNLOAD_EXPLANATION, artifactDelegateConfig.getIdentifier(),
                artifactDelegateConfig.getArtifactType()),
            new SshCommandExecutionException(
                format(SshExceptionConstants.ARTIFACT_DOWNLOAD_FAILED, artifactDelegateConfig.getArtifactType())));
      }

      log.info("Input stream acquired for file:[{}]. Saving locally", key);
      File downloadedFile = new File(ARTIFACT_REPO_TMP_DIR, key);
      FileUtils.copyInputStreamToFile(artifact, downloadedFile);
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
      throw NestedExceptionUtils.hintWithExplanationException(
          format(SshExceptionConstants.ARTIFACT_NOT_FOUND_HINT, key),
          format(SshExceptionConstants.ARTIFACT_NOT_FOUND_EXPLANATION, key),
          new SshCommandExecutionException(format(SshExceptionConstants.ARTIFACT_NOT_FOUND, key)));
    }
  }

  public void deleteCachedArtifacts() {
    try {
      File[] files = new File(ARTIFACT_REPO_BASE_DIR).listFiles();
      Integer maxCachedArtifacts = DEFAULT_MAX_CACHED_ARTIFACT;
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
}
