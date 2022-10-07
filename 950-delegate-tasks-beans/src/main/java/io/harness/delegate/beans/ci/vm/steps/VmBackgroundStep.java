/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.vm.steps;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.expression.Expression;

import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(CI)
public class VmBackgroundStep implements VmStepInfo {
  @NotNull String identifier;
  String name;
  String image;
  String command;
  List<String> entrypoint;
  ConnectorDetails imageConnector;
  Map<String, String> portBindings;
  String pullPolicy; // always, if-not-exists or never
  String runAsUser;
  boolean privileged;
  VmUnitTestReport unitTestReport;
  @Expression(ALLOW_SECRETS) Map<String, String> envVariables;

  @Override
  public Type getType() {
    return Type.BACKGROUND;
  }
}
