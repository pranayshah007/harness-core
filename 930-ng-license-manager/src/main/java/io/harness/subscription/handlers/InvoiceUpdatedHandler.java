/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.handlers;

import io.harness.subscription.helpers.StripeHelper;
import io.harness.telemetry.Destination;
import io.harness.telemetry.TelemetryReporter;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.InvoiceLineItem;
import java.util.HashMap;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class InvoiceUpdatedHandler implements StripeEventHandler {
  private final StripeHelper stripeHelper;
  private final TelemetryReporter telemetryReporter;

  private static final String ACCOUNT_IDENTIFIER_KEY = "accountIdentifier";
  private static final String AVATAX_KEY = "Is_AvaTax_Line";
  private static final String INVOICE_DRAFT_STATUS = "draft";
  private static final String SUBSCRIPTION_TELEMETRY_CATEGORY = "subscription";
  private static final String SUBSCRIPTION_TELEMETRY_EVENT_INITIATED = "Subscription Invoice Finalize Initiated";
  private static final String SUBSCRIPTION_TELEMETRY_EVENT_SUCCESS = "Subscription Invoice Finalize Succeeded";

  @Inject
  public InvoiceUpdatedHandler(StripeHelper stripeHelper, TelemetryReporter telemetryReporter) {
    this.stripeHelper = stripeHelper;
    this.telemetryReporter = telemetryReporter;
  }

  @Override
  public void handleEvent(Event event) {
    Invoice invoice = StripeEventUtils.convertEvent(event, Invoice.class);
    if (invoice.getStatus().equals(INVOICE_DRAFT_STATUS)
        && invoice.getLines().getData().stream().anyMatch(
            n -> Boolean.TRUE.toString().equals(n.getMetadata().get(AVATAX_KEY)))) {
      finalizeInvoice(invoice);
    }
  }

  private void finalizeInvoice(Invoice invoice) {
    String accountIdentifier = getAccountIdentifier(invoice);

    HashMap<String, Object> properties = new HashMap<>();
    telemetryReporter.sendIdentifyEvent(invoice.getCustomerEmail(), properties,
        ImmutableMap.<Destination, Boolean>builder().put(Destination.AMPLITUDE, true).build());
    telemetryReporter.sendTrackEvent(SUBSCRIPTION_TELEMETRY_EVENT_INITIATED, invoice.getCustomerEmail(),
        accountIdentifier, properties,
        ImmutableMap.<Destination, Boolean>builder().put(Destination.AMPLITUDE, true).build(),
        SUBSCRIPTION_TELEMETRY_CATEGORY);

    if (invoice.getPaymentIntent() == null) {
      stripeHelper.finalizeInvoice(invoice.getId());
    }

    telemetryReporter.sendTrackEvent(SUBSCRIPTION_TELEMETRY_EVENT_SUCCESS, invoice.getCustomerEmail(),
        accountIdentifier, properties,
        ImmutableMap.<Destination, Boolean>builder().put(Destination.AMPLITUDE, true).build(),
        SUBSCRIPTION_TELEMETRY_CATEGORY);
  }

  private String getAccountIdentifier(Invoice invoice) {
    Optional<InvoiceLineItem> lineItem =
        invoice.getLines()
            .getData()
            .stream()
            .filter(invoiceLineItem -> invoiceLineItem.getMetadata().get(ACCOUNT_IDENTIFIER_KEY) != null)
            .findFirst();

    return lineItem.isPresent() ? lineItem.get().getMetadata().get(ACCOUNT_IDENTIFIER_KEY) : null;
  }
}
