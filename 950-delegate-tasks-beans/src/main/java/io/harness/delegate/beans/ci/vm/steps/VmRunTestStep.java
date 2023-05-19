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
public class VmRunTestStep implements VmStepInfo {
  private String image;
  private ConnectorDetails connector;
  private String pullPolicy;
  private boolean privileged;
  private String runAsUser;

  @Expression(ALLOW_SECRETS) private String args;
  private List<String> entrypoint;
  private String language;
  private String buildTool;
  private String packages;
  private String namespaces;
  private String testAnnotations;
  private boolean runOnlySelectedTests;
  @Expression(ALLOW_SECRETS) private String preCommand;
  @Expression(ALLOW_SECRETS) private String postCommand;
  @Expression(ALLOW_SECRETS) private Map<String, String> envVariables;
  private List<String> outputVariables;
  private VmUnitTestReport unitTestReport;
  private long timeoutSecs;
  private String buildEnvironment;
  private String frameworkVersion;
  private String testSplitStrategy;
  private boolean parallelizeTests;
  private String testGlobs;
  private String pythonVersion;
  private String testRoot;

  @Override
  public VmStepInfo.Type getType() {
    return VmStepInfo.Type.RUN_TEST;
  }
}
