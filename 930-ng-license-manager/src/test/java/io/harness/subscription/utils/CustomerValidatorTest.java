/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.utils;

import static io.harness.rule.OwnerRule.TOMMY;
import static io.harness.subscription.constant.SubscriptionTestConstant.DEFAULT_CUSTOMER_DTO;
import static io.harness.subscription.constant.SubscriptionTestConstant.INVALID_CHARACTERS_CUSTOMER_DTO;
import static io.harness.subscription.constant.SubscriptionTestConstant.INVALID_EMAIL_CUSTOMER_DTO;
import static io.harness.subscription.constant.SubscriptionTestConstant.MAX_LENGTH_EXCEEDED_CUSTOMER_DTO;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArgumentsException;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CustomerValidatorTest extends CategoryTest {
  @Test
  @Owner(developers = TOMMY)
  @Category(UnitTests.class)
  public void testValidateCustomer() {
    CustomerValidator.validateCustomer(DEFAULT_CUSTOMER_DTO);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = TOMMY)
  @Category(UnitTests.class)
  public void testValidateCustomerInvalidEmail() {
    CustomerValidator.validateCustomer(INVALID_EMAIL_CUSTOMER_DTO);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = TOMMY)
  @Category(UnitTests.class)
  public void testValidateCustomerInvalidCharacters() {
    CustomerValidator.validateCustomer(INVALID_CHARACTERS_CUSTOMER_DTO);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = TOMMY)
  @Category(UnitTests.class)
  public void testValidateCustomerMaxLengthExceeded() {
    CustomerValidator.validateCustomer(MAX_LENGTH_EXCEEDED_CUSTOMER_DTO);
  }
}
