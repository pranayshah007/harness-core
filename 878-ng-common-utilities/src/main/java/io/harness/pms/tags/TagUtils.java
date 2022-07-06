/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.tags;

import static io.harness.pms.yaml.YamlNode.UUID_FIELD_NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;

import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@RecasterAlias("io.harness.pms.tags.TagUtils")
public class TagUtils {
  public void removeUuidFromTags(Map<String, String> tags) {
    if (EmptyPredicate.isNotEmpty(tags)) {
      tags.remove(UUID_FIELD_NAME);
    }
  }
}
