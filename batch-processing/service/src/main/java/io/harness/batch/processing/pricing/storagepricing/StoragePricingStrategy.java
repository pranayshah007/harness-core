/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.storagepricing;

import io.harness.batch.processing.pricing.InstancePricingStrategy;
import io.harness.batch.processing.pricing.PricingData;
import io.harness.ccm.commons.entities.batch.InstanceData;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StoragePricingStrategy implements InstancePricingStrategy {
  @Override
  public PricingData getPricePerHour(InstanceData instanceData, Instant startTime, Instant endTime,
      double instanceActiveSeconds, double parentInstanceActiveSecond) {
    Double pricePerMbPerHour =
        io.harness.batch.processing.pricing.storagepricing.StoragePricingData.getPricePerMbPerHour(
            instanceData.getMetaData());
    Double storageMb = instanceData.getStorageResource().getCapacity();

    return PricingData.builder()
        .storageMb(storageMb)
        .pricePerHour(storageMb * pricePerMbPerHour)
        .pricingSource(StoragePricingData.getPricingSource(instanceData.getMetaData()))
        .networkCost(0D) // Google doesn't charge for network usage
        .build();
  }
}
