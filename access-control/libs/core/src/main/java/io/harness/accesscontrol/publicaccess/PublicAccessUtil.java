/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.publicaccess;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PL)
public class PublicAccessUtil {
  public static final String ALL_USERS = "ALL_USERS";
  public static final String ALL_AUTHENTICATED_USERS = "ALL_AUTHENTICATED_USERS";
  private static final String PUBLIC_RESOURCE_GROUP_IDENTIFIER = "_public_resources";
  public static boolean isPrincipalPublic(String identifier) {
    return ALL_USERS == identifier || ALL_AUTHENTICATED_USERS == identifier;
  }

  public static boolean isResourceGroupPublic(String identifier) {
    return PUBLIC_RESOURCE_GROUP_IDENTIFIER == identifier;
  }
}
