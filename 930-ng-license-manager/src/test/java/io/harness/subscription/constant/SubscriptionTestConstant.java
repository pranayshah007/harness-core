/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.constant;

import static io.harness.subscription.params.UsageKey.NUMBER_OF_MAUS;
import static io.harness.subscription.params.UsageKey.NUMBER_OF_USERS;

import io.harness.ModuleType;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.LicenseType;
import io.harness.licensing.entities.modules.CFModuleLicense;
import io.harness.licensing.entities.modules.ModuleLicense;
import io.harness.subscription.params.RecommendationRequest;
import io.harness.subscription.params.UsageKey;

import java.util.EnumMap;
import org.joda.time.DateTime;

public class SubscriptionTestConstant {
  public static final String DEFAULT_ACCOUNT_ID = "TEST_ACCOUNT_ID";
  public static final String TRIAL_ACCOUNT_ID = "TRIAL_ACCOUNT_ID";
  public static final String COMPANY_NAME = "TEST_COMPANY_NAME";
  public static final Long DEFAULT_MAU_QUANTITY = 300000L;
  public static final int DEFAULT_MAX_USERS = 10;

  public static ModuleLicense DEFAULT_MODULE_LICENSE =
      CFModuleLicense.builder().numberOfClientMAUs(DEFAULT_MAU_QUANTITY).numberOfUsers(DEFAULT_MAX_USERS).build();
  static {
    DEFAULT_MODULE_LICENSE.setStatus(LicenseStatus.ACTIVE);
    DEFAULT_MODULE_LICENSE.setAccountIdentifier(DEFAULT_ACCOUNT_ID);
    DEFAULT_MODULE_LICENSE.setEdition(Edition.ENTERPRISE);
    DEFAULT_MODULE_LICENSE.setLicenseType(LicenseType.PAID);
    DEFAULT_MODULE_LICENSE.setModuleType(ModuleType.CF);
    DEFAULT_MODULE_LICENSE.setStartTime(DateTime.now().getMillis());
    DEFAULT_MODULE_LICENSE.setExpiryTime(Long.MAX_VALUE);
  }
  public static ModuleLicense TRIAL_MODULE_LICENSE =
      CFModuleLicense.builder().numberOfClientMAUs(DEFAULT_MAU_QUANTITY).numberOfUsers(DEFAULT_MAX_USERS).build();
  static {
    TRIAL_MODULE_LICENSE.setStatus(LicenseStatus.ACTIVE);
    TRIAL_MODULE_LICENSE.setAccountIdentifier(TRIAL_ACCOUNT_ID);
    TRIAL_MODULE_LICENSE.setEdition(Edition.ENTERPRISE);
    TRIAL_MODULE_LICENSE.setLicenseType(LicenseType.TRIAL);
    TRIAL_MODULE_LICENSE.setModuleType(ModuleType.CF);
    TRIAL_MODULE_LICENSE.setStartTime(DateTime.now().getMillis());
    TRIAL_MODULE_LICENSE.setExpiryTime(Long.MAX_VALUE);
  }

  public static EnumMap<UsageKey, Long> CF_USAGE_MAP = new EnumMap<>(UsageKey.class);
  static {
    CF_USAGE_MAP.put(NUMBER_OF_MAUS, 1000l);
    CF_USAGE_MAP.put(NUMBER_OF_USERS, 5l);
  }

  public static RecommendationRequest DEFAULT_RECOMMENDATION_REQUEST =
      RecommendationRequest.builder().moduleType(ModuleType.CF).usageMap(CF_USAGE_MAP).build();
}
