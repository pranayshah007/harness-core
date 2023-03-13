/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.terraformcloud;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.terraformcloud.model.ApplyData;
import io.harness.terraformcloud.model.OrganizationData;
import io.harness.terraformcloud.model.PlanData;
import io.harness.terraformcloud.model.PolicyCheckData;
import io.harness.terraformcloud.model.RunActionRequest;
import io.harness.terraformcloud.model.RunData;
import io.harness.terraformcloud.model.RunRequest;
import io.harness.terraformcloud.model.StateVersionOutputData;
import io.harness.terraformcloud.model.TerraformCloudResponse;
import io.harness.terraformcloud.model.WorkspaceData;

import java.io.IOException;
import java.util.List;

@OwnedBy(CDP)
public interface TerraformCloudClient {
  TerraformCloudResponse<List<OrganizationData>> listOrganizations(String url, String token, int page)
      throws IOException;

  TerraformCloudResponse<List<WorkspaceData>> listWorkspaces(String url, String token, String organization, int page)
      throws IOException;

  TerraformCloudResponse<RunData> createRun(String url, String token, RunRequest request) throws IOException;

  TerraformCloudResponse<RunData> getRun(String url, String token, String runId) throws IOException;

  void applyRun(String url, String token, String runId, RunActionRequest request) throws IOException;

  void discardRun(String url, String token, String runId, RunActionRequest request) throws IOException;

  void forceExecuteRun(String url, String token, String runId) throws IOException;

  TerraformCloudResponse<PlanData> getPlan(String url, String token, String planId) throws IOException;

  String getPlanJsonOutput(String url, String token, String planId) throws IOException;

  TerraformCloudResponse<ApplyData> getApply(String url, String token, String applyId) throws IOException;

  TerraformCloudResponse<List<PolicyCheckData>> listPolicyChecks(String url, String token, String runId, int page)
      throws IOException;

  String getPolicyCheckOutput(String url, String token, String policyCheckId) throws IOException;

  TerraformCloudResponse<List<StateVersionOutputData>> getStateVersionOutputs(
      String url, String token, String stateVersionId, int page) throws IOException;

  String getLogs(String url, int offset, int limit) throws IOException;

  void overridePolicyChecks(String url, String token, String policyChecksId) throws IOException;

  TerraformCloudResponse<List<RunData>> getAppliedRuns(String url, String token, String workspaceId) throws IOException;
}
