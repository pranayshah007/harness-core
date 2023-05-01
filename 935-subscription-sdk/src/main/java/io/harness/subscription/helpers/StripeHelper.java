/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.subscription.helpers;

import io.harness.ModuleType;
import io.harness.subscription.dto.CustomerDetailDTO;
import io.harness.subscription.dto.InvoiceDetailDTO;
import io.harness.subscription.dto.PaymentMethodCollectionDTO;
import io.harness.subscription.dto.PriceCollectionDTO;
import io.harness.subscription.dto.SubscriptionDetailDTO;
import io.harness.subscription.params.BillingParams;
import io.harness.subscription.params.CustomerParams;
import io.harness.subscription.params.StripeSubscriptionRequest;
import io.harness.subscription.params.SubscriptionItemRequest;
import io.harness.subscription.params.SubscriptionRequest;

import com.stripe.model.Price;
import java.util.List;
import java.util.Optional;

public interface StripeHelper {
  CustomerDetailDTO createCustomer(CustomerParams customerParams);

  CustomerDetailDTO updateCustomer(CustomerParams customerParams);
  CustomerDetailDTO updateBilling(BillingParams customerParams);

  CustomerDetailDTO getCustomer(String customerId);

  PriceCollectionDTO getPrices(ModuleType moduleType);
  Optional<Price> getPrice(SubscriptionRequest subscriptionRequest, SubscriptionItemRequest subscriptionItemRequest);
  Price getPrice(String lookupKey);
  PriceCollectionDTO listPrices(List<String> lookupKeys);
  SubscriptionDetailDTO createSubscription(StripeSubscriptionRequest stripeSubscriptionRequest);
  SubscriptionDetailDTO addToSubscription(
      StripeSubscriptionRequest subscriptionParams, SubscriptionDetailDTO subscription);
  SubscriptionDetailDTO updateSubscription(StripeSubscriptionRequest stripeSubscriptionRequest);
  SubscriptionDetailDTO updateSubscriptionDefaultPayment(StripeSubscriptionRequest stripeSubscriptionRequest);
  void cancelSubscription(StripeSubscriptionRequest stripeSubscriptionRequest);
  SubscriptionDetailDTO retrieveSubscription(StripeSubscriptionRequest stripeSubscriptionRequest);
  InvoiceDetailDTO getUpcomingInvoice(String invoiceParams);
  InvoiceDetailDTO previewInvoice(StripeSubscriptionRequest stripeSubscriptionRequest);
  void payInvoice(String invoiceId);

  PaymentMethodCollectionDTO listPaymentMethods(String customerId);
  InvoiceDetailDTO finalizeInvoice(String invoiceId);
}