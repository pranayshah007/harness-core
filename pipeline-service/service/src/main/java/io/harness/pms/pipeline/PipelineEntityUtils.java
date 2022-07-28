/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import io.harness.data.structure.EmptyPredicate;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PipelineEntityUtils {
  public String getModuleNameFromPipelineEntity(PipelineEntity pipelineEntity, String defaultValue) {
    String moduleName = "";
    List<String> modules = new ArrayList<>(pipelineEntity.getFilters().keySet());
    if (!EmptyPredicate.isEmpty(modules)) {
      if (!modules.get(0).equals("pms")) {
        moduleName = modules.get(0);
      } else if (modules.size() > 1) {
        moduleName = modules.get(1);
      }
    }
    return moduleName.isEmpty() ? defaultValue : moduleName;
  }
}
