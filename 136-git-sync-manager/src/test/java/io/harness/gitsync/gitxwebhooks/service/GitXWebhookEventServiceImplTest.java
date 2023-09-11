/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.service;

import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookTriggerType.GIT;
import static io.harness.rule.OwnerRule.ADITHYA;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhookEvent;
import io.harness.gitsync.gitxwebhooks.runnable.FetchFilesFromGitHelper;
import io.harness.repositories.gitxwebhook.GitXWebhookEventsRepository;
import io.harness.repositories.gitxwebhook.GitXWebhookRepository;
import io.harness.rule.Owner;

import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class GitXWebhookEventServiceImplTest extends GitSyncTestBase {
  @InjectMocks GitXWebhookEventServiceImpl gitXWebhookEventService;
  @Mock GitXWebhookEventsRepository gitXWebhookEventsRepository;
  @Mock GitXWebhookRepository gitXWebhookRepository;
  @Mock GitXWebhookEventHelper gitXWebhookEventHelper;

  @Mock FetchFilesFromGitHelper fetchFilesFromGitHelper;
  private static final String ACCOUNT_IDENTIFIER = "accountId";
  private static final String WEBHOOK_IDENTIFIER = "gitWebhook";

  private ConnectorInfoDTO connectorInfo;
  ;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    GithubConnectorDTO githubConnector = GithubConnectorDTO.builder()
                                             .connectionType(GitConnectionType.ACCOUNT)
                                             .apiAccess(GithubApiAccessDTO.builder().build())
                                             .url("https://github.com/")
                                             .build();
    connectorInfo = ConnectorInfoDTO.builder().connectorConfig(githubConnector).build();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testProcessEventForSuccessfulMatch() {
    WebhookEventType eventType = WebhookEventType.PUSH;
    WebhookDTO webhookDTO = WebhookDTO.newBuilder()
                                .setAccountId(ACCOUNT_IDENTIFIER)
                                .setWebhookEventType(eventType)
                                .setWebhookTriggerType(GIT)
                                .build();

    GitXWebhookEvent gitXWebhookEvent = GitXWebhookEvent.builder().build();
    GitXWebhook gitXWebhook = GitXWebhook.builder().identifier(WEBHOOK_IDENTIFIER).isEnabled(true).build();
    when(gitXWebhookRepository.findByAccountIdentifierAndRepoName(any(), any())).thenReturn(gitXWebhook);
    when(gitXWebhookEventsRepository.create(any())).thenReturn(gitXWebhookEvent);

    when(gitXWebhookEventHelper.getScmConnector(any(), any(), any()))
        .thenReturn((ScmConnector) connectorInfo.getConnectorConfig());
    String modifiedFilePath = "/.harness/testPipeline.yaml";
    when(gitXWebhookEventHelper.getDiffFilesUsingSCM(any(), any(), any(), any()))
        .thenReturn(Arrays.asList(modifiedFilePath));

    gitXWebhookEventService.processEvent(webhookDTO);
    verify(gitXWebhookEventsRepository, times(1)).create(any());
    verify(gitXWebhookEventHelper, times(1)).getDiffFilesUsingSCM(any(), any(), any(), any());
    verify(gitXWebhookRepository, times(1)).findByAccountIdentifierAndRepoName(any(), any());
  }
}
