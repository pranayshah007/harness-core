/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.resources.resourcegroups;

import static io.harness.accesscontrol.resources.resourcegroups.ResourceSelector.validateResourceType;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class ResourceSelectorTest {
  public static final String USER_RESOURCE_TYPE = "user";
  public static final String USERGROUP_RESOURCE_TYPE = "usergroup";

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testPermissionApplicableToResourceType() {
    assertThat(validateResourceType(USER_RESOURCE_TYPE, "/*/*")).isTrue();
    assertThat(validateResourceType(USER_RESOURCE_TYPE, "/ACCOUNT/account-id$/USER/*")).isTrue();
    assertThat(validateResourceType(USER_RESOURCE_TYPE, "/ACCOUNT/account-id$/USER/user-id")).isTrue();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testPermissionNotApplicableToResourceType() {
    assertThat(validateResourceType(USERGROUP_RESOURCE_TYPE, "/ACCOUNT/account-id$/USER/*")).isFalse();
    assertThat(validateResourceType(USERGROUP_RESOURCE_TYPE, "/ACCOUNT/account-id$/SERVICE/*")).isFalse();
    assertThat(validateResourceType(USERGROUP_RESOURCE_TYPE, "/ACCOUNT/account-id$/SERVICE/user-id")).isFalse();
  }
}
