/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.helper;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.PATH_CONNECTOR;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.environment.helper.EnvironmentYamlV2VisitorHelper;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.InfraDefinitionReferenceProtoDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.FQNMapGenerator;
import io.harness.pms.yaml.ParameterField;
import io.harness.preflight.PreFlightCheckMetadata;
import io.harness.setupusage.InfrastructureEntitySetupUsageHelper;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.IdentifierRefProtoUtils;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractor;
import io.harness.walktree.visitor.utilities.VisitorParentPathUtils;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.protobuf.StringValue;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
@Slf4j
public class InfraStructureDefinitionVisitorHelper implements ConfigValidator, EntityReferenceExtractor {
  @Inject InfrastructureEntityService infrastructureEntityService;

  @Inject InfrastructureEntitySetupUsageHelper infrastructureEntitySetupUsageHelper;

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

        if (ParameterField.isNotNull(infraYaml.getInputs()) && !infraYaml.getInputs().isExpression()) {
          if (EmptyPredicate.isEmpty(infraRef) || NGExpressionUtils.isRuntimeOrExpressionField(infraRef)) {
            return result;
          }

          Optional<InfrastructureEntity> infraEntity = infrastructureEntityService.get(
              envIdentifierRef.getAccountIdentifier(), envIdentifierRef.getOrgIdentifier(),
              envIdentifierRef.getProjectIdentifier(), envIdentifierRef.getIdentifier(), infraRef);

          if (infraEntity.isEmpty()) {
            return result;
          }

          Set<EntityDetailProtoDTO> entityDetailProtoDTOS =
              infrastructureEntitySetupUsageHelper.getAllReferredEntities(infraEntity.get());

          Map<String, Object> map = new LinkedHashMap<>();
          map.put("infrastructureDefinition", infraYaml.getInputs().getValue());
          Map<FQN, Object> fqnToValueMap = FQNMapGenerator.generateFQNMap(JsonPipelineUtils.asTree(map));
          Map<String, Object> fqnStringToValueMap = new HashMap<>();
          fqnToValueMap.forEach((fqn, value) -> fqnStringToValueMap.put(fqn.getExpressionFqn(), value));

          for (EntityDetailProtoDTO entityDetailProtoDTO : entityDetailProtoDTOS) {
            if (isReferredEntityForRuntimeInput(entityDetailProtoDTO.getIdentifierRef())) {
              JsonNode obj = (JsonNode) fqnStringToValueMap.get(
                  entityDetailProtoDTO.getIdentifierRef().getMetadataMap().get("fqn"));
              if (obj != null) {
                EntityDetailProtoDTO entityDetailProtoDTOFinal =
                    convertToEntityDetailProtoDTO(accountIdentifier, orgIdentifier, projectIdentifier,
                        entityDetailProtoDTO.getIdentifierRef().getMetadataMap().get("fqn"), obj.textValue(),
                        entityDetailProtoDTO.getType(), true);
                result.add(entityDetailProtoDTOFinal);
              }
            }
          }
        }
      }
      return result;
    } catch (Exception ex) {
      log.error("failed to add setup usage reference for infrastructure" + infraRef);
      return new HashSet<>();
    }
  }

  private boolean isReferredEntityForRuntimeInput(IdentifierRefProtoDTO identifierRefOfReferredEntity) {
    return identifierRefOfReferredEntity.getMetadataMap() != null
        && isNotEmpty(identifierRefOfReferredEntity.getMetadataMap().get(PreFlightCheckMetadata.FQN))
        && isNotEmpty(identifierRefOfReferredEntity.getMetadataMap().get(PreFlightCheckMetadata.EXPRESSION))
        && NGExpressionUtils.matchesInputSetPattern(identifierRefOfReferredEntity.getIdentifier().getValue());
  }

  private EntityDetailProtoDTO getEntityDetailProtoDTO(String infraRef, IdentifierRef envIdentifierRef) {
    InfraDefinitionReferenceProtoDTO.Builder infraDefinitionReferenceProtoDTO =
        InfraDefinitionReferenceProtoDTO.newBuilder()
            .setAccountIdentifier(StringValue.of(envIdentifierRef.getAccountIdentifier()))
            .setOrgIdentifier(StringValue.of(defaultIfBlank(envIdentifierRef.getOrgIdentifier(), "")))
            .setProjectIdentifier(StringValue.of(defaultIfBlank(envIdentifierRef.getProjectIdentifier(), "")))
            .setIdentifier(StringValue.of(infraRef))
            .setEnvIdentifier(StringValue.of(envIdentifierRef.getIdentifier()));

    if (EmptyPredicate.isNotEmpty(envIdentifierRef.getMetadata())) {
      infraDefinitionReferenceProtoDTO.putAllMetadata(envIdentifierRef.getMetadata());
    }

    return EntityDetailProtoDTO.newBuilder()
        .setInfraDefRef(infraDefinitionReferenceProtoDTO.build())
        .setType(EntityTypeProtoEnum.INFRASTRUCTURE)
        .build();
  }

  private EntityDetailProtoDTO convertToEntityDetailProtoDTO(String accountId, String orgId, String projectId,
      String fullQualifiedDomainName, String entityRefValue, EntityTypeProtoEnum entityTypeProtoEnum,
      boolean shouldModifyFqn) {
    Map<String, String> metadata = new HashMap<>();

    metadata.put(PreFlightCheckMetadata.FQN, fullQualifiedDomainName);

    if (NGExpressionUtils.isRuntimeOrExpressionField(entityRefValue)) {
      metadata.put(PreFlightCheckMetadata.EXPRESSION, entityRefValue);
      IdentifierRef identifierRef = IdentifierRefHelper.createIdentifierRefWithUnknownScope(
          accountId, orgId, projectId, entityRefValue, metadata);
      return EntityDetailProtoDTO.newBuilder()
          .setIdentifierRef(IdentifierRefProtoUtils.createIdentifierRefProtoFromIdentifierRef(identifierRef))
          .setType(entityTypeProtoEnum)
          .build();
    } else {
      IdentifierRef identifierRef =
          IdentifierRefHelper.getIdentifierRef(entityRefValue, accountId, orgId, projectId, metadata);
      return EntityDetailProtoDTO.newBuilder()
          .setIdentifierRef(IdentifierRefProtoUtils.createIdentifierRefProtoFromIdentifierRef(identifierRef))
          .setType(entityTypeProtoEnum)
          .build();
    }
  }
}
