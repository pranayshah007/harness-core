/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.pcf;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.delegate.beans.pcf.TasApplicationInfo")
public class TasApplicationInfo {
  int runningCount;
  String applicationName;
  String oldName;
  String applicationGuid;
  List<String> attachedRoutes;
  boolean isAutoScalarEnabled;

  public CfAppSetupTimeDetails toCfAppSetupTimeDetails() {
    return CfAppSetupTimeDetails.builder()
        .applicationGuid(applicationGuid)
        .applicationName(applicationName)
        .oldName(oldName)
        .initialInstanceCount(runningCount)
        .urls(attachedRoutes)
        .build();
  }

  public TasApplicationInfo cloneObject() {
    return TasApplicationInfo.builder()
        .applicationGuid(applicationGuid)
        .applicationName(applicationName)
        .oldName(oldName)
        .runningCount(runningCount)
        .attachedRoutes(attachedRoutes)
        .isAutoScalarEnabled(isAutoScalarEnabled)
        .build();
  }
}
