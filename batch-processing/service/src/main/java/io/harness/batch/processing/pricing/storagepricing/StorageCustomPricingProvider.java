/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.storagepricing;

import io.harness.batch.processing.pricing.PricingSource;

import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
public class StorageCustomPricingProvider {
  @Getter public static final PricingSource pricingSource = PricingSource.HARDCODED;

  private StorageCustomPricingProvider() {}

  public enum NFS {
    DEFAULT("default", 0.001018D); // segate 1 TB	ST1000DM010	$49.99,	survives for 4 years
    @Getter private final String name;
    @Getter private final Double defaultPrice;
    NFS(String name, Double defaultPrice) {
      this.name = name;
      this.defaultPrice = defaultPrice;
    }
  }

  public enum Unknown {
    DEFAULT("default", 0.040D); // google default pricing for "non-regional" "pd-standard" "us-central1"
    @Getter private final String name;
    @Getter private final Double defaultPrice;
    Unknown(String name, Double defaultPrice) {
      this.name = name;
      this.defaultPrice = defaultPrice;
    }
  }
}
