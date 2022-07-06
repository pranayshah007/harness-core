package io.harness.delegate.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@OwnedBy(HarnessTeam.DEL)
public class DelegateSelector {
  private final String name;
  private final boolean connected;
}
