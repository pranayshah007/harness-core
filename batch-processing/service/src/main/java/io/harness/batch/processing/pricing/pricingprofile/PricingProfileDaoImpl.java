/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.pricingprofile;

import io.harness.ccm.cluster.entities.PricingProfile;
import io.harness.ccm.cluster.entities.PricingProfile.PricingProfileKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import org.springframework.stereotype.Component;

@Component
public class PricingProfileDaoImpl implements PricingProfileDao {
  private final HPersistence hPersistence;

  @Inject
  public PricingProfileDaoImpl(HPersistence hPersistence) {
    this.hPersistence = hPersistence;
  }

  @Override
  public PricingProfile fetchPricingProfile(String accountId) {
    return hPersistence.createQuery(PricingProfile.class).filter(PricingProfileKeys.accountId, accountId).get();
  }

  @Override
  public boolean create(PricingProfile pricingProfile) {
    return hPersistence.save(pricingProfile) != null;
  }
}
