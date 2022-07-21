/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.IdentifierRef.IdentifierRefBuilder;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.encryption.ScopeHelper;
import io.harness.exception.InvalidIdentifierRefException;

import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class IdentifierRefHelper {
  public final String IDENTIFIER_REF_DELIMITER = "\\."; // check if this is the correct delimiter

  public IdentifierRef createIdentifierRefWithUnknownScope(String accountId, String orgIdentifier,
      String projectIdentifier, String unknownIdentifier, Map<String, String> metadata) {
    return IdentifierRef.builder()
        .scope(Scope.UNKNOWN)
        .metadata(metadata)
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .identifier(unknownIdentifier)
        .build();
  }

  public IdentifierRef getIdentifierRef(
      String scopedIdentifierConfig, String accountId, String orgIdentifier, String projectIdentifier) {
    return getIdentifierRef(scopedIdentifierConfig, accountId, orgIdentifier, projectIdentifier, null);
  }

  public IdentifierRef getIdentifierRef(String scopedIdentifierConfig, String accountId, String orgIdentifier,
      String projectIdentifier, Map<String, String> metadata) {
    Scope scope;
    String identifier;
    IdentifierRefBuilder identifierRefBuilder = IdentifierRef.builder().accountIdentifier(accountId);

    if (EmptyPredicate.isNotEmpty(metadata)) {
      identifierRefBuilder.metadata(metadata);
    }

    if (isEmpty(scopedIdentifierConfig)) {
      throw new InvalidIdentifierRefException("Empty identifier ref cannot be given");
    }
    String[] identifierConfigStringSplit = scopedIdentifierConfig.split(IDENTIFIER_REF_DELIMITER);

    if (identifierConfigStringSplit.length == 1) {
      identifier = identifierConfigStringSplit[0];
      scope = Scope.PROJECT;
      verifyFieldExistence(scope, accountId, orgIdentifier, projectIdentifier);
      return identifierRefBuilder.orgIdentifier(orgIdentifier)
          .projectIdentifier(projectIdentifier)
          .identifier(identifier)
          .scope(scope)
          .build();
    } else if (identifierConfigStringSplit.length == 2) {
      identifier = identifierConfigStringSplit[1];
      scope = getScope(identifierConfigStringSplit[0]);
      identifierRefBuilder = identifierRefBuilder.identifier(identifier).scope(scope);
      if (scope == Scope.PROJECT || scope == null) {
        throw new InvalidIdentifierRefException("Invalid Identifier Reference, Scope.PROJECT invalid.");
      } else if (scope == Scope.ORG) {
        verifyFieldExistence(scope, accountId, orgIdentifier);
        return identifierRefBuilder.orgIdentifier(orgIdentifier).build();
      }
      verifyFieldExistence(scope, accountId);
      return identifierRefBuilder.build();
    } else {
      throw new InvalidIdentifierRefException("Invalid Identifier Reference.");
    }
  }

  public IdentifierRef getIdentifierRef(Scope scope, String identifier, String accountId, String orgIdentifier,
      String projectIdentifier, Map<String, String> metadata) {
    IdentifierRefBuilder identifierRefBuilder =
        IdentifierRef.builder().accountIdentifier(accountId).identifier(identifier).scope(scope);

    if (EmptyPredicate.isNotEmpty(metadata)) {
      identifierRefBuilder.metadata(metadata);
    }
    if (scope == Scope.ACCOUNT) {
      verifyFieldExistence(scope, accountId);
      return identifierRefBuilder.build();
    }
    if (scope == Scope.ORG) {
      verifyFieldExistence(scope, accountId, orgIdentifier);
      return identifierRefBuilder.orgIdentifier(orgIdentifier).build();
    }
    if (scope == Scope.PROJECT) {
      verifyFieldExistence(scope, accountId, orgIdentifier, projectIdentifier);
      return identifierRefBuilder.orgIdentifier(orgIdentifier).projectIdentifier(projectIdentifier).build();
    } else {
      throw new InvalidIdentifierRefException("Invalid Identifier Reference.");
    }
  }

  public IdentifierRef getIdentifierRefFromEntityIdentifiers(
      String entityIdentifier, String accountId, String orgIdentifier, String projectIdentifier) {
    return IdentifierRef.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .identifier(entityIdentifier)
        .scope(ScopeHelper.getScope(accountId, orgIdentifier, projectIdentifier))
        .build();
  }

  public Scope getScope(String identifierScopeString) {
    if (isEmpty(identifierScopeString)) {
      return null;
    }
    return Scope.fromString(identifierScopeString);
  }

  public String getFullyQualifiedIdentifierRefString(IdentifierRef identifierRef) {
    if (identifierRef == null) {
      return null;
    }

    return FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(identifierRef.getAccountIdentifier(),
        identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
  }

  public String getIdentifier(String scopedIdentifierConfig) {
    if (isEmpty(scopedIdentifierConfig)) {
      throw new InvalidIdentifierRefException("scopedIdentifierConfig is null");
    }
    String identifier;
    String[] identifierConfigStringSplit = scopedIdentifierConfig.split(IDENTIFIER_REF_DELIMITER);
    if (identifierConfigStringSplit.length == 1) {
      identifier = identifierConfigStringSplit[0];
    } else if (identifierConfigStringSplit.length == 2) {
      identifier = identifierConfigStringSplit[1];
      Scope scope = getScope(identifierConfigStringSplit[0]);
      if (scope == Scope.PROJECT || scope == null) {
        throw new InvalidIdentifierRefException("Invalid Identifier Reference, Scope.PROJECT invalid.");
      }
    } else {
      throw new InvalidIdentifierRefException("Invalid Identifier Reference.");
    }
    return identifier;
  }

  // provide fields in order of accountId, orgId and projectId
  private void verifyFieldExistence(Scope scope, String... fields) {
    for (int fieldNum = 0; fieldNum < fields.length; fieldNum++) {
      if (isEmpty(fields[fieldNum])) {
        throw new InvalidIdentifierRefException(
            String.format("%s cannot be empty for %s scope", getEmptyFieldName(fieldNum), scope));
      }
    }
  }

  private String getEmptyFieldName(int fieldNum) {
    switch (fieldNum) {
      case 0:
        return "AccountIdentifier";
      case 1:
        return "OrgIdentifier";
      case 2:
        return "ProjectIdentifier";
      default:
        return "unknown";
    }
  }
}
