package io.harness.queueservice;

import io.harness.beans.DelegateTask;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class DelegateTaskDequeue {
  private String payload;
  private String itemId;
  private DelegateTask delegateTask;
}
