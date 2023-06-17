/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.businessmapping.entities;

import static io.harness.rule.OwnerRule.SAHILDEEP;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.views.businessmapping.helper.BusinessMappingTestHelper;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class UnallocatedCostTest extends CategoryTest {
  public static final String TEST_LABEL = "TEST_LABEL";

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testConstructor() {
    final UnallocatedCost unallocatedCost =
        new UnallocatedCost(UnallocatedCostStrategy.DISPLAY_NAME, TEST_LABEL, null, null);
    assertThat(unallocatedCost).isNotNull();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testBuilder() {
    final UnallocatedCost unallocatedCost = UnallocatedCost.builder().strategy(UnallocatedCostStrategy.HIDE).build();
    assertThat(unallocatedCost).isNotNull();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testJsonStringToObjectConversion() throws IOException {
    final String unallocatedCostJsonString = "{\n"
        + "\t\"strategy\": \"" + UnallocatedCostStrategy.DISPLAY_NAME.toString() + "\",\n"
        + "\t\"label\": \"" + TEST_LABEL + "\"\n"
        + "}";
    final UnallocatedCost unallocatedCost =
        new ObjectMapper().readValue(unallocatedCostJsonString, UnallocatedCost.class);
    assertThat(unallocatedCost.getStrategy()).isEqualTo(UnallocatedCostStrategy.DISPLAY_NAME);
    assertThat(unallocatedCost.getLabel()).isEqualTo(TEST_LABEL);
    assertThat(unallocatedCost.getSharingStrategy()).isNull();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testEqualsAndHashCode() {
    final UnallocatedCost unallocatedCost1 =
        BusinessMappingTestHelper.getUnallocatedCost(UnallocatedCostStrategy.DISPLAY_NAME);
    final UnallocatedCost unallocatedCost2 =
        BusinessMappingTestHelper.getUnallocatedCost(UnallocatedCostStrategy.DISPLAY_NAME);
    final UnallocatedCost unallocatedCost3 =
        BusinessMappingTestHelper.getUnallocatedCost(UnallocatedCostStrategy.SHARE);
    assertThat(unallocatedCost1).isEqualTo(unallocatedCost2);
    assertThat(unallocatedCost1).isNotEqualTo(unallocatedCost3);
    assertThat(unallocatedCost1.hashCode()).isEqualTo(unallocatedCost2.hashCode());
    assertThat(unallocatedCost1.hashCode()).isNotEqualTo(unallocatedCost3.hashCode());
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testToString() {
    final UnallocatedCost unallocatedCost1 =
        BusinessMappingTestHelper.getUnallocatedCost(UnallocatedCostStrategy.SHARE);
    final UnallocatedCost unallocatedCost2 =
        BusinessMappingTestHelper.getUnallocatedCost(UnallocatedCostStrategy.SHARE);
    final UnallocatedCost unallocatedCost3 =
        BusinessMappingTestHelper.getUnallocatedCost(UnallocatedCostStrategy.DISPLAY_NAME);
    assertThat(unallocatedCost1.toString()).isEqualTo(unallocatedCost2.toString());
    assertThat(unallocatedCost1.toString()).isNotEqualTo(unallocatedCost3.toString());
  }
}
