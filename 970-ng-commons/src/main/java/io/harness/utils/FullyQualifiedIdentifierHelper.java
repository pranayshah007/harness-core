/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.IdentifierRefHelper.MAX_RESULT_THRESHOLD_FOR_SPLIT;

import io.harness.beans.IdentifierRef;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class FullyQualifiedIdentifierHelper {
  private void validateAccountIdentifier(String accountIdentifier) {
    if (EmptyPredicate.isEmpty(accountIdentifier)) {
      throw new InvalidRequestException("No account identifier provided.");
    }
  }

  private void validateOrgIdentifier(String orgIdentifier) {
    if (EmptyPredicate.isEmpty(orgIdentifier)) {
      throw new InvalidRequestException("No org identifier provided.");
    }
  }

  private void validateIdentifier(String identifier) {
    if (EmptyPredicate.isEmpty(identifier)) {
      throw new InvalidRequestException("No identifier provided.");
    }
  }

  public String getFullyQualifiedIdentifier(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier) {
    validateIdentifier(identifier);
    if (EmptyPredicate.isNotEmpty(projectIdentifier)) {
      validateOrgIdentifier(orgIdentifier);
      validateAccountIdentifier(accountId);
      return String.format("%s/%s/%s/%s", accountId, orgIdentifier, projectIdentifier, identifier);
    } else if (EmptyPredicate.isNotEmpty(orgIdentifier)) {
      validateAccountIdentifier(accountId);
      return String.format("%s/%s/%s", accountId, orgIdentifier, identifier);
    } else if (EmptyPredicate.isNotEmpty(accountId)) {
      return String.format("%s/%s", accountId, identifier);
    }
    throw new InvalidRequestException("No account ID provided.");
  }

  public String getFullyQualifiedScope(String accountId, String orgIdentifier, String projectIdentifier) {
    if (EmptyPredicate.isNotEmpty(projectIdentifier)) {
      validateOrgIdentifier(orgIdentifier);
      validateAccountIdentifier(accountId);
      return String.format("%s/%s/%s", accountId, orgIdentifier, projectIdentifier);
    } else if (EmptyPredicate.isNotEmpty(orgIdentifier)) {
      validateAccountIdentifier(accountId);
      return String.format("%s/%s", accountId, orgIdentifier);
    } else if (EmptyPredicate.isNotEmpty(accountId)) {
      return String.format("%s", accountId);
    }
    throw new InvalidRequestException("No account ID provided.");
  }

  /***
   *
   * @param accountId
   * @param orgIdentifier
   * @param projectIdentifier
   * @param identifier
   * @return IdentifierRef with appropriate scope based on the identifiers provided
   */

  public IdentifierRef getIdentifierRefWithScope(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier) {
    validateIdentifier(identifier);
    if (EmptyPredicate.isNotEmpty(projectIdentifier)) {
      validateOrgIdentifier(orgIdentifier);
      validateAccountIdentifier(accountId);
      return IdentifierRef.builder()
          .accountIdentifier(accountId)
          .orgIdentifier(orgIdentifier)
          .projectIdentifier(projectIdentifier)
          .identifier(identifier)
          .scope(Scope.PROJECT)
          .build();
    } else if (EmptyPredicate.isNotEmpty(orgIdentifier)) {
      validateAccountIdentifier(accountId);
      return IdentifierRef.builder()
          .accountIdentifier(accountId)
          .orgIdentifier(orgIdentifier)
          .identifier(identifier)
          .scope(Scope.ORG)
          .build();
    } else if (EmptyPredicate.isNotEmpty(accountId)) {
      return IdentifierRef.builder().accountIdentifier(accountId).identifier(identifier).scope(Scope.ACCOUNT).build();
    }
    throw new InvalidRequestException("No account ID provided.");
  }

  /**
   *
   * @param accountId account identifier
   * @param orgIdentifier org identifier
   * @param projectIdentifier project identifier
   * @param identifierOrRef identifier or scoped identifier
   * @return scoped identifier built from accountId, orgId, projectId, identifier
   */
  public String getRefFromIdentifierOrRef(
      String accountId, String orgIdentifier, String projectIdentifier, String identifierOrRef) {
    String[] identifierSplit = StringUtils.split(identifierOrRef, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);

    // Length 2 means already a ref
    if (identifierSplit == null || identifierSplit.length == 2) {
      return identifierOrRef;
    }

    return FullyQualifiedIdentifierHelper
        .getIdentifierRefWithScope(accountId, orgIdentifier, projectIdentifier, identifierOrRef)
        .buildScopedIdentifier();
  }

  public ScopeWiseIds getScopeWiseIds(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Collection<String> refs) {
    List<String> projectIds = new ArrayList<>();
    List<String> orgIds = new ArrayList<>();
    List<String> accountIds = new ArrayList<>();

    for (String ref : refs) {
      if (isNotEmpty(ref)) {
        IdentifierRef identifierRef =
            IdentifierRefHelper.getIdentifierRef(ref, accountIdentifier, orgIdentifier, projectIdentifier);

        if (Scope.PROJECT.equals(identifierRef.getScope())) {
          projectIds.add(identifierRef.getIdentifier());
        } else if (Scope.ORG.equals(identifierRef.getScope())) {
          orgIds.add(identifierRef.getIdentifier());
        } else if (Scope.ACCOUNT.equals(identifierRef.getScope())) {
          accountIds.add(identifierRef.getIdentifier());
        }
      }
    }
    return ScopeWiseIds.builder().accountIds(accountIds).orgIds(orgIds).projectIds(projectIds).build();
  }
}
