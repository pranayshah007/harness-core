/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.credit;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.beans.credits.CICreditDTO;
import io.harness.credit.beans.credits.CreditDTO;
import io.harness.credit.entities.CICredit;
import io.harness.credit.entities.Credit;
import io.harness.credit.utils.CreditStatus;

@OwnedBy(HarnessTeam.GTM)
public class CreditTestConstant {
  public static final String ACCOUNT_IDENTIFIER = "account_identifier";
  public static final String DEFAULT_ID = "id";
  public static final ModuleType DEFAULT_MODULE_TYPE = ModuleType.CI;
  public static final int QUANTITY = 100;
  public static final long PURCHASE_TIME = 1684424719000L;
  public static final long EXPIRY_TIME = 2631195919000L;

  public static final CreditDTO DEFAULT_CI_CREDIT_DTO = CICreditDTO.builder()
                                                            .id(DEFAULT_ID)
                                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                            .creditStatus(CreditStatus.EXPIRED)
                                                            .quantity(QUANTITY)
                                                            .purchaseTime(PURCHASE_TIME)
                                                            .expiryTime(EXPIRY_TIME)
                                                            .moduleType(DEFAULT_MODULE_TYPE)
                                                            .build();

  public static final Credit DEFAULT_CREDIT = CICredit.builder().build();
  static {
    DEFAULT_CREDIT.setId(DEFAULT_ID);
    DEFAULT_CREDIT.setAccountIdentifier(ACCOUNT_IDENTIFIER);
    DEFAULT_CREDIT.setCreditStatus(CreditStatus.EXPIRED);
    DEFAULT_CREDIT.setQuantity(QUANTITY);
    DEFAULT_CREDIT.setPurchaseTime(PURCHASE_TIME);
    DEFAULT_CREDIT.setExpiryTime(EXPIRY_TIME);
    DEFAULT_CREDIT.setModuleType(DEFAULT_MODULE_TYPE);
  }

  public static final CreditDTO REQUEST_CREDIT_DTO = CICreditDTO.builder()
                                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                         .creditStatus(CreditStatus.EXPIRED)
                                                         .quantity(QUANTITY)
                                                         .purchaseTime(PURCHASE_TIME)
                                                         .expiryTime(EXPIRY_TIME)
                                                         .moduleType(DEFAULT_MODULE_TYPE)
                                                         .build();
}
