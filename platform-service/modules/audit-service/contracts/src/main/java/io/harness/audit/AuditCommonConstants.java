/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class AuditCommonConstants {
  public static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  public static final String ORG_IDENTIFIER = "orgIdentifier";
  public static final String PROJECT_IDENTIFIER = "projectIdentifier";
  public static final String MODULE = "module";
  public static final String ACTION = "action";
  public static final String ENVIRONMENT_IDENTIFIER = "environmentIdentifier";
  public static final String IDENTIFIER = "identifier";
  public static final String TYPE = "type";
  public static final String CORRELATION_ID = "correlationId";
  public static final String USER_ID = "userId";
  public static final String USERNAME = "username";
}
