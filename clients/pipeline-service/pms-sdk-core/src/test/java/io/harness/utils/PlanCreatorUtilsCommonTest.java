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
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PlanCreatorUtilsCommonTest extends PmsSdkCoreTestBase {
  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetFromParentInfoString() {
    PlanCreationResponse response =
        PlanCreationResponse.builder()
            .dependencies(Dependencies.newBuilder()
                              .putDependencyMetadata("dep",
                                  Dependency.newBuilder()
                                      .setParentInfo(
                                          HarnessStruct.newBuilder()
                                              .putData("key", HarnessValue.newBuilder().setStringValue("value").build())
                                              .build())
                                      .build())
                              .putDependencies("dep", "dep")
                              .build())
            .build();
    PlanCreationContext context = PlanCreationContext.builder()
                                      .dependency(response.getDependencies().getDependencyMetadataMap().get("dep"))
                                      .build();
    assertThat(PlanCreatorUtilsCommon.getFromParentInfo("key", context).getStringValue()).isEqualTo("value");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetFromParentInfoEmptyString() {
    PlanCreationResponse response =
        PlanCreationResponse.builder()
            .dependencies(Dependencies.newBuilder()
                              .putDependencyMetadata("dep",
                                  Dependency.newBuilder()
                                      .setParentInfo(
                                          HarnessStruct.newBuilder()
                                              .putData("key", HarnessValue.newBuilder().setStringValue("value").build())
                                              .build())
                                      .build())
                              .putDependencies("dep", "dep")
                              .build())
            .build();
    PlanCreationContext context = PlanCreationContext.builder()
                                      .dependency(response.getDependencies().getDependencyMetadataMap().get("dep"))
                                      .build();
    assertThat(PlanCreatorUtilsCommon.getFromParentInfo("wrongKey", context).getStringValue()).isEqualTo("");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetFromParentInfoBooleanDefaultsToFalse() {
    PlanCreationResponse response =
        PlanCreationResponse.builder()
            .dependencies(
                Dependencies.newBuilder()
                    .putDependencyMetadata("dep",
                        Dependency.newBuilder()
                            .setParentInfo(HarnessStruct.newBuilder()
                                               .putData("key", HarnessValue.newBuilder().setBoolValue(true).build())
                                               .build())
                            .build())
                    .putDependencies("dep", "dep")
                    .build())
            .build();
    PlanCreationContext context = PlanCreationContext.builder()
                                      .dependency(response.getDependencies().getDependencyMetadataMap().get("dep"))
                                      .build();
    assertThat(PlanCreatorUtilsCommon.getFromParentInfo("key", context).getBoolValue()).isTrue();
    assertThat(PlanCreatorUtilsCommon.getFromParentInfo("wrongKey", context).getBoolValue()).isFalse();
  }
}
