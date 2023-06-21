/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import io.harness.pms.pipeline.yaml.SimplifiedPipelineYaml;
import io.harness.pms.yaml.YamlUtils;

import java.io.IOException;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SimplifiedPipelineYamlHelper {
  public SimplifiedPipelineYaml getSimplifiedPipelineYaml(String yaml) throws IOException {
    return YamlUtils.read(yaml, SimplifiedPipelineYaml.class);
  }
}
