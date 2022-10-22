/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(PIPELINE)
@UtilityClass
public class IdentifierGeneratorUtils {
  public String getId(String name) {
    List<String> builder = new ArrayList<>();
    String[] pipelineNameList = name.split(" ");
    Arrays.stream(pipelineNameList).forEach(value -> {
      value = value.replace("-", "").replace(".", "").trim();
      if (StringUtils.isNotEmpty(value)) {
        builder.add(value);
      }
    });
    return String.join("_", builder);
    // TODO: if name is null we need to generate id in some other way
  }
}
