/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import java.io.File;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@OwnedBy(CDP)
@Data
@Builder
public class TerragruntCliCommandRequestParams {
  private String directory;
  private String backendConfigFilePath;
  private String commandUnitName;
  private Long timeoutInMillis;
  private Map<String, String> envVars;
  private String targetArgs;
  private String varParams;
  private String uiLogs;
  private String workspaceCommand;
  private File tfOutputsFile;
  private ActivityLogOutputStream activityLogOutputStream;
  private PlanLogOutputStream planLogOutputStream;
  private PlanJsonLogOutputStream planJsonLogOutputStream;
  private ErrorLogOutputStream errorLogOutputStream;
}
