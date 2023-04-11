/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.resources;

import static io.harness.idp.common.Constants.IDP_PERMISSION;
import static io.harness.idp.common.Constants.IDP_RESOURCE_TYPE;

import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.idp.gitintegration.mappers.ConnectorDetailsMapper;
import io.harness.idp.gitintegration.service.GitIntegrationService;
import io.harness.security.annotations.NextGenManagerAuth;
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
@NextGenManagerAuth
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class ConnectorInfoApiImpl implements ConnectorInfoApi {
  @Inject GitIntegrationService gitIntegrationService;

  @Override
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response getConnectorInfo(String harnessAccount) {
    CatalogConnectorEntity catalogConnectorEntity = gitIntegrationService.findDefaultConnectorDetails(harnessAccount);
    if (catalogConnectorEntity == null) {
      log.warn("Could not fetch connector details for accountId: {}", harnessAccount);
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.status(Response.Status.OK)
        .entity(ConnectorDetailsMapper.toResponse(catalogConnectorEntity))
        .build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response getConnectorInfoByProviderType(String providerType, String harnessAccount) {
    Optional<CatalogConnectorEntity> catalogConnector =
        gitIntegrationService.findByAccountIdAndProviderType(harnessAccount, providerType);
    if (catalogConnector.isEmpty()) {
      log.warn("Could not fetch connector details for accountId: {}, providerType: {}", harnessAccount, providerType);
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.status(Response.Status.OK)
        .entity(ConnectorDetailsMapper.toResponse(catalogConnector.get()))
        .build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response getConnectorsInfo(String harnessAccount) {
    List<CatalogConnectorEntity> catalogConnectorEntities =
        gitIntegrationService.getAllConnectorDetails(harnessAccount);
    return Response.status(Response.Status.OK)
        .entity(ConnectorDetailsMapper.toResponseList(catalogConnectorEntities))
        .build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response saveConnectorInfo(@Valid ConnectorInfoRequest body, String harnessAccount) {
    try {
      CatalogConnectorEntity catalogConnectorEntity =
          gitIntegrationService.saveConnectorDetails(harnessAccount, body.getConnectorDetails());
      return Response.status(Response.Status.CREATED)
          .entity(ConnectorDetailsMapper.toResponse(catalogConnectorEntity))
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
