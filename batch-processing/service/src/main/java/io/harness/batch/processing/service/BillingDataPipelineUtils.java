/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service;

import software.wings.beans.Account;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class BillingDataPipelineUtils {
  private static final String ACCOUNT_NAME_LABEL_KEY = "account_name";
  private static final String ACCOUNT_TYPE_LABEL_KEY = "account_type";
  private static final String PAID_ACCOUNT_TYPE = "PAID";

  public String modifyStringToComplyRegex(String accountInfo) {
    return accountInfo.toLowerCase().replaceAll("[^a-z0-9]", "_");
  }

  public Map<String, String> getLabelMap(String accountName, String accountType) {
    Map<String, String> labelMap = new HashMap<>();
    labelMap.put(ACCOUNT_NAME_LABEL_KEY, BillingDataPipelineUtils.modifyStringToComplyRegex(accountName));
    labelMap.put(ACCOUNT_TYPE_LABEL_KEY, BillingDataPipelineUtils.modifyStringToComplyRegex(accountType));
    return labelMap;
  }

  public String getAccountType(Account accountInfo) {
    if (accountInfo.getLicenseInfo() != null) {
      return accountInfo.getLicenseInfo().getAccountType();
    }
    return PAID_ACCOUNT_TYPE;
  }
}
