/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.git;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eraro.ErrorCode.FAILED_TO_ACQUIRE_NON_PERSISTENT_LOCK;
import static io.harness.eraro.ErrorCode.GIT_CONNECTION_ERROR;
import static io.harness.exception.WingsException.ADMIN_SRE;
import static io.harness.exception.WingsException.NOBODY;
import static io.harness.exception.WingsException.SRE;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_ADMIN;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.git.Constants.GIT_DEFAULT_LOG_PREFIX;
import static io.harness.git.Constants.GIT_HELM_LOG_PREFIX;
import static io.harness.git.Constants.GIT_REPO_BASE_DIR;
import static io.harness.git.Constants.GIT_TERRAFORM_LOG_PREFIX;
import static io.harness.git.Constants.GIT_TERRAGRUNT_LOG_PREFIX;
import static io.harness.git.Constants.GIT_TRIGGER_LOG_PREFIX;
import static io.harness.git.Constants.GIT_YAML_LOG_PREFIX;
import static io.harness.git.Constants.REPOSITORY;
import static io.harness.git.Constants.REPOSITORY_GIT_FILE_DOWNLOADS;
import static io.harness.git.Constants.REPOSITORY_GIT_FILE_DOWNLOADS_ACCOUNT;
import static io.harness.git.Constants.REPOSITORY_GIT_FILE_DOWNLOADS_BASE;
import static io.harness.git.Constants.REPOSITORY_GIT_FILE_DOWNLOADS_REPO_BASE_DIR;
import static io.harness.git.Constants.REPOSITORY_GIT_FILE_DOWNLOADS_REPO_DIR;
import static io.harness.git.Constants.REPOSITORY_GIT_LOCK_DIR;
import static io.harness.git.Constants.REPOSITORY_GIT_LOCK_FIlE;
import static io.harness.git.model.ChangeType.ADD;
import static io.harness.git.model.ChangeType.DELETE;
import static io.harness.git.model.ChangeType.MODIFY;
import static io.harness.git.model.ChangeType.RENAME;
import static io.harness.govern.Switch.unhandled;

import static java.lang.String.format;
import static org.apache.commons.codec.binary.Hex.encodeHexString;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GitClientException;
import io.harness.exception.GitConnectionDelegateException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NonPersistentLockException;
import io.harness.exception.YamlException;
import io.harness.filesystem.FileIo;
import io.harness.git.model.ChangeType;
import io.harness.git.model.GitBaseRequest;
import io.harness.git.model.GitFile;
import io.harness.git.model.GitRepositoryType;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
public class GitClientHelper {
  private static final String GIT_URL_REGEX =
      "(http|https|git|ssh)(:\\/\\/|@)([^\\/:]+(:\\d+)?)[\\/:](v\\d\\/)?([^\\/:]+)\\/(.+)?(.git)?";
  private static final String GIT_URL_REGEX_NO_OWNER = "(http|https|git|ssh)(:\\/\\/|@)([^\\/:]+(:\\d+)?)";
  private static final Pattern GIT_URL = Pattern.compile(GIT_URL_REGEX);
  private static final Pattern GIT_URL_NO_OWNER = Pattern.compile(GIT_URL_REGEX_NO_OWNER);
  private static final Integer OWNER_GROUP = 6;
  private static final Integer REPO_GROUP = 7;
  private static final Integer SCM_GROUP = 3;
  private static final Integer PROTOCOL_GROUP = 1;
  private static final String DOT_SEPARATOR = ".";
  private static final String PATH_SEPARATOR = "/";
  private static final String COLON_SEPARATOR = ":";
  private static final String AZURE_REPO_GIT_LABEL = "/_git/";
  private static final String AZURE_SSH_PROTOCOl = "git@ssh";
  private static final String AZURE_SSH_API_VERSION = "v3";
  private static final String HTTPS = "https";
  private static final String BITBUCKET_SAAS_GIT_LABEL = "scm";

  private static final String AZURE_OLD_REPO_PREFIX = ".visualstudio.com";

  private static final String AZURE_NEW_REPO_PREFIX_HTTP = "https://dev.azure.com/";

  private static final String AZURE_NEW_REPO_PREFIX_SSH = "git@ssh.dev.azure.com:v3/";

  static {
    try {
      createDirectoryIfDoesNotExist(REPOSITORY_GIT_LOCK_DIR);
    } catch (IOException e) {
      log.error("Error occurred while creating the lock directory", e);
    }
  }

  private static final LoadingCache<String, File> cache = CacheBuilder.newBuilder()
                                                              .maximumSize(2000)
                                                              .expireAfterAccess(1, TimeUnit.HOURS)
                                                              .build(new CacheLoader<String, File>() {
                                                                @Override
                                                                public File load(String key) throws IOException {
                                                                  File file =
                                                                      new File(format(REPOSITORY_GIT_LOCK_FIlE, key));
                                                                  file.createNewFile();
                                                                  return file;
                                                                }
                                                              });

  public static String getGitRepo(String url) {
    Matcher m = GIT_URL.matcher(url);
    try {
      if (m.find()) {
        String repoName = m.toMatchResult().group(REPO_GROUP);
        repoName = StringUtils.removeEnd(repoName, "/");
        repoName = StringUtils.removeEnd(repoName, ".git");
        return StringUtils.removeStart(repoName, "/");
      } else {
        throw new GitClientException(format("Invalid git repo url  %s", url), SRE);
      }

    } catch (Exception e) {
      throw new GitClientException(format("Failed to parse repo from git url  %s", url), SRE, e);
    }
  }

  public static String getGitProtocol(String url) {
    Matcher m = GIT_URL.matcher(url);
    try {
      if (m.find()) {
        return m.toMatchResult().group(PROTOCOL_GROUP);
      } else {
        throw new GitClientException(format("Invalid git repo url  %s", url), SRE);
      }
    } catch (Exception e) {
      throw new GitClientException(format("Failed to parse protocol from git url  %s", url), SRE, e);
    }
  }

  public static String getGitOwner(String url, boolean isAccountLevelConnector) {
    if (!url.endsWith("/") && isAccountLevelConnector) {
      url += "/";
    }

    Matcher m = GIT_URL.matcher(url);
    try {
      if (m.find()) {
        String ownerName = m.toMatchResult().group(OWNER_GROUP);
        ownerName = StringUtils.removeEnd(ownerName, "/");
        ownerName = StringUtils.removeEnd(ownerName, ".git");
        return StringUtils.removeStart(ownerName, "/");
      } else {
        Matcher noOwnerMatcher = GIT_URL_NO_OWNER.matcher(url);
        if (isAccountLevelConnector && noOwnerMatcher.find()) {
          return null;
        }

        throw new GitClientException(format("Invalid git repo url  %s", url), SRE);
      }

    } catch (Exception e) {
      throw new GitClientException(format("Failed to parse owner from git url  %s", url), SRE);
    }
  }

  public static boolean isHTTPProtocol(String url) {
    String protocol = getGitProtocol(url);
    return protocol.equals("http") || protocol.equals("https");
  }

  public static boolean isSSHProtocol(String url) {
    String protocol = getGitProtocol(url);
    return protocol.equals("git") || protocol.equals("ssh");
  }

  public static boolean isGithubSAAS(String url) {
    String host = getGitSCM(url);
    return host.equals("github.com") || host.equals("www.github.com");
  }

  public static boolean isGitlabSAAS(String url) {
    String host = getGitSCM(url);
    return host.equals("gitlab.com") || host.equals("www.gitlab.com");
  }

  public static boolean isBitBucketSAAS(String url) {
    String host = getGitSCM(url);
    return host.equals("bitbucket.org") || host.equals("www.bitbucket.org") || host.equals("api.bitbucket.org");
  }

  public static boolean isAzureRepoSAAS(String url) {
    String host = getGitSCM(url);
    return host.equals("dev.azure.com") || host.equals("www.dev.azure.com");
  }

  public static String getGithubApiURL(String url) {
    if (GitClientHelper.isGithubSAAS(url)) {
      return "https://api.github.com/";
    } else {
      String domain = GitClientHelper.getGitSCM(url);
      return getHttpProtocolPrefix(url) + domain + "/api/v3/";
    }
  }

  public static String getHarnessApiURL(String url) {
    String domain = GitClientHelper.getGitSCM(url);
    return getHttpProtocolPrefix(url) + domain;
  }

  private static boolean isUrlHTTP(String url) {
    return url.startsWith("http") && !url.startsWith("https");
  }

  public static String getHttpProtocolPrefix(String url) {
    if (isUrlHTTP(url)) {
      return "http://";
    }
    return "https://";
  }

  public static String getGitlabApiURL(String url, String apiUrl) {
    if (!StringUtils.isBlank(apiUrl)) {
      return StringUtils.stripEnd(apiUrl, "/") + "/";
    }
    if (GitClientHelper.isGitlabSAAS(url)) {
      return "https://gitlab.com/";
    } else {
      String domain = GitClientHelper.getGitSCM(url);
      return getHttpProtocolPrefix(url) + domain + "/";
    }
  }

  public static String getBitBucketApiURL(String url) {
    if (isBitBucketSAAS(url)) {
      return "https://api.bitbucket.org/";
    } else {
      String domain = GitClientHelper.getGitSCM(url);
      domain = fetchCustomBitbucketDomain(url, domain);
      return getHttpProtocolPrefix(url) + domain + "/";
    }
  }

  private static String fetchCustomBitbucketDomain(String url, String domain) {
    final String SCM_SPLITTER = "/scm";
    String[] splits = url.split(domain);
    if (splits.length <= 1) {
      // URL only contains the domain
      return domain;
    }

    String scmString = splits[1];
    if (!scmString.contains(SCM_SPLITTER)) {
      // Remaining URL does not contain the custom splitter string
      // Fallback to the original domain
      return domain;
    }

    String[] endpointSplits = scmString.split(SCM_SPLITTER);
    if (endpointSplits.length == 0) {
      // URL does not have anything after the splitter
      // as well as between domain and splitter
      return domain;
    }

    String customEndpoint = endpointSplits[0];
    return domain + customEndpoint;
  }

  public static String getAzureRepoApiURL(String url) {
    if (isAzureRepoSAAS(url)) {
      return "https://dev.azure.com/";
    } else {
      String domain = GitClientHelper.getGitSCM(url);
      return getHttpProtocolPrefix(url) + domain + "/";
    }
  }

  public static String getAzureRepoOrgAndProjectHTTP(String url) {
    String temp = StringUtils.substringBeforeLast(url, "/_git/");
    return StringUtils.substringAfter(temp, ".com/");
  }

  public static String getAzureRepoOrg(String orgAndProject) {
    return StringUtils.substringBefore(orgAndProject, "/");
  }

  public static String getAzureRepoProject(String orgAndProject) {
    return StringUtils.substringAfter(orgAndProject, "/");
  }

  public static String getAzureRepoOrgAndProjectSSH(String url) {
    String temp = StringUtils.substringAfter(url, "/");
    if (temp.split("/").length > 2) {
      return StringUtils.substringBeforeLast(temp, "/");
    }
    return temp;
  }

  private static String getGitSCMHost(String url) {
    Matcher m = GIT_URL_NO_OWNER.matcher(url);
    try {
      if (m.find()) {
        return m.toMatchResult().group(SCM_GROUP);
      } else {
        throw new GitClientException(format("Invalid git repo url  %s", url), SRE);
      }

    } catch (Exception e) {
      throw new GitClientException(format("Failed to parse repo from git url  %s", url), SRE);
    }
  }

  public static String getGitSCM(String url) {
    String host = getGitSCMHost(url);
    // Process the case where both ssh and @ exist in url
    String[] hostParts = host.split("@");
    if (hostParts.length > 1) {
      // take last one
      return hostParts[hostParts.length - 1].split(":")[0];
    }
    return hostParts[0].split(":")[0];
  }

  // Returns port on which git SCM is running. Returns null if port is not present in the url.
  public static String getGitSCMPort(String url) {
    String host = getGitSCMHost(url);
    String[] hostParts = host.split(":");
    if (hostParts.length == 2) {
      return hostParts[1];
    } else {
      return null;
    }
  }

  String getGitLogMessagePrefix(GitRepositoryType repositoryType) {
    if (repositoryType == null) {
      return GIT_DEFAULT_LOG_PREFIX;
    }

    switch (repositoryType) {
      case YAML:
        return GIT_YAML_LOG_PREFIX;

      case TERRAFORM:
        return GIT_TERRAFORM_LOG_PREFIX;

      case TERRAGRUNT:
        return GIT_TERRAGRUNT_LOG_PREFIX;

      case TRIGGER:
        return GIT_TRIGGER_LOG_PREFIX;

      case HELM:
        return GIT_HELM_LOG_PREFIX;

      default:
        unhandled(repositoryType);
        return GIT_DEFAULT_LOG_PREFIX;
    }
  }

  File getLockObject(String id) {
    try {
      return cache.get(id);
    } catch (Exception e) {
      throw new NonPersistentLockException(
          format("Failed to acquire distributed lock for %s", id), FAILED_TO_ACQUIRE_NON_PERSISTENT_LOCK, NOBODY);
    }
  }

  public String getRepoDirectory(GitBaseRequest request) {
    String repoName = getRepoName(request.getRepoUrl());
    String repoUrlHash = getRepoUrlHash(request.getRepoUrl());
    return buildGitRepoBaseDir(
        request.getAccountId(), request.getConnectorId(), repoName, repoUrlHash, request.getRepoType());
  }

  String getFileDownloadRepoDirectory(GitBaseRequest request) {
    String repoName = getRepoName(request.getRepoUrl());
    String repoUrlHash = getRepoUrlHash(request.getRepoUrl());
    return buildGitFileDownloadsRepoDir(request.getAccountId(), request.getConnectorId(), repoName, repoUrlHash);
  }

  void createDirStructureForFileDownload(GitBaseRequest request) {
    try {
      createDirectoryIfDoesNotExist(REPOSITORY);
      createDirectoryIfDoesNotExist(REPOSITORY_GIT_FILE_DOWNLOADS);

      createDirectoryIfDoesNotExist(
          REPOSITORY_GIT_FILE_DOWNLOADS_ACCOUNT.replace("{ACCOUNT_ID}", request.getAccountId()));

      createDirectoryIfDoesNotExist(REPOSITORY_GIT_FILE_DOWNLOADS_BASE.replace("{ACCOUNT_ID}", request.getAccountId())
                                        .replace("{CONNECTOR_ID}", request.getConnectorId()));

      createDirectoryIfDoesNotExist(
          REPOSITORY_GIT_FILE_DOWNLOADS_REPO_BASE_DIR.replace("{ACCOUNT_ID}", request.getAccountId())
              .replace("{CONNECTOR_ID}", request.getConnectorId())
              .replace("{REPO_NAME}", getRepoName(request.getRepoUrl())));

      createDirectoryIfDoesNotExist(
          REPOSITORY_GIT_FILE_DOWNLOADS_REPO_DIR.replace("{ACCOUNT_ID}", request.getAccountId())
              .replace("{CONNECTOR_ID}", request.getConnectorId())
              .replace("{REPO_NAME}", getRepoName(request.getRepoUrl()))
              .replace("{REPO_URL_HASH}", getRepoUrlHash(request.getRepoUrl())));

    } catch (IOException e) {
      log.error("Failed to created required dir structure for gitFileDownloads", e);
      throw new GitClientException("Failed to created required dir structure for gitFileDownloads", SRE, e);
    }
  }

  void addFiles(List<GitFile> gitFiles, Path path, String repoPath) {
    if (gitFiles == null || path == null) {
      throw new GitClientException("GitFiles arg is null, will cause NPE", SRE);
    }

    StringBuilder contentBuilder = new StringBuilder();
    try (Stream<String> stream = Files.lines(path, StandardCharsets.UTF_8)) {
      stream.forEach(s -> contentBuilder.append(s).append("\n"));
    } catch (IOException e) {
      log.error("Failed to read file Content {}", path.toString());
      throw new GitClientException("Failed to read file Content {}", SRE, e);
    }

    String filePath = getFilePath(path, repoPath);

    gitFiles.add(GitFile.builder().filePath(filePath).fileContent(contentBuilder.toString()).build());
  }

  private String getFilePath(Path path, String repoPath) {
    Path fileAbsolutePath = path.toAbsolutePath();
    Path repoAbsolutePath = Paths.get(repoPath).toAbsolutePath();
    return repoAbsolutePath.relativize(fileAbsolutePath).toString();
  }

  synchronized void releaseLock(GitBaseRequest request, String repoDirectory) {
    try {
      File repoDir = new File(repoDirectory);
      File file = new File(repoDir.getAbsolutePath() + "/.git/index.lock");
      FileIo.deleteFileIfExists(file.getAbsolutePath());
    } catch (Exception e) {
      log.error(new StringBuilder(64)
                    .append("Failed to delete index.lock file for account: ")
                    .append(request.getAccountId())
                    .append(", Repo URL: ")
                    .append(request.getRepoUrl())
                    .append(", Branch: ")
                    .append(request.getBranch())
                    .toString());

      throw new GitClientException("GIT_SYNC_ISSUE: Failed to delete index.lock file", SRE, e);
    }
  }

  void checkIfGitConnectivityIssue(Exception ex) {
    // These are the common error we find while delegate runs git command
    // TransportException is subclass of GitAPIException. This is thrown when there is any issue in connecting to git
    // repo, like invalid authorization and invalid repo
    // RefNotFound is a subclass of GitAPIException, thrown when there's an invalid reference.

    // MissingObjectException is caused when some object(commit/ref) is missing in the git history
    if ((ex instanceof GitAPIException && ex.getCause() instanceof TransportException)
        || ex instanceof JGitInternalException || ex instanceof MissingObjectException
        || ex instanceof RefNotFoundException) {
      throw new GitConnectionDelegateException(GIT_CONNECTION_ERROR, ex.getCause() == null ? ex : ex.getCause(),
          ExceptionSanitizer.sanitizeTheMessage(ex.getMessage()), USER_ADMIN);
    }
  }

  public void checkIfMissingCommitIdIssue(Exception ex, String commitId) {
    if ((ex instanceof JGitInternalException && ex.getCause() instanceof MissingObjectException)
        || ex instanceof MissingObjectException) {
      throw new GitClientException(
          format("Unable to find any references with commit id: %s. Check provided value for commit id", commitId),
          USER);
    }
  }

  private String buildGitFileDownloadsRepoDir(
      String accountId, String connectorId, String repoName, String repoUrlHash) {
    return REPOSITORY_GIT_FILE_DOWNLOADS_REPO_DIR.replace("{ACCOUNT_ID}", accountId)
        .replace("{CONNECTOR_ID}", connectorId)
        .replace("{REPO_NAME}", repoName)
        .replace("{REPO_URL_HASH}", repoUrlHash);
  }

  private String buildGitRepoBaseDir(
      String accountId, String connectorId, String repoName, String repoUrlHash, GitRepositoryType repoType) {
    return GIT_REPO_BASE_DIR.replace("${ACCOUNT_ID}", accountId)
        .replace("${REPO_TYPE}", repoType.name().toLowerCase())
        .replace("${CONNECTOR_ID}", connectorId)
        .replace("${REPO_NAME}", repoName)
        .replace("${REPO_URL_HASH}", repoUrlHash);
  }

  private String getRepoName(String repoUrl) {
    // TODO:: support more url types and validation);
    return repoUrl.substring(repoUrl.lastIndexOf('/') + 1).split("\\.")[0];
  }

  private String getRepoUrlHash(String repoUrl) {
    return calculateHash(repoUrl);
  }

  private String calculateHash(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] messageDigest = md.digest(input.getBytes());
      return encodeHexString(messageDigest);
    } catch (Exception e) {
      throw new YamlException(format("Error while calculating hash for input [%s].", input), e, ADMIN_SRE);
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

  public static void validateURL(@NotNull String url) {
    Matcher m = GIT_URL_NO_OWNER.matcher(url);
    log.info("url==" + url);

    checkInvalidCharacters(url.trim());
    if (!(m.find())) {
      throw new InvalidRequestException(
          format("Invalid repo url  %s,should start with either http:// , https:// , ssh:// or git@", url));
    }
    if (url.startsWith("git@") && !url.contains(":")) {
      throw new InvalidRequestException(
          format("Invalid repo url  %s, valid ssh url formats are git@provider.com:username/repo, "
                  + "ssh://provider.com/username/repo, "
                  + "ssh://git@provider.com/username/repo",
              url));
    }
  }

  private static void checkInvalidCharacters(String url) {
    if (url.isEmpty()) {
      throw new InvalidRequestException(format("Url cannot be left blank"));
    }
    if (url.trim().contains(" ")) {
      throw new InvalidRequestException(format("Invalid repo url  %s, It should not contain spaces in between", url));
    }
    try {
      URLDecoder.decode(url, StandardCharsets.UTF_8.name());
    } catch (Exception e) {
      throw new InvalidRequestException(format("Url %s is invalid", url));
    }
  }

  public static String getCompleteUrlForProjectLevelAzureConnector(String url, String repoName) {
    String azureCompleteUrl = StringUtils.join(StringUtils.stripEnd(url, PATH_SEPARATOR));
    if (GitClientHelper.isHTTPProtocol(azureCompleteUrl)) {
      azureCompleteUrl = StringUtils.join(azureCompleteUrl, AZURE_REPO_GIT_LABEL);
    } else if (GitClientHelper.isSSHProtocol(azureCompleteUrl)) {
      azureCompleteUrl = StringUtils.join(azureCompleteUrl, PATH_SEPARATOR);
    }
    return StringUtils.join(azureCompleteUrl, StringUtils.stripStart(repoName, PATH_SEPARATOR));
  }

  public static String getCompleteSSHUrlFromHttpUrlForAzure(String httpUrl) {
    String scmGroup = getGitSCM(httpUrl);
    String gitOwner = getGitOwner(httpUrl, true);
    String gitRepo = getGitRepo(httpUrl);
    String completeUrl = StringUtils.join(AZURE_SSH_PROTOCOl, DOT_SEPARATOR, scmGroup, COLON_SEPARATOR,
        AZURE_SSH_API_VERSION, PATH_SEPARATOR, gitOwner, PATH_SEPARATOR, gitRepo);
    return completeUrl.replaceFirst(AZURE_REPO_GIT_LABEL, PATH_SEPARATOR);
  }

  public static String getCompleteHTTPUrlForGithub(String anyRepoUrl) {
    String scmGroup = getGitSCM(anyRepoUrl);
    String gitOwner = getGitOwner(anyRepoUrl, true);
    String gitRepo = getGitRepo(anyRepoUrl);
    return StringUtils.join(HTTPS, COLON_SEPARATOR, PATH_SEPARATOR, PATH_SEPARATOR, scmGroup, PATH_SEPARATOR, gitOwner,
        PATH_SEPARATOR, gitRepo);
  }

  public static String getCompleteHTTPUrlForBitbucketSaas(String anyRepoUrl) {
    String scmGroup = getGitSCM(anyRepoUrl);
    String gitOwner = getGitOwner(anyRepoUrl, true);
    String gitRepo = getGitRepo(anyRepoUrl);
    return StringUtils.join(HTTPS, COLON_SEPARATOR, PATH_SEPARATOR, PATH_SEPARATOR, scmGroup, PATH_SEPARATOR, gitOwner,
        PATH_SEPARATOR, gitRepo);
  }

  public static String getCompleteHTTPUrlFromSSHUrlForBitbucketServer(String sshRepoUrl) {
    String scmGroup = getGitSCM(sshRepoUrl);
    String gitOwner = getGitOwner(sshRepoUrl, true);
    String gitRepo = getGitRepo(sshRepoUrl);
    return StringUtils.join(HTTPS, COLON_SEPARATOR, PATH_SEPARATOR, PATH_SEPARATOR, scmGroup, PATH_SEPARATOR,
        BITBUCKET_SAAS_GIT_LABEL, PATH_SEPARATOR, gitOwner, PATH_SEPARATOR, gitRepo);
  }

  public static String getCompleteHTTPUrlForGitLab(String anyRepoUrl) {
    String scmGroup = getGitSCM(anyRepoUrl);
    String gitOwner = getGitOwner(anyRepoUrl, true);
    String gitRepo = getGitRepo(anyRepoUrl);
    return StringUtils.join(HTTPS, COLON_SEPARATOR, PATH_SEPARATOR, PATH_SEPARATOR, scmGroup, PATH_SEPARATOR, gitOwner,
        PATH_SEPARATOR, gitRepo);
  }

  public static String getCompleteHTTPRepoUrlForAzureRepoSaas(String anyRepoUrl) {
    final String AZURE_REPO_URL = "https://dev.azure.com";
    String gitOwner = getGitOwner(anyRepoUrl, true);
    String gitRepoProject = getGitRepo(anyRepoUrl);
    List<String> parts = Arrays.asList(gitRepoProject.split("/"));
    if (parts.size() < 2) {
      // as gitRepoProject should contain repo and project, there must be atleast two parts
      throw new InvalidRequestException(String.format("Invalid Azure repoUrl [%s]", anyRepoUrl));
    }
    String gitRepo = parts.get(parts.size() - 1);
    String gitProject = parts.get(0);
    return StringUtils.join(
        AZURE_REPO_URL, PATH_SEPARATOR, gitOwner, PATH_SEPARATOR, gitProject, AZURE_REPO_GIT_LABEL, gitRepo);
  }

  public static String convertToNewHTTPUrlForAzure(String httpURL) {
    // Convert Old Azure URLs to new URLs if any
    if (httpURL.contains(AZURE_OLD_REPO_PREFIX)) {
      return AZURE_NEW_REPO_PREFIX_HTTP
          + StringUtils.substringBetween(httpURL, HTTPS + "://", AZURE_OLD_REPO_PREFIX + "/")
          + StringUtils.substringAfter(httpURL, AZURE_OLD_REPO_PREFIX);
    }

    return httpURL;
  }

  public static String convertToNewSSHUrlForAzure(String sshURL) {
    // Convert Old Azure URLs to new URLs if any
    if (sshURL.contains(AZURE_OLD_REPO_PREFIX)) {
      return AZURE_NEW_REPO_PREFIX_SSH
          + StringUtils.substringBetween(sshURL, AZURE_SSH_PROTOCOl + ".", AZURE_OLD_REPO_PREFIX)
          + StringUtils.substringAfter(sshURL, AZURE_OLD_REPO_PREFIX + ":v3");
    }
    return sshURL;
  }

  public static String convertToHttps(String url) {
    return url.replace("http://", "https://");
  }

  public static String getHarnessRepoName(String url) {
    String repoName = getGitRepo(url);
    String ownerName = getGitOwner(url, true);
    String s = ownerName + "/" + repoName;
    String gitnessGitApiPrefix = "code/git/";
    String gitnessUiApiPrefix = "ng/account/";
    String gitApiPrefix = "git/";

    if (s.startsWith(gitnessGitApiPrefix)) {
      return s.substring(9) + "/+";
    } else if (s.startsWith(gitnessUiApiPrefix)) {
      s = s.substring(11);
      String[] split = s.split("/");
      StringBuilder finalUrl = new StringBuilder();
      if ("code".equals(split[1])) {
        for (int i = 0; i < split.length; i++) {
          if (i == 1) {
            continue;
          }
          String part = split[i] + "/";
          finalUrl.append(part);
        }
        return String.valueOf(finalUrl.append("+"));
      }
      return s.substring(11);
    } else if (s.startsWith(gitApiPrefix)) {
      return s.substring(4) + "/+";
    }
    return s + "/+";
  }
}
