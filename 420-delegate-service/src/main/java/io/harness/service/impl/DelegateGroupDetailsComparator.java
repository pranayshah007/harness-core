/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.impl;

import io.harness.delegate.beans.DelegateGroupDetails;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class DelegateGroupDetailsComparator implements Comparator<DelegateGroupDetails> {
  @Override
  public int compare(DelegateGroupDetails o1, DelegateGroupDetails o2) {
    final String connectivityStatus1 = o1.getConnectivityStatus();
    final String connectivityStatus2 = o2.getConnectivityStatus();
    if (connectivityStatus1.equals(connectivityStatus2)) {
      return o1.getGroupName().compareTo(o2.getGroupName());
    } else {
      Map<String, Integer> rank = new HashMap<String, Integer>() {
        {
          put(DelegateConnectivityStatus.GROUP_STATUS_CONNECTED, 1);
          put(DelegateConnectivityStatus.GROUP_STATUS_PARTIALLY_CONNECTED, 2);
          put(DelegateConnectivityStatus.GROUP_STATUS_DISCONNECTED, 3);
        }
      };
      return rank.get(o1.getConnectivityStatus()).compareTo(rank.get(o2.getConnectivityStatus()));
    }
  }
}
