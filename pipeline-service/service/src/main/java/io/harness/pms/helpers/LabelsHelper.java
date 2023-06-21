/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import io.harness.pms.pipeline.yaml.SimplifiedPipelineYaml;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.yaml.core.NGLabel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j;

@UtilityClass
@Log4j
public class LabelsHelper {
  public List<NGLabel> getLabels(String yaml, String version) {
    if (PipelineVersion.V1.equals(version)) {
      List<NGLabel> ngLabelList = new ArrayList<>();
      try {
        SimplifiedPipelineYaml simplifiedPipelineYaml = SimplifiedPipelineYamlHelper.getSimplifiedPipelineYaml(yaml);
        Map<String, String> labels = simplifiedPipelineYaml.getLabels();
        if (labels != null) {
          labels.forEach((key, val) -> {
            if (val == null) {
              val = "";
            }
            ngLabelList.add(NGLabel.builder().key(key).value(val).build());
          });
        }
      } catch (Exception exception) {
        log.error("Unable to parse labels", exception);
        return new ArrayList<>();
      }
      return ngLabelList;
    }
    return new ArrayList<>();
  }
}
