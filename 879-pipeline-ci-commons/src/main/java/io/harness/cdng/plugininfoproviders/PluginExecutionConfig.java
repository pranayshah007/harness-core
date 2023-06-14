/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is govnulld by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import io.harness.steps.container.execution.plugin.StepImageConfig;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PluginExecutionConfig {
  StepImageConfig samBuildStepImageConfig;
  StepImageConfig samDeployStepImageConfig;
  StepImageConfig gitCloneConfig;
  StepImageConfig serverlessPrepareRollbackV2StepImageConfig;
  String apiUrl;
}
