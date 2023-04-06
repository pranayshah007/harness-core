/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.apis.resource;

import static io.harness.NGCommonEntityConstants.DIFFERENT_IDENTIFIER_IN_PAYLOAD_AND_PARAM;
import static io.harness.NGCommonEntityConstants.DIFFERENT_ORG_IN_PAYLOAD_AND_PARAM;
import static io.harness.NGCommonEntityConstants.ORG_SCOPED_REQUEST_NON_NULL_PROJECT;
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
import io.harness.accesscontrol.OrgIdentifier;
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
import io.harness.spec.server.connector.v1.OrgConnectorApi;
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
public class OrgConnectorApiImpl implements OrgConnectorApi {
  private final AccessControlClient accessControlClient;
  private final ConnectorService connectorService;
  private final ConnectorApiUtils connectorApiUtils;
  private final ConnectorRbacHelper connectorRbacHelper;

  @Inject
  public OrgConnectorApiImpl(AccessControlClient accessControlClient,
      @Named("connectorDecoratorService") ConnectorService connectorService, ConnectorApiUtils connectorApiUtils,
      ConnectorRbacHelper connectorRbacHelper) {
    this.accessControlClient = accessControlClient;
    this.connectorService = connectorService;
    this.connectorApiUtils = connectorApiUtils;
    this.connectorRbacHelper = connectorRbacHelper;
  }

  @NGAccessControlCheck(resourceType = ResourceTypes.CONNECTOR, permission = EDIT_CONNECTOR_PERMISSION)
  @Override
  public Response createOrgScopedConnector(
      ConnectorRequest request, @OrgIdentifier String org, @AccountIdentifier String account) {
    return createConnector(request, account, org);
  }

  @NGAccessControlCheck(resourceType = ResourceTypes.CONNECTOR, permission = DELETE_CONNECTOR_PERMISSION)
  @Override
  public Response deleteOrgScopedConnector(
      @OrgIdentifier String org, @ResourceIdentifier String connector, @AccountIdentifier String account) {
    if (HARNESS_SECRET_MANAGER_IDENTIFIER.equals(connector)) {
      throw new InvalidRequestException("Delete operation not supported for Harness Secret Manager");
    }
    Optional<ConnectorResponseDTO> connectorResponseDTO = connectorService.get(account, org, null, connector);
    if (!connectorResponseDTO.isPresent()) {
      throw new NotFoundException(String.format("Connector with identifier [%s] not found", connector));
    }
    boolean deleted = connectorService.delete(account, org, null, connector, false);

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
  public Response getOrgScopedConnector(
      @OrgIdentifier String org, @ResourceIdentifier String connector, @AccountIdentifier String account) {
    Optional<ConnectorResponseDTO> connectorResponseDTO = connectorService.get(account, org, null, connector);
    if (!connectorResponseDTO.isPresent()) {
      throw new NotFoundException(String.format("Connector with identifier [%s] not found", connector));
    }
    ConnectorResponseDTO responseDTO = connectorResponseDTO.get();
    ConnectorResponse connectorResponse = connectorApiUtils.toConnectorResponse(responseDTO);

    return Response.ok().entity(connectorResponse).build();
  }

  @NGAccessControlCheck(resourceType = ResourceTypes.CONNECTOR, permission = VIEW_CONNECTOR_PERMISSION)
  @Override
  public Response getOrgScopedConnectors(@OrgIdentifier String org, Boolean recursive, String searchTerm, Integer page,
      Integer limit, String sort, String order, @AccountIdentifier String account) {
    PageResponse<ConnectorResponseDTO> pageResponse = getNGPageResponse(connectorService.list(account, null, org, null,
        null, searchTerm, recursive, null, getPageRequest(ApiUtils.getPageRequest(page, limit, sort, order))));

    List<ConnectorResponseDTO> connectorResponseDTOS = pageResponse.getContent();

    List<ConnectorResponse> connectorResponses = connectorApiUtils.toConnectorResponses(connectorResponseDTOS);

    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, pageResponse.getTotalItems(), page, limit);

    return responseBuilderWithLinks.entity(connectorResponses).build();
  }

  @Override
  public Response testOrgScopedConnector(String org, String connector, String account) {
    connectorService.get(account, org, null, connector)
        .map(connectorResponseDTO
            -> connectorRbacHelper.checkSecretRuntimeAccessWithConnectorDTO(
                connectorResponseDTO.getConnector(), account))
        .orElseThrow(()
                         -> new ConnectorNotFoundException(
                             String.format("No connector found with identifier %s", connector), USER));

    ConnectorValidationResult connectorValidationResult =
        connectorService.testConnection(account, org, null, connector);
    ConnectorTestConnectionResponse connectorTestConnectionResponse =
        connectorApiUtils.toConnectorTestConnectionResponse(connectorValidationResult);

    return Response.ok().entity(connectorTestConnectionResponse).build();
  }

  @NGAccessControlCheck(resourceType = ResourceTypes.CONNECTOR, permission = EDIT_CONNECTOR_PERMISSION)
  @Override
  public Response updateOrgScopedConnector(ConnectorRequest connectorRequest, @OrgIdentifier String org,
      @ResourceIdentifier String connector, @AccountIdentifier String account) {
    if (!Objects.equals(connectorRequest.getConnector().getIdentifier(), connector)) {
      throw new InvalidRequestException(DIFFERENT_IDENTIFIER_IN_PAYLOAD_AND_PARAM, USER);
    }
    if (!Objects.equals(connectorRequest.getConnector().getOrg(), org)) {
      throw new InvalidRequestException(DIFFERENT_ORG_IN_PAYLOAD_AND_PARAM, USER);
    }
    if (nonNull(connectorRequest.getConnector().getProject())) {
      throw new InvalidRequestException(ORG_SCOPED_REQUEST_NON_NULL_PROJECT, USER);
    }

    if (HARNESS_SECRET_MANAGER_IDENTIFIER.equals(connectorRequest.getConnector().getIdentifier())) {
      throw new InvalidRequestException("Update operation not supported for Harness Secret Manager");
    }
    ConnectorDTO connectorDTO = connectorApiUtils.toConnectorDTO(connectorRequest);
    ConnectorResponseDTO connectorResponseDTO = connectorService.update(connectorDTO, account);
    ConnectorResponse connectorResponse = connectorApiUtils.toConnectorResponse(connectorResponseDTO);

    return Response.ok().entity(connectorResponse).build();
  }

  private Response createConnector(ConnectorRequest request, String account, String org) {
    if (!Objects.equals(request.getConnector().getOrg(), org)) {
      throw new InvalidRequestException(DIFFERENT_ORG_IN_PAYLOAD_AND_PARAM, USER);
    }
    if (nonNull(request.getConnector().getProject())) {
      throw new InvalidRequestException(ORG_SCOPED_REQUEST_NON_NULL_PROJECT, USER);
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
