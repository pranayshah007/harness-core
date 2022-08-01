/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.mappers;

import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.bson.Document;

@UtilityClass
public class ModuleInfoMapper {
  public static Map<String, LinkedHashMap<String, Object>> getModuleInfo(Map<String, Document> moduleInfo) {
    if (EmptyPredicate.isEmpty(moduleInfo)) {
      return new LinkedHashMap<>();
    }
    Map<String, LinkedHashMap<String, Object>> moduleInfoReturn = new LinkedHashMap<>();
    for (Map.Entry<String, Document> moduleEntry : moduleInfo.entrySet()) {
      Document document = moduleEntry.getValue();
      moduleInfoReturn.put(moduleEntry.getKey(), new LinkedHashMap<>(CollectionUtils.emptyIfNull(document)));
    }
    return moduleInfoReturn;
  }
}
