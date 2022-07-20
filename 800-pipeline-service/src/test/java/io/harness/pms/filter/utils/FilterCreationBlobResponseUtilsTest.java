/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.filter.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.FilterCreationBlobResponse;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.filter.creation.FilterCreationBlobResponseUtils;
import io.harness.pms.filter.creation.FilterCreationResponseWrapper;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class FilterCreationBlobResponseUtilsTest extends CategoryTest {
  private String getYamlFieldFromGivenFileName(String file) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream(file);
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    return yaml;
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testUpdateStageCount() {
    FilterCreationBlobResponse.Builder filterCreationResponse1 =
        FilterCreationBlobResponse.newBuilder().setStageCount(1);
    FilterCreationBlobResponse filterCreationResponse2 =
        FilterCreationBlobResponse.newBuilder().setStageCount(2).build();
    FilterCreationBlobResponseUtils.updateStageCount(filterCreationResponse1, filterCreationResponse2);
    assertThat(filterCreationResponse1.getStageCount()).isEqualTo(3);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testMergeFilters() {
    FilterCreationBlobResponse filterCreationResponse =
        FilterCreationBlobResponse.newBuilder().setFilter("filter").build();
    FilterCreationResponseWrapper filterCreationResponseWrapper =
        FilterCreationResponseWrapper.builder().serviceName("pipeline").response(filterCreationResponse).build();
    Map<String, String> filters = new HashMap<>();
    FilterCreationBlobResponseUtils.mergeFilters(filterCreationResponseWrapper, filters);
    assertThat(filters.containsKey("pipeline")).isTrue();
    assertThat(filters.get("pipeline")).isEqualTo("filter");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testMergeReferredEntities() {
    EntityDetailProtoDTO entityDetailProtoDTO = EntityDetailProtoDTO.newBuilder().build();
    FilterCreationBlobResponse.Builder filterCreationResponse1 =
        FilterCreationBlobResponse.newBuilder().addReferredEntities(entityDetailProtoDTO);
    FilterCreationBlobResponse filterCreationResponse2 =
        FilterCreationBlobResponse.newBuilder().addReferredEntities(entityDetailProtoDTO).build();
    FilterCreationBlobResponseUtils.mergeReferredEntities(filterCreationResponse1, filterCreationResponse2);
    assertThat(filterCreationResponse1.getReferredEntitiesCount()).isEqualTo(2);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testMergeStageNames() {
    FilterCreationBlobResponse.Builder filterCreationResponse1 =
        FilterCreationBlobResponse.newBuilder().addStageNames("stage1");
    FilterCreationBlobResponse filterCreationResponse2 =
        FilterCreationBlobResponse.newBuilder().addStageNames("stage2").build();
    FilterCreationBlobResponseUtils.mergeStageNames(filterCreationResponse1, filterCreationResponse2);
    assertThat(filterCreationResponse1.getStageNamesList().size()).isEqualTo(2);
    assertThat(filterCreationResponse1.getStageNamesList().contains("stage1")).isTrue();
    assertThat(filterCreationResponse1.getStageNamesList().contains("stage2")).isTrue();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testMergeResolvedDependencies() {
    Map<String, String> deps = new HashMap<>();
    deps.put("uuid1", "value1");
    deps.put("uuid2", "value2");

    FilterCreationBlobResponse.Builder filterCreationResponse1 =
        FilterCreationBlobResponse.newBuilder().setDeps(Dependencies.newBuilder().putAllDependencies(deps).build());
    FilterCreationBlobResponse filterCreationResponse2 =
        FilterCreationBlobResponse.newBuilder()
            .setResolvedDeps(Dependencies.newBuilder().putDependencies("uuid1", "value1").build())
            .build();
    FilterCreationBlobResponseUtils.mergeResolvedDependencies(filterCreationResponse1, filterCreationResponse2);
    // Resolved dependencies
    assertThat(filterCreationResponse1.getResolvedDeps().getDependenciesMap().size()).isEqualTo(1);
    assertThat(filterCreationResponse1.getResolvedDeps().containsDependencies("uuid1")).isTrue();

    // dependencies
    assertThat(filterCreationResponse1.getDeps().getDependenciesMap().size()).isEqualTo(1);
    assertThat(filterCreationResponse1.getDeps().containsDependencies("uuid1")).isFalse();
    assertThat(filterCreationResponse1.getDeps().containsDependencies("uuid2")).isTrue();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testMergeDependencies() {
    Map<String, String> deps = new HashMap<>();
    deps.put("uuid1", "value1");
    deps.put("uuid2", "value2");

    FilterCreationBlobResponse.Builder filterCreationResponse1 =
        FilterCreationBlobResponse.newBuilder().setDeps(Dependencies.newBuilder().putAllDependencies(deps).build());
    FilterCreationBlobResponse filterCreationResponse2 =
        FilterCreationBlobResponse.newBuilder()
            .setDeps(Dependencies.newBuilder().putDependencies("uuid3", "value3").build())
            .build();
    FilterCreationBlobResponseUtils.mergeDependencies(filterCreationResponse1, filterCreationResponse2);
    // dependencies
    assertThat(filterCreationResponse1.getDeps().getDependenciesMap().size()).isEqualTo(3);
    assertThat(filterCreationResponse1.getDeps().containsDependencies("uuid1")).isTrue();
    assertThat(filterCreationResponse1.getDeps().containsDependencies("uuid2")).isTrue();
    assertThat(filterCreationResponse1.getDeps().containsDependencies("uuid3")).isTrue();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddYamlUpdates() {
    FilterCreationBlobResponse.Builder filterCreationResponse1 = FilterCreationBlobResponse.newBuilder();
    FilterCreationBlobResponse filterCreationResponse2 = FilterCreationBlobResponse.newBuilder().build();
    FilterCreationBlobResponseUtils.addYamlUpdates(filterCreationResponse1, filterCreationResponse2);
    assertThat(filterCreationResponse1.getYamlUpdates().getFqnToYamlMap().size()).isEqualTo(0);

    // yaml Updates
    filterCreationResponse2 = filterCreationResponse2.toBuilder()
                                  .setYamlUpdates(YamlUpdates.newBuilder().putFqnToYaml("fqn1", "value1").build())
                                  .build();
    FilterCreationBlobResponseUtils.addYamlUpdates(filterCreationResponse1, filterCreationResponse2);
    assertThat(filterCreationResponse1.getYamlUpdates().getFqnToYamlMap().size()).isEqualTo(1);
    assertThat(filterCreationResponse1.getYamlUpdates().getFqnToYamlMap().containsKey("fqn1")).isTrue();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testRemoveInitialDependencies() throws IOException {
    // have added a template ref for spec yaml field
    Map<String, String> deps = new HashMap<>();
    deps.put("uuid1", "pipeline");
    deps.put("uuid2", "pipeline/stages/[0]/stage/spec");

    String pipelineYamlField = getYamlFieldFromGivenFileName("pipeline.yml");

    Dependencies initialDependencies =
        Dependencies.newBuilder().setYaml(pipelineYamlField).putAllDependencies(deps).build();

    Dependencies resultedDependencies = FilterCreationBlobResponseUtils.removeTemplateDependencies(initialDependencies);
    assertThat(resultedDependencies.getDependenciesMap().size()).isEqualTo(1);
    assertThat(resultedDependencies.getDependenciesMap().containsKey("uuid1")).isEqualTo(true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testMergeResponses() throws IOException {
    Map<String, String> deps = new HashMap<>();
    deps.put("uuid1", "value1");
    deps.put("uuid2", "value2");

    FilterCreationBlobResponse.Builder finalCreationResponse =
        FilterCreationBlobResponse.newBuilder().setStageCount(1).addStageNames("stage1").setDeps(
            Dependencies.newBuilder().putAllDependencies(deps).build());

    // response as null
    FilterCreationBlobResponseUtils.mergeResponses(
        finalCreationResponse, FilterCreationResponseWrapper.builder().build(), null);

    FilterCreationBlobResponse filterCreationResponse =
        FilterCreationBlobResponse.newBuilder()
            .setFilter("filter")
            .setStageCount(2)
            .setResolvedDeps(Dependencies.newBuilder().putDependencies("uuid4", "value4").build())
            .addStageNames("stage2")
            .setDeps(Dependencies.newBuilder().putDependencies("uuid3", "value3"))
            .setYamlUpdates(YamlUpdates.newBuilder().putFqnToYaml("fqn1", "value1").build())
            .build();
    FilterCreationResponseWrapper filterCreationResponseWrapper =
        FilterCreationResponseWrapper.builder().serviceName("pipeline").response(filterCreationResponse).build();
    Map<String, String> filters = new HashMap<>();
    FilterCreationBlobResponseUtils.mergeFilters(filterCreationResponseWrapper, filters);

    FilterCreationBlobResponseUtils.mergeResponses(finalCreationResponse, filterCreationResponseWrapper, filters);
    assertThat(finalCreationResponse.getStageCount()).isEqualTo(3);
    assertThat(finalCreationResponse.getDeps().getDependenciesMap().size()).isEqualTo(3);
    assertThat(finalCreationResponse.getResolvedDeps().getDependenciesMap().size()).isEqualTo(1);
    assertThat(finalCreationResponse.getYamlUpdates()).isNotNull();
    assertThat(finalCreationResponse.getStageNamesCount()).isEqualTo(2);
  }
}
