/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;
import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidIdentifierRefException;
import io.harness.exception.InvalidRequestException;
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
        .isInstanceOf(InvalidIdentifierRefException.class);
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

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testProjectLevelParentAccLevelChildScope() {
    IdentifierRefHelper.getIdentifierRefOrThrowException("account.templ1", "Account", "Org", "Project", "template");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOrgLevelParentAccLevelChildScope() {
    IdentifierRefHelper.getIdentifierRefOrThrowException("account.templ1", "Account", "Org", null, "template");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testAccLevelParentAccLevelChildScope() {
    IdentifierRefHelper.getIdentifierRefOrThrowException("account.templ1", "Account", null, null, "template");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testProjectLevelParentOrgLevelChildScope() {
    IdentifierRefHelper.getIdentifierRefOrThrowException("org.templ1", "Account", "Org", "Project", "template");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOrgLevelParentOrgLevelChildScope() {
    IdentifierRefHelper.getIdentifierRefOrThrowException("org.templ1", "Account", "Org", null, "template");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testAccLevelParentOrgLevelChildScope() {
    assertThatThrownBy(
        () -> IdentifierRefHelper.getIdentifierRefOrThrowException("org.templ1", "Account", null, null, "template"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The org level template cannot be used at account level. Ref: [org.templ1]");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testProjectLevelParentProjectLevelChildScope() {
    IdentifierRefHelper.getIdentifierRefOrThrowException("templ1", "Account", "Org", "Project", "template");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOrgLevelParentProjectLevelChildScope() {
    assertThatThrownBy(
        () -> IdentifierRefHelper.getIdentifierRefOrThrowException("templ1", "Account", "Org", null, "template"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The project level template cannot be used at org level. Ref: [templ1]");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testAccLevelParentProjectLevelChildScope() {
    assertThatThrownBy(
        () -> IdentifierRefHelper.getIdentifierRefOrThrowException("templ1", "Account", null, null, "template"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The project level template cannot be used at account level. Ref: [templ1]");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testAccLevelParentProjectLevelChildService() {
    assertThatThrownBy(
        () -> IdentifierRefHelper.getIdentifierRefOrThrowException("templ1", "Account", null, null, "service"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The project level service cannot be used at account level. Ref: [templ1]");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testEmptyIdentifierRefForIsEntityInAllowedScope() {
    assertThatThrownBy(() -> IdentifierRefHelper.getIdentifierRefOrThrowException("", "Account", null, null, "service"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Empty identifier ref cannot be used for service");

    assertThatThrownBy(
        () -> IdentifierRefHelper.getIdentifierRefOrThrowException(null, "Account", null, null, "service"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Empty identifier ref cannot be used for service");
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testIncorrectScopeCreation() {
    assertThatThrownBy(
        () -> IdentifierRefHelper.getIdentifierRef("<+pipeline.var", "Account", "ORGANIZATION", "PROJECT", null))
        .isInstanceOf(InvalidIdentifierRefException.class)
        .hasMessage(
            "Invalid Identifier Reference <+pipeline.var. Valid references must be one of the following formats [ id, org.id, account.id ]  for scope [ project, organisation, account ] respectively");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testProjectLevelScopeIdentifierRefOrThrowExceptionWithEmptyIdentifiers() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String identifier = "identifier";

    assertThatThrownBy(()
                           -> IdentifierRefHelper.getIdentifierRefOrThrowException(
                               identifier, accountIdentifier, orgIdentifier, "", "template"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The project level template cannot be used at org level. Ref: [identifier]");

    assertThatThrownBy(()
                           -> IdentifierRefHelper.getIdentifierRefOrThrowException(
                               identifier, accountIdentifier, "", projectIdentifier, "template"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid Identifier Reference used for template");

    assertThatThrownBy(()
                           -> IdentifierRefHelper.getIdentifierRefOrThrowException(
                               identifier, "", orgIdentifier, projectIdentifier, "template"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid Identifier Reference used for template");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOrgLevelScopeIdentifierRefOrThrowExceptionWithEmptyIdentifiers() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String identifier = "identifier";

    assertThatThrownBy(()
                           -> IdentifierRefHelper.getIdentifierRefOrThrowException(
                               "org." + identifier, accountIdentifier, "", null, "template"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The org level template cannot be used at account level. Ref: [org.identifier]");

    assertThatThrownBy(()
                           -> IdentifierRefHelper.getIdentifierRefOrThrowException(
                               "org." + identifier, "", orgIdentifier, null, "template"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid Identifier Reference used for template");
  }
}
