/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cd;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.execution.ExecutionStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@OwnedBy(PIPELINE)
public class CDDashboardServiceHelper {
  public static List<String> failedStatusList =
      Arrays.asList(ExecutionStatus.FAILED.name(), ExecutionStatus.ABORTED.name(), ExecutionStatus.EXPIRED.name(),
          ExecutionStatus.IGNOREFAILED.name(), ExecutionStatus.ERRORED.name(), ExecutionStatus.APPROVALREJECTED.name());

  public static List<String> getSuccessFailedStatusList() {
    List<String> successFailedList = new ArrayList<>(failedStatusList);
    successFailedList.add(ExecutionStatus.SUCCESS.name());
    return successFailedList;
  }
}
