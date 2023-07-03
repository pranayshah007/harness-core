/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.git;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.git.model.ChangeType.ADD;
import static io.harness.git.model.ChangeType.DELETE;
import static io.harness.git.model.ChangeType.RENAME;
import static io.harness.git.model.PushResultGit.pushResultBuilder;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.SATHISH;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.YOGESH;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Fail.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.YamlException;
import io.harness.exception.runtime.JGitRuntimeException;
import io.harness.git.model.CommitAndPushRequest;
import io.harness.git.model.CommitAndPushResult;
import io.harness.git.model.CommitResult;
import io.harness.git.model.DiffRequest;
import io.harness.git.model.DiffResult;
import io.harness.git.model.DownloadFilesRequest;
import io.harness.git.model.FetchFilesBwCommitsRequest;
import io.harness.git.model.FetchFilesByPathRequest;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitBaseRequest;
import io.harness.git.model.GitFileChange;
import io.harness.git.model.ListRemoteRequest;
import io.harness.git.model.ListRemoteResult;
import io.harness.git.model.PushRequest;
import io.harness.git.model.PushResultGit;
import io.harness.git.model.RevertAndPushRequest;
import io.harness.git.model.RevertAndPushResult;
import io.harness.git.model.RevertRequest;
import io.harness.rule.Owner;

import software.wings.misc.CustomUserGitConfigSystemReader;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.SystemReader;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

@Slf4j
@OwnedBy(CDP)
public class GitClientV2ImplTest extends CategoryTest {
  private static final String USERNAME = "USERNAME";
  private static final String PASSWORD = "PASSWORD";
  private static final String REPO_GIT_DOWNLOAD_DIR = "./repository/gitFileDownloads/.locks";
  private static final String INTER_PROCESS_LOCK_FILE = REPO_GIT_DOWNLOAD_DIR + "/lock_%s";
  private static final String CONNECTOR_ID = "CONNECTOR_ID";

  private static final LoadingCache<String, Object> cache =
      CacheBuilder.newBuilder()
          .maximumSize(2000)
          .expireAfterAccess(1, TimeUnit.HOURS)
          .build(new CacheLoader<String, Object>() {
            @Override
            public Object load(String key) throws Exception {
              return new File(format(INTER_PROCESS_LOCK_FILE, key));
            }
          });
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock GitClientHelper gitClientHelper;
  @InjectMocks @Spy GitClientV2Impl gitClient;
  private Git git;
  private String repoPath;

  @Before
  public void setUp() throws Exception {
    repoPath = Files.createTempDirectory(UUID.randomUUID().toString()).toString();
    createRepo(repoPath, false);
    git = Git.open(new File(repoPath));
    doReturn("").when(gitClientHelper).getGitLogMessagePrefix(any());
    createDirectoryIfDoesNotExist(format(REPO_GIT_DOWNLOAD_DIR, CONNECTOR_ID));
    File indexLockFile = new File(format(INTER_PROCESS_LOCK_FILE, CONNECTOR_ID));
    indexLockFile.createNewFile();
  }

  @AfterClass
  public static void afterClass() throws IOException {
    deleteDirectoryAndItsContentIfExists(format(REPO_GIT_DOWNLOAD_DIR, CONNECTOR_ID));
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testValidate() throws Exception {
    assertThatThrownBy(() -> gitClient.validate(null)).isInstanceOf(GeneralException.class);
    assertThatThrownBy(() -> gitClient.validate(GitBaseRequest.builder().build()))
        .isInstanceOf(InvalidRequestException.class);
    GitBaseRequest request = GitBaseRequest.builder()
                                 .repoUrl(repoPath)
                                 .authRequest(new UsernamePasswordAuthRequest(USERNAME, PASSWORD.toCharArray()))
                                 .build();
    doReturn("").when(gitClientHelper).getGitLogMessagePrefix(any());
    assertThat(gitClient.validate(request)).isNull();

    String randomPath = Files.createTempDirectory(UUID.randomUUID().toString()).toString();
    request.setRepoUrl(randomPath);
    assertThat(gitClient.validate(request)).isNotNull();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testValidate_Exception() throws Exception {
    GitBaseRequest request = GitBaseRequest.builder()
                                 .repoUrl(repoPath)
                                 .authRequest(new UsernamePasswordAuthRequest(USERNAME, PASSWORD.toCharArray()))
                                 .build();
    doReturn("").when(gitClientHelper).getGitLogMessagePrefix(any());
    LsRemoteCommand mockedLsRemoteCommand = Mockito.mock(LsRemoteCommand.class);
    doReturn(mockedLsRemoteCommand).when(gitClient).getAuthConfiguredCommand(any(), any());
    doReturn(mockedLsRemoteCommand).when(mockedLsRemoteCommand).setRemote(repoPath);
    doReturn(mockedLsRemoteCommand).when(mockedLsRemoteCommand).setHeads(true);
    doReturn(mockedLsRemoteCommand).when(mockedLsRemoteCommand).setTags(true);
    TransportException exception1 =
        new TransportException("m1", new TransportException("m2", new UnknownHostException()));
    doThrow(exception1).when(mockedLsRemoteCommand).call();
    assertThat(gitClient.validate(request)).contains("Unreachable hostname");

    TransportException exception2 = new TransportException("m1");
    doThrow(exception2).when(mockedLsRemoteCommand).call();
    assertThat(gitClient.validate(request)).contains("m1");

    TransportException exception3 = new TransportException("m1", new TransportException("m2"));
    doThrow(exception3).when(mockedLsRemoteCommand).call();
    assertThat(gitClient.validate(request)).contains("m1");

    InvalidRemoteException exception4 = new InvalidRemoteException("m3");
    doThrow(exception4).when(mockedLsRemoteCommand).call();
    assertThat(gitClient.validate(request)).contains("Invalid git repo");

    GitAPIException exception5 = new WrongRepositoryStateException("m4");
    doThrow(exception5).when(mockedLsRemoteCommand).call();
    assertThat(gitClient.validate(request)).contains("m4");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testEnsureRepoLocallyClonedAndUpdated() throws Exception {
    String repoPathInternal = Files.createTempDirectory(UUID.randomUUID().toString()).toString();
    createRepo(repoPathInternal, false);
    Git gitInternal = Git.open(new File(repoPathInternal));

    assertThatThrownBy(() -> gitClient.ensureRepoLocallyClonedAndUpdated(null)).isInstanceOf(GeneralException.class);
    GitBaseRequest request = GitBaseRequest.builder()
                                 .repoUrl(repoPathInternal)
                                 .authRequest(new UsernamePasswordAuthRequest(USERNAME, PASSWORD.toCharArray()))
                                 .branch("main")
                                 .build();

    doReturn(repoPathInternal).when(gitClientHelper).getRepoDirectory(request);
    String remoteUrl = addRemote(repoPathInternal);
    assertThat(gitInternal.getRepository().getConfig().getString("remote", "origin", "url")).isEqualTo(remoteUrl);

    assertThatThrownBy(() -> gitClient.ensureRepoLocallyClonedAndUpdated(request))
        .hasMessageContaining("Unable to checkout given reference: main");

    request.setBranch("master");
    request.setRepoUrl(remoteUrl);
    gitClient.ensureRepoLocallyClonedAndUpdated(request);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testEnsureRepoLocallyClonedAndUpdatedForUnsureOrNonExistentBranch() throws Exception {
    String repoPathInternal = Files.createTempDirectory(UUID.randomUUID().toString()).toString();
    createRepo(repoPathInternal, false);
    Git gitInternal = Git.open(new File(repoPathInternal));

    assertThatThrownBy(() -> gitClient.ensureRepoLocallyClonedAndUpdated(null)).isInstanceOf(GeneralException.class);
    GitBaseRequest request = GitBaseRequest.builder()
                                 .repoUrl(repoPathInternal)
                                 .authRequest(new UsernamePasswordAuthRequest(USERNAME, PASSWORD.toCharArray()))
                                 .branch("main")
                                 .build();

    doReturn(repoPathInternal).when(gitClientHelper).getRepoDirectory(request);
    String remoteUrl = addRemote(repoPathInternal);
    assertThat(gitInternal.getRepository().getConfig().getString("remote", "origin", "url")).isEqualTo(remoteUrl);

    assertThatThrownBy(() -> gitClient.ensureRepoLocallyClonedAndUpdated(request))
        .hasMessageContaining("Unable to checkout given reference: main");

    request.setUnsureOrNonExistentBranch(true);
    request.setBranch("master-test");
    request.setRepoUrl(remoteUrl);
    gitClient.ensureRepoLocallyClonedAndUpdated(request);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testEnsureRepoLocallyClonedAndUpdatedWithGitTag() throws IOException {
    String remoteRepo = addRemote(repoPath);
    GitBaseRequest request = GitBaseRequest.builder()
                                 .repoUrl(remoteRepo)
                                 .authRequest(new UsernamePasswordAuthRequest(USERNAME, PASSWORD.toCharArray()))
                                 .branch("master")
                                 .build();
    doReturn(repoPath).when(gitClientHelper).getRepoDirectory(request);
    gitClient.ensureRepoLocallyClonedAndUpdated(request);

    String workRepo = Files.createTempDirectory(UUID.randomUUID().toString()).toString();
    String tag = "hello-tag";
    String command = new StringBuilder(128)
                         .append("git clone " + remoteRepo + " ")
                         .append(workRepo)
                         .append(";")
                         .append("cd " + workRepo + ";")
                         .append("touch ")
                         .append(tag)
                         .append(";")
                         .append("git add ")
                         .append(tag)
                         .append(";")
                         .append("git commit -m 'commit base 2';")
                         .append("git tag ")
                         .append(tag)
                         .append(";")
                         .append("git push origin ")
                         .append(tag)
                         .append(";")
                         .append("git remote update;")
                         .append("git fetch;")
                         .toString();
    executeCommand(command);

    request.setBranch(null);
    request.setCommitId(tag);

    try {
      gitClient.ensureRepoLocallyClonedAndUpdated(request);
      verify(gitClient, times(0)).clone(eq(request), anyString(), eq(false));
    } catch (Exception e) {
      fail("Should not have thrown any exception");
    }
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testDownloadFiles_Branch_Directory() throws Exception {
    DownloadFilesRequest request = DownloadFilesRequest.builder()
                                       .repoUrl(repoPath)
                                       .authRequest(new UsernamePasswordAuthRequest(USERNAME, PASSWORD.toCharArray()))
                                       .branch("master")
                                       .connectorId(CONNECTOR_ID)
                                       .accountId("ACCOUNT_ID")
                                       .destinationDirectory("./")
                                       .build();
    assertThatThrownBy(() -> gitClient.downloadFiles(request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("FilePaths can not be empty");
    request.setFilePaths(Collections.singletonList("./"));
    doReturn(cache.get(CONNECTOR_ID)).when(gitClientHelper).getLockObject(request.getConnectorId());

    doNothing().when(gitClientHelper).createDirStructureForFileDownload(any());
    doReturn(repoPath).when(gitClientHelper).getFileDownloadRepoDirectory(any());
    addRemote(repoPath);
    gitClient.downloadFiles(request);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testDownloadFiles_File() throws Exception {
    String destinationDirectory = Files.createTempDirectory(UUID.randomUUID().toString()).toString();
    DownloadFilesRequest request = DownloadFilesRequest.builder()
                                       .repoUrl(repoPath)
                                       .authRequest(new UsernamePasswordAuthRequest(USERNAME, PASSWORD.toCharArray()))
                                       .branch("master")
                                       .connectorId(CONNECTOR_ID)
                                       .accountId("ACCOUNT_ID")
                                       .destinationDirectory(destinationDirectory)
                                       .build();
    addRemote(repoPath);
    String data = "ABCD\nDEP\n";
    FileUtils.writeStringToFile(new File(repoPath + "/base.txt"), data, UTF_8);

    request.setFilePaths(Collections.singletonList("./base.txt"));
    doReturn(cache.get(CONNECTOR_ID)).when(gitClientHelper).getLockObject(request.getConnectorId());

    doNothing().when(gitClientHelper).createDirStructureForFileDownload(any());
    doReturn(repoPath).when(gitClientHelper).getFileDownloadRepoDirectory(any());
    gitClient.downloadFiles(request);
    assertThat(FileUtils.readFileToString(new File(destinationDirectory + "/base.txt"), UTF_8)).isEqualTo(data);

    gitClient.downloadFiles(request);
    doReturn(null).when(gitClientHelper).getFileDownloadRepoDirectory(any());
    assertThatThrownBy(() -> gitClient.downloadFiles(request)).isInstanceOf(YamlException.class);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testDownloadFiles_Commit() throws Exception {
    DownloadFilesRequest request = DownloadFilesRequest.builder()
                                       .repoUrl(repoPath)
                                       .authRequest(new UsernamePasswordAuthRequest(USERNAME, PASSWORD.toCharArray()))
                                       .commitId("t1")
                                       .connectorId(CONNECTOR_ID)
                                       .accountId("ACCOUNT_ID")
                                       .destinationDirectory("./")
                                       .build();
    assertThatThrownBy(() -> gitClient.downloadFiles(request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("FilePaths can not be empty");
    request.setFilePaths(Collections.singletonList("./"));
    doReturn(cache.get(CONNECTOR_ID)).when(gitClientHelper).getLockObject(request.getConnectorId());

    doNothing().when(gitClientHelper).createDirStructureForFileDownload(any());
    doReturn(repoPath).when(gitClientHelper).getFileDownloadRepoDirectory(any());
    addRemote(repoPath);
    addGitTag(repoPath, "t1");
    gitClient.downloadFiles(request);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testDownloadFiles_Clone() throws Exception {
    DownloadFilesRequest request = DownloadFilesRequest.builder()
                                       .repoUrl(repoPath)
                                       .authRequest(new UsernamePasswordAuthRequest(USERNAME, PASSWORD.toCharArray()))
                                       .branch("master")
                                       .connectorId(CONNECTOR_ID)
                                       .accountId("ACCOUNT_ID")
                                       .destinationDirectory("./")
                                       .build();
    assertThatThrownBy(() -> gitClient.downloadFiles(request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("FilePaths can not be empty");
    request.setFilePaths(Collections.singletonList("./"));
    doReturn(cache.get(CONNECTOR_ID)).when(gitClientHelper).getLockObject(request.getConnectorId());
    doNothing().when(gitClientHelper).createDirStructureForFileDownload(any());
    doReturn(repoPath).when(gitClientHelper).getFileDownloadRepoDirectory(any());

    assertThatThrownBy(() -> gitClient.downloadFiles(request))
        .isInstanceOf(YamlException.class)
        .hasMessageContaining("Reason: Error in checking out Branch master, Ref origin/master cannot be resolved");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testFetchFilesByPathFail() throws Exception {
    FetchFilesByPathRequest request =
        FetchFilesByPathRequest.builder()
            .repoUrl(repoPath)
            .authRequest(new UsernamePasswordAuthRequest(USERNAME, PASSWORD.toCharArray()))
            .connectorId(CONNECTOR_ID)
            .accountId("ACCOUNT_ID")
            .build();
    assertThatThrownBy(() -> gitClient.fetchFilesByPath(request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("FilePaths can not be empty");
    request.setFilePaths(Collections.singletonList("./"));
    assertThatThrownBy(() -> gitClient.fetchFilesByPath(request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No refs provided to checkout");
    request.setBranch("master");
    doReturn(cache.get(CONNECTOR_ID)).when(gitClientHelper).getLockObject(request.getConnectorId());

    doNothing().when(gitClientHelper).createDirStructureForFileDownload(any());
    doReturn(repoPath).when(gitClientHelper).getFileDownloadRepoDirectory(any());

    assertThatThrownBy(() -> gitClient.fetchFilesByPath(request)).isInstanceOf(JGitRuntimeException.class);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testFetchFilesByPathSuccess() throws Exception {
    FetchFilesByPathRequest request =
        FetchFilesByPathRequest.builder()
            .repoUrl(repoPath)
            .authRequest(new UsernamePasswordAuthRequest(USERNAME, PASSWORD.toCharArray()))
            .connectorId(CONNECTOR_ID)
            .accountId("ACCOUNT_ID")
            .build();
    request.setFilePaths(Collections.singletonList("./"));
    request.setBranch("master");
    doReturn(cache.get(CONNECTOR_ID)).when(gitClientHelper).getLockObject(request.getConnectorId());

    doNothing().when(gitClientHelper).createDirStructureForFileDownload(any());
    doReturn(repoPath).when(gitClientHelper).getFileDownloadRepoDirectory(any());
    addRemote(repoPath);
    gitClient.fetchFilesByPath(request);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testFetchFilesBetweenCommits() throws Exception {
    FetchFilesBwCommitsRequest request =
        FetchFilesBwCommitsRequest.builder()
            .repoUrl(repoPath)
            .authRequest(new UsernamePasswordAuthRequest(USERNAME, PASSWORD.toCharArray()))
            .branch("master")
            .connectorId(CONNECTOR_ID)
            .accountId("ACCOUNT_ID")
            .build();

    assertThatThrownBy(() -> gitClient.fetchFilesBetweenCommits(request))
        .isInstanceOf(YamlException.class)
        .hasMessageContaining("Old commit id can not be empty");

    request.setOldCommitId("t1");

    assertThatThrownBy(() -> gitClient.fetchFilesBetweenCommits(request))
        .isInstanceOf(YamlException.class)
        .hasMessageContaining("New commit id can not be empty");

    request.setNewCommitId("t2");
    doReturn(cache.get(CONNECTOR_ID)).when(gitClientHelper).getLockObject(request.getConnectorId());

    doNothing().when(gitClientHelper).createDirStructureForFileDownload(any());
    doReturn(repoPath).when(gitClientHelper).getFileDownloadRepoDirectory(any());

    assertThatThrownBy(() -> gitClient.fetchFilesBetweenCommits(request)).isInstanceOf(YamlException.class);

    addRemote(repoPath);
    addGitTag(repoPath, "t1");
    addGitTag(repoPath, "t2");
    FetchFilesResult fetchFilesResult = gitClient.fetchFilesBetweenCommits(request);
    assertThat(fetchFilesResult).isNotNull();
    assertThat(fetchFilesResult.getCommitResult().getCommitId()).isEqualTo("t2");
    assertThat(fetchFilesResult.getFiles().size()).isEqualTo(1);
    assertThat(fetchFilesResult.getFiles().get(0).getFilePath()).isEqualTo("t2");
  }

  private String executeCommand(String command) {
    final String[] returnString = new String[1];
    try {
      ProcessExecutor processExecutor = new ProcessExecutor()
                                            .timeout(30, TimeUnit.SECONDS)
                                            .command("/bin/sh", "-c", command)
                                            .readOutput(true)
                                            .redirectOutput(new LogOutputStream() {
                                              @Override
                                              protected void processLine(String line) {
                                                returnString[0] = line;
                                              }
                                            });

      ProcessResult processResult = processExecutor.execute();
      assertThat(processResult.getExitValue()).isEqualTo(0);

    } catch (InterruptedException | TimeoutException | IOException ex) {
      fail("Should not reach here.");
    }
    return Arrays.toString(returnString);
  }

  private String addRemote(String repoPath) {
    try {
      String remoteRepo = Files.createTempDirectory(UUID.randomUUID().toString()).toString();
      createRepo(remoteRepo, true);
      String command = new StringBuilder(128)
                           .append("cd " + repoPath + ";")
                           .append("git remote add origin " + remoteRepo + ";")
                           .append("touch base.txt;")
                           .append("git add base.txt;")
                           .append("git commit -m 'commit base';")
                           .append("git push -u origin master;")
                           .append("git tag base;")
                           .append("git push origin base;")
                           .append("git remote update;")
                           .append("git fetch;")
                           .toString();

      executeCommand(command);
      return remoteRepo;
    } catch (Exception e) {
      fail("Should not reach here.");
      return null;
    }
  }

  private void addGitTag(String repoPath, String tag) {
    try {
      String command = new StringBuilder(128)
                           .append("cd " + repoPath + ";")
                           .append("touch " + tag + ";")
                           .append("git add " + tag + ";")
                           .append("git config user.email 'someone@someplace.com';")
                           .append("git config user.name 'someone';")
                           .append("git commit -m 'commit " + tag + "';")
                           .append("git push origin master;")
                           .append("git tag " + tag + ";")
                           .append("git push origin " + tag + ";")
                           .append("git remote update;")
                           .append("git fetch;")
                           .toString();

      executeCommand(command);
    } catch (Exception e) {
      fail("Should not reach here.");
    }
  }

  private void createRepo(String repoPath, boolean bare) {
    String command = new StringBuilder(128)
                         .append("mkdir -p " + repoPath + ";")
                         .append("cd " + repoPath + ";")
                         .append("git init " + (bare ? "--bare" : "") + ";")
                         .append("git config user.email 'someone@someplace.com';")
                         .append("git config user.name 'someone';")
                         .append("git config gc.auto '0';")
                         .append("git config gc.autopacklimit '0';")
                         .toString();

    executeCommand(command);
  }

  private String addGitCommit(String filename) {
    String command = new StringBuilder(128)
                         .append("cd " + repoPath + ";")
                         .append("touch " + filename + ";")
                         .append("git add " + filename + ";")
                         .append("git commit -m 'commit" + filename + " ';")
                         .append("git push origin master;")
                         .append("git remote update;")
                         .append("git fetch;")
                         .toString();
    return executeCommand(command);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testDiff() throws GitAPIException, IOException {
    final DiffRequest diffRequest = DiffRequest.builder()
                                        .accountId("accountId")
                                        .branch("master")
                                        .authRequest(new UsernamePasswordAuthRequest(USERNAME, PASSWORD.toCharArray()))
                                        .build();
    doNothing().when(gitClient).ensureRepoLocallyClonedAndUpdated(diffRequest);
    doReturn(repoPath).when(gitClientHelper).getRepoDirectory(diffRequest);
    doNothing().when(gitClient).performGitPull(any(), any());

    addRemote(repoPath);
    addGitCommit("2.txt");
    addGitCommit("3.txt");
    final DiffResult diffResult = gitClient.diff(diffRequest);

    assertThat(diffResult).isNotNull();
    assertThat(diffResult.getCommitTimeMs()).isNotNull();
    assertThat(diffResult.getGitFileChanges().size()).isEqualTo(2);
    assertThat(diffResult.getAccountId()).isNotNull();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testApplyChangeSetOnFileSystem() throws Exception {
    CommitAndPushRequest gitCommitAndPushRequest =
        CommitAndPushRequest.builder().gitFileChanges(getSampleGitFileChanges()).build();

    List<String> filesToAdd = new ArrayList<>();

    String repoPath = Files.createTempDirectory(UUID.randomUUID().toString()).toString();
    File rootDirectory = new File(repoPath);
    FileUtils.cleanDirectory(rootDirectory);
    createRepo(repoPath, false);
    Git git = Git.open(rootDirectory);
    // should not delete since files they are not tracked
    gitClient.applyChangeSetOnFileSystem(repoPath, gitCommitAndPushRequest, filesToAdd, git);

    Status status = git.status().call();
    assertThat(status.getAdded()).hasSize(0);
    assertThat(status.getRemoved()).hasSize(0);
    assertThat(status.getUntracked()).isNotEmpty();

    gitClient.applyGitAddCommand(gitCommitAndPushRequest, filesToAdd, git);
    filesToAdd.clear();

    status = git.status().call();
    assertThat(status.getAdded().stream().map(filePath -> Paths.get(filePath).getFileName().toString()))
        .containsExactlyInAnyOrderElementsOf(
            gitCommitAndPushRequest.getGitFileChanges()
                .stream()
                .filter(gfc -> ADD == gfc.getChangeType())
                .map(gitFileChange -> Paths.get(gitFileChange.getFilePath()).getFileName().toString())
                .collect(Collectors.toSet()));

    // should delete the required files
    gitClient.applyChangeSetOnFileSystem(repoPath, gitCommitAndPushRequest, filesToAdd, git);

    status = git.status().call();
    assertThat(status.getRemoved()).isEmpty();
    assertThat(status.getAdded().stream().map(filePath -> Paths.get(filePath).getFileName().toString()))
        .doesNotContainAnyElementsOf(
            gitCommitAndPushRequest.getGitFileChanges()
                .stream()
                .filter(gfc -> DELETE == gfc.getChangeType())
                .map(gitFileChange -> Paths.get(gitFileChange.getFilePath()).getFileName().toString())
                .collect(Collectors.toSet()));

    doGitCommit(git);

    // Test Rename
    String anyExistingFileName = buildFileName(5);
    String newFileName = anyExistingFileName + "-new";
    GitFileChange renameGitFileChange =
        GitFileChange.builder().changeType(RENAME).oldFilePath(anyExistingFileName).filePath(newFileName).build();
    gitCommitAndPushRequest.setGitFileChanges(asList(renameGitFileChange));

    gitClient.applyChangeSetOnFileSystem(repoPath, gitCommitAndPushRequest, filesToAdd, git);

    gitClient.applyGitAddCommand(gitCommitAndPushRequest, filesToAdd, git);
    filesToAdd.clear();
    status = git.status().call();
    assertThat(status.getAdded()).containsExactly(newFileName);
    assertThat(status.getRemoved()).containsExactly(anyExistingFileName);

    // CleanUp
    FileUtils.deleteQuietly(rootDirectory);
  }

  private void doGitCommit(Git git) throws Exception {
    RevCommit revCommit = git.commit().setCommitter("dummy", "dummy@Dummy").setAll(true).setMessage("dummy").call();
  }

  private List<GitFileChange> getSampleGitFileChanges() {
    List<GitFileChange> gitFileChanges = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      gitFileChanges.add(GitFileChange.builder()
                             .changeType(ADD)
                             .filePath(buildFileName(i))
                             .fileContent(i + " " + System.currentTimeMillis())
                             .build());
    }
    for (int i = 0; i < 10; i += 3) {
      gitFileChanges.add(GitFileChange.builder().changeType(DELETE).filePath(buildFileName(i)).build());
    }
    log.info(gitFileChanges.toString());
    return gitFileChanges;
  }

  private String buildFileName(int i) {
    return i + ".txt";
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testRevert() throws GitAPIException, IOException {
    File newFile = new File(repoPath, "file1.txt");
    FileUtils.writeStringToFile(newFile, "Line 1\r\n", "UTF-8", true);
    git.add().addFilepattern("file1.txt").call();
    git.commit().setAuthor("test", "test@test.com").setMessage("Commit Log 1").call();

    // commit some changes
    FileUtils.writeStringToFile(newFile, "Line 2\r\n", "UTF-8", true);
    git.add().addFilepattern("file1.txt").call();
    RevCommit rev2 = git.commit().setAll(true).setAuthor("test", "test@test.com").setMessage("Commit Log 2").call();

    final RevertAndPushRequest revertAndPushRequest =
        RevertAndPushRequest.builder().commitId(rev2.getId().getName()).build();

    doNothing().when(gitClient).ensureRepoLocallyClonedAndUpdated(any());
    doReturn(repoPath).when(gitClientHelper).getRepoDirectory(any());

    final CommitResult commit = gitClient.revert(RevertRequest.mapFromRevertAndPushRequest(revertAndPushRequest));
    git.rm();

    assertThat(commit).isNotNull();
    assertThat(commit.getCommitId()).isNotNull();
    assertThat(commit.getCommitMessage()).isNotNull();
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testRevert_shouldFailWithNoParentsCommit() throws IOException, GitAPIException {
    File newFile = new File(repoPath, "file1.txt");
    FileUtils.writeStringToFile(newFile, "Line 1\r\n", "UTF-8", true);
    git.add().addFilepattern("file1.txt").call();
    RevCommit rev1 = git.commit().setAuthor("test", "test@test.com").setMessage("Commit Log 1").call();

    final RevertAndPushRequest revertAndPushRequest =
        RevertAndPushRequest.builder().commitId(rev1.getId().getName()).build();

    doNothing().when(gitClient).ensureRepoLocallyClonedAndUpdated(any());
    doReturn(repoPath).when(gitClientHelper).getRepoDirectory(any());

    git.rm();
    assertThatThrownBy(() -> gitClient.revert(RevertRequest.mapFromRevertAndPushRequest(revertAndPushRequest)))
        .isInstanceOf(YamlException.class);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testRevert_shouldFailWithInvalidCommit() throws IOException, GitAPIException {
    File newFile = new File(repoPath, "file1.txt");
    FileUtils.writeStringToFile(newFile, "Line 1\r\n", "UTF-8", true);
    git.add().addFilepattern("file1.txt").call();
    git.commit().setAuthor("test", "test@test.com").setMessage("Commit Log 1").call();

    // commit some changes
    FileUtils.writeStringToFile(newFile, "Line 2\r\n", "UTF-8", true);
    git.add().addFilepattern("file1.txt").call();
    git.commit().setAll(true).setAuthor("test", "test@test.com").setMessage("Commit Log 2").call();

    final RevertAndPushRequest revertAndPushRequest =
        RevertAndPushRequest.builder().commitId("invalid-commit-id").build();

    doNothing().when(gitClient).ensureRepoLocallyClonedAndUpdated(any());
    doReturn(repoPath).when(gitClientHelper).getRepoDirectory(any());

    git.rm();
    assertThatThrownBy(() -> gitClient.revert(RevertRequest.mapFromRevertAndPushRequest(revertAndPushRequest)))
        .isInstanceOf(YamlException.class);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testPushRevert() throws IOException, GitAPIException {
    File newFile = new File(repoPath, "file1.txt");
    FileUtils.writeStringToFile(newFile, "Line 1\r\n", "UTF-8", true);
    git.add().addFilepattern("file1.txt").call();
    git.commit().setAuthor("test", "test@test.com").setMessage("Commit Log 1").call();

    // commit some changes
    FileUtils.writeStringToFile(newFile, "Line 2\r\n", "UTF-8", true);
    git.add().addFilepattern("file1.txt").call();
    RevCommit rev2 = git.commit().setAll(true).setAuthor("test", "test@test.com").setMessage("Commit Log 2").call();

    doNothing().when(gitClient).updateRemoteOriginInConfig(any(), any(), any());
    RevertAndPushRequest request = RevertAndPushRequest.builder().commitId(rev2.getId().getName()).build();

    PushResultGit toBeReturned = pushResultBuilder().refUpdate(PushResultGit.RefUpdate.builder().build()).build();
    addRemote(repoPath);

    doNothing().when(gitClient).ensureRepoLocallyClonedAndUpdated(any());
    doReturn(repoPath).when(gitClientHelper).getRepoDirectory(any());

    doReturn(toBeReturned).when(gitClient).push(PushRequest.mapFromRevertAndPushRequest(request));
    git.rm();
    final RevertAndPushResult revertAndPushResult = gitClient.revertAndPush(request);
    assertThat(revertAndPushResult).isNotNull();
    assertThat(revertAndPushResult.getGitCommitResult()).isNotNull();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testCommit() {
    final CommitAndPushRequest commitAndPushRequest =
        CommitAndPushRequest.builder().gitFileChanges(getSampleGitFileChanges()).build();
    doNothing().when(gitClient).ensureRepoLocallyClonedAndUpdated(commitAndPushRequest);
    doReturn(repoPath).when(gitClientHelper).getRepoDirectory(commitAndPushRequest);
    final CommitResult commit = gitClient.commit(commitAndPushRequest);
    git.rm();
    assertThat(commit).isNotNull();
    assertThat(commit.getCommitId()).isNotNull();
    assertThat(commit.getCommitMessage()).isNotNull();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testCommitAndPush() throws Exception {
    doNothing().when(gitClient).updateRemoteOriginInConfig(any(), any(), any());
    List<GitFileChange> gitFileChanges = getSampleGitFileChanges();
    CommitAndPushRequest gitCommitAndPushRequest =
        CommitAndPushRequest.builder().gitFileChanges(gitFileChanges).build();

    PushResultGit toBeReturned = pushResultBuilder().refUpdate(PushResultGit.RefUpdate.builder().build()).build();
    addRemote(repoPath);
    doNothing().when(gitClient).ensureRepoLocallyClonedAndUpdated(gitCommitAndPushRequest);
    doReturn(repoPath).when(gitClientHelper).getRepoDirectory(gitCommitAndPushRequest);
    doReturn(toBeReturned).when(gitClient).push(gitCommitAndPushRequest);
    git.rm();
    final CommitAndPushResult commitAndPushResult = gitClient.commitAndPush(gitCommitAndPushRequest);
    assertThat(commitAndPushResult).isNotNull();
    assertThat(commitAndPushResult.getFilesCommittedToGit()).isNotNull();
    assertThat(commitAndPushResult.getGitCommitResult()).isNotNull();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testCommitAndPush_Warn() throws Exception {
    CommitAndPushRequest request = CommitAndPushRequest.builder().build();
    CommitResult result = CommitResult.builder().build();
    doReturn(result).when(gitClient).commit(request);

    assertThat(gitClient.commitAndPush(request).getGitCommitResult()).isEqualTo(result);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testPush() {
    doNothing().when(gitClient).updateRemoteOriginInConfig(any(), any(), any());

    GitFileChange gitFileChange = GitFileChange.builder().changeType(ADD).filePath(repoPath + "/1.txt").build();
    CommitAndPushRequest gitCommitAndPushRequest =
        CommitAndPushRequest.builder()
            .gitFileChanges(Collections.singletonList(gitFileChange))
            .authorName("authorName")
            .authRequest(new UsernamePasswordAuthRequest(USERNAME, PASSWORD.toCharArray()))
            .accountId("accountId")
            .branch("master")
            .build();
    doNothing().when(gitClient).ensureRepoLocallyClonedAndUpdated(gitCommitAndPushRequest);
    doReturn(repoPath).when(gitClientHelper).getRepoDirectory(gitCommitAndPushRequest);
    addRemote(repoPath);
    git.rm();
    final PushResultGit pushResultGit = gitClient.push(gitCommitAndPushRequest);
    assertThat(pushResultGit).isNotNull();
    assertThat(pushResultGit.getRefUpdate()).isNotNull();
    assertThat(pushResultGit.getRefUpdate().getExpectedOldObjectId()).isNull();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testUpdateRemoteOriginInConfig_Exceptions() throws Exception {
    gitClient.updateRemoteOriginInConfig(repoPath, new File("wrong_path"), false);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testPull_http() throws Exception {
    DiffRequest request = DiffRequest.builder()
                              .repoUrl(repoPath)
                              .authRequest(new UsernamePasswordAuthRequest(USERNAME, PASSWORD.toCharArray()))
                              .build();
    addRemote(repoPath);
    addGitTag(repoPath, "t1");
    gitClient.performGitPull(request, git);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetHeadCommit() throws Exception {
    assertThatThrownBy(() -> gitClient.getHeadCommit(git)).isInstanceOf(YamlException.class);
    addRemote(repoPath);
    addGitTag(repoPath, "t1");
    String headCommit = gitClient.getHeadCommit(git);
    assertThat(headCommit).isNotNull();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testEnsureLastProcessedCommitIsHead() throws Exception {
    gitClient.ensureLastProcessedCommitIsHead(false, null, git);
    gitClient.ensureLastProcessedCommitIsHead(true, null, git);
    addRemote(repoPath);
    addGitTag(repoPath, "t1");
    assertThatThrownBy(() -> gitClient.ensureLastProcessedCommitIsHead(true, "12345", git))
        .isInstanceOf(YamlException.class);
    final String headCommit = gitClient.getHeadCommit(git);
    gitClient.ensureLastProcessedCommitIsHead(true, headCommit, git);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testApplyGitAddCommand_Exception() throws Exception {
    GitBaseRequest request = GitBaseRequest.builder().build();
    List<String> filesToAdd = new ArrayList<>();
    gitClient.applyGitAddCommand(request, filesToAdd, git);

    Git mockedGit = Mockito.mock(Git.class);
    AddCommand mockedAddGit = Mockito.mock(AddCommand.class);

    doReturn(mockedAddGit).when(mockedGit).add();
    doReturn(mockedAddGit).when(mockedAddGit).addFilepattern(any());
    doThrow(new NoFilepatternException("m1")).when(mockedAddGit).call();

    filesToAdd.add("testApplyGitAddCommand_Exception");

    assertThatThrownBy(() -> gitClient.applyGitAddCommand(request, filesToAdd, mockedGit))
        .isInstanceOf(YamlException.class);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testMatchingFilesExtensions() {
    addRemote(repoPath);
    Path path = Paths.get(repoPath + "/base.txt");
    Predicate<Path> nullPredicate = gitClient.matchingFilesExtensions(null);
    Predicate<Path> emptyPredicate = gitClient.matchingFilesExtensions(new ArrayList<>());
    Predicate<Path> validPredicate = gitClient.matchingFilesExtensions(Arrays.asList(".txt"));
    Predicate<Path> invalidPredicate = gitClient.matchingFilesExtensions(Arrays.asList(".yaml"));

    assertThat(nullPredicate.test(path)).isTrue();
    assertThat(emptyPredicate.test(path)).isTrue();
    assertThat(validPredicate.test(path)).isTrue();
    assertThat(invalidPredicate.test(path)).isFalse();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testDisablingLocalGitConfigWithCustomUserGitConfigSystemReader() throws Exception {
    String rightRemoteUrl = "rightUrl";
    String wrongRemoteUrl = "wrongUrl";
    String repoPathInternal = Files.createTempDirectory(UUID.randomUUID().toString()).toString();
    createRepo(repoPathInternal, false);
    String command =
        new StringBuilder(128)
            .append("cd " + repoPathInternal + ";")
            .append("git remote add origin " + rightRemoteUrl + ";")
            .append("printf '[url \"" + wrongRemoteUrl + "\"]\\n insteadOf = " + rightRemoteUrl + "\\n' >> .gitconfig;")
            .toString();
    executeCommand(command);
    String userGitConfigPath = repoPathInternal + "/.gitconfig";
    SystemReader.setInstance(new CustomUserGitConfigSystemReader(userGitConfigPath));
    Git git1 = Git.open(new File(repoPathInternal));
    assertThat(git1.remoteList().call().get(0).getURIs().get(0).getPath()).isEqualTo(wrongRemoteUrl);
    git1.close();
    SystemReader.setInstance(null);
    Git git2 = Git.open(new File(repoPathInternal));
    assertThat(git2.remoteList().call().get(0).getURIs().get(0).getPath()).isEqualTo(rightRemoteUrl);
    git2.close();
    SystemReader.setInstance(new CustomUserGitConfigSystemReader(userGitConfigPath));
    Git git3 = Git.open(new File(repoPathInternal));
    assertThat(git3.remoteList().call().get(0).getURIs().get(0).getPath()).isEqualTo(wrongRemoteUrl);
    git3.close();
    SystemReader.setInstance(null);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testListRemote() {
    addRemote(repoPath);
    ListRemoteRequest request = ListRemoteRequest.builder()
                                    .repoUrl(repoPath)
                                    .authRequest(new UsernamePasswordAuthRequest(USERNAME, PASSWORD.toCharArray()))
                                    .build();
    ListRemoteResult listRemoteResult = gitClient.listRemote(request);
    Map<String, String> remoteListing = listRemoteResult.getRemoteList();
    assertThat(remoteListing)
        .containsEntry("HEAD", "refs/heads/master")
        .containsEntry("refs/tags/base", "refs/tags/base")
        .containsEntry("refs/heads/master", "refs/heads/master")
        .containsEntry("refs/remotes/origin/master", "refs/remotes/origin/master");
  }
}
