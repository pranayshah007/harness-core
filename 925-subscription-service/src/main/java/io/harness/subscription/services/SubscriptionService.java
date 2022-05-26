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
import io.harness.subscription.dto.ListPricesDTO;
import io.harness.subscription.dto.PaymentMethodCollectionDTO;
import io.harness.subscription.dto.PriceCollectionDTO;
import io.harness.subscription.dto.StripeEventDTO;
import io.harness.subscription.dto.SubscriptionDTO;
import io.harness.subscription.dto.SubscriptionDetailDTO;

import java.util.List;

public interface SubscriptionService {
  PriceCollectionDTO listPrices(String accountIdentifier, ListPricesDTO lookupPrices);
  InvoiceDetailDTO previewInvoice(String accountIdentifier, SubscriptionDTO subscriptionDTO);
  SubscriptionDetailDTO createSubscription(String accountIdentifier, SubscriptionDTO subscriptionDTO);
  SubscriptionDetailDTO updateSubscription(
      String accountIdentifier, String subscriptionId, SubscriptionDTO subscriptionDTO);
  void cancelSubscription(String accountIdentifier, String subscriptionId);
  SubscriptionDetailDTO getSubscription(String accountIdentifier, String subscriptionId);
  boolean checkSubscriptionExists(String subscriptionId);
  List<SubscriptionDetailDTO> listSubscriptions(String accountIdentifier, ModuleType moduleType);

  CustomerDetailDTO createStripeCustomer(String accountIdentifier, CustomerDTO customerDTO);
  CustomerDetailDTO updateStripeCustomer(String accountIdentifier, String customerId, CustomerDTO customerDTO);
  CustomerDetailDTO getStripeCustomer(String accountIdentifier, String customerId);
  List<CustomerDetailDTO> listStripeCustomers(String accountIdentifier);

  PaymentMethodCollectionDTO listPaymentMethods(String accountIdentifier, String customerId);

  void syncStripeEvent(StripeEventDTO stripeEventDTO);
}
