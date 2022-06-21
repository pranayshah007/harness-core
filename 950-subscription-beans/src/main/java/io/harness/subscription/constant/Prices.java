/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.constant;

public class Prices {
  public static final String PREMIUM_SUPPORT = "PREMIUM_SUPPORT";
  public static final String[] CD_PRICES = new String[] {"CD_ENTERPRISE_SERVICE_MONTHLY",
      "CD_ENTERPRISE_SERVICE_YEARLY", "CD_ENTERPRISE_PREMIUM_SUPPORT_MONTHLY", "CD_ENTERPRISE_PREMIUM_SUPPORT_YEARLY",
      PREMIUM_SUPPORT};
  public static final String[] CI_PRICES = new String[] {"CI_ENTERPRISE_DEVELOPERS_MONTHLY",
      "CI_ENTERPRISE_DEVELOPERS_YEARLY", "CI_TEAM_DEVELOPERS_MONTHLY", "CI_TEAM_DEVELOPERS_YEARLY",
      PREMIUM_SUPPORT};
  public static final String[] FF_PRICES = new String[] {"FF_ENTERPRISE_DEVELOPERS_MONTHLY",
      "FF_ENTERPRISE_DEVELOPERS_YEARLY", "FF_TEAM_DEVELOPERS_MONTHLY", "FF_TEAM_DEVELOPERS_YEARLY",
      "FF_ENTERPRISE_MAU_MONTHLY", "FF_ENTERPRISE_MAU_YEARLY", "FF_TEAM_MAU_MONTHLY", "FF_TEAM_MAU_YEARLY",
      PREMIUM_SUPPORT};
  public static String getLookupKey(String module, String edition, String product, String paymentFrequency) {
    return module.toUpperCase().trim() + "_" + edition.toUpperCase().trim() + "_" + product.toUpperCase().trim() + "_"
        + paymentFrequency.toUpperCase().trim();
  }
}