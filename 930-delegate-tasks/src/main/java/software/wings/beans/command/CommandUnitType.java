/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Created by peeyushaggarwal on 6/2/16.
 */
@OwnedBy(CDP)
@TargetModule(_930_DELEGATE_TASKS)
public enum CommandUnitType {
  EXEC("Exec"),
  SCP("Copy"),
  COPY_CONFIGS("Copy Configs"),
  COMMAND("Command"),
  SETUP_ENV("Setup Env"),
  DOCKER_START("Docker Start"),
  DOCKER_STOP("Docker Stop"),
  PROCESS_CHECK_RUNNING("Process Running"),
  PROCESS_CHECK_STOPPED("Process Stopped"),
  PORT_CHECK_CLEARED("Port Cleared"),
  PORT_CHECK_LISTENING("Port Listening"),
  CODE_DEPLOY("Amazon Code Deploy"),
  AWS_LAMBDA("AWS Lambda"),
  AWS_AMI("AWS AMI"),
  ECS_SETUP("Setup ECS Service"),
  ECS_SETUP_DAEMON_SCHEDULING_TYPE("Setup ECS Daemon Service"),
  KUBERNETES_SETUP("Setup Kubernetes Service"),
  RESIZE("Resize ECS Service"),
  RESIZE_KUBERNETES("Resize Kubernetes Service"),
  DOWNLOAD_ARTIFACT("Download Artifact"),
  K8S_DUMMY("K8s Command Unit"),
  RANCHER_DUMMY("Rancher Command Unit"),
  SPOTINST_DUMMY("Spotinst Command Unit"),
  HELM_DUMMY("Helm Command Unit"),
  PCF_DUMMY("PCF Command Unit"),
  AZURE_VMSS_DUMMY("Azure VMSS Command Unit"),
  AZURE_WEBAPP("Azure WebApp Command Unit"),
  FETCH_INSTANCES_DUMMY("Fetch Instances"),
  AZURE_ARM("Azure ARM Command Unit"),
  TERRAGRUNT_PROVISION("Terragrunt Provision");

  @JsonIgnore private String name;

  /**
   * Instantiates a new command unit type.
   *
   * @param name
   */
  CommandUnitType(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }
}
