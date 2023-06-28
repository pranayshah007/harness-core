/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ResourceParams;
import io.harness.exception.InvalidArgumentsException;
import io.harness.utils.FullyQualifiedIdentifierHelper;

import com.cronutils.utils.Preconditions;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public class ScopedInformation {
  private ScopedInformation() {}

  public static String getScopedInformation(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier) {
    Preconditions.checkNotNull(accountId, "accountIdentifier can't be null");
    if (orgIdentifier == null && projectIdentifier == null) {
      return "ACCOUNT." + accountId + '.' + identifier;
    } else if (projectIdentifier == null) {
      return "ORG." + accountId + '.' + orgIdentifier + '.' + identifier;
    } else {
      return "PROJECT." + accountId + '.' + orgIdentifier + '.' + projectIdentifier + '.' + identifier;
    }
  }

  public static String getScopedInformation(String accountId, String orgIdentifier, String projectIdentifier) {
    Preconditions.checkNotNull(accountId, "accountIdentifier can't be null");
    if (orgIdentifier == null && projectIdentifier == null) {
      return "ACCOUNT." + accountId;
    } else if (projectIdentifier == null) {
      return "ORG." + accountId + '.' + orgIdentifier;
    } else {
      return "PROJECT." + accountId + '.' + orgIdentifier + '.' + projectIdentifier;
    }
  }
  public static ResourceParams getResourceParamsFromScopedIdentifier(String scopedIdentifier) {
    String[] splitScopedIdentifier = scopedIdentifier.split("\\.");
    if (splitScopedIdentifier.length == 5 && splitScopedIdentifier[0].equals("PROJECT")) {
      return ResourceParams.builder()
          .accountIdentifier(splitScopedIdentifier[1])
          .orgIdentifier(splitScopedIdentifier[2])
          .projectIdentifier(splitScopedIdentifier[3])
          .identifier(splitScopedIdentifier[4])
          .build();
    } else if (splitScopedIdentifier.length == 4 && splitScopedIdentifier[0].equals("ORG")) {
      return ResourceParams.builder()
          .accountIdentifier(splitScopedIdentifier[1])
          .orgIdentifier(splitScopedIdentifier[2])
          .identifier(splitScopedIdentifier[3])
          .build();
    } else if (splitScopedIdentifier.length == 3 && splitScopedIdentifier[0].equals("ACCOUNT")) {
      return ResourceParams.builder()
          .accountIdentifier(splitScopedIdentifier[1])
          .identifier(splitScopedIdentifier[2])
          .build();
    } else {
      throw new InvalidArgumentsException("Invalid Scoped Identifier");
    }
  }

  public static List<ResourceParams> getResourceParamsFromScopedIdentifiers(List<String> scopedIdentifiers) {
    return scopedIdentifiers.stream()
        .map(ScopedInformation::getResourceParamsFromScopedIdentifier)
        .collect(Collectors.toList());
  }

  public static String getLowerCaseScope(ProjectParams projectParams) {
    if (projectParams.getProjectIdentifier() != null) {
      return "project";
    } else if (projectParams.getOrgIdentifier() != null) {
      return "org";
    } else if (projectParams.getAccountIdentifier() != null) {
      return "account";
    } else {
      throw new InvalidArgumentsException("Invalid Scoped Identifier");
    }
  }

  public static String getFNQFromScopedIdentifier(
      String accountId, String orgIdentifier, String projectIdentifier, String identifierWithScope) {
    Pair<String, String> scopeIdentifierPair = getScopeAndIdentifierPair(identifierWithScope);
    switch (scopeIdentifierPair.getLeft()) {
      case "account":
        return FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
            accountId, null, null, scopeIdentifierPair.getRight());
      case "org":
        return FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
            accountId, orgIdentifier, null, scopeIdentifierPair.getRight());
      case "project":
        return FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
            accountId, orgIdentifier, projectIdentifier, scopeIdentifierPair.getRight());
      default:
        throw new IllegalArgumentException(String.format(
            "Invalid Scope: %s for connector Identifier: %s", scopeIdentifierPair.getLeft(), identifierWithScope));
    }
  }

  public static Pair<String, String> getScopeAndIdentifierPair(String identifierWithScope) {
    String[] parts = identifierWithScope.split("[.]", 2);
    if (parts.length > 0 && (parts[0].equals("account") || parts[0].equals("org"))) {
      return Pair.of(parts[0], parts[1]);
    } else {
      return Pair.of("project", identifierWithScope);
    }
  }

  public static String getScopedIdentifier(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier) {
    String scope = getLowerCaseScope(ProjectParams.builder()
                                         .accountIdentifier(accountId)
                                         .orgIdentifier(orgIdentifier)
                                         .projectIdentifier(projectIdentifier)
                                         .build());
    if (scope.equals("account") || scope.equals("org")) {
      return scope + "." + identifier;
    }
    return identifier;
  }
}
