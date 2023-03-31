/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.taskprogress.ITaskProgressClient;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.git.GitFetchFilesTaskHelper;
import io.harness.delegate.task.git.GitFetchTaskHelper;
import io.harness.delegate.task.git.ScmFetchFilesHelperNG;
import io.harness.delegate.task.serverless.request.ServerlessGitFetchRequest;
import io.harness.delegate.task.serverless.response.ServerlessGitFetchResponse;
import io.harness.git.GitClientV2;
import io.harness.git.model.CommitResult;
import io.harness.git.model.FetchFilesResult;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServerlessGitFetchTaskTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  final DelegateTaskPackage delegateTaskPackage =
      DelegateTaskPackage.builder().data(TaskData.builder().build()).build();
  @Mock private GitFetchTaskHelper serverlessGitFetchTaskHelper;
  @Mock private GitAuthenticationDTO gitAuthenticationDTO = GitHTTPAuthenticationDTO.builder().build();
  @Mock private BooleanSupplier preExecute;
  @Mock private Consumer<DelegateTaskResponse> consumer;
  @Mock private ILogStreamingTaskClient logStreamingTaskClient;
  @Mock private GitDecryptionHelper gitDecryptionHelper;
  @Mock private GitClientV2 gitClientV2;
  @Mock private ITaskProgressClient taskProgressClient;
  @Mock private ExecutorService executorService;
  @Mock private GitFetchFilesTaskHelper gitFetchFilesTaskHelper;
  @Mock private ScmFetchFilesHelperNG scmFetchFilesHelper;
  @Mock private NGGitService ngGitService;
  @Mock private SecretDecryptionService secretDecryptionService;

  @Inject
  @InjectMocks
  ServerlessGitFetchTask serverlessGitFetchTask =
      new ServerlessGitFetchTask(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);

  private static final String identifier = "iden";
  private static final String manifestType = "serverless-lambda";
  private static final String url = "url";
  private GitConnectionType gitConnectionType = GitConnectionType.ACCOUNT;
  private ScmConnector scmConnector = GitConfigDTO.builder()
                                          .url(url)
                                          .gitAuthType(GitAuthType.HTTP)
                                          .gitAuth(gitAuthenticationDTO)
                                          .validationRepo("asfd")
                                          .branchName("asfdf")
                                          .executeOnDelegate(false)
                                          .delegateSelectors(Collections.emptySet())
                                          .gitConnectionType(gitConnectionType)
                                          .build();
  private static final String branch = "bran";
  private static final String path = "path/";
  private static final String accountId = "account";
  private static final String configOverridePath = "override/";
  private GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder()
                                                              .gitConfigDTO(scmConnector)
                                                              .fetchType(FetchType.BRANCH)
                                                              .branch(branch)
                                                              .optimizedFilesFetch(true)
                                                              .path(path)
                                                              .build();
  private ServerlessGitFetchFileConfig serverlessGitFetchFileConfig =
      ServerlessGitFetchFileConfig.builder()
          .identifier(identifier)
          .manifestType(manifestType)
          .configOverridePath(configOverridePath)
          .gitStoreDelegateConfig(gitStoreDelegateConfig)
          .build();
  private static final String activityId = "activityid";
  private TaskParameters taskParameters = ServerlessGitFetchRequest.builder()
                                              .activityId(activityId)
                                              .accountId(accountId)
                                              .serverlessGitFetchFileConfig(serverlessGitFetchFileConfig)
                                              .shouldOpenLogStream(true)
                                              .closeLogStream(true)
                                              .build();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Reflect.on(serverlessGitFetchTask).set("gitFetchTaskHelper", serverlessGitFetchTaskHelper);
    Reflect.on(serverlessGitFetchTaskHelper).set("gitDecryptionHelper", gitDecryptionHelper);
    Reflect.on(serverlessGitFetchTaskHelper).set("gitClientV2", gitClientV2);
    Reflect.on(serverlessGitFetchTaskHelper).set("gitFetchFilesTaskHelper", gitFetchFilesTaskHelper);
    Reflect.on(serverlessGitFetchTaskHelper).set("scmFetchFilesHelper", scmFetchFilesHelper);
    Reflect.on(serverlessGitFetchTaskHelper).set("ngGitService", ngGitService);
    Reflect.on(serverlessGitFetchTaskHelper).set("secretDecryptionService", secretDecryptionService);
    doReturn(taskProgressClient).when(logStreamingTaskClient).obtainTaskProgressClient();
    doReturn(executorService).when(logStreamingTaskClient).obtainTaskProgressExecutor();
  }

  @Test(expected = TaskNGDataException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void fetchManifestFileInPriorityOrderExceptionTest() throws IOException {
    GitConfigDTO gitConfigDTO = GitConfigDTO.builder().url(url).build();
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder()
                                                        .branch(branch)
                                                        .commitId("commitId")
                                                        .connectorName("connector")
                                                        .manifestId("manifest")
                                                        .path(path)
                                                        .gitConfigDTO(gitConfigDTO)
                                                        .fetchType(FetchType.BRANCH)
                                                        .optimizedFilesFetch(false)
                                                        .build();
    ServerlessGitFetchFileConfig serverlessGitFetchFileConfig = ServerlessGitFetchFileConfig.builder()
                                                                    .identifier(identifier)
                                                                    .manifestType(manifestType)
                                                                    .gitStoreDelegateConfig(gitStoreDelegateConfig)
                                                                    .build();
    FetchFilesResult fetchFilesResult =
        FetchFilesResult.builder()
            .commitResult(CommitResult.builder().commitId("commitId").accountId(accountId).build())
            .build();
    String combinedPath = path + configOverridePath;
    Collections.singletonList(combinedPath);
    doReturn(fetchFilesResult).when(gitClientV2).fetchFilesByPath(any());
    TaskParameters taskParameters = ServerlessGitFetchRequest.builder()
                                        .activityId(activityId)
                                        .accountId(accountId)
                                        .serverlessGitFetchFileConfig(serverlessGitFetchFileConfig)
                                        .shouldOpenLogStream(true)
                                        .closeLogStream(true)
                                        .build();
    serverlessGitFetchTask.run(taskParameters);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void runTest() throws IOException {
    String combinedPath = path + configOverridePath;
    List<String> filePaths = Collections.singletonList(combinedPath);
    FetchFilesResult fetchFilesResult = FetchFilesResult.builder().build();

    doReturn(fetchFilesResult)
        .when(serverlessGitFetchTaskHelper)
        .fetchFileFromRepo(gitStoreDelegateConfig, filePaths, accountId, null);
    ServerlessGitFetchResponse serverlessGitFetchResponse =
        (ServerlessGitFetchResponse) serverlessGitFetchTask.run(taskParameters);
    Map<String, FetchFilesResult> filesFromMultipleRepo = new HashMap<>();
    filesFromMultipleRepo.put(serverlessGitFetchFileConfig.getIdentifier(), fetchFilesResult);
    assertThat(serverlessGitFetchResponse.getFilesFromMultipleRepo()).isEqualTo(filesFromMultipleRepo);
  }
}