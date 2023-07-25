/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;
import static io.harness.beans.constants.JsonConstants.RESOLVE_OBJECTS_VIA_JSON_SELECT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.serializer.JsonUtils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_FIRST_GEN, HarnessModuleComponent.CDS_AMI_ASG,
        HarnessModuleComponent.CDS_DASHBOARD})
@Slf4j
public class JsonFunctor implements ExpressionFunctor {
  private final Map<String, Object> contextMap;
  public JsonFunctor(Map<String, Object> contextMap) {
    this.contextMap = contextMap;
  }

  public JsonFunctor() {
    this.contextMap = null;
  }

  public Object object(String json) {
    return JsonUtils.asObject(json, HashMap.class);
  }

  public Object select(String path, String json) {
    final Object object = JsonUtils.jsonPath(json, path);
    log.info(String.format("Json functor evaluated for the Json: %s and path %s", json, path));
    if (object instanceof String) {
      return object;
    } else if (object instanceof LinkedList) {
      LinkedList list = (LinkedList) object;
      if (list.size() == 1) {
        return list.get(0);
      }
    }
    return contextMap != null && contextMap.containsKey(RESOLVE_OBJECTS_VIA_JSON_SELECT) ? object : null;
  }

  public Object list(String path, String json) {
    return JsonUtils.jsonPath(json, path);
  }

  public String format(Object object) {
    return JsonUtils.asJson(object);
  }
}
