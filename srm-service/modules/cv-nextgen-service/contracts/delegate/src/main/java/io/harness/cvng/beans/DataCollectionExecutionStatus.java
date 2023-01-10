/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import java.util.Arrays;
import java.util.List;

public enum DataCollectionExecutionStatus {
  FAILED,
  QUEUED,
  RUNNING,
  WAITING,
  EXPIRED,
  SUCCESS,
  ABORTED;
  public static List<DataCollectionExecutionStatus> getFailedStatuses() {
    return Arrays.asList(FAILED, EXPIRED);
  }
  public static List<DataCollectionExecutionStatus> getNonFinalStatuses() {
    return Arrays.asList(QUEUED, RUNNING, WAITING);
  }
}
