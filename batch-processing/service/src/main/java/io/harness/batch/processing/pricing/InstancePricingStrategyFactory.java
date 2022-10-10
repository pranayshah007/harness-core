/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing;

import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.PricingGroup;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InstancePricingStrategyFactory {
  Map<PricingGroup, InstancePricingStrategy> pricingStrategyContext = new HashMap<>();

  @Autowired
  public InstancePricingStrategyFactory(InstancePricingStrategy computeInstancePricingStrategy,
      InstancePricingStrategy ecsFargateInstancePricingStrategy, InstancePricingStrategy storagePricingStrategy) {
    pricingStrategyContext.put(PricingGroup.COMPUTE, computeInstancePricingStrategy);
    pricingStrategyContext.put(PricingGroup.ECS_FARGATE, ecsFargateInstancePricingStrategy);
    pricingStrategyContext.put(PricingGroup.STORAGE, storagePricingStrategy);
  }

  public InstancePricingStrategy getInstancePricingStrategy(InstanceType instanceType) {
    return pricingStrategyContext.get(instanceType.getPricingGroup());
  }
}
