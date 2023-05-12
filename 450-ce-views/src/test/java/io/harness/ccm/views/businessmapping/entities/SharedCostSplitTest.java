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

public class SharedCostSplitTest extends CategoryTest {
  private static final String TEST_COST_TARGET_NAME = "TEST_COST_TARGET_NAME";
  private static final double TEST_PERCENTAGE_CONTRIBUTION = 25.0;

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testConstructor() {
    final SharedCostSplit sharedCostSplit = new SharedCostSplit(TEST_COST_TARGET_NAME, TEST_PERCENTAGE_CONTRIBUTION);
    assertThat(sharedCostSplit).isNotNull();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testBuilder() {
    final SharedCostSplit sharedCostSplit = SharedCostSplit.builder()
                                                .costTargetName(TEST_COST_TARGET_NAME)
                                                .percentageContribution(TEST_PERCENTAGE_CONTRIBUTION)
                                                .build();
    assertThat(sharedCostSplit).isNotNull();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testJsonStringToObjectConversion() throws IOException {
    final String sharedCostSplitJsonString = "{\n"
        + "\t\"costTargetName\": \"" + TEST_COST_TARGET_NAME + "\",\n"
        + "\t\"percentageContribution\": " + TEST_PERCENTAGE_CONTRIBUTION + "\n"
        + "}";
    final SharedCostSplit sharedCostSplit =
        new ObjectMapper().readValue(sharedCostSplitJsonString, SharedCostSplit.class);
    assertThat(sharedCostSplit.getCostTargetName()).isEqualTo(TEST_COST_TARGET_NAME);
    assertThat(sharedCostSplit.getPercentageContribution()).isEqualTo(TEST_PERCENTAGE_CONTRIBUTION);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testEqualsAndHashCode() {
    final SharedCostSplit sharedCostSplit1 =
        BusinessMappingTestHelper.getSharedCostSplit(TEST_COST_TARGET_NAME, TEST_PERCENTAGE_CONTRIBUTION);
    final SharedCostSplit sharedCostSplit2 =
        BusinessMappingTestHelper.getSharedCostSplit(TEST_COST_TARGET_NAME, TEST_PERCENTAGE_CONTRIBUTION);
    final SharedCostSplit sharedCostSplit3 = BusinessMappingTestHelper.getSharedCostSplit("TEST_NAME", 50.0);
    assertThat(sharedCostSplit1).isEqualTo(sharedCostSplit2);
    assertThat(sharedCostSplit1).isNotEqualTo(sharedCostSplit3);
    assertThat(sharedCostSplit1.hashCode()).isEqualTo(sharedCostSplit2.hashCode());
    assertThat(sharedCostSplit1.hashCode()).isNotEqualTo(sharedCostSplit3.hashCode());
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testToString() {
    final SharedCostSplit sharedCostSplit1 =
        BusinessMappingTestHelper.getSharedCostSplit(TEST_COST_TARGET_NAME, TEST_PERCENTAGE_CONTRIBUTION);
    final SharedCostSplit sharedCostSplit2 =
        BusinessMappingTestHelper.getSharedCostSplit(TEST_COST_TARGET_NAME, TEST_PERCENTAGE_CONTRIBUTION);
    final SharedCostSplit sharedCostSplit3 = BusinessMappingTestHelper.getSharedCostSplit("TEST_NAME", 50.0);
    assertThat(sharedCostSplit1.toString()).isEqualTo(sharedCostSplit2.toString());
    assertThat(sharedCostSplit1.toString()).isNotEqualTo(sharedCostSplit3.toString());
  }
}
