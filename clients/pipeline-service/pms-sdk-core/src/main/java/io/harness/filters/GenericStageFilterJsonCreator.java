/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.filters;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.yaml.ParameterField.isNotNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.SecretRefData;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.plancreator.stages.stage.StageInfoConfig;
import io.harness.pms.exception.runtime.InvalidYamlRuntimeException;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.filter.creation.FilterCreationResponse.FilterCreationResponseBuilder;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.strategy.StrategyValidationUtils;
import io.harness.walktree.visitor.SimpleVisitorFactory;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractorVisitor;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;

@OwnedBy(PIPELINE)
public abstract class GenericStageFilterJsonCreator implements FilterJsonCreator<StageElementConfig> {
  @Inject private SimpleVisitorFactory simpleVisitorFactory;

  public abstract Set<String> getSupportedStageTypes();

  public abstract PipelineFilter getFilter(
      FilterCreationContext filterCreationContext, StageElementConfig stageElementConfig);

  @Override
  public Class<StageElementConfig> getFieldClass() {
    return StageElementConfig.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    Set<String> stageTypes = getSupportedStageTypes();
    if (EmptyPredicate.isEmpty(stageTypes)) {
      return Collections.emptyMap();
    }
    return Collections.singletonMap(YAMLFieldNameConstants.STAGE, stageTypes);
  }

  @Override
  public FilterCreationResponse handleNode(
      FilterCreationContext filterCreationContext, StageElementConfig stageElementConfig) {
    if (isNotNull(stageElementConfig.getStrategy()) && stageElementConfig.getStrategy().getValue() != null) {
      StrategyValidationUtils.validateStrategyNode(stageElementConfig.getStrategy().getValue());
    }
    FilterCreationResponseBuilder creationResponse = FilterCreationResponse.builder();

    YamlField variablesField =
        filterCreationContext.getCurrentField().getNode().getField(YAMLFieldNameConstants.VARIABLES);
    if (variablesField != null) {
      FilterCreatorHelper.checkIfVariableNamesAreValid(variablesField);
    }

    if (stageElementConfig.getStageType() == null || stageElementConfig.getStageType().getExecution() == null) {
      throw new InvalidYamlRuntimeException(String.format(
          "Execution section is required in %s stage [%s]. Please add it and try again", stageElementConfig.getType(),
          YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }

    creationResponse.referredEntities(new ArrayList<>(getReferredEntities(filterCreationContext, stageElementConfig)));

    creationResponse.dependencies(
        DependenciesUtils.toDependenciesProto(getDependencies(filterCreationContext.getCurrentField())));

    PipelineFilter filter = getFilter(filterCreationContext, stageElementConfig);
    if (filter != null) {
      creationResponse.pipelineFilter(filter);
    }
    return creationResponse.build();
  }

  public Set<EntityDetailProtoDTO> getReferredEntities(
      FilterCreationContext filterCreationContext, StageElementConfig stageElementConfig) {
    Set<EntityDetailProtoDTO> referredEntities = getReferences(filterCreationContext.getSetupMetadata().getAccountId(),
        filterCreationContext.getSetupMetadata().getOrgId(), filterCreationContext.getSetupMetadata().getProjectId(),
        stageElementConfig.getStageType(), filterCreationContext.getCurrentField());
    referredEntities.addAll(extractSecretRefs(filterCreationContext));
    return referredEntities;
  }

  private Set<EntityDetailProtoDTO> getReferences(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, StageInfoConfig stageInfoConfig, YamlField stageYamlField) {
    YamlField specYamlField = stageYamlField.getNode().getField(YAMLFieldNameConstants.SPEC);
    String fullQualifiedNameFromRoot = YamlUtils.getFullyQualifiedName(specYamlField.getNode());
    List<String> qualifiedNameList = new LinkedList<>(Arrays.asList(fullQualifiedNameFromRoot.split("\\.")));
    EntityReferenceExtractorVisitor visitor = simpleVisitorFactory.obtainEntityReferenceExtractorVisitor(
        accountIdentifier, orgIdentifier, projectIdentifier, qualifiedNameList);
    visitor.walkElementTree(stageInfoConfig);
    return visitor.getEntityReferenceSet();
  }

  private Set<EntityDetailProtoDTO> extractSecretRefs(FilterCreationContext context) {
    String accountId = context.getSetupMetadata().getAccountId();
    String orgId = context.getSetupMetadata().getOrgId();
    String projectId = context.getSetupMetadata().getProjectId();
    Set<EntityDetailProtoDTO> entityDetailProtoDTOS = new HashSet<>();
    YamlField variablesField = context.getCurrentField().getNode().getField(YAMLFieldNameConstants.VARIABLES);
    if (variablesField == null) {
      return entityDetailProtoDTOS;
    }
    Map<String, ParameterField<SecretRefData>> fqnToSecretRefs =
        SecretRefExtractorHelper.extractSecretRefsFromVariables(variablesField);
    for (Map.Entry<String, ParameterField<SecretRefData>> entry : fqnToSecretRefs.entrySet()) {
      entityDetailProtoDTOS.add(FilterCreatorHelper.convertSecretToEntityDetailProtoDTO(
          accountId, orgId, projectId, entry.getKey(), entry.getValue()));
    }
    return entityDetailProtoDTOS;
  }

  @NotNull
  protected Map<String, YamlField> getDependencies(YamlField stageField) {
    // Add dependency for execution
    YamlField executionField =
        stageField.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YAMLFieldNameConstants.EXECUTION);
    Map<String, YamlField> dependencies = new HashMap<>();
    dependencies.put(executionField.getNode().getUuid(), executionField);
    return dependencies;
  }
}
