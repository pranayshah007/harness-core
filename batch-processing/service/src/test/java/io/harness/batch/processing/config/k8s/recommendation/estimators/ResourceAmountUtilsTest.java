/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation.estimators;

import static io.harness.batch.processing.config.k8s.recommendation.estimators.ResourceAmountUtils.convertToReadableForm;
import static io.harness.batch.processing.config.k8s.recommendation.estimators.ResourceAmountUtils.readableCpuAmount;
import static io.harness.batch.processing.config.k8s.recommendation.estimators.ResourceAmountUtils.readableMemoryAmount;
import static io.harness.rule.OwnerRule.AVMOHAN;

import static software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement.CPU;
import static software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement.MEMORY;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ResourceAmountUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testCpuConversion() throws Exception {
    assertThat(readableCpuAmount(234)).isEqualTo("234m");
    assertThat(readableCpuAmount(1000)).isEqualTo("1");
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testMemoryConversion() throws Exception {
    assertThat(readableMemoryAmount(25 * 1000 * 1000)).isEqualTo("25M");
    assertThat(readableMemoryAmount(1000L * 1000 * 1000)).isEqualTo("1G");
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testMemoryConversionRoundsUpForReadability() throws Exception {
    assertThat(readableMemoryAmount(1730769L)).isEqualTo("1731k");
    assertThat(readableMemoryAmount(61730769L)).isEqualTo("62M");
    assertThat(readableMemoryAmount(861730769L)).isEqualTo("862M");
    assertThat(readableMemoryAmount(1861730769L)).isEqualTo("1862M");
    assertThat(readableMemoryAmount(21861730769L)).isEqualTo("22G");
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testConvertResources() throws Exception {
    assertThat(convertToReadableForm(ImmutableMap.of(CPU, 40L, MEMORY, 25L * 1000 * 1000)))
        .isEqualTo(ImmutableMap.of(CPU, "40m", MEMORY, "25M"));
    assertThat(convertToReadableForm(ImmutableMap.of(MEMORY, 25L * 1000 * 1000)))
        .isEqualTo(ImmutableMap.of(MEMORY, "25M"));
    assertThat(convertToReadableForm(ImmutableMap.of(CPU, 40L))).isEqualTo(ImmutableMap.of(CPU, "40m"));
  }
}
