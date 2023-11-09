/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.DOT_SEPARATOR;
import static io.harness.idp.common.Constants.IDP_PERMISSION;
import static io.harness.idp.common.Constants.IDP_RESOURCE_TYPE;
import static io.harness.idp.common.Constants.SUCCESS_RESPONSE;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.checks.entity.CheckStatusEntity;
import io.harness.idp.scorecard.checks.mappers.CheckMapper;
import io.harness.idp.scorecard.checks.service.CheckService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.ChecksApi;
import io.harness.spec.server.idp.v1.model.CheckDetails;
import io.harness.spec.server.idp.v1.model.CheckDetailsRequest;
import io.harness.spec.server.idp.v1.model.CheckDetailsResponse;
import io.harness.spec.server.idp.v1.model.CheckGraph;
import io.harness.spec.server.idp.v1.model.CheckListItem;
import io.harness.spec.server.idp.v1.model.CheckStats;
import io.harness.spec.server.idp.v1.model.DefaultSaveResponse;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@NextGenManagerAuth
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class ChecksApiImpl implements ChecksApi {
  private final CheckService checkService;
  private final IdpCommonService idpCommonService;

  @Inject
  public ChecksApiImpl(CheckService checkService, IdpCommonService idpCommonService) {
    this.checkService = checkService;
    this.idpCommonService = idpCommonService;
  }

  @Override
  public Response getChecks(
      Boolean custom, String harnessAccount, Integer page, Integer limit, String sort, String searchTerm) {
    int pageIndex = page == null ? 0 : page;
    int pageLimit = limit == null ? 100 : limit;
    Pageable pageRequest = isEmpty(sort)
        ? PageRequest.of(pageIndex, pageLimit, Sort.by(Sort.Direction.DESC, CheckEntity.CheckKeys.lastUpdatedAt))
        : PageUtils.getPageRequest(pageIndex, pageLimit, List.of(sort));
    Page<CheckEntity> checkEntityList =
        checkService.getChecksByAccountId(custom, harnessAccount, pageRequest, searchTerm);
    Map<String, CheckStatusEntity> checkStatusEntityMap =
        checkService.getCheckStatusByAccountIdAndIdentifiers(harnessAccount,
            checkEntityList.getContent().stream().map(CheckEntity::getIdentifier).collect(Collectors.toList()));
    List<CheckListItem> checkListItems = new ArrayList<>();
    checkEntityList.getContent().forEach(checkEntity
        -> checkListItems.add(CheckMapper.toDTO(checkEntity,
            checkStatusEntityMap.get(
                checkEntity.getAccountIdentifier() + DOT_SEPARATOR + checkEntity.getIdentifier()))));
    return idpCommonService.buildPageResponse(
        pageIndex, pageLimit, checkEntityList.getTotalElements(), CheckMapper.toResponseList(checkListItems));
  }

  @Override
  public Response getCheck(String checkId, String harnessAccount, Boolean isCustom) {
    try {
      CheckDetails checkDetails = checkService.getCheckDetails(harnessAccount, checkId, isCustom);
      CheckDetailsResponse response = new CheckDetailsResponse();
      response.setCheckDetails(checkDetails);
      return Response.status(Response.Status.OK).entity(response).build();
    } catch (Exception e) {
      String errorMessage = String.format(
          "Error occurred while fetching check details for accountId: [%s], checkId: [%s]", harnessAccount, checkId);
      log.error(errorMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response getCheckGraph(String checkId, String harnessAccount, Boolean custom) {
    try {
      List<CheckGraph> checkGraphs = checkService.getCheckGraph(harnessAccount, checkId, custom);
      return Response.status(Response.Status.OK).entity(checkGraphs).build();
    } catch (Exception e) {
      String errorMessage = String.format(
          "Error occurred while fetching check graph for accountId: [%s], checkId: [%s]", harnessAccount, checkId);
      log.error(errorMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response getCheckStats(String checkId, String harnessAccount, Boolean isCustom) {
    try {
      List<CheckStats> checkStats = checkService.getCheckStats(harnessAccount, checkId, isCustom);
      return Response.status(Response.Status.OK).entity(checkStats).build();
    } catch (Exception e) {
      String errorMessage = String.format(
          "Error occurred while fetching check stats for accountId: [%s], checkId: [%s]", harnessAccount, checkId);
      log.error(errorMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response createCheck(@Valid CheckDetailsRequest body, @AccountIdentifier String harnessAccount) {
    try {
      checkService.createCheck(body.getCheckDetails(), harnessAccount);
      return Response.status(Response.Status.CREATED)
          .entity(new DefaultSaveResponse().status(SUCCESS_RESPONSE))
          .build();
    } catch (DuplicateKeyException e) {
      String errorMessage = String.format(
          "Check [%s] already created for accountId [%s]", body.getCheckDetails().getIdentifier(), harnessAccount);
      log.info(errorMessage);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(errorMessage).build())
          .build();
    } catch (Exception e) {
      log.error("Could not create check", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response deleteCheck(String checkId, @AccountIdentifier String harnessAccount, Boolean forceDelete) {
    try {
      checkService.deleteCustomCheck(harnessAccount, checkId, forceDelete);
      return Response.status(Response.Status.NO_CONTENT).build();
    } catch (Exception e) {
      String errorMessage = String.format(
          "Error occurred while deleting check for accountId: [%s], checkId: [%s]", harnessAccount, checkId);
      log.error(errorMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .type(MediaType.APPLICATION_JSON)
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response updateCheck(
      String checkId, @Valid CheckDetailsRequest body, @AccountIdentifier String harnessAccount) {
    try {
      checkService.updateCheck(body.getCheckDetails(), harnessAccount);
      return Response.status(Response.Status.OK).entity(new DefaultSaveResponse().status(SUCCESS_RESPONSE)).build();
    } catch (Exception e) {
      log.error("Could not update check", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
