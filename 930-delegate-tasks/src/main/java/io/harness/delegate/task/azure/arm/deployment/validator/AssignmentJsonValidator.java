/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm.deployment.validator;

import static io.harness.azure.model.AzureConstants.ASSIGN_JSON_FILE_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.BLUEPRINT_ID_IS_NOT_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.BLUEPRINT_ID_REGEX;
import static io.harness.azure.model.AzureConstants.RESOURCE_SCOPE_SUBSCRIPTION_PATTERN;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.azure.model.AzureConstants;
import io.harness.azure.model.blueprint.ResourceScopeType;
import io.harness.azure.model.blueprint.assignment.Assignment;
import io.harness.azure.utility.AzureResourceUtility;
import io.harness.delegate.task.azure.common.validator.Validator;
import io.harness.exception.InvalidArgumentsException;
import io.harness.serializer.JsonUtils;

import org.apache.commons.lang3.StringUtils;

public class AssignmentJsonValidator implements Validator<String> {
  @Override
  public void validate(final String assignJson) {
    if (isBlank(assignJson)) {
      throw new InvalidArgumentsException(ASSIGN_JSON_FILE_BLANK_VALIDATION_MSG);
    }

    Assignment assignment = JsonUtils.asObject(assignJson, Assignment.class);

    if (assignment.getIdentity() == null) {
      throw new InvalidArgumentsException(AzureConstants.ASSIGNMENT_IDENTITY_NULL_VALIDATION_MSG);
    }
    if (isBlank(assignment.getLocation())) {
      throw new InvalidArgumentsException(AzureConstants.ASSIGNMENT_LOCATION_BLANK_VALIDATION_MSG);
    }

    Assignment.Properties properties = assignment.getProperties();
    String blueprintId = properties.getBlueprintId();
    if (isBlank(blueprintId)) {
      throw new InvalidArgumentsException(AzureConstants.PROPERTIES_BLUEPRINT_ID_VALIDATION_MSG);
    }

    if (!AzureResourceUtility.isValidResourceScope(blueprintId)) {
      throw new IllegalArgumentException(format(BLUEPRINT_ID_IS_NOT_VALIDATION_MSG, blueprintId));
    }

    if (blueprintId.endsWith("/")) {
      throw new InvalidArgumentsException(format(
          "Not valid value of properties.blueprintId property, ending with '/' character. Required format "
              + "/{resourceScope}/providers/Microsoft.Blueprint/blueprints/{blueprintName}/versions/{versionId}, but found - blueprintId: %s",
          blueprintId));
    }

    if (!BLUEPRINT_ID_REGEX.matcher(blueprintId).matches()) {
      throw new InvalidArgumentsException(format("Not valid value of properties.blueprintId property. Required format "
              + "/{resourceScope}/providers/Microsoft.Blueprint/blueprints/{blueprintName}/versions/{versionId}, but found - blueprintId: %s",
          blueprintId));
    }

    ResourceScopeType resourceScopeType = ResourceScopeType.fromBlueprintId(blueprintId);
    if (resourceScopeType == null) {
      throw new InvalidArgumentsException(
          format("Not found valid resource scope from properties.blueprintId: %s", blueprintId));
    }

    validateAssignmentPropertiesScope(assignment, resourceScopeType);
  }

  private void validateAssignmentPropertiesScope(Assignment assignment, ResourceScopeType resourceScopeType) {
    if (ResourceScopeType.MANAGEMENT_GROUP == resourceScopeType) {
      String scope = assignment.getProperties().getScope();
      if (isBlank(scope)) {
        throw new InvalidArgumentsException(AzureConstants.PROPERTIES_SCOPE_BLANK_VALIDATION_MSG);
      }
      if (!scope.startsWith(RESOURCE_SCOPE_SUBSCRIPTION_PATTERN)) {
        throw new InvalidArgumentsException(format("Not valid value of properties.scope property. Required format "
                + "/subscriptions/{subscriptionId}, but found - scope: %s",
            scope));
      }
      if (scope.endsWith("/")) {
        throw new InvalidArgumentsException(
            format("Not valid value of properties.scope property, ending with '/' character. Required format "
                    + "/subscriptions/{subscriptionId}, but found - scope: %s",
                scope));
      }
    }

    if (ResourceScopeType.SUBSCRIPTION == resourceScopeType) {
      String blueprintId = assignment.getProperties().getBlueprintId();
      String scope = assignment.getProperties().getScope();

      if (StringUtils.isNotBlank(scope)
          && !scope.equals(AzureResourceUtility.getDefinitionResourceScope(blueprintId))) {
        throw new InvalidArgumentsException(format(
            "Not valid value of properties.scope property. Required same value as for blueprint definition resource scope, property.scope: %s",
            scope));
      }
    }
  }
}
