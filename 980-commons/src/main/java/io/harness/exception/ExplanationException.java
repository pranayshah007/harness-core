/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.eraro.ErrorCode.EXPLANATION;
import static io.harness.eraro.Level.INFO;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.EnumSet;

@OwnedBy(HarnessTeam.DX)
public class ExplanationException extends WingsException {
  static final long serialVersionUID = -1494201320263679028L;
  public static String EXPLANATION_IRSA_ROLE_CHECK =
      "IRSA capability for delegate doesn't exist or doesn't have required permissions to describe regions.";
  public static final String EXPLANATION_EMPTY_ACCESS_KEY = "Access Key cannot be empty";
  public static String EXPLANATION_EMPTY_SECRET_KEY = "Secret Key cannot be empty";
  public static String EXPLANATION_EMPTY_CROSS_ACCOUNT_ROLE_ARN_KEY = "Cross account role ARN cannot be empty";
  public static String EXPLANATION_INCORRECT_ACCESS_KEY_SECRET_KEY_PERMISSIONS_KEY =
      "Access Key or Secret Key is invalid. User may not have required permissions to describe regions.";
  public static String EXPLANATION_INVALID_CROSS_ACCOUNT_ROLE_ARN_EXTERNAL_ID_PERMISSIONS_KEY =
      "\nCross account role ARN or External Id is invalid or User / IAM Role on delegate / IRSA role doesn't have permission to perform sts:AssumeRole. \nAssumed Cross account role may not have permissions to describe regions.";
  public static String EXPLANATION_AWS_AM_ROLE_CHECK =
      "IAM role on delegate ec2 doesn't exist or doesn't have required permissions to describe regions.";
  public static String EXPLANATION_LITE_ENGINE_CHECK = "Delegate is not able to connect to the created build farm";
  public static String EXPLANATION_AWS_CLIENT_UNKNOWN_ISSUE = "Seems to encounter unknown AWS client issue";
  public static String EXPLANATION_UNEXPECTED_ERROR = "Unexpected error while handling task";
  public static String IMAGE_TAG_METADATA_NOT_FOUND =
      "Tag not found: Unable to fetch artifact metadata for {%s:%s} using Connector %s and the credentials provided";
  public static String IMAGE_METADATA_NOT_FOUND =
      "Image Not Found: Unable to fetch artifact metadata for {%s} using connector %s with the given credentials.";
  public static String REGISTRY_ACCESS_DENIED =
      "Failed to fetch artifact metadata using connector %s with given credentials.";
  public static String COMMAND_TRIED_FOR_ARTIFACT = "Commands tried %s but no metadata was returned";
  public static String ILLEGAL_IMAGE_FORMAT = "Provided image path [%s] does not satisfy ECR image path format";
  public static String AWS_SM_DECRYPTION_FAILURE =
      "Secrets Manager is unable to decrypt the protected secret text using the given KMS key.";
  public static String AWS_SM_ENCRYPTION_FAILURE =
      "Secrets Manager is unable to encrypt the protected secret content using the given KMS key.";
  public static String AWS_SM_INVALID_TOKEN = "The NextToken value provided is invalid.";
  public static String AWS_SM_INVALID_PARAMETER_NAME = "The name of one or more parameters provided is incorrect.";
  public static String AZURE_SM_VAULT_ENGINE_PERMISSION =
      "The selected azure vault engine do not have apt permissions.";
  public static String AZURE_SM_VAULT_ENGINE_FETCH_ERROR_SUBSCRIPTION_ID =
      "The Subscription ID used is either malformed or Invalid.";
  public static String AZURE_SM_VAULT_ENGINE_FETCH_ERROR =
      "One or more of Client Id, Subscription, Tenant ID and Secret key is/are invalid.";
  public static String INVALID_PARAMETER = "The credentials provided are incorrect.";

  // GIT
  public static String INVALID_GIT_REPO = "Provided repo url is invalid.";
  public static String INVALID_GIT_AUTHORIZATION = "Provided credentials are not authorized.";
  public static String INVALID_GIT_AUTHENTICATION = "Authentication is not supported.";
  public static String GIT_TIME_OUT = "Git connection timed out";

  public static String INVALID_GIT_API_AUTHORIZATION = "Provided api access credentials are not authorized.";

  public static String MALFORMED_GIT_SSH_KEY = "Provided ssh key uses incorrect format.";
  public static String INVALID_GIT_SSH_AUTHORIZATION = "Provided ssh credentials are not authorized.";

  public static String EXPLANATION_MISSING_BRANCH = "Provided branch '%s' doesn't exist in the git repository";
  public static String EXPLANATION_MISSING_REFERENCE = "Provided reference '%s' doesn't exist in the git repository";
  public static String EXPLANATION_GIT_FILE_NOT_FOUND = "Provided file path doesn't exist";
  public static String EXPLANATION_SCM_INVALID_REQUEST =
      "Delegate wasn’t able to download or run SCM service which interacts with Git provider";

  public static String URL_NOT_FOUND = "Provided URL path does not exist.";
  public static String AUTHORIZATION_FAILURE = "Provided credentials are unauthorized.";
  public static String DELEGATE_TO_HOST_SOCKET_CONNECTION_FAILED =
      "Delegate(s) is(are) not able to establish socket connection to host(s)";
  public ExplanationException(String message, Throwable cause) {
    super(message, cause, EXPLANATION, INFO, USER_SRE, null);
    super.excludeReportTarget(EXPLANATION, EnumSet.of(ReportTarget.LOG_SYSTEM));
    super.param("message", message);
  }
}
