/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing;

public enum EditionAction {
  START_FREE(""),
  START_TRIAL(""),
  EXTEND_TRIAL(""),
  SUBSCRIBE(""),
  UPGRADE(""),
  CONTACT_SALES(""),
  CONTACT_SUPPORT(""),
  MANAGE(""),
  DISABLED_BY_TEAM("Other Harness modules are on plans other than Team"),
  DISABLED_BY_ENTERPRISE("Other Harness modules are on plans other than Enterprise");

  private String reason;

  EditionAction(String reason) {
    this.reason = reason;
  }

  public String getReason() {
    return reason;
  }
}
