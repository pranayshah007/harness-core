/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.dtos;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.PL)
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class ScmGetBatchFilesByBranchRequestDTO {
  String accountIdentifier;
  Map<ScmGetBatchFileRequestIdentifier, ScmGetFileByBranchRequestDTO> scmGetFileByBranchRequestDTOMap;

  public void validate() {
    validateNonNullInputs();
    validateAllRequestsHaveSameAccountIdentifier();
    validateThatThereAreNoScopeViolations();
  }

  private void validateNonNullInputs() {
    if (accountIdentifier == null) {
      throw new InvalidRequestException("Account Identifier cannot be null");
    }
    if (scmGetFileByBranchRequestDTOMap == null) {
      throw new InvalidRequestException("Batch file request field cannot be null");
    }
  }

  private void validateThatThereAreNoScopeViolations() {
    Set<String> organizations = new HashSet<>();
    Set<String> projects = new HashSet<>();
    scmGetFileByBranchRequestDTOMap.forEach((requestIdentifier, scmGetFileByBranchRequestDTO) -> {
      String orgIdentifier = scmGetFileByBranchRequestDTO.getScope().getOrgIdentifier();
      String projectIdentifier = scmGetFileByBranchRequestDTO.getScope().getProjectIdentifier();
      if (isNotEmpty(orgIdentifier)) {
        organizations.add(orgIdentifier);
      }
      if (isNotEmpty(projectIdentifier)) {
        if (isEmpty(orgIdentifier)) {
          String errorMessage = "Org Identifier cannot be empty for a project scoped [%s] file request";
          throw new InvalidRequestException(String.format(errorMessage, projectIdentifier));
        }
        projects.add(projectIdentifier);
      }
      if (organizations.size() > 1) {
        String errorMessage = "Multiple org [%s] file requests are not allowed as single batch file request";
        throw new InvalidRequestException(String.format(errorMessage, String.join(",", organizations)));
      }
      if (projects.size() > 1) {
        String errorMessage = "Multiple project [%s] file requests are not allowed as single batch file request";
        throw new InvalidRequestException(String.format(errorMessage, String.join(",", projects)));
      }
    });
  }

  private void validateAllRequestsHaveSameAccountIdentifier() {
    scmGetFileByBranchRequestDTOMap.forEach((identifier, request) -> {
      String fileRequestAccountIdentifier = request.getScope().getAccountIdentifier();
      if (isEmpty(fileRequestAccountIdentifier) || !fileRequestAccountIdentifier.equals(accountIdentifier)) {
        String errorMessage =
            "Account Identifier  %s in one of the file requests doesn't match global Account Identifier of complete batch file request %s";
        throw new InvalidRequestException(String.format(errorMessage, fileRequestAccountIdentifier, accountIdentifier));
      }
    });
  }
}
