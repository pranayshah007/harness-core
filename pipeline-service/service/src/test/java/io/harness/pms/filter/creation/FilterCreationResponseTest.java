/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.filter.creation;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.PipelineServiceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.FilterCreationBlobResponse;
import io.harness.rule.Owner;

import com.google.protobuf.StringValue;
import io.fabric8.utils.Lists;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class FilterCreationResponseTest extends PipelineServiceTestBase {
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testAddReferredEntities() {
    FilterCreationResponse filterCreationResponse = FilterCreationResponse.builder().build();
    List<EntityDetailProtoDTO> entityDetailProtoDTOS =
        Lists.newArrayList(EntityDetailProtoDTO.newBuilder()
                               .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                                     .setIdentifier(StringValue.newBuilder().setValue("test").build())
                                                     .build())
                               .build());
    filterCreationResponse.addReferredEntities(entityDetailProtoDTOS);
    assertThat(filterCreationResponse.getReferredEntities()).isEqualTo(entityDetailProtoDTOS);
    filterCreationResponse.addReferredEntities(entityDetailProtoDTOS);
    assertThat(filterCreationResponse.getReferredEntities().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testAddStageNames() {
    FilterCreationResponse filterCreationResponse = FilterCreationResponse.builder().build();
    List<String> stageNames = Lists.newArrayList("stage1");
    filterCreationResponse.addStageNames(stageNames);
    assertThat(filterCreationResponse.getStageNames().get(0)).isEqualTo("stage1");
    filterCreationResponse.addStageNames(Lists.newArrayList("stage2"));
    assertThat(filterCreationResponse.getStageNames().size()).isEqualTo(2);
    assertThat(filterCreationResponse.getStageNames()).isEqualTo(Lists.newArrayList("stage1", "stage2"));
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testAddResolvedDependency() {
    FilterCreationResponse filterCreationResponse = FilterCreationResponse.builder().build();
    filterCreationResponse.addResolvedDependency("yaml", "nodeId", "yamlPath");
    assertThat(filterCreationResponse.getResolvedDependencies().getDependenciesMap().size()).isEqualTo(1);
    assertThat(filterCreationResponse.getResolvedDependencies().getDependenciesMap().get("nodeId"))
        .isEqualTo("yamlPath");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testAddDependency() {
    FilterCreationResponse filterCreationResponse = FilterCreationResponse.builder().build();
    filterCreationResponse.addDependency("yaml", "nodeId", "yamlPath");
    assertThat(filterCreationResponse.getDependencies().getDependenciesMap().size()).isEqualTo(1);
    assertThat(filterCreationResponse.getDependencies().getDependenciesMap().get("nodeId")).isEqualTo("yamlPath");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testToBlobResponse() {
    List<EntityDetailProtoDTO> entityDetailProtoDTOS =
        Lists.newArrayList(EntityDetailProtoDTO.newBuilder()
                               .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                                     .setIdentifier(StringValue.newBuilder().setValue("test").build())
                                                     .build())
                               .build());
    Dependencies dependencies = Dependencies.newBuilder().setYaml("yaml").putDependencies("a", "b").build();
    FilterCreationResponse filterCreationResponse = FilterCreationResponse.builder()
                                                        .dependencies(dependencies)
                                                        .resolvedDependencies(dependencies)
                                                        .referredEntities(entityDetailProtoDTOS)
                                                        .stageNames(Lists.newArrayList("test"))
                                                        .stageCount(1)
                                                        .build();
    FilterCreationBlobResponse response = FilterCreationBlobResponse.newBuilder()
                                              .setDeps(dependencies)
                                              .setResolvedDeps(dependencies)
                                              .addAllReferredEntities(entityDetailProtoDTOS)
                                              .addStageNames("test")
                                              .setStageCount(1)
                                              .build();
    assertThat(filterCreationResponse.toBlobResponse()).isEqualTo(response);
  }
}
