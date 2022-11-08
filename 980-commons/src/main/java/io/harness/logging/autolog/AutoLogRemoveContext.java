/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

@Slf4j
public class AutoLogRemoveContext implements AutoCloseable {
  private Map<String, String> originals;

  public AutoLogRemoveContext(String... keys) {
    for (String key : keys) {
      String original = MDC.get(key);
      if (original != null) {
        if (originals == null) {
          originals = new HashMap<>();
        }

        originals.put(key, original);
        MDC.remove(key);
      }
    }
  }

  @Override
  public void close() {
    if (isEmpty(originals)) {
      return;
    }
    for (Entry<String, String> entry : originals.entrySet()) {
      MDC.put(entry.getKey(), entry.getValue());
    }
  }
}
