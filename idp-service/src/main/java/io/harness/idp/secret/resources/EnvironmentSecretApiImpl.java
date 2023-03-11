/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.secret.resources;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.secret.mappers.EnvironmentSecretMapper;
import io.harness.idp.secret.service.EnvironmentSecretService;
import io.harness.spec.server.idp.v1.EnvironmentSecretApi;
import io.harness.spec.server.idp.v1.model.EnvironmentSecret;
import io.harness.spec.server.idp.v1.model.EnvironmentSecretRequest;
import io.harness.spec.server.idp.v1.model.EnvironmentSecretResponse;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class EnvironmentSecretApiImpl implements EnvironmentSecretApi {
  private EnvironmentSecretService environmentSecretService;

  @Override
  public Response createEnvironmentSecret(@Valid EnvironmentSecretRequest body, String harnessAccount) {
    EnvironmentSecret secret;
    try {
      secret = environmentSecretService.saveAndSyncK8sSecret(body.getSecret(), harnessAccount);
    } catch (Exception e) {
      log.error("Could not create environment secret", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
    EnvironmentSecretResponse secretResponse = new EnvironmentSecretResponse();
    secretResponse.setSecret(secret);
    return Response.status(Response.Status.CREATED).entity(secretResponse).build();
  }

  @Override
  public Response createEnvironmentSecrets(@Valid List<EnvironmentSecretRequest> body, String harnessAccount) {
    final List<EnvironmentSecret> requestSecrets = new ArrayList<>();
    body.forEach(environmentSecretRequest -> requestSecrets.add(environmentSecretRequest.getSecret()));
    List<EnvironmentSecret> responseSecrets;
    try {
      responseSecrets = environmentSecretService.saveAndSyncK8sSecrets(requestSecrets, harnessAccount);
    } catch (Exception e) {
      log.error("Could not create all environment secrets", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
    return Response.status(Response.Status.CREATED)
        .entity(EnvironmentSecretMapper.toResponseList(responseSecrets))
        .build();
  }

  @Override
  public Response deleteEnvironmentSecret(String secretIdentifier, String harnessAccount) {
    try {
      environmentSecretService.delete(secretIdentifier, harnessAccount);
    } catch (Exception e) {
      log.error("Could not delete environment secret for id {}", secretIdentifier, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  public Response deleteEnvironmentSecrets(List<String> environmentSecrets, String accountIdentifier) {
    try {
      environmentSecretService.deleteMulti(environmentSecrets, accountIdentifier);
    } catch (Exception e) {
      log.error("Could not delete all environment secrets [{}]", environmentSecrets, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  public Response getEnvironmentSecret(String secretIdentifier, String harnessAccount) {
    Optional<EnvironmentSecret> secretOpt =
        environmentSecretService.findByIdAndAccountIdentifier(secretIdentifier, harnessAccount);
    EnvironmentSecretResponse secretResponse = new EnvironmentSecretResponse();
    if (secretOpt.isEmpty()) {
      log.warn("Could not fetch environment secret for id {}", secretIdentifier);
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    secretResponse.setSecret(secretOpt.get());
    return Response.status(Response.Status.CREATED).entity(secretResponse).build();
  }

  @Override
  public Response getEnvironmentSecrets(
      @Valid EnvironmentSecretRequest body, String harnessAccount, Integer page, Integer limit, String sort) {
    List<EnvironmentSecret> secrets = environmentSecretService.findByAccountIdentifier(harnessAccount);
    return Response.status(Response.Status.OK).entity(EnvironmentSecretMapper.toResponseList(secrets)).build();
  }

  @Override
  public Response syncEnvironmentSecrets(String harnessAccount) {
    List<EnvironmentSecret> secrets = environmentSecretService.findByAccountIdentifier(harnessAccount);
    try {
      environmentSecretService.syncK8sSecret(secrets, harnessAccount);
    } catch (Exception e) {
      log.error("Could not create all environment secrets", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  public Response updateEnvironmentSecret(
      String secretIdentifier, @Valid EnvironmentSecretRequest body, String harnessAccount) {
    EnvironmentSecret secret;
    try {
      secret = environmentSecretService.updateAndSyncK8sSecret(body.getSecret(), harnessAccount);
    } catch (Exception e) {
      log.error("Could not update environment secret for id {}", secretIdentifier, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
    EnvironmentSecretResponse secretResponse = new EnvironmentSecretResponse();
    secretResponse.setSecret(secret);
    return Response.status(Response.Status.OK).entity(secretResponse).build();
  }

  @Override
  public Response updateEnvironmentSecrets(@Valid List<EnvironmentSecretRequest> body, String accountIdentifier) {
    final List<EnvironmentSecret> requestSecrets = new ArrayList<>();
    body.forEach(environmentSecretRequest -> requestSecrets.add(environmentSecretRequest.getSecret()));
    List<EnvironmentSecret> responseSecrets;
    try {
      responseSecrets = environmentSecretService.updateAndSyncK8sSecrets(requestSecrets, accountIdentifier);
    } catch (Exception e) {
      log.error("Could not create all environment secrets", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
    return Response.status(Response.Status.CREATED)
        .entity(EnvironmentSecretMapper.toResponseList(responseSecrets))
        .build();
  }
}
