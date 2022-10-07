/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.exception;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_NAME_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.ERROR_INVALID_TENANT_CREDENTIALS;
import static io.harness.azure.model.AzureConstants.LOCATION_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.LOCATION_SET_AT_RESOURCE_GROUP_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.MANAGEMENT_GROUP_ID_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.SUBSCRIPTION_ID_NULL_VALIDATION_MSG;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.exception.ngexception.AzureARMTaskException;
import io.harness.exception.runtime.azure.AzureARMDeploymentException;
import io.harness.exception.runtime.azure.AzureARMManagementScopeException;
import io.harness.exception.runtime.azure.AzureARMResourceGroupScopeException;
import io.harness.exception.runtime.azure.AzureARMRuntimeException;
import io.harness.exception.runtime.azure.AzureARMSubscriptionScopeException;
import io.harness.exception.runtime.azure.AzureARMTenantScopeException;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

@OwnedBy(CDP)

public class AzureARMRuntimeExceptionHandler implements ExceptionHandler {
  private static final String HINT_RESOURCE_GROUP_NAME_NOT_FOUND =
      "Check that the resource group name is defined in the yaml definition";
  private static final String EXPLANATION_RESOURCE_GROUP_NAME_NOT_FOUND = "Unable to find the resource group name";
  private static final String HINT_RESOURCE_GROUP_SUBSCRIPTION_ID_NOT_FOUND =
      "Check that the subscription ID is defined in the yaml definition";
  private static final String EXPLANATION_RESOURCE_GROUP_SUBSCRIPTION_ID_NOT_FOUND =
      "Unable to find the subscription ID";
  private static final String HINT_RESOURCE_GROUP_DEPLOYMENT_NAME_BLANK =
      "The deployment name is required but currently is missing";
  private static final String EXPLANATION_RESOURCE_GROUP_DEPLOYMENT_NAME_BLANK =
      "Contact Harness Support for more information";
  private static final String HINT_RESOURCE_GROUP_NAME_LOCATION_SET =
      "Location is not allowed in ResourceGroups deployments";
  private static final String EXPLANATION_RESOURCE_GROUP_NAME_LOCATION_SET =
      "Contact Harness Support for more information";
  private static final String HINT_SUBSCRIPTION_ID_NULL_NOT_FOUND =
      "Check that the subscription ID is defined in the yaml definition";
  private static final String EXPLANATION_SUBSCRIPTION_ID_NULL_NOT_FOUND = "Unable to find the subscription ID";
  private static final String HINT_SUBSCRIPTION_DEPLOYMENT_NAME_BLANK =
      "The deployment name is required but currently is missing";
  private static final String EXPLANATION_SUBSCRIPTION_DEPLOYMENT_NAME_BLANK =
      "Contact Harness Support for more information";
  private static final String HINT_SUBSCRIPTION_LOCATION_BLANK =
      "Check your location parameter in the yaml definition. It can not be empty or null";
  private static final String EXPLANATION_SUBSCRIPTION_LOCATION_BLANK = "The location parameter can't be empty or null";
  private static final String HINT_MANAGEMENT_GROUP_DEPLOYMENT_NAME_BLANK =
      "The deployment name is required but currently is missing";
  private static final String EXPLANATION_MANAGEMENT_GROUP_DEPLOYMENT_NAME_BLANK =
      "Contact Harness Support for more information";
  private static final String HINT_MANAGEMENT_GROUP_ID_BLANK =
      "Unable to find the management group ID. Check your yaml definition";
  private static final String EXPLANATION_MANAGEMENT_GROUP_ID_BLANK =
      "The management group ID is required but currently is missing";
  private static final String HINT_MANAGEMENT_GROUP_LOCATION_BLANK =
      "Check your location parameter in your yaml definition. It can not be empty or null";
  private static final String EXPLANATION_MANAGEMENT_GROUP_LOCATION_BLANK =
      "The location parameter can't be empty or null";
  private static final String HINT_TENANT_DEPLOYMENT_NAME_BLANK =
      "The deployment name is required but currently is missing";
  private static final String EXPLANATION_TENANT_DEPLOYMENT_NAME_BLANK = "Contact Harness Support for more information";
  private static final String HINT_TENANT_DEPLOYMENT_LOCATION_BLANK =
      "Check your location parameter in your yaml definition. It can not be empty or null";
  private static final String EXPLANATION_TENANT_DEPLOYMENT_LOCATION_BLANK =
      "The location parameter can't be empty or null";
  private static final String DEPLOYMENT_DOES_NOT_EXIST_IN_RESOURCE_GROUP = "does not exist in resource group";

  private static final String HINT_DEPLOYMENT_DOES_NOT_EXIST_SUBSCRIPTION =
      "The deployment doesn't exist in the current subscription. Check if the deployment exists in the resource group";
  private static final String EXPLANATION_DEPLOYMENT_DOES_NOT_EXIST_SUBSCRIPTION =
      "The deployment could have not been created or deleted in the middle of the execution";
  private static final String LOCATION_DOES_NOT_EXIST = "is not a valid region";
  private static final String HINT_LOCATION_NOT_FOUND =
      "Check if the current location in the yaml definition matches with the valid Azure Locations";
  private static final String EXPLANATION_LOCATION_NOT_FOUND =
      "The location provided doesn't match with a valid Azure Location";
  private static final String HINT_TENANT_INVALID_CREDENTIALS =
      "The AZ Directory app can't deploy at tenant level. Try elevating the permissions to be able to perform root deployments";
  private static final String EXPLANATION_TENANT_INVALID_CREDENTIALS =
      "When trying to deploy at tenant level, the application could not proceed with the deployment due to lack of privileges";
  private static final String ERROR_TIMEOUT = "Timed out waiting for executing operation deployment";
  private static final String HINT_TIMEOUT = "Check the defined timeout in the pipeline and in the deployment";
  private static final String EXPLANATION_TIMEOUT = "Timeout while executing the deployment";
  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.<Class<? extends Exception>>builder().add(AzureARMRuntimeException.class).build();
  }
  @Override
  public WingsException handleException(Exception exception) {
    if (exception instanceof AzureARMResourceGroupScopeException) {
      return mapResourceGroupErrorWithHint(exception);
    } else if (exception instanceof AzureARMSubscriptionScopeException) {
      return mapSubscriptionErrorsWithHint(exception);
    } else if (exception instanceof AzureARMManagementScopeException) {
      return mapManagementErrorsWithHints(exception);
    } else if (exception instanceof AzureARMTenantScopeException) {
      return mapTenantErrorsWithHints(exception);
    } else if (exception instanceof AzureARMDeploymentException) {
      return mapDeploymentExceptionWithHints(exception);
    }
    return new AzureARMTaskException(exception.getMessage());
  }

  private WingsException mapDeploymentExceptionWithHints(Exception exception) {
    if (exception.getMessage().contains(ERROR_TIMEOUT)) {
      return NestedExceptionUtils.hintWithExplanationException(
          HINT_TIMEOUT, EXPLANATION_TIMEOUT, new AzureARMTaskException(exception.getMessage()));
    }
    return new AzureARMTaskException(exception.getMessage());
  }

  private WingsException mapResourceGroupErrorWithHint(Exception exception) {
    switch (exception.getMessage()) {
      case RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_RESOURCE_GROUP_NAME_NOT_FOUND,
            EXPLANATION_RESOURCE_GROUP_NAME_NOT_FOUND, new AzureARMTaskException(exception.getMessage()));
      case SUBSCRIPTION_ID_NULL_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_RESOURCE_GROUP_SUBSCRIPTION_ID_NOT_FOUND,
            EXPLANATION_RESOURCE_GROUP_SUBSCRIPTION_ID_NOT_FOUND, new AzureARMTaskException(exception.getMessage()));
      case DEPLOYMENT_NAME_BLANK_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_RESOURCE_GROUP_DEPLOYMENT_NAME_BLANK,
            EXPLANATION_RESOURCE_GROUP_DEPLOYMENT_NAME_BLANK, new AzureARMTaskException(exception.getMessage()));
      case LOCATION_SET_AT_RESOURCE_GROUP_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_RESOURCE_GROUP_NAME_LOCATION_SET,
            EXPLANATION_RESOURCE_GROUP_NAME_LOCATION_SET, new AzureARMTaskException(exception.getMessage()));
      default:
        if (exception.getMessage().contains(DEPLOYMENT_DOES_NOT_EXIST_IN_RESOURCE_GROUP)) {
          return NestedExceptionUtils.hintWithExplanationException(HINT_DEPLOYMENT_DOES_NOT_EXIST_SUBSCRIPTION,
              EXPLANATION_DEPLOYMENT_DOES_NOT_EXIST_SUBSCRIPTION, new AzureARMTaskException(exception.getMessage()));
        }
        return new AzureARMTaskException(exception.getMessage());
    }
  }

  private WingsException mapSubscriptionErrorsWithHint(Exception exception) {
    switch (exception.getMessage()) {
      case SUBSCRIPTION_ID_NULL_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_SUBSCRIPTION_ID_NULL_NOT_FOUND,
            EXPLANATION_SUBSCRIPTION_ID_NULL_NOT_FOUND, new AzureARMTaskException(exception.getMessage()));
      case DEPLOYMENT_NAME_BLANK_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_SUBSCRIPTION_DEPLOYMENT_NAME_BLANK,
            EXPLANATION_SUBSCRIPTION_DEPLOYMENT_NAME_BLANK, new AzureARMTaskException(exception.getMessage()));
      case LOCATION_BLANK_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_SUBSCRIPTION_LOCATION_BLANK,
            EXPLANATION_SUBSCRIPTION_LOCATION_BLANK, new AzureARMTaskException(exception.getMessage()));
      default:
        if (exception.getMessage().contains(LOCATION_DOES_NOT_EXIST)) {
          return NestedExceptionUtils.hintWithExplanationException(HINT_LOCATION_NOT_FOUND,
              EXPLANATION_LOCATION_NOT_FOUND, new AzureARMTaskException(exception.getMessage()));
        }
        return new AzureARMTaskException(exception.getMessage());
    }
  }

  private WingsException mapManagementErrorsWithHints(Exception exception) {
    switch (exception.getMessage()) {
      case DEPLOYMENT_NAME_BLANK_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_MANAGEMENT_GROUP_DEPLOYMENT_NAME_BLANK,
            EXPLANATION_MANAGEMENT_GROUP_DEPLOYMENT_NAME_BLANK, new AzureARMTaskException(exception.getMessage()));
      case MANAGEMENT_GROUP_ID_BLANK_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_MANAGEMENT_GROUP_ID_BLANK,
            EXPLANATION_MANAGEMENT_GROUP_ID_BLANK, new AzureARMTaskException(exception.getMessage()));
      case LOCATION_BLANK_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_MANAGEMENT_GROUP_LOCATION_BLANK,
            EXPLANATION_MANAGEMENT_GROUP_LOCATION_BLANK, new AzureARMTaskException(exception.getMessage()));
      default:
        if (exception.getMessage().contains(LOCATION_DOES_NOT_EXIST)) {
          return NestedExceptionUtils.hintWithExplanationException(HINT_LOCATION_NOT_FOUND,
              EXPLANATION_LOCATION_NOT_FOUND, new AzureARMTaskException(exception.getMessage()));
        }
        return new AzureARMTaskException(exception.getMessage());
    }
  }

  private WingsException mapTenantErrorsWithHints(Exception exception) {
    switch (exception.getMessage()) {
      case DEPLOYMENT_NAME_BLANK_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_TENANT_DEPLOYMENT_NAME_BLANK,
            EXPLANATION_TENANT_DEPLOYMENT_NAME_BLANK, new AzureARMTaskException(exception.getMessage()));
      case LOCATION_BLANK_VALIDATION_MSG:
        return NestedExceptionUtils.hintWithExplanationException(HINT_TENANT_DEPLOYMENT_LOCATION_BLANK,
            EXPLANATION_TENANT_DEPLOYMENT_LOCATION_BLANK, new AzureARMTaskException(exception.getMessage()));
      case ERROR_INVALID_TENANT_CREDENTIALS:
        return NestedExceptionUtils.hintWithExplanationException(HINT_TENANT_INVALID_CREDENTIALS,
            EXPLANATION_TENANT_INVALID_CREDENTIALS, new AzureARMTaskException(exception.getMessage()));
      default:
        return new AzureARMTaskException(exception.getMessage());
    }
  }
}
