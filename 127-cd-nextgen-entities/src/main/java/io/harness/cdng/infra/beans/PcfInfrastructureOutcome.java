package io.harness.cdng.infra.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@JsonTypeName(InfrastructureKind.PCF)
@TypeAlias("cdng.infra.beans.PcfInfrastructureOutcome")
@RecasterAlias("io.harness.cdng.infra.beans.PcfInfrastructureOutcome")
public class PcfInfrastructureOutcome extends InfrastructureOutcomeAbstract {
  String connectorRef;
  String organization;
  String space;
  @VariableExpression(skipVariableExpression = true) EnvironmentOutcome environment;
  String infrastructureKey;
  @Override
  public String getKind() {
    return InfrastructureKind.PCF;
  }
}
