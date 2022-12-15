/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.service;

import io.harness.batch.processing.pricing.PricingSource;

import lombok.Value;

@Value
public class BillingData {
  private BillingAmountBreakup billingAmountBreakup;
  private IdleCostData idleCostData;
  private SystemCostData systemCostData;
  private double usageDurationSeconds;
  private double cpuUnitSeconds;
  private double memoryMbSeconds;
  private double storageMbSeconds;
  private double networkCost;
  private PricingSource pricingSource;
}
