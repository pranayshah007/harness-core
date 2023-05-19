/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.handlers;

import io.harness.repositories.SubscriptionDetailRepository;
import io.harness.telemetry.Destination;
import io.harness.telemetry.TelemetryReporter;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class SubscriptionDeleteHandler implements StripeEventHandler {
  private final SubscriptionDetailRepository subscriptionDetailRepository;
  private final TelemetryReporter telemetryReporter;

  private static final String ACCOUNT_IDENTIFIER_KEY = "accountIdentifier";
  private static final String SUBSCRIPTION_TELEMETRY_CATEGORY = "subscription";
  private static final String SUBSCRIPTION_TELEMETRY_EVENT_INITIATED = "Subscription Deletion Initiated";
  private static final String SUBSCRIPTION_TELEMETRY_EVENT_SUCCEEDED = "Subscription Deletion Succeeded";

  @Inject
  public SubscriptionDeleteHandler(
      SubscriptionDetailRepository subscriptionDetailRepository, TelemetryReporter telemetryReporter) {
    this.subscriptionDetailRepository = subscriptionDetailRepository;
    this.telemetryReporter = telemetryReporter;
  }
  @Override
  public void handleEvent(Event event) {
    Subscription subscription = StripeEventUtils.convertEvent(event, Subscription.class);

    String accountIdentifier = getAccountIdentifier(subscription);
    HashMap<String, Object> properties = new HashMap<>();
    telemetryReporter.sendIdentifyEvent(subscription.getLatestInvoiceObject().getCustomerEmail(), properties,
        ImmutableMap.<Destination, Boolean>builder().put(Destination.AMPLITUDE, true).build());
    telemetryReporter.sendTrackEvent(SUBSCRIPTION_TELEMETRY_EVENT_INITIATED,
        subscription.getLatestInvoiceObject().getCustomerEmail(), accountIdentifier, properties,
        ImmutableMap.<Destination, Boolean>builder().put(Destination.AMPLITUDE, true).build(),
        SUBSCRIPTION_TELEMETRY_CATEGORY);

    // delete subscription mapping
    subscriptionDetailRepository.deleteBySubscriptionId(subscription.getId());
    log.info("Handled subscription deletion for {}", subscription.getId());

    telemetryReporter.sendTrackEvent(SUBSCRIPTION_TELEMETRY_EVENT_SUCCEEDED,
        subscription.getLatestInvoiceObject().getCustomerEmail(), accountIdentifier, properties,
        ImmutableMap.<Destination, Boolean>builder().put(Destination.AMPLITUDE, true).build(),
        SUBSCRIPTION_TELEMETRY_CATEGORY);
  }

  private String getAccountIdentifier(Subscription subscription) {
    return subscription.getMetadata().get(ACCOUNT_IDENTIFIER_KEY);
  }
}
