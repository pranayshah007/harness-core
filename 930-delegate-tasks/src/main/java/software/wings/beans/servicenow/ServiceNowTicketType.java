/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.servicenow;

import lombok.Getter;

public enum ServiceNowTicketType {
  INCIDENT("Incident"),
  PROBLEM("Problem"),
  CHANGE_REQUEST("Change"),
  CHANGE_TASK("Change Task");
  @Getter private String displayName;
  ServiceNowTicketType(String s) {
    displayName = s;
  }
}
