package software.wings.service.impl.aws.model;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@TargetModule(Module._950_DELEGATE_TASKS_BEANS)
public class AwsAsgGetRunningCountData {
  private int asgMin;
  private int asgMax;
  private int asgDesired;
  private String asgName;
}
