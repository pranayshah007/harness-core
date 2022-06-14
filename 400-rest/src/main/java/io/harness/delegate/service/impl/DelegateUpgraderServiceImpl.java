/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.service.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateType;
import io.harness.delegate.beans.UpgradeCheckResult;
import io.harness.delegate.service.DelegateVersionService;
import io.harness.delegate.service.intfc.DelegateUpgraderService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

@Singleton
@ValidateOnExecution
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateUpgraderServiceImpl implements DelegateUpgraderService {
  private final DelegateVersionService delegateVersionService;

  @Inject
  public DelegateUpgraderServiceImpl(DelegateVersionService delegateVersionService) {
    this.delegateVersionService = delegateVersionService;
  }

  @Override
  public UpgradeCheckResult getDelegateImageTag(String accountId, String currentDelegateImageTag) {
    String newDelegateImageTag = delegateVersionService.getDelegateImageTag(accountId, DelegateType.KUBERNETES);
    final boolean shouldUpgrade = !currentDelegateImageTag.equals(newDelegateImageTag);
    return new UpgradeCheckResult(shouldUpgrade ? newDelegateImageTag : currentDelegateImageTag, shouldUpgrade);
  }

  @Override
  public UpgradeCheckResult getUpgraderImageTag(String accountId, String currentUpgraderImageTag) {
    String newUpgraderImageTag = delegateVersionService.getUpgraderImageTag(accountId, DelegateType.KUBERNETES);
    final boolean shouldUpgrade = !currentUpgraderImageTag.equals(newUpgraderImageTag);
    return new UpgradeCheckResult(shouldUpgrade ? newUpgraderImageTag : currentUpgraderImageTag, shouldUpgrade);
  }
}
