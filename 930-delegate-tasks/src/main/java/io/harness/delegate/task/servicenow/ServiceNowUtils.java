/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;

import retrofit2.Response;

@OwnedBy(CDC)
public class ServiceNowUtils {
  public static boolean isUnauthorizedError(Response<?> response) {
    return 401 == response.code();
  }

  public static String errorWhileUpdatingTicket(Exception ex) {
    return String.format("Error occurred while updating serviceNow ticket: %s", ExceptionUtils.getMessage(ex));
  }

  public static String failedToUpdateTicket() {
    return "Failed to update ServiceNow ticket: {}";
  }
}
