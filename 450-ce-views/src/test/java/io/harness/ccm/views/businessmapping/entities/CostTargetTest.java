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
import io.harness.ccm.views.entities.ViewRule;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CostTargetTest extends CategoryTest {
  public static final String TEST_NAME = "TEST_NAME";

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testConstructor() {
    final CostTarget costTarget = new CostTarget(TEST_NAME, BusinessMappingTestHelper.getRules());
    assertThat(costTarget).isNotNull();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testBuilder() {
    final CostTarget costTarget =
        CostTarget.builder().name(TEST_NAME).rules(BusinessMappingTestHelper.getRules()).build();
    assertThat(costTarget).isNotNull();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testJsonStringToObjectConversion() throws IOException {
    final List<ViewRule> rules = BusinessMappingTestHelper.getRules();
    final String costTargetJsonString = "{\n"
        + "\t\"name\": \"" + TEST_NAME + "\",\n"
        + "\t\"rules\": " + new ObjectMapper().writeValueAsString(rules) + "\n"
        + "}";
    final CostTarget costTarget = new ObjectMapper().readValue(costTargetJsonString, CostTarget.class);
    assertThat(costTarget.getName()).isEqualTo(TEST_NAME);
    assertThat(costTarget.getRules()).isEqualTo(rules);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testEqualsAndHashCode() {
    final CostTarget costTarget1 =
        BusinessMappingTestHelper.getCostTarget(TEST_NAME, BusinessMappingTestHelper.getRules());
    final CostTarget costTarget2 =
        BusinessMappingTestHelper.getCostTarget(TEST_NAME, BusinessMappingTestHelper.getRules());
    final CostTarget costTarget3 = BusinessMappingTestHelper.getCostTarget(TEST_NAME, null);
    assertThat(costTarget1).isEqualTo(costTarget2);
    assertThat(costTarget1).isNotEqualTo(costTarget3);
    assertThat(costTarget1.hashCode()).isEqualTo(costTarget2.hashCode());
    assertThat(costTarget1.hashCode()).isNotEqualTo(costTarget3.hashCode());
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testToString() {
    final CostTarget costTarget1 =
        BusinessMappingTestHelper.getCostTarget(TEST_NAME, BusinessMappingTestHelper.getRules());
    final CostTarget costTarget2 =
        BusinessMappingTestHelper.getCostTarget(TEST_NAME, BusinessMappingTestHelper.getRules());
    final CostTarget costTarget3 = BusinessMappingTestHelper.getCostTarget(TEST_NAME, null);
    assertThat(costTarget1.toString()).isEqualTo(costTarget2.toString());
    assertThat(costTarget1.toString()).isNotEqualTo(costTarget3.toString());
  }
}
