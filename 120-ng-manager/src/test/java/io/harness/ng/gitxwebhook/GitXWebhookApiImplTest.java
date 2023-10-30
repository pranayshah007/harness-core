/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitxwebhook;

import static io.harness.rule.OwnerRule.ADITHYA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.gitxwebhooks.dtos.CreateGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.DeleteGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GetGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventsListResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.ListGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookEventService;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookService;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.CreateGitXWebhookRequest;
import io.harness.spec.server.ng.v1.model.CreateGitXWebhookResponse;
import io.harness.spec.server.ng.v1.model.GitXWebhookEventResponse;
import io.harness.spec.server.ng.v1.model.GitXWebhookResponse;
import io.harness.spec.server.ng.v1.model.UpdateGitXWebhookRequest;
import io.harness.spec.server.ng.v1.model.UpdateGitXWebhookResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class GitXWebhookApiImplTest extends CategoryTest {
  private GitXWebhooksApiImpl gitXWebhooksApi;
  @Mock GitXWebhookService gitXWebhookService;
  @Mock GitXWebhookEventService gitXWebhookEventService;

  private static final String ACCOUNT_IDENTIFIER = "accountId";
  private static final String WEBHOOK_IDENTIFIER = "gitWebhook";
  private static final String WEBHOOK_IDENTIFIER2 = "gitWebhook2";
  private static final String CONNECTOR_REF = "connectorRef";
  private static final String REPO_NAME = "testRepo";

  private List<String> FOLDER_PATHS;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    gitXWebhooksApi = new GitXWebhooksApiImpl(gitXWebhookService, gitXWebhookEventService);
    FOLDER_PATHS = new ArrayList<>();
    FOLDER_PATHS.add("path1");
    FOLDER_PATHS.add("path2");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testCreateWebhook() {
    CreateGitXWebhookRequest createGitXWebhookRequest = new CreateGitXWebhookRequest();
    createGitXWebhookRequest.connectorRef(CONNECTOR_REF);
    createGitXWebhookRequest.repoName(REPO_NAME);
    createGitXWebhookRequest.webhookIdentifier(WEBHOOK_IDENTIFIER);
    createGitXWebhookRequest.folderPaths(FOLDER_PATHS);

    CreateGitXWebhookResponseDTO createGitXWebhookResponseDTO =
        CreateGitXWebhookResponseDTO.builder().webhookIdentifier(WEBHOOK_IDENTIFIER).build();
    when(gitXWebhookService.createGitXWebhook(any())).thenReturn(createGitXWebhookResponseDTO);
    Response response = gitXWebhooksApi.createGitxWebhook(createGitXWebhookRequest, ACCOUNT_IDENTIFIER);
    assertEquals(201, response.getStatus());
    CreateGitXWebhookResponse createGitXWebhookResponse = (CreateGitXWebhookResponse) response.getEntity();
    assertEquals(WEBHOOK_IDENTIFIER, createGitXWebhookResponse.getWebhookIdentifier());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetWebhook() {
    GetGitXWebhookResponseDTO getGitXWebhookResponseDTO = GetGitXWebhookResponseDTO.builder()
                                                              .webhookIdentifier(WEBHOOK_IDENTIFIER)
                                                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                              .isEnabled(true)
                                                              .build();
    when(gitXWebhookService.getGitXWebhook(any())).thenReturn(Optional.ofNullable(getGitXWebhookResponseDTO));
    Response response = gitXWebhooksApi.getGitxWebhook(WEBHOOK_IDENTIFIER, ACCOUNT_IDENTIFIER);
    assertEquals(200, response.getStatus());
    GitXWebhookResponse getGitXWebhookResponse = (GitXWebhookResponse) response.getEntity();
    assertEquals(WEBHOOK_IDENTIFIER, getGitXWebhookResponse.getWebhookIdentifier());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testUpdateWebhook() {
    UpdateGitXWebhookRequest updateGitXWebhookRequest = new UpdateGitXWebhookRequest();
    updateGitXWebhookRequest.setFolderPaths(FOLDER_PATHS);
    UpdateGitXWebhookResponseDTO updateGitXWebhookResponseDTO =
        UpdateGitXWebhookResponseDTO.builder().webhookIdentifier(WEBHOOK_IDENTIFIER).build();
    when(gitXWebhookService.updateGitXWebhook(any(), any())).thenReturn(updateGitXWebhookResponseDTO);
    Response response =
        gitXWebhooksApi.updateGitxWebhook(WEBHOOK_IDENTIFIER, updateGitXWebhookRequest, ACCOUNT_IDENTIFIER);
    UpdateGitXWebhookResponse updateGitXWebhookResponse = (UpdateGitXWebhookResponse) response.getEntity();
    assertEquals(WEBHOOK_IDENTIFIER, updateGitXWebhookResponse.getWebhookIdentifier());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testDeleteWebhook() {
    DeleteGitXWebhookResponseDTO deleteGitXWebhookResponseDTO =
        DeleteGitXWebhookResponseDTO.builder().successfullyDeleted(true).build();
    when(gitXWebhookService.deleteGitXWebhook(any())).thenReturn(deleteGitXWebhookResponseDTO);
    Response response = gitXWebhooksApi.deleteGitxWebhook(WEBHOOK_IDENTIFIER, ACCOUNT_IDENTIFIER);
    assertEquals(204, response.getStatus());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testListWebhook() {
    GetGitXWebhookResponseDTO getGitXWebhookResponseDTO1 = GetGitXWebhookResponseDTO.builder()
                                                               .webhookIdentifier(WEBHOOK_IDENTIFIER)
                                                               .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                               .isEnabled(true)
                                                               .build();
    GetGitXWebhookResponseDTO getGitXWebhookResponseDTO2 = GetGitXWebhookResponseDTO.builder()
                                                               .webhookIdentifier(WEBHOOK_IDENTIFIER2)
                                                               .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                               .isEnabled(false)
                                                               .build();
    List<GetGitXWebhookResponseDTO> gitXWebhooksList = new ArrayList<>();
    gitXWebhooksList.add(getGitXWebhookResponseDTO1);
    gitXWebhooksList.add(getGitXWebhookResponseDTO2);
    ListGitXWebhookResponseDTO listGitXWebhookResponseDTO =
        ListGitXWebhookResponseDTO.builder().gitXWebhooksList(gitXWebhooksList).build();
    when(gitXWebhookService.listGitXWebhooks(any())).thenReturn(listGitXWebhookResponseDTO);
    Response response = gitXWebhooksApi.listGitxWebhooks(ACCOUNT_IDENTIFIER, 0, 10, "");
    List<GitXWebhookResponse> listGitXWebhookResponse = (List<GitXWebhookResponse>) response.getEntity();
    assertEquals(2, listGitXWebhookResponse.size());
    assertEquals(WEBHOOK_IDENTIFIER, listGitXWebhookResponse.get(0).getWebhookIdentifier());
    assertTrue(listGitXWebhookResponse.get(0).isIsEnabled());
    assertEquals(WEBHOOK_IDENTIFIER2, listGitXWebhookResponse.get(1).getWebhookIdentifier());
    assertFalse(listGitXWebhookResponse.get(1).isIsEnabled());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testListWebhookEvents() {
    GitXEventDTO gitXEventDTO1 = GitXEventDTO.builder()
                                     .authorName("author1")
                                     .webhookIdentifier(WEBHOOK_IDENTIFIER)
                                     .eventIdentifier("event123")
                                     .build();
    GitXEventDTO gitXEventDTO2 = GitXEventDTO.builder()
                                     .authorName("author2")
                                     .webhookIdentifier(WEBHOOK_IDENTIFIER2)
                                     .eventIdentifier("event234")
                                     .build();
    List<GitXEventDTO> gitXEventDTOList = new ArrayList<>();
    gitXEventDTOList.add(gitXEventDTO1);
    gitXEventDTOList.add(gitXEventDTO2);
    GitXEventsListResponseDTO gitXEventsListResponseDTO =
        GitXEventsListResponseDTO.builder().gitXEventDTOS(gitXEventDTOList).build();
    when(gitXWebhookEventService.listEvents(any())).thenReturn(gitXEventsListResponseDTO);
    Response response = gitXWebhooksApi.listGitxWebhookEvents(ACCOUNT_IDENTIFIER, 0, 10, "", null, null, null, null);
    List<GitXWebhookEventResponse> eventResponseList = (List<GitXWebhookEventResponse>) response.getEntity();
    assertEquals(2, eventResponseList.size());
    assertEquals(WEBHOOK_IDENTIFIER, eventResponseList.get(0).getWebhookIdentifier());
    assertEquals("event123", eventResponseList.get(0).getEventIdentifier());

    assertEquals(WEBHOOK_IDENTIFIER2, eventResponseList.get(1).getWebhookIdentifier());
    assertEquals("event234", eventResponseList.get(1).getEventIdentifier());
  }
}
