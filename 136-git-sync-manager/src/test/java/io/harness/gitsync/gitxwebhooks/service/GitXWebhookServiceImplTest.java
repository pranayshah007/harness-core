/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.service;

import static io.harness.rule.OwnerRule.ADITHYA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InternalServerErrorException;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.helper.GitRepoHelper;
import io.harness.gitsync.common.helper.GitSyncConnectorHelper;
import io.harness.gitsync.gitxwebhooks.dtos.CreateGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.CreateGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.DeleteGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.DeleteGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GetGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GetGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.ListGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.ListGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookCriteriaDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook.GitXWebhookKeys;
import io.harness.ng.webhook.UpsertWebhookResponseDTO;
import io.harness.ng.webhook.services.api.WebhookEventService;
import io.harness.product.ci.scm.proto.WebhookResponse;
import io.harness.repositories.gitxwebhook.GitXWebhookRepository;
import io.harness.rule.Owner;

import com.mongodb.client.result.DeleteResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.PIPELINE)
public class GitXWebhookServiceImplTest extends GitSyncTestBase {
  @InjectMocks GitXWebhookServiceImpl gitXWebhookService;
  @Mock GitXWebhookRepository gitXWebhookRepository;
  @Mock GitRepoHelper gitRepoHelper;
  @Mock GitSyncConnectorHelper gitSyncConnectorHelper;
  @Mock WebhookEventService webhookEventService;

  private static final String ACCOUNT_IDENTIFIER = "accountId";
  private static final String WEBHOOK_IDENTIFIER = "gitWebhook";
  private static final String WEBHOOK_NAME = "gitWebhook";
  private static final String WEBHOOK_IDENTIFIER2 = "gitWebhook2";
  private static final String CONNECTOR_REF = "connectorRef";
  private static final String REPO_NAME = "testRepo";

  private List<String> FOLDER_PATHS;

  public void setup() {
    MockitoAnnotations.initMocks(this);
    FOLDER_PATHS = new ArrayList<>();
    FOLDER_PATHS.add("path1");
    FOLDER_PATHS.add("path2");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testCreateGitXWebhook() {
    CreateGitXWebhookRequestDTO createGitXWebhookRequestDTO = CreateGitXWebhookRequestDTO.builder()
                                                                  .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                                  .webhookIdentifier(WEBHOOK_IDENTIFIER)
                                                                  .webhookName(WEBHOOK_NAME)
                                                                  .connectorRef(CONNECTOR_REF)
                                                                  .repoName(REPO_NAME)
                                                                  .folderPaths(FOLDER_PATHS)
                                                                  .build();
    UpsertWebhookResponseDTO upsertWebhookResponseDTO =
        UpsertWebhookResponseDTO.builder()
            .status(200)
            .webhookResponse(WebhookResponse.newBuilder().setId(WEBHOOK_IDENTIFIER).build())
            .build();
    when(webhookEventService.upsertWebhook(any())).thenReturn(upsertWebhookResponseDTO);
    GitXWebhook gitXWebhook = GitXWebhook.builder()
                                  .accountIdentifier(ACCOUNT_IDENTIFIER)
                                  .identifier(WEBHOOK_IDENTIFIER)
                                  .repoName(REPO_NAME)
                                  .connectorRef(CONNECTOR_REF)
                                  .build();
    when(gitXWebhookRepository.create(any())).thenReturn(gitXWebhook);
    CreateGitXWebhookResponseDTO createGitXWebhookResponseDTO =
        gitXWebhookService.createGitXWebhook(createGitXWebhookRequestDTO);
    assertNotNull(createGitXWebhookResponseDTO);
    assertEquals(WEBHOOK_IDENTIFIER, createGitXWebhookResponseDTO.getWebhookIdentifier());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testCreateGitXWebhookFailureCase() {
    CreateGitXWebhookRequestDTO createGitXWebhookRequestDTO = CreateGitXWebhookRequestDTO.builder()
                                                                  .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                                  .webhookIdentifier(WEBHOOK_IDENTIFIER)
                                                                  .webhookName(WEBHOOK_NAME)
                                                                  .connectorRef(CONNECTOR_REF)
                                                                  .repoName(REPO_NAME)
                                                                  .folderPaths(FOLDER_PATHS)
                                                                  .build();
    UpsertWebhookResponseDTO upsertWebhookResponseDTO =
        UpsertWebhookResponseDTO.builder()
            .status(200)
            .webhookResponse(WebhookResponse.newBuilder().setId(WEBHOOK_IDENTIFIER).build())
            .build();
    when(webhookEventService.upsertWebhook(any())).thenReturn(upsertWebhookResponseDTO);
    assertThrows(
        InternalServerErrorException.class, () -> gitXWebhookService.createGitXWebhook(createGitXWebhookRequestDTO));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetGitXWebhook() {
    GetGitXWebhookRequestDTO getGitXWebhookRequestDTO = GetGitXWebhookRequestDTO.builder()
                                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                            .webhookIdentifier(WEBHOOK_IDENTIFIER)
                                                            .build();
    GitXWebhook gitXWebhook = GitXWebhook.builder()
                                  .accountIdentifier(ACCOUNT_IDENTIFIER)
                                  .identifier(WEBHOOK_IDENTIFIER)
                                  .repoName(REPO_NAME)
                                  .connectorRef(CONNECTOR_REF)
                                  .build();
    when(gitXWebhookRepository.findByAccountIdentifierAndIdentifier(any(), any()))
        .thenReturn(Arrays.asList(gitXWebhook));
    Optional<GetGitXWebhookResponseDTO> getGitXWebhookResponseDTO =
        gitXWebhookService.getGitXWebhook(getGitXWebhookRequestDTO);
    assertTrue(getGitXWebhookResponseDTO.isPresent());
    assertEquals(WEBHOOK_IDENTIFIER, getGitXWebhookResponseDTO.get().getWebhookIdentifier());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetGitXWebhookForFailure() {
    GetGitXWebhookRequestDTO getGitXWebhookRequestDTO = GetGitXWebhookRequestDTO.builder()
                                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                            .webhookIdentifier(WEBHOOK_IDENTIFIER)
                                                            .build();
    GitXWebhook gitXWebhook1 = GitXWebhook.builder()
                                   .accountIdentifier(ACCOUNT_IDENTIFIER)
                                   .identifier(WEBHOOK_IDENTIFIER)
                                   .repoName(REPO_NAME)
                                   .connectorRef(CONNECTOR_REF)
                                   .build();
    GitXWebhook gitXWebhook2 = GitXWebhook.builder()
                                   .accountIdentifier(ACCOUNT_IDENTIFIER)
                                   .identifier(WEBHOOK_IDENTIFIER2)
                                   .repoName(REPO_NAME)
                                   .connectorRef(CONNECTOR_REF)
                                   .build();
    ArrayList<GitXWebhook> gitXWebhookArrayList = new ArrayList<>();
    gitXWebhookArrayList.add(gitXWebhook1);
    gitXWebhookArrayList.add(gitXWebhook2);
    when(gitXWebhookRepository.findByAccountIdentifierAndIdentifier(any(), any())).thenReturn(gitXWebhookArrayList);
    assertThrows(InternalServerErrorException.class, () -> gitXWebhookService.getGitXWebhook(getGitXWebhookRequestDTO));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testUpdateGitXWebhook() {
    UpdateGitXWebhookCriteriaDTO updateGitXWebhookCriteriaDTO = UpdateGitXWebhookCriteriaDTO.builder()
                                                                    .webhookIdentifier(WEBHOOK_IDENTIFIER)
                                                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                                    .build();
    UpdateGitXWebhookRequestDTO updateGitXWebhookRequestDTO =
        UpdateGitXWebhookRequestDTO.builder().isEnabled(true).build();
    GitXWebhook gitXWebhook = GitXWebhook.builder()
                                  .accountIdentifier(ACCOUNT_IDENTIFIER)
                                  .identifier(WEBHOOK_IDENTIFIER)
                                  .repoName(REPO_NAME)
                                  .connectorRef(CONNECTOR_REF)
                                  .isEnabled(true)
                                  .build();
    when(gitXWebhookRepository.update(any(), any())).thenReturn(gitXWebhook);
    UpdateGitXWebhookResponseDTO updateGitXWebhookResponseDTO =
        gitXWebhookService.updateGitXWebhook(updateGitXWebhookCriteriaDTO, updateGitXWebhookRequestDTO);
    assertNotNull(updateGitXWebhookResponseDTO);
    assertTrue(updateGitXWebhookRequestDTO.getIsEnabled());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testUpdateRepoGitXWebhook() {
    UpdateGitXWebhookCriteriaDTO updateGitXWebhookCriteriaDTO = UpdateGitXWebhookCriteriaDTO.builder()
                                                                    .webhookIdentifier(WEBHOOK_IDENTIFIER)
                                                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                                    .build();
    UpdateGitXWebhookRequestDTO updateGitXWebhookRequestDTO =
        UpdateGitXWebhookRequestDTO.builder().repoName(REPO_NAME).connectorRef(CONNECTOR_REF).build();
    GitXWebhook gitXWebhook = GitXWebhook.builder()
                                  .accountIdentifier(ACCOUNT_IDENTIFIER)
                                  .identifier(WEBHOOK_IDENTIFIER)
                                  .repoName(REPO_NAME)
                                  .connectorRef(CONNECTOR_REF)
                                  .isEnabled(true)
                                  .build();
    when(gitXWebhookRepository.update(any(), any())).thenReturn(gitXWebhook);
    UpsertWebhookResponseDTO upsertWebhookResponseDTO =
        UpsertWebhookResponseDTO.builder()
            .status(200)
            .webhookResponse(WebhookResponse.newBuilder().setId(WEBHOOK_IDENTIFIER).build())
            .build();
    when(webhookEventService.upsertWebhook(any())).thenReturn(upsertWebhookResponseDTO);
    UpdateGitXWebhookResponseDTO updateGitXWebhookResponseDTO =
        gitXWebhookService.updateGitXWebhook(updateGitXWebhookCriteriaDTO, updateGitXWebhookRequestDTO);
    assertNotNull(updateGitXWebhookResponseDTO);
    verify(webhookEventService, times(1)).upsertWebhook(any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testUpdateGitXWebhookFailure() {
    UpdateGitXWebhookCriteriaDTO updateGitXWebhookCriteriaDTO = UpdateGitXWebhookCriteriaDTO.builder()
                                                                    .webhookIdentifier(WEBHOOK_IDENTIFIER)
                                                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                                    .build();
    UpdateGitXWebhookRequestDTO updateGitXWebhookRequestDTO =
        UpdateGitXWebhookRequestDTO.builder().isEnabled(true).build();
    assertThrows(InternalServerErrorException.class,
        () -> gitXWebhookService.updateGitXWebhook(updateGitXWebhookCriteriaDTO, updateGitXWebhookRequestDTO));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testDeleteGitXWebhook() {
    DeleteGitXWebhookRequestDTO deleteGitXWebhookRequestDTO = DeleteGitXWebhookRequestDTO.builder()
                                                                  .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                                  .webhookIdentifier(WEBHOOK_IDENTIFIER)
                                                                  .build();
    Criteria criteria = Criteria.where(GitXWebhookKeys.accountIdentifier)
                            .is(ACCOUNT_IDENTIFIER)
                            .and(GitXWebhookKeys.identifier)
                            .is(WEBHOOK_IDENTIFIER);
    when(gitXWebhookRepository.delete(criteria)).thenReturn(DeleteResult.acknowledged(1));
    DeleteGitXWebhookResponseDTO deleteGitXWebhookResponseDTO =
        gitXWebhookService.deleteGitXWebhook(deleteGitXWebhookRequestDTO);
    assertNotNull(deleteGitXWebhookResponseDTO);
    assertTrue(deleteGitXWebhookResponseDTO.isSuccessfullyDeleted());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testDeleteGitXWebhookFailure() {
    DeleteGitXWebhookRequestDTO deleteGitXWebhookRequestDTO = DeleteGitXWebhookRequestDTO.builder()
                                                                  .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                                  .webhookIdentifier(WEBHOOK_IDENTIFIER)
                                                                  .build();
    Criteria criteria = Criteria.where(GitXWebhookKeys.accountIdentifier)
                            .is(ACCOUNT_IDENTIFIER)
                            .and(GitXWebhookKeys.identifier)
                            .is(WEBHOOK_IDENTIFIER);
    when(gitXWebhookRepository.delete(criteria)).thenReturn(DeleteResult.unacknowledged());
    assertThrows(
        InternalServerErrorException.class, () -> gitXWebhookService.deleteGitXWebhook(deleteGitXWebhookRequestDTO));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testListGitXWebhooks() {
    ListGitXWebhookRequestDTO listGitXWebhookRequestDTO =
        ListGitXWebhookRequestDTO.builder().accountIdentifier(ACCOUNT_IDENTIFIER).build();
    GitXWebhook gitXWebhook1 = GitXWebhook.builder()
                                   .accountIdentifier(ACCOUNT_IDENTIFIER)
                                   .identifier(WEBHOOK_IDENTIFIER)
                                   .repoName(REPO_NAME)
                                   .connectorRef(CONNECTOR_REF)
                                   .isEnabled(true)
                                   .build();
    GitXWebhook gitXWebhook2 = GitXWebhook.builder()
                                   .accountIdentifier(ACCOUNT_IDENTIFIER)
                                   .identifier(WEBHOOK_IDENTIFIER2)
                                   .repoName(REPO_NAME)
                                   .connectorRef(CONNECTOR_REF)
                                   .isEnabled(true)
                                   .build();
    ArrayList<GitXWebhook> gitXWebhookArrayList = new ArrayList<>();
    gitXWebhookArrayList.add(gitXWebhook1);
    gitXWebhookArrayList.add(gitXWebhook2);
    when(gitXWebhookRepository.list(any())).thenReturn(gitXWebhookArrayList);
    ListGitXWebhookResponseDTO listGitXWebhookResponseDTO =
        gitXWebhookService.listGitXWebhooks(listGitXWebhookRequestDTO);
    assertNotNull(listGitXWebhookResponseDTO);
    assertEquals(2, listGitXWebhookResponseDTO.getGitXWebhooksList().size());
    assertEquals(WEBHOOK_IDENTIFIER, listGitXWebhookResponseDTO.getGitXWebhooksList().get(0).getWebhookIdentifier());
    assertEquals(WEBHOOK_IDENTIFIER2, listGitXWebhookResponseDTO.getGitXWebhooksList().get(1).getWebhookIdentifier());
  }
}
