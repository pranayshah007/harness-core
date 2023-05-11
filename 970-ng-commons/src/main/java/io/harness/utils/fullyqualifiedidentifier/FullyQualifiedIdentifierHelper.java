/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;

import lombok.experimental.UtilityClass;

@UtilityClass
public class FullyQualifiedIdentifierHelper {
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

  private void validateAccountIdentifier(String accountIdentifier) {
    if (isEmpty(accountIdentifier)) {
      throw new InvalidRequestException("No account identifier provided.");
    }
  }

  private void validateOrgIdentifier(String orgIdentifier) {
    if (isEmpty(orgIdentifier)) {
      throw new InvalidRequestException("No org identifier provided.");
    }
  }

  private void validateIdentifier(String identifier) {
    if (isEmpty(identifier)) {
      throw new InvalidRequestException("No identifier provided.");
    }
  }
}
