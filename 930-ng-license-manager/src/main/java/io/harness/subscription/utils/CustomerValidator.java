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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Utility class for validating customer data including addresses and email.
 */
@Slf4j
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

  /**
   * Validates a CustomerDTO instance.
   *
   * @param customer The CustomerDTO instance to validate.
   * @throws InvalidArgumentsException If validation fails.
   */
  public static void validateCustomer(CustomerDTO customer) {
    if (customer == null) {
      String errorMessage = "Customer is found null and cannot be validated.";
      log.error(errorMessage);
      throw new InvalidArgumentsException(errorMessage);
    }

    validateAddress(customer.getAddress(), customer.getCompanyName());
    validateEmail(customer.getBillingEmail());
  }

  private static void validateAddress(AddressDto address, String customerName) {
    if (address == null) {
      String errorMessage = "Address is found null and cannot be validated.";
      log.error(errorMessage);
      throw new InvalidArgumentsException(errorMessage);
    }

    if (!StringUtils.isEmpty(customerName)) {
      validateInput(customerName, MAX_CUSTOMER_NAME_LENGTH);
    }

    validateInput(address.getLine1(), MAX_ADDRESS_LINE_LENGTH);
    if (!StringUtils.isEmpty(address.getLine2())) {
      validateInput(address.getLine2(), MAX_ADDRESS_LINE_LENGTH);
    }
    validateInput(address.getCity(), MAX_CITY_LENGTH);
    validateInput(address.getState(), MAX_STATE_LENGTH);
    validateInput(address.getPostalCode(), MAX_POSTAL_CODE_LENGTH);
    validateInput(address.getCountry(), MAX_COUNTRY_LENGTH);
  }

  /**
   * Validates an email address.
   *
   * @param email The email address to validate.
   * @throws InvalidArgumentsException If the email address is invalid.
   */
  public static void validateEmail(String email) {
    if (!isValidEmail(email)) {
      String errorMessage = "Invalid email address";
      log.error(errorMessage);
      throw new InvalidArgumentsException(errorMessage);
    }
  }

  private static void validateInput(String input, int maxLength) {
    if (input != null && input.length() > maxLength) {
      String errorMessage = "Customer detail length exceeds maximum allowed length";
      log.error(errorMessage);
      throw new InvalidArgumentsException(errorMessage);
    }
    if (StringUtils.isEmpty(input) || !patternMatches(ADDRESS_REGEX, input)) {
      String errorMessage = "Invalid customer details";
      log.error(errorMessage);
      throw new InvalidArgumentsException(errorMessage);
    }
  }

  private static boolean isValidEmail(String email) {
    return email.length() <= MAX_EMAIL_LENGTH && patternMatches(EMAIL_REGEX, email);
  }

  private static boolean patternMatches(String regex, String input) {
    Pattern pattern = Pattern.compile(regex);
    return pattern.matcher(input).matches();
  }
}
