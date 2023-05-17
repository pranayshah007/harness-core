/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.async.beans;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public enum SetupUsageEventStatus {
  INITIATED,
  IN_PROGRESS,
  SUCCESS,
  FAILURE,
  ERROR,
  TERMINATED;

  public static boolean isFinalStatus(SetupUsageEventStatus status) {
    Set<SetupUsageEventStatus> finalStatuses = new HashSet<>(Arrays.asList(SUCCESS, FAILURE, ERROR, TERMINATED));
    return finalStatuses.contains(status);
  }
}
