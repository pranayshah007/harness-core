/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Map;
import org.slf4j.MDC;

@OwnedBy(HarnessTeam.DX)
public class MdcContextSetter implements AutoCloseable {
  Map<String, String> contexts;

  public MdcContextSetter(Map<String, String> contexts) {
    if (isEmpty(contexts)) {
      return;
    }
    this.contexts = contexts;
    for (Map.Entry<String, String> context : contexts.entrySet()) {
      MDC.put(context.getKey(), context.getValue());
    }
  }

  @Override
  public void close() {
    if (isEmpty(contexts)) {
      return;
    }
    for (Map.Entry<String, String> context : contexts.entrySet()) {
      MDC.remove(context.getKey());
    }
  }
}
