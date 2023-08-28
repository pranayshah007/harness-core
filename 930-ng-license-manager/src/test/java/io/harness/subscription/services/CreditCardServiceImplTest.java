/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.services;

import static io.harness.rule.OwnerRule.TOMMY;
import static io.harness.subscription.constant.CreditCardTestConstants.ALTERNATE_ACCOUNT_ID;
import static io.harness.subscription.constant.CreditCardTestConstants.ALTERNATE_CREDIT_CARD_DTO;
import static io.harness.subscription.constant.CreditCardTestConstants.ALTERNATE_CUSTOMER;
import static io.harness.subscription.constant.CreditCardTestConstants.ALTERNATE_CUSTOMER_ID;
import static io.harness.subscription.constant.CreditCardTestConstants.DEFAULT_CREDIT_CARD;
import static io.harness.subscription.constant.CreditCardTestConstants.DEFAULT_CREDIT_CARD_DTO;
import static io.harness.subscription.constant.CreditCardTestConstants.DEFAULT_CREDIT_CARD_IDENTIFIER;
import static io.harness.subscription.constant.CreditCardTestConstants.DEFAULT_CUSTOMER;
import static io.harness.subscription.constant.CreditCardTestConstants.DEFAULT_CUSTOMER_ID;
import static io.harness.subscription.constant.CreditCardTestConstants.DEFAULT_FINGERPRINT;
import static io.harness.subscription.constant.CreditCardTestConstants.DEFAULT_PAYMENT_METHODS;
import static io.harness.subscription.constant.CreditCardTestConstants.DEFAULT_SOURCE_PRINCIPLE_DATA;
import static io.harness.subscription.constant.CreditCardTestConstants.EXPIRED_PAYMENT_METHODS;
import static io.harness.subscription.constant.SubscriptionTestConstant.DEFAULT_ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.account.AccountClient;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.UnauthorizedException;
import io.harness.manage.GlobalContextManager;
import io.harness.remote.client.CGRestUtils;
import io.harness.repositories.CreditCardRepository;
import io.harness.repositories.StripeCustomerRepository;
import io.harness.rule.Owner;
import io.harness.subscription.dto.CardDTO;
import io.harness.subscription.helpers.impl.StripeHelperImpl;
import io.harness.subscription.responses.CreditCardResponse;
import io.harness.subscription.services.impl.CreditCardServiceImpl;

import javax.ws.rs.BadRequestException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;

public class CreditCardServiceImplTest extends CategoryTest {
  @Mock private AccountClient accountClient;
  @Mock private CreditCardRepository creditCardRepository;
  @Mock private StripeCustomerRepository stripeCustomerRepository;
  @Mock private static StripeHelperImpl stripeHelper;

  private static CreditCardServiceImpl creditCardService;

  @Before
  public void setUp() {
    initMocks(this);
    creditCardService =
        new CreditCardServiceImpl(accountClient, creditCardRepository, stripeCustomerRepository, stripeHelper);
  }

  @Test
  @Owner(developers = TOMMY)
  @Category(UnitTests.class)
  public void testSaveCreditCard() {
    when(stripeCustomerRepository.findByAccountIdentifier(DEFAULT_ACCOUNT_ID)).thenReturn(DEFAULT_CUSTOMER);
    when(stripeHelper.listPaymentMethods(DEFAULT_CUSTOMER_ID)).thenReturn(DEFAULT_PAYMENT_METHODS);
    when(creditCardRepository.findByFingerprint(DEFAULT_FINGERPRINT)).thenReturn(null);
    when(creditCardRepository.save(DEFAULT_CREDIT_CARD)).thenReturn(DEFAULT_CREDIT_CARD);

    CreditCardResponse response = creditCardService.saveCreditCard(DEFAULT_CREDIT_CARD_DTO);
    assertThat(response).isNotNull();
    assertThat(response.getCreditCardDTO().getAccountIdentifier()).isEqualTo(DEFAULT_ACCOUNT_ID);
    assertThat(response.getCreditCardDTO().getCreditCardIdentifier()).isEqualTo(DEFAULT_CREDIT_CARD_IDENTIFIER);
  }

  @Test
  @Owner(developers = TOMMY)
  @Category(UnitTests.class)
  public void testSaveCreditCardDuplicate() {
    when(stripeCustomerRepository.findByAccountIdentifier(DEFAULT_ACCOUNT_ID)).thenReturn(DEFAULT_CUSTOMER);
    when(stripeHelper.listPaymentMethods(DEFAULT_CUSTOMER_ID)).thenReturn(DEFAULT_PAYMENT_METHODS);
    when(creditCardRepository.findByFingerprint(DEFAULT_FINGERPRINT)).thenReturn(DEFAULT_CREDIT_CARD);
    when(creditCardRepository.save(DEFAULT_CREDIT_CARD)).thenReturn(DEFAULT_CREDIT_CARD);

    CreditCardResponse response = creditCardService.saveCreditCard(DEFAULT_CREDIT_CARD_DTO);
    assertThat(response).isNotNull();
  }

  @Test(expected = BadRequestException.class)
  @Owner(developers = TOMMY)
  @Category(UnitTests.class)
  public void testSaveCreditCardBadRequest() {
    when(stripeCustomerRepository.findByAccountIdentifier(ALTERNATE_ACCOUNT_ID)).thenReturn(ALTERNATE_CUSTOMER);
    when(stripeHelper.listPaymentMethods(ALTERNATE_CUSTOMER_ID)).thenReturn(DEFAULT_PAYMENT_METHODS);
    when(creditCardRepository.findByFingerprint(DEFAULT_FINGERPRINT)).thenReturn(DEFAULT_CREDIT_CARD);

    CreditCardResponse response = creditCardService.saveCreditCard(ALTERNATE_CREDIT_CARD_DTO);
    assertThat(response).isNotNull();
  }

  @Test
  @Owner(developers = TOMMY)
  @Category(UnitTests.class)
  public void testDeleteCreditCard() {
    when(creditCardRepository.findByCreditCardIdentifier(DEFAULT_CREDIT_CARD_IDENTIFIER))
        .thenReturn(DEFAULT_CREDIT_CARD);

    MockedStatic<GlobalContextManager> globalContextManager = mockStatic(GlobalContextManager.class);
    globalContextManager.when(() -> GlobalContextManager.get(any())).thenReturn(DEFAULT_SOURCE_PRINCIPLE_DATA);

    MockedStatic<CGRestUtils> mockRestUtils = mockStatic(CGRestUtils.class);
    mockRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(true);

    CreditCardResponse response =
        creditCardService.deleteCreditCard(DEFAULT_ACCOUNT_ID, DEFAULT_CREDIT_CARD_IDENTIFIER);
    assertThat(response).isNotNull();
    assertThat(response.getCreditCardDTO().getAccountIdentifier()).isEqualTo(DEFAULT_ACCOUNT_ID);
    assertThat(response.getCreditCardDTO().getCreditCardIdentifier()).isEqualTo(DEFAULT_CREDIT_CARD_IDENTIFIER);
  }

  @Test(expected = UnauthorizedException.class)
  @Owner(developers = TOMMY)
  @Category(UnitTests.class)
  public void testDeleteCreditCardExpectsUnauthorizedException() {
    when(creditCardRepository.findByCreditCardIdentifier(DEFAULT_CREDIT_CARD_IDENTIFIER))
        .thenReturn(DEFAULT_CREDIT_CARD);

    MockedStatic<GlobalContextManager> globalContextManager = mockStatic(GlobalContextManager.class);
    globalContextManager.when(() -> GlobalContextManager.get(any())).thenReturn(DEFAULT_SOURCE_PRINCIPLE_DATA);

    MockedStatic<CGRestUtils> mockRestUtils = mockStatic(CGRestUtils.class);
    mockRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(false);

    creditCardService.deleteCreditCard(DEFAULT_ACCOUNT_ID, DEFAULT_CREDIT_CARD_IDENTIFIER);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = TOMMY)
  @Category(UnitTests.class)
  public void testDeleteCardExpectsInvalidArgumentsException() {
    when(stripeCustomerRepository.findByAccountIdentifier(DEFAULT_ACCOUNT_ID)).thenReturn(null);

    MockedStatic<GlobalContextManager> globalContextManager = mockStatic(GlobalContextManager.class);
    globalContextManager.when(() -> GlobalContextManager.get(any())).thenReturn(DEFAULT_SOURCE_PRINCIPLE_DATA);

    MockedStatic<CGRestUtils> mockRestUtils = mockStatic(CGRestUtils.class);
    mockRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(true);

    creditCardService.deleteCreditCard(DEFAULT_ACCOUNT_ID, DEFAULT_CREDIT_CARD_IDENTIFIER);
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

  @Test
  @Owner(developers = TOMMY)
  @Category(UnitTests.class)
  public void testGetDefaultCard() {
    when(stripeCustomerRepository.findByAccountIdentifier(DEFAULT_ACCOUNT_ID)).thenReturn(DEFAULT_CUSTOMER);
    when(stripeHelper.listPaymentMethods(DEFAULT_CUSTOMER_ID)).thenReturn(DEFAULT_PAYMENT_METHODS);

    CardDTO cardDTO = creditCardService.getDefaultCreditCard(DEFAULT_ACCOUNT_ID);

    assertThat(cardDTO.getIsDefaultCard()).isTrue();
  }
}