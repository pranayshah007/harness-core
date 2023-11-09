/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.status.resources;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.status.service.StatusInfoService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.StatusInfoV2Api;
import io.harness.spec.server.idp.v1.model.StatusInfoV2;

import com.google.inject.Inject;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class StatusInfoV2ApiImpl implements StatusInfoV2Api {
  private StatusInfoService statusInfoService;
  @Override
  public Response getStatusInfoTypeV2(String type, String harnessAccount) {
    try {
      StatusInfoV2 statusInfoV2 = statusInfoService.findByAccountIdentifierAndTypeV2(harnessAccount, type);
      return Response.status(Response.Status.OK).entity(statusInfoV2).build();
    } catch (Exception e) {
      String errorMessage = String.format(
          "Error occurred while fetching status info for accountId: [%s], type: [%s]", harnessAccount, type);
      log.error(errorMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
