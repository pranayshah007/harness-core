/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.IdentifierRef.IdentifierRefBuilder;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidIdentifierRefException;
import io.harness.exception.InvalidRequestException;
import io.harness.scope.ScopeHelper;

import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class IdentifierRefHelper {
  public final String IDENTIFIER_REF_DELIMITER = "\\."; // check if this is the correct delimiter
  public static final int MAX_RESULT_THRESHOLD_FOR_SPLIT = 2;
  private static final String GENERIC_IDENTIFIER_REFERENCE_HELP =
      "Valid references must be one of the following formats [ id, org.id, account.id ]  for scope [ project, organisation, account ] respectively";

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

  /**
   * parentEntityScope :- Scope of Parent entity which is referencing entityIdentifier.
   * -1 -> Invalid Scope
   * 0 -> AccLevelParentEntity
   * 1 -> OrgLevelParentEntity
   * 2 -> ProjectLevelParentEntity
   * entityIdentifier :- Identifier for the child entity.
   * Account Scope -> account.Identifier
   * Org Scope -> org.Identifier
   * Project Scope -> Identifer
   * Invalid Scope -> xyz.Identifer
   */
  public void validateEntityScopes(
      String accountId, String orgIdentifier, String projectIdentifier, String entityIdentifierRef, String fieldName) {
    int parentEntityScope;
    int childEntityScope;
    String parentScope;
    if (isNotEmpty(projectIdentifier) && isNotEmpty(orgIdentifier) && isNotEmpty(accountId)) {
      // project level parent entity.
      parentEntityScope = 2;
      parentScope = "project";
    } else if (isNotEmpty(orgIdentifier) && isNotEmpty(accountId) && isEmpty(projectIdentifier)) {
      // org level parent entity.
      parentEntityScope = 1;
      parentScope = "org";
    } else if (isNotEmpty(accountId) && isEmpty(projectIdentifier) && isEmpty(orgIdentifier)) {
      // account level parent entity.
      parentEntityScope = 0;
      parentScope = "account";
    } else {
      // Invalid parent scope.
      parentEntityScope = -1;
      parentScope = "invalid";
    }

    if (isEmpty(entityIdentifierRef)) {
      throw new InvalidRequestException(String.format("Empty identifier ref cannot be used for %s", fieldName));
    }

    String[] entityIdentifierRefStringSplit = entityIdentifierRef.split(IDENTIFIER_REF_DELIMITER);
    String childScope;
    if (entityIdentifierRefStringSplit.length == 1) {
      // project level child entity.
      childEntityScope = 2;
      childScope = "project";
    } else if (entityIdentifierRefStringSplit.length == 2) {
      childScope = entityIdentifierRefStringSplit[0];
      if ("account".equals(childScope)) {
        // account level child entity.
        childEntityScope = 0;
      } else if ("org".equals(childScope)) {
        // org level child entity.
        childEntityScope = 1;
      } else {
        // invalid scope
        childEntityScope = -1;
        childScope = "invalid";
      }
    } else {
      // invalid scope
      childEntityScope = -1;
      childScope = "invalid";
    }

    if (childEntityScope == -1 || parentEntityScope == -1) {
      throw new InvalidRequestException(String.format("Invalid Identifier Reference used for %s", fieldName));
    }

    // As child entity can exist in lower scopes but not the vica versa.
    if (parentEntityScope < childEntityScope) {
      throw new InvalidRequestException(String.format("The %s level %s cannot be used at %s level. Ref: [%s]",
          childScope, fieldName, parentScope, entityIdentifierRef));
    }
  }

  public IdentifierRef getConnectorIdentifierRef(
      String scopedConnectorIdentifierRef, String accountId, String orgIdentifier, String projectIdentifier) {
    if (isEmpty(scopedConnectorIdentifierRef)) {
      throw new InvalidIdentifierRefException("Unable to resolve empty connector identifier reference");
    }
    return getIdentifierRef(scopedConnectorIdentifierRef, accountId, orgIdentifier, projectIdentifier);
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
      throw new InvalidIdentifierRefException("Empty identifier values are not supported");
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
        throw new InvalidIdentifierRefException(String.format(
            "Invalid Identifier Reference %s. " + GENERIC_IDENTIFIER_REFERENCE_HELP, scopedIdentifierConfig));
      } else if (scope == Scope.ORG) {
        verifyFieldExistence(scope, accountId, orgIdentifier);
        return identifierRefBuilder.orgIdentifier(orgIdentifier).build();
      }
      verifyFieldExistence(scope, accountId);
      return identifierRefBuilder.build();
    } else {
      throw new InvalidIdentifierRefException(String.format(
          "Invalid Identifier Reference %s. " + GENERIC_IDENTIFIER_REFERENCE_HELP, scopedIdentifierConfig));
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
      throw new InvalidIdentifierRefException(
          String.format("Invalid Identifier Reference %s. " + GENERIC_IDENTIFIER_REFERENCE_HELP, identifier));
    }
  }

  public IdentifierRef getIdentifierRefOrThrowException(String scopedIdentifierConfig, String accountId,
      String orgIdentifier, String projectIdentifier, String fieldName) {
    return getIdentifierRefOrThrowException(
        scopedIdentifierConfig, accountId, orgIdentifier, projectIdentifier, null, fieldName);
  }

  public IdentifierRef getIdentifierRefOrThrowException(String scopedIdentifierConfig, String accountId,
      String orgIdentifier, String projectIdentifier, Map<String, String> metadata, String fieldName) {
    validateEntityScopes(accountId, orgIdentifier, projectIdentifier, scopedIdentifierConfig, fieldName);
    return getIdentifierRef(scopedIdentifierConfig, accountId, orgIdentifier, projectIdentifier, metadata);
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
        throw new InvalidIdentifierRefException(
            "Invalid Identifier Reference, Scope.PROJECT invalid." + GENERIC_IDENTIFIER_REFERENCE_HELP);
      }
    } else {
      throw new InvalidIdentifierRefException(String.format(
          "Invalid Identifier Reference %s. " + GENERIC_IDENTIFIER_REFERENCE_HELP, scopedIdentifierConfig));
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
