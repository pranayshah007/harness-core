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
public class HostElementParamMapper implements ContextElementParamMapper {
  private final HostElement element;

  public HostElementParamMapper(HostElement element) {
    this.element = element;
  }

  @Override
  public Map<String, Object> paramMap(ExecutionContext context) {
    Map<String, Object> map = new HashMap<>();
    map.put(ContextElement.HOST, this.element);

    if (this.element.getPcfElement() != null) {
      PcfInstanceElementParamMapper mapper = new PcfInstanceElementParamMapper(this.element.getPcfElement());
      map.putAll(mapper.paramMap(context));
    }
    return map;
  }
}