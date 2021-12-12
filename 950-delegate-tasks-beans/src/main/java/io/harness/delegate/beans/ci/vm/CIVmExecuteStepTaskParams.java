package io.harness.delegate.beans.ci.vm;

import io.harness.delegate.beans.ci.CIExecuteStepTaskParams;
import io.harness.delegate.beans.ci.vm.steps.VmStepInfo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CIVmExecuteStepTaskParams implements CIExecuteStepTaskParams {
  @NotNull private String ipAddress;
  @NotNull private String poolId;
  @NotNull private String stageRuntimeId;
  @NotNull private String stepRuntimeId;
  @NotNull private String stepId;
  @NotNull private VmStepInfo stepInfo;
  @NotNull private String logKey;
  @NotNull private String workingDir;

  @Builder.Default private static final Type type = Type.VM;

  @Override
  public Type getType() {
    return type;
  }
}
