/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.service.beans;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.PATH_CONNECTOR;

import io.harness.beans.IdentifierRef;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.impl.ServiceEntityServiceImpl;
import io.harness.ng.core.service.services.impl.ServiceEntitySetupUsageHelper;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.FQNMapGenerator;
import io.harness.pms.yaml.ParameterField;
import io.harness.preflight.PreFlightCheckMetadata;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.IdentifierRefProtoUtils;
import io.harness.walktree.visitor.SimpleVisitorFactory;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractor;
import io.harness.walktree.visitor.utilities.VisitorParentPathUtils;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ServiceEntityVisitorHelperV2 implements ConfigValidator, EntityReferenceExtractor {
  @Inject SimpleVisitorFactory simpleVisitorFactory;
  @Inject ServiceEntityServiceImpl serviceEntityService;
  @Inject ServiceEntitySetupUsageHelper serviceEntitySetupUsageHelper;

  private static final int PAGE = 0;
  private static final int SIZE = 100;
  @Override
  public void validate(Object object, ValidationVisitor visitor) {
    // Nothing to validate.
  }

  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    return ServiceYamlV2.builder().build();
  }

  @Override
  public Set<EntityDetailProtoDTO> addReference(Object object, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<String, Object> contextMap) {
    ServiceYamlV2 serviceYamlV2 = (ServiceYamlV2) object;

    final String fullQualifiedDomainName =
        VisitorParentPathUtils.getFullQualifiedDomainName(contextMap) + PATH_CONNECTOR + YamlTypes.SERVICE_REF;
    final Map<String, String> metadata =
        new HashMap<>(Collections.singletonMap(PreFlightCheckMetadata.FQN, fullQualifiedDomainName));

    // Clear out Service References
    if (ParameterField.isNull(serviceYamlV2.getServiceRef())) {
      IdentifierRef identifierRef = IdentifierRefHelper.createIdentifierRefWithUnknownScope(
          accountIdentifier, orgIdentifier, projectIdentifier, "unknown", metadata);
      EntityDetailProtoDTO entityDetail =
          EntityDetailProtoDTO.newBuilder()
              .setIdentifierRef(IdentifierRefProtoUtils.createIdentifierRefProtoFromIdentifierRef(identifierRef))
              .setType(EntityTypeProtoEnum.SERVICE)
              .build();
      return Set.of(entityDetail);
    }

    final Set<EntityDetailProtoDTO> result = new HashSet<>();
    if (!serviceYamlV2.getServiceRef().isExpression()) {
      String serviceRefString = serviceYamlV2.getServiceRef().getValue();
      if (EmptyPredicate.isEmpty(serviceRefString)) {
        return result;
      }
      IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
          serviceRefString, accountIdentifier, orgIdentifier, projectIdentifier, metadata);

      EntityDetailProtoDTO entityDetail =
          EntityDetailProtoDTO.newBuilder()
              .setIdentifierRef(IdentifierRefProtoUtils.createIdentifierRefProtoFromIdentifierRef(identifierRef))
              .setType(EntityTypeProtoEnum.SERVICE)
              .build();
      result.add(entityDetail);

      Optional<ServiceEntity> serviceEntity =
          serviceEntityService.get(accountIdentifier, orgIdentifier, projectIdentifier, serviceRefString, false);
      if (!serviceEntity.isPresent()) {
        return result;
      }

      Set<EntityDetailProtoDTO> entityDetailProtoDTOS =
          serviceEntitySetupUsageHelper.getAllReferredEntities(serviceEntity.get());

      Map<String, Object> map = new LinkedHashMap<>();
      if (ParameterField.isNotNull(serviceYamlV2.serviceInputs) && !serviceYamlV2.getServiceInputs().isExpression()) {
        map.put("service", serviceYamlV2.getServiceInputs().getValue());
        Map<FQN, Object> fqnToValueMap = FQNMapGenerator.generateFQNMap(JsonPipelineUtils.asTree(map));
        Map<String, Object> fqnStringToValueMap = new HashMap<>();
        fqnToValueMap.forEach((fqn, value) -> fqnStringToValueMap.put(fqn.getExpressionFqn(), value));

        for (EntityDetailProtoDTO entityDetailProtoDTO : entityDetailProtoDTOS) {
          {
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
    } else {
      metadata.put(PreFlightCheckMetadata.EXPRESSION, serviceYamlV2.getServiceRef().getExpressionValue());
      IdentifierRef identifierRef = IdentifierRefHelper.createIdentifierRefWithUnknownScope(accountIdentifier,
          orgIdentifier, projectIdentifier, serviceYamlV2.getServiceRef().getExpressionValue(), metadata);
      EntityDetailProtoDTO entityDetail =
          EntityDetailProtoDTO.newBuilder()
              .setIdentifierRef(IdentifierRefProtoUtils.createIdentifierRefProtoFromIdentifierRef(identifierRef))
              .setType(EntityTypeProtoEnum.SERVICE)
              .build();
      result.add(entityDetail);
    }
    return result;
  }

  private boolean isReferredEntityForRuntimeInput(IdentifierRefProtoDTO identifierRefOfReferredEntity) {
    return identifierRefOfReferredEntity.getMetadataMap() != null
        && isNotEmpty(identifierRefOfReferredEntity.getMetadataMap().get(PreFlightCheckMetadata.FQN))
        && isNotEmpty(identifierRefOfReferredEntity.getMetadataMap().get(PreFlightCheckMetadata.EXPRESSION))
        && NGExpressionUtils.matchesInputSetPattern(identifierRefOfReferredEntity.getIdentifier().getValue());
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
