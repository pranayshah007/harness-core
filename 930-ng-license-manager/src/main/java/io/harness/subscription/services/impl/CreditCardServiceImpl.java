/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.services.impl;

import io.harness.account.AccountClient;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.WingsException;
import io.harness.manage.GlobalContextManager;
import io.harness.remote.client.CGRestUtils;
import io.harness.repositories.CreditCardRepository;
import io.harness.repositories.StripeCustomerRepository;
import io.harness.security.SourcePrincipalContextData;
import io.harness.subscription.dto.CardDTO;
import io.harness.subscription.dto.CreditCardDTO;
import io.harness.subscription.dto.PaymentMethodCollectionDTO;
import io.harness.subscription.entities.CreditCard;
import io.harness.subscription.entities.StripeCustomer;
import io.harness.subscription.helpers.StripeHelper;
import io.harness.subscription.params.CustomerParams;
import io.harness.subscription.responses.CreditCardResponse;
import io.harness.subscription.services.CreditCardService;

import com.google.inject.Inject;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.BadRequestException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CreditCardServiceImpl implements CreditCardService {
  private final AccountClient accountClient;
  private final CreditCardRepository creditCardRepository;
  private final StripeCustomerRepository stripeCustomerRepository;
  private final StripeHelper stripeHelper;

  private final String SAVE_CARD_FAILED = "Could not save credit card.";
  private final String CUSTOMER_DOES_NOT_EXIST = "Customer with account identifier %s does not exist.";
  private final String PRIMARY_CARD_NOT_FOUND = "No primary credit card found for account %s";
  private final String NO_CARDS_FOUND = "No credit cards found for account %s";
  private final String CARD_NOT_FOUND = "No card found with identifier %s";

  @Inject
  public CreditCardServiceImpl(AccountClient accountClient, CreditCardRepository creditCardRepository,
      StripeCustomerRepository stripeCustomerRepository, StripeHelper stripeHelper) {
    this.accountClient = accountClient;
    this.creditCardRepository = creditCardRepository;
    this.stripeCustomerRepository = stripeCustomerRepository;
    this.stripeHelper = stripeHelper;
  }

  @Override
  public CreditCardResponse saveCreditCard(CreditCardDTO creditCardRequest) {
    StripeCustomer stripeCustomer =
        stripeCustomerRepository.findByAccountIdentifier(creditCardRequest.getAccountIdentifier());

    PaymentMethodCollectionDTO paymentMethodCollectionDTO =
        stripeHelper.listPaymentMethods(stripeCustomer.getCustomerId());

    if (paymentMethodCollectionDTO.getPaymentMethods().isEmpty()) {
      String errorMessage = String.format(NO_CARDS_FOUND, creditCardRequest.getCreditCardIdentifier());
      log.error(errorMessage);
      throw new InvalidArgumentsException(errorMessage);
    }

    Optional<CardDTO> newDefaultPaymentMethod =
        paymentMethodCollectionDTO.getPaymentMethods()
            .stream()
            .filter((CardDTO cardDTO) -> cardDTO.getId().equals(creditCardRequest.getCreditCardIdentifier()))
            .findFirst();

    if (newDefaultPaymentMethod.isEmpty() || isCreditCardExpired(newDefaultPaymentMethod.get())) {
      log.error(SAVE_CARD_FAILED);
      throw new InvalidArgumentsException(SAVE_CARD_FAILED);
    }

    CreditCard creditCard = creditCardRepository.findByFingerprint(newDefaultPaymentMethod.get().getFingerPrint());
    if (creditCard != null) {
      validateCreditCard(creditCard, creditCardRequest.getCreditCardIdentifier(),
          creditCardRequest.getAccountIdentifier(), stripeCustomer.getCustomerId());
    }
    stripeHelper.updateCustomer(CustomerParams.builder()
                                    .customerId(stripeCustomer.getCustomerId())
                                    .defaultPaymentMethod(newDefaultPaymentMethod.get().getId())
                                    .build());

    paymentMethodCollectionDTO.getPaymentMethods()
        .stream()
        .filter((CardDTO stripePaymentMethod)
                    -> !stripePaymentMethod.getId().equals(creditCardRequest.getCreditCardIdentifier()))
        .forEach((CardDTO stripePaymentMethod)
                     -> stripeHelper.detachPaymentMethod(stripeCustomer.getCustomerId(), stripePaymentMethod.getId()));

    return toCreditCardResponse(
        creditCardRepository.save(CreditCard.builder()
                                      .accountIdentifier(creditCardRequest.getAccountIdentifier())
                                      .creditCardIdentifier(newDefaultPaymentMethod.get().getId())
                                      .fingerprint(newDefaultPaymentMethod.get().getFingerPrint())
                                      .build()));
  }

  @Override
  public CreditCardResponse deleteCreditCard(String accountIdentifier, String creditCardIdentifier) {
    SourcePrincipalContextData sourcePrincipalContextData =
        GlobalContextManager.get(SourcePrincipalContextData.SOURCE_PRINCIPAL);
    String userId = sourcePrincipalContextData.getPrincipal().getName();
    boolean isHarnessSupportEnabled = false;
    try {
      isHarnessSupportEnabled = CGRestUtils.getResponse(accountClient.isHarnessSupportUserId(userId));
    } catch (Exception e) {
      log.error("client call to cg-manager failed due to: ", e);
      throw e;
    }
    if (!isHarnessSupportEnabled) {
      String errorMessage = String.format(
          "User with ID %s is not authorized to delete credit cards. Only Harness support users can delete credit cards.",
          userId);
      log.error(errorMessage);
      throw new UnauthorizedException(errorMessage, WingsException.USER);
    }
    CreditCard creditCard = creditCardRepository.findByCreditCardIdentifier(creditCardIdentifier);
    if (creditCard == null) {
      String errorMessage = String.format("Could not find a credit card with identifier %s", creditCardIdentifier);
      log.error(errorMessage);
      throw new InvalidArgumentsException(errorMessage);
    }
    creditCardRepository.delete(creditCard);
    return toCreditCardResponse(creditCard);
  }

  @Override
  public boolean hasAtleastOneValidCreditCard(String accountIdentifier) {
    List<CardDTO> creditCards = getCreditCards(accountIdentifier);

    return creditCards.stream().anyMatch(creditCard -> !isCreditCardExpired(creditCard));
  }

  @Override
  public boolean isValid(String accountIdentifier, String creditCardIdentifier) {
    List<CardDTO> creditCards = getCreditCards(accountIdentifier);
    Optional<CardDTO> optionalCreditCard =
        creditCards.stream().filter(creditCard -> creditCard.getId().equals(creditCardIdentifier)).findFirst();

    return optionalCreditCard.isPresent() && !isCreditCardExpired(optionalCreditCard.get());
  }

  @Override
  public CardDTO getDefaultCreditCard(String accountIdentifier) {
    StripeCustomer stripeCustomer = stripeCustomerRepository.findByAccountIdentifier(accountIdentifier);
    if (stripeCustomer == null) {
      String errorMessage = String.format(CUSTOMER_DOES_NOT_EXIST, accountIdentifier);
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    PaymentMethodCollectionDTO paymentMethodCollectionDTO =
        stripeHelper.listPaymentMethods(stripeCustomer.getCustomerId());

    if (paymentMethodCollectionDTO == null) {
      String errorMessage = String.format(PRIMARY_CARD_NOT_FOUND, accountIdentifier);
      log.error(errorMessage);
      throw new InvalidArgumentsException(errorMessage);
    }

    Optional<CardDTO> primaryCardDTO =
        paymentMethodCollectionDTO.getPaymentMethods().stream().filter(CardDTO::getIsDefaultCard).findFirst();
    if (primaryCardDTO.isEmpty()) {
      String errorMessage = String.format(PRIMARY_CARD_NOT_FOUND, accountIdentifier);
      log.error(errorMessage);
      throw new InvalidArgumentsException(errorMessage);
    }

    return primaryCardDTO.get();
  }

  private void validateCreditCard(
      CreditCard creditCard, String paymentMethodIdentifier, String accountIdentifier, String customerIdentifier) {
    if (creditCard == null) {
      String errorMessage = String.format(CARD_NOT_FOUND, paymentMethodIdentifier);
      log.error(errorMessage);
      throw new InvalidArgumentsException(errorMessage);
    }

    if (!creditCard.getAccountIdentifier().equals(accountIdentifier)) {
      stripeHelper.detachPaymentMethod(customerIdentifier, paymentMethodIdentifier);
      log.error(SAVE_CARD_FAILED);
      throw new BadRequestException(SAVE_CARD_FAILED);
    }
  }

  private CreditCardResponse toCreditCardResponse(CreditCard creditCard) {
    return CreditCardResponse.builder()
        .creditCardDTO(CreditCardDTO.builder()
                           .accountIdentifier(creditCard.getAccountIdentifier())
                           .creditCardIdentifier(creditCard.getCreditCardIdentifier())
                           .build())
        .createdAt(creditCard.getCreatedAt())
        .lastUpdatedAt(creditCard.getLastUpdatedAt())
        .build();
  }

  private List<CardDTO> getCreditCards(String accountIdentifier) {
    StripeCustomer stripeCustomer = stripeCustomerRepository.findByAccountIdentifier(accountIdentifier);
    if (stripeCustomer == null) {
      String errorMessage = String.format(CUSTOMER_DOES_NOT_EXIST, accountIdentifier);
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    return stripeHelper.listPaymentMethods(stripeCustomer.getCustomerId()).getPaymentMethods();
  }

  private boolean isCreditCardExpired(CardDTO cardDTO) {
    int currentYear = Calendar.getInstance().get(Calendar.YEAR);
    int currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1;

    return currentYear > cardDTO.getExpireYear()
        || (currentYear == cardDTO.getExpireYear() && currentMonth > cardDTO.getExpireMonth());
  }
}
