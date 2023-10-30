/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
public class ServiceNowConstants {
  // auth types
  public static final String USERNAME_PASSWORD = "UsernamePassword";
  public static final String ADFS = "AdfsClientCredentialsWithCertificate";
  public static final String REFRESH_TOKEN = "RefreshTokenGrantType";
  public static final long TIME_OUT = 120;
  public static final String INVALID_SERVICE_NOW_CREDENTIALS = "Invalid ServiceNow credentials";
  public static final String NOT_FOUND = "404 Not found";
  public static final String CHANGE_TASK = "change_task";
  public static final String SYS_ID = "sys_id";
  public static final String ISSUE_NUMBER = "number";
  public static final String SYS_NAME = "sys_name";
  public static final String RETURN_FIELDS = "number,sys_id,change_task_type";
  public static final String QUERY_FOR_GETTING_CHANGE_TASK = "change_request.number=%s^change_task_type=%s";
  public static final String QUERY_FOR_GETTING_CHANGE_TASK_ALL = "change_request.number=%s";
  public static final String RESULT = "result";
  public static final String TEMPLATE = "template";
  public static final String VALUE = "value";
  public static final String STANDARD = "Standard";
  public static final String FORM = "Form";
  public static final String NORMAL = "Normal";
  public static final String META = "__meta";
  public static final String IGNOREFIELDS = "ignoredFields";
}
