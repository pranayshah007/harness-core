/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.expressions.functors;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.HeaderConfig;
import io.harness.expression.LateBindingMap;
import io.harness.ngtriggers.helpers.TriggerHelper;

import java.util.List;

@OwnedBy(HarnessTeam.PIPELINE)
public class TriggerHeaderBindingMap extends LateBindingMap {
  List<HeaderConfig> triggerHeader;

  public TriggerHeaderBindingMap(List<HeaderConfig> triggerHeader) {
    this.triggerHeader = triggerHeader;
    super.putAll(TriggerHelper.processTriggerHeader(triggerHeader));
  }

  @Override
  public synchronized Object get(Object key) {
    if (!(key instanceof String)) {
      return null;
    }

    return fetchHeader((String) key);
  }

  private Object fetchHeader(String key) {
    for (HeaderConfig header : triggerHeader) {
      if (header.getKey().toLowerCase().equals(key.toLowerCase())) {
        return String.join(",", header.getValues());
      }
    }
    return null;
  }
}
