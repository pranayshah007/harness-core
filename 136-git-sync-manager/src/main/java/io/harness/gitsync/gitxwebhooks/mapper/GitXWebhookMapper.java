/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.mapper;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.gitxwebhooks.dtos.CreateGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.CreateGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.DeleteGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GetGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GetGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventsListRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventsListResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.ListGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.ListGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookResponseDTO;
import io.harness.spec.server.ng.v1.model.CreateGitXWebhookRequest;
import io.harness.spec.server.ng.v1.model.CreateGitXWebhookResponse;
import io.harness.spec.server.ng.v1.model.GitXWebhookEventResponse;
import io.harness.spec.server.ng.v1.model.GitXWebhookResponse;
import io.harness.spec.server.ng.v1.model.UpdateGitXWebhookRequest;
import io.harness.spec.server.ng.v1.model.UpdateGitXWebhookResponse;
import io.harness.utils.PageUtils;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.springframework.data.domain.Page;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class GitXWebhookMapper {
  public CreateGitXWebhookRequestDTO buildCreateGitXWebhookRequestDTO(
      String harnessAccount, CreateGitXWebhookRequest body) {
    if (body == null) {
      return CreateGitXWebhookRequestDTO.builder().accountIdentifier(harnessAccount).build();
    }
    return CreateGitXWebhookRequestDTO.builder()
        .accountIdentifier(harnessAccount)
        .webhookIdentifier(body.getWebhookIdentifier())
        .connectorRef(body.getConnectorRef())
        .folderPaths(body.getFolderPaths())
        .repoName(body.getRepoName())
        .webhookName(body.getWebhookName())
        .isEnabled(true)
        .build();
  }

  public CreateGitXWebhookResponse buildCreateGitXWebhookResponse(
      CreateGitXWebhookResponseDTO createGitXWebhookResponseDTO) {
    CreateGitXWebhookResponse responseBody = new CreateGitXWebhookResponse();
    responseBody.setWebhookIdentifier(createGitXWebhookResponseDTO.getWebhookIdentifier());
    return responseBody;
  }

  public GetGitXWebhookRequestDTO buildGetGitXWebhookRequestDTO(String harnessAccount, String gitXWebhookIdentifier) {
    return GetGitXWebhookRequestDTO.builder()
        .webhookIdentifier(gitXWebhookIdentifier)
        .accountIdentifier(harnessAccount)
        .build();
  }

  public GitXWebhookResponse buildGetGitXWebhookResponseDTO(GetGitXWebhookResponseDTO getGitXWebhookResponseDTO) {
    GitXWebhookResponse responseBody = new GitXWebhookResponse();
    responseBody.setWebhookIdentifier(getGitXWebhookResponseDTO.getWebhookIdentifier());
    responseBody.setWebhookName(getGitXWebhookResponseDTO.getWebhookName());
    responseBody.setRepoName(getGitXWebhookResponseDTO.getRepoName());
    responseBody.setConnectorRef(getGitXWebhookResponseDTO.getConnectorRef());
    responseBody.setFolderPaths(getGitXWebhookResponseDTO.getFolderPaths());
    responseBody.setIsEnabled(getGitXWebhookResponseDTO.getIsEnabled());
    return responseBody;
  }

  public UpdateGitXWebhookRequestDTO buildUpdateGitXWebhookRequestDTO(UpdateGitXWebhookRequest body) {
    if (body == null) {
      throw new InvalidRequestException("The request body cannot be null.");
    }
    return UpdateGitXWebhookRequestDTO.builder()
        .webhookName(body.getWebhookName())
        .connectorRef(body.getConnectorRef())
        .repoName(body.getRepoName())
        .folderPaths(body.getFolderPaths())
        .isEnabled(body.isIsEnabled())
        .build();
  }

  public UpdateGitXWebhookResponse buildUpdateGitXWebhookResponse(
      UpdateGitXWebhookResponseDTO updateGitXWebhookResponseDTO) {
    UpdateGitXWebhookResponse responseBody = new UpdateGitXWebhookResponse();
    responseBody.setWebhookIdentifier(updateGitXWebhookResponseDTO.getWebhookIdentifier());
    return responseBody;
  }

  public DeleteGitXWebhookRequestDTO buildDeleteGitXWebhookRequestDTO(
      String harnessAccount, String gitXWebhookIdentifier) {
    return DeleteGitXWebhookRequestDTO.builder()
        .accountIdentifier(harnessAccount)
        .webhookIdentifier(gitXWebhookIdentifier)
        .build();
  }

  public ListGitXWebhookRequestDTO buildListGitXWebhookRequestDTO(String harnessAccount, String webhookIdentifier) {
    return ListGitXWebhookRequestDTO.builder()
        .accountIdentifier(harnessAccount)
        .webhookIdentifier(webhookIdentifier)
        .build();
  }

  public Page<GitXWebhookResponse> buildListGitXWebhookResponse(
      ListGitXWebhookResponseDTO listGitXWebhookResponseDTO, Integer page, Integer limit) {
    List<GitXWebhookResponse> getGitXWebhookResponseList =
        emptyIfNull(listGitXWebhookResponseDTO.getGitXWebhooksList())
            .stream()
            .map(gitXWebhook -> {
              GitXWebhookResponse gitXWebhookResponse = new GitXWebhookResponse();
              gitXWebhookResponse.setWebhookIdentifier(gitXWebhook.getWebhookIdentifier());
              gitXWebhookResponse.setWebhookName(gitXWebhook.getWebhookName());
              gitXWebhookResponse.setRepoName(gitXWebhook.getRepoName());
              gitXWebhookResponse.setConnectorRef(gitXWebhook.getConnectorRef());
              gitXWebhookResponse.setFolderPaths(gitXWebhook.getFolderPaths());
              gitXWebhookResponse.setIsEnabled(gitXWebhook.getIsEnabled());
              gitXWebhookResponse.setEventTriggerTime(gitXWebhook.getEventTriggerTime());
              return gitXWebhookResponse;
            })
            .collect(Collectors.toList());
    return PageUtils.getPage(getGitXWebhookResponseList, page, limit);
  }

  public GitXWebhookResponse buildGetGitXWebhookResponseDTO(GitXWebhookResponse gitXWebhook) {
    GitXWebhookResponse responseBody = new GitXWebhookResponse();
    responseBody.setWebhookIdentifier(gitXWebhook.getWebhookIdentifier());
    responseBody.setWebhookName(gitXWebhook.getWebhookName());
    responseBody.setRepoName(gitXWebhook.getRepoName());
    responseBody.setConnectorRef(gitXWebhook.getConnectorRef());
    responseBody.setFolderPaths(gitXWebhook.getFolderPaths());
    responseBody.setIsEnabled(gitXWebhook.isIsEnabled());
    responseBody.setEventTriggerTime(gitXWebhook.getEventTriggerTime());
    return responseBody;
  }

  public GitXEventsListRequestDTO buildEventsListGitXWebhookRequestDTO(
      String accountIdentifier, String webhookIdentifier, Long eventStartTime, Long eventEndTime) {
    if ((eventStartTime == null && eventEndTime != null) || (eventStartTime != null && eventEndTime == null)) {
      throw new InvalidRequestException(String.format(
          "Either the Event start time [%d] or the Event end time [%d] not provided.", eventStartTime, eventEndTime));
    }
    return GitXEventsListRequestDTO.builder()
        .accountIdentifier(accountIdentifier)
        .webhookIdentifier(webhookIdentifier)
        .eventStartTime(eventStartTime)
        .eventEndTime(eventEndTime)
        .build();
  }

  public Page<GitXWebhookEventResponse> buildListGitXWebhookEventResponse(
      GitXEventsListResponseDTO gitXEventsListResponseDTO, Integer page, Integer limit) {
    List<GitXWebhookEventResponse> eventResponseList =
        emptyIfNull(gitXEventsListResponseDTO.getGitXEventDTOS())
            .stream()
            .map(gitXEventDTO -> {
              GitXWebhookEventResponse gitXWebhookEventResponse = new GitXWebhookEventResponse();
              gitXWebhookEventResponse.setEventIdentifier(gitXEventDTO.getEventIdentifier());
              gitXWebhookEventResponse.setWebhookIdentifier(gitXEventDTO.getWebhookIdentifier());
              gitXWebhookEventResponse.setEventTriggerTime(gitXEventDTO.getEventTriggerTime());
              gitXWebhookEventResponse.setPayload(gitXEventDTO.getPayload());
              gitXWebhookEventResponse.setAuthorName(gitXEventDTO.getAuthorName());
              return gitXWebhookEventResponse;
            })
            .collect(Collectors.toList());
    return PageUtils.getPage(eventResponseList, page, limit);
  }

  public GitXWebhookEventResponse buildGitXWebhookEventResponse(GitXWebhookEventResponse gitXWebhookEventResponse) {
    GitXWebhookEventResponse responseBody = new GitXWebhookEventResponse();
    responseBody.setEventIdentifier(gitXWebhookEventResponse.getEventIdentifier());
    responseBody.setWebhookIdentifier(gitXWebhookEventResponse.getWebhookIdentifier());
    responseBody.setEventTriggerTime(gitXWebhookEventResponse.getEventTriggerTime());
    responseBody.setPayload(gitXWebhookEventResponse.getPayload());
    responseBody.setAuthorName(gitXWebhookEventResponse.getAuthorName());
    return responseBody;
  }
}
