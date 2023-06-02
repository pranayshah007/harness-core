/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.SweepingOutput;
import io.harness.beans.WorkflowType;
import io.harness.beans.terraform.TerraformPlanParam;
import io.harness.context.ContextElementType;
import io.harness.cvng.beans.cvnglog.ApiCallLogDTO;
import io.harness.cvng.beans.cvnglog.ApiCallLogDTO.ApiCallLogDTOField;
import io.harness.serializer.KryoRegistrar;

import software.wings.api.CloudProviderType;
import software.wings.api.ExecutionDataValue;
import software.wings.api.ServiceElement;
import software.wings.api.ecs.EcsBGSetupData;
import software.wings.beans.AmiDeploymentType;
import software.wings.beans.AppContainer;
import software.wings.beans.ApprovalDetails;
import software.wings.beans.ArtifactStreamMetadata;
import software.wings.beans.ArtifactVariable;
import software.wings.beans.AwsInstanceFilter;
import software.wings.beans.CountsByStatuses;
import software.wings.beans.EntityType;
import software.wings.beans.ErrorStrategy;
import software.wings.beans.ExecutionStrategy;
import software.wings.beans.GitFileConfig;
import software.wings.beans.LicenseInfo;
import software.wings.beans.PhaseStepType;
import software.wings.beans.S3FileConfig;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.Tag;
import software.wings.beans.TerraformSourceType;
import software.wings.beans.VMSSAuthType;
import software.wings.beans.VMSSDeploymentType;
import software.wings.beans.Variable;
import software.wings.beans.VariableType;
import software.wings.beans.appmanifest.ManifestInput;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactFile;
import software.wings.beans.artifact.ArtifactInput;
import software.wings.beans.artifact.ArtifactStreamSummary;
import software.wings.beans.artifact.ArtifactSummary;
import software.wings.beans.container.IstioConfig;
import software.wings.beans.container.KubernetesBlueGreenConfig;
import software.wings.beans.container.KubernetesPortProtocol;
import software.wings.beans.container.KubernetesServiceSpecification;
import software.wings.beans.container.KubernetesServiceType;
import software.wings.beans.container.Label;
import software.wings.beans.infrastructure.Host;
import software.wings.beans.trigger.WebhookSource;
import software.wings.helpers.ext.gcb.models.BuildStep;
import software.wings.helpers.ext.gcb.models.GcbBuildDetails;
import software.wings.helpers.ext.gcb.models.GcbBuildStatus;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.metrics.MetricType;
import software.wings.sm.ExecutionInterruptEffect;
import software.wings.sm.PipelineSummary;
import software.wings.sm.StateTypeScope;
import software.wings.sm.StepExecutionSummary;
import software.wings.utils.ContainerFamily;
import software.wings.utils.FileType;

import com.esotericsoftware.kryo.Kryo;

@OwnedBy(CDP)
public class CgOrchestrationBeansKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(ContextElementType.class, 4004);
    kryo.register(WorkflowType.class, 5025);
    kryo.register(ServiceElement.class, 5083);
    kryo.register(ExecutionStatus.class, 5136);
    kryo.register(KubernetesBlueGreenConfig.class, 5364);
    kryo.register(Variable.class, 5378);
    kryo.register(GitFileConfig.class, 5472);
    kryo.register(LicenseInfo.class, 5511);
    kryo.register(ArtifactVariable.class, 7195);
    kryo.register(ArtifactStreamSummary.class, 7202);
    kryo.register(ArtifactStreamMetadata.class, 8126);
    kryo.register(ArtifactSummary.class, 8127);

    // Put promoted classes here and do not change the id
    kryo.register(SweepingOutput.class, 3101);
    kryo.register(ExecutionInterruptType.class, 4000);
    kryo.register(ExecutionDataValue.class, 5368);
    kryo.register(CountsByStatuses.class, 4008);
    kryo.register(AppContainer.class, 5064);
    kryo.register(Host.class, 5067);
    kryo.register(ContainerFamily.class, 5118);
    kryo.register(FileType.class, 5119);
    kryo.register(KubernetesPortProtocol.class, 5152);
    kryo.register(KubernetesServiceType.class, 5153);
    kryo.register(MetricType.class, 5313);
    kryo.register(Label.class, 5345);
    kryo.register(EntityType.class, 5360);
    kryo.register(KubernetesServiceSpecification.class, 5363);
    kryo.register(IstioConfig.class, 5466);
    kryo.register(ErrorStrategy.class, 4005);
    kryo.register(ExecutionStrategy.class, 4002);
    kryo.register(PhaseStepType.class, 5026);
    kryo.register(VariableType.class, 5379);
    kryo.register(ExecutionInterruptEffect.class, 5236);
    kryo.register(PipelineSummary.class, 5142);
    kryo.register(StateTypeScope.class, 5144);
    kryo.register(StepExecutionSummary.class, 5145);
    kryo.register(EcsBGSetupData.class, 5611);
    kryo.register(WebhookSource.class, 8551);
    kryo.register(ApiCallLogDTO.class, 9048);
    kryo.register(ApiCallLogDTOField.class, 9049);
    kryo.register(ApiCallLogDTO.FieldType.class, 9050);
    kryo.register(AwsInstanceFilter.class, 40092);
    kryo.register(VMSSDeploymentType.class, 400124);
    kryo.register(VMSSAuthType.class, 400127);
    kryo.register(AmiDeploymentType.class, 400125);
    kryo.register(CloudProviderType.class, 400126);
    kryo.register(TerraformPlanParam.class, 7458);
    kryo.register(OrchestrationWorkflowType.class, 5148);
    kryo.register(GcbBuildDetails.class, 7411);
    kryo.register(GcbBuildStatus.class, 7412);
    kryo.register(BuildStep.class, 7423);
    kryo.register(ArtifactInput.class, 7459);
    kryo.register(ServiceVariableType.class, 5362);
    kryo.register(ArtifactFile.class, 5066);
    kryo.register(Tag.class, 7185);
    kryo.register(Artifact.class, 7192);
    kryo.register(Artifact.ContentStatus.class, 7193);
    kryo.register(Artifact.Status.class, 7194);
    kryo.register(ApprovalDetails.Action.class, 7461);
    kryo.register(ManifestInput.class, 7462);
    kryo.register(K8sValuesLocation.class, 7463);
    kryo.register(TerraformSourceType.class, 7474);
    kryo.register(S3FileConfig.class, 7475);
  }
}
