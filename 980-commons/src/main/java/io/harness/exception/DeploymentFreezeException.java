/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;

import java.util.EnumSet;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class DeploymentFreezeException extends WingsException {
  private String accountId;
  private boolean masterFreeze;
  private List<String> deploymentFreezeIds;
  private List<String> deploymentFreezeNamesList;
  private String deploymentFreezeNames;
  private boolean serviceFrozen;

  private static final String MESSAGE_KEY = "message";
  public DeploymentFreezeException(ErrorCode code, Level level, EnumSet<ReportTarget> reportTargets, String accountId,
      List<String> deploymentFreezeIds, List<String> deploymentFreezeNamesList, String deploymentFreezeNames,
      boolean masterFreeze, boolean serviceFrozen) {
    super(getErrorMessage(deploymentFreezeNames, deploymentFreezeIds.size(), masterFreeze, serviceFrozen), null, code,
        level, reportTargets, null);
    param(MESSAGE_KEY, getErrorMessage(deploymentFreezeNames, deploymentFreezeIds.size(), masterFreeze, serviceFrozen));
    this.masterFreeze = masterFreeze;
    this.deploymentFreezeIds = deploymentFreezeIds;
    this.deploymentFreezeNamesList = deploymentFreezeNamesList;
    this.accountId = accountId;
    this.deploymentFreezeNames = deploymentFreezeNames;
    this.serviceFrozen = serviceFrozen;
  }

  @Override
  public String getMessage() {
    return getErrorMessage(deploymentFreezeNames, deploymentFreezeIds.size(), masterFreeze, serviceFrozen);
  }

  private static String getErrorMessage(
      String names, double freezeWindowCount, boolean masterFreeze, boolean serviceFrozen) {
    if (masterFreeze) {
      return "Master Deployment Freeze is active. No deployments are allowed.";
    }
    return String.format("Deployment Freeze Window%s %s %s active for the %s. No deployments are allowed to proceed.",
        freezeWindowCount > 1 ? "s" : "", names, freezeWindowCount > 1 ? "are" : "is",
        serviceFrozen ? "service" : "environment");
  }
}
