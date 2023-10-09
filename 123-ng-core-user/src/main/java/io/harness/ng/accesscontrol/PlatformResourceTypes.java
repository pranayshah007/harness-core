/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.accesscontrol;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class PlatformResourceTypes {
  public static final String ACCOUNT = "ACCOUNT";
  public static final String ORGANIZATION = "ORGANIZATION";
  public static final String PROJECT = "PROJECT";
  public static final String USERGROUP = "USERGROUP";
  public static final String USER = "USER";
  public static final String AUTHSETTING = "AUTHSETTING";
  public static final String SERVICEACCOUNT = "SERVICEACCOUNT";
}
