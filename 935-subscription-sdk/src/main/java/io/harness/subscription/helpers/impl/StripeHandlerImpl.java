/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.subscription.helpers.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.exception.InvalidRequestException;
import io.harness.telemetry.Destination;
import io.harness.telemetry.TelemetryReporter;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.PaymentMethodCollection;
import com.stripe.model.Price;
import com.stripe.model.PriceCollection;
import com.stripe.model.PriceSearchResult;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionSearchResult;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerRetrieveParams;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.InvoiceUpcomingParams;
import com.stripe.param.PaymentMethodListParams;
import com.stripe.param.PriceListParams;
import com.stripe.param.PriceSearchParams;
import com.stripe.param.SubscriptionCreateParams;
import com.stripe.param.SubscriptionRetrieveParams;
import com.stripe.param.SubscriptionSearchParams;
import com.stripe.param.SubscriptionUpdateParams;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StripeHandlerImpl {
  private final TelemetryReporter telemetryReporter;
  private static final String SUBSCRIPTION = "subscription";

  @Inject
  StripeHandlerImpl(TelemetryReporter telemetryReporter) {
    this.telemetryReporter = telemetryReporter;
  }

  Subscription createSubscription(SubscriptionCreateParams subscriptionCreateParams, String moduleType) {
    try {
      Subscription subscription = Subscription.create(subscriptionCreateParams);
      sendTelemetryEvent("Subscription Creation Succeeded", null, null, moduleType);
      return subscription;
    } catch (StripeException e) {
      sendTelemetryEvent("Subscription Creation Failed", null, null, moduleType);
      throw new InvalidRequestException("Unable to create subscription", e);
    }
  }

  Subscription updateSubscription(
      String subscriptionId, SubscriptionUpdateParams subscriptionUpdateParams, String moduleType) {
    try {
      Subscription subscription = Subscription.retrieve(subscriptionId);

      Subscription updatedSubscription = subscription.update(subscriptionUpdateParams);

      sendTelemetryEvent("Subscription Modification Succeeded", null, null, moduleType);
      return updatedSubscription;
    } catch (StripeException e) {
      sendTelemetryEvent("Subscription Modification Failed", null, null, moduleType);
      throw new InvalidRequestException("Unable to update subscription", e);
    }
  }

  Subscription cancelSubscription(String subscriptionId, String moduleType) {
    try {
      Subscription subscription = Subscription.retrieve(subscriptionId);
      Subscription cancelledSubscription = subscription.cancel();
      sendTelemetryEvent("Subscription Cancellation Succeeded", null, null, moduleType);
      return cancelledSubscription;
    } catch (StripeException e) {
      sendTelemetryEvent("Subscription Cancellation Failed", null, null, moduleType);
      throw new InvalidRequestException("Unable to cancel subscription", e);
    }
  }

  Subscription retrieveSubscription(String subscriptionId) {
    return retrieveSubscription(subscriptionId, null);
  }

  Subscription retrieveSubscription(String subscriptionId, List<String> expandList) {
    try {
      SubscriptionRetrieveParams.Builder builder = SubscriptionRetrieveParams.builder();

      if (isNotEmpty(expandList)) {
        builder.addAllExpand(expandList);
      }
      return Subscription.retrieve(subscriptionId, builder.build(), null);
    } catch (StripeException e) {
      throw new InvalidRequestException("Unable to retrieve subscription", e);
    }
  }

  Customer createCustomer(CustomerCreateParams customerCreateParams) {
    try {
      return Customer.create(customerCreateParams);
    } catch (StripeException e) {
      throw new InvalidRequestException("Unable to create customer information", e);
    }
  }

  PaymentMethod linkPaymentMethodToCustomer(String customerId, String paymentMethodId) {
    try {
      PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);

      Map<String, Object> params = new HashMap<>();
      params.put("customer", customerId);

      return paymentMethod.attach(params);
    } catch (StripeException e) {
      throw new InvalidRequestException("Unable to link customer to payment method", e);
    }
  }

  Customer updateCustomer(String customerId, CustomerUpdateParams customerUpdateParams) {
    try {
      Customer customer = Customer.retrieve(customerId);
      return customer.update(customerUpdateParams);
    } catch (StripeException e) {
      throw new InvalidRequestException("Unable to update customer information", e);
    }
  }

  Customer retrieveCustomer(String customerId, CustomerRetrieveParams customerRetrieveParams) {
    try {
      return Customer.retrieve(customerId, customerRetrieveParams, null);
    } catch (StripeException e) {
      throw new InvalidRequestException("Unable to retrieve customer information", e);
    }
  }

  SubscriptionSearchResult searchSubscriptions(SubscriptionSearchParams subscriptionSearchParams) {
    try {
      return Subscription.search(subscriptionSearchParams);
    } catch (StripeException e) {
      throw new InvalidRequestException("Unable to list subscriptions", e);
    }
  }

  PriceSearchResult searchPrices(PriceSearchParams priceSearchParams) {
    try {
      return Price.search(priceSearchParams);
    } catch (StripeException e) {
      throw new InvalidRequestException("Unable to list prices", e);
    }
  }

  PriceCollection listPrices(PriceListParams priceListParams) {
    try {
      return Price.list(priceListParams);
    } catch (StripeException e) {
      throw new InvalidRequestException("Unable to list prices", e);
    }
  }

  Price retrievePrice(String priceId) {
    try {
      return Price.retrieve(priceId);
    } catch (StripeException e) {
      throw new InvalidRequestException("Unable to retrieve price", e);
    }
  }

  Invoice retrieveUpcomingInvoice(Map<String, Object> params) {
    try {
      return Invoice.upcoming(params);
    } catch (StripeException e) {
      throw new InvalidRequestException("Unable to retrieve invoice", e);
    }
  }

  Invoice retrieveInvoice(String invoiceId) {
    try {
      return Invoice.retrieve(invoiceId);
    } catch (StripeException e) {
      throw new InvalidRequestException("Unable to retrieve invoice", e);
    }
  }

  Invoice previewInvoice(String customerId, String subscriptionId, InvoiceUpcomingParams invoiceUpcomingParams) {
    try {
      return Invoice.upcoming(invoiceUpcomingParams);
    } catch (StripeException e) {
      throw new InvalidRequestException("Unable to preview upcoming invoice", e);
    }
  }

  Invoice payInvoice(String invoiceId) {
    try {
      Invoice invoice = Invoice.retrieve(invoiceId);

      return invoice.pay();
    } catch (StripeException e) {
      throw new InvalidRequestException("Unable to preview upcoming invoice", e);
    }
  }

  PaymentIntent retrievePaymentIntent(String paymentIntentId) {
    try {
      return PaymentIntent.retrieve(paymentIntentId);
    } catch (StripeException e) {
      throw new InvalidRequestException("Unable to retrieve payment intent invoice", e);
    }
  }

  PaymentMethodCollection retrievePaymentMethodsUnderCustomer(String customerId) {
    try {
      PaymentMethodListParams paymentMethodListParams =
          PaymentMethodListParams.builder().setType(PaymentMethodListParams.Type.CARD).setCustomer(customerId).build();

      return PaymentMethod.list(paymentMethodListParams);
    } catch (StripeException e) {
      throw new InvalidRequestException("Unable to retrieve payment methods", e);
    }
  }

  public Invoice finalizeInvoice(String invoiceId) {
    try {
      Invoice invoice = Invoice.retrieve(invoiceId);

      return invoice.finalizeInvoice();
    } catch (StripeException e) {
      throw new InvalidRequestException("Unable to finalize invoice", e);
    }
  }

  private void sendTelemetryEvent(String event, String email, String accountId, String module) {
    HashMap<String, Object> properties = new HashMap<>();
    properties.put("module", module);
    telemetryReporter.sendTrackEvent(event, email, accountId, properties,
        ImmutableMap.<Destination, Boolean>builder().put(Destination.AMPLITUDE, true).build(), SUBSCRIPTION);
  }
}