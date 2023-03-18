/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.enforcement;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.ExecutionQueueLimit;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.ci.config.ExecutionLimits;
import io.harness.ci.config.ExecutionLimits.ExecutionLimitSpec;
import io.harness.ci.execution.QueueExecutionUtils;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;
import io.harness.repositories.ExecutionQueueLimitRepository;

import com.google.inject.Inject;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CI)
@Slf4j
public class CIBuildEnforcerImpl implements CIBuildEnforcer {
  @Inject CILicenseService ciLicenseService;
  @Inject private QueueExecutionUtils queueExecutionUtils;
  @Inject private ExecutionLimits executionLimits;
  @Inject private ExecutionQueueLimitRepository executionQueueLimitRepository;

  @Override
  public boolean checkBuildEnforcement(String accountId) {
    long currExecutionCount = queueExecutionUtils.getActiveExecutionsCount(accountId);
    long macExecutionsCount = queueExecutionUtils.getActiveMacExecutionsCount(accountId);

    Optional<ExecutionQueueLimit> overriddenConfig =
        executionQueueLimitRepository.findFirstByAccountIdentifier(accountId);

    // if the limits are override for a specific account, give priority to those
    if (overriddenConfig.isPresent()) {
      Integer macLimit = 100000;
      Integer totalLimit = 100000;
      ExecutionQueueLimit executionQueueLimit = overriddenConfig.get();
      if (StringUtils.isNotEmpty(executionQueueLimit.getTotalExecLimit())) {
        macLimit = Integer.parseInt(executionQueueLimit.getMacExecLimit());
        totalLimit = Integer.parseInt(executionQueueLimit.getTotalExecLimit());
      }
      log.info("overridden limits for account: {}, total: {}, mac: {}. Current count: total: {}, mac: {}", accountId,
          totalLimit, macLimit, currExecutionCount, macExecutionsCount);
      return currExecutionCount <= totalLimit && macExecutionsCount <= macLimit;
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
      long defaultTotalExecutionCount = executionLimitSpec.getDefaultTotalExecutionCount();
      long defaultMacExecutionCount = executionLimitSpec.getDefaultMacExecutionCount();
      log.info("queue limits for the account: {}, total: {}, mac: {}. Current count: total: {}, mac: {}", accountId,
          defaultTotalExecutionCount, defaultMacExecutionCount, currExecutionCount, macExecutionsCount);
      return currExecutionCount <= defaultTotalExecutionCount && macExecutionsCount <= defaultMacExecutionCount;
    }
    // in case of any failures in fetching the license, keeping the default behaviour as allowed
    return true;
  }
}
