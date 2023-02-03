/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.helper;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.PATH_CONNECTOR;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.environment.helper.EnvironmentYamlV2VisitorHelper;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.InfraDefinitionReferenceProtoDTO;
import io.harness.preflight.PreFlightCheckMetadata;
import io.harness.utils.IdentifierRefHelper;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractor;
import io.harness.walktree.visitor.utilities.VisitorParentPathUtils;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

import com.google.protobuf.StringValue;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
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
    InfraStructureDefinitionYaml infraYaml = (InfraStructureDefinitionYaml) object;
    final String infraRef = (String) infraYaml.getIdentifier().fetchFinalValue();
    try {
      final String fullQualifiedDomainName =
          VisitorParentPathUtils.getFullQualifiedDomainName(contextMap) + PATH_CONNECTOR + infraRef;
      final Map<String, String> metadata =
          new HashMap<>(Collections.singletonMap(PreFlightCheckMetadata.FQN, fullQualifiedDomainName));

      final String envRef = (String) contextMap.getOrDefault(EnvironmentYamlV2VisitorHelper.ENV_REF, "");

      if (EmptyPredicate.isEmpty(envRef)) {
        log.warn("environmentRef is not present in context map while updating infrastructure references.");
        return new HashSet<>();
      }

      final Set<EntityDetailProtoDTO> result = new HashSet<>();
      if (NGExpressionUtils.isRuntimeOrExpressionField(envRef)) {
        IdentifierRef envIdentifierRef = IdentifierRefHelper.createIdentifierRefWithUnknownScope(
            accountIdentifier, orgIdentifier, projectIdentifier, envRef, metadata);
        EntityDetailProtoDTO detailProto = getEntityDetailProtoDTO(infraRef, envIdentifierRef);
        result.add(detailProto);
      } else {
        IdentifierRef envIdentifierRef =
            IdentifierRefHelper.getIdentifierRef(envRef, accountIdentifier, orgIdentifier, projectIdentifier, metadata);
        EntityDetailProtoDTO detailProto = getEntityDetailProtoDTO(infraRef, envIdentifierRef);
        result.add(detailProto);
      }
      return result;
    } catch (Exception ex) {
      log.error("failed to add setup usage reference for infrastructure" + infraRef);
      return new HashSet<>();
    }
  }

  private EntityDetailProtoDTO getEntityDetailProtoDTO(String infraRef, IdentifierRef envIdentifierRef) {
    InfraDefinitionReferenceProtoDTO infraDefinitionReferenceProtoDTO =
        InfraDefinitionReferenceProtoDTO.newBuilder()
            .setAccountIdentifier(StringValue.of(envIdentifierRef.getAccountIdentifier()))
            .setOrgIdentifier(StringValue.of(defaultIfBlank(envIdentifierRef.getOrgIdentifier(), "")))
            .setProjectIdentifier(StringValue.of(defaultIfBlank(envIdentifierRef.getProjectIdentifier(), "")))
            .setIdentifier(StringValue.of(infraRef))
            .setEnvIdentifier(StringValue.of(envIdentifierRef.getIdentifier()))
            .build();
    return EntityDetailProtoDTO.newBuilder()
        .setInfraDefRef(infraDefinitionReferenceProtoDTO)
        .setType(EntityTypeProtoEnum.INFRASTRUCTURE)
        .build();
  }
}
