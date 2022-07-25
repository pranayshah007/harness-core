/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidIdentifierRefException;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class IdentifierRefHelperTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testProjectLevelScopeIdentifierRef() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String identifier = "identifier";
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(identifier, accountIdentifier, orgIdentifier, projectIdentifier);

    IdentifierRef expected = IdentifierRef.builder()
                                 .scope(Scope.PROJECT)
                                 .accountIdentifier(accountIdentifier)
                                 .orgIdentifier(orgIdentifier)
                                 .projectIdentifier(projectIdentifier)
                                 .identifier(identifier)
                                 .build();

    assertThat(identifierRef).isEqualTo(expected);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testOrgLevelScopeIdentifierRef() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = null;
    String identifier = "identifier";
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef("org." + identifier, accountIdentifier, orgIdentifier, projectIdentifier);

    IdentifierRef expected = IdentifierRef.builder()
                                 .scope(Scope.ORG)
                                 .accountIdentifier(accountIdentifier)
                                 .orgIdentifier(orgIdentifier)
                                 .identifier(identifier)
                                 .build();

    assertThat(identifierRef).isEqualTo(expected);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testAccountLevelScopeIdentifierRef() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = null;
    String projectIdentifier = null;
    String identifier = "identifier";
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        "account." + identifier, accountIdentifier, orgIdentifier, projectIdentifier);

    IdentifierRef expected = IdentifierRef.builder()
                                 .scope(Scope.ACCOUNT)
                                 .accountIdentifier(accountIdentifier)
                                 .orgIdentifier(orgIdentifier)
                                 .projectIdentifier(projectIdentifier)
                                 .identifier(identifier)
                                 .build();

    assertThat(identifierRef).isEqualTo(expected);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testInvalidScopeInIdentifierStringThrowsException() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String identifier = "proj.identifier";

    assertThatThrownBy(
        () -> IdentifierRefHelper.getIdentifierRef(identifier, accountIdentifier, orgIdentifier, projectIdentifier))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetFullyQualifiedIdentifierRefStringProjLevel() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String identifier = "identifier";
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .scope(Scope.PROJECT)
                                      .accountIdentifier(accountIdentifier)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .identifier(identifier)
                                      .build();

    String fullyQualifiedIdentifier = IdentifierRefHelper.getFullyQualifiedIdentifierRefString(identifierRef);
    assertThat(fullyQualifiedIdentifier).isEqualTo("accountIdentifier/orgIdentifier/projectIdentifier/identifier");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetFullyQualifiedIdentifierRefStringOrgLevel() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String identifier = "identifier";
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .scope(Scope.PROJECT)
                                      .accountIdentifier(accountIdentifier)
                                      .orgIdentifier(orgIdentifier)
                                      .identifier(identifier)
                                      .build();

    String fullyQualifiedIdentifier = IdentifierRefHelper.getFullyQualifiedIdentifierRefString(identifierRef);
    assertThat(fullyQualifiedIdentifier).isEqualTo("accountIdentifier/orgIdentifier/identifier");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetFullyQualifiedIdentifierRefStringAccountLevel() {
    String accountIdentifier = "accountIdentifier";
    String identifier = "identifier";
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .scope(Scope.PROJECT)
                                      .accountIdentifier(accountIdentifier)
                                      .identifier(identifier)
                                      .build();

    String fullyQualifiedIdentifier = IdentifierRefHelper.getFullyQualifiedIdentifierRefString(identifierRef);
    assertThat(fullyQualifiedIdentifier).isEqualTo("accountIdentifier/identifier");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFullyQualifiedIdentifierOfAccountLevelIdentifier() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String identifier = "account.identifier";
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(identifier, accountIdentifier, orgIdentifier, projectIdentifier);

    String fullyQualifiedIdentifier = IdentifierRefHelper.getFullyQualifiedIdentifierRefString(identifierRef);
    assertThat(fullyQualifiedIdentifier).isEqualTo("accountIdentifier/identifier");
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void testGetIdentifier() {
    String identifierRefAccount = "account.identifier";
    String identifierRefOrg = "org.identifier";
    String identifierRefProj = "identifier";

    assertThat(IdentifierRefHelper.getIdentifier(identifierRefAccount)).isEqualTo("identifier");
    assertThat(IdentifierRefHelper.getIdentifier(identifierRefOrg)).isEqualTo("identifier");
    assertThat(IdentifierRefHelper.getIdentifier(identifierRefProj)).isEqualTo("identifier");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testProjectLevelScopeIdentifierRefThrowExceptionWithEmptyIdentifiers() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String identifier = "identifier";

    assertThatThrownBy(() -> IdentifierRefHelper.getIdentifierRef(identifier, accountIdentifier, orgIdentifier, ""))
        .isInstanceOf(InvalidIdentifierRefException.class)
        .hasMessage("ProjectIdentifier cannot be empty for PROJECT scope");

    assertThatThrownBy(() -> IdentifierRefHelper.getIdentifierRef(identifier, accountIdentifier, "", projectIdentifier))
        .isInstanceOf(InvalidIdentifierRefException.class)
        .hasMessage("OrgIdentifier cannot be empty for PROJECT scope");

    assertThatThrownBy(() -> IdentifierRefHelper.getIdentifierRef(identifier, "", orgIdentifier, projectIdentifier))
        .isInstanceOf(InvalidIdentifierRefException.class)
        .hasMessage("AccountIdentifier cannot be empty for PROJECT scope");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testOrgLevelScopeIdentifierRefThrowExceptionWithEmptyIdentifiers() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String identifier = "identifier";

    assertThatThrownBy(() -> IdentifierRefHelper.getIdentifierRef("org." + identifier, accountIdentifier, "", null))
        .isInstanceOf(InvalidIdentifierRefException.class)
        .hasMessage("OrgIdentifier cannot be empty for ORG scope");

    assertThatThrownBy(() -> IdentifierRefHelper.getIdentifierRef("org." + identifier, "", orgIdentifier, null))
        .isInstanceOf(InvalidIdentifierRefException.class)
        .hasMessage("AccountIdentifier cannot be empty for ORG scope");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testAccountLevelScopeIdentifierRefThrowExceptionWithEmptyIdenifiers() {
    String identifier = "identifier";

    assertThatThrownBy(() -> IdentifierRefHelper.getIdentifierRef("account." + identifier, null, null, null))
        .isInstanceOf(InvalidIdentifierRefException.class)
        .hasMessage("AccountIdentifier cannot be empty for ACCOUNT scope");
  }
}
