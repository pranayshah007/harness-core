/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.beans.DelegateTaskRequest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.task.scm.ScmGitRefTaskResponseData;
import io.harness.encryption.SecretRefData;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.ListCommitsInPRResponse;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.rule.Owner;
import io.harness.secrets.SecretDecryptor;
import io.harness.serializer.KryoSerializer;
import io.harness.service.ScmServiceClient;
import io.harness.tasks.BinaryResponseData;
import io.harness.utils.ConnectorUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class SCMDataObtainerTest extends CategoryTest {
  @Mock SecretDecryptor secretDecryptor;
  @Mock ScmServiceClient scmServiceClient;
  @Mock TaskExecutionUtils taskExecutionUtils;
  @Mock KryoSerializer kryoSerializer;
  @Mock KryoSerializer referenceFalseKryoSerializer;
  @Mock ConnectorUtils connectorUtils;
  @InjectMocks SCMDataObtainer scmDataObtainer;

  @Before
  public void setUp() throws IOException {
    initMocks(this);
    on(scmDataObtainer).set("kryoSerializer", kryoSerializer);
    on(scmDataObtainer).set("referenceFalseKryoSerializer", referenceFalseKryoSerializer);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testAcquireProviderData() {
    SCMDataObtainer spyScmDataObtainer =
        spy(new SCMDataObtainer(taskExecutionUtils, connectorUtils, kryoSerializer, referenceFalseKryoSerializer));
    List<TriggerDetails> triggers = Collections.emptyList();
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(
                        TriggerWebhookEvent.builder().sourceRepoType(WebhookSourceRepo.GITHUB.name()).build())
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder().setPr(PullRequestHook.newBuilder().build()).build())
                    .build())

            .build();
    doNothing().when(spyScmDataObtainer).acquirePullRequestCommits(any(), any());
    spyScmDataObtainer.acquireProviderData(filterRequestData, triggers);
    verify(spyScmDataObtainer, times(1)).acquirePullRequestCommits(filterRequestData, triggers);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetGitURL() {
    String gitURL = scmDataObtainer.getGitURL(GitConnectionType.ACCOUNT, "url", "repo_name");
    assertThat(gitURL).isEqualTo("url/repo_name.git");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testRetrieveGenericGitConnectorURL() {
    String genericGitConnectorURL =
        scmDataObtainer.retrieveGenericGitConnectorURL("repo_name", GitConnectionType.ACCOUNT, "url");
    assertThat(genericGitConnectorURL).isEqualTo("url/repo_name");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testAcquirePullRequestCommits() {
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(
                        TriggerWebhookEvent.builder().sourceRepoType(WebhookSourceRepo.GITHUB.name()).build())
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder().setPr(PullRequestHook.newBuilder().build()).build())
                    .build())

            .build();
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).build())
                                .build())
                    .build())
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("account")
                    .metadata(NGTriggerMetadata.builder()
                                  .webhook(WebhookMetadata.builder()
                                               .git(GitMetadata.builder().connectorIdentifier("connector").build())
                                               .build())
                                  .build())
                    .build())
            .build();
    List<TriggerDetails> triggers = Collections.singletonList(triggerDetails);
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder().connectionType(GitConnectionType.REPO).url("url").build())
            .executeOnDelegate(true)
            .build();
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(connectorDetails);
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenReturn(BinaryResponseData.builder().build());

    byte[] list = ListCommitsInPRResponse.newBuilder()
                      .addCommits(Commit.newBuilder()
                                      .setSha("commitId")
                                      .setMessage("message")
                                      .setLink("http://github.com/octocat/hello-world/pull/1/commits/commitId")
                                      .build())
                      .build()
                      .toByteArray();
    when(kryoSerializer.asInflatedObject(any()))
        .thenReturn(ScmGitRefTaskResponseData.builder().listCommitsInPRResponse(list).build());
    scmDataObtainer.acquirePullRequestCommits(filterRequestData, triggers);
    assertThat(
        filterRequestData.getWebhookPayloadData().getParseWebhookResponse().getPr().getPr().getCommitsList().size())
        .isEqualTo(1);
    assertThat(
        filterRequestData.getWebhookPayloadData().getParseWebhookResponse().getPr().getPr().getCommits(0).getSha())
        .isEqualTo("commitId");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetCommitsInPrViaDelegate() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).build())
                                .build())
                    .build())
            .ngTriggerEntity(NGTriggerEntity.builder().accountId("account").build())
            .build();

    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder().connectionType(GitConnectionType.REPO).url("url").build())
            .executeOnDelegate(true)
            .build();

    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenReturn(BinaryResponseData.builder().build());

    byte[] list = ListCommitsInPRResponse.newBuilder()
                      .addCommits(Commit.newBuilder()
                                      .setSha("commitId")
                                      .setMessage("message")
                                      .setLink("http://github.com/octocat/hello-world/pull/1/commits/commitId")
                                      .build())
                      .build()
                      .toByteArray();
    when(kryoSerializer.asInflatedObject(any()))
        .thenReturn(ScmGitRefTaskResponseData.builder().listCommitsInPRResponse(list).build());

    List<Commit> commits = scmDataObtainer.getCommitsInPr(connectorDetails, triggerDetails, 3);
    assertThat(commits.size()).isEqualTo(1);
    assertThat(commits.get(0).getSha()).isEqualTo("commitId");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetCommitsInPrViaDelegateUsingKryoWithoutReference() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).build())
                                .build())
                    .build())
            .ngTriggerEntity(NGTriggerEntity.builder().accountId("account").build())
            .build();

    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder().connectionType(GitConnectionType.REPO).url("url").build())
            .executeOnDelegate(true)
            .build();

    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenReturn(BinaryResponseData.builder().usingKryoWithoutReference(true).build());

    byte[] list = ListCommitsInPRResponse.newBuilder()
                      .addCommits(Commit.newBuilder()
                                      .setSha("commitId")
                                      .setMessage("message")
                                      .setLink("http://github.com/octocat/hello-world/pull/1/commits/commitId")
                                      .build())
                      .build()
                      .toByteArray();
    when(referenceFalseKryoSerializer.asInflatedObject(any()))
        .thenReturn(ScmGitRefTaskResponseData.builder().listCommitsInPRResponse(list).build());

    List<Commit> commits = scmDataObtainer.getCommitsInPr(connectorDetails, triggerDetails, 3);
    assertThat(commits.size()).isEqualTo(1);
    assertThat(commits.get(0).getSha()).isEqualTo("commitId");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetCommitsInPrViaGithubApp() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).build())
                                .build())
                    .build())
            .ngTriggerEntity(NGTriggerEntity.builder().accountId("account").build())
            .build();

    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.REPO)
                                 .url("url")
                                 .apiAccess(GithubApiAccessDTO.builder()
                                                .type(GithubApiAccessType.GITHUB_APP)
                                                .spec(GithubAppSpecDTO.builder().build())
                                                .build())
                                 .build())
            .executeOnDelegate(false)
            .build();

    ListCommitsInPRResponse list =
        ListCommitsInPRResponse.newBuilder()
            .addCommits(Commit.newBuilder()
                            .setSha("commitId")
                            .setMessage("message")
                            .setLink("http://github.com/octocat/hello-world/pull/1/commits/commitId")
                            .build())
            .build();
    when(scmServiceClient.listCommitsInPR(any(), anyLong(), any())).thenReturn(list);

    List<Commit> commits = scmDataObtainer.getCommitsInPr(connectorDetails, triggerDetails, 3);
    assertThat(commits.size()).isEqualTo(1);
    assertThat(commits.get(0).getSha()).isEqualTo("commitId");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetCommitsInPrViaManager() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).build())
                                .build())
                    .build())
            .ngTriggerEntity(NGTriggerEntity.builder().accountId("account").build())
            .build();

    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.REPO)
                                 .url("url")
                                 .apiAccess(GithubApiAccessDTO.builder()
                                                .type(GithubApiAccessType.TOKEN)
                                                .spec(GithubTokenSpecDTO.builder()
                                                          .tokenRef(SecretRefData.builder().identifier("token").build())
                                                          .build())
                                                .build())
                                 .build())
            .executeOnDelegate(false)
            .build();

    ListCommitsInPRResponse list =
        ListCommitsInPRResponse.newBuilder()
            .addCommits(Commit.newBuilder()
                            .setSha("commitId")
                            .setMessage("message")
                            .setLink("http://github.com/octocat/hello-world/pull/1/commits/commitId")
                            .build())
            .build();
    when(scmServiceClient.listCommitsInPR(any(), anyLong(), any())).thenReturn(list);

    List<Commit> commits = scmDataObtainer.getCommitsInPr(connectorDetails, triggerDetails, 3);
    assertThat(commits.size()).isEqualTo(1);
    assertThat(commits.get(0).getSha()).isEqualTo("commitId");
  }
}
