/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.helper;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.secrets.SecretPermissions.SECRET_ACCESS_PERMISSION;
import static io.harness.secrets.SecretPermissions.SECRET_RESOURCE_TYPE;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.reflect.Field;
import java.util.List;
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
}
