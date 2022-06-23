/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.helper;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.preflight.PreFlightCheckMetadata;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.IdentifierRefProtoUtils;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractor;
import io.harness.walktree.visitor.utilities.VisitorParentPathUtils;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.PATH_CONNECTOR;

@OwnedBy(CDC)
public class InfraStructureDefinitionVisitorHelper implements ConfigValidator, EntityReferenceExtractor {
  @Override
  public void validate(Object object, ValidationVisitor visitor) {
    // Nothing to validate.
  }

  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    return InfraStructureDefinitionYaml.builder().build();
  }

  @Override
  public Set<EntityDetailProtoDTO> addReference(Object object, String accountIdentifier, String orgIdentifier,
                                                String projectIdentifier, Map<String, Object> contextMap) {
    InfraStructureDefinitionYaml infraStructureDefinitionYaml = (InfraStructureDefinitionYaml) object;
    Set<EntityDetailProtoDTO> result = new HashSet<>();

    String fullQualifiedDomainName =
            VisitorParentPathUtils.getFullQualifiedDomainName(contextMap) + PATH_CONNECTOR + YamlTypes.INFRASTRUCTURE_DEF;
    Map<String, String> metadata =
            new HashMap<>(Collections.singletonMap(PreFlightCheckMetadata.FQN, fullQualifiedDomainName));
    if (!infraStructureDefinitionYaml.getRef().isExpression()) {
      String infraRefIdentifier = infraStructureDefinitionYaml.getRef().getValue();
      IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
              infraRefIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, metadata);
      EntityDetailProtoDTO entityDetail =
              EntityDetailProtoDTO.newBuilder()
                      .setIdentifierRef(IdentifierRefProtoUtils.createIdentifierRefProtoFromIdentifierRef(identifierRef))
                      .setType(EntityTypeProtoEnum.INFRASTRUCTURE)
                      .build();
      result.add(entityDetail);
    } else {
      metadata.put(PreFlightCheckMetadata.EXPRESSION, infraStructureDefinitionYaml.getRef().getExpressionValue());
      IdentifierRef identifierRef = IdentifierRefHelper.createIdentifierRefWithUnknownScope(accountIdentifier,
              orgIdentifier, projectIdentifier, infraStructureDefinitionYaml.getRef().getExpressionValue(), metadata);
      EntityDetailProtoDTO entityDetail =
              EntityDetailProtoDTO.newBuilder()
                      .setIdentifierRef(IdentifierRefProtoUtils.createIdentifierRefProtoFromIdentifierRef(identifierRef))
                      .setType(EntityTypeProtoEnum.INFRASTRUCTURE)
                      .build();
      result.add(entityDetail);
    }
    return result;
  }
}
