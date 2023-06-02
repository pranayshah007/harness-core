/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filters;

import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.PATH_CONNECTOR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.pms.yaml.ParameterField;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractor;
import io.harness.walktree.visitor.utilities.VisitorParentPathUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

@OwnedBy(HarnessTeam.CDP)
public class GenericEntityRefExtractorHelper implements EntityReferenceExtractor {
  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    return null;
  }

  @Override
  public Set<EntityDetailProtoDTO> addReference(Object object, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<String, Object> contextMap) {
    Set<EntityDetailProtoDTO> result = new HashSet<>();
    if (object instanceof WithConnectorRef) {
      WithConnectorRef withConnectorRef = (WithConnectorRef) object;
      addReference(accountIdentifier, orgIdentifier, projectIdentifier, contextMap, result,
          EntityTypeProtoEnum.CONNECTORS, withConnectorRef.extractConnectorRefs());
    }

    if (object instanceof WithSecretRef) {
      WithSecretRef withSecretRef = (WithSecretRef) object;
      addReference(accountIdentifier, orgIdentifier, projectIdentifier, contextMap, result, EntityTypeProtoEnum.SECRETS,
          withSecretRef.extractSecretRefs());
    }

    return result;
  }

  private void addReference(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      Map<String, Object> contextMap, Set<EntityDetailProtoDTO> result, EntityTypeProtoEnum entityType,
      Map<String, ParameterField<String>> entityRefs) {
    for (String key : entityRefs.keySet()) {
      ParameterField<String> entityRef = entityRefs.get(key);

      if (ParameterField.isNull(entityRef)) {
        continue;
      }

      if (!entityRef.isExpression() || NGExpressionUtils.matchesInputSetPattern(entityRef.getExpressionValue())) {
        String fullQualifiedDomainName = getFullQualifiedDomainName(contextMap, key);

        result.add(FilterCreatorHelper.convertToEntityDetailProtoDTO(
            accountIdentifier, orgIdentifier, projectIdentifier, fullQualifiedDomainName, entityRef, entityType));
      }
    }
  }

  @NotNull
  private String getFullQualifiedDomainName(Map<String, Object> contextMap, String key) {
    return VisitorParentPathUtils.getFullQualifiedDomainName(contextMap) + PATH_CONNECTOR + key;
  }
}
