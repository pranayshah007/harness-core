/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression.functors;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.ExpressionFunctor;
import io.harness.serializer.JsonUtils;

import java.util.HashMap;

@OwnedBy(HarnessTeam.PIPELINE)
public class NGJsonFunctor implements ExpressionFunctor {
  public Object object(String json) {
    return JsonUtils.asObject(json, HashMap.class);
  }

  public Object select(String path, String json) {
    return JsonUtils.jsonPath(json, path);
  }

  public Object list(String path, String json) {
    return JsonUtils.jsonPath(json, path);
  }

  public String format(Object object) {
    return JsonUtils.asJson(object);
  }
}
