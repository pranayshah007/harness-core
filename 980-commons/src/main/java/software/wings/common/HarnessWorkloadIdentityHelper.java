/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.common;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HarnessWorkloadIdentityHelper {
  public static final String ENV_VARIABLE_WORKLOAD_IDENTITY = "HARNESS_USING_WORKLOAD_IDENTITY";
  public static boolean usingWorkloadIdentity() {
    String usingWorkloadIdentity = System.getenv(ENV_VARIABLE_WORKLOAD_IDENTITY);
    boolean harnessIsUsingWorkloadIdentity = Boolean.valueOf(usingWorkloadIdentity);
    if (harnessIsUsingWorkloadIdentity) {
      log.info("[WI]: Harness Service is deployed in workload identity enabled environment");
    }
    return harnessIsUsingWorkloadIdentity;
  }
}
