/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.creator.variables;

import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.steps.StepSpecTypeConstants;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@Slf4j
public class CIStageVariableCreatorTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Inject CIStageVariableCreator ciStageVariableCreator = new CIStageVariableCreator();

  private String SOURCE_PIPELINE_YAML;

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateSupportedTypes() {
    assertThat(ciStageVariableCreator.getSupportedTypes())
        .isEqualTo(Collections.singletonMap(
            YAMLFieldNameConstants.STAGE, Set.of(StepSpecTypeConstants.CI_STAGE, StepSpecTypeConstants.CI_STAGE_V2)));
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateFieldClasss() {
    assertThat(ciStageVariableCreator.getFieldClass()).isInstanceOf(java.lang.Class.class);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateVariableForChildNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("gcrUploadJsonStep.yaml");
    SOURCE_PIPELINE_YAML = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.readTree(SOURCE_PIPELINE_YAML);
    YamlField stageField = fullYamlField.getNode()
                               .getField("pipeline")
                               .getNode()
                               .getField("stages")
                               .getNode()
                               .asArray()
                               .get(0)
                               .getField("stage");

    YamlField variablesField = stageField.getNode().getField("variables");
    LinkedHashMap<String, VariableCreationResponse> responseMap =
        ciStageVariableCreator.createVariablesForChildrenNodes(
            VariableCreationContext.builder().currentField(stageField).build(), stageField);

    assertThat(responseMap).isNotNull();
    assertThat(responseMap.entrySet().stream().findFirst().get().getKey()).isEqualTo("mYRt58hVTump7YOMGYGSxA");
    assertThat(variablesField.getNode().asArray().get(0).getField("name").getNode().getCurrJsonNode().textValue())
        .isEqualTo("GCP_SECRET_KEY");
    assertThat(variablesField.getNode().asArray().get(0).getField("type").getNode().getCurrJsonNode().textValue())
        .isEqualTo("Secret");
    assertThat(variablesField.getNode().asArray().get(0).getField("value").getNode().getCurrJsonNode().textValue())
        .isEqualTo("account.testCISecretmDJzyNtUNe");
    assertThat(variablesField.getNode().asArray().get(0).getField("__uuid").getNode().getCurrJsonNode().textValue())
        .isEqualTo("2-Bja1vwRV-MOMra66vTCg");
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateVariableForParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("gcrUploadJsonStep.yaml");
    SOURCE_PIPELINE_YAML = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.readTree(SOURCE_PIPELINE_YAML);
    YamlField stageField = fullYamlField.getNode()
                               .getField("pipeline")
                               .getNode()
                               .getField("stages")
                               .getNode()
                               .asArray()
                               .get(0)
                               .getField("stage");
    IntegrationStageNode integrationStageNode =
        YamlUtils.read(stageField.getNode().toString(), IntegrationStageNode.class);

    VariableCreationResponse variableCreationResponse = ciStageVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stageField).build(), integrationStageNode);

    List<String> fqnPropertiesList = variableCreationResponse.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());

    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.gcpBuildPush.spec.infrastructure.spec.runAsUser",
            "pipeline.stages.gcpBuildPush.spec.infrastructure.spec.serviceAccountName",
            "pipeline.stages.gcpBuildPush.spec.infrastructure.spec.volumes",
            "pipeline.stages.gcpBuildPush.spec.infrastructure.spec.connectorRef",
            "pipeline.stages.gcpBuildPush.spec.infrastructure.spec.annotations",
            "pipeline.stages.gcpBuildPush.description",
            "pipeline.stages.gcpBuildPush.spec.infrastructure.spec.harnessImageConnectorRef",
            "pipeline.stages.gcpBuildPush.variables.GCP_SECRET_KEY",
            "pipeline.stages.gcpBuildPush.spec.infrastructure.spec.namespace",
            "pipeline.stages.gcpBuildPush.spec.infrastructure.spec.labels",
            "pipeline.stages.gcpBuildPush.spec.infrastructure.spec.automountServiceAccountToken",
            "pipeline.stages.gcpBuildPush.delegateSelectors", "pipeline.stages.gcpBuildPush.spec.sharedPaths",
            "pipeline.stages.gcpBuildPush.spec.infrastructure.spec.hostNames",
            "pipeline.stages.gcpBuildPush.spec.platform", "pipeline.stages.gcpBuildPush.spec.cloneCodebase",
            "pipeline.stages.gcpBuildPush.spec.infrastructure.spec.priorityClassName",
            "pipeline.stages.gcpBuildPush.spec.infrastructure.spec.nodeSelector",
            "pipeline.stages.gcpBuildPush.spec.infrastructure.spec.os",
            "pipeline.stages.gcpBuildPush.spec.serviceDependencies", "pipeline.stages.gcpBuildPush.name",
            "pipeline.stages.gcpBuildPush.spec.infrastructure.spec.containerSecurityContext",
            "pipeline.stages.gcpBuildPush.when", "pipeline.stages.gcpBuildPush.spec.infrastructure.spec.initTimeout");

    List<String> fqnExtraPropertiesList = variableCreationResponse.getYamlExtraProperties()
                                              .get(integrationStageNode.getUuid()) // step uuid
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());

    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.stages.gcpBuildPush.type", "pipeline.stages.gcpBuildPush.pipelineVariables",
            "pipeline.stages.gcpBuildPush.identifier", "pipeline.stages.gcpBuildPush.tags",
            "pipeline.stages.gcpBuildPush.startTs", "pipeline.stages.gcpBuildPush.endTs");
  }
}
