/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/**
 *
 */

package software.wings.beans;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.CreatedByType;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.WorkflowType;

import software.wings.beans.appmanifest.HelmChart;
import software.wings.persistence.artifact.Artifact;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Transient;

/**
 * The type Execution args.
 *
 * @author Rishi
 */
@FieldNameConstants(innerTypeName = "ExecutionArgsKeys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@OwnedBy(HarnessTeam.CDC)
@TargetModule(HarnessModule._957_CG_BEANS)
public class ExecutionArgs {
  private WorkflowType workflowType;
  private String serviceId;
  private String commandName;
  private ExecutionStrategy executionStrategy;
  private List<Artifact> artifacts;
  private Map<String, String> artifactIdNames;
  private String orchestrationId;
  @Transient private List<ServiceInstance> serviceInstances;
  private Map<String, String> serviceInstanceIdNames;
  @Transient private ExecutionCredential executionCredential;
  private ErrorStrategy errorStrategy;
  private boolean triggeredFromPipeline;
  private String pipelineId;
  private String pipelinePhaseElementId;
  private int pipelinePhaseParallelIndex;
  private String stageName;
  private Map<String, String> workflowVariables;
  private String notes;
  @JsonIgnore @Deprecated private EmbeddedUser triggeredBy;
  private CreatedByType createdByType;
  private String triggeringApiKeyId;
  private boolean excludeHostsWithSameArtifact;
  private boolean notifyTriggeredUserOnly;
  private List<ArtifactVariable> artifactVariables;
  private boolean targetToSpecificHosts;
  private List<String> hosts;
  // If any variable is Runtime and Default values is provided
  private boolean continueWithDefaultValues;
  private List<HelmChart> helmCharts;
  private List<ManifestVariable> manifestVariables;
  private Map<String, String> helmChartIdNames;
  @Transient private boolean continueRunningPipelinesDuringMigration;
}
