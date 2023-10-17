/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ecs;

import io.harness.exception.InvalidRequestException;

import lombok.experimental.UtilityClass;

@UtilityClass
public class EcsUtils {
  public static final String DELIMITER = "__";

  public static String getServicePrefixByRemovingNumber(String name) {
    if (name != null) {
      int index = name.lastIndexOf(DELIMITER);
      if (index >= 0) {
        return name.substring(0, index + DELIMITER.length());
      }
    }
    return name;
  }

  public static int getRevisionFromServiceName(String name) {
    if (name != null) {
      int index = name.lastIndexOf(DELIMITER);
      if (index >= 0) {
        try {
          return Integer.parseInt(name.substring(index + DELIMITER.length()));
        } catch (NumberFormatException e) {
          throw new InvalidRequestException("Invalid revision in service:" + name);
        }
      }
    }
    return -1;
  }

  public static int getPercentCount(double percent, double thresholdCount) {
    return (int) Math.round((percent * thresholdCount) / 100);
  }
}
