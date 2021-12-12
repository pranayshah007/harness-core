package io.harness.delegate.beans.ci.vm.steps;

import io.harness.delegate.beans.ci.pod.ConnectorDetails;

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

  private String args;
  private List<String> entrypoint;
  private String language;
  private String buildTool;
  private String packages;
  private String testAnnotations;
  private boolean runOnlySelectedTests;
  private String preCommand;
  private String postCommand;
  private Map<String, String> envVariables;
  private List<String> outputVariables;
  private VmUnitTestReport unitTestReport;
  private long timeoutSecs;

  @Override
  public VmStepInfo.Type getType() {
    return VmStepInfo.Type.RUN_TEST;
  }
}
