package io.harness.cdng.pipeline.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.service.beans.ServicesYaml;
import io.harness.plancreator.strategy.StrategyType;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("MultiDeploymentStepParameters")
@OwnedBy(CDC)
@RecasterAlias("io.harness.cdng.pipeline.beans.MultiDeploymentStepParameters")
public class MultiDeploymentStepParameters implements StepParameters {
  ServicesYaml services;
  EnvironmentsYaml environments;
  EnvironmentGroupYaml environmentGroup;
  @NotNull String childNodeId;
  ParameterField<Integer> maxConcurrency;
  @NotNull StrategyType strategyType;
}
