package io.harness.ng.opa.entities.connector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.opa.OpaEvaluationContext;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@OwnedBy(HarnessTeam.PL)
public class ConnectorOpaEvaluationContext extends OpaEvaluationContext {
    Object entity;
}
