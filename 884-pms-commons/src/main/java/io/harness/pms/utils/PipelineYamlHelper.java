/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import java.io.IOException;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PipelineYamlHelper {
  private static final String VERSION_FIELD_NAME = "version";

  public String getVersion(String yaml, boolean yamlSimplification) {
    String version;
    try {
      YamlField yamlField = YamlUtils.readTree(yaml);
      version = yamlField.getNode().getProperty(VERSION_FIELD_NAME);
    } catch (IOException ioException) {
      throw new InvalidRequestException("Invalid yaml passed.");
    }
    return version != null && yamlSimplification ? version : PipelineVersion.V0;
  }
}
