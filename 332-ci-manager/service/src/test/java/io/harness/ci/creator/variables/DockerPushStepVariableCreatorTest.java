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
import io.harness.beans.steps.nodes.BuildAndPushDockerNode;
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
public class DockerPushStepVariableCreatorTest extends CategoryTest {
  @Inject DockerStepVariableCreator dockerStepVariableCreator = new DockerStepVariableCreator();
  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void createVariablesForParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("dockerPushUuidJsonSteps.yaml");
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
    BuildAndPushDockerNode buildAndPushDockerNode =
        YamlUtils.read(stepField.getNode().toString(), BuildAndPushDockerNode.class);
    VariableCreationResponse variablesForParentNodeV2 = dockerStepVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepField).build(), buildAndPushDockerNode);

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.spec.tags",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.description",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.spec.runAsUser",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.spec.labels.foo",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.spec.target",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.timeout",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.spec.repo",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.spec.buildArgs.foo",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.spec.dockerfile",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.spec.remoteCacheRepo",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.spec.labels.hello",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.spec.connectorRef",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.spec.buildArgs.hello",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.spec.optimize",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.spec.context",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.spec.cacheFrom",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.spec.cacheTo",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.spec.caching",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.name",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.when");

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get(buildAndPushDockerNode.getUuid()) // step uuid
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.identifier",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.type",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.startTs",
            "pipeline.stages.docker_buildPush_success.spec.execution.steps.dockerBuildPush.endTs");
  }
}
