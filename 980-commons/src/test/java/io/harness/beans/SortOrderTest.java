/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.rule.OwnerRule.NISHANT;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SortOrderTest extends CategoryTest {
  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testSortOrderStringConstructorForValidInputsAscOrder() {
    String[] validValues = {
        randomAlphabetic(10) + ",ASC", randomAlphabetic(10) + ",asc", randomAlphabetic(10) + ",AsC"};
    for (String inputValue : validValues) {
      SortOrder sortOrder = new SortOrder(inputValue);
      assertThat(sortOrder.getFieldName()).isNotNull();
      assertThat(sortOrder.getOrderType()).isNotNull();
      assertThat(sortOrder.getOrderType()).isEqualTo(SortOrder.OrderType.ASC);
    }
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testSortOrderStringConstructorForValidInputsDescOrder() {
    String[] validValues = {randomAlphabetic(10), randomAlphabetic(10) + ",DESC", randomAlphabetic(10) + ",desc",
        randomAlphabetic(10) + ",dEsC", randomAlphabetic(10) + "," + randomAlphabetic(5, 10)};
    for (String inputValue : validValues) {
      SortOrder sortOrder = new SortOrder(inputValue);
      assertThat(sortOrder.getFieldName()).isNotNull();
      assertThat(sortOrder.getOrderType()).isNotNull();
      assertThat(sortOrder.getOrderType()).isEqualTo(SortOrder.OrderType.DESC);
    }
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testSortOrderStringConstructorForInValidInputsDescOrder() {
    String[] validValues = {"", " ", " ,DESC", ",ASC"};
    for (String inputValue : validValues) {
      SortOrder sortOrder = new SortOrder(inputValue);
      assertThat(sortOrder.getFieldName()).isNull();
      assertThat(sortOrder.getOrderType()).isNull();
    }
  }
}
