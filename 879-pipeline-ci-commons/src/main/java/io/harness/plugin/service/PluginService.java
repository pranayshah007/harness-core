/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plugin.service;

import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.yaml.core.variables.SecretNGVariable;

import java.util.Map;

public interface PluginService {
  Map<String, String> getPluginCompatibleEnvVariables(PluginCompatibleStep stepInfo, String identifier, long timeout,
      Ambiance ambiance, StageInfraDetails.Type infraType, boolean isMandatory, boolean isContainerizedPlugin);

  Map<String, SecretNGVariable> getPluginCompatibleSecretVars(PluginCompatibleStep step);
}
