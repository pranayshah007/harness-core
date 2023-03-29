/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.resources;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.idp.gitintegration.mappers.ConnectorDetailsMapper;
import io.harness.idp.gitintegration.service.GitIntegrationService;
import io.harness.spec.server.idp.v1.ConnectorInfoApi;
import io.harness.spec.server.idp.v1.model.ConnectorInfoRequest;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class ConnectorInfoApiImpl implements ConnectorInfoApi {
  @Inject GitIntegrationService gitIntegrationService;

  @Override
  public Response getConnectorInfoByProviderType(String providerType, String harnessAccount) {
    Optional<CatalogConnectorEntity> catalogConnector =
        gitIntegrationService.findByAccountIdAndProviderType(harnessAccount, providerType);
    if (catalogConnector.isEmpty()) {
      log.warn("Could not fetch connector details for accountId: {}, providerType: {}", harnessAccount, providerType);
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.status(Response.Status.OK).entity(ConnectorDetailsMapper.toDTO(catalogConnector.get())).build();
  }

  @Override
  public Response getConnectorsInfo(String harnessAccount) {
    List<CatalogConnectorEntity> catalogConnectorEntities =
        gitIntegrationService.getAllConnectorDetails(harnessAccount);
    return Response.status(Response.Status.OK)
        .entity(ConnectorDetailsMapper.toResponseList(catalogConnectorEntities))
        .build();
  }

  @Override
  public Response saveConnectorInfo(@Valid ConnectorInfoRequest body, String harnessAccount) {
    try {
      CatalogConnectorEntity catalogConnectorEntity =
          gitIntegrationService.saveConnectorDetails(harnessAccount, body.getConnectorDetails());
      return Response.status(Response.Status.CREATED)
          .entity(ConnectorDetailsMapper.toDTO(catalogConnectorEntity))
          .build();
    } catch (Exception e) {
      String errorMessage =
          String.format("Error occurred while saving connectorInfo for accountId: [%s], connectorId: [%s]",
              harnessAccount, body.getConnectorDetails().getIdentifier());
      log.error(errorMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
