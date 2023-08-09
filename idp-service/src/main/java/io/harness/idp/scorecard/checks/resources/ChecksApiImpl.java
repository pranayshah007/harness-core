/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.resources;

import static io.harness.idp.common.Constants.IDP_PERMISSION;
import static io.harness.idp.common.Constants.IDP_RESOURCE_TYPE;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.scorecard.checks.mappers.CheckMapper;
import io.harness.idp.scorecard.checks.service.CheckService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.ChecksApi;
import io.harness.spec.server.idp.v1.model.CheckDetails;
import io.harness.spec.server.idp.v1.model.CheckDetailsRequest;
import io.harness.spec.server.idp.v1.model.CheckDetailsResponse;
import io.harness.spec.server.idp.v1.model.CheckListItem;

import com.google.inject.Inject;
import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@NextGenManagerAuth
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class ChecksApiImpl implements ChecksApi {
  private final CheckService checkService;

  @Inject
  public ChecksApiImpl(CheckService checkService) {
    this.checkService = checkService;
  }

  @Override
  public Response getChecks(Boolean custom, String harnessAccount) {
    List<CheckListItem> checks = checkService.getChecksByAccountId(custom, harnessAccount);
    return Response.status(Response.Status.OK).entity(CheckMapper.toResponseList(checks)).build();
  }

  @Override
  public Response getCheck(String checkId, String harnessAccount) {
    try {
      CheckDetails checkDetails = checkService.getCheckDetails(harnessAccount, checkId);
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
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response createCheck(@Valid CheckDetailsRequest body, @AccountIdentifier String harnessAccount) {
    try {
      checkService.createCheck(body.getCheckDetails(), harnessAccount);
      return Response.status(Response.Status.CREATED).build();
    } catch (Exception e) {
      log.error("Could not create check", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response updateCheck(String checkId, @Valid CheckDetailsRequest body, String harnessAccount) {
    try {
      checkService.updateCheck(body.getCheckDetails(), harnessAccount);
      return Response.status(Response.Status.OK).build();
    } catch (Exception e) {
      log.error("Could not update check", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
