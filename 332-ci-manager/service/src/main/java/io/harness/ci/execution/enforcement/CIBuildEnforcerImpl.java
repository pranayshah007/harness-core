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
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.ExecutionLimitSpec;
import io.harness.ci.config.ExecutionLimits;
import io.harness.ci.execution.execution.QueueExecutionUtils;
import io.harness.ci.execution.integrationstage.IntegrationStageUtils;
import io.harness.ci.pipeline.executions.beans.CIInfraDetails;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;
import io.harness.pms.contracts.execution.Status;
import io.harness.repositories.ExecutionQueueLimitRepository;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CI)
@Slf4j
public class CIBuildEnforcerImpl implements CIBuildEnforcer {
  @Inject CILicenseService ciLicenseService;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject private QueueExecutionUtils queueExecutionUtils;
  @Inject private ExecutionLimits executionLimits;
  @Inject private ExecutionQueueLimitRepository executionQueueLimitRepository;
  private static final int MAX_LIMIT = 100;

  @Override
  public boolean shouldQueue(String accountID, Infrastructure infrastructure) {
    long activeExecutionsCount = queueExecutionUtils.getActiveExecutionsCount(
        accountID, List.of(Status.QUEUED.toString(), Status.RUNNING.toString()));
    long macExecutionsCount = queueExecutionUtils.getActiveMacExecutionsCount(
        accountID, List.of(Status.QUEUED.toString(), Status.RUNNING.toString()));
    long currExecutionCountNonMac = activeExecutionsCount - macExecutionsCount;
    OSType osType = getOSType(infrastructure);
    ExecutionLimitSpec executionLimitSpec = getExecutionLimit(accountID);
    log.info("queue limits for account: {}, total: {}, mac: {}. Current count: total: {}, mac: {}", accountID,
        executionLimitSpec.getDefaultTotalExecutionCount(), executionLimitSpec.getDefaultMacExecutionCount(),
        currExecutionCountNonMac, macExecutionsCount);
    if (osType == OSType.MacOS) {
      return macExecutionsCount > executionLimitSpec.getDefaultMacExecutionCount();
    }
    return currExecutionCountNonMac > executionLimitSpec.getDefaultTotalExecutionCount();
  }

  @Override
  public boolean shouldRun(String accountID, Infrastructure infrastructure) {
    long activeExecutionsCount =
        queueExecutionUtils.getActiveExecutionsCount(accountID, List.of(Status.RUNNING.toString()));
    long macExecutionsCount =
        queueExecutionUtils.getActiveMacExecutionsCount(accountID, List.of(Status.RUNNING.toString()));
    long currExecutionCountNonMac = activeExecutionsCount - macExecutionsCount;
    OSType osType = getOSType(infrastructure);
    ExecutionLimitSpec executionLimitSpec = getExecutionLimit(accountID);
    log.info("queue limits for account: {}, total: {}, mac: {}. Current count: total: {}, mac: {}", accountID,
        executionLimitSpec.getDefaultTotalExecutionCount(), executionLimitSpec.getDefaultMacExecutionCount(),
        currExecutionCountNonMac, macExecutionsCount);
    if (osType == OSType.MacOS) {
      return macExecutionsCount < executionLimitSpec.getDefaultMacExecutionCount();
    }
    return currExecutionCountNonMac < executionLimitSpec.getDefaultTotalExecutionCount();
  }

  private ExecutionLimitSpec getExecutionLimit(String accountId) {
    long macLimit = MAX_LIMIT;
    long totalLimit = MAX_LIMIT;
    Optional<ExecutionQueueLimit> overriddenConfig =
        executionQueueLimitRepository.findFirstByAccountIdentifier(accountId);
    // if the limits are override for a specific account, give priority to those
    if (overriddenConfig.isPresent()) {
      ExecutionQueueLimit executionQueueLimit = overriddenConfig.get();
      if (StringUtils.isNotEmpty(executionQueueLimit.getTotalExecLimit())) {
        macLimit = Integer.parseInt(executionQueueLimit.getMacExecLimit());
        totalLimit = Integer.parseInt(executionQueueLimit.getTotalExecLimit());
      }
    } else {
      LicensesWithSummaryDTO licensesWithSummaryDTO = ciLicenseService.getLicenseSummary(accountId);
      if (licensesWithSummaryDTO == null) {
        throw new CIStageExecutionException("Please enable CI free plan or reach out to support.");
      }
      ExecutionLimitSpec executionLimitSpec;
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
      totalLimit = executionLimitSpec.getDefaultTotalExecutionCount();
      macLimit = executionLimitSpec.getDefaultMacExecutionCount();
    }
    return ExecutionLimitSpec.builder()
        .defaultMacExecutionCount(macLimit)
        .defaultTotalExecutionCount(totalLimit)
        .build();
  }

  private OSType getOSType(Infrastructure infrastructure) {
    CIInfraDetails ciInfraDetails = IntegrationStageUtils.getCiInfraDetails(infrastructure);
    OSType osType = OSType.Linux;
    if (EmptyPredicate.isNotEmpty(ciInfraDetails.getInfraOSType())) {
      osType = OSType.fromString(ciInfraDetails.getInfraOSType());
    }
    return osType;
  }
}
