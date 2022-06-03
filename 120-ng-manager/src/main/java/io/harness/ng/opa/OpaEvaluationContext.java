package io.harness.ng.opa;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.opaclient.model.UserOpaEvaluationContext;

import java.util.Date;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.PL)
public class OpaEvaluationContext {
  Object entity;
  UserOpaEvaluationContext user;
  String action;
  Date date;
}
