/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.subscription;

import io.harness.subscription.helpers.StripeHelper;
import io.harness.subscription.helpers.impl.StripeHelperImpl;

import com.google.inject.AbstractModule;
import com.stripe.Stripe;

public class SubscriptionSdkModule extends AbstractModule {
  private static SubscriptionSdkModule instance;
  private static SubscriptionConfig subscriptionConfig;

  private SubscriptionSdkModule() {}

  public static SubscriptionSdkModule createInstance(SubscriptionConfig config) {
    if (instance == null) {
      instance = new SubscriptionSdkModule();
      subscriptionConfig = config;

      Stripe.apiKey = subscriptionConfig.getStripeApiKey();
      Stripe.setMaxNetworkRetries(subscriptionConfig.getMaxNetworkReties());
      Stripe.setConnectTimeout(subscriptionConfig.getConnectTimeout());
      Stripe.setReadTimeout(subscriptionConfig.getReadTimeout());
    }
    return instance;
  }

  @Override
  protected void configure() {
    bind(StripeHelper.class).to(StripeHelperImpl.class);
  }
}