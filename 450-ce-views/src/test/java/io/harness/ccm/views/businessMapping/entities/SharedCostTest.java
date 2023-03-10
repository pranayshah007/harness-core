/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.businessMapping.entities;

import static io.harness.rule.OwnerRule.SAHILDEEP;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.views.businessMapping.helper.BusinessMappingTestHelper;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SharedCostTest extends CategoryTest {
  public static final String TEST_NAME = "TEST_NAME";

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testConstructor() {
    final SharedCost sharedCost =
        new SharedCost(TEST_NAME, BusinessMappingTestHelper.getRules(), SharingStrategy.EQUAL, null);
    assertThat(sharedCost).isNotNull();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testBuilder() {
    final SharedCost sharedCost = SharedCost.builder()
                                      .name(TEST_NAME)
                                      .rules(BusinessMappingTestHelper.getRules())
                                      .strategy(SharingStrategy.PROPORTIONAL)
                                      .build();
    assertThat(sharedCost).isNotNull();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testJsonStringToObjectConversion() throws IOException {
    final List<ViewRule> rules = BusinessMappingTestHelper.getRules();
    final String sharedCostJsonString = "{\n"
        + "\t\"name\": \"" + TEST_NAME + "\",\n"
        + "\t\"rules\": " + new ObjectMapper().writeValueAsString(rules) + ",\n"
        + "\t\"strategy\": \"" + SharingStrategy.EQUAL + "\"\n"
        + "}";
    final SharedCost sharedCost = new ObjectMapper().readValue(sharedCostJsonString, SharedCost.class);
    assertThat(sharedCost.getName()).isEqualTo(TEST_NAME);
    assertThat(sharedCost.getRules()).isEqualTo(rules);
    assertThat(sharedCost.getStrategy()).isEqualTo(SharingStrategy.EQUAL);
    assertThat(sharedCost.getSplits()).isNull();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testEqualsAndHashCode() {
    final SharedCost sharedCost1 = BusinessMappingTestHelper.getSharedCost(
        TEST_NAME, BusinessMappingTestHelper.getRules(), SharingStrategy.EQUAL, null);
    final SharedCost sharedCost2 = BusinessMappingTestHelper.getSharedCost(
        TEST_NAME, BusinessMappingTestHelper.getRules(), SharingStrategy.EQUAL, null);
    final SharedCost sharedCost3 =
        BusinessMappingTestHelper.getSharedCost(TEST_NAME, BusinessMappingTestHelper.getRules(),
            SharingStrategy.PROPORTIONAL, BusinessMappingTestHelper.getSharedCostSplits());
    assertThat(sharedCost1).isEqualTo(sharedCost2);
    assertThat(sharedCost1).isNotEqualTo(sharedCost3);
    assertThat(sharedCost1.hashCode()).isEqualTo(sharedCost2.hashCode());
    assertThat(sharedCost1.hashCode()).isNotEqualTo(sharedCost3.hashCode());
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testToString() {
    final SharedCost sharedCost1 = BusinessMappingTestHelper.getSharedCost(
        TEST_NAME, BusinessMappingTestHelper.getRules(), SharingStrategy.EQUAL, null);
    final SharedCost sharedCost2 = BusinessMappingTestHelper.getSharedCost(
        TEST_NAME, BusinessMappingTestHelper.getRules(), SharingStrategy.EQUAL, null);
    final SharedCost sharedCost3 =
        BusinessMappingTestHelper.getSharedCost(TEST_NAME, BusinessMappingTestHelper.getRules(),
            SharingStrategy.PROPORTIONAL, BusinessMappingTestHelper.getSharedCostSplits());
    assertThat(sharedCost1.toString()).isEqualTo(sharedCost2.toString());
    assertThat(sharedCost1.toString()).isNotEqualTo(sharedCost3.toString());
  }
}
