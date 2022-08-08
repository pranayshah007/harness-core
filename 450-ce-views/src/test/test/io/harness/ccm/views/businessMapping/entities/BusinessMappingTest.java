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
import io.harness.ccm.views.businessMapping.helper.BusinessMappingHelper;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class BusinessMappingTest extends CategoryTest {
  public static final String TEST_NAME = "TEST_NAME";

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testConstructor() {
    final long currentTime = System.currentTimeMillis();
    final BusinessMapping businessMapping =
        new BusinessMapping(BusinessMappingHelper.TEST_ID, TEST_NAME, BusinessMappingHelper.TEST_ACCOUNT_ID,
            BusinessMappingHelper.getCostTargets(), null, null, currentTime, currentTime, null, null);
    assertThat(businessMapping).isNotNull();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testBuilder() {
    final BusinessMapping businessMapping = BusinessMapping.builder()
                                                .uuid(BusinessMappingHelper.TEST_ID)
                                                .name(TEST_NAME)
                                                .accountId(BusinessMappingHelper.TEST_ACCOUNT_ID)
                                                .sharedCosts(BusinessMappingHelper.getSharedCosts())
                                                .build();
    assertThat(businessMapping).isNotNull();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testJsonStringToObjectConversion() throws IOException {
    final List<CostTarget> costTargets = BusinessMappingHelper.getCostTargets();
    final String businessMappingJsonString = "{\n"
        + "\t\"uuid\": \"" + BusinessMappingHelper.TEST_ID + "\",\n"
        + "\t\"name\": \"" + TEST_NAME + "\",\n"
        + "\t\"accountId\": \"" + BusinessMappingHelper.TEST_ACCOUNT_ID + "\",\n"
        + "\t\"costTargets\": " + new ObjectMapper().writeValueAsString(costTargets) + "\n"
        + "}";
    final BusinessMapping businessMapping =
        new ObjectMapper().readValue(businessMappingJsonString, BusinessMapping.class);
    assertThat(businessMapping.getUuid()).isEqualTo(BusinessMappingHelper.TEST_ID);
    assertThat(businessMapping.getName()).isEqualTo(TEST_NAME);
    assertThat(businessMapping.getAccountId()).isEqualTo(BusinessMappingHelper.TEST_ACCOUNT_ID);
    assertThat(businessMapping.getCostTargets()).isEqualTo(costTargets);
    assertThat(businessMapping.getSharedCosts()).isNull();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testEqualsAndHashCode() {
    final BusinessMapping businessMapping1 = BusinessMappingHelper.getBusinessMapping(BusinessMappingHelper.TEST_ID);
    final BusinessMapping businessMapping2 = BusinessMappingHelper.getBusinessMapping(BusinessMappingHelper.TEST_ID);
    final BusinessMapping businessMapping3 = BusinessMappingHelper.getBusinessMapping(UUID.randomUUID().toString());
    assertThat(businessMapping1).isEqualTo(businessMapping2);
    assertThat(businessMapping1).isNotEqualTo(businessMapping3);
    assertThat(businessMapping1.hashCode()).isEqualTo(businessMapping2.hashCode());
    assertThat(businessMapping1.hashCode()).isNotEqualTo(businessMapping3.hashCode());
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testToString() {
    final BusinessMapping businessMapping1 = BusinessMappingHelper.getBusinessMapping(BusinessMappingHelper.TEST_ID);
    final BusinessMapping businessMapping2 = BusinessMappingHelper.getBusinessMapping(BusinessMappingHelper.TEST_ID);
    final BusinessMapping businessMapping3 = BusinessMappingHelper.getBusinessMapping(UUID.randomUUID().toString());
    assertThat(businessMapping1.toString()).isEqualTo(businessMapping2.toString());
    assertThat(businessMapping1.toString()).isNotEqualTo(businessMapping3.toString());
  }
}
