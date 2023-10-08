/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.credit;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.CreditType;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.beans.credits.CICreditDTO;
import io.harness.credit.beans.credits.CreditDTO;
import io.harness.credit.utils.CreditStatus;

import java.util.Calendar;

@OwnedBy(GTM)
public class CreditUtils {
  private static final int FREE_CREDITS_QUANTITY = 2000;

  private static Calendar getStartOfNextMonth() {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.MONTH, 1);
    calendar.set(Calendar.DAY_OF_MONTH, 1);
    calendar.set(Calendar.AM_PM, Calendar.AM);
    calendar.set(Calendar.HOUR, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    return calendar;
  }

  public static CreditDTO buildCreditDTO(String accountIdentifier) {
    return CICreditDTO.builder()
        .accountIdentifier(accountIdentifier)
        .creditStatus(CreditStatus.ACTIVE)
        .quantity(FREE_CREDITS_QUANTITY)
        .purchaseTime(System.currentTimeMillis())
        .expiryTime(getStartOfNextMonth().getTimeInMillis())
        .creditType(CreditType.FREE)
        .moduleType(ModuleType.CI)
        .build();
  }
}
