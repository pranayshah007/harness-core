/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.apis.resource;

import static io.harness.NGCommonEntityConstants.ACCOUNT_SCOPED_REQUEST_NON_NULL_ORG_PROJECT;
import static io.harness.NGCommonEntityConstants.DIFFERENT_IDENTIFIER_IN_PAYLOAD_AND_PARAM;
import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.connector.accesscontrol.ConnectorsAccessControlPermissions.DELETE_CONNECTOR_PERMISSION;
import static io.harness.connector.accesscontrol.ConnectorsAccessControlPermissions.EDIT_CONNECTOR_PERMISSION;
import static io.harness.connector.accesscontrol.ConnectorsAccessControlPermissions.VIEW_CONNECTOR_PERMISSION;
import static io.harness.exception.WingsException.USER;
import static io.harness.utils.PageUtils.getNGPageResponse;
import static io.harness.utils.PageUtils.getPageRequest;

import static java.util.Objects.nonNull;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorRegistryFactory;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.accesscontrol.ResourceTypes;
import io.harness.connector.helper.ConnectorRbacHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.connector.v1.AccountConnectorApi;
import io.harness.spec.server.connector.v1.model.ConnectorRequest;
import io.harness.spec.server.connector.v1.model.ConnectorResponse;
import io.harness.spec.server.connector.v1.model.ConnectorTestConnectionResponse;
import io.harness.utils.ApiUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

@NextGenManagerAuth
public class AccountConnectorApiImpl implements AccountConnectorApi {
  private final AccessControlClient accessControlClient;
  private final ConnectorService connectorService;
  private final ConnectorApiUtils connectorApiUtils;
  private final ConnectorRbacHelper connectorRbacHelper;

  @Inject
  public AccountConnectorApiImpl(AccessControlClient accessControlClient,
      @Named("connectorDecoratorService") ConnectorService connectorService, ConnectorApiUtils connectorApiUtils,
      ConnectorRbacHelper connectorRbacHelper) {
    this.accessControlClient = accessControlClient;
    this.connectorService = connectorService;
    this.connectorApiUtils = connectorApiUtils;
    this.connectorRbacHelper = connectorRbacHelper;
  }

  @NGAccessControlCheck(resourceType = ResourceTypes.CONNECTOR, permission = EDIT_CONNECTOR_PERMISSION)
  @Override
  public Response createAccountScopedConnector(ConnectorRequest request, @AccountIdentifier String account) {
    return createConnector(request, account);
  }

  @NGAccessControlCheck(resourceType = ResourceTypes.CONNECTOR, permission = DELETE_CONNECTOR_PERMISSION)
  @Override
  public Response deleteAccountScopedConnector(
      @ResourceIdentifier String connector, @AccountIdentifier String account) {
    if (HARNESS_SECRET_MANAGER_IDENTIFIER.equals(connector)) {
      throw new InvalidRequestException("Delete operation not supported for Harness Secret Manager");
    }
    Optional<ConnectorResponseDTO> connectorResponseDTO = connectorService.get(account, null, null, connector);
    if (!connectorResponseDTO.isPresent()) {
      throw new NotFoundException(String.format("Connector with identifier [%s] not found", connector));
    }
    boolean deleted = connectorService.delete(account, null, null, connector, false);

    if (!deleted) {
      throw new InvalidRequestException(
          String.format("Connector with identifier [%s] could not be deleted", connector));
    }
    ConnectorResponseDTO responseDTO = connectorResponseDTO.get();
    ConnectorResponse connectorResponse = connectorApiUtils.toConnectorResponse(responseDTO);

    return Response.ok().entity(connectorResponse).build();
  }

  @NGAccessControlCheck(resourceType = ResourceTypes.CONNECTOR, permission = VIEW_CONNECTOR_PERMISSION)
  @Override
  public Response getAccountScopedConnector(@ResourceIdentifier String connector, @AccountIdentifier String account) {
    Optional<ConnectorResponseDTO> connectorResponseDTO = connectorService.get(account, null, null, connector);
    if (!connectorResponseDTO.isPresent()) {
      throw new NotFoundException(String.format("Connector with identifier [%s] not found", connector));
    }
    ConnectorResponseDTO responseDTO = connectorResponseDTO.get();
    ConnectorResponse connectorResponse = connectorApiUtils.toConnectorResponse(responseDTO);

    return Response.ok().entity(connectorResponse).build();
  }

  @NGAccessControlCheck(resourceType = ResourceTypes.CONNECTOR, permission = VIEW_CONNECTOR_PERMISSION)
  @Override
  public Response getAccountScopedConnectors(Boolean recursive, String searchTerm, Integer page, Integer limit,
      String sort, String order, @AccountIdentifier String account) {
    PageResponse<ConnectorResponseDTO> pageResponse = getNGPageResponse(connectorService.list(account, null, null, null,
        null, searchTerm, recursive, null, getPageRequest(ApiUtils.getPageRequest(page, limit, sort, order))));

    List<ConnectorResponseDTO> connectorResponseDTOS = pageResponse.getContent();

    List<ConnectorResponse> connectorResponses = connectorApiUtils.toConnectorResponses(connectorResponseDTOS);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, pageResponse.getTotalItems(), page, limit);

    return responseBuilderWithLinks.entity(connectorResponses).build();
  }

  @Override
  public Response testAccountScopedConnector(String connector, String account) {
    connectorService.get(account, null, null, connector)
        .map(connectorResponseDTO
            -> connectorRbacHelper.checkSecretRuntimeAccessWithConnectorDTO(
                connectorResponseDTO.getConnector(), account))
        .orElseThrow(()
                         -> new ConnectorNotFoundException(
                             String.format("No connector found with identifier %s", connector), USER));

    ConnectorValidationResult connectorValidationResult =
        connectorService.testConnection(account, null, null, connector);
    ConnectorTestConnectionResponse connectorTestConnectionResponse =
        connectorApiUtils.toConnectorTestConnectionResponse(connectorValidationResult);

    return Response.ok().entity(connectorTestConnectionResponse).build();
  }

  @NGAccessControlCheck(resourceType = ResourceTypes.CONNECTOR, permission = EDIT_CONNECTOR_PERMISSION)
  @Override
  public Response updateAccountScopedConnector(
      ConnectorRequest connectorRequest, @ResourceIdentifier String connector, @AccountIdentifier String account) {
    if (!Objects.equals(connectorRequest.getConnector().getIdentifier(), connector)) {
      throw new InvalidRequestException(DIFFERENT_IDENTIFIER_IN_PAYLOAD_AND_PARAM, USER);
    }
    if (nonNull(connectorRequest.getConnector().getOrg()) || nonNull(connectorRequest.getConnector().getProject())) {
      throw new InvalidRequestException(ACCOUNT_SCOPED_REQUEST_NON_NULL_ORG_PROJECT, USER);
    }

    if (HARNESS_SECRET_MANAGER_IDENTIFIER.equals(connectorRequest.getConnector().getIdentifier())) {
      throw new InvalidRequestException("Update operation not supported for Harness Secret Manager");
    }
    ConnectorDTO connectorDTO = connectorApiUtils.toConnectorDTO(connectorRequest);
    ConnectorResponseDTO connectorResponseDTO = connectorService.update(connectorDTO, account);
    ConnectorResponse connectorResponse = connectorApiUtils.toConnectorResponse(connectorResponseDTO);

    return Response.ok().entity(connectorResponse).build();
  }

  private Response createConnector(ConnectorRequest request, String account) {
    if (nonNull(request.getConnector().getOrg()) || nonNull(request.getConnector().getProject())) {
      throw new InvalidRequestException(ACCOUNT_SCOPED_REQUEST_NON_NULL_ORG_PROJECT, USER);
    }
    if (HARNESS_SECRET_MANAGER_IDENTIFIER.equals(request.getConnector().getIdentifier())) {
      throw new InvalidRequestException(
          String.format("%s cannot be used as connector identifier", HARNESS_SECRET_MANAGER_IDENTIFIER), USER);
    }
    if (request.getConnector().getSpec().getType().value() == ConnectorType.LOCAL.getDisplayName()) {
      throw new InvalidRequestException("Local Secret Manager creation not supported", USER);
    }
    Map<String, String> connectorAttributes = new HashMap<>();
    connectorAttributes.put("category",
        ConnectorRegistryFactory.getConnectorCategory(connectorApiUtils.getConnectorType(request.getConnector()))
            .toString());
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(account, request.getConnector().getOrg(), request.getConnector().getProject()),
        Resource.of(ResourceTypes.CONNECTOR, null, connectorAttributes), EDIT_CONNECTOR_PERMISSION);
    ConnectorResponseDTO connectorResponseDTO =
        connectorService.create(connectorApiUtils.toConnectorDTO(request), account);
    ConnectorResponse connectorResponse = connectorApiUtils.toConnectorResponse(connectorResponseDTO);

    return Response.status(Response.Status.CREATED).entity(connectorResponse).build();
  }
}
