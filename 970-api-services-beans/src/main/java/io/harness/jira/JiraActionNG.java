/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

@OwnedBy(CDC)
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_APPROVALS})
public enum JiraActionNG {
  VALIDATE_CREDENTIALS("Validate Credentials"),
  GET_PROJECTS("Get Projects"),
  GET_STATUSES("Get Statuses"),
  GET_ISSUE("Get Issue"),
  GET_ISSUE_CREATE_METADATA("Get Issue Create Metadata"),
  GET_ISSUE_UPDATE_METADATA("Get Issue Update Metadata"),
  CREATE_ISSUE("Create Issue"),
  UPDATE_ISSUE("Update Issue"),
  SEARCH_USER("Search user"),
  GET_TRANSITIONS("Get Issue Transitions");

  private final String displayName;

  JiraActionNG(String s) {
    displayName = s;
  }

  public String getDisplayName() {
    return displayName;
  }
}
