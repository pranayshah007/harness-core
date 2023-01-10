/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.enforcement;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.config.ExecutionLimits;
import io.harness.ci.config.ExecutionLimits.ExecutionLimitSpec;
import io.harness.ci.execution.QueueExecutionUtils;
import io.harness.ci.license.CILicenseService;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@Slf4j
public class CIBuildEnforcerImpl implements CIBuildEnforcer {
  @Inject CILicenseService ciLicenseService;
  @Inject private QueueExecutionUtils queueExecutionUtils;
  @Inject private ExecutionLimits executionLimits;

  @Override
  public boolean checkBuildEnforcement(String accountId) {
    long currExecutionCount = queueExecutionUtils.getActiveExecutionsCount(accountId);
    long macExecutionsCount = queueExecutionUtils.getActiveMacExecutionsCount(accountId);

    // if the limits are override for a specific account, give priority to those
    if (executionLimits.getOverrideConfigMap().containsKey(accountId)) {
      log.info("overridden limits for the account: {}, total: {}, mac: {}", accountId,
          executionLimits.getOverrideConfigMap().get(accountId).getDefaultTotalExecutionCount(),
          executionLimits.getOverrideConfigMap().get(accountId).getDefaultMacExecutionCount());
      return currExecutionCount <= executionLimits.getOverrideConfigMap().get(accountId).getDefaultTotalExecutionCount()
          && macExecutionsCount <= executionLimits.getOverrideConfigMap().get(accountId).getDefaultMacExecutionCount();
    }

    LicensesWithSummaryDTO licensesWithSummaryDTO = ciLicenseService.getLicenseSummary(accountId);
    if (licensesWithSummaryDTO != null) {
      ExecutionLimitSpec executionLimitSpec = null;
      switch (licensesWithSummaryDTO.getEdition()) {
        case TEAM:
          executionLimitSpec = executionLimits.getTeam();
          break;
        case ENTERPRISE:
          executionLimitSpec = executionLimits.getEnterprise();
          break;
        default:
          executionLimitSpec = executionLimits.getFree();
      }

      return currExecutionCount <= executionLimitSpec.getDefaultTotalExecutionCount()
          && macExecutionsCount <= executionLimitSpec.getDefaultMacExecutionCount();
    }
    // in case of any failures in fetching the license, keeping the default behaviour as allowed
    return true;
  }
}
