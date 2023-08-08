/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.constant;

import io.harness.subscription.dto.CardDTO;
import io.harness.subscription.dto.CreditCardDTO;
import io.harness.subscription.dto.PaymentMethodCollectionDTO;
import io.harness.subscription.entities.CreditCard;
import io.harness.subscription.entities.StripeCustomer;

import java.util.Arrays;

public class CreditCardTestConstants {
  public static final String DEFAULT_ACCOUNT_ID = "TEST_ACCOUNT_ID";
  public static final String ALTERNATE_ACCOUNT_ID = "ALTERNATE_ACCOUNT_ID";
  public static final String DEFAULT_FINGERPRINT = "TEST_FINGERPRINT";
  public static final String ALTERNATE_FINGERPRINT = "ALTERNATE_FINGERPRINT";
  public static final String DEFAULT_CREDIT_CARD_IDENTIFIER = "TEST_CREDIT_CARD_IDENTIFIER";
  public static final String DEFAULT_CUSTOMER_ID = "TEST_CUSTOMER_ID";
  public static final String ALTERNATE_CUSTOMER_ID = "ALTERNATE_CUSTOMER_ID";

  public static final CreditCard DEFAULT_CREDIT_CARD = CreditCard.builder()
                                                           .fingerprint(DEFAULT_FINGERPRINT)
                                                           .creditCardIdentifier(DEFAULT_CREDIT_CARD_IDENTIFIER)
                                                           .accountIdentifier(DEFAULT_ACCOUNT_ID)
                                                           .build();

  public static final CreditCardDTO DEFAULT_CREDIT_CARD_DTO = CreditCardDTO.builder()
                                                                  .creditCardIdentifier(DEFAULT_CREDIT_CARD_IDENTIFIER)
                                                                  .accountIdentifier(DEFAULT_ACCOUNT_ID)
                                                                  .build();

  public static final CreditCardDTO ALTERNATE_CREDIT_CARD_DTO =
      CreditCardDTO.builder()
          .creditCardIdentifier(DEFAULT_CREDIT_CARD_IDENTIFIER)
          .accountIdentifier(ALTERNATE_ACCOUNT_ID)
          .build();

  public static final StripeCustomer DEFAULT_CUSTOMER =
      StripeCustomer.builder().accountIdentifier(DEFAULT_ACCOUNT_ID).customerId(DEFAULT_CUSTOMER_ID).build();
  public static final StripeCustomer ALTERNATE_CUSTOMER =
      StripeCustomer.builder().accountIdentifier(ALTERNATE_ACCOUNT_ID).customerId(ALTERNATE_CUSTOMER_ID).build();

  public static final CardDTO DEFAULT_CARD_DTO = CardDTO.builder()
                                                     .fingerPrint(DEFAULT_FINGERPRINT)
                                                     .id(DEFAULT_CREDIT_CARD_IDENTIFIER)
                                                     .expireMonth(1L)
                                                     .expireYear(3000L)
                                                     .isDefaultCard(true)
                                                     .build();
  public static final CardDTO EXPIRED_CARD_DTO = CardDTO.builder()
                                                     .fingerPrint(ALTERNATE_FINGERPRINT)
                                                     .id(DEFAULT_CREDIT_CARD_IDENTIFIER)
                                                     .expireMonth(1L)
                                                     .expireYear(2000L)
                                                     .isDefaultCard(false)
                                                     .build();

  public static final PaymentMethodCollectionDTO DEFAULT_PAYMENT_METHODS =
      PaymentMethodCollectionDTO.builder().paymentMethods(Arrays.asList(DEFAULT_CARD_DTO, EXPIRED_CARD_DTO)).build();
  public static final PaymentMethodCollectionDTO EXPIRED_PAYMENT_METHODS =
      PaymentMethodCollectionDTO.builder().paymentMethods(Arrays.asList(EXPIRED_CARD_DTO)).build();
}
