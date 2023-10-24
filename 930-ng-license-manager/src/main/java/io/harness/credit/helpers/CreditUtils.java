/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.credit.helpers;

import io.harness.credit.entities.Credit;

import com.google.inject.Singleton;
import lombok.experimental.UtilityClass;

@UtilityClass
@Singleton
public class CreditUtils {
  public static Credit compareAndUpdate(Credit existingCredit, Credit toBeUpdatedCredit) {
    if (toBeUpdatedCredit.getCreditType() != null
        && !toBeUpdatedCredit.getCreditType().equals(existingCredit.getCreditType())) {
      existingCredit.setCreditType(toBeUpdatedCredit.getCreditType());
    }
    if (toBeUpdatedCredit.getCreditStatus() != null
        && !toBeUpdatedCredit.getCreditStatus().equals(existingCredit.getCreditStatus())) {
      existingCredit.setCreditStatus(toBeUpdatedCredit.getCreditStatus());
    }
    if (toBeUpdatedCredit.getExpiryTime() != 0 && toBeUpdatedCredit.getExpiryTime() != existingCredit.getExpiryTime()) {
      existingCredit.setExpiryTime(toBeUpdatedCredit.getExpiryTime());
    }
    if (toBeUpdatedCredit.getQuantity() != 0 && toBeUpdatedCredit.getQuantity() != existingCredit.getQuantity()) {
      existingCredit.setQuantity(toBeUpdatedCredit.getQuantity());
    }
    if (toBeUpdatedCredit.getModuleType() != null
        && !toBeUpdatedCredit.getModuleType().equals(existingCredit.getModuleType())) {
      existingCredit.setModuleType(toBeUpdatedCredit.getModuleType());
    }
    if (toBeUpdatedCredit.getPurchaseTime() != 0
        && toBeUpdatedCredit.getPurchaseTime() != existingCredit.getPurchaseTime()) {
      existingCredit.setPurchaseTime(toBeUpdatedCredit.getPurchaseTime());
    }
    return toBeUpdatedCredit;
  }
}
