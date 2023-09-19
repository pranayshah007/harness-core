/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;

import java.util.List;
import java.util.Set;

public class ConnectorCapabilityBaseHelper {
  private static final String origin = "connector";
  public static void populateDelegateSelectorCapability(
      List<ExecutionCapability> capabilityList, Set<String> delegateSelectors, ConnectorType connectorType) {
    if (isNotEmpty(delegateSelectors)) {
      StringBuilder connectorOrigin = new StringBuilder();
      if (connectorType != null) {
        connectorOrigin.append(connectorType.getDisplayName());
        connectorOrigin.append(" ");
      }
      connectorOrigin.append(origin);
      capabilityList.add(
          SelectorCapability.builder().selectors(delegateSelectors).selectorOrigin(connectorOrigin.toString()).build());
    }
  }
}
