/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.commons.utils;

import static io.harness.ccm.commons.utils.ResourceAmountUtils.convertToReadableForm;
import static io.harness.ccm.commons.utils.ResourceAmountUtils.readableCpuAmount;
import static io.harness.ccm.commons.utils.ResourceAmountUtils.readableMemoryAmount;
import static io.harness.rule.OwnerRule.TRUNAPUSHPA;

import static software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement.CPU;
import static software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement.MEMORY;

import static io.kubernetes.client.custom.Quantity.Format.BINARY_SI;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ResourceAmountUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testCpuConversion() throws Exception {
    assertThat(readableCpuAmount(234, BINARY_SI)).isEqualTo("229m");
    assertThat(readableCpuAmount(1024, BINARY_SI)).isEqualTo("1");
    assertThat(readableCpuAmount(1024 * 1024, BINARY_SI)).isEqualTo("1024");
    assertThat(readableCpuAmount(1024L * 1024L * 1024L, BINARY_SI)).isEqualTo("1048576");
    assertThat(readableCpuAmount(103487478L, BINARY_SI)).isEqualTo("101061990m");
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testMemoryConversion() throws Exception {
    assertThat(readableMemoryAmount(25 * 1024 * 1024, BINARY_SI)).isEqualTo("25Mi");
    assertThat(readableMemoryAmount(1010L * 1010 * 1010, BINARY_SI)).isEqualTo("982.6Mi");
    assertThat(readableMemoryAmount(1024L * 1024 * 1024, BINARY_SI)).isEqualTo("1Gi");
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testMemoryConversionRoundsUpForReadability() throws Exception {
    assertThat(readableMemoryAmount(1L, BINARY_SI)).isEqualTo("1");
    assertThat(readableMemoryAmount(12L, BINARY_SI)).isEqualTo("12");
    assertThat(readableMemoryAmount(102L, BINARY_SI)).isEqualTo("102");
    assertThat(readableMemoryAmount(1024L, BINARY_SI)).isEqualTo("1Ki");
    assertThat(readableMemoryAmount(1034L, BINARY_SI)).isEqualTo("1.0Ki");
    assertThat(readableMemoryAmount(1730769L, BINARY_SI)).isEqualTo("1.7Mi");
    assertThat(readableMemoryAmount(61730769L, BINARY_SI)).isEqualTo("58.9Mi");
    assertThat(readableMemoryAmount(861730769L, BINARY_SI)).isEqualTo("821.8Mi");
    assertThat(readableMemoryAmount(1861730769L, BINARY_SI)).isEqualTo("1.7Gi");
    assertThat(readableMemoryAmount(21861730769L, BINARY_SI)).isEqualTo("20.4Gi");
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testConvertResources() throws Exception {
    assertThat(convertToReadableForm(ImmutableMap.of(CPU, 40L, MEMORY, 25L * 1024 * 1024), BINARY_SI))
        .isEqualTo(ImmutableMap.of(CPU, "39m", MEMORY, "25Mi"));
    assertThat(convertToReadableForm(ImmutableMap.of(MEMORY, 25L * 1000 * 1000), BINARY_SI))
        .isEqualTo(ImmutableMap.of(MEMORY, "23.8Mi"));
    assertThat(convertToReadableForm(ImmutableMap.of(CPU, 40L), BINARY_SI)).isEqualTo(ImmutableMap.of(CPU, "39m"));
  }
}
