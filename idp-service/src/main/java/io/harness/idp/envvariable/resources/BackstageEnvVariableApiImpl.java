/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.envvariable.resources;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity.BackstageEnvVariableMapper;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.BackstageEnvVariableApi;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariableBatchRequest;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariableRequest;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariableResponse;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
public class BackstageEnvVariableApiImpl implements BackstageEnvVariableApi {
  private IdpCommonService idpCommonService;
  private BackstageEnvVariableService backstageEnvVariableService;

  @Override
  public Response createBackstageEnvVariable(@Valid BackstageEnvVariableRequest body, String harnessAccount) {
    idpCommonService.checkUserAuthorization();
    try {
      BackstageEnvVariable envVariable = backstageEnvVariableService.create(body.getEnvVariable(), harnessAccount);
      BackstageEnvVariableResponse secretResponse = new BackstageEnvVariableResponse();
      secretResponse.setEnvVariable(envVariable);
      return Response.status(Response.Status.CREATED).entity(secretResponse).build();
    } catch (Exception e) {
      log.error("Could not create environment variable", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response createBackstageEnvVariables(@Valid BackstageEnvVariableBatchRequest body, String harnessAccount) {
    idpCommonService.checkUserAuthorization();
    List<BackstageEnvVariable> responseSecrets;
    try {
      responseSecrets = backstageEnvVariableService.createOrUpdate(body.getEnvVariables(), harnessAccount);
    } catch (Exception e) {
      log.error("Could not create all environment variables", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
    return Response.status(Response.Status.CREATED)
        .entity(BackstageEnvVariableMapper.toResponseList(responseSecrets))
        .build();
  }

  @Override
  public Response deleteBackstageEnvVariable(String backstageEnvVariable, String harnessAccount) {
    idpCommonService.checkUserAuthorization();
    try {
      backstageEnvVariableService.delete(backstageEnvVariable, harnessAccount);
    } catch (Exception e) {
      log.error("Could not delete backstage env variable for id {}", backstageEnvVariable, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  public Response deleteBackstageEnvVariables(List<String> backstageEnvVariables, String accountIdentifier) {
    idpCommonService.checkUserAuthorization();
    try {
      backstageEnvVariableService.deleteMulti(backstageEnvVariables, accountIdentifier);
    } catch (Exception e) {
      log.error("Could not delete all backstage env variables [{}]", backstageEnvVariables, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  public Response getBackstageEnvVariable(String backstageEnvVariable, String harnessAccount) {
    idpCommonService.checkUserAuthorization();
    Optional<BackstageEnvVariable> backstageEnvVariableOpt =
        backstageEnvVariableService.findByIdAndAccountIdentifier(backstageEnvVariable, harnessAccount);
    if (backstageEnvVariableOpt.isEmpty()) {
      log.warn("Could not fetch backstage env variable for id {}", backstageEnvVariable);
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    BackstageEnvVariableResponse secretResponse = new BackstageEnvVariableResponse();
    secretResponse.setEnvVariable(backstageEnvVariableOpt.get());
    return Response.status(Response.Status.OK).entity(secretResponse).build();
  }

  @Override
  public Response getBackstageEnvVariables(String harnessAccount, Integer page, Integer limit, String sort) {
    idpCommonService.checkUserAuthorization();
    List<BackstageEnvVariable> secrets = backstageEnvVariableService.findByAccountIdentifier(harnessAccount);
    return Response.status(Response.Status.OK).entity(BackstageEnvVariableMapper.toResponseList(secrets)).build();
  }

  @Override
  public Response syncBackstageEnvVariables(String harnessAccount) {
    idpCommonService.checkUserAuthorization();
    try {
      backstageEnvVariableService.findAndSync(harnessAccount);
    } catch (Exception e) {
      log.error("Could not sync all backstage env variables", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  public Response updateBackstageEnvVariable(
      String backstageEnvVariableId, @Valid BackstageEnvVariableRequest request, String harnessAccount) {
    idpCommonService.checkUserAuthorization();
    try {
      BackstageEnvVariable backstageEnvVariable =
          backstageEnvVariableService.update(request.getEnvVariable(), harnessAccount);
      BackstageEnvVariableResponse backstageEnvVariableResponse = new BackstageEnvVariableResponse();
      backstageEnvVariableResponse.setEnvVariable(backstageEnvVariable);
      return Response.status(Response.Status.OK).entity(backstageEnvVariableResponse).build();
    } catch (Exception e) {
      log.error("Could not update backstage env variable for id {}", backstageEnvVariableId, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
  @Override
  public Response updateBackstageEnvVariables(@Valid BackstageEnvVariableBatchRequest body, String accountIdentifier) {
    idpCommonService.checkUserAuthorization();
    try {
      List<BackstageEnvVariable> responseVariables =
          backstageEnvVariableService.createOrUpdate(body.getEnvVariables(), accountIdentifier);
      return Response.status(Response.Status.OK)
          .entity(BackstageEnvVariableMapper.toResponseList(responseVariables))
          .build();
    } catch (Exception e) {
      log.error("Could not update all environment variables", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
