/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.limits;

public interface LimitVicinityHandler {
  /**
   * Checks if a particular account is approaching limits consumption and takes appropriate actions (like raising
   * alerts) in that case
   */
  void checkAndAct(String accountId, String appId);
}
