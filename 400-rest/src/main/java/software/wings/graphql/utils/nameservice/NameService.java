/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.graphql.utils.nameservice;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import java.util.Set;
import javax.validation.constraints.NotNull;

@OwnedBy(DX)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public interface NameService {
  String application = "Application";
  String service = "Service";
  String environment = "Environment";
  String environmentType = "EnvironmentType";
  String Type = "Type";
  String cloudProvider = "CloudProvider";
  String status = "Status";
  String triggeredBy = "TriggeredBy";
  String triggerId = "TriggerId";
  String workflow = "Workflow";
  String pipeline = "Pipeline";
  String instanceType = "InstanceType";
  String artifactType = "ArtifactType";
  String artifactSource = "ArtifactSource";
  String connector = "Connector";
  String deployment = "Deployment";
  String instance = "Instance";
  String trigger = "Trigger";
  String pipelineExecution = "PipelineExecution";
  String user = "User";
  String tag = "Tag";
  String rejectedByFreezeWindow = "RejectedByFreezeWindow";

  String infrastructureDefinition = "InfrastructureDefinition";
  String deploymentType = "DeploymentType";
  String orchestrationWorkflowType = "OrchestrationWorkflowType";

  NameResult getNames(@NotNull Set<String> ids, @NotNull String type);
}
