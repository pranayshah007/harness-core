/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.services.impl;

import io.harness.exception.DuplicateFieldException;
import io.harness.repositories.CreditCardRepository;
import io.harness.subscription.dto.CreditCardDTO;
import io.harness.subscription.entities.CreditCard;
import io.harness.subscription.helpers.StripeHelper;
import io.harness.subscription.responses.CreditCardResponse;
import io.harness.subscription.services.CreditCardService;

import com.google.inject.Inject;
import javax.ws.rs.BadRequestException;

public class CreditCardServiceImpl implements CreditCardService {
  private final CreditCardRepository creditCardRepository;
  private final StripeHelper stripeHelper;

  @Inject
  public CreditCardServiceImpl(CreditCardRepository creditCardRepository, StripeHelper stripeHelper) {
    this.creditCardRepository = creditCardRepository;
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
}
