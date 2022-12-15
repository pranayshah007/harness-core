/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.exception;

import static io.harness.azure.model.AzureConstants.ARTIFACT_JSON_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.ARTIFACT_NAME_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.ASSIGNMENT_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.ASSIGNMENT_IDENTITY_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.ASSIGNMENT_JSON_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.ASSIGNMENT_LOCATION_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.ASSIGNMENT_NAME_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.ASSIGNMENT_SUBSCRIPTION_ID_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.ASSIGN_JSON_FILE_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.BLUEPRINT_ID_IS_NOT_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.BLUEPRINT_JSON_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.BLUEPRINT_JSON_FILE_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.BLUEPRINT_NAME_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.PROPERTIES_BLUEPRINT_ID_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.PROPERTIES_SCOPE_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.RESOURCE_SCOPE_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.VERSION_ID_BLANK_VALIDATION_MSG;

import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.exception.ngexception.AzureBPTaskException;
import io.harness.exception.runtime.azure.AzureBPDeploymentException;
import io.harness.exception.runtime.azure.AzureBPRuntimeException;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

public class AzureBPRuntimeExceptionHandler implements ExceptionHandler {
  private static final String ERROR_BP_NO_NAME_FOUND_IN_TEMPLATE =
      "Not match blueprint name found in blueprint json file with properties.blueprintId property in assign json file";
  private static final String HINT_NO_NAME_FOUND_IN_TEMPLATE =
      "Check that the assign.json file has blueprintId property";
  private static final String EXPLANATION_NO_NAME_FOUND_IN_TEMPLATE = "The blueprintId is missing.";
  private static final String HINT_RESOURCE_SCOPE_BLANK =
      "Check that the blueprintId in assign.json follows the pattern for the resource scope as `/subscription/<subscription_id>/`";
  private static final String EXPLANATION_RESOURCE_SCOPE_BLANK =
      "The subscription id in the blueprintId is empty or doesn't match with the pattern expected";
  private static final String HINT_VERSION_ID_BLANK =
      "Check that the blueprintId in assign.json is not missing the version number";
  private static final String EXPLANATION_VERSION_ID_BLANK = "The version id in the blueprintId is invalid";
  private static final String HINT_ASSIGNMENT_NAME_BLANK = "Check that the assignment name is not empty";
  private static final String EXPLANATION_ASSIGNMENT_NAME_BLANK = "The assignment name is invalid";
  private static final String HINT_ASSIGNMENT_JSON_BLANK = "Check the content of the assign.json file";
  private static final String EXPLANATION_ASSIGNMENT_JSON_BLANK = "The assign.json file is empty";
  private static final String HINT_BLUEPRINT_JSON_BLANK = "Check the content of the blueprint.json file";
  private static final String EXPLANATION_BLUEPRINT_JSON_BLANK = "The blueprint.json file is empty";
  ;
  private static final String HINT_ARTIFACT_JSON_BLANK = "Check the content of the artifact files";
  private static final String EXPLANATION_ARTIFACT_JSON_BLANK = "No valid artifact files found";
  private static final String HINT_ARTIFACT_NAME_BLANK = "The artifact name is empty";
  private static final String EXPLANATION_ARTIFACT_NAME_BLANK = "Name properly the artifact files";
  private static final String HINT_ASSIGNMENT_IDENTITY_BLANK =
      "Check that the identity property in the assign.json file is not empty";
  private static final String EXPLANATION_IDENTITY_JSON_BLANK = "No valid identity type was found";
  private static final String HINT_ASSIGNMENT_LOCATION_BLANK =
      "Check that the location property in the assign.json file is not empty";
  private static final String EXPLANATION_LOCATION_JSON_BLANK = "No valid location was found";
  private static final String HINT_BLUEPRINT_ID_RESOURCE_SCOPE_VALID =
      "Check that the resource scope follows the `/providers/Microsoft.Management/managementGroups/<id>` or `/subscriptions/<id>` pattern";
  private static final String EXPLANATION_BLUEPRINT_ID_RESOURCE_SCOPE_VALID =
      "The pattern for the scope in the blueprintId is invalid";
  private static final String ERROR_NOT_VALID_RESOURCE_SCOPE =
      "Not found valid resource scope from properties.blueprintId";
  private static final String HINT_BLUEPRINT_ID_MANAGEMENT_SCOPE_VALID =
      "Check that the management id is in the assign.json file";
  private static final String EXPLANATION_BLUEPRINT_ID_MANAGEMENT_SCOPE_VALID = "Invalid management scope id";
  private static final String ERROR_INVALID_BLUEPRINT_JSON_FILE = "Invalid Blueprint JSON file";
  private static final String ERROR_INVALID_ARTIFACT_JSON_FILE = "Invalid Artifact JSON";
  private static final String HINT_INVALID_BLUEPRINT_JSON_FORMAT =
      "Check that the blueprint.json file has a valid json format";
  private static final String EXPLANATION_INVALID_BLUEPRINT_JSON_FORMAT = "Invalid blueprint.json file";
  private static final String HINT_INVALID_ARTIFACT_JSON_FORMAT =
      "Check that the artifacts.json files has a valid json format";
  private static final String EXPLANATION_INVALID_ARTIFACT_JSON_FORMAT = "Invalid artifacts.json files";
  private static final String ERROR_INVALID_BLUEPRINT_PARAMETERS = "Parameter blueprintId is not valid";
  private static final String ERROR_INVALID_BLUEPRINT_ID_PARAMETERS =
      "Not valid value of properties.blueprintId property";
  private static final String HINT_BLUEPRINT_ID_BLANK = "Check that the blueprintID in the assign.json is valid";
  private static final String EXPLANATION_BLUEPRINT_ID_BLANK = "BlueprintID is not valid";

  @Override
  public WingsException handleException(Exception exception) {
    if (exception instanceof AzureBPDeploymentException) {
      return mapBPErrorWithHint(exception);
    }
    return new AzureBPTaskException(exception.getMessage());
  }

  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.<Class<? extends Exception>>builder().add(AzureBPRuntimeException.class).build();
  }

  private WingsException mapBPErrorWithHint(Exception exception) {
    switch (exception.getMessage()) {
      case RESOURCE_SCOPE_BLANK_VALIDATION_MSG:
      case RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_RESOURCE_SCOPE_BLANK,
            EXPLANATION_RESOURCE_SCOPE_BLANK, new AzureBPTaskException(exception.getMessage()));
      case BLUEPRINT_NAME_BLANK_VALIDATION_MSG:
      case PROPERTIES_BLUEPRINT_ID_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(
            HINT_BLUEPRINT_ID_BLANK, EXPLANATION_BLUEPRINT_ID_BLANK, new AzureBPTaskException(exception.getMessage()));
      case BLUEPRINT_ID_IS_NOT_VALIDATION_MSG:
      case ASSIGNMENT_SUBSCRIPTION_ID_BLANK_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_BLUEPRINT_ID_RESOURCE_SCOPE_VALID,
            EXPLANATION_BLUEPRINT_ID_RESOURCE_SCOPE_VALID, new AzureBPTaskException(exception.getMessage()));
      case PROPERTIES_SCOPE_BLANK_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_BLUEPRINT_ID_MANAGEMENT_SCOPE_VALID,
            EXPLANATION_BLUEPRINT_ID_MANAGEMENT_SCOPE_VALID, new AzureBPTaskException(exception.getMessage()));
      case BLUEPRINT_JSON_BLANK_VALIDATION_MSG:
      case BLUEPRINT_JSON_FILE_BLANK_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_BLUEPRINT_JSON_BLANK,
            EXPLANATION_BLUEPRINT_JSON_BLANK, new AzureBPTaskException(exception.getMessage()));
      case VERSION_ID_BLANK_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(
            HINT_VERSION_ID_BLANK, EXPLANATION_VERSION_ID_BLANK, new AzureBPTaskException(exception.getMessage()));
      case ASSIGNMENT_NAME_BLANK_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_ASSIGNMENT_NAME_BLANK,
            EXPLANATION_ASSIGNMENT_NAME_BLANK, new AzureBPTaskException(exception.getMessage()));
      case ASSIGNMENT_JSON_BLANK_VALIDATION_MSG:
      case ASSIGN_JSON_FILE_BLANK_VALIDATION_MSG:
      case ASSIGNMENT_BLANK_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_ASSIGNMENT_JSON_BLANK,
            EXPLANATION_ASSIGNMENT_JSON_BLANK, new AzureBPTaskException(exception.getMessage()));
      case ARTIFACT_JSON_BLANK_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_ARTIFACT_JSON_BLANK,
            EXPLANATION_ARTIFACT_JSON_BLANK, new AzureBPTaskException(exception.getMessage()));
      case ARTIFACT_NAME_BLANK_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_ARTIFACT_NAME_BLANK,
            EXPLANATION_ARTIFACT_NAME_BLANK, new AzureBPTaskException(exception.getMessage()));
      case ASSIGNMENT_IDENTITY_NULL_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_ASSIGNMENT_IDENTITY_BLANK,
            EXPLANATION_IDENTITY_JSON_BLANK, new AzureBPTaskException(exception.getMessage()));
      case ASSIGNMENT_LOCATION_BLANK_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_ASSIGNMENT_LOCATION_BLANK,
            EXPLANATION_LOCATION_JSON_BLANK, new AzureBPTaskException(exception.getMessage()));

      default:
        if (exception.getMessage().contains(ERROR_BP_NO_NAME_FOUND_IN_TEMPLATE)) {
          return NestedExceptionUtils.hintWithExplanationException(HINT_NO_NAME_FOUND_IN_TEMPLATE,
              EXPLANATION_NO_NAME_FOUND_IN_TEMPLATE, new AzureBPTaskException(exception.getMessage()));
        } else if (exception.getMessage().contains(ERROR_NOT_VALID_RESOURCE_SCOPE)) {
          return NestedExceptionUtils.hintWithExplanationException(HINT_RESOURCE_SCOPE_BLANK,
              EXPLANATION_RESOURCE_SCOPE_BLANK, new AzureBPTaskException(exception.getMessage()));
        } else if (exception.getMessage().contains(ERROR_INVALID_BLUEPRINT_JSON_FILE)) {
          return NestedExceptionUtils.hintWithExplanationException(HINT_INVALID_BLUEPRINT_JSON_FORMAT,
              EXPLANATION_INVALID_BLUEPRINT_JSON_FORMAT, new AzureBPTaskException(exception.getMessage()));
        } else if (exception.getMessage().contains(ERROR_INVALID_ARTIFACT_JSON_FILE)) {
          return NestedExceptionUtils.hintWithExplanationException(HINT_INVALID_ARTIFACT_JSON_FORMAT,
              EXPLANATION_INVALID_ARTIFACT_JSON_FORMAT, new AzureBPTaskException(exception.getMessage()));
        } else if (exception.getMessage().contains(ERROR_INVALID_BLUEPRINT_PARAMETERS)
            || (exception.getMessage().contains(ERROR_INVALID_BLUEPRINT_ID_PARAMETERS))) {
          return NestedExceptionUtils.hintWithExplanationException(HINT_BLUEPRINT_ID_BLANK,
              EXPLANATION_BLUEPRINT_ID_BLANK, new AzureBPTaskException(exception.getMessage()));
        }
    }
    return new AzureBPTaskException(exception.getMessage());
  }
}
