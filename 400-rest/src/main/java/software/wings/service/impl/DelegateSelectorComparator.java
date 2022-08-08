/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import io.harness.delegate.beans.DelegateSelector;

import java.util.Comparator;

public class DelegateSelectorComparator implements Comparator<DelegateSelector> {
  @Override
  public int compare(DelegateSelector s1, DelegateSelector s2) {
    if ((s1.isConnected() && s2.isConnected()) || (!s1.isConnected() && !s2.isConnected())) {
      return s1.getName().compareTo(s2.getName());
    } else if (s1.isConnected()) {
      return -1;
    } else {
      return 1;
    }
  }
}
