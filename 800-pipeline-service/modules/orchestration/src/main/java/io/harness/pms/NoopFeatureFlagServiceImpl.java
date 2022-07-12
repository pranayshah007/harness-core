/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms;

import io.harness.beans.FeatureName;

public class NoopFeatureFlagServiceImpl implements PmsFeatureFlagService {
  @Override
  public boolean isEnabled(String accountId, FeatureName featureName) {
    return false;
  }

  @Override
  public boolean isEnabled(String accountId, String featureName) {
    return false;
  }
}
