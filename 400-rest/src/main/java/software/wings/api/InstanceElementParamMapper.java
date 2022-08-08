/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;

import java.util.HashMap;
import java.util.Map;

@OwnedBy(CDC)
public class InstanceElementParamMapper implements ContextElementParamMapper {
  private final InstanceElement element;

  public InstanceElementParamMapper(InstanceElement element) {
    this.element = element;
  }

  @Override
  public Map<String, Object> paramMap(ExecutionContext context) {
    Map<String, Object> map = new HashMap<>();
    map.put(ContextElement.INSTANCE, this.element);
    if (this.element.getHost() != null) {
      HostElementParamMapper mapper = new HostElementParamMapper(this.element.getHost());
      map.putAll(mapper.paramMap(context));
    }
    if (this.element.getServiceTemplateElement() != null) {
      ServiceTemplateElementParamMapper mapper =
          new ServiceTemplateElementParamMapper(this.element.getServiceTemplateElement());
      map.putAll(mapper.paramMap(context));
    }
    return map;
  }
}