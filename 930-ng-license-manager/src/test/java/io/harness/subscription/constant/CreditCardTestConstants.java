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
  public static final String DEFAULT_CUSTOMER_ID = "TEST_FINGERPRINT";

  public static final CreditCard DEFAULT_CREDIT_CARD =
      CreditCard.builder().fingerprint(DEFAULT_FINGERPRINT).accountIdentifier(DEFAULT_ACCOUNT_ID).build();

  public static final CreditCardDTO DEFAULT_CREDIT_CARD_DTO =
      CreditCardDTO.builder().fingerprint(DEFAULT_FINGERPRINT).accountIdentifier(DEFAULT_ACCOUNT_ID).build();

  public static final CreditCardDTO ALTERNATE_CREDIT_CARD_DTO =
      CreditCardDTO.builder().fingerprint(DEFAULT_FINGERPRINT).accountIdentifier(ALTERNATE_ACCOUNT_ID).build();

  public static final StripeCustomer DEFAULT_CUSTOMER =
      StripeCustomer.builder().accountIdentifier(DEFAULT_ACCOUNT_ID).customerId(DEFAULT_CUSTOMER_ID).build();

  public static final CardDTO DEFAULT_CARD_DTO = CardDTO.builder().expireMonth(1L).expireYear(3000L).build();
  public static final CardDTO EXPIRED_CARD_DTO = CardDTO.builder().expireMonth(1L).expireYear(2000L).build();

  public static final PaymentMethodCollectionDTO DEFAULT_PAYMENT_METHODS =
      PaymentMethodCollectionDTO.builder().paymentMethods(Arrays.asList(DEFAULT_CARD_DTO, EXPIRED_CARD_DTO)).build();
  public static final PaymentMethodCollectionDTO EXPIRED_PAYMENT_METHODS =
      PaymentMethodCollectionDTO.builder().paymentMethods(Arrays.asList(EXPIRED_CARD_DTO)).build();
}
