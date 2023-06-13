/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.creator.variables;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.HARSH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.nodes.BuildAndPushGCRNode;
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
public class GCRUploadStepVariableCreatorTest extends CategoryTest {
  @Inject BuildAndPushGCRStepVariableCreator gcrStepVariableCreator = new BuildAndPushGCRStepVariableCreator();
  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void createVariablesForParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("gcrUploadJsonStep.yaml");
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
                              .get(2)
                              .getField("step");
    // yaml input expressions
    BuildAndPushGCRNode buildAndPushGCRNode = YamlUtils.read(stepField.getNode().toString(), BuildAndPushGCRNode.class);
    VariableCreationResponse variablesForParentNodeV2 = gcrStepVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepField).build(), buildAndPushGCRNode);

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.spec.buildArgs",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.spec.runAsUser",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.spec.tags",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.timeout",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.spec.host",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.spec.target",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.spec.imageName",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.spec.dockerfile",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.spec.optimize",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.spec.labels",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.spec.connectorRef",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.description",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.name",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.spec.projectID",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.spec.context",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.spec.remoteCacheImage",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.when",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.spec.cacheFrom",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.spec.cacheTo",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.spec.caching");

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get(buildAndPushGCRNode.getUuid()) // step uuid
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.type",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.identifier",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.startTs",
            "pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.endTs");

    List<String> fqnExtraPropertiesList1 = variablesForParentNodeV2.getYamlExtraProperties()
                                               .get(buildAndPushGCRNode.getGcrStepInfo().getUuid()) // step uuid
                                               .getPropertiesList()
                                               .stream()
                                               .map(YamlProperties::getFqn)
                                               .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList1)
        .containsOnly("pipeline.stages.gcpBuildPush.spec.execution.steps.pushGCR.spec.resources");
  }
}
