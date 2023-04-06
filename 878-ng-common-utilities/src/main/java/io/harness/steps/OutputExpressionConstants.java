/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PIPELINE)
public interface OutputExpressionConstants {
  String K8S_INFRA_DELEGATE_CONFIG_OUTPUT_NAME = "K8S_INFRA_DELEGATE_CONFIG_OUTPUT";
  String SSH_INFRA_DELEGATE_CONFIG_OUTPUT_NAME = "SSH_INFRA_DELEGATE_CONFIG_OUTPUT";
  String WINRM_INFRA_DELEGATE_CONFIG_OUTPUT_NAME = "WINRM_INFRA_DELEGATE_CONFIG_OUTPUT";
  String EXECUTION_INFO_KEY_OUTPUT_NAME = "EXECUTION_INFO_KEY_OUTPUT_NAME";
  String ENVIRONMENT = "env";
  String ENVIRONMENT_GROUP = "envgroup";
  String OUTPUT = "output";
  String INFRA = "infra";
  String INSTANCES = "instances";
  String PROVISIONER = "provisioner";
}
