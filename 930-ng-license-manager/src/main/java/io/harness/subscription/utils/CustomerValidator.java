/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.utils;

import io.harness.exception.InvalidArgumentsException;
import io.harness.subscription.dto.AddressDto;
import io.harness.subscription.dto.CustomerDTO;

import java.util.regex.Pattern;

public class CustomerValidator {
  private static final String ADDRESS_REGEX = "^[a-zA-Z0-9 \\-.,'&#/()@!]+$";
  private static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,4}$";
  private static final int MAX_CUSTOMER_NAME_LENGTH = 46;
  private static final int MAX_ADDRESS_LINE_LENGTH = 46;
  private static final int MAX_CITY_LENGTH = 50;
  private static final int MAX_STATE_LENGTH = 50;
  private static final int MAX_POSTAL_CODE_LENGTH = 10;
  private static final int MAX_COUNTRY_LENGTH = 50;
  private static final int MAX_EMAIL_LENGTH = 254;

  public static void validateCustomer(CustomerDTO customer) {
    if (customer == null) {
      throw new InvalidArgumentsException("CustomerDTO cannot be null");
    }

    validateAddress(customer.getAddress(), customer.getCompanyName());
    validateEmail(customer.getBillingEmail());
  }

  private static void validateAddress(AddressDto address, String customerName) {
    if (address == null) {
      throw new InvalidArgumentsException("AddressDto cannot be null");
    }

    if (!isEmpty(customerName)) {
      validateLengthAndThrowIfInvalid(customerName, MAX_CUSTOMER_NAME_LENGTH);
    }

    validateLengthAndThrowIfInvalid(address.getLine1(), MAX_ADDRESS_LINE_LENGTH);
    if (!isEmpty(address.getLine2())) {
      validateLengthAndThrowIfInvalid(address.getLine2(), MAX_ADDRESS_LINE_LENGTH);
    }
    validateLengthAndThrowIfInvalid(address.getCity(), MAX_CITY_LENGTH);
    validateLengthAndThrowIfInvalid(address.getState(), MAX_STATE_LENGTH);
    validateLengthAndThrowIfInvalid(address.getPostalCode(), MAX_POSTAL_CODE_LENGTH);
    validateLengthAndThrowIfInvalid(address.getCountry(), MAX_COUNTRY_LENGTH);
  }

  public static void validateEmail(String email) {
    if (!isValidEmail(email)) {
      throw new InvalidArgumentsException("Invalid email address");
    }
  }

  private static void validateLengthAndThrowIfInvalid(String input, int maxLength) {
    if (input != null && input.length() > maxLength) {
      throw new InvalidArgumentsException("Customer detail length exceeds maximum allowed length");
    }
    if (isEmpty(input) || !patternMatches(ADDRESS_REGEX, input)) {
      throw new InvalidArgumentsException("Invalid customer details");
    }
  }

  private static boolean isValidEmail(String email) {
    return email.length() <= MAX_EMAIL_LENGTH && patternMatches(EMAIL_REGEX, email);
  }

  private static boolean patternMatches(String regex, String input) {
    Pattern pattern = Pattern.compile(regex);
    return pattern.matcher(input).matches();
  }

  private static boolean isEmpty(String str) {
    return str == null || str.trim().isEmpty();
  }
}
