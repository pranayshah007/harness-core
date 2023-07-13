/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.services.impl;

import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.repositories.CreditCardRepository;
import io.harness.repositories.StripeCustomerRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.BadRequestException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CreditCardServiceImpl implements CreditCardService {
  private final CreditCardRepository creditCardRepository;
  private final StripeCustomerRepository stripeCustomerRepository;
  private final StripeHelper stripeHelper;

  private final String SAVE_CARD_FAILED = "Could not save credit card.";
  private final String CUSTOMER_DOES_NOT_EXIST = "Customer with account identifier %s does not exist.";
  private final String PRIMARY_CARD_NOT_FOUND = "No primary credit card found for account %s";
  private final String NO_CARDS_FOUND = "No credit cards found for account %s";
  private final String CARD_NOT_FOUND = "No card found with identifier %s";

  @Inject
  public CreditCardServiceImpl(CreditCardRepository creditCardRepository,
      StripeCustomerRepository stripeCustomerRepository, StripeHelper stripeHelper) {
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

    if (newDefaultPaymentMethod.isEmpty()) {
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
  public boolean hasValidCard(String accountIdentifier) {
    StripeCustomer stripeCustomer = stripeCustomerRepository.findByAccountIdentifier(accountIdentifier);
    if (stripeCustomer == null) {
      String errorMessage = String.format(CUSTOMER_DOES_NOT_EXIST, accountIdentifier);
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }
    List<CardDTO> creditCards = stripeHelper.listPaymentMethods(stripeCustomer.getCustomerId()).getPaymentMethods();

    return !creditCards.isEmpty() && hasAtLeastOneUnexpiredCard(creditCards);
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

  private boolean hasAtLeastOneUnexpiredCard(List<CardDTO> creditCards) {
    return creditCards.stream().anyMatch((CardDTO cardDTO)
                                             -> cardDTO.getExpireYear() > LocalDate.now().getYear()
            || (cardDTO.getExpireYear() == LocalDate.now().getYear()
                && cardDTO.getExpireMonth() >= LocalDate.now().getMonth().getValue()));
  }
}
