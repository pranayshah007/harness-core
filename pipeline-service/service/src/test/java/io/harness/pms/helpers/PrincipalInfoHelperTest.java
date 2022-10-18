/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.SHALINI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.AccessDeniedException;
import io.harness.rule.Owner;
import io.harness.security.dto.PrincipalType;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class PrincipalInfoHelperTest extends CategoryTest {
  @InjectMocks PrincipalInfoHelper principalInfoHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testBuildRetryInfo() {
    for (PrincipalType value : PrincipalType.values()) {
      io.harness.pms.contracts.plan.PrincipalType principalType = principalInfoHelper.fromSecurityPrincipalType(value);
      assertThat(principalType).isNotNull();
    }
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetPrincipalInfoFromSecurityContext() {
    assertThatThrownBy(() -> principalInfoHelper.getPrincipalInfoFromSecurityContext())
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Principal cannot be null");
  }
}
