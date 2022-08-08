/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDC)
public enum JiraAction {
  CREATE_TICKET("Create Ticket"),
  CREATE_TICKET_NG("Create Ticket NG"),
  UPDATE_TICKET("Update Ticket"),
  UPDATE_TICKET_NG("Update Ticket NG"),
  AUTH("Auth"),

  GET_PROJECTS("Get Projects"),
  GET_FIELDS_OPTIONS("Get Field Options"),
  GET_STATUSES("Get Statuses"),
  GET_CREATE_METADATA("Get Create Metadata"),

  FETCH_ISSUE("Fetch Issue"),
  FETCH_ISSUE_DATA("Fetch Issue Details"),
  CHECK_APPROVAL("Check Jira Approval"),

  SEARCH_USER("Search User");

  private final String displayName;

  JiraAction(String s) {
    displayName = s;
  }

  public String getDisplayName() {
    return displayName;
  }
}
