/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "FileReferenceKeys")
@OwnedBy(CDP)
public class FileReference {
  private static String FILE_REF_DELIMITER = ":";

  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String path;
  Scope scope;

  public static FileReference of(final String scopedFilePath, final String accountIdentifier,
      final String orgIdentifier, final String projectIdentifier) {
    Scope scope = buildScope(scopedFilePath);
    String[] scopedFilePathSplit = scopedFilePath.split(FILE_REF_DELIMITER);

    if (Scope.PROJECT == scope) {
      io.harness.beans.Scope.verifyFieldExistence(Scope.PROJECT, accountIdentifier, orgIdentifier, projectIdentifier);

      return FileReference.builder()
          .accountIdentifier(accountIdentifier)
          .orgIdentifier(orgIdentifier)
          .projectIdentifier(projectIdentifier)
          .path(scopedFilePathSplit[0])
          .scope(Scope.PROJECT)
          .build();
    } else if (Scope.ORG == scope) {
      io.harness.beans.Scope.verifyFieldExistence(scope, accountIdentifier, orgIdentifier);

      return FileReference.builder()
          .accountIdentifier(accountIdentifier)
          .orgIdentifier(orgIdentifier)
          .path(scopedFilePathSplit[1])
          .scope(Scope.ORG)
          .build();
    } else if (Scope.ACCOUNT == scope) {
      io.harness.beans.Scope.verifyFieldExistence(scope, accountIdentifier);

      return FileReference.builder()
          .accountIdentifier(accountIdentifier)
          .path(scopedFilePathSplit[1])
          .scope(Scope.ACCOUNT)
          .build();
    } else {
      throw new InvalidArgumentsException(format("Invalid file scoped path, %s", scopedFilePath));
    }
  }

  private static Scope buildScope(final String scopedFilePath) {
    if (isEmpty(scopedFilePath)) {
      throw new InvalidRequestException("Scoped file path cannot be null or empty");
    }

    String[] scopedFilePathSplit = scopedFilePath.split(FILE_REF_DELIMITER);
    if (scopedFilePathSplit.length == 1) {
      return Scope.PROJECT;
    } else if (scopedFilePathSplit.length == 2) {
      return Scope.fromString(scopedFilePathSplit[0]);
    } else {
      throw new InvalidArgumentsException(format("Invalid file scoped path, %s", scopedFilePath));
    }
  }

  public static String getScopedFileIdentifier(Scope fileScope, String fileIdentifier) {
    if (Scope.PROJECT != fileScope) {
      fileIdentifier = format("%s.%s", fileScope.getYamlRepresentation(), fileIdentifier);
    }
    return fileIdentifier;
  }
}
