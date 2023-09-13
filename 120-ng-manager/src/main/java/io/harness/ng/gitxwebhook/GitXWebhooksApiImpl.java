/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitxwebhook;

import io.harness.gitsync.gitxwebhooks.dtos.CreateGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.CreateGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.DeleteGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GetGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GetGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.ListGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.ListGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookCriteriaDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.mapper.GitXWebhookMapper;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookService;
import io.harness.spec.server.ng.v1.GitXWebhooksApi;
import io.harness.spec.server.ng.v1.model.CreateGitXWebhookRequest;
import io.harness.spec.server.ng.v1.model.CreateGitXWebhookResponse;
import io.harness.spec.server.ng.v1.model.GitXWebhookResponse;
import io.harness.spec.server.ng.v1.model.UpdateGitXWebhookRequest;
import io.harness.spec.server.ng.v1.model.UpdateGitXWebhookResponse;
import io.harness.utils.ApiUtils;

import com.google.inject.Inject;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class GitXWebhooksApiImpl implements GitXWebhooksApi {
  GitXWebhookService gitXWebhookService;
  private final int HTTP_201 = 201;
  private final int HTTP_404 = 404;
  private final int HTTP_204 = 204;

  @Override
  public Response createGitxWebhook(@Valid CreateGitXWebhookRequest body, String harnessAccount) {
    CreateGitXWebhookRequestDTO createGitXWebhookRequestDTO =
        GitXWebhookMapper.buildCreateGitXWebhookRequestDTO(harnessAccount, body);
    CreateGitXWebhookResponseDTO createGitXWebhookResponseDTO =
        gitXWebhookService.createGitXWebhook(createGitXWebhookRequestDTO);
    CreateGitXWebhookResponse responseBody =
        GitXWebhookMapper.buildCreateGitXWebhookResponse(createGitXWebhookResponseDTO);
    return Response.status(HTTP_201).entity(responseBody).build();
  }

  @Override
  public Response getGitxWebhook(String gitXWebhookIdentifier, String harnessAccount) {
    GetGitXWebhookRequestDTO getGitXWebhookRequestDTO =
        GitXWebhookMapper.buildGetGitXWebhookRequestDTO(harnessAccount, gitXWebhookIdentifier);
    Optional<GetGitXWebhookResponseDTO> optionalGetGitXWebhookResponseDTO =
        gitXWebhookService.getGitXWebhook(getGitXWebhookRequestDTO);
    if (optionalGetGitXWebhookResponseDTO.isEmpty()) {
      return Response.status(HTTP_404).build();
    }
    GitXWebhookResponse responseBody =
        GitXWebhookMapper.buildGetGitXWebhookResponseDTO(optionalGetGitXWebhookResponseDTO.get());
    return Response.ok().entity(responseBody).build();
  }

  @Override
  public Response updateGitxWebhook(
      String gitXWebhookIdentifier, @Valid UpdateGitXWebhookRequest body, String harnessAccount) {
    UpdateGitXWebhookRequestDTO updateGitXWebhookRequestDTO = GitXWebhookMapper.buildUpdateGitXWebhookRequestDTO(body);
    UpdateGitXWebhookResponseDTO updateGitXWebhookResponseDTO =
        gitXWebhookService.updateGitXWebhook(UpdateGitXWebhookCriteriaDTO.builder()
                                                 .accountIdentifier(harnessAccount)
                                                 .webhookIdentifier(gitXWebhookIdentifier)
                                                 .build(),
            updateGitXWebhookRequestDTO);
    UpdateGitXWebhookResponse responseBody =
        GitXWebhookMapper.buildUpdateGitXWebhookResponse(updateGitXWebhookResponseDTO);
    return Response.ok().entity(responseBody).build();
  }

  @Override
  public Response deleteGitxWebhook(String gitXWebhookIdentifier, String harnessAccount) {
    DeleteGitXWebhookRequestDTO deleteGitXWebhookRequestDTO =
        GitXWebhookMapper.buildDeleteGitXWebhookRequestDTO(harnessAccount, gitXWebhookIdentifier);
    gitXWebhookService.deleteGitXWebhook(deleteGitXWebhookRequestDTO);
    return Response.status(HTTP_204).build();
  }

  @Override
  public Response listGitxWebhooks(
      String harnessAccount, Integer page, @Max(1000L) Integer limit, String webhookIdentifier) {
    ListGitXWebhookRequestDTO listGitXWebhookRequestDTO =
        GitXWebhookMapper.buildListGitXWebhookRequestDTO(harnessAccount, webhookIdentifier);
    ListGitXWebhookResponseDTO listGitXWebhookResponseDTO =
        gitXWebhookService.listGitXWebhooks(listGitXWebhookRequestDTO);
    Page<GitXWebhookResponse> gitXWebhooks =
        GitXWebhookMapper.buildListGitXWebhookResponse(listGitXWebhookResponseDTO, page, limit);

    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, gitXWebhooks.getTotalElements(), page, limit);
    return responseBuilderWithLinks
        .entity(gitXWebhooks.getContent()
                    .stream()
                    .map(GitXWebhookMapper::buildGetGitXWebhookResponseDTO)
                    .collect(Collectors.toList()))
        .build();
  }

  @Override
  public Response listGitxWebhookEvents(String harnessAccount, Integer page, @Max(1000L) Integer limit,
      String webhookIdentifier, Long eventStartTime, Long eventEndTime) {
    return null;
  }
}
