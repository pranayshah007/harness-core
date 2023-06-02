/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.plugin.resources;

import static io.harness.idp.common.Constants.IDP_PERMISSION;
import static io.harness.idp.common.Constants.IDP_RESOURCE_TYPE;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity.BackstageEnvVariableMapper;
import io.harness.idp.plugin.services.AuthInfoService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.AuthInfoApi;
import io.harness.spec.server.idp.v1.model.AuthInfo;
import io.harness.spec.server.idp.v1.model.AuthInfoResponse;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariableBatchRequest;

import com.google.inject.Inject;
import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class AuthInfoApiImpl implements AuthInfoApi {
  private AuthInfoService authInfoService;
  @Override
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response getAuthInfoAuthId(String authId, @AccountIdentifier String harnessAccount) {
    try {
      AuthInfo authInfo = authInfoService.getAuthInfo(authId, harnessAccount);
      AuthInfoResponse response = new AuthInfoResponse();
      response.setAuthInfo(authInfo);
      return Response.status(Response.Status.OK).entity(response).build();
    } catch (Exception e) {
      String errorMessage = String.format(
          "Error occurred while fetching auth info for accountId: [%s], authId: [%s]", harnessAccount, authId);
      log.error(errorMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response saveAuthInfoAuthId(
      String authId, @Valid BackstageEnvVariableBatchRequest body, @AccountIdentifier String harnessAccount) {
    try {
      List<BackstageEnvVariable> backstageEnvVariables =
          authInfoService.saveAuthEnvVariables(authId, body.getEnvVariables(), harnessAccount);
      return Response.status(Response.Status.CREATED)
          .entity(BackstageEnvVariableMapper.toResponseList(backstageEnvVariables))
          .build();
    } catch (Exception e) {
      String errorMessage = String.format(
          "Error occurred while saving auth info for accountId: [%s], authId: [%s]", harnessAccount, authId);
      log.error(errorMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
