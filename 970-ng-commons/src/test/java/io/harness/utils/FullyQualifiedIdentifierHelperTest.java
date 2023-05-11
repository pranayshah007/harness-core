/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class FullyQualifiedIdentifierHelperTest extends CategoryTest {
  public static final String ACC_ID = "ACC_ID";
  public static final String ORG_ID = "ORG_ID";
  public static final String PROJ_ID = "PROJ_ID";

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void getFullyQualifiedIdentifier() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String connectorIdentifier = "connectorIdentifier";

    // FQN for a account level identifier
    String accountLevelFQN =
        FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(accountIdentifier, null, null, connectorIdentifier);
    assertThat(accountLevelFQN).isEqualTo("accountIdentifier/connectorIdentifier");
    String orgLevelFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, null, connectorIdentifier);
    assertThat(orgLevelFQN).isEqualTo("accountIdentifier/orgIdentifier/connectorIdentifier");
    String projectLevelFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
    assertThat(projectLevelFQN).isEqualTo("accountIdentifier/orgIdentifier/projectIdentifier/connectorIdentifier");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN)
  @Category(UnitTests.class)
  public void testNoAccountIdentifierSentThrowsException() {
    String connectorIdentifier = "connectorIdentifier";
    assertThatThrownBy(
        () -> FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(null, null, null, connectorIdentifier))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.BOOPESH)
  @Category(UnitTests.class)
  public void testNoOrgIdentifierForProjectConnectorThrowsException() {
    String connectorIdentifier = randomAlphabetic(7);
    assertThatThrownBy(()
                           -> FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
                               randomAlphabetic(10), null, randomAlphabetic(8), connectorIdentifier))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.BOOPESH)
  @Category(UnitTests.class)
  public void testNoAccountIdentifierForProjectConnectorThrowsException() {
    String connectorIdentifier = randomAlphabetic(7);
    assertThatThrownBy(()
                           -> FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
                               null, randomAlphabetic(10), randomAlphabetic(8), connectorIdentifier))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.BOOPESH)
  @Category(UnitTests.class)
  public void testNoAccountIdentiferForAccountConnectorThrowsException() {
    String connectorIdentifier = randomAlphabetic(7);
    assertThatThrownBy(()
                           -> FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
                               null, randomAlphabetic(10), null, connectorIdentifier))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldFetchScopeWiseIds() {
    ScopeWiseIds scopeWiseIds =
        IdentifierRefHelper.getScopeWiseIds(ACC_ID, ORG_ID, PROJ_ID, Arrays.asList("ref1", "org.ref2", "account.ref3"));
    assertThat(scopeWiseIds).isNotNull();
    assertThat(scopeWiseIds.getAccountScopedIds()).hasSize(1);
    assertThat(scopeWiseIds.getAccountScopedIds()).contains("ref3");
    assertThat(scopeWiseIds.getOrgScopedIds()).hasSize(1);
    assertThat(scopeWiseIds.getOrgScopedIds()).contains("ref2");
    assertThat(scopeWiseIds.getProjectScopedIds()).hasSize(1);
    assertThat(scopeWiseIds.getProjectScopedIds()).contains("ref1");
  }
}
