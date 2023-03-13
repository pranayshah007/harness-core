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
import io.harness.spec.server.idp.v1.StatusInfoApi;
import io.harness.spec.server.idp.v1.model.StatusInfo;
import io.harness.spec.server.idp.v1.model.StatusInfoRequest;
import io.harness.spec.server.idp.v1.model.StatusInfoResponse;

import com.google.inject.Inject;
import java.util.Optional;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class StatusInfoApiImpl implements StatusInfoApi {
  private StatusInfoService statusInfoService;

  @Override
  public Response getStatusInfoByType(String type, String harnessAccount) {
    try {
      Optional<StatusInfo> statusInfo = statusInfoService.findByAccountIdentifierAndType(harnessAccount, type);
      StatusInfoResponse statusResponse = new StatusInfoResponse();
      if (statusInfo.isEmpty()) {
        log.warn("Could not fetch status for accountId: {} and type: {}", harnessAccount, type);
        return Response.status(Response.Status.NOT_FOUND).build();
      }
      statusResponse.setStatus(statusInfo.get());
      return Response.status(Response.Status.OK).entity(statusResponse).build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response saveStatusInfoByType(String type, @Valid StatusInfoRequest body, String harnessAccount) {
    try {
      StatusInfo statusInfo = statusInfoService.save(body.getStatus(), harnessAccount, type);
      StatusInfoResponse statusInfoResponse = new StatusInfoResponse();
      statusInfoResponse.setStatus(statusInfo);
      return Response.status(Response.Status.CREATED).entity(statusInfoResponse).build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
