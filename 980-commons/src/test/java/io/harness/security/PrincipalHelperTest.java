/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.MEENAKSHI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class PrincipalHelperTest extends CategoryTest {
  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testGetEmail_principalIsNull() {
    final String email = PrincipalHelper.getEmail(null);
    assertThat(email).isEqualTo(null);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testGetEmail_principalIsUserType_validEmail() {
    Principal principal = new UserPrincipal("name", "testuser@gmail.com", "testuser", "accountId");
    final String email = PrincipalHelper.getEmail(principal);
    assertThat(email).isEqualTo("testuser@gmail.com");
  }
  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testGetEmail_principalIsUserType_emailContainingREDACTED() {
    Principal principal = new UserPrincipal("name", "[REDACTED]", "testuser", "accountId");
    final String email = PrincipalHelper.getEmail(principal);
    assertThat(email).isEqualTo(null);
  }
}
