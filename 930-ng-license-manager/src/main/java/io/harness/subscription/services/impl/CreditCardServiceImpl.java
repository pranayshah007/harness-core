/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.services.impl;

import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.repositories.CreditCardRepository;
import io.harness.repositories.StripeCustomerRepository;
import io.harness.subscription.dto.CardDTO;
import io.harness.subscription.dto.CreditCardDTO;
import io.harness.subscription.entities.CreditCard;
import io.harness.subscription.entities.StripeCustomer;
import io.harness.subscription.helpers.StripeHelper;
import io.harness.subscription.responses.CreditCardResponse;
import io.harness.subscription.services.CreditCardService;

import com.google.inject.Inject;
import java.time.LocalDate;
import java.util.List;
import javax.ws.rs.BadRequestException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CreditCardServiceImpl implements CreditCardService {
  private final CreditCardRepository creditCardRepository;
  private final StripeCustomerRepository stripeCustomerRepository;
  private final StripeHelper stripeHelper;

  @Inject
  public CreditCardServiceImpl(CreditCardRepository creditCardRepository,
      StripeCustomerRepository stripeCustomerRepository, StripeHelper stripeHelper) {
    this.creditCardRepository = creditCardRepository;
    this.stripeCustomerRepository = stripeCustomerRepository;
    this.stripeHelper = stripeHelper;
  }

  @Override
  public CreditCardResponse saveCreditCard(CreditCardDTO creditCardDTO) {
    CreditCard creditCard = creditCardRepository.findByFingerprint(creditCardDTO.getFingerprint());

    if (creditCard != null) {
      if (creditCard.getAccountIdentifier().equals(creditCardDTO.getAccountIdentifier())) {
        throw new DuplicateFieldException("Credit card already exists.");
      } else {
        stripeHelper.deleteCard(creditCardDTO.getCustomerIdentifier(), creditCardDTO.getCreditCardIdentifier());
        throw new BadRequestException("Could not save credit card.");
      }
    }

    return toCreditCardResponse(creditCardRepository.save(CreditCard.builder()
                                                              .accountIdentifier(creditCardDTO.getAccountIdentifier())
                                                              .fingerprint(creditCardDTO.getFingerprint())
                                                              .build()));
  }

  @Override
  public boolean hasValidCard(String accountIdentifier) {
    StripeCustomer stripeCustomer = stripeCustomerRepository.findByAccountIdentifier(accountIdentifier);
    if (stripeCustomer == null) {
      log.warn("Customer with account identifier {} does not exist.", accountIdentifier);
      throw new InvalidRequestException("Customer doesn't exists");
    }
    List<CardDTO> creditCards = stripeHelper.listPaymentMethods(stripeCustomer.getCustomerId()).getPaymentMethods();

    return !creditCards.isEmpty() && hasAtLeastOneUnexpiredCard(creditCards);
  }

  private CreditCardResponse toCreditCardResponse(CreditCard creditCard) {
    return CreditCardResponse.builder()
        .creditCardDTO(CreditCardDTO.builder()
                           .accountIdentifier(creditCard.getAccountIdentifier())
                           .fingerprint(creditCard.getFingerprint())
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
