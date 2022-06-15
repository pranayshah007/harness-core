/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.handlers;

import io.harness.ModuleType;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.entities.modules.ModuleLicense;
import io.harness.licensing.services.LicenseService;
import io.harness.repositories.SubscriptionDetailRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class SubscriptionDeleteHandler implements StripeEventHandler {
  private final LicenseService licenseService;
  private final SubscriptionDetailRepository subscriptionDetailRepository;

  @Inject
  public SubscriptionDeleteHandler(
      LicenseService licenseService, SubscriptionDetailRepository subscriptionDetailRepository) {
    this.licenseService = licenseService;
    this.subscriptionDetailRepository = subscriptionDetailRepository;
  }
  @Override
  public void handleEvent(Event event) {
    Subscription subscription = StripeEventUtils.convertEvent(event, Subscription.class);

    ModuleLicense currentLicense = licenseService.getCurrentLicense(subscription.getMetadata().get("accountIdentifier"),
        ModuleType.valueOf(subscription.getMetadata().get("moduleType")));
    log.info("Expire license {} because subscription {} canceled", currentLicense.getId(), subscription.getId());
    currentLicense.setExpiryTime(Instant.now().toEpochMilli());
    currentLicense.setStatus(LicenseStatus.EXPIRED);
    currentLicense.setSelfService(false);
    licenseService.updateModuleLicense(currentLicense);

    // delete subscription mapping
    subscriptionDetailRepository.deleteBySubscriptionId(subscription.getId());
    log.info("Handled subscription deletion for {}", subscription.getId());
  }
}
