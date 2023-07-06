/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;
import static lombok.AccessLevel.PRIVATE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;

import java.util.Arrays;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(PL)
@Data
@Builder
@FieldDefaults(level = PRIVATE, makeFinal = true)
@FieldNameConstants(innerTypeName = "ScopeKeys")
@EqualsAndHashCode
@RecasterAlias("io.harness.beans.Scope")
public class Scope {
  @NotEmpty String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;

  public static Scope of(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return Scope.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  public static Scope of(final String accountIdentifier, final String orgIdentifier, final String projectIdentifier,
      io.harness.encryption.Scope scope) {
    ScopeBuilder scopeBuilder = Scope.builder().accountIdentifier(accountIdentifier);

    if (scope == io.harness.encryption.Scope.ACCOUNT) {
      verifyFieldExistence(io.harness.encryption.Scope.ACCOUNT, accountIdentifier);

      return scopeBuilder.build();
    } else if (scope == io.harness.encryption.Scope.ORG) {
      verifyFieldExistence(io.harness.encryption.Scope.ORG, accountIdentifier, orgIdentifier);

      return scopeBuilder.orgIdentifier(orgIdentifier).build();
    } else if (scope == io.harness.encryption.Scope.PROJECT) {
      verifyFieldExistence(io.harness.encryption.Scope.PROJECT, accountIdentifier, orgIdentifier, projectIdentifier);

      return scopeBuilder.orgIdentifier(orgIdentifier).projectIdentifier(projectIdentifier).build();
    }

    throw new InvalidArgumentsException(format("Unknown scope %s", scope.getYamlRepresentation()));
  }

  // orderedFields must be in next order accountIdentifier, orgIdentifier, projectIdentifier
  public static void verifyFieldExistence(io.harness.encryption.Scope scope, String... orderedFields) {
    final String[] fieldNames = {"accountIdentifier", "orgIdentifier", "projectIdentifier"};
    final int fieldsNum = orderedFields.length;

    if (fieldsNum > fieldNames.length) {
      throw new InvalidArgumentsException(String.format(
          "Verification field existence failed for %s scope, fields: %s ", scope, Arrays.toString(orderedFields)));
    }

    for (int fieldNum = 0; fieldNum < fieldsNum; fieldNum++) {
      String fieldValue = orderedFields[fieldNum];

      if (isEmpty(fieldValue)) {
        throw new InvalidArgumentsException(
            String.format("%s cannot be empty for %s scope", fieldNames[fieldNum], scope));
      }
    }
  }
}
