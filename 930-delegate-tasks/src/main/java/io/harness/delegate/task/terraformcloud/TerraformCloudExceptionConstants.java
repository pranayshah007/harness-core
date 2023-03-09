/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraformcloud;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDP)
public final class TerraformCloudExceptionConstants {
  public static final class Hints {
    public static final String PLEASE_CONTACT_HARNESS = "Please contact Harness Support Team";
    public static final String PLEASE_CHECK_PLAN = "Please check Plan and Policy Checks";
    public static final String PLEASE_CHECK_TFC_CONFIG = "Please check Terraform Cloud configuration";
    public static final String PLEASE_CHECK_POLICY = "Please check status of policy check: %s";
    public static final String PLEASE_CHECK_RUN = "Please check status of run: %s";
    public static final String POLICY_OVERRIDE_HINT =
        "To override policies select option [ Continue on Soft-Mandatory Policy evaluation result ] in step configuration";
  }
  public static final class Explanation {
    public static final String COULD_NOT_GET_WORKSPACE = "Could not get workspaces from: %s for organization: %s";
    public static final String COULD_NOT_GET_ORG = "Could not get organizations from: %s";
    public static final String COULD_NOT_CREATE_RUN = "Error happened during a run creation";
    public static final String COULD_NOT_GET_PLAN = "Could not get a plan: %s";
    public static final String APPLY_UNREACHABLE_ERROR = "Apply failed for run id: %s . Status: %s";
    public static final String COULD_NOT_GET_APPLY_LOGS = "Could not get apply logs for apply: %s";
    public static final String COULD_NOT_UPLOAD_FILE = "Could not upload json file: %s to the bucket: %s";
    public static final String COULD_NOT_FIND_RELATIONSHIP = "Could not find relationship: %s";
    public static final String RELATIONSHIP_DATA_EMPTY = "Relationship: %s data is empty";
    public static final String COULD_NOT_GET_APPLY_OUTPUT = "Could not get apply output";
    public static final String COULD_NOT_GET_PLAN_JSON = "Could not get json plan";
    public static final String COULD_NOT_APPLY = "Could not apply run";
    public static final String COULD_NOT_GET_RUN = "Could not get run";
    public static final String COULD_NOT_GET_POLICY_OUTPUT = "Could not get policy output";
    public static final String COULD_NOT_GET_POLICY_DATA = "Could not get policy check data";
    public static final String COULD_NOT_OVERRIDE_POLICY = "Could not override policy";
    public static final String COULD_NOT_GET_LAST_APPLIED = "Could not get applied runs from workspace: %s";
    public static final String APPLY_ERROR_MESSAGE = "Apply can't be done when run is in status %s";
    public static final String COULD_NOT_CONVERT_POLICIES = "Couldn't convert policy checks into json format";
    public static final String POLICY_OVERRIDE_ERROR_MESSAGE = "Policy check failed and not overridden";
    public static final String WORKSPACE_OR_RUN_MUST_BE_PROVIDED =
        "Workspace or run id must be provided to fetch last applied run";
  }

  public static final class Message {
    public static final String ERROR_GETTING_WORKSPACE = "Failed to get workspaces";
    public static final String ERROR_GETTING_ORG = "Failed to get organizations";
    public static final String ERROR_CREATING_RUN = "Failed to create a run";
    public static final String ERROR_STREAMING_PLAN_LOGS = "Failed to stream Plan logs";
    public static final String ERROR_APPLYING = "Apply is unreachable";
    public static final String ERROR_STREAMING_APPLY_LOGS = "Failed to stream Apply logs";
    public static final String CANT_PROCESS_TFC_RESPONSE = "Can't process response from Terraform Cloud";
    public static final String ERROR_GETTING_APPLY_OUTPUT = "Failed to get Apply output";
    public static final String ERROR_GETTING_JSON_PLAN = "Failed to get JSON plan";
    public static final String ERROR_APPLY = "Failed to apply run or to stream apply logs";
    public static final String ERROR_GETTING_RUN = "Failed to get a Run plan";
    public static final String ERROR_GETTING_POLICY_OUTPUT = "Failed to get policy check output";
    public static final String ERROR_GETTING_POLICY_DATA = "Failed to get policy data";
    public static final String ERROR_OVERRIDE_POLICY = "Failed to override policy";
    public static final String ERROR_GETTING_APPLIED_POLICIES = "Failed to get applied polices";
    public static final String ERROR_TO_APPLY = "Failed to do apply";
    public static final String MISSING_WORKSPACE_ID = "Workspace id is missing";
  }
}
