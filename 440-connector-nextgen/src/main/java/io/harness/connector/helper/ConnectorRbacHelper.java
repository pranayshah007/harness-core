/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.helper;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.connector.accesscontrol.ConnectorsAccessControlPermissions.VIEW_CONNECTOR_PERMISSION;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.secrets.SecretPermissions.SECRET_ACCESS_PERMISSION;
import static io.harness.secrets.SecretPermissions.SECRET_RESOURCE_TYPE;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.accesscontrol.ResourceTypes;
import io.harness.connector.entities.Connector;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.EntityScopeInfo;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(DX)
public class ConnectorRbacHelper {
  @Inject private AccessControlClient accessControlClient;

  public boolean checkSecretRuntimeAccessWithConnectorDTO(ConnectorInfoDTO connectorInfoDTO, String accountIdentifier) {
    List<DecryptableEntity> decryptableEntities = null;
    ConnectorConfigDTO connectorConfigDTO = connectorInfoDTO.getConnectorConfig();
    decryptableEntities = connectorConfigDTO.getDecryptableEntities();
    checkForSecretRuntimeAccess(decryptableEntities, accountIdentifier, connectorInfoDTO.getOrgIdentifier(),
        connectorInfoDTO.getProjectIdentifier());
    return true;
  }

  public void checkForSecretRuntimeAccess(List<DecryptableEntity> decryptableEntityList, String accountIdentifier,
      String orgIdentifier, String projectIdentifier) {
    if (isEmpty(decryptableEntityList)) {
      return;
    }
    NGAccess ngAccess = BaseNGAccess.builder()
                            .accountIdentifier(accountIdentifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .build();

    for (DecryptableEntity decryptableEntity : decryptableEntityList) {
      List<Field> secretFields = decryptableEntity.getSecretReferenceFields();
      for (Field field : secretFields) {
        SecretRefData secretRefData = null;
        try {
          field.setAccessible(true);
          secretRefData = (SecretRefData) field.get(decryptableEntity);
        } catch (IllegalAccessException ex) {
          log.error("Error reading the secret data", ex);
          throw new UnexpectedException("Error processing the data");
        }
        if (secretRefData != null && !secretRefData.isNull()) {
          validateTheScopeOfTheSecret(secretRefData, ngAccess);
          IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(secretRefData.toSecretRefStringValue(),
              ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
          accessControlClient.checkForAccessOrThrow(
              ResourceScope.of(identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
                  identifierRef.getProjectIdentifier()),
              Resource.of(SECRET_RESOURCE_TYPE, secretRefData.getIdentifier()), SECRET_ACCESS_PERMISSION);
        }
      }
    }
  }

  private void validateTheScopeOfTheSecret(SecretRefData secretRefData, NGAccess ngAccess) {
    if (isNotBlank(ngAccess.getProjectIdentifier())) {
      // It is a project level entity
      return;
    } else if (isNotBlank(ngAccess.getOrgIdentifier())) {
      // It is a org level entity
      if (secretRefData.getScope() == Scope.PROJECT) {
        throw new InvalidRequestException("The project level secret cannot be used at a org level");
      }
    } else {
      // It is a account level entity
      if (secretRefData.getScope() == Scope.PROJECT || secretRefData.getScope() == Scope.ORG) {
        throw new InvalidRequestException(String.format(
            "The %s level secret cannot be used at account level", secretRefData.getScope().getYamlRepresentation()));
      }
    }
  }

  public List<Connector> getPermitted(List<Connector> connectors) {
    if (isEmpty(connectors)) {
      throw new NGAccessDeniedException(
          String.format("Missing permission %s on %s", VIEW_CONNECTOR_PERMISSION, ResourceTypes.CONNECTOR), USER,
          emptyList());
    }
    Map<EntityScopeInfo, List<Connector>> connectorsMap =
        connectors.stream().collect(groupingBy(ConnectorRbacHelper::getEntityScopeInfoFromConnector));
    List<PermissionCheckDTO> permissionChecks =
        connectors.stream()
            .map(connector
                -> PermissionCheckDTO.builder()
                       .permission(VIEW_CONNECTOR_PERMISSION)
                       .resourceIdentifier(connector.getIdentifier())
                       .resourceScope(ResourceScope.of(connector.getAccountIdentifier(), connector.getOrgIdentifier(),
                           connector.getProjectIdentifier()))
                       .resourceType(ResourceTypes.CONNECTOR)
                       .build())
            .collect(Collectors.toList());
    AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccessOrThrow(permissionChecks);

    List<Connector> permittedConnectors = new ArrayList<>();
    for (AccessControlDTO accessControlDTO : accessCheckResponse.getAccessControlList()) {
      if (accessControlDTO.isPermitted()) {
        permittedConnectors.add(
            connectorsMap.get(ConnectorRbacHelper.getEntityScopeInfoFromAccessControlDTO(accessControlDTO)).get(0));
      }
    }
    return permittedConnectors;
  }

  private static EntityScopeInfo getEntityScopeInfoFromConnector(Connector connector) {
    return EntityScopeInfo.builder()
        .accountIdentifier(connector.getAccountIdentifier())
        .orgIdentifier(isBlank(connector.getOrgIdentifier()) ? null : connector.getOrgIdentifier())
        .projectIdentifier(isBlank(connector.getProjectIdentifier()) ? null : connector.getProjectIdentifier())
        .identifier(connector.getIdentifier())
        .build();
  }

  private static EntityScopeInfo getEntityScopeInfoFromAccessControlDTO(AccessControlDTO accessControlDTO) {
    return EntityScopeInfo.builder()
        .accountIdentifier(accessControlDTO.getResourceScope().getAccountIdentifier())
        .orgIdentifier(isBlank(accessControlDTO.getResourceScope().getOrgIdentifier())
                ? null
                : accessControlDTO.getResourceScope().getOrgIdentifier())
        .projectIdentifier(isBlank(accessControlDTO.getResourceScope().getProjectIdentifier())
                ? null
                : accessControlDTO.getResourceScope().getProjectIdentifier())
        .identifier(accessControlDTO.getResourceIdentifier())
        .build();
  }
}
