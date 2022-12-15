/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.terragrunt.v2;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_INFO_COMMAND;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_INIT_COMMAND_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_PLAN_COMMAND_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_PLAN_DESTROY_COMMAND_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_REFRESH_COMMAND_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_RUN_ALL_PLAN_COMMAND_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_RUN_ALL_PLAN_DESTROY_COMMAND_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_RUN_ALL_REFRESH_COMMAND_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_RUN_ALL_SHOW_COMMAND_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_SHOW_COMMAND_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_VERSION_COMMAND;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_WORKSPACE_LIST_COMMAND_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_WORKSPACE_NEW_COMMAND_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_WORKSPACE_SELECT_COMMAND_FORMAT;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;

import java.io.File;
import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class TerragruntCommandUtils {
  public String init(String backendConfigFilePath) {
    File backendConfigFile = new File(backendConfigFilePath);
    return format(TERRAGRUNT_INIT_COMMAND_FORMAT,
        backendConfigFile.exists() ? format(" -backend-config=%s", backendConfigFilePath) : "");
  }

  public String refresh(String targetArgs, String varParams) {
    return format(TERRAGRUNT_REFRESH_COMMAND_FORMAT, targetArgs.trim(), varParams.trim());
  }

  public String runAllRefresh(String targetArgs, String varParams) {
    return format(TERRAGRUNT_RUN_ALL_REFRESH_COMMAND_FORMAT, targetArgs.trim(), varParams.trim());
  }

  public String plan(String targetArgs, String varParams, boolean destroy) {
    return format(destroy ? TERRAGRUNT_PLAN_DESTROY_COMMAND_FORMAT : TERRAGRUNT_PLAN_COMMAND_FORMAT, targetArgs.trim(),
        varParams.trim());
  }

  public String runAllPlan(String targetArgs, String varParams, boolean destroy) {
    return format(destroy ? TERRAGRUNT_RUN_ALL_PLAN_DESTROY_COMMAND_FORMAT : TERRAGRUNT_RUN_ALL_PLAN_COMMAND_FORMAT,
        targetArgs.trim(), varParams.trim());
  }

  public String show(boolean json, String planName) {
    return format(TERRAGRUNT_SHOW_COMMAND_FORMAT, json ? "-json " + planName : planName);
  }

  public String runAllShow(boolean json, String planName) {
    return format(TERRAGRUNT_RUN_ALL_SHOW_COMMAND_FORMAT, json ? "-json " + planName : planName);
  }

  public String info() {
    return TERRAGRUNT_INFO_COMMAND;
  }

  public String version() {
    return TERRAGRUNT_VERSION_COMMAND;
  }

  public String workspaceList() {
    return TERRAGRUNT_WORKSPACE_LIST_COMMAND_FORMAT;
  }

  public String workspaceNew(String workspace) {
    return format(TERRAGRUNT_WORKSPACE_NEW_COMMAND_FORMAT, workspace);
  }

  public String workspaceSelect(String workspace) {
    return format(TERRAGRUNT_WORKSPACE_SELECT_COMMAND_FORMAT, workspace);
  }
}
