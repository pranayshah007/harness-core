/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.services;

import io.harness.ModuleType;
import io.harness.subscription.dto.CustomerDTO;
import io.harness.subscription.dto.CustomerDetailDTO;
import io.harness.subscription.dto.InvoiceDetailDTO;
import io.harness.subscription.dto.PaymentMethodCollectionDTO;
import io.harness.subscription.dto.PriceCollectionDTO;
import io.harness.subscription.dto.StripeBillingDTO;
import io.harness.subscription.dto.SubscriptionDTO;
import io.harness.subscription.dto.SubscriptionDetailDTO;
import io.harness.subscription.params.RecommendationRequest;
import io.harness.subscription.params.SubscriptionRequest;
import io.harness.subscription.params.UsageKey;

import java.util.EnumMap;
import java.util.List;

public interface SubscriptionService {
  EnumMap<UsageKey, Long> getRecommendation(String accountIdentifier, long numberOfMAUs, long numberOfUsers);
  EnumMap<UsageKey, Long> getRecommendationRc(String accountIdentifier, RecommendationRequest recommendationRequest);
  PriceCollectionDTO listPrices(String accountIdentifier, ModuleType moduleType);
  InvoiceDetailDTO previewInvoice(String accountIdentifier, SubscriptionDTO subscriptionDTO);
  void payInvoice(String invoiceId, String accountIdentifier);

  SubscriptionDetailDTO createSubscription(String accountIdentifier, SubscriptionRequest subscriptionRequest);
  SubscriptionDetailDTO updateSubscription(
      String accountIdentifier, String subscriptionId, SubscriptionDTO subscriptionDTO);
  void cancelSubscription(String accountIdentifier, String subscriptionId, ModuleType moduleType);
  void cancelAllSubscriptions(String accountIdentifier);
  SubscriptionDetailDTO getSubscription(String accountIdentifier, String subscriptionId);
  boolean checkSubscriptionExists(String subscriptionId);
  List<SubscriptionDetailDTO> listSubscriptions(String accountIdentifier);

  CustomerDetailDTO createStripeCustomer(String accountIdentifier, CustomerDTO customerDTO);
  CustomerDetailDTO updateStripeCustomer(String accountIdentifier, String customerId, CustomerDTO customerDTO);
  CustomerDetailDTO getStripeCustomer(String accountIdentifier);
  CustomerDetailDTO updateStripeBilling(String accountIdentifier, StripeBillingDTO stripeBillingDTO);

  PaymentMethodCollectionDTO listPaymentMethods(String accountIdentifier);

  void syncStripeEvent(String event);
}
