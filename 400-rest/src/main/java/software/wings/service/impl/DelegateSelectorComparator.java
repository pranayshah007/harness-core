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
