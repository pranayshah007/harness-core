/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.eraro.ErrorCode.GENERAL_YAML_ERROR;
import static io.harness.eraro.ErrorCode.GIT_CONNECTION_ERROR;
import static io.harness.exception.WingsException.ADMIN_SRE;
import static io.harness.exception.WingsException.SRE;
import static io.harness.exception.WingsException.USER_ADMIN;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.git.model.ChangeType.ADD;
import static io.harness.git.model.ChangeType.DELETE;
import static io.harness.git.model.ChangeType.MODIFY;
import static io.harness.git.model.ChangeType.RENAME;
import static io.harness.govern.Switch.unhandled;

import static java.lang.String.format;
import static org.apache.commons.codec.binary.Hex.encodeHexString;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ErrorCode;
import io.harness.exception.GitConnectionDelegateException;
import io.harness.exception.WingsException;
import io.harness.exception.YamlException;
import io.harness.filesystem.FileIo;
import io.harness.git.ExceptionSanitizer;
import io.harness.git.model.ChangeType;
import io.harness.git.model.GitFile;
import io.harness.git.model.GitRepositoryType;

import software.wings.beans.GitConfig;
import software.wings.beans.GitOperationContext;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.TransportException;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CDP)
@Singleton
@Slf4j
@BreakDependencyOn("software.wings.beans.GitConfig")
@TargetModule(HarnessModule._960_API_SERVICES)
public class GitClientHelper {
  public static final String REPOSITORY = "./repository";
  public static final String REPOSITORY_GIT_FILE_DOWNLOADS = "./repository/gitFileDownloads";
  public static final String REPOSITORY_GIT_FILE_DOWNLOADS_ACCOUNT = "./repository/gitFileDownloads/{ACCOUNT_ID}";
  public static final String REPOSITORY_GIT_FILE_DOWNLOADS_BASE =
      "./repository/gitFileDownloads/{ACCOUNT_ID}/{CONNECTOR_ID}";
  public static final String REPOSITORY_GIT_FILE_DOWNLOADS_REPO_BASE_DIR =
      REPOSITORY_GIT_FILE_DOWNLOADS_BASE + "/{REPO_NAME}";
  public static final String REPOSITORY_GIT_FILE_DOWNLOADS_REPO_DIR =
      REPOSITORY_GIT_FILE_DOWNLOADS_REPO_BASE_DIR + "/{REPO_URL_HASH}";
  private static final String GIT_REPO_BASE_DIR =
      "./repository/${REPO_TYPE}/${ACCOUNT_ID}/${CONNECTOR_ID}/${REPO_NAME}/${REPO_URL_HASH}";
  private static final String REPOSITORY_GIT_LOCK_DIR = "./repository/gitFileDownloads/.locks";
  private static final String REPOSITORY_GIT_LOCK_FIlE = REPOSITORY_GIT_LOCK_DIR + "/lock_%s";

  static {
    try {
      createDirectoryIfDoesNotExist(REPOSITORY_GIT_LOCK_DIR);
    } catch (IOException e) {
      log.error("Error occurred while creating the lock directory", e);
    }
  }

  private LoadingCache<String, File> cache = CacheBuilder.newBuilder()
                                                 .maximumSize(2000)
                                                 .expireAfterAccess(1, TimeUnit.HOURS)
                                                 .build(new CacheLoader<String, File>() {
                                                   @Override
                                                   public File load(String key) throws Exception {
                                                     File file = new File(format(REPOSITORY_GIT_LOCK_FIlE, key));
                                                     file.createNewFile();
                                                     return file;
                                                   }
                                                 });

  public File getLockObject(String gitConnectorId) {
    try {
      return cache.get(gitConnectorId);
    } catch (Exception e) {
      throw new WingsException(ErrorCode.GENERAL_ERROR, WingsException.USER);
    }
  }

  public String getFileDownloadRepoDirectory(GitConfig gitConfig, String connectorId) {
    String repoName = getRepoName(gitConfig);
    String repoUrlHash = getRepoUrlHash(gitConfig);
    if (gitConfig.getGitRepoType() == null) {
      log.error("gitRepoType can not be null. defaulting it to YAML");
      gitConfig.setGitRepoType(GitRepositoryType.YAML);
    }
    return buildGitFileDownloadsRepoDir(gitConfig.getAccountId(), connectorId, repoName, repoUrlHash);
  }

  @NotNull
  private String buildGitFileDownloadsRepoDir(
      String accountId, String connectorId, String repoName, String repoUrlHash) {
    return REPOSITORY_GIT_FILE_DOWNLOADS_REPO_DIR.replace("{ACCOUNT_ID}", accountId)
        .replace("{CONNECTOR_ID}", connectorId)
        .replace("{REPO_NAME}", repoName)
        .replace("{REPO_URL_HASH}", repoUrlHash);
  }

  @NotNull
  private String buildGitRepoBaseDir(
      String accountId, String connectorId, String repoName, String repoUrlHash, GitRepositoryType repoType) {
    return GIT_REPO_BASE_DIR.replace("${ACCOUNT_ID}", accountId)
        .replace("${REPO_TYPE}", repoType.name().toLowerCase())
        .replace("${CONNECTOR_ID}", connectorId)
        .replace("${REPO_NAME}", repoName)
        .replace("${REPO_URL_HASH}", repoUrlHash);
  }

  public void createDirStructureForFileDownload(GitConfig gitConfig, String connectorId) {
    try {
      FileIo.createDirectoryIfDoesNotExist(REPOSITORY);
      FileIo.createDirectoryIfDoesNotExist(REPOSITORY_GIT_FILE_DOWNLOADS);

      FileIo.createDirectoryIfDoesNotExist(
          REPOSITORY_GIT_FILE_DOWNLOADS_ACCOUNT.replace("{ACCOUNT_ID}", gitConfig.getAccountId()));

      FileIo.createDirectoryIfDoesNotExist(
          REPOSITORY_GIT_FILE_DOWNLOADS_BASE.replace("{ACCOUNT_ID}", gitConfig.getAccountId())
              .replace("{CONNECTOR_ID}", connectorId));

      FileIo.createDirectoryIfDoesNotExist(
          REPOSITORY_GIT_FILE_DOWNLOADS_REPO_BASE_DIR.replace("{ACCOUNT_ID}", gitConfig.getAccountId())
              .replace("{CONNECTOR_ID}", connectorId)
              .replace("{REPO_NAME}", getRepoName(gitConfig)));

      FileIo.createDirectoryIfDoesNotExist(
          REPOSITORY_GIT_FILE_DOWNLOADS_REPO_DIR.replace("{ACCOUNT_ID}", gitConfig.getAccountId())
              .replace("{CONNECTOR_ID}", connectorId)
              .replace("{REPO_NAME}", getRepoName(gitConfig))
              .replace("{REPO_URL_HASH}", getRepoUrlHash(gitConfig)));

    } catch (IOException e) {
      log.error("Failed to created required dir structure for gitFileDownloads", e);
      throw new WingsException(
          ErrorCode.GENERAL_ERROR, "Failed to created required dir structure for gitFileDownloads", SRE);
    }
  }

  public void addFiles(List<GitFile> gitFiles, Set<String> uniqueFilePaths, Path path, String repoPath) {
    if (gitFiles == null || path == null) {
      throw new WingsException(GENERAL_ERROR, "GitFiles arg is null, will cause NPE", SRE);
    }
    String filePath = getFilePath(path, repoPath);
    if (uniqueFilePaths.contains(filePath)) {
      return;
    }
    StringBuilder contentBuilder = new StringBuilder();
    try (Stream<String> stream = Files.lines(path, StandardCharsets.UTF_8)) {
      stream.forEach(s -> contentBuilder.append(s).append("\n"));
    } catch (IOException e) {
      log.error("Failed to read file Content {}", path.toString());
      throw new WingsException(GENERAL_ERROR, "Failed to read file Content {}", e);
    }
    uniqueFilePaths.add(filePath);
    gitFiles.add(GitFile.builder().filePath(filePath).fileContent(contentBuilder.toString()).build());
  }

  private String getFilePath(Path path, String repoPath) {
    Path fileAbsolutePath = path.toAbsolutePath();
    Path repoAbsolutePath = Paths.get(repoPath).toAbsolutePath();
    return repoAbsolutePath.relativize(fileAbsolutePath).toString();
  }

  public String getRepoPathForFileDownload(GitConfig gitConfig, String gitConnectorId) {
    String repoName = getRepoName(gitConfig);
    String repoUrlHash = getRepoUrlHash(gitConfig);
    return buildGitFileDownloadsRepoDir(gitConfig.getAccountId(), gitConnectorId, repoName, repoUrlHash);
  }

  private String getRepoName(GitConfig gitConfig) {
    return gitConfig.getRepoUrl()
        .substring(gitConfig.getRepoUrl().lastIndexOf('/') + 1) // TODO:: support more url types and validation);
        .split("\\.")[0];
  }

  private String getRepoUrlHash(GitConfig gitConfig) {
    return calculateHash(gitConfig.getRepoUrl());
  }

  public synchronized void releaseLock(GitConfig gitConfig, String repoPath) {
    try {
      File repoDir = new File(repoPath);
      File file = new File(repoDir.getAbsolutePath() + "/.git/index.lock");
      FileIo.deleteFileIfExists(file.getAbsolutePath());
    } catch (Exception e) {
      log.error(new StringBuilder(64)
                    .append("Failed to delete index.lock file for account: ")
                    .append(gitConfig.getAccountId())
                    .append(", Repo URL: ")
                    .append(gitConfig.getRepoUrl())
                    .append(", Branch: ")
                    .append(gitConfig.getBranch())
                    .toString());

      throw new WingsException(GENERAL_YAML_ERROR, "GIT_SYNC_ISSUE: Failed to delete index.lock file", SRE, e);
    }
  }

  public void checkIfGitConnectivityIssue(Exception ex) {
    // These are the common error we find while delegate runs git command
    // TransportException is subclass of GitAPIException. This is thrown when there is any issue in connecting to git
    // repo, like invalid authorization and invalid repo
    // RefNotFound is a subclass of GitAPIException, thrown when there's an invalid reference.

    // MissingObjectException is caused when some object(commit/ref) is missing in the git history
    if ((ex instanceof GitAPIException && ex.getCause() instanceof TransportException)
        || ex instanceof RefNotFoundException || ex instanceof JGitInternalException
        || ex instanceof MissingObjectException) {
      throw new GitConnectionDelegateException(
          GIT_CONNECTION_ERROR, ex, ExceptionSanitizer.sanitizeTheMessage(ex.getMessage()), USER_ADMIN);
    }
  }

  public ChangeType getChangeType(DiffEntry.ChangeType gitDiffChangeType) {
    switch (gitDiffChangeType) {
      case ADD:
        return ADD;
      case MODIFY:
        return MODIFY;
      case DELETE:
        return DELETE;
      case RENAME:
        return RENAME;
      default:
        unhandled(gitDiffChangeType);
    }
    return null;
  }

  public String getRepoDirectory(GitOperationContext gitOperationContext) {
    GitConfig gitConfig = gitOperationContext.getGitConfig();
    String gitConnectorId = gitOperationContext.getGitConnectorId();

    String repoName = getRepoName(gitConfig);
    String repoUrlHash = getRepoUrlHash(gitConfig);

    if (gitConfig.getGitRepoType() == null) {
      log.error("gitRepoType can not be null. defaulting it to YAML");
      gitConfig.setGitRepoType(GitRepositoryType.YAML);
    }
    return buildGitRepoBaseDir(
        gitConfig.getAccountId(), gitConnectorId, repoName, repoUrlHash, gitConfig.getGitRepoType());
  }

  private String calculateHash(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] messageDigest = md.digest(input.getBytes());
      return encodeHexString(messageDigest);
    } catch (Exception e) {
      throw new YamlException(String.format("Error while calculating hash for input [%s].", input), e, ADMIN_SRE);
    }
  }
}
