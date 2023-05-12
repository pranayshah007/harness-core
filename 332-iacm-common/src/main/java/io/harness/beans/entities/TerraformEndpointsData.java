/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.entities;

import static io.harness.annotations.dev.HarnessTeam.IACM;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(IACM)
public class TerraformEndpointsData {
  String base_url;
  String org_id;
  String project_id;
  String account_id;
  String pipeline_execution_id;
  String pipeline_stage_execution_id;
  String workspace_id;
  String token;
}
