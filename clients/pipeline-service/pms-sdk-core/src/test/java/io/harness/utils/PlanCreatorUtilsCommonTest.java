/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PlanCreatorUtilsCommonTest extends PmsSdkCoreTestBase {
  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testParentInfoPutAndGet() {
    PlanCreationResponse response = PlanCreationResponse.builder()
                                        .dependencies(Dependencies.newBuilder()
                                                          .putDependencyMetadata("dep", Dependency.newBuilder().build())
                                                          .putDependencies("dep", "dep")
                                                          .build())
                                        .build();
    PlanCreatorUtilsCommon.putParentInfoInternal(
        response, "key", HarnessValue.newBuilder().setStringValue("value").build());
    PlanCreationContext context = PlanCreationContext.builder()
                                      .dependency(response.getDependencies().getDependencyMetadataMap().get("dep"))
                                      .build();
    assertThat(PlanCreatorUtilsCommon.getFromParentInfo("key", context).getStringValue()).isEqualTo("value");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testPutParentInfoString() {
    PlanCreationResponse response = PlanCreationResponse.builder()
                                        .dependencies(Dependencies.newBuilder()
                                                          .putDependencyMetadata("dep", Dependency.newBuilder().build())
                                                          .putDependencies("dep", "dep")
                                                          .build())
                                        .build();
    PlanCreatorUtilsCommon.putParentInfo(response, "key", "value");
    PlanCreationContext context = PlanCreationContext.builder()
                                      .dependency(response.getDependencies().getDependencyMetadataMap().get("dep"))
                                      .build();
    assertThat(PlanCreatorUtilsCommon.getFromParentInfo("key", context).getStringValue()).isEqualTo("value");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetFromParentInfoEmptyString() {
    PlanCreationResponse response = PlanCreationResponse.builder()
                                        .dependencies(Dependencies.newBuilder()
                                                          .putDependencyMetadata("dep", Dependency.newBuilder().build())
                                                          .putDependencies("dep", "dep")
                                                          .build())
                                        .build();
    PlanCreatorUtilsCommon.putParentInfo(response, "key", "value");
    PlanCreationContext context = PlanCreationContext.builder()
                                      .dependency(response.getDependencies().getDependencyMetadataMap().get("dep"))
                                      .build();
    assertThat(PlanCreatorUtilsCommon.getFromParentInfo("wrongKey", context).getStringValue()).isEqualTo("");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testPutParentInfoBoolean() {
    PlanCreationResponse response = PlanCreationResponse.builder()
                                        .dependencies(Dependencies.newBuilder()
                                                          .putDependencyMetadata("dep", Dependency.newBuilder().build())
                                                          .putDependencies("dep", "dep")
                                                          .build())
                                        .build();
    PlanCreatorUtilsCommon.putParentInfo(response, "key", true);
    PlanCreationContext context = PlanCreationContext.builder()
                                      .dependency(response.getDependencies().getDependencyMetadataMap().get("dep"))
                                      .build();
    assertThat(PlanCreatorUtilsCommon.getFromParentInfo("key", context).getBoolValue()).isTrue();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetFromParentInfoBooleanDefaultsToFalse() {
    PlanCreationResponse response = PlanCreationResponse.builder()
                                        .dependencies(Dependencies.newBuilder()
                                                          .putDependencyMetadata("dep", Dependency.newBuilder().build())
                                                          .putDependencies("dep", "dep")
                                                          .build())
                                        .build();
    PlanCreatorUtilsCommon.putParentInfo(response, "key", true);
    PlanCreationContext context = PlanCreationContext.builder()
                                      .dependency(response.getDependencies().getDependencyMetadataMap().get("dep"))
                                      .build();
    assertThat(PlanCreatorUtilsCommon.getFromParentInfo("wrongKey", context).getBoolValue()).isFalse();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testPopulateParentInfo() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("complex_pipeline_with_strategy.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);
    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");

    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stagesNodes = stagesYamlField.getNode().asArray();
    YamlField approvalStageField = stagesNodes.get(0).getField("stage");
    YamlField approvalSpecField = Objects.requireNonNull(approvalStageField).getNode().getField("spec");
    YamlField executionField = Objects.requireNonNull(approvalSpecField).getNode().getField("execution");
    YamlField stepGroupYamlField =
        executionField.getNode().getField("steps").getNode().asArray().get(0).getField("stepGroup");
    assertThat(stepGroupYamlField).isNotNull();

    PlanCreationContext context =
        PlanCreationContext.builder()
            .currentField(stepGroupYamlField)
            .globalContext("metadata",
                PlanCreationContextValue.newBuilder().setMetadata(ExecutionMetadata.newBuilder().build()).build())
            .build();
    PlanCreationResponse response = PlanCreationResponse.builder()
                                        .dependencies(Dependencies.newBuilder()
                                                          .putDependencyMetadata("dep", Dependency.newBuilder().build())
                                                          .putDependencies("dep", "dep")
                                                          .build())
                                        .build();
    Map<String, PlanCreationResponse> responseMap = Map.of("dep", response);

    PlanCreatorUtilsCommon.populateParentInfo(context, responseMap, PlanCreatorConstants.STEP_GROUP_ID);
    PlanCreationResponse newResponse = responseMap.get("dep");
    assertThat(newResponse.getDependencies()).isNotNull();
    assertThat(newResponse.getDependencies()
                   .getDependencyMetadataMap()
                   .get("dep")
                   .getParentInfo()
                   .getDataMap()
                   .get("stepGroupId")
                   .getStringValue())
        .isEqualTo(stepGroupYamlField.getUuid());
    assertThat(newResponse.getDependencies()
                   .getDependencyMetadataMap()
                   .get("dep")
                   .getParentInfo()
                   .getDataMap()
                   .get("strategyId")
                   .getStringValue())
        .isEqualTo(stepGroupYamlField.getUuid());
  }
}
