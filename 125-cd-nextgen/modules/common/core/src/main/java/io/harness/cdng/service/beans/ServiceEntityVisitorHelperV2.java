/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.service.beans;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.walktree.visitor.entityreference.EntityReferenceExtractorVisitor.SETUP_METADATA_KEY;
import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.PARENT_PATH_KEY;
import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.PATH_CONNECTOR;
import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.VALUES;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.ScmException;
import io.harness.gitx.GitXTransientBranchGuard;
import io.harness.ng.core.security.NgManagerSourcePrincipalGuard;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.impl.ServiceEntityServiceImpl;
import io.harness.ng.core.service.services.impl.ServiceEntitySetupUsageHelper;
import io.harness.pms.contracts.plan.SetupMetadata;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@Slf4j
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
    SetupMetadata setupMetadata = getSetupMetadata(contextMap);
    try (NgManagerSourcePrincipalGuard ignore = new NgManagerSourcePrincipalGuard(setupMetadata)) {
      return addReferenceInternal(object, accountIdentifier, orgIdentifier, projectIdentifier, contextMap);
    } catch (ExplanationException | HintException | ScmException ex) {
      // Todo : @Tathagat iteratively improve on this exception handling
      log.error("Exception while adding references in ServiceEntityVisitorHelperV2", ex);
      throw ex;
    } catch (Exception ex) {
      log.error("Exception while adding references in ServiceEntityVisitorHelperV2", ex);
      return new HashSet<>();
    }
  }

  private Set<EntityDetailProtoDTO> addReferenceInternal(Object object, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<String, Object> contextMap) {
    ServiceYamlV2 serviceYamlV2 = (ServiceYamlV2) object;
    String fullQualifiedDomainName = "";
    final Map<String, String> metadata = new HashMap<>();
    Optional<LinkedList<String>> parentPath = Optional.ofNullable((LinkedList<String>) contextMap.get(PARENT_PATH_KEY));
    if (parentPath.isPresent() && VALUES.equals(parentPath.get().getLast())) {
      if (serviceYamlV2.getServiceRef().isExpression()) {
        /*Since we do not know the value of service identifier(s)/ref(s) currently, so we only construct the fqn till
        ".services". The service identifier(s) and "serviceRef" string will be added later when the value of services
        are fixed
        */
        fullQualifiedDomainName = VisitorParentPathUtils.getFullQualifiedDomainName(contextMap);
        metadata.put(PreFlightCheckMetadata.YAML_TYPE_REF_NAME, YamlTypes.SERVICE_REF);
      } else if (ParameterField.isNotNull(serviceYamlV2.getServiceRef())) {
        fullQualifiedDomainName = VisitorParentPathUtils.getFullQualifiedDomainName(contextMap) + PATH_CONNECTOR
            + serviceYamlV2.getServiceRef().getValue() + PATH_CONNECTOR + YamlTypes.SERVICE_REF;
      }
    } else {
      fullQualifiedDomainName =
          VisitorParentPathUtils.getFullQualifiedDomainName(contextMap) + PATH_CONNECTOR + YamlTypes.SERVICE_REF;
    }
    metadata.put(PreFlightCheckMetadata.FQN, fullQualifiedDomainName);

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

      String gitBranch = serviceYamlV2.getGitBranch();

      Optional<ServiceEntity> serviceEntity;
      try (GitXTransientBranchGuard ignore = new GitXTransientBranchGuard(gitBranch)) {
        serviceEntity = serviceEntityService.get(
            accountIdentifier, orgIdentifier, projectIdentifier, serviceRefString, false, true, false);
      }

      if (serviceEntity.isEmpty()) {
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

  private SetupMetadata getSetupMetadata(Map<String, Object> contextMap) {
    if (isNotEmpty(contextMap)) {
      Object setupMetadata = contextMap.get(SETUP_METADATA_KEY);
      if (setupMetadata != null) {
        return (SetupMetadata) setupMetadata;
      }
    }
    return null;
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
