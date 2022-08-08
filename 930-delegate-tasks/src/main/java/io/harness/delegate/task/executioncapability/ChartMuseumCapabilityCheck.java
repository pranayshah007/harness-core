/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.executioncapability;

import static io.harness.delegate.clienttools.ClientTool.CHARTMUSEUM;
import static io.harness.k8s.kubectl.Utils.encloseWithQuotesIfNeeded;
import static io.harness.k8s.kubectl.Utils.executeCommand;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.capability.CapabilityParameters;
import io.harness.capability.CapabilitySubjectPermission;
import io.harness.capability.CapabilitySubjectPermission.CapabilitySubjectPermissionBuilder;
import io.harness.capability.CapabilitySubjectPermission.PermissionResult;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.ChartMuseumCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.clienttools.ChartmuseumVersion;
import io.harness.delegate.clienttools.InstallUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChartMuseumCapabilityCheck implements CapabilityCheck, ProtoCapabilityCheck {
  @Override
  public CapabilityResponse performCapabilityCheck(ExecutionCapability delegateCapability) {
    ChartMuseumCapability capability = (ChartMuseumCapability) delegateCapability;
    String chartMuseumPath = getChartMuseumPath(capability.isUseLatestChartMuseumVersion());
    if (isBlank(chartMuseumPath)) {
      return CapabilityResponse.builder().delegateCapability(capability).validated(false).build();
    }
    String chartMuseumVersionCommand =
        "${CHART_MUSEUM_PATH} -v".replace("${CHART_MUSEUM_PATH}", encloseWithQuotesIfNeeded(chartMuseumPath));
    return CapabilityResponse.builder()
        .validated(executeCommand(chartMuseumVersionCommand, 2))
        .delegateCapability(capability)
        .build();
  }

  @Override
  public CapabilitySubjectPermission performCapabilityCheckWithProto(CapabilityParameters parameters) {
    CapabilitySubjectPermissionBuilder builder = CapabilitySubjectPermission.builder();
    if (parameters.getCapabilityCase() != CapabilityParameters.CapabilityCase.CHART_MUSEUM_PARAMETERS) {
      return builder.permissionResult(PermissionResult.DENIED).build();
    }
    String chartMuseumPath = getChartMuseumPath(false);
    if (isBlank(chartMuseumPath)) {
      return builder.permissionResult(PermissionResult.DENIED).build();
    }
    String chartMuseumVersionCommand =
        "${CHART_MUSEUM_PATH} -v".replace("${CHART_MUSEUM_PATH}", encloseWithQuotesIfNeeded(chartMuseumPath));
    return builder
        .permissionResult(
            executeCommand(chartMuseumVersionCommand, 2) ? PermissionResult.ALLOWED : PermissionResult.DENIED)
        .build();
  }

  private String getChartMuseumPath(final boolean useLatestVersion) {
    if (useLatestVersion) {
      return InstallUtils.getLatestVersionPath(CHARTMUSEUM);
    }
    return InstallUtils.getPath(CHARTMUSEUM, ChartmuseumVersion.V0_8);
  }
}
