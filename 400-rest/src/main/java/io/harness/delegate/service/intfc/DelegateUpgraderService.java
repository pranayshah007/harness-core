/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.service.intfc;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.UpgradeCheckResult;

@OwnedBy(HarnessTeam.DEL)
public interface DelegateUpgraderService {
  UpgradeCheckResult getDelegateImageTag(String accountId, String currentDelegateImageTag, String delegateGroupName);

  UpgradeCheckResult getDelegateImageTag(String accountId, String currentDelegateImageTag);

  UpgradeCheckResult getUpgraderImageTag(String accountId, String currentUpgraderImageTag);
}
