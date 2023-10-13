/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.visitor.helpers;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.grpc.utils.StringValueUtils.getStringFromStringValue;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.common.NGExpressionUtils;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.impl.SecretRefInputValidationHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.encryption.SecretRefData;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.utils.IdentifierRefHelper;
import io.harness.walktree.visitor.entityreference.SecretReferenceExtractor;
import io.harness.walktree.visitor.entityreference.beans.VisitedSecretReference;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class SecretConnectorRefExtractorHelper extends ConnectorRefExtractorHelper implements SecretReferenceExtractor {
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Inject private SecretRefInputValidationHelper secretRefInputValidationHelper;

  @Override
  public Set<VisitedSecretReference> addSecretReference(Object object, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<String, Object> contextMap) {
    final Set<EntityDetailProtoDTO> connectorDetails =
        addReference(object, accountIdentifier, orgIdentifier, projectIdentifier, contextMap);
    final Set<VisitedSecretReference> secretReferences = new HashSet<>(connectorDetails.size());

    for (EntityDetailProtoDTO connectorDetail : connectorDetails) {
      IdentifierRefProtoDTO connectorIdentifierRef = connectorDetail.getIdentifierRef();
      if (NGExpressionUtils.matchesInputSetPattern(getStringFromStringValue(connectorIdentifierRef.getIdentifier()))) {
        continue;
      }

      Optional<ConnectorResponseDTO> connector =
          connectorService.get(getStringFromStringValue(connectorIdentifierRef.getAccountIdentifier()),
              getStringFromStringValue(connectorIdentifierRef.getOrgIdentifier()),
              getStringFromStringValue(connectorIdentifierRef.getProjectIdentifier()),
              getStringFromStringValue(connectorIdentifierRef.getIdentifier()));

      if (connector.isEmpty()) {
        log.warn("Unable to retrieve connector for identifier: [accountId: {}, orgId: {}, projectId: {}, ref: {}]",
            connectorIdentifierRef.getAccountIdentifier(), connectorIdentifierRef.getOrgIdentifier(),
            connectorIdentifierRef.getProjectIdentifier(), connectorIdentifierRef.getIdentifier());
        continue;
      }

      Set<IdentifierRef> newSecretRefs = getSecretRefsFromConnector(
          accountIdentifier, orgIdentifier, projectIdentifier, connector.get().getConnector());
      if (isNotEmpty(newSecretRefs)) {
        newSecretRefs.forEach(secretRef
            -> secretReferences.add(
                VisitedSecretReference.builder().secretRef(secretRef).referredBy(connectorDetail).build()));
      }
    }

    return secretReferences;
  }

  private Set<IdentifierRef> getSecretRefsFromConnector(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, ConnectorInfoDTO connectorInfoDTO) {
    if (connectorInfoDTO == null) {
      return Collections.emptySet();
    }

    final Set<IdentifierRef> secretRefs = new HashSet<>();
    Map<String, SecretRefData> fieldsData = secretRefInputValidationHelper.getDecryptableFieldsData(
        connectorInfoDTO.getConnectorConfig().getDecryptableEntities());
    if (isNotEmpty(fieldsData)) {
      fieldsData.values()
          .stream()
          .filter(Objects::nonNull)
          .filter(secretRefData -> !secretRefData.isNull())
          .forEach(secretRefData
              -> secretRefs.add(IdentifierRefHelper.getIdentifierRef(
                  secretRefData.toSecretRefStringValue(), accountIdentifier, orgIdentifier, projectIdentifier)));
    }

    return secretRefs;
  }
}
