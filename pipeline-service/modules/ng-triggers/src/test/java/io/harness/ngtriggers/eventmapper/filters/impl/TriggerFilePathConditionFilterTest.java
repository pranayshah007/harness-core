/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.delegate.beans.connector.scm.GitAuthType.HTTP;
import static io.harness.ngtriggers.Constants.CHANGED_FILES;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.EQUALS;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.REGEX;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static software.wings.beans.TaskType.SCM_PATH_FILTER_EVALUATION_TASK;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernamePasswordDTO;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTaskParams;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTaskResponse;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.ng.core.NGAccess;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.ngtriggers.conditionchecker.ConditionEvaluator;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.ngtriggers.utils.SCMFilePathEvaluatorFactory;
import io.harness.ngtriggers.utils.SCMFilePathEvaluatorOnDelegate;
import io.harness.ngtriggers.utils.SCMFilePathEvaluatorOnManager;
import io.harness.ngtriggers.utils.TaskExecutionUtils;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.rule.Owner;
import io.harness.secrets.SecretDecryptor;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.BinaryResponseData;
import io.harness.utils.ConnectorUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class TriggerFilePathConditionFilterTest extends CategoryTest {
  @Mock private TaskExecutionUtils taskExecutionUtils;
  @Mock private NGTriggerElementMapper ngTriggerElementMapper;
  @Mock private NGTriggerService ngTriggerService;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private KryoSerializer referenceFalseKryoSerializer;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private SecretDecryptor secretDecryptor;
  @Mock private TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  @Inject @InjectMocks private FilepathTriggerFilter filter;
  @Mock private SCMFilePathEvaluatorOnDelegate scmFilePathEvaluatorOnDelegate;
  @Mock private SCMFilePathEvaluatorOnManager scmFilePathEvaluatorOnManager;
  @InjectMocks private SCMFilePathEvaluatorFactory scmFilePathEvaluatorFactory;
  private static List<NGTriggerEntity> triggerEntities;
  private MockedStatic<ConditionEvaluator> aStatic;

  String pushPayload = "{\"commits\": [\n"
      + "  {\n"
      + "    \"id\": \"3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"tree_id\": \"cc8524297287b55f07e38c56ecd43625f935252f\",\n"
      + "    \"distinct\": true,\n"
      + "    \"message\": \"nn\",\n"
      + "    \"timestamp\": \"2021-06-25T14:52:49-07:00\",\n"
      + "    \"url\": \"https://github.com/wings-software/cicddemo/commit/3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"author\": {\n"
      + "      \"name\": \"Adwait Bhandare\",\n"
      + "      \"email\": \"adwait.bhandare@harness.io\",\n"
      + "      \"username\": \"adwaitabhandare\"\n"
      + "    },\n"
      + "    \"committer\": {\n"
      + "      \"name\": \"GitHub\",\n"
      + "      \"email\": \"noreply@github.com\",\n"
      + "      \"username\": \"web-flow\"\n"
      + "    },\n"
      + "    \"added\": [\n"
      + "      \"spec/manifest1.yml\"\n"
      + "    ],\n"
      + "    \"removed\": [\n"
      + "      \"File1_Removed.txt\"\n"
      + "    ],\n"
      + "    \"modified\": [\n"
      + "      \"values/value1.yml\"\n"
      + "    ]\n"
      + "  }, \n"
      + "  {\n"
      + "    \"id\": \"3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"tree_id\": \"cc8524297287b55f07e38c56ecd43625f935252f\",\n"
      + "    \"distinct\": true,\n"
      + "    \"message\": \"nn\",\n"
      + "    \"timestamp\": \"2021-06-25T14:52:49-07:00\",\n"
      + "    \"url\": \"https://github.com/wings-software/cicddemo/commit/3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"author\": {\n"
      + "      \"name\": \"Adwait Bhandare\",\n"
      + "      \"email\": \"adwait.bhandare@harness.io\",\n"
      + "      \"username\": \"adwaitabhandare\"\n"
      + "    },\n"
      + "    \"committer\": {\n"
      + "      \"name\": \"GitHub\",\n"
      + "      \"email\": \"noreply@github.com\",\n"
      + "      \"username\": \"web-flow\"\n"
      + "    },\n"
      + "    \"added\": [\n"
      + "      \"spec/manifest2.yml\"\n"
      + "    ],\n"
      + "    \"removed\": [\n"
      + "      \"File2_Removed.txt\"\n"
      + "    ],\n"
      + "    \"modified\": [\n"
      + "      \"values/value2.yml\"\n"
      + "    ]\n"
      + "  }\n"
      + "]}";

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    aStatic = mockStatic(ConditionEvaluator.class, CALLS_REAL_METHODS);
    on(filter).set("scmFilePathEvaluatorFactory", scmFilePathEvaluatorFactory);
    on(scmFilePathEvaluatorOnManager).set("secretDecryptor", secretDecryptor);
    on(scmFilePathEvaluatorOnDelegate).set("taskExecutionUtils", taskExecutionUtils);
    on(scmFilePathEvaluatorOnDelegate).set("kryoSerializer", kryoSerializer);
    on(scmFilePathEvaluatorOnDelegate).set("referenceFalseKryoSerializer", referenceFalseKryoSerializer);
    on(scmFilePathEvaluatorOnDelegate).set("taskSetupAbstractionHelper", taskSetupAbstractionHelper);
  }

  @After
  public void cleanup() {
    aStatic.close();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testPathFilterEvaluationNotNeeded() {
    FilterRequestData filterRequestData =
        FilterRequestData.builder().webhookPayloadData(WebhookPayloadData.builder().build()).build();
    assertThat(filter.pathFilterEvaluationNotNeeded(filterRequestData)).isTrue();

    filterRequestData.setWebhookPayloadData(
        WebhookPayloadData.builder()
            .originalEvent(TriggerWebhookEvent.builder()
                               .sourceRepoType(WebhookTriggerType.AWS_CODECOMMIT.getEntityMetadataName())
                               .build())
            .build());
    assertThat(filter.pathFilterEvaluationNotNeeded(filterRequestData)).isTrue();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testInitiateDelegateTaskAndEvaluateForPR() {
    // Init Data
    TriggerDetails triggerDetails = generateTriggerDetails();

    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .url("http://localhost")
            .apiAccess(GithubApiAccessDTO.builder().spec(GithubTokenSpecDTO.builder().build()).build())
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                                          .username("usermane")
                                                                          .passwordRef(SecretRefData.builder().build())
                                                                          .build())
                                                 .build())
                                .build())
            .build();

    List<EncryptedDataDetail> encryptedDataDetails = emptyList();
    TriggerEventDataCondition pathCondition =
        TriggerEventDataCondition.builder().key(CHANGED_FILES).operator(EQUALS).value("test").build();
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .build();
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder().parseWebhookResponse(parseWebhookResponse).build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder().accountId("acc").webhookPayloadData(webhookPayloadData).build();

    // Mock apis
    doReturn(ConnectorDetails.builder()
                 .connectorConfig(githubConnectorDTO)
                 .connectorType(ConnectorType.GITHUB)
                 .encryptedDataDetails(encryptedDataDetails)
                 .build())
        .when(connectorUtils)
        .getConnectorDetails(any(NGAccess.class), eq("account.conn"));

    when(scmFilePathEvaluatorOnDelegate.getScmPathFilterEvaluationTaskParams(any(), any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnDelegate.execute(any(), any(), any(), any())).thenCallRealMethod();

    byte[] data = new byte[0];
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenReturn(BinaryResponseData.builder().data(data).build());
    doReturn(ScmPathFilterEvaluationTaskResponse.builder().matched(true).build())
        .when(kryoSerializer)
        .asInflatedObject(data);
    doReturn(null).when(taskSetupAbstractionHelper).getOwner(any(), any(), any());

    ArgumentCaptor<DelegateTaskRequest> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    assertThat(filter.initiateSCMTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition)).isTrue();
    verify(taskExecutionUtils, times(1)).executeSyncTask(argumentCaptor.capture());

    // Assert Delegate Task request object generated
    DelegateTaskRequest delegateTaskRequest = argumentCaptor.getValue();
    assertThat(delegateTaskRequest.getAccountId()).isEqualTo("acc");
    assertThat(delegateTaskRequest.getTaskType()).isEqualTo(SCM_PATH_FILTER_EVALUATION_TASK.toString());

    assertThat(delegateTaskRequest.getTaskParameters()).isNotNull();

    TaskParameters taskParameters = delegateTaskRequest.getTaskParameters();
    assertThat(ScmPathFilterEvaluationTaskParams.class.isAssignableFrom(taskParameters.getClass()));
    ScmPathFilterEvaluationTaskParams params = (ScmPathFilterEvaluationTaskParams) taskParameters;
    assertThat(params.getScmConnector()).isEqualTo(githubConnectorDTO);
    assertThat(params.getEncryptedDataDetails()).isEqualTo(encryptedDataDetails);
    assertThat(params.getPrNumber()).isEqualTo(2);
    assertThat(params.getOperator()).isEqualTo(EQUALS.getValue());
    assertThat(params.getStandard()).isEqualTo("test");

    // DelegateTask returns Error
    doReturn(ErrorNotifyResponseData.builder().errorMessage("error").build())
        .when(kryoSerializer)
        .asInflatedObject(data);
    assertThat(filter.initiateSCMTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition)).isFalse();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testInitiateDelegateTaskAndEvaluateForPRUsingKryoWithoutReference() {
    // Init Data
    TriggerDetails triggerDetails = generateTriggerDetails();

    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .url("http://localhost")
            .apiAccess(GithubApiAccessDTO.builder().spec(GithubTokenSpecDTO.builder().build()).build())
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                                          .username("usermane")
                                                                          .passwordRef(SecretRefData.builder().build())
                                                                          .build())
                                                 .build())
                                .build())
            .build();

    List<EncryptedDataDetail> encryptedDataDetails = emptyList();
    TriggerEventDataCondition pathCondition =
        TriggerEventDataCondition.builder().key(CHANGED_FILES).operator(EQUALS).value("test").build();
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .build();
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder().parseWebhookResponse(parseWebhookResponse).build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder().accountId("acc").webhookPayloadData(webhookPayloadData).build();

    // Mock apis
    doReturn(ConnectorDetails.builder()
                 .connectorConfig(githubConnectorDTO)
                 .connectorType(ConnectorType.GITHUB)
                 .encryptedDataDetails(encryptedDataDetails)
                 .build())
        .when(connectorUtils)
        .getConnectorDetails(any(NGAccess.class), eq("account.conn"));

    when(scmFilePathEvaluatorOnDelegate.getScmPathFilterEvaluationTaskParams(any(), any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnDelegate.execute(any(), any(), any(), any())).thenCallRealMethod();

    byte[] data = new byte[0];
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenReturn(BinaryResponseData.builder().data(data).usingKryoWithoutReference(true).build());
    doReturn(ScmPathFilterEvaluationTaskResponse.builder().matched(true).build())
        .when(referenceFalseKryoSerializer)
        .asInflatedObject(data);
    doReturn(null).when(taskSetupAbstractionHelper).getOwner(any(), any(), any());

    ArgumentCaptor<DelegateTaskRequest> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    assertThat(filter.initiateSCMTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition)).isTrue();
    verify(taskExecutionUtils, times(1)).executeSyncTask(argumentCaptor.capture());

    // Assert Delegate Task request object generated
    DelegateTaskRequest delegateTaskRequest = argumentCaptor.getValue();
    assertThat(delegateTaskRequest.getAccountId()).isEqualTo("acc");
    assertThat(delegateTaskRequest.getTaskType()).isEqualTo(SCM_PATH_FILTER_EVALUATION_TASK.toString());

    assertThat(delegateTaskRequest.getTaskParameters()).isNotNull();

    TaskParameters taskParameters = delegateTaskRequest.getTaskParameters();
    assertThat(ScmPathFilterEvaluationTaskParams.class.isAssignableFrom(taskParameters.getClass()));
    ScmPathFilterEvaluationTaskParams params = (ScmPathFilterEvaluationTaskParams) taskParameters;
    assertThat(params.getScmConnector()).isEqualTo(githubConnectorDTO);
    assertThat(params.getEncryptedDataDetails()).isEqualTo(encryptedDataDetails);
    assertThat(params.getPrNumber()).isEqualTo(2);
    assertThat(params.getOperator()).isEqualTo(EQUALS.getValue());
    assertThat(params.getStandard()).isEqualTo("test");

    // DelegateTask returns Error
    doReturn(ErrorNotifyResponseData.builder().errorMessage("error").build())
        .when(referenceFalseKryoSerializer)
        .asInflatedObject(data);
    assertThat(filter.initiateSCMTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition)).isFalse();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testInitiateManagerTaskAndEvaluateForPR() {
    TriggerDetails triggerDetails = generateTriggerDetails();
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .url("http://localhost")
            .apiAccess(GithubApiAccessDTO.builder().spec(GithubTokenSpecDTO.builder().build()).build())
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                                          .username("usermane")
                                                                          .passwordRef(SecretRefData.builder().build())
                                                                          .build())
                                                 .build())
                                .build())
            .build();

    List<EncryptedDataDetail> encryptedDataDetails = emptyList();
    TriggerEventDataCondition pathCondition =
        TriggerEventDataCondition.builder().key(CHANGED_FILES).operator(EQUALS).value("test").build();
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .build();
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder().parseWebhookResponse(parseWebhookResponse).build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder().accountId("acc").webhookPayloadData(webhookPayloadData).build();

    doReturn(ConnectorDetails.builder()
                 .connectorConfig(githubConnectorDTO)
                 .connectorType(ConnectorType.GITHUB)
                 .encryptedDataDetails(encryptedDataDetails)
                 .executeOnDelegate(false)
                 .build())
        .when(connectorUtils)
        .getConnectorDetails(any(NGAccess.class), eq("account.conn"));

    when(scmFilePathEvaluatorOnManager.execute(any(), any(), any(), any())).thenCallRealMethod();
    when(scmFilePathEvaluatorOnManager.getScmPathFilterEvaluationTaskParams(any(), any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnManager.getChangedFileset(any(), any(), any()))
        .thenReturn(new HashSet<>(Collections.singletonList("file")));

    when(ConditionEvaluator.evaluate(any(), any(), any())).thenReturn(true);
    assertThat(filter.initiateSCMTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition)).isTrue();
  }

  private TriggerDetails generateTriggerDetails() {
    NGTriggerEntity ngTriggerEntity =
        NGTriggerEntity.builder()
            .identifier("id")
            .accountId("acc")
            .orgIdentifier("org")
            .projectIdentifier("proj")
            .metadata(NGTriggerMetadata.builder()
                          .webhook(WebhookMetadata.builder()
                                       .git(GitMetadata.builder().connectorIdentifier("account.conn").build())
                                       .build())
                          .build())
            .build();
    return TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testInitiateDelegateTaskAndEvaluateForPush() {
    // Init Data
    final String url = "url";
    final String validationRepo = "validationRepo";
    TriggerDetails triggerDetails = generateTriggerDetails();

    final GitlabAuthenticationDTO gitlabAuthenticationDTO =
        GitlabAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(GitlabHttpCredentialsDTO.builder()
                             .type(GitlabHttpAuthenticationType.USERNAME_AND_PASSWORD)
                             .httpCredentialsSpec(GitlabUsernamePasswordDTO.builder()
                                                      .passwordRef(SecretRefHelper.createSecretRef("passwordRef"))
                                                      .username("username")
                                                      .build())
                             .build())
            .build();

    final GitlabApiAccessDTO gitlabApiAccessDTO =
        GitlabApiAccessDTO.builder()
            .type(GitlabApiAccessType.TOKEN)
            .spec(GitlabTokenSpecDTO.builder().tokenRef(SecretRefHelper.createSecretRef("privateKeyRef")).build())
            .build();

    GitlabConnectorDTO gitlabConnectorDTO = GitlabConnectorDTO.builder()
                                                .url(url)
                                                .validationRepo(validationRepo)
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .authentication(gitlabAuthenticationDTO)
                                                .apiAccess(gitlabApiAccessDTO)
                                                .build();
    List<EncryptedDataDetail> encryptedDataDetails = emptyList();
    TriggerEventDataCondition pathCondition =
        TriggerEventDataCondition.builder().key(CHANGED_FILES).operator(EQUALS).value("test").build();
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPush(PushHook.newBuilder().setBefore("before").setAfter("after").setRef("ref").build())
            .build();
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder().parseWebhookResponse(parseWebhookResponse).build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder().accountId("acc").webhookPayloadData(webhookPayloadData).build();

    // Mock apis
    doReturn(ConnectorDetails.builder()
                 .connectorConfig(gitlabConnectorDTO)
                 .orgIdentifier("org")
                 .connectorType(ConnectorType.GITLAB)
                 .projectIdentifier("proj")
                 .encryptedDataDetails(encryptedDataDetails)
                 .build())
        .when(connectorUtils)
        .getConnectorDetails(any(NGAccess.class), eq("account.conn"));

    when(scmFilePathEvaluatorOnDelegate.getScmPathFilterEvaluationTaskParams(any(), any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnDelegate.execute(any(), any(), any(), any())).thenCallRealMethod();

    byte[] data = new byte[0];
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenReturn(BinaryResponseData.builder().data(data).build());
    doReturn(ScmPathFilterEvaluationTaskResponse.builder().matched(true).build())
        .when(kryoSerializer)
        .asInflatedObject(data);
    doReturn(null).when(taskSetupAbstractionHelper).getOwner(any(), any(), any());

    ArgumentCaptor<DelegateTaskRequest> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    assertThat(filter.initiateSCMTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition)).isTrue();
    verify(taskExecutionUtils, times(1)).executeSyncTask(argumentCaptor.capture());

    // Assert Delegate Task request object generated
    DelegateTaskRequest delegateTaskRequest = argumentCaptor.getValue();
    assertThat(delegateTaskRequest.getAccountId()).isEqualTo("acc");
    assertThat(delegateTaskRequest.getTaskType()).isEqualTo(SCM_PATH_FILTER_EVALUATION_TASK.toString());

    assertThat(delegateTaskRequest.getTaskParameters()).isNotNull();

    TaskParameters taskParameters = delegateTaskRequest.getTaskParameters();
    assertThat(ScmPathFilterEvaluationTaskParams.class.isAssignableFrom(taskParameters.getClass()));
    ScmPathFilterEvaluationTaskParams params = (ScmPathFilterEvaluationTaskParams) taskParameters;
    assertThat(params.getScmConnector()).isEqualTo(gitlabConnectorDTO);
    assertThat(params.getEncryptedDataDetails()).isEqualTo(encryptedDataDetails);
    assertThat(params.getPreviousCommit()).isEqualTo("before");
    assertThat(params.getLatestCommit()).isEqualTo("after");
    assertThat(params.getBranch()).isEqualTo("ref");
    assertThat(params.getOperator()).isEqualTo(EQUALS.getValue());
    assertThat(params.getStandard()).isEqualTo("test");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testInitiateDelegateTaskAndEvaluateForPushUsingKryoWithoutReference() {
    // Init Data
    final String url = "url";
    final String validationRepo = "validationRepo";
    TriggerDetails triggerDetails = generateTriggerDetails();

    final GitlabAuthenticationDTO gitlabAuthenticationDTO =
        GitlabAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(GitlabHttpCredentialsDTO.builder()
                             .type(GitlabHttpAuthenticationType.USERNAME_AND_PASSWORD)
                             .httpCredentialsSpec(GitlabUsernamePasswordDTO.builder()
                                                      .passwordRef(SecretRefHelper.createSecretRef("passwordRef"))
                                                      .username("username")
                                                      .build())
                             .build())
            .build();

    final GitlabApiAccessDTO gitlabApiAccessDTO =
        GitlabApiAccessDTO.builder()
            .type(GitlabApiAccessType.TOKEN)
            .spec(GitlabTokenSpecDTO.builder().tokenRef(SecretRefHelper.createSecretRef("privateKeyRef")).build())
            .build();

    GitlabConnectorDTO gitlabConnectorDTO = GitlabConnectorDTO.builder()
                                                .url(url)
                                                .validationRepo(validationRepo)
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .authentication(gitlabAuthenticationDTO)
                                                .apiAccess(gitlabApiAccessDTO)
                                                .build();
    List<EncryptedDataDetail> encryptedDataDetails = emptyList();
    TriggerEventDataCondition pathCondition =
        TriggerEventDataCondition.builder().key(CHANGED_FILES).operator(EQUALS).value("test").build();
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPush(PushHook.newBuilder().setBefore("before").setAfter("after").setRef("ref").build())
            .build();
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder().parseWebhookResponse(parseWebhookResponse).build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder().accountId("acc").webhookPayloadData(webhookPayloadData).build();

    // Mock apis
    doReturn(ConnectorDetails.builder()
                 .connectorConfig(gitlabConnectorDTO)
                 .orgIdentifier("org")
                 .connectorType(ConnectorType.GITLAB)
                 .projectIdentifier("proj")
                 .encryptedDataDetails(encryptedDataDetails)
                 .build())
        .when(connectorUtils)
        .getConnectorDetails(any(NGAccess.class), eq("account.conn"));

    when(scmFilePathEvaluatorOnDelegate.getScmPathFilterEvaluationTaskParams(any(), any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnDelegate.execute(any(), any(), any(), any())).thenCallRealMethod();

    byte[] data = new byte[0];
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenReturn(BinaryResponseData.builder().data(data).usingKryoWithoutReference(true).build());
    doReturn(ScmPathFilterEvaluationTaskResponse.builder().matched(true).build())
        .when(referenceFalseKryoSerializer)
        .asInflatedObject(data);
    doReturn(null).when(taskSetupAbstractionHelper).getOwner(any(), any(), any());

    ArgumentCaptor<DelegateTaskRequest> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    assertThat(filter.initiateSCMTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition)).isTrue();
    verify(taskExecutionUtils, times(1)).executeSyncTask(argumentCaptor.capture());

    // Assert Delegate Task request object generated
    DelegateTaskRequest delegateTaskRequest = argumentCaptor.getValue();
    assertThat(delegateTaskRequest.getAccountId()).isEqualTo("acc");
    assertThat(delegateTaskRequest.getTaskType()).isEqualTo(SCM_PATH_FILTER_EVALUATION_TASK.toString());

    assertThat(delegateTaskRequest.getTaskParameters()).isNotNull();

    TaskParameters taskParameters = delegateTaskRequest.getTaskParameters();
    assertThat(ScmPathFilterEvaluationTaskParams.class.isAssignableFrom(taskParameters.getClass()));
    ScmPathFilterEvaluationTaskParams params = (ScmPathFilterEvaluationTaskParams) taskParameters;
    assertThat(params.getScmConnector()).isEqualTo(gitlabConnectorDTO);
    assertThat(params.getEncryptedDataDetails()).isEqualTo(encryptedDataDetails);
    assertThat(params.getPreviousCommit()).isEqualTo("before");
    assertThat(params.getLatestCommit()).isEqualTo("after");
    assertThat(params.getBranch()).isEqualTo("ref");
    assertThat(params.getOperator()).isEqualTo(EQUALS.getValue());
    assertThat(params.getStandard()).isEqualTo("test");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testInitiateManagerTaskAndEvaluateForPush() {
    TriggerDetails triggerDetails = generateTriggerDetails();

    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .url("http://localhost")
            .apiAccess(GithubApiAccessDTO.builder().spec(GithubTokenSpecDTO.builder().build()).build())
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                                          .username("usermane")
                                                                          .passwordRef(SecretRefData.builder().build())
                                                                          .build())
                                                 .build())
                                .build())
            .build();

    List<EncryptedDataDetail> encryptedDataDetails = emptyList();
    TriggerEventDataCondition pathCondition =
        TriggerEventDataCondition.builder().key(CHANGED_FILES).operator(EQUALS).value("test").build();
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPush(PushHook.newBuilder().setBefore("before").setAfter("after").setRef("ref").build())
            .build();
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder().parseWebhookResponse(parseWebhookResponse).build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder().accountId("acc").webhookPayloadData(webhookPayloadData).build();

    doReturn(ConnectorDetails.builder()
                 .connectorConfig(githubConnectorDTO)
                 .orgIdentifier("org")
                 .connectorType(ConnectorType.GITHUB)
                 .projectIdentifier("proj")
                 .encryptedDataDetails(encryptedDataDetails)
                 .executeOnDelegate(false)
                 .build())
        .when(connectorUtils)
        .getConnectorDetails(any(NGAccess.class), eq("account.conn"));

    when(scmFilePathEvaluatorOnManager.execute(any(), any(), any(), any())).thenCallRealMethod();
    when(scmFilePathEvaluatorOnManager.getScmPathFilterEvaluationTaskParams(any(), any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnManager.getChangedFileset(any(), any(), any()))
        .thenReturn(new HashSet<>(Collections.singletonList("file")));

    when(ConditionEvaluator.evaluate(any(), any(), any())).thenReturn(true);
    assertThat(filter.initiateSCMTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition)).isTrue();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testInitiateTaskAndEvaluateForGithubAPP() {
    TriggerDetails triggerDetails = generateTriggerDetails();

    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .url("http://localhost")
            .apiAccess(GithubApiAccessDTO.builder()
                           .type(GithubApiAccessType.GITHUB_APP)
                           .spec(GithubAppSpecDTO.builder().build())
                           .build())
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                                          .username("usermane")
                                                                          .passwordRef(SecretRefData.builder().build())
                                                                          .build())
                                                 .build())
                                .build())
            .build();

    List<EncryptedDataDetail> encryptedDataDetails = emptyList();
    TriggerEventDataCondition pathCondition =
        TriggerEventDataCondition.builder().key(CHANGED_FILES).operator(EQUALS).value("test").build();
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPush(PushHook.newBuilder().setBefore("before").setAfter("after").setRef("ref").build())
            .build();
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder().parseWebhookResponse(parseWebhookResponse).build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder().accountId("acc").webhookPayloadData(webhookPayloadData).build();

    doReturn(ConnectorDetails.builder()
                 .connectorConfig(githubConnectorDTO)
                 .orgIdentifier("org")
                 .connectorType(ConnectorType.GITHUB)
                 .projectIdentifier("proj")
                 .encryptedDataDetails(encryptedDataDetails)
                 .executeOnDelegate(false)
                 .build())
        .when(connectorUtils)
        .getConnectorDetails(any(NGAccess.class), eq("account.conn"));

    when(scmFilePathEvaluatorOnManager.execute(any(), any(), any(), any())).thenCallRealMethod();
    when(scmFilePathEvaluatorOnManager.getScmPathFilterEvaluationTaskParams(any(), any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnManager.getChangedFileset(any(), any(), any()))
        .thenReturn(new HashSet<>(Collections.singletonList("file")));

    when(ConditionEvaluator.evaluate(any(), any(), any())).thenReturn(true);
    assertThat(filter.initiateSCMTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition)).isTrue();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void evaluateFromPushPayload() {
    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload(pushPayload).sourceRepoType("Github").build();

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .webhookPayloadData(WebhookPayloadData.builder().originalEvent(triggerWebhookEvent).build())
            .build();

    TriggerEventDataCondition condition =
        TriggerEventDataCondition.builder().key(CHANGED_FILES).operator(EQUALS).value("spec/manifest1.yml").build();
    assertThat(filter.evaluateFromPushPayload(filterRequestData, condition)).isTrue();

    condition.setOperator(REGEX);
    condition.setValue("(^spec/manifest)[0-9](.yml1$)");
    assertThat(filter.evaluateFromPushPayload(filterRequestData, condition)).isFalse();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void shouldEvaluateOnSCM() {
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder().setPr(PullRequestHook.newBuilder().build()).build();
    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload("").sourceRepoType("Github").build();

    WebhookPayloadData webhookPayloadData = WebhookPayloadData.builder()
                                                .originalEvent(triggerWebhookEvent)
                                                .parseWebhookResponse(parseWebhookResponse)
                                                .build();
    FilterRequestData filterRequestData = FilterRequestData.builder().webhookPayloadData(webhookPayloadData).build();

    // PR
    assertThat(filter.shouldEvaluateOnSCM(filterRequestData)).isTrue();

    // Push Github , commits < 20
    List<Commit> commits = Arrays.asList(Commit.newBuilder().build(), Commit.newBuilder().build());
    parseWebhookResponse =
        ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().addAllCommits(commits).build()).build();
    webhookPayloadData = WebhookPayloadData.builder()
                             .originalEvent(triggerWebhookEvent)
                             .parseWebhookResponse(parseWebhookResponse)
                             .build();
    filterRequestData = FilterRequestData.builder().webhookPayloadData(webhookPayloadData).build();
    assertThat(filter.shouldEvaluateOnSCM(filterRequestData)).isFalse();

    // Push gitlab , commits < 20
    parseWebhookResponse =
        ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().addAllCommits(commits).build()).build();
    triggerWebhookEvent.setSourceRepoType("Gitlab");
    webhookPayloadData = WebhookPayloadData.builder()
                             .originalEvent(triggerWebhookEvent)
                             .parseWebhookResponse(parseWebhookResponse)
                             .build();
    filterRequestData = FilterRequestData.builder().webhookPayloadData(webhookPayloadData).build();
    assertThat(filter.shouldEvaluateOnSCM(filterRequestData)).isFalse();

    // Push Bitbucket , commits < 20
    commits = emptyList();
    parseWebhookResponse =
        ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().addAllCommits(commits).build()).build();
    triggerWebhookEvent.setSourceRepoType("Bitbucket");
    webhookPayloadData = WebhookPayloadData.builder()
                             .originalEvent(triggerWebhookEvent)
                             .parseWebhookResponse(parseWebhookResponse)
                             .build();
    filterRequestData = FilterRequestData.builder().webhookPayloadData(webhookPayloadData).build();
    assertThat(filter.shouldEvaluateOnSCM(filterRequestData)).isTrue();

    commits = new ArrayList<>();
    Commit commit = Commit.newBuilder().build();
    for (int i = 0; i < 20; i++) {
      commits.add(commit);
    }
    // Push Github , commits > 20
    parseWebhookResponse =
        ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().addAllCommits(commits).build()).build();
    triggerWebhookEvent.setSourceRepoType("github");
    webhookPayloadData = WebhookPayloadData.builder()
                             .originalEvent(triggerWebhookEvent)
                             .parseWebhookResponse(parseWebhookResponse)
                             .build();
    filterRequestData = FilterRequestData.builder().webhookPayloadData(webhookPayloadData).build();
    assertThat(filter.shouldEvaluateOnSCM(filterRequestData)).isFalse();

    // Push gitlab , commits > 20
    parseWebhookResponse =
        ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().addAllCommits(commits).build()).build();
    triggerWebhookEvent.setSourceRepoType("Gitlab");
    webhookPayloadData = WebhookPayloadData.builder()
                             .originalEvent(triggerWebhookEvent)
                             .parseWebhookResponse(parseWebhookResponse)
                             .build();
    filterRequestData = FilterRequestData.builder().webhookPayloadData(webhookPayloadData).build();
    assertThat(filter.shouldEvaluateOnSCM(filterRequestData)).isTrue();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetFilesFromPushPayload() {
    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload(pushPayload).sourceRepoType("Github").build();
    Set<String> filesFromPushPayload = filter.getFilesFromPushPayload(
        FilterRequestData.builder()
            .webhookPayloadData(WebhookPayloadData.builder().originalEvent(triggerWebhookEvent).build())
            .build());

    assertThat(filesFromPushPayload)
        .containsExactlyInAnyOrder("spec/manifest1.yml", "spec/manifest2.yml", "File1_Removed.txt", "File2_Removed.txt",
            "values/value1.yml", "values/value2.yml");
    triggerWebhookEvent.setSourceRepoType("GITHUB");
    assertThat(filesFromPushPayload)
        .containsExactlyInAnyOrder("spec/manifest1.yml", "spec/manifest2.yml", "File1_Removed.txt", "File2_Removed.txt",
            "values/value1.yml", "values/value2.yml");
    triggerWebhookEvent.setSourceRepoType("Gitlab");
    assertThat(filesFromPushPayload)
        .containsExactlyInAnyOrder("spec/manifest1.yml", "spec/manifest2.yml", "File1_Removed.txt", "File2_Removed.txt",
            "values/value1.yml", "values/value2.yml");
  }
}
