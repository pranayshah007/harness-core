/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.gitapi.client.impl;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.cistatus.service.GithubServiceImpl;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.task.gitpolling.github.GitHubPollingDelegateRequest;
import io.harness.exception.InvalidRequestException;
import io.harness.gitpolling.github.GitPollingWebhookData;
import io.harness.security.encryption.SecretDecryptionService;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class GithubApiClientTest {
  private final String GITHUB_WEBHOOK_URL = "https://api.github.com/";

  private final String TOKEN = "t3stT0keN";

  private final String REPO_NAME = "testRepo";

  private final String REPO_OWNER = "owner";

  GithubApiClient githubApiClient;
  GithubServiceImpl githubService;

  SecretDecryptionService secretDecryptionService;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Category(UnitTests.class)
  public void testGetWebhookRecentDeliveryEvents() {
    GitHubPollingDelegateRequest gitHubPollingDelegateRequest =
        GitHubPollingDelegateRequest.builder()
            .webhookId("1234")
            .connectorDetails(
                ConnectorDetails.builder()
                    .connectorConfig(
                        GithubConnectorDTO.builder()
                            .connectionType(GitConnectionType.REPO)
                            .url("https://github.com/wings-software/harness-core")
                            .apiAccess(GithubApiAccessDTO.builder().type(GithubApiAccessType.TOKEN).build())
                            .build())
                    .build())
            .build();

    githubService = mock(GithubServiceImpl.class);
    secretDecryptionService = mock(SecretDecryptionService.class);
    githubApiClient = new GithubApiClient(githubService, secretDecryptionService);
    githubApiClient = spy(githubApiClient);

    List<GitPollingWebhookData> webhookData = Arrays.asList(GitPollingWebhookData.builder().deliveryId("999").build());
    when(githubService.getWebhookRecentDeliveryEvents(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(webhookData);
    Mockito.doReturn(TOKEN).when(githubApiClient).retrieveAuthToken(any());
    List<GitPollingWebhookData> result = githubApiClient.getWebhookRecentDeliveryEvents(gitHubPollingDelegateRequest);
    assertEquals(result.size(), 1);
  }

  @Test(expected = InvalidRequestException.class)
  @Category(UnitTests.class)
  public void testGetWebhookRecentDeliveryEventsException() {
    GitHubPollingDelegateRequest gitHubPollingDelegateRequest =
        GitHubPollingDelegateRequest.builder().webhookId("1234").connectorDetails(null).build();

    githubService = mock(GithubServiceImpl.class);
    secretDecryptionService = mock(SecretDecryptionService.class);
    githubApiClient = new GithubApiClient(githubService, secretDecryptionService);
    githubApiClient = spy(githubApiClient);

    List<GitPollingWebhookData> webhookData = Arrays.asList(GitPollingWebhookData.builder().deliveryId("999").build());
    when(githubService.getWebhookRecentDeliveryEvents(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(webhookData);
    Mockito.doReturn(TOKEN).when(githubApiClient).retrieveAuthToken(any());
    githubApiClient.getWebhookRecentDeliveryEvents(gitHubPollingDelegateRequest);
  }
}
