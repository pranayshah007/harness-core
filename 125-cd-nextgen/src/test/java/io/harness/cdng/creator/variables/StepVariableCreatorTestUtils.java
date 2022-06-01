/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.variables;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class StepVariableCreatorTestUtils {
  public YamlField getPipelineYamlField(String resource) throws IOException {
    final URL file = StepVariableCreatorTestUtils.class.getClassLoader().getResource(resource);
    String pipelineJson = Resources.toString(file, Charsets.UTF_8);

    return YamlUtils.readTree(pipelineJson);
  }

  public YamlField getNthExecutionStepFromPipelineYamlField(YamlField pipelineYamlField, int n) {
    return pipelineYamlField.getNode()
        .getField(YAMLFieldNameConstants.PIPELINE)
        .getNode()
        .getField(YAMLFieldNameConstants.STAGES)
        .getNode()
        .asArray()
        .get(0)
        .getField(YAMLFieldNameConstants.STAGE)
        .getNode()
        .getField(YAMLFieldNameConstants.SPEC)
        .getNode()
        .getField(YAMLFieldNameConstants.EXECUTION)
        .getNode()
        .getField(YAMLFieldNameConstants.STEPS)
        .getNode()
        .asArray()
        .get(n)
        .getField(YAMLFieldNameConstants.STEP);
  }

  public YamlField getNthInfraStepFromPipelineYamlField(
      YamlField pipelineYamlField, String provisionerStepType, int n) {
    return pipelineYamlField.getNode()
        .getField(YAMLFieldNameConstants.PIPELINE)
        .getNode()
        .getField(YAMLFieldNameConstants.STAGES)
        .getNode()
        .asArray()
        .get(0)
        .getField(YAMLFieldNameConstants.STAGE)
        .getNode()
        .getField(YAMLFieldNameConstants.SPEC)
        .getNode()
        .getField(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE)
        .getNode()
        .getField("infrastructureDefinition")
        .getNode()
        .getField(YAMLFieldNameConstants.PROVISIONER)
        .getNode()
        .getField(provisionerStepType)
        .getNode()
        .asArray()
        .get(n)
        .getField(YAMLFieldNameConstants.STEP);
  }

  public <T extends AbstractStepNode> List<String> getFqnPropertiesForParentNodeV2(
      String resource, GenericStepVariableCreator<T> variableCreator, Class<T> stepNodeClass) throws IOException {
    return getFqnPropertiesForParentNodeV2(resource, variableCreator, stepNodeClass, 0);
  }

  public <T extends AbstractStepNode> List<String> getFqnPropertiesForParentNodeV2(String resource,
      GenericStepVariableCreator<T> variableCreator, Class<T> stepNodeClass, int nthStep) throws IOException {
    YamlField pipelineYamlField = StepVariableCreatorTestUtils.getPipelineYamlField(resource);
    YamlField stepYamlField =
        StepVariableCreatorTestUtils.getNthExecutionStepFromPipelineYamlField(pipelineYamlField, nthStep);
    VariableCreationContext context = VariableCreationContext.builder().currentField(stepYamlField).build();

    VariableCreationResponse response = variableCreator.createVariablesForParentNodeV2(
        context, YamlUtils.read(stepYamlField.getNode().toString(), stepNodeClass));

    return response.getYamlProperties().values().stream().map(YamlProperties::getFqn).collect(Collectors.toList());
  }

  public <T extends AbstractStepNode> List<String> getInfraFqnPropertiesForParentNodeV2(
      String resource, GenericStepVariableCreator<T> variableCreator, Class<T> stepNodeClass) throws IOException {
    YamlField pipelineYamlField = StepVariableCreatorTestUtils.getPipelineYamlField(resource);
    YamlField stepYamlField = StepVariableCreatorTestUtils.getNthInfraStepFromPipelineYamlField(
        pipelineYamlField, YAMLFieldNameConstants.STEPS, 0);
    VariableCreationContext context = VariableCreationContext.builder().currentField(stepYamlField).build();

    VariableCreationResponse response = variableCreator.createVariablesForParentNodeV2(
        context, YamlUtils.read(stepYamlField.getNode().toString(), stepNodeClass));

    return response.getYamlProperties().values().stream().map(YamlProperties::getFqn).collect(Collectors.toList());
  }

  public <T extends AbstractStepNode> List<String> getInfraRollbackFqnPropertiesForParentNodeV2(
      String resource, GenericStepVariableCreator<T> variableCreator, Class<T> stepNodeClass) throws IOException {
    YamlField pipelineYamlField = StepVariableCreatorTestUtils.getPipelineYamlField(resource);
    YamlField stepYamlField = StepVariableCreatorTestUtils.getNthInfraStepFromPipelineYamlField(
        pipelineYamlField, YAMLFieldNameConstants.ROLLBACK_STEPS, 0);
    VariableCreationContext context = VariableCreationContext.builder().currentField(stepYamlField).build();

    VariableCreationResponse response = variableCreator.createVariablesForParentNodeV2(
        context, YamlUtils.read(stepYamlField.getNode().toString(), stepNodeClass));

    return response.getYamlProperties().values().stream().map(YamlProperties::getFqn).collect(Collectors.toList());
  }
}
