/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.configfile.mapper;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.configfile.ConfigFileAttributes;
import io.harness.cdng.configfile.ConfigFileOutcome;
import io.harness.cdng.configfile.steps.ConfigFileStepParameters;
import io.harness.cdng.configfile.validator.ConfigFileOutcomeValidator;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.common.ParameterFieldHelper;

@OwnedBy(CDP)
public final class ConfigFileOutcomeMapper {
  private ConfigFileOutcomeMapper() {}

  public static ConfigFileOutcome toConfigFileOutcome(
      ConfigFileStepParameters configFileStepParameters, ConfigFileAttributes configFileAttributes) {
    StoreConfig store = ParameterFieldHelper.getParameterFieldValue(configFileAttributes.getStore()).getSpec();
    ConfigFileOutcomeValidator.validateStore(configFileStepParameters.getIdentifier(), store, true);
    return ConfigFileOutcome.builder()
        .identifier(configFileStepParameters.getIdentifier())
        .store(store)
        .order(configFileStepParameters.getOrder())
        .build();
  }
}
