/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.execution.tas;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.configfile.ConfigFileOutcome;
import io.harness.cdng.execution.ExecutionDetails;
import io.harness.delegate.task.pcf.request.TasManifestsPackage;
import io.harness.expression.Expression;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@OwnedBy(CDP)
@Data
@Builder
@FieldNameConstants(innerTypeName = "TasStageExecutionDetailsKeys")
@JsonTypeName("TasStageExecutionDetails")
@RecasterAlias("io.harness.cdng.execution.tas.TasStageExecutionDetails")
public class TasStageExecutionDetails implements ExecutionDetails {
  private List<ArtifactOutcome> artifactsOutcome;
  private Map<String, ConfigFileOutcome> configFilesOutcome;
  private Map<String, Object> envVariables;
  private Map<String, Object> outVariables;
  @Expression(ALLOW_SECRETS) private TasManifestsPackage tasManifestsPackage;
  private String pipelineExecutionId;
  private Boolean isAutoscalarEnabled;
  @Expression(ALLOW_SECRETS) private List<String> routeMaps;
  private Integer desiredCount;
  private Boolean isFirstDeployment;
  @Override
  public List<ArtifactOutcome> getArtifactsOutcome() {
    return artifactsOutcome;
  }
  @Override
  public Map<String, ConfigFileOutcome> getConfigFilesOutcome() {
    return configFilesOutcome;
  }
  @Override
  public Map<String, Object> getEnvVariables() {
    return envVariables;
  }
  @Override
  public Map<String, Object> getOutVariables() {
    return outVariables;
  }
}
