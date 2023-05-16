/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.filters;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.yaml.ParameterField.isNotNull;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;
import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.PATH_CONNECTOR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.strategy.StrategyValidationUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

@OwnedBy(PIPELINE)
public abstract class GenericStepPMSFilterJsonCreatorV2 implements FilterJsonCreator<AbstractStepNode> {
  public abstract Set<String> getSupportedStepTypes();

  @Override
  public Class<AbstractStepNode> getFieldClass() {
    return AbstractStepNode.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    Set<String> stepTypes = getSupportedStepTypes();
    if (isEmpty(stepTypes)) {
      return Collections.emptyMap();
    }
    return Collections.singletonMap(STEP, stepTypes);
  }

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, AbstractStepNode yamlField) {
    if (isNotNull(yamlField.getStrategy()) && yamlField.getStrategy().getValue() != null) {
      StrategyValidationUtils.validateStrategyNode(yamlField.getStrategy().getValue());
    }

    List<EntityDetailProtoDTO> result = new ArrayList<>();
    if (WithConnectorRef.class.isAssignableFrom(yamlField.getStepSpecType().getClass())) {
      Map<String, ParameterField<String>> connectorRefs =
          ((WithConnectorRef) yamlField.getStepSpecType()).extractConnectorRefs();
      addReferredEntities(filterCreationContext, result, connectorRefs, EntityTypeProtoEnum.CONNECTORS);
    }

    if (WithSecretRef.class.isAssignableFrom(yamlField.getStepSpecType().getClass())) {
      Map<String, ParameterField<String>> secretRefs =
          ((WithSecretRef) yamlField.getStepSpecType()).extractSecretRefs();
      addReferredEntities(filterCreationContext, result, secretRefs, EntityTypeProtoEnum.SECRETS);
    }

    return isEmpty(result) ? FilterCreationResponse.builder().build()
                           : FilterCreationResponse.builder().referredEntities(result).build();
  }

  private void addReferredEntities(FilterCreationContext filterCreationContext, List<EntityDetailProtoDTO> result,
      Map<String, ParameterField<String>> refs, EntityTypeProtoEnum entityType) {
    String accountIdentifier = filterCreationContext.getSetupMetadata().getAccountId();
    String orgIdentifier = filterCreationContext.getSetupMetadata().getOrgId();
    String projectIdentifier = filterCreationContext.getSetupMetadata().getProjectId();

    for (Map.Entry<String, ParameterField<String>> ref : refs.entrySet()) {
      ParameterField<String> refValue = ref.getValue();
      if (ParameterField.isNull(refValue)) {
        continue;
      }

      String fullQualifiedDomainName =
          getFullQualifiedDomainName(filterCreationContext.getCurrentField().getNode(), ref.getKey());

      result.add(FilterCreatorHelper.convertToEntityDetailProtoDTO(
          accountIdentifier, orgIdentifier, projectIdentifier, fullQualifiedDomainName, refValue, entityType));
    }
  }

  @NotNull
  private String getFullQualifiedDomainName(YamlNode node, String refKey) {
    String fullQualifiedDomainNameFromNode = YamlUtils.getFullyQualifiedName(node);
    return fullQualifiedDomainNameFromNode + (isEmpty(fullQualifiedDomainNameFromNode) ? "" : PATH_CONNECTOR)
        + YAMLFieldNameConstants.SPEC + PATH_CONNECTOR + refKey;
  }
}