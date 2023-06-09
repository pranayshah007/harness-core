/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.services;

import static io.harness.rule.OwnerRule.TOMMY;
import static io.harness.subscription.constant.CreditCardTestConstants.ALTERNATE_CREDIT_CARD_DTO;
import static io.harness.subscription.constant.CreditCardTestConstants.DEFAULT_CREDIT_CARD;
import static io.harness.subscription.constant.CreditCardTestConstants.DEFAULT_CREDIT_CARD_DTO;
import static io.harness.subscription.constant.CreditCardTestConstants.DEFAULT_CUSTOMER;
import static io.harness.subscription.constant.CreditCardTestConstants.DEFAULT_CUSTOMER_ID;
import static io.harness.subscription.constant.CreditCardTestConstants.DEFAULT_FINGERPRINT;
import static io.harness.subscription.constant.CreditCardTestConstants.DEFAULT_PAYMENT_METHODS;
import static io.harness.subscription.constant.CreditCardTestConstants.EXPIRED_PAYMENT_METHODS;
import static io.harness.subscription.constant.SubscriptionTestConstant.DEFAULT_ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.DuplicateFieldException;
import io.harness.repositories.CreditCardRepository;
import io.harness.repositories.StripeCustomerRepository;
import io.harness.rule.Owner;
import io.harness.subscription.helpers.impl.StripeHelperImpl;
import io.harness.subscription.responses.CreditCardResponse;
import io.harness.subscription.services.impl.CreditCardServiceImpl;

import javax.ws.rs.BadRequestException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class CreditCardServiceImplTest extends CategoryTest {
  @Mock private CreditCardRepository creditCardRepository;
  @Mock private StripeCustomerRepository stripeCustomerRepository;
  @Mock private static StripeHelperImpl stripeHelper;

  private static CreditCardServiceImpl creditCardService;

  @Before
  public void setUp() {
    initMocks(this);

    creditCardService = new CreditCardServiceImpl(creditCardRepository, stripeCustomerRepository, stripeHelper);
  }

  @Test
  @Owner(developers = TOMMY)
  @Category(UnitTests.class)
  public void testSaveCreditCard() {
    when(creditCardRepository.findByFingerprint(DEFAULT_FINGERPRINT)).thenReturn(null);
    when(creditCardRepository.save(DEFAULT_CREDIT_CARD)).thenReturn(DEFAULT_CREDIT_CARD);

    CreditCardResponse response = creditCardService.saveCreditCard(DEFAULT_CREDIT_CARD_DTO);
    assertThat(response).isNotNull();
    assertThat(response.getCreditCardDTO().getAccountIdentifier()).isEqualTo(DEFAULT_ACCOUNT_ID);
    assertThat(response.getCreditCardDTO().getFingerprint()).isEqualTo(DEFAULT_FINGERPRINT);
  }

  @Test(expected = DuplicateFieldException.class)
  @Owner(developers = TOMMY)
  @Category(UnitTests.class)
  public void testSaveCreditCardDuplicate() {
    when(creditCardRepository.findByFingerprint(DEFAULT_FINGERPRINT)).thenReturn(DEFAULT_CREDIT_CARD);

    CreditCardResponse response = creditCardService.saveCreditCard(DEFAULT_CREDIT_CARD_DTO);
    assertThat(response).isNotNull();
  }

  @Test(expected = BadRequestException.class)
  @Owner(developers = TOMMY)
  @Category(UnitTests.class)
  public void testSaveCreditCardBadRequest() {
    when(creditCardRepository.findByFingerprint(DEFAULT_FINGERPRINT)).thenReturn(DEFAULT_CREDIT_CARD);

    CreditCardResponse response = creditCardService.saveCreditCard(ALTERNATE_CREDIT_CARD_DTO);
    assertThat(response).isNotNull();
  }

  @Test
  @Owner(developers = TOMMY)
  @Category(UnitTests.class)
  public void testHasValidCard() {
    when(stripeCustomerRepository.findByAccountIdentifier(DEFAULT_ACCOUNT_ID)).thenReturn(DEFAULT_CUSTOMER);
    when(stripeHelper.listPaymentMethods(DEFAULT_CUSTOMER_ID)).thenReturn(DEFAULT_PAYMENT_METHODS);

    creditCardService.hasValidCard(DEFAULT_ACCOUNT_ID);
    assertThat(creditCardService.hasValidCard(DEFAULT_ACCOUNT_ID)).isTrue();
  }

  @Test
  @Owner(developers = TOMMY)
  @Category(UnitTests.class)
  public void testHasValidCardFailure() {
    when(stripeCustomerRepository.findByAccountIdentifier(DEFAULT_ACCOUNT_ID)).thenReturn(DEFAULT_CUSTOMER);
    when(stripeHelper.listPaymentMethods(DEFAULT_CUSTOMER_ID)).thenReturn(EXPIRED_PAYMENT_METHODS);

    creditCardService.hasValidCard(DEFAULT_ACCOUNT_ID);
    assertThat(creditCardService.hasValidCard(DEFAULT_ACCOUNT_ID)).isFalse();
  }
}