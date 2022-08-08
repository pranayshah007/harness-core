/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.pms.plan.execution.SetupAbstractionUtils.getAccountId;
import static io.harness.pms.plan.execution.SetupAbstractionUtils.getOrgIdentifier;
import static io.harness.pms.plan.execution.SetupAbstractionUtils.getProjectIdentifier;
import static io.harness.rule.OwnerRule.FERNANDOD;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SetupAbstractionUtilsTest {
  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetAccountId() {
    assertThat(getAccountId(singletonMap(SetupAbstractionKeys.accountId, "accountValue"))).isEqualTo("accountValue");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldNotGetAccountId() {
    assertThat(getAccountId(singletonMap("anyOtherKey", "accountValue"))).isNull();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetProjectIdentifier() {
    assertThat(getProjectIdentifier(singletonMap(SetupAbstractionKeys.projectIdentifier, "projectValue")))
        .isEqualTo("projectValue");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldNotGetProjectIdentifier() {
    assertThat(getProjectIdentifier(singletonMap("anyOtherKey", "projectValue"))).isNull();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetOrgIdentifier() {
    assertThat(getOrgIdentifier(singletonMap(SetupAbstractionKeys.orgIdentifier, "orgValue"))).isEqualTo("orgValue");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldNotGetOrgIdentifier() {
    assertThat(getOrgIdentifier(singletonMap("anyOtherKey", "orgValue"))).isNull();
  }
}
