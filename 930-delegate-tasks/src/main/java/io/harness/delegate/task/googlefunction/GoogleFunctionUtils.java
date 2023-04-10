/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.googlefunction;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDP)
public class GoogleFunctionUtils {
  public static final String ENVIRONMENT_TYPE_GEN_ONE = "GEN_1";
  public static final String GOOGLE_CLOUD_STORAGE_ARTIFACT_FORMAT = "gs://%s/%s";
  public static final String GOOGLE_CLOUD_SOURCE_ARTIFACT_COMMIT_FORMAT =
      "https://source.developers.google.com/projects/%s/repos/%s/revisions/%s/paths/%s";
  public static final String GOOGLE_CLOUD_SOURCE_ARTIFACT_BRANCH_FORMAT =
      "https://source.developers.google.com/projects/%s/repos/%s/moveable-aliases/%s/paths/%s";
  public static final String GOOGLE_CLOUD_SOURCE_ARTIFACT_TAG_FORMAT =
      "https://source.developers.google.com/projects/%s/repos/%s/fixed-aliases/%s/paths/%s";
  public static final String CREATE_FUNCTION_FAILURE_HINT = "Please check for possible issues in: \n "
      + "1. Execution logs under deploy section. \n 2. Validate fields in cloud function manifest. \n "
      + "3. Refer documentation for createFunctionRequest: https://cloud.google.com/functions/docs/reference/rpc/google.cloud.functions.v2#createfunctionrequest \n "
      + "4. Refer troubleshooting documentation: https://cloud.google.com/functions/docs/troubleshooting";

  public static final String UPDATE_FUNCTION_FAILURE_HINT = "Please check for possible issues in: \n "
      + "1. Execution logs under deploy section. \n 2. Validate fields in cloud function manifest. \n "
      + "3. Refer documentation for createFunctionRequest: https://cloud.google.com/functions/docs/reference/rpc/google.cloud.functions.v2#createfunctionrequest \n "
      + "4. Refer troubleshooting documentation: https://cloud.google.com/functions/docs/troubleshooting";

  public static final String DELETE_FUNCTION_FAILURE_HINT = "Please check for possible issues in: \n "
      + "1. Execution logs under deploy section. \n "
      + "2. Refer troubleshooting documentation: https://cloud.google.com/functions/docs/troubleshooting";

  public static final String DELETE_REVISION_FAILURE_HINT = "Please check for possible issues in: \n "
      + "1. Execution logs under deploy section. \n "
      + "2. Refer troubleshooting documentation: https://cloud.google.com/run/docs/troubleshooting";

  public static final String UPDATE_TRAFFIC_FAILURE_HINT = "Please check for possible issues in: \n "
      + "1. Execution logs. \n "
      + "2. Refer troubleshooting documentation: https://cloud.google.com/run/docs/troubleshooting";

  public static final String CREATE_FUNCTION_PARSE_FAILURE_HINT = "Please check for possible issues in: \n "
      + "1. Execution logs under deploy section. \n 2. Validate fields in cloud function manifest. \n "
      + "2. Refer documentation for createFunctionRequest: https://cloud.google.com/functions/docs/reference/rpc/google.cloud.functions.v2#createfunctionrequest \n ";

  public static final String CREATE_FUNCTION_GEN_ONE_PARSE_FAILURE_HINT = "Please check for possible issues in: \n "
      + "1. Execution logs under deploy section. \n 2. Validate fields in cloud function manifest. \n "
      + "2. Refer documentation for createFunctionRequest: https://cloud.google.com/functions/docs/reference/rpc/google.cloud.functions.v1#google.cloud.functions.v1.CreateFunctionRequest \n ";

  public static final String FIELD_MASK_PARSE_FAILURE_HINT = "Please check for possible issues in: \n "
      + "1. Execution logs under deploy section. \n 2. Validate fields in cloud function manifest. \n "
      + "2. Refer documentation for createFunctionRequest: https://developers.google.com/protocol-buffers/docs/reference/google.protobuf#google.protobuf.FieldMask \n ";

  public static final String GET_FUNCTION_FAILURE_HINT = "Please check for possible issues in: \n "
      + "1. Execution logs. \n "
      + "2. Refer troubleshooting documentation: https://cloud.google.com/functions/docs/troubleshooting";

  public static final String GET_FUNCTION_FAILURE_EXPLAIN = "Get Cloud Function API call failed.";

  public static final String GET_FUNCTION_FAILURE_ERROR = "Could not able to retrieve cloud function details.";

  public static final String GET_CLOUD_RUN_SERVICE_FAILURE_HINT = "Please check for possible issues in: \n "
      + "1. Execution logs. \n "
      + "2. Refer troubleshooting documentation: https://cloud.google.com/run/docs/troubleshooting";

  public static final String GET_CLOUD_RUN_SERVICE_FAILURE_EXPLAIN = "Get Cloud-Run Service API call failed.";

  public static final String GET_CLOUD_RUN_SERVICE_FAILURE_ERROR =
      "Could not able to retrieve cloud-run service details.";

  public static final String GET_CLOUD_RUN_REVISION_FAILURE_HINT = "Please check for possible issues in: \n "
      + "1. Execution logs. \n "
      + "2. Refer troubleshooting documentation: https://cloud.google.com/run/docs/troubleshooting";

  public static final String GET_CLOUD_RUN_REVISION_FAILURE_EXPLAIN = "Get Cloud-Run Revision API call failed.";

  public static final String GET_CLOUD_RUN_REVISION_FAILURE_ERROR =
      "Could not able to retrieve cloud-run service revision details.";

  public static final String CREATE_FUNCTION_GEN_ONE_FAILURE_HINT = "Please check for possible issues in: \n "
      + "1. Execution logs under deploy section. \n 2. Validate fields in cloud function manifest. \n "
      + "3. Refer documentation for createFunctionRequest: https://cloud.google.com/functions/docs/reference/rpc/google.cloud.functions.v1#google.cloud.functions.v1.CreateFunctionRequest \n "
      + "4. Refer troubleshooting documentation: https://cloud.google.com/functions/docs/troubleshooting";

  public static final String UPDATE_FUNCTION_GEN_ONE_FAILURE_HINT = "Please check for possible issues in: \n "
      + "1. Execution logs under deploy section. \n 2. Validate fields in cloud function manifest. \n "
      + "3. Refer documentation for createFunctionRequest: https://cloud.google.com/functions/docs/reference/rpc/google.cloud.functions.v1#google.cloud.functions.v1.CreateFunctionRequest \n "
      + "4. Refer troubleshooting documentation: https://cloud.google.com/functions/docs/troubleshooting";
}
