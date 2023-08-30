/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.environment.helper;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.PARENT_PATH_KEY;
import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.PATH_CONNECTOR;
import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.VALUES;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.pms.yaml.ParameterField;
import io.harness.preflight.PreFlightCheckMetadata;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.IdentifierRefProtoUtils;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractor;
import io.harness.walktree.visitor.utilities.VisitorParentPathUtils;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(CDC)
public class EnvironmentYamlV2VisitorHelper implements ConfigValidator, EntityReferenceExtractor {
  public static final String ENV_REF = "envRef";

  @Override
  public void validate(Object object, ValidationVisitor visitor) {
    // Nothing to validate.
  }

  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    return EnvironmentYamlV2.builder().build();
  }

  @Override
  public Set<EntityDetailProtoDTO> addReference(Object object, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<String, Object> contextMap) {
    EnvironmentYamlV2 environmentYamlV2 = (EnvironmentYamlV2) object;
    Set<EntityDetailProtoDTO> result = new HashSet<>();
    String fullQualifiedDomainName = "";
    final Map<String, String> metadata = new HashMap<>();
    Optional<LinkedList<String>> parentPath = Optional.ofNullable((LinkedList<String>) contextMap.get(PARENT_PATH_KEY));

    if (parentPath.isPresent() && VALUES.equals(parentPath.get().getLast())) {
      if (environmentYamlV2.getEnvironmentRef().isExpression()) {
        /*
        Since we do not know the value of environment identifier(s)/ref(s) currently, so we only construct the fqn
        till ".environments". The environment identifier(s) and "environmentRef" string will be added later when the
        value of environments are fixed
        */
        fullQualifiedDomainName = VisitorParentPathUtils.getFullQualifiedDomainName(contextMap);
        metadata.put(PreFlightCheckMetadata.YAML_TYPE_REF_NAME, YamlTypes.ENVIRONMENT_REF);
      } else if (ParameterField.isNotNull(environmentYamlV2.getEnvironmentRef())) {
        fullQualifiedDomainName = VisitorParentPathUtils.getFullQualifiedDomainName(contextMap) + PATH_CONNECTOR
            + environmentYamlV2.getEnvironmentRef().getValue() + PATH_CONNECTOR + YamlTypes.ENVIRONMENT_REF;
      }
    } else {
      fullQualifiedDomainName =
          VisitorParentPathUtils.getFullQualifiedDomainName(contextMap) + PATH_CONNECTOR + YamlTypes.ENVIRONMENT_REF;
    }
    metadata.put(PreFlightCheckMetadata.FQN, fullQualifiedDomainName);
    if (!environmentYamlV2.getEnvironmentRef().isExpression()) {
      String environmentRefString = environmentYamlV2.getEnvironmentRef().getValue();
      if (EmptyPredicate.isEmpty(environmentRefString)) {
        return result;
      }
      IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
          environmentRefString, accountIdentifier, orgIdentifier, projectIdentifier, metadata);
      EntityDetailProtoDTO entityDetail =
          EntityDetailProtoDTO.newBuilder()
              .setIdentifierRef(IdentifierRefProtoUtils.createIdentifierRefProtoFromIdentifierRef(identifierRef))
              .setType(EntityTypeProtoEnum.ENVIRONMENT)
              .build();
      contextMap.put(ENV_REF, environmentRefString);
      result.add(entityDetail);
    } else {
      metadata.put(PreFlightCheckMetadata.EXPRESSION, environmentYamlV2.getEnvironmentRef().getExpressionValue());
      IdentifierRef identifierRef = IdentifierRefHelper.createIdentifierRefWithUnknownScope(accountIdentifier,
          orgIdentifier, projectIdentifier, environmentYamlV2.getEnvironmentRef().getExpressionValue(), metadata);
      EntityDetailProtoDTO entityDetail =
          EntityDetailProtoDTO.newBuilder()
              .setIdentifierRef(IdentifierRefProtoUtils.createIdentifierRefProtoFromIdentifierRef(identifierRef))
              .setType(EntityTypeProtoEnum.ENVIRONMENT)
              .build();
      contextMap.put(ENV_REF, environmentYamlV2.getEnvironmentRef().getExpressionValue());
      result.add(entityDetail);
    }
    return result;
  }
}
