/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTaskParams;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTaskResponse;
import io.harness.encryption.SecretRefData;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.ngtriggers.conditionchecker.ConditionOperator;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.product.ci.scm.proto.CompareCommitsResponse;
import io.harness.product.ci.scm.proto.PRFile;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.rule.Owner;
import io.harness.secrets.SecretDecryptor;
import io.harness.service.ScmServiceClient;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class SCMFilePathEvaluatorOnManagerTest extends CategoryTest {
  @Mock private SecretDecryptor secretDecryptor;
  @Mock private SCMGrpc.SCMBlockingStub scmBlockingStub;
  @Mock private ScmServiceClient scmServiceClient;
  @InjectMocks private SCMFilePathEvaluatorOnManager scmFilePathEvaluatorOnManager;

  @Before
  public void setUp() throws IOException {
    initMocks(this);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetChangedFilesetForBitbucketOnPrem() {
    BitbucketConnectorDTO scmConnector =
        BitbucketConnectorDTO.builder().url("https://on-prem-url.com/bitbucket-repo.git").build();
    ScmPathFilterEvaluationTaskParams params = ScmPathFilterEvaluationTaskParams.builder()
                                                   .latestCommit("latestCommit")
                                                   .previousCommit("previousCommit")
                                                   .build();
    String connectorIdentifier = "connector";
    when(scmServiceClient.compareCommits(any(), any(), any(), any()))
        .thenReturn(
            CompareCommitsResponse.newBuilder().addFiles(PRFile.newBuilder().setPath("file1.txt").build()).build());
    Set<String> changedFiles =
        scmFilePathEvaluatorOnManager.getChangedFileset(params, scmConnector, connectorIdentifier);
    verify(scmServiceClient, times(1)).compareCommits(scmConnector, "latestCommit", "previousCommit", scmBlockingStub);
    assertThat(changedFiles.size()).isEqualTo(1);
    assertThat(changedFiles.contains("file1.txt")).isTrue();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetChangedFilesetNotForBitbucketOnPrem() {
    GithubConnectorDTO scmConnector = GithubConnectorDTO.builder().url("https://github.com/user/repo.git").build();
    ScmPathFilterEvaluationTaskParams params = ScmPathFilterEvaluationTaskParams.builder()
                                                   .latestCommit("latestCommit")
                                                   .previousCommit("previousCommit")
                                                   .build();
    String connectorIdentifier = "connector";
    when(scmServiceClient.compareCommits(any(), any(), any(), any()))
        .thenReturn(
            CompareCommitsResponse.newBuilder().addFiles(PRFile.newBuilder().setPath("file1.txt").build()).build());
    Set<String> changedFiles =
        scmFilePathEvaluatorOnManager.getChangedFileset(params, scmConnector, connectorIdentifier);
    verify(scmServiceClient, times(1)).compareCommits(scmConnector, "previousCommit", "latestCommit", scmBlockingStub);
    assertThat(changedFiles.size()).isEqualTo(1);
    assertThat(changedFiles.contains("file1.txt")).isTrue();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testDecrypt() {
    GithubTokenSpecDTO githubTokenSpec =
        GithubTokenSpecDTO.builder().tokenRef(SecretRefData.builder().identifier("token").build()).build();
    GithubConnectorDTO scmConnector = GithubConnectorDTO.builder()
                                          .url("https://github.com/user/repo.git")
                                          .apiAccess(GithubApiAccessDTO.builder().spec(githubTokenSpec).build())
                                          .build();
    when(secretDecryptor.decrypt(any(), any())).thenReturn(githubTokenSpec);
    scmFilePathEvaluatorOnManager.decrypt(scmConnector, Collections.emptyList());
    verify(secretDecryptor, times(1)).decrypt(any(), any());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testExecute() {
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
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
    TriggerEventDataCondition triggerEventDataCondition =
        TriggerEventDataCondition.builder().value("file.*\\.txt").operator(ConditionOperator.IN).build();
    GithubTokenSpecDTO githubTokenSpec =
        GithubTokenSpecDTO.builder().tokenRef(SecretRefData.builder().identifier("token").build()).build();
    GithubConnectorDTO scmConnector = GithubConnectorDTO.builder()
                                          .url("https://github.com/user/repo.git")
                                          .apiAccess(GithubApiAccessDTO.builder().spec(githubTokenSpec).build())
                                          .build();
    when(secretDecryptor.decrypt(any(), any())).thenReturn(githubTokenSpec);
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .identifier("connector")
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder().connectionType(GitConnectionType.REPO).url("url").build())
            .executeOnDelegate(true)
            .build();
    when(scmServiceClient.compareCommits(any(), any(), any(), any()))
        .thenReturn(
            CompareCommitsResponse.newBuilder().addFiles(PRFile.newBuilder().setPath("file1.txt").build()).build());
    ScmPathFilterEvaluationTaskResponse response = scmFilePathEvaluatorOnManager.execute(
        filterRequestData, triggerEventDataCondition, connectorDetails, scmConnector);
    assertThat(response.isMatched()).isTrue();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testIsBitbucketOnPremTrue() {
    BitbucketConnectorDTO scmConnector =
        BitbucketConnectorDTO.builder().url("https://on-prem-url.com/bitbucket-repo.git").build();
    assertThat(scmFilePathEvaluatorOnManager.isBitBucketOnPrem(scmConnector)).isTrue();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testIsBitbucketOnPremFalseWithGithub() {
    GithubConnectorDTO scmConnector = GithubConnectorDTO.builder().url("https://github.com/user/repo.git").build();
    assertThat(scmFilePathEvaluatorOnManager.isBitBucketOnPrem(scmConnector)).isFalse();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testIsBitbucketOnPremFalseWithBitbucketCloud() {
    BitbucketConnectorDTO scmConnector =
        BitbucketConnectorDTO.builder().url("https://bitbucket.org/project/bitbucket-repo.git").build();
    assertThat(scmFilePathEvaluatorOnManager.isBitBucketOnPrem(scmConnector)).isFalse();
  }
}
