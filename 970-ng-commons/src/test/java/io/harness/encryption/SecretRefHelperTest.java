/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryption;

import static io.harness.rule.OwnerRule.TEJAS;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

import io.harness.category.element.UnitTests;
import io.harness.ng.core.BaseNGAccess;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SecretRefHelperTest {
  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetScopeIdentifierForSecretRef_AccountScope() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);

    SecretRefData secretRefData = SecretRefData.builder().identifier(identifier).scope(Scope.ACCOUNT).build();
    BaseNGAccess baseNGAccess = SecretRefHelper.getScopeIdentifierForSecretRef(
        secretRefData, accountIdentifier, orgIdentifier, projectIdentifier);
    assertEquals(baseNGAccess.getIdentifier(), identifier);
    assertEquals(baseNGAccess.getAccountIdentifier(), accountIdentifier);
    assertNull(baseNGAccess.getOrgIdentifier());
    assertNull(baseNGAccess.getProjectIdentifier());
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetScopeIdentifierForSecretRef_OrgScope() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);

    SecretRefData secretRefData = SecretRefData.builder().identifier(identifier).scope(Scope.ORG).build();
    BaseNGAccess baseNGAccess = SecretRefHelper.getScopeIdentifierForSecretRef(
        secretRefData, accountIdentifier, orgIdentifier, projectIdentifier);
    assertEquals(identifier, baseNGAccess.getIdentifier());
    assertEquals(baseNGAccess.getAccountIdentifier(), accountIdentifier);
    assertEquals(baseNGAccess.getOrgIdentifier(), orgIdentifier);
    assertNull(baseNGAccess.getProjectIdentifier());
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetScopeIdentifierForSecretRef_ProjectScope() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);

    SecretRefData secretRefData = SecretRefData.builder().identifier(identifier).scope(Scope.PROJECT).build();
    BaseNGAccess baseNGAccess = SecretRefHelper.getScopeIdentifierForSecretRef(
        secretRefData, accountIdentifier, orgIdentifier, projectIdentifier);
    assertEquals(identifier, baseNGAccess.getIdentifier());
    assertEquals(baseNGAccess.getAccountIdentifier(), accountIdentifier);
    assertEquals(baseNGAccess.getOrgIdentifier(), orgIdentifier);
    assertEquals(baseNGAccess.getProjectIdentifier(), projectIdentifier);
  }
}
