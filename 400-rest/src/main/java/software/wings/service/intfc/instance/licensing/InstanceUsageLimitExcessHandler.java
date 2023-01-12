/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.instance.licensing;

public interface InstanceUsageLimitExcessHandler {
  /**
   * Checks whether given accounts instance usage is within limit.
   * And takes appropriate action otherwise.
   *
   * @param accountId
   */
  void handle(String accountId, String appId, double actualUsage);

  /**
   * Violation: If am account's deployed SI usage is more than allowed usage, it is considered a violation.
   * This method updates the count of that violation in store (Mongo at present, but can be anything else depending upon
   * implementation)
   */
  void updateViolationCount(String accountId, double actualUsage);
}
