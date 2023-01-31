/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline;

import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.PATH_CONNECTOR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.environment.yaml.EnvironmentYaml;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.visitor.YamlTypes;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineInfrastructureVisitorHelper implements ConfigValidator, EntityReferenceExtractor {
  @Override
  public void validate(Object object, ValidationVisitor visitor) {
    // Nothing to validate.
  }

  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    return PipelineInfrastructure.builder().build();
  }

  @Override
  public Set<EntityDetailProtoDTO> addReference(Object object, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<String, Object> contextMap) {
    PipelineInfrastructure pipelineInfrastructure = (PipelineInfrastructure) object;
    Set<EntityDetailProtoDTO> result = new HashSet<>();
    if (ParameterField.isNull(pipelineInfrastructure.getEnvironmentRef())) {
      return addEnvironmentInformation(
          pipelineInfrastructure, accountIdentifier, orgIdentifier, projectIdentifier, contextMap);
    }
    String fullQualifiedDomainName =
        VisitorParentPathUtils.getFullQualifiedDomainName(contextMap) + PATH_CONNECTOR + YamlTypes.ENVIRONMENT_REF;
    Map<String, String> metadata =
        new HashMap<>(Collections.singletonMap(PreFlightCheckMetadata.FQN, fullQualifiedDomainName));
    if (!pipelineInfrastructure.getEnvironmentRef().isExpression()) {
      String environmentRefString = pipelineInfrastructure.getEnvironmentRef().getValue();
      IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
          environmentRefString, accountIdentifier, orgIdentifier, projectIdentifier, metadata);
      EntityDetailProtoDTO entityDetail =
          EntityDetailProtoDTO.newBuilder()
              .setIdentifierRef(IdentifierRefProtoUtils.createIdentifierRefProtoFromIdentifierRef(identifierRef))
              .setType(EntityTypeProtoEnum.ENVIRONMENT)
              .build();
      result.add(entityDetail);
    } else {
      metadata.put(PreFlightCheckMetadata.EXPRESSION, pipelineInfrastructure.getEnvironmentRef().getExpressionValue());
      IdentifierRef identifierRef = IdentifierRefHelper.createIdentifierRefWithUnknownScope(accountIdentifier,
          orgIdentifier, projectIdentifier, pipelineInfrastructure.getEnvironmentRef().getExpressionValue(), metadata);
      EntityDetailProtoDTO entityDetail =
          EntityDetailProtoDTO.newBuilder()
              .setIdentifierRef(IdentifierRefProtoUtils.createIdentifierRefProtoFromIdentifierRef(identifierRef))
              .setType(EntityTypeProtoEnum.ENVIRONMENT)
              .build();
      result.add(entityDetail);
    }
    return result;
  }

  private Set<EntityDetailProtoDTO> addEnvironmentInformation(PipelineInfrastructure pipelineInfrastructure,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Map<String, Object> contextMap) {
    Set<EntityDetailProtoDTO> result = new HashSet<>();
    if (pipelineInfrastructure.getEnvironment() != null) {
      EnvironmentYaml environmentYaml = pipelineInfrastructure.getEnvironment();
      String fullQualifiedDomainName = VisitorParentPathUtils.getFullQualifiedDomainName(contextMap) + PATH_CONNECTOR
          + YamlTypes.ENVIRONMENT_YAML + PATH_CONNECTOR + "identifier";
      Map<String, String> metadata =
          new HashMap<>(Collections.singletonMap(PreFlightCheckMetadata.FQN, fullQualifiedDomainName));
      metadata.put("new", "true");
      if (environmentYaml.getIdentifier() != null) {
        String serviceYamlIdentifier = environmentYaml.getIdentifier();
        IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
            serviceYamlIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, metadata);
        EntityDetailProtoDTO entityDetail =
            EntityDetailProtoDTO.newBuilder()
                .setIdentifierRef(IdentifierRefProtoUtils.createIdentifierRefProtoFromIdentifierRef(identifierRef))
                .setType(EntityTypeProtoEnum.ENVIRONMENT)
                .build();
        result.add(entityDetail);
      }
    }
    return result;
  }
}
