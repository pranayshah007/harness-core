/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.AuditCommonConstants;
import io.harness.audit.beans.AuditFilterPropertiesDTO;
import io.harness.audit.beans.Environment;
import io.harness.audit.beans.Principal;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;

@OwnedBy(PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class AuditFilterPropertiesValidator {
  public void validate(String accountIdentifier, AuditFilterPropertiesDTO auditFilterPropertiesDTO) {
    validateFilterRequest(accountIdentifier, auditFilterPropertiesDTO);
  }

  private void validateFilterRequest(String accountIdentifier, AuditFilterPropertiesDTO auditFilterPropertiesDTO) {
    if (isEmpty(accountIdentifier)) {
      throw new InvalidRequestException("Missing accountIdentifier in the audit filter request");
    }
    if (auditFilterPropertiesDTO == null) {
      return;
    }
    if (isNotEmpty(auditFilterPropertiesDTO.getScopes())) {
      verifyScopes(accountIdentifier, auditFilterPropertiesDTO.getScopes());
    }
    if (isNotEmpty(auditFilterPropertiesDTO.getResources())) {
      verifyResources(auditFilterPropertiesDTO.getResources());
    }
    if (isNotEmpty(auditFilterPropertiesDTO.getPrincipals())) {
      verifyPrincipals(auditFilterPropertiesDTO.getPrincipals());
    }
    if (isNotEmpty(auditFilterPropertiesDTO.getEnvironments())) {
      verifyEnvironments(auditFilterPropertiesDTO.getEnvironments());
    }
    verifyTimeFilter(auditFilterPropertiesDTO.getStartTime(), auditFilterPropertiesDTO.getEndTime());
  }

  private void verifyPrincipals(List<Principal> principals) {
    principals.forEach(principal -> {
      if (principal.getType() == null) {
        throw new InvalidRequestException("Invalid principal filter with missing principal type.");
      }
      if (isEmpty(principal.getIdentifier())) {
        throw new InvalidRequestException("Invalid principal filter with missing principal identifier.");
      }
    });
  }

  private void verifyEnvironments(List<Environment> environments) {
    if (isNotEmpty(environments)) {
      environments.forEach(environment -> {
        if (environment.getType() == null && isEmpty(environment.getIdentifier())) {
          throw new InvalidRequestException("Invalid environment filter with missing both type and identifier.");
        }
      });
    }
  }

  private void verifyScopes(String accountIdentifier, List<ResourceScopeDTO> resourceScopes) {
    if (isNotEmpty(resourceScopes)) {
      resourceScopes.forEach(resourceScope -> verifyScope(accountIdentifier, resourceScope));
    }
  }

  private void verifyScope(String accountIdentifier, ResourceScopeDTO resourceScope) {
    if (!accountIdentifier.equals(resourceScope.getAccountIdentifier())) {
      throw new InvalidRequestException(String.format(
          "Invalid resource scope filter with accountIdentifier %s.", resourceScope.getAccountIdentifier()));
    }
    if (isEmpty(resourceScope.getOrgIdentifier()) && isNotEmpty(resourceScope.getProjectIdentifier())) {
      throw new InvalidRequestException(
          String.format("Invalid resource scope filter with projectIdentifier %s but missing orgIdentifier.",
              resourceScope.getProjectIdentifier()));
    }
    if (isEmpty(resourceScope.getOrgIdentifier()) && isNotEmpty(resourceScope.getLabels())) {
      throw new InvalidRequestException("Invalid resource scope filter with labels present but missing orgIdentifier.");
    }
    if (isEmpty(resourceScope.getProjectIdentifier()) && isNotEmpty(resourceScope.getLabels())) {
      throw new InvalidRequestException(
          "Invalid resource scope filter with labels present but missing projectIdentifier.");
    }
    Map<String, String> labels = resourceScope.getLabels();
    if (isNotEmpty(labels)) {
      labels.forEach((key, value) -> {
        if (isEmpty(key)) {
          throw new InvalidRequestException("Invalid resource scope filter with missing key in resource scope labels.");
        }
        if (isEmpty(value)) {
          throw new InvalidRequestException(
              "Invalid resource scope filter with missing value in resource scope labels.");
        }
        if (AuditCommonConstants.ACCOUNT_IDENTIFIER.equals(key) || AuditCommonConstants.ORG_IDENTIFIER.equals(key)
            || AuditCommonConstants.PROJECT_IDENTIFIER.equals(key)) {
          throw new InvalidRequestException(
              String.format("Invalid resource scope filter with key as %s in resource scope labels.", key));
        }
      });
    }
  }

  private void verifyResources(List<ResourceDTO> resources) {
    if (isNotEmpty(resources)) {
      resources.forEach(this::verifyResource);
    }
  }

  private void verifyResource(ResourceDTO resource) {
    if (isEmpty(resource.getType())) {
      throw new InvalidRequestException("Invalid resource filter with missing resource type.");
    }
    Map<String, String> labels = resource.getLabels();
    if (isNotEmpty(labels)) {
      labels.forEach((key, value) -> {
        if (isEmpty(key)) {
          throw new InvalidRequestException("Invalid resource filter with missing key in resource labels.");
        }
        if (isEmpty(value)) {
          throw new InvalidRequestException("Invalid resource filter with missing value in resource labels.");
        }
        if (AuditCommonConstants.TYPE.equals(key) || AuditCommonConstants.IDENTIFIER.equals(key)) {
          throw new InvalidRequestException(
              String.format("Invalid resource scope filter with key as %s in resource labels.", key));
        }
      });
    }
  }

  private void verifyTimeFilter(Long startTime, Long endTime) {
    if (startTime != null && startTime < 0) {
      throw new InvalidRequestException(
          String.format("Invalid time filter with start time %d less than zero.", startTime));
    }
    if (endTime != null && endTime < 0) {
      throw new InvalidRequestException(String.format("Invalid time filter with end time %d less than zero.", endTime));
    }
    if (startTime != null && endTime != null && startTime > endTime) {
      throw new InvalidRequestException(
          String.format("Invalid time filter with start time %d after end time %d.", startTime, endTime));
    }
  }
}
