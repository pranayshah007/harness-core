/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.creation;

import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.GraphLayoutInfo;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.PlanCreationBlobResponse;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.rule.Owner;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanCreatorBlobResponseUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testMerge() {
    assertThatCode(() -> PlanCreationBlobResponseUtils.merge(PlanCreationBlobResponse.newBuilder(), null))
        .doesNotThrowAnyException();
    assertThatCode(()
                       -> PlanCreationBlobResponseUtils.merge(
                           PlanCreationBlobResponse.newBuilder(), PlanCreationBlobResponse.newBuilder().build()))
        .doesNotThrowAnyException();

    PlanCreationBlobResponse.Builder builder =
        PlanCreationBlobResponse.newBuilder()
            .setDeps(Dependencies.newBuilder().putDependencies("id1", "this/fqn").build())
            .putNodes("id2", PlanNodeProto.newBuilder().setUuid("id2").build())
            .putContext("k1", PlanCreationContextValue.newBuilder().setStringValue("v1").build())
            .setGraphLayoutInfo(GraphLayoutInfo.newBuilder()
                                    .putLayoutNodes("id2", GraphLayoutNode.newBuilder().setNodeUUID("id2").build())
                                    .build());
    PlanCreationBlobResponseUtils.merge(builder,
        PlanCreationBlobResponse.newBuilder()
            .setDeps(Dependencies.newBuilder().putDependencies("id3", "this/fqn").build())
            .putNodes("id1", PlanNodeProto.newBuilder().setUuid("id1").build())
            .putContext("k2", PlanCreationContextValue.newBuilder().setStringValue("v2").build())
            .setStartingNodeId("id3")
            .setGraphLayoutInfo(GraphLayoutInfo.newBuilder()
                                    .setStartingNodeId("id3")
                                    .putLayoutNodes("id1", GraphLayoutNode.newBuilder().setNodeUUID("id1").build())
                                    .build())
            .build());

    PlanCreationBlobResponse blobResponse = builder.build();
    assertThat(blobResponse.getStartingNodeId()).isEqualTo("id3");
    assertThat(blobResponse.getDeps().getDependenciesMap().keySet()).containsExactly("id3");
    assertThat(blobResponse.getNodesMap().keySet()).containsExactlyInAnyOrder("id1", "id2");
    assertThat(blobResponse.getContextMap().keySet()).containsExactlyInAnyOrder("k1", "k2");

    assertThat(blobResponse.getGraphLayoutInfo().getStartingNodeId()).isEqualTo("id3");
    assertThat(blobResponse.getGraphLayoutInfo().getLayoutNodesMap().keySet()).containsExactlyInAnyOrder("id1", "id2");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testUpdates() {
    PlanCreationBlobResponse.Builder variable = PlanCreationBlobResponse.newBuilder();
    Map<String, String> fqnMap = new LinkedHashMap<>();
    fqnMap.put("pipeline/stages", "yaml1");
    fqnMap.put("pipeline/stages/[0]/stage/spec/execution/steps", "yaml2");
    fqnMap.put(
        "pipeline/stages/[0]/stage/spec/infrastructure/infrastructureDefinition/provisioner/steps/step2", "yaml3");
    fqnMap.put("pipeline/stages/[0]", "yaml4");
    YamlUpdates yamlUpdates1 = YamlUpdates.newBuilder().putAllFqnToYaml(fqnMap).build();
    PlanCreationBlobResponse currentResponse =
        PlanCreationBlobResponse.newBuilder().setYamlUpdates(yamlUpdates1).build();
    PlanCreationBlobResponse creationBlobResponse1 =
        PlanCreationBlobResponseUtils.addYamlUpdates(variable, currentResponse);

    assertThat(creationBlobResponse1.getYamlUpdates().getFqnToYamlMap()).containsExactlyEntriesOf(fqnMap);
  }
}
