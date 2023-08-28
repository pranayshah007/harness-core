/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.creator.variables;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.HARSH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.nodes.ArtifactoryUploadNode;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CI)
public class ArtifactoryStepVariableCreatorTest extends CategoryTest {
  @Inject
  ArtifactoryUploadStepVariableCreator artifactoryUploadStepVariableCreator =
      new ArtifactoryUploadStepVariableCreator();
  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void createVariablesForParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("artifactoryCreatorUuidJsonSteps.yaml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.readTree(pipelineJson);

    // Pipeline Node
    YamlField stepField = fullYamlField.getNode()
                              .getField("pipeline")
                              .getNode()
                              .getField("stages")
                              .getNode()
                              .asArray()
                              .get(0)
                              .getField("stage")
                              .getNode()
                              .getField("spec")
                              .getNode()
                              .getField("execution")
                              .getNode()
                              .getField("steps")
                              .getNode()
                              .asArray()
                              .get(0)
                              .getField("step");
    // yaml input expressions
    ArtifactoryUploadNode artifactoryUploadNode =
        YamlUtils.read(stepField.getNode().toString(), ArtifactoryUploadNode.class);
    VariableCreationResponse variablesForParentNodeV2 =
        artifactoryUploadStepVariableCreator.createVariablesForParentNodeV2(
            VariableCreationContext.builder().currentField(stepField).build(), artifactoryUploadNode);

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.artifactory_saas_upload_success.spec.execution.steps.uploadFile.spec.runAsUser",
            "pipeline.stages.artifactory_saas_upload_success.spec.execution.steps.uploadFile.description",
            "pipeline.stages.artifactory_saas_upload_success.spec.execution.steps.uploadFile.spec.target",
            "pipeline.stages.artifactory_saas_upload_success.spec.execution.steps.uploadFile.timeout",
            "pipeline.stages.artifactory_saas_upload_success.spec.execution.steps.uploadFile.spec.sourcePath",
            "pipeline.stages.artifactory_saas_upload_success.spec.execution.steps.uploadFile.name",
            "pipeline.stages.artifactory_saas_upload_success.spec.execution.steps.uploadFile.spec.connectorRef",
            "pipeline.stages.artifactory_saas_upload_success.spec.execution.steps.uploadFile.when");

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get(artifactoryUploadNode.getUuid()) // step uuid
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.stages.artifactory_saas_upload_success.spec.execution.steps.uploadFile.type",
            "pipeline.stages.artifactory_saas_upload_success.spec.execution.steps.uploadFile.identifier",
            "pipeline.stages.artifactory_saas_upload_success.spec.execution.steps.uploadFile.startTs",
            "pipeline.stages.artifactory_saas_upload_success.spec.execution.steps.uploadFile.endTs");

    List<String> fqnExtraPropertiesList1 =
        variablesForParentNodeV2.getYamlExtraProperties()
            .get(artifactoryUploadNode.getUploadToArtifactoryStepInfo().getUuid()) // step uuid
            .getPropertiesList()
            .stream()
            .map(YamlProperties::getFqn)
            .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList1)
        .containsOnly("pipeline.stages.artifactory_saas_upload_success.spec.execution.steps.uploadFile.spec.resources");
  }
}
