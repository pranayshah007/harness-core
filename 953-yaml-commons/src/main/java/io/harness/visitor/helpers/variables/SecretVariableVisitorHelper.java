/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.visitor.helpers.variables;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.secrets.SecretEntityUtils;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractor;
import io.harness.walktree.visitor.utilities.VisitorParentPathUtils;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class SecretVariableVisitorHelper implements ConfigValidator, EntityReferenceExtractor {
  @Override
  public void validate(Object object, ValidationVisitor visitor) {
    // Nothing to validate.
  }

  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    SecretNGVariable element = (SecretNGVariable) originalElement;
    return SecretNGVariable.builder().name(element.getName()).type(NGVariableType.SECRET).build();
  }

  @Override
  public Set<EntityDetailProtoDTO> addReference(Object object, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<String, Object> contextMap) {
    SecretNGVariable secretNGVariable = (SecretNGVariable) object;
    Set<EntityDetailProtoDTO> result = new HashSet<>();
    if (ParameterField.isNull(secretNGVariable.getValue())) {
      return result;
    }

    // do not add reference if the secret value is empty
    if (!secretNGVariable.getValue().isExpression()
        && EmptyPredicate.isEmpty(secretNGVariable.getValue().getValue().getIdentifier())) {
      return result;
    }

    String fullQualifiedDomainName = VisitorParentPathUtils.getFullQualifiedDomainName(contextMap);
    try {
      result.add(SecretEntityUtils.convertSecretToEntityDetailProtoDTO(
          accountIdentifier, orgIdentifier, projectIdentifier, fullQualifiedDomainName, secretNGVariable.getValue()));
      return result;
    } catch (Exception ex) {
      throw new InvalidRequestException("Failed to create reference for secret " + secretNGVariable.getName(), ex);
    }
  }
}
