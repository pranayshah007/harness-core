package io.harness.ci.enforcement;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.ExecutionLimits.ExecutionLimitSpec;
import io.harness.ci.execution.QueueExecutionUtils;
import io.harness.ci.license.CILicenseService;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.CI)
public class CIBuildEnforcerImpl implements CIBuildEnforcer {
  @Inject private CILicenseService ciLicenseService;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;

  @Inject private QueueExecutionUtils queueExecutionUtils;

  @Override
  public boolean checkBuildEnforcement(String accountId) {
    LicensesWithSummaryDTO licensesWithSummaryDTO = ciLicenseService.getLicenseSummary(accountId);
    if (licensesWithSummaryDTO != null) {
      ExecutionLimitSpec executionLimitSpec = null;
      switch (licensesWithSummaryDTO.getEdition()) {
        case FREE:
          executionLimitSpec = ciExecutionServiceConfig.getExecutionLimits().getFree();
          break;
        case TEAM:
          executionLimitSpec = ciExecutionServiceConfig.getExecutionLimits().getTeam();
          break;
        case ENTERPRISE:
          executionLimitSpec = ciExecutionServiceConfig.getExecutionLimits().getEnterprise();
          break;
        default:
          executionLimitSpec = ciExecutionServiceConfig.getExecutionLimits().getFree();
      }
      long currExecutionCount = queueExecutionUtils.getActiveExecutionsCount(accountId);
      long macExecutionsCount = queueExecutionUtils.getActiveExecutionsCount(accountId);

      return currExecutionCount < executionLimitSpec.getDefaultTotalExecutionCount()
          && macExecutionsCount < executionLimitSpec.getDefaultMacExecutionCount();
    }
    return false;
  }
}
