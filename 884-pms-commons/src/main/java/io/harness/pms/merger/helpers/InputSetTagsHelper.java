/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class InputSetTagsHelper {
  public List<NGTag> getTagsFromYaml(String yaml, Ambiance ambiance) {
    List<NGTag> resolvedTags = new ArrayList<>();
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      YamlField tagYamlField = YamlUtils.readTree(yaml);
      JsonNode tags = tagYamlField.getNode().getCurrJsonNode().get("pipeline").get("tags");
      if (tags != null) {
        Iterator<Map.Entry<String, JsonNode>> tagFields = tags.fields();
        while (tagFields.hasNext()) {
          Map.Entry<String, JsonNode> tag = tagFields.next();
          String key = tag.getKey();
          String value = tag.getValue().asText();
          if (value == null) {
            value = "";
          }
          resolvedTags.add(NGTag.builder().key(key).value(value).build());
        }
      }
    } catch (Exception exception) {
      log.error("Unable to parse yaml to get tags", exception);
      return new ArrayList<>();
    }
    return resolvedTags;
  }
}
