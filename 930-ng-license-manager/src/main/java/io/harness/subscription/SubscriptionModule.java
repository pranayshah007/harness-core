/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription;

import static io.harness.authorization.AuthorizationServiceHeader.SUBSCRIPTION_SERVICE;

import io.harness.AccessControlClientModule;
import io.harness.subscription.handlers.InvoicePaymentSucceedHandler;
import io.harness.subscription.handlers.InvoiceUpdatedHandler;
import io.harness.subscription.handlers.StripeEventHandler;
import io.harness.subscription.handlers.SubscriptionDeleteHandler;
import io.harness.subscription.handlers.SubscriptionUpdateHandler;
import io.harness.subscription.services.CreditCardService;
import io.harness.subscription.services.SubscriptionService;
import io.harness.subscription.services.impl.CreditCardServiceImpl;
import io.harness.subscription.services.impl.SubscriptionServiceImpl;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;

public class SubscriptionModule extends AbstractModule {
  private static SubscriptionModule instance;
  private SubscriptionConfig subscriptionConfig;

  public static SubscriptionModule createInstance(SubscriptionConfig subscriptionConfig) {
    if (instance == null) {
      instance = new SubscriptionModule(subscriptionConfig);
    }
    return instance;
  }

  private SubscriptionModule(SubscriptionConfig subscriptionConfig) {
    this.subscriptionConfig = subscriptionConfig;
  }

  @Override
  protected void configure() {
    install(AccessControlClientModule.getInstance(
        subscriptionConfig.getAccessControlClientConfiguration(), SUBSCRIPTION_SERVICE.getServiceId()));
    install(SubscriptionSdkModule.createInstance(subscriptionConfig));
    bind(SubscriptionService.class).to(SubscriptionServiceImpl.class);
    bind(CreditCardService.class).to(CreditCardServiceImpl.class);

    MapBinder<String, StripeEventHandler> eventHandlerMapBinder =
        MapBinder.newMapBinder(binder(), String.class, StripeEventHandler.class);
    eventHandlerMapBinder.addBinding("invoice.paid").to(InvoicePaymentSucceedHandler.class);
    eventHandlerMapBinder.addBinding("invoice.updated").to(InvoiceUpdatedHandler.class);
    eventHandlerMapBinder.addBinding("customer.subscription.updated").to(SubscriptionUpdateHandler.class);
    eventHandlerMapBinder.addBinding("customer.subscription.deleted").to(SubscriptionDeleteHandler.class);
  }
}
