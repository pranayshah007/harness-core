/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

import io.harness.cvng.core.beans.params.ProjectParams;

import com.cronutils.utils.Preconditions;

public class ScopedInformation {
  private ScopedInformation() {}
  public static String getScopedInformation(ProjectParams projectParams, String identifier) {
    return projectParams.getAccountIdentifier() + '.' + projectParams.getOrgIdentifier() + '.'
        + projectParams.getProjectIdentifier() + '.' + identifier;
  }

  public static String getScopedInformation(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier) {
    Preconditions.checkNotNull(accountId, "accountIdentifier can't be null");
    if (orgIdentifier == null && projectIdentifier == null) {
      return "ACCOUNT." + accountId + '.' + identifier;
    } else if (projectIdentifier == null) {
      return "ORG." + accountId + '.' + orgIdentifier + '.' + identifier;
    } else {
      return "PROJECT." + accountId + '.' + orgIdentifier + '.' + projectIdentifier + '.' + identifier;
    }
  }
}
