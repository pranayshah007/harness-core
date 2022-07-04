/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filters;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.PATH_CONNECTOR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.common.ParameterFieldHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractor;
import io.harness.walktree.visitor.utilities.VisitorParentPathUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@OwnedBy(CDP)
public class SecretFileRefExtractorHelper implements EntityReferenceExtractor {
  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    return null;
  }

  @Override
  public Set<EntityDetailProtoDTO> addReference(Object object, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<String, Object> contextMap) {
    if (!(object instanceof WithFileRefs)) {
      throw new InvalidRequestException(String.format(
          "Object of class %s does not implement WithFileRefs, and hence can't be annotated with SecretFileRefExtractorHelper as its visitor helper",
          object.getClass().toString()));
    }

    WithFileRefs withFileRefs = (WithFileRefs) object;
    Set<EntityDetailProtoDTO> result = new HashSet<>();
    // extract secret files
    for (String key : withFileRefs.extractFileRefs().keySet()) {
      ParameterField<List<String>> secretFileRefs = withFileRefs.extractFileRefs().get(key);
      if (ParameterField.isNull(secretFileRefs)) {
        continue;
      }

      if (!secretFileRefs.isExpression()
          || NGExpressionUtils.matchesInputSetPattern(secretFileRefs.getExpressionValue())) {
        List<String> fileRefListValue = ParameterFieldHelper.getParameterFieldValue(secretFileRefs);
        if (fileRefListValue != null) {
          fileRefListValue.stream().filter(Objects::nonNull).forEach(secretFieldRef -> {
            String fullQualifiedDomainName =
                VisitorParentPathUtils.getFullQualifiedDomainName(contextMap) + PATH_CONNECTOR + key;
            result.add(FilterCreatorHelper.convertToEntityDetailProtoDTO(accountIdentifier, orgIdentifier,
                projectIdentifier, fullQualifiedDomainName, ParameterField.createValueField(secretFieldRef),
                EntityTypeProtoEnum.SECRETS));
          });
        }
      }
    }

    return result;
  }
}
