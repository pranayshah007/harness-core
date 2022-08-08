/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.limits.lib;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DeploymentErrorType {
  private ErrorType errorType;
  private double limit;
  public enum ErrorType {
    SHORT_TERM_DEPLOYMENT_ERROR,

    LONG_TERM_DEPLOYMENT_ERROR
  }

  public String getErrorMessage() {
    if (errorType == ErrorType.SHORT_TERM_DEPLOYMENT_ERROR) {
      return "You can deploy only" + limit * 100
          + "% of maximum allowed deployments in a day in an hour. Please try again after some time.";
    } else {
      return "You have reached " + limit * 100
          + "% of allowed limits for deployments in a day. Some deployments may not be allowed. Please contact Harness support.";
    }
  }
}
