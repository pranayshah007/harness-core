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
import io.harness.beans.steps.nodes.BuildAndPushECRNode;
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
public class ECRUploadStepVariableCreatorTest extends CategoryTest {
  @Inject
  BuildAndPushECRStepVariableCreator buildAndPushECRStepVariableCreator = new BuildAndPushECRStepVariableCreator();
  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void createVariablesForParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("ecrUploadJsonStep.yaml");
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
    BuildAndPushECRNode buildAndPushECRNode = YamlUtils.read(stepField.getNode().toString(), BuildAndPushECRNode.class);
    VariableCreationResponse variablesForParentNodeV2 =
        buildAndPushECRStepVariableCreator.createVariablesForParentNodeV2(
            VariableCreationContext.builder().currentField(stepField).build(), buildAndPushECRNode);

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.spec.region",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.spec.buildArgs.foo",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.spec.runAsUser",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.spec.connectorRef",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.spec.context",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.spec.target",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.spec.buildArgs.hello",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.spec.optimize",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.timeout",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.spec.account",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.description",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.spec.tags",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.spec.labels.hello",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.spec.dockerfile",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.spec.imageName",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.spec.labels.foo",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.name",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.spec.remoteCacheImage",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.spec.baseImageConnectorRefs",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.when",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.spec.cacheFrom",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.spec.cacheTo",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.spec.caching");

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get(buildAndPushECRNode.getUuid()) // step uuid
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.type",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.identifier",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.startTs",
            "pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.endTs");

    List<String> fqnExtraPropertiesList1 = variablesForParentNodeV2.getYamlExtraProperties()
                                               .get(buildAndPushECRNode.getEcrStepInfo().getUuid()) // step uuid
                                               .getPropertiesList()
                                               .stream()
                                               .map(YamlProperties::getFqn)
                                               .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList1)
        .containsOnly("pipeline.stages.ecr_build_push.spec.execution.steps.pushECR.spec.resources");
  }
}
