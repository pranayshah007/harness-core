package io.harness.queueservice;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@OwnedBy(HarnessTeam.DEL)
public class DelegateTaskDequeue {
  private String payload;
  private String itemId;
  private DelegateTask delegateTask;
}
