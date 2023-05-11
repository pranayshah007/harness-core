/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.vm.steps;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.expression.Expression;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class VmRunStep implements VmStepInfo {
  private String image;
  private ConnectorDetails imageConnector;
  private ConnectorDetails connector;
  private String pullPolicy; // always, if-not-exists or never
  private String runAsUser;
  private boolean privileged;

  private List<String> entrypoint;
  @Expression(ALLOW_SECRETS) private String command;
  private List<String> outputVariables;
  @Expression(ALLOW_SECRETS) private Map<String, String> envVariables;
  private VmUnitTestReport unitTestReport;
  private long timeoutSecs;

  @Override
  public VmStepInfo.Type getType() {
    return Type.RUN;
  }
}
