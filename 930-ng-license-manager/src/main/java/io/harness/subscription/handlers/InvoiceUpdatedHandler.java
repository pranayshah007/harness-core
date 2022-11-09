/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.handlers;

import io.harness.subscription.helpers.StripeHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class InvoiceUpdatedHandler implements StripeEventHandler {
  private final StripeHelper stripeHelper;

  private static final String AVATAX_KEY = "Is_AvaTax_Line";

  @Inject
  public InvoiceUpdatedHandler(StripeHelper stripeHelper) {
    this.stripeHelper = stripeHelper;
  }

  @Override
  public void handleEvent(Event event) {
    Invoice invoice = StripeEventUtils.convertEvent(event, Invoice.class);
    if (invoice.getLines().getData().stream().anyMatch(n -> "true".equals(n.getMetadata().get(AVATAX_KEY)))) {
      finalizeInvoice(invoice);
    }
  }

  private void finalizeInvoice(Invoice invoice) {
    if (invoice.getPaymentIntent() == null) {
      stripeHelper.finalizeInvoice(invoice.getId());
    }
  }
}
