/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

@OwnedBy(CDC)
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_APPROVALS})
public enum ServiceNowActionNG {
  VALIDATE_CREDENTIALS("Validate Credentials"),
  GET_TICKET_CREATE_METADATA("Get Ticket Create Metadata"),
  GET_TICKET("Get ticket"),
  CREATE_TICKET("Create ticket"),
  UPDATE_TICKET("Update ticket"),
  GET_METADATA("Get Metadata"),
  GET_TEMPLATE("Get Template"),
  GET_STANDARD_TEMPLATE("Get Standard Template"),
  IMPORT_SET("Import Set"),
  GET_IMPORT_SET_STAGING_TABLES("Get Import Set Staging Tables"),
  GET_TICKET_TYPES("Get Ticket Types"),
  GET_STANDARD_TEMPLATES_READONLY_FIELDS("Get read only fields for standard templates"),
  CREATE_TICKET_USING_STANDARD_TEMPLATE("Create ticket using standard template"),
  GET_METADATA_V2("Get Metadata V2");

  private final String displayName;

  ServiceNowActionNG(String s) {
    displayName = s;
  }

  public String getDisplayName() {
    return displayName;
  }
}
