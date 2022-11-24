/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

@Slf4j
public class AutoLogRemoveAllContext implements AutoCloseable {
  private Map<String, String> original;

  public AutoLogRemoveAllContext() {
    original = MDC.getCopyOfContextMap();
    MDC.clear();
  }

  @Override
  public void close() {
    if (original != null) {
      MDC.setContextMap(original);
    }
  }
}
