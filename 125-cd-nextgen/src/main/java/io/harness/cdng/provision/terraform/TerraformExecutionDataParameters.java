/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.provision.terraform.TerraformExecutionDataParameters")
public class TerraformExecutionDataParameters {
  ParameterField<String> workspace;
  TerraformConfigFilesWrapper configFiles;
  LinkedHashMap<String, TerraformVarFile> varFiles;
  TerraformBackendConfig backendConfig;
  ParameterField<List<String>> targets;
  Map<String, Object> environmentVariables;
  ParameterField<Boolean> isTerraformCloudCli;
}
