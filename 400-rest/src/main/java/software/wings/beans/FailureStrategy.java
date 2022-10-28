/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.RepairActionCode;
import io.harness.exception.FailureType;
import io.harness.yaml.BaseYaml;

import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@Builder(toBuilder = true)
@TargetModule(HarnessModule._957_CG_BEANS)
public class FailureStrategy {
  @NotNull @Size(min = 1, message = "should not be empty") private List<FailureType> failureTypes;
  private ExecutionScope executionScope;
  private RepairActionCode repairActionCode;
  private int retryCount;
  private List<Integer> retryIntervals;
  private RepairActionCode repairActionCodeAfterRetry;
  @Valid private FailureCriteria failureCriteria;
  private List<String> specificSteps;
  private Long manualInterventionTimeout;
  private ExecutionInterruptType actionAfterTimeout;

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends BaseYaml {
    private List<String> failureTypes = new ArrayList<>();
    private String executionScope;
    private String repairActionCode;
    private int retryCount;
    private List<Integer> retryIntervals;
    private String repairActionCodeAfterRetry;
    private FailureCriteria failureCriteria;
    private List<String> specificSteps = new ArrayList<>();
    private String actionAfterTimeout;
    private Long manualInterventionTimeout;

    @Builder
    public Yaml(List<String> failureTypes, String executionScope, String repairActionCode, int retryCount,
        List<Integer> retryIntervals, String repairActionCodeAfterRetry, FailureCriteria failureCriteria,
        List<String> specificSteps, String actionAfterTimeout, Long manualInterventionTimeout) {
      this.failureTypes = failureTypes;
      this.executionScope = executionScope;
      this.repairActionCode = repairActionCode;
      this.retryCount = retryCount;
      this.retryIntervals = retryIntervals;
      this.repairActionCodeAfterRetry = repairActionCodeAfterRetry;
      this.failureCriteria = failureCriteria;
      this.specificSteps = specificSteps;
      this.actionAfterTimeout = actionAfterTimeout;
      this.manualInterventionTimeout = manualInterventionTimeout;
    }
  }
}
