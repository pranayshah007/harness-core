/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.rule.OwnerRule.MARKO;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.DelegateServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.rule.Owner;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.DEL)
public class DelegateEntityOwnerHelperTest extends DelegateServiceTestBase {
  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testBuildOwner() {
    assertThat(DelegateEntityOwnerHelper.buildOwner(null, null)).isNull();
    assertThat(DelegateEntityOwnerHelper.buildOwner("", "")).isNull();
    assertThat(DelegateEntityOwnerHelper.buildOwner("o1", null))
        .isEqualTo(DelegateEntityOwner.builder().identifier("o1").build());
    assertThat(DelegateEntityOwnerHelper.buildOwner(null, "p1"))
        .isEqualTo(DelegateEntityOwner.builder().identifier("p1").build());
    assertThat(DelegateEntityOwnerHelper.buildOwner("o1", "p1"))
        .isEqualTo(DelegateEntityOwner.builder().identifier("o1/p1").build());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testExtractOrgIdFromOwnerIdentifier() {
    assertThat(DelegateEntityOwnerHelper.extractOrgIdFromOwnerIdentifier(null)).isNull();
    assertThat(DelegateEntityOwnerHelper.extractOrgIdFromOwnerIdentifier("")).isNull();
    assertThat(DelegateEntityOwnerHelper.extractOrgIdFromOwnerIdentifier("o1")).isEqualTo("o1");
    assertThat(DelegateEntityOwnerHelper.extractOrgIdFromOwnerIdentifier("o1/")).isEqualTo("o1");
    assertThat(DelegateEntityOwnerHelper.extractOrgIdFromOwnerIdentifier("o1/p1")).isEqualTo("o1");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testExtractProjectIdFromOwnerIdentifier() {
    assertThat(DelegateEntityOwnerHelper.extractProjectIdFromOwnerIdentifier(null)).isNull();
    assertThat(DelegateEntityOwnerHelper.extractProjectIdFromOwnerIdentifier("")).isNull();
    assertThat(DelegateEntityOwnerHelper.extractProjectIdFromOwnerIdentifier("o1")).isNull();
    assertThat(DelegateEntityOwnerHelper.extractProjectIdFromOwnerIdentifier("o1/")).isNull();
    assertThat(DelegateEntityOwnerHelper.extractProjectIdFromOwnerIdentifier("o1/p1")).isEqualTo("p1");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenOwnerNullThenIsAccount() {
    assertThat(DelegateEntityOwnerHelper.isAccount(null)).isTrue();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenOwnerNullThenIsNotOrg() {
    assertThat(DelegateEntityOwnerHelper.isOrganisation(null)).isFalse();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenOwnerNullThenIsNotProject() {
    assertThat(DelegateEntityOwnerHelper.isProject(null)).isFalse();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenOwnerOrgThenIsOrganisation() {
    final DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner("orgId", StringUtils.EMPTY);
    assertThat(DelegateEntityOwnerHelper.isOrganisation(owner)).isTrue();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenOwnerProjectThenIsProject() {
    final DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner("orgId", "projId");
    assertThat(DelegateEntityOwnerHelper.isProject(owner)).isTrue();
  }
}
