/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.stages.parallel.v1;

import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.fork.NGForkStep;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class ParallelPlanCreatorV1Test extends CategoryTest {
  @Mock KryoSerializer kryoSerializer;
  @InjectMocks ParallelPlanCreatorV1 planCreator;
  YamlField pipelineYamlField;
  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("complex-pipeline-with-parallel-v1.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);
    pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(planCreator.getSupportedTypes())
        .isEqualTo(Collections.singletonMap("parallel", Collections.singleton(PlanCreatorUtils.ANY_TYPE)));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testSupportedYamlVersions() {
    assertThat(planCreator.getSupportedYamlVersions()).isEqualTo(Set.of(PipelineVersion.V1));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void getCreatePlanForChildrenNodes() {
    List<YamlNode> stepsNode = pipelineYamlField.getNode()
                                   .getField("stages")
                                   .getNode()
                                   .asArray()
                                   .get(0)
                                   .getField("spec")
                                   .getNode()
                                   .getField("steps")
                                   .getNode()
                                   .asArray();
    LinkedHashMap<String, PlanCreationResponse> planForChildrenNodes =
        planCreator.createPlanForChildrenNodes(PlanCreationContext.builder().build(), new YamlField(stepsNode.get(0)));
    assertThat(planForChildrenNodes).isNotNull();
    assertThat(planForChildrenNodes.size()).isEqualTo(2);
    for (YamlNode yamlNode : stepsNode.get(0).getField("spec").getNode().getField("steps").getNode().asArray()) {
      assertThat(planForChildrenNodes.get(yamlNode.getUuid())).isNotNull();
      assertThat(planForChildrenNodes.get(yamlNode.getUuid())
                     .getDependencies()
                     .getDependenciesMap()
                     .get(yamlNode.getUuid())
                     .toString())
          .isEqualTo(yamlNode.getYamlPath());
    }

    List<YamlNode> stagesNode = pipelineYamlField.getNode().getField("stages").getNode().asArray();

    planForChildrenNodes =
        planCreator.createPlanForChildrenNodes(PlanCreationContext.builder().build(), new YamlField(stagesNode.get(1)));
    assertThat(planForChildrenNodes).isNotNull();
    assertThat(planForChildrenNodes.size()).isEqualTo(2);

    for (YamlNode yamlNode : stagesNode.get(1).getField("spec").getNode().getField("stages").getNode().asArray()) {
      assertThat(planForChildrenNodes.get(yamlNode.getUuid())).isNotNull();
      assertThat(planForChildrenNodes.get(yamlNode.getUuid())
                     .getDependencies()
                     .getDependenciesMap()
                     .get(yamlNode.getUuid())
                     .toString())
          .isEqualTo(yamlNode.getYamlPath());
    }
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void getCreatePlanForParentNode() {
    List<YamlNode> stagesNode = pipelineYamlField.getNode().getField("stages").getNode().asArray();
    List<String> childrenIds = new ArrayList<>();
    for (YamlNode yamlNode : stagesNode.get(1).getField("spec").getNode().getField("stages").getNode().asArray()) {
      childrenIds.add(yamlNode.getUuid());
    }

    PlanNode planNode = planCreator.createPlanForParentNode(
        PlanCreationContext.builder().build(), new YamlField(stagesNode.get(1)), childrenIds);
    assertThat(planNode).isNotNull();
    assertThat(planNode.getStepType()).isEqualTo(NGForkStep.STEP_TYPE);
    assertThat(planNode.getAdviserObtainments()).isEmpty();
    assertThat(planNode.getFacilitatorObtainments().size()).isEqualTo(1);
    assertThat(planNode.getFacilitatorObtainments().get(0).getType())
        .isEqualTo(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build());

    doReturn("nextNodeUuid").when(kryoSerializer).asObject((byte[]) any());
    doReturn("adviserResponse".getBytes()).when(kryoSerializer).asBytes(any());

    planNode = planCreator.createPlanForParentNode(
        PlanCreationContext.builder()
            .dependency(Dependency.newBuilder()
                            .putMetadata(YAMLFieldNameConstants.NEXT_ID,
                                ByteString.copyFrom("nextNodeUuid".getBytes(StandardCharsets.UTF_8)))
                            .build())
            .build(),
        new YamlField(stagesNode.get(1)), childrenIds);

    assertThat(planNode).isNotNull();
    assertThat(planNode.getStepType()).isEqualTo(NGForkStep.STEP_TYPE);
    assertThat(planNode.getAdviserObtainments().size()).isEqualTo(1);
    assertThat(planNode.getAdviserObtainments().get(0).getType().getType())
        .isEqualTo(OrchestrationAdviserTypes.NEXT_STAGE.name());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetLayoutNodeInfo() {
    List<YamlNode> stepsNode = pipelineYamlField.getNode()
                                   .getField("stages")
                                   .getNode()
                                   .asArray()
                                   .get(0)
                                   .getField("spec")
                                   .getNode()
                                   .getField("steps")
                                   .getNode()
                                   .asArray();
    assertThat(planCreator.getLayoutNodeInfo(PlanCreationContext.builder().build(), new YamlField(stepsNode.get(0))))
        .isEqualTo(GraphLayoutResponse.builder().build());

    List<YamlNode> stagesNode = pipelineYamlField.getNode().getField("stages").getNode().asArray();
    GraphLayoutResponse response =
        planCreator.getLayoutNodeInfo(PlanCreationContext.builder().build(), new YamlField(stagesNode.get(1)));

    assertThat(response.getLayoutNodes().size()).isEqualTo(3);

    for (YamlNode yamlNode : stagesNode.get(1).getField("spec").getNode().getField("stages").getNode().asArray()) {
      assertThat(response.getLayoutNodes().get(yamlNode.getUuid())).isNotNull();
      assertThat(response.getLayoutNodes().get(yamlNode.getUuid()).getNodeGroup()).isEqualTo("STAGE");
      assertThat(response.getLayoutNodes().get(yamlNode.getUuid()).getNodeType()).isEqualTo("custom");
    }
    assertThat(response.getLayoutNodes().get(stagesNode.get(1).getUuid())).isNotNull();
    assertThat(response.getLayoutNodes().get(stagesNode.get(1).getUuid()).getNodeGroup()).isEqualTo("STAGE");
    assertThat(response.getLayoutNodes().get(stagesNode.get(1).getUuid()).getNodeType())
        .isEqualTo(YAMLFieldNameConstants.PARALLEL);
    assertThat(
        response.getLayoutNodes().get(stagesNode.get(1).getUuid()).getEdgeLayoutList().getCurrentNodeChildrenCount())
        .isEqualTo(2);

    assertThat(response.getLayoutNodes().get(stagesNode.get(1).getUuid()).getEdgeLayoutList().getNextIdsCount())
        .isEqualTo(0);

    doReturn("nextNodeUuid").when(kryoSerializer).asObject((byte[]) any());
    doReturn("adviserResponse".getBytes()).when(kryoSerializer).asBytes(any());

    response = planCreator.getLayoutNodeInfo(
        PlanCreationContext.builder()
            .dependency(Dependency.newBuilder()
                            .putMetadata(YAMLFieldNameConstants.NEXT_ID,
                                ByteString.copyFrom("nextNodeUuid".getBytes(StandardCharsets.UTF_8)))
                            .build())
            .build(),
        new YamlField(stagesNode.get(1)));

    assertThat(response.getLayoutNodes().get(stagesNode.get(1).getUuid()).getEdgeLayoutList().getNextIdsCount())
        .isEqualTo(1);
    assertThat(
        response.getLayoutNodes().get(stagesNode.get(1).getUuid()).getEdgeLayoutList().getCurrentNodeChildrenCount())
        .isEqualTo(2);
    assertThat(response.getLayoutNodes().get(stagesNode.get(1).getUuid()).getEdgeLayoutList().getNextIds(0))
        .isEqualTo("nextNodeUuid");
  }
}
