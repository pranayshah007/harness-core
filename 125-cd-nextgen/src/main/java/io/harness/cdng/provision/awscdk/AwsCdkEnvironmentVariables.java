/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.awscdk;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDP)
public interface AwsCdkEnvironmentVariables {
  String PLUGIN_AWS_CDK_APP_PATH = "PLUGIN_AWS_CDK_APP_PATH";
  String PLUGIN_AWS_CDK_COMMAND_OPTIONS = "PLUGIN_AWS_CDK_COMMAND_OPTIONS";
  String PLUGIN_AWS_CDK_EXPORT_BOOTSTRAP_TEMPLATE = "PLUGIN_AWS_CDK_EXPORT_BOOTSTRAP_TEMPLATE";
  String PLUGIN_AWS_CDK_EXPORT_SYNTH_TEMPLATE = "PLUGIN_AWS_CDK_EXPORT_SYNTH_TEMPLATE";
  String PLUGIN_AWS_CDK_STACK_NAMES = "PLUGIN_AWS_CDK_STACK_NAMES";
}
