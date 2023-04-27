/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import java.io.IOException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
public class PipelineYamlHelper {
  private static final String VERSION_FIELD_NAME = "version";

  public String getVersion(String yaml, boolean yamlSimplification) {
    return yamlSimplification ? getVersion(yaml) : PipelineVersion.V0;
  }

  public String getVersion(String yaml) {
    if (isEmpty(yaml)) {
      return PipelineVersion.V0;
    }
    String version;
    try {
      YamlField yamlField = YamlUtils.readTree(yaml);
      version = yamlField.getNode().getProperty(VERSION_FIELD_NAME);
    } catch (IOException ioException) {
      log.error("Error while deserializing the Yaml into YamlField", ioException);
      throw new InvalidYamlException(
          String.format("Invalid yaml passed. Error due to - %s", ioException.getMessage()), ioException);
    }
    return isEmpty(version) ? PipelineVersion.V0 : version;
  }
}
