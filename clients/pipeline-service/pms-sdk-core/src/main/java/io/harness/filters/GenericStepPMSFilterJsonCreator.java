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
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.strategy.StrategyValidationUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(PIPELINE)
public abstract class GenericStepPMSFilterJsonCreator implements FilterJsonCreator<StepElementConfig> {
  public abstract Set<String> getSupportedStepTypes();

  @Override
  public Class<StepElementConfig> getFieldClass() {
    return StepElementConfig.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    Set<String> stepTypes = getSupportedStepTypes();
    if (EmptyPredicate.isEmpty(stepTypes)) {
      return Collections.emptyMap();
    }
    return Collections.singletonMap(STEP, stepTypes);
  }

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, StepElementConfig yamlField) {
    if (isNotNull(yamlField.getStrategy()) && yamlField.getStrategy().getValue() != null) {
      StrategyValidationUtils.validateStrategyNode(yamlField.getStrategy().getValue());
    }
    if (WithConnectorRef.class.isAssignableFrom(yamlField.getStepSpecType().getClass())) {
      String accountIdentifier = filterCreationContext.getSetupMetadata().getAccountId();
      String orgIdentifier = filterCreationContext.getSetupMetadata().getOrgId();
      String projectIdentifier = filterCreationContext.getSetupMetadata().getProjectId();
      Map<String, ParameterField<String>> connectorRefs =
          ((WithConnectorRef) yamlField.getStepSpecType()).extractConnectorRefs();
      List<EntityDetailProtoDTO> result = new ArrayList<>();
      for (Map.Entry<String, ParameterField<String>> entry : connectorRefs.entrySet()) {
        if (ParameterField.isNull(entry.getValue())) {
          continue;
        }
        String fullQualifiedDomainNameFromNode =
            YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode());
        String fullQualifiedDomainName = fullQualifiedDomainNameFromNode
            + (isEmpty(fullQualifiedDomainNameFromNode) ? "" : PATH_CONNECTOR) + YAMLFieldNameConstants.SPEC
            + PATH_CONNECTOR + entry.getKey();
        result.add(FilterCreatorHelper.convertToEntityDetailProtoDTO(accountIdentifier, orgIdentifier,
            projectIdentifier, fullQualifiedDomainName, entry.getValue(), EntityTypeProtoEnum.CONNECTORS));
      }
      return FilterCreationResponse.builder().referredEntities(result).build();
    }
    return FilterCreationResponse.builder().build();
  }
}
