/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.beans.DelegateTaskRequest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTaskParams;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTaskResponse;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.encryption.SecretRefData;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.ngtriggers.conditionchecker.ConditionOperator;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.BinaryResponseData;

import software.wings.beans.TaskType;

import java.io.IOException;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class SCMFilePathEvaluatorOnDelegateTest extends CategoryTest {
  @Mock private TaskExecutionUtils taskExecutionUtils;
  @Mock private KryoSerializer kryoSerializer;
  private SCMFilePathEvaluatorOnDelegate scmFilePathEvaluatorOnDelegate;

  private FilterRequestData filterRequestData;
  private GithubTokenSpecDTO githubTokenSpec;
  private TriggerEventDataCondition triggerEventDataCondition;

  @Before
  public void setUp() throws IOException {
    initMocks(this);
    TaskSetupAbstractionHelper taskSetupAbstractionHelper = new TaskSetupAbstractionHelper();
    scmFilePathEvaluatorOnDelegate = new SCMFilePathEvaluatorOnDelegate(
        taskExecutionUtils, kryoSerializer, kryoSerializer, taskSetupAbstractionHelper);
    githubTokenSpec =
        GithubTokenSpecDTO.builder().tokenRef(SecretRefData.builder().identifier("token").build()).build();
    triggerEventDataCondition =
        TriggerEventDataCondition.builder().value("file.*\\.txt").operator(ConditionOperator.IN).build();
    filterRequestData =
        FilterRequestData.builder()
            .accountId("account")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(
                        TriggerWebhookEvent.builder().sourceRepoType(WebhookSourceRepo.GITHUB.name()).build())
                    .parseWebhookResponse(ParseWebhookResponse.newBuilder()
                                              .setPush(PushHook.newBuilder()
                                                           .setAfter("latestCommit")
                                                           .setBefore("previousCommit")
                                                           .setRef("branch")
                                                           .build())
                                              .build())
                    .build())

            .build();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testExecuteWithProjectLevelConnector() {
    GithubConnectorDTO scmConnector = GithubConnectorDTO.builder()
                                          .url("https://github.com/user/repo.git")
                                          .apiAccess(GithubApiAccessDTO.builder().spec(githubTokenSpec).build())
                                          .executeOnDelegate(true)
                                          .build();
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .identifier("connector")
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder().connectionType(GitConnectionType.REPO).url("url").build())
            .executeOnDelegate(true)
            .orgIdentifier("org")
            .projectIdentifier("proj")
            .build();
    when(taskExecutionUtils.executeSyncTask(any())).thenReturn(BinaryResponseData.builder().build());
    when(kryoSerializer.asInflatedObject(any())).thenReturn(ScmPathFilterEvaluationTaskResponse.builder().build());
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequest = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    scmFilePathEvaluatorOnDelegate.execute(
        filterRequestData, triggerEventDataCondition, connectorDetails, scmConnector);
    verify(taskExecutionUtils, times(1)).executeSyncTask(delegateTaskRequest.capture());
    assertThat(delegateTaskRequest.getValue().getAccountId()).isEqualTo("account");
    assertThat(delegateTaskRequest.getValue().getTaskType())
        .isEqualTo(TaskType.SCM_PATH_FILTER_EVALUATION_TASK.toString());
    assertThat(delegateTaskRequest.getValue().getExecutionTimeout()).isEqualTo(Duration.ofMinutes(1l));
    assertThat(delegateTaskRequest.getValue().getTaskSetupAbstractions().get(NG)).isEqualTo("true");
    assertThat(delegateTaskRequest.getValue().getTaskSetupAbstractions().get(OWNER)).isEqualTo("org/proj");
    assertThat(((ScmPathFilterEvaluationTaskParams) delegateTaskRequest.getValue().getTaskParameters()).getBranch())
        .isEqualTo("branch");
    assertThat(
        ((ScmPathFilterEvaluationTaskParams) delegateTaskRequest.getValue().getTaskParameters()).getPreviousCommit())
        .isEqualTo("previousCommit");
    assertThat(
        ((ScmPathFilterEvaluationTaskParams) delegateTaskRequest.getValue().getTaskParameters()).getLatestCommit())
        .isEqualTo("latestCommit");
    assertThat(((ScmPathFilterEvaluationTaskParams) delegateTaskRequest.getValue().getTaskParameters()).getOperator())
        .isEqualTo(ConditionOperator.IN.getValue());
    assertThat(((ScmPathFilterEvaluationTaskParams) delegateTaskRequest.getValue().getTaskParameters()).getStandard())
        .isEqualTo("file.*\\.txt");
    assertThat(
        ((ScmPathFilterEvaluationTaskParams) delegateTaskRequest.getValue().getTaskParameters()).getScmConnector())
        .isEqualTo(scmConnector);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testExecuteWithOrgLevelConnector() {
    GithubConnectorDTO scmConnector = GithubConnectorDTO.builder()
                                          .url("https://github.com/user/repo.git")
                                          .apiAccess(GithubApiAccessDTO.builder().spec(githubTokenSpec).build())
                                          .executeOnDelegate(true)
                                          .build();
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .identifier("connector")
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder().connectionType(GitConnectionType.REPO).url("url").build())
            .executeOnDelegate(true)
            .orgIdentifier("org")
            .build();
    when(taskExecutionUtils.executeSyncTask(any())).thenReturn(BinaryResponseData.builder().build());
    when(kryoSerializer.asInflatedObject(any())).thenReturn(ScmPathFilterEvaluationTaskResponse.builder().build());
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequest = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    scmFilePathEvaluatorOnDelegate.execute(
        filterRequestData, triggerEventDataCondition, connectorDetails, scmConnector);
    verify(taskExecutionUtils, times(1)).executeSyncTask(delegateTaskRequest.capture());
    assertThat(delegateTaskRequest.getValue().getAccountId()).isEqualTo("account");
    assertThat(delegateTaskRequest.getValue().getTaskType())
        .isEqualTo(TaskType.SCM_PATH_FILTER_EVALUATION_TASK.toString());
    assertThat(delegateTaskRequest.getValue().getExecutionTimeout()).isEqualTo(Duration.ofMinutes(1l));
    assertThat(delegateTaskRequest.getValue().getTaskSetupAbstractions().get(NG)).isEqualTo("true");
    assertThat(delegateTaskRequest.getValue().getTaskSetupAbstractions().get(OWNER)).isEqualTo("org");
    assertThat(((ScmPathFilterEvaluationTaskParams) delegateTaskRequest.getValue().getTaskParameters()).getBranch())
        .isEqualTo("branch");
    assertThat(
        ((ScmPathFilterEvaluationTaskParams) delegateTaskRequest.getValue().getTaskParameters()).getPreviousCommit())
        .isEqualTo("previousCommit");
    assertThat(
        ((ScmPathFilterEvaluationTaskParams) delegateTaskRequest.getValue().getTaskParameters()).getLatestCommit())
        .isEqualTo("latestCommit");
    assertThat(((ScmPathFilterEvaluationTaskParams) delegateTaskRequest.getValue().getTaskParameters()).getOperator())
        .isEqualTo(ConditionOperator.IN.getValue());
    assertThat(((ScmPathFilterEvaluationTaskParams) delegateTaskRequest.getValue().getTaskParameters()).getStandard())
        .isEqualTo("file.*\\.txt");
    assertThat(
        ((ScmPathFilterEvaluationTaskParams) delegateTaskRequest.getValue().getTaskParameters()).getScmConnector())
        .isEqualTo(scmConnector);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testExecuteWithAccountLevelConnector() {
    GithubConnectorDTO scmConnector = GithubConnectorDTO.builder()
                                          .url("https://github.com/user/repo.git")
                                          .apiAccess(GithubApiAccessDTO.builder().spec(githubTokenSpec).build())
                                          .executeOnDelegate(true)
                                          .build();
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .identifier("connector")
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder().connectionType(GitConnectionType.REPO).url("url").build())
            .executeOnDelegate(true)
            .build();
    when(taskExecutionUtils.executeSyncTask(any())).thenReturn(BinaryResponseData.builder().build());
    when(kryoSerializer.asInflatedObject(any())).thenReturn(ScmPathFilterEvaluationTaskResponse.builder().build());
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequest = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    scmFilePathEvaluatorOnDelegate.execute(
        filterRequestData, triggerEventDataCondition, connectorDetails, scmConnector);
    verify(taskExecutionUtils, times(1)).executeSyncTask(delegateTaskRequest.capture());
    assertThat(delegateTaskRequest.getValue().getAccountId()).isEqualTo("account");
    assertThat(delegateTaskRequest.getValue().getTaskType())
        .isEqualTo(TaskType.SCM_PATH_FILTER_EVALUATION_TASK.toString());
    assertThat(delegateTaskRequest.getValue().getExecutionTimeout()).isEqualTo(Duration.ofMinutes(1l));
    assertThat(delegateTaskRequest.getValue().getTaskSetupAbstractions().get(NG)).isEqualTo("true");
    assertThat(delegateTaskRequest.getValue().getTaskSetupAbstractions().containsKey(OWNER)).isFalse();
    assertThat(((ScmPathFilterEvaluationTaskParams) delegateTaskRequest.getValue().getTaskParameters()).getBranch())
        .isEqualTo("branch");
    assertThat(
        ((ScmPathFilterEvaluationTaskParams) delegateTaskRequest.getValue().getTaskParameters()).getPreviousCommit())
        .isEqualTo("previousCommit");
    assertThat(
        ((ScmPathFilterEvaluationTaskParams) delegateTaskRequest.getValue().getTaskParameters()).getLatestCommit())
        .isEqualTo("latestCommit");
    assertThat(((ScmPathFilterEvaluationTaskParams) delegateTaskRequest.getValue().getTaskParameters()).getOperator())
        .isEqualTo(ConditionOperator.IN.getValue());
    assertThat(((ScmPathFilterEvaluationTaskParams) delegateTaskRequest.getValue().getTaskParameters()).getStandard())
        .isEqualTo("file.*\\.txt");
    assertThat(
        ((ScmPathFilterEvaluationTaskParams) delegateTaskRequest.getValue().getTaskParameters()).getScmConnector())
        .isEqualTo(scmConnector);
  }
}
