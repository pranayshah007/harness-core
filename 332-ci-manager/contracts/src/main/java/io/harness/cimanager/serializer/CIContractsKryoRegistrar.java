/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cimanager.serializer;

import static io.harness.beans.yaml.extended.infrastrucutre.K8sHostedInfraYaml.K8sHostedInfraYamlSpec;
import static io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml.VmPoolYamlSpec;

import io.harness.beans.dependencies.CIServiceInfo;
import io.harness.beans.dependencies.DependencyElement;
import io.harness.beans.execution.PublishedFileArtifact;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.beans.steps.outcome.CIStepOutcome;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.steps.stepinfo.CleanupStepInfo;
import io.harness.beans.steps.stepinfo.DockerStepInfo;
import io.harness.beans.steps.stepinfo.ECRStepInfo;
import io.harness.beans.steps.stepinfo.GCRStepInfo;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.steps.stepinfo.RestoreCacheGCSStepInfo;
import io.harness.beans.steps.stepinfo.RestoreCacheS3StepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.beans.steps.stepinfo.SaveCacheGCSStepInfo;
import io.harness.beans.steps.stepinfo.SaveCacheS3StepInfo;
import io.harness.beans.steps.stepinfo.SecurityStepInfo;
import io.harness.beans.steps.stepinfo.TestStepInfo;
import io.harness.beans.steps.stepinfo.UploadToArtifactoryStepInfo;
import io.harness.beans.steps.stepinfo.UploadToGCSStepInfo;
import io.harness.beans.steps.stepinfo.UploadToS3StepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.yaml.extended.CustomSecretVariable;
import io.harness.beans.yaml.extended.CustomTextVariable;
import io.harness.beans.yaml.extended.CustomVariable;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.DockerInfraSpec;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml.HostedVmInfraSpec;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.beans.yaml.extended.infrastrucutre.K8sHostedInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.UseFromStageInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraSpec;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.beans.yaml.extended.platform.ArchType;
import io.harness.beans.yaml.extended.platform.Platform;
import io.harness.beans.yaml.extended.platform.V1.Arch;
import io.harness.beans.yaml.extended.platform.V1.OS;
import io.harness.beans.yaml.extended.platform.V1.PlatformV1;
import io.harness.beans.yaml.extended.runtime.CloudRuntime;
import io.harness.beans.yaml.extended.runtime.DockerRuntime;
import io.harness.beans.yaml.extended.runtime.Runtime;
import io.harness.beans.yaml.extended.runtime.V1.CloudRuntimeV1;
import io.harness.beans.yaml.extended.runtime.V1.CloudRuntimeV1.CloudRuntimeSpec;
import io.harness.beans.yaml.extended.runtime.V1.MachineRuntimeV1;
import io.harness.beans.yaml.extended.runtime.V1.MachineRuntimeV1.MachineRuntimeSpec;
import io.harness.beans.yaml.extended.runtime.V1.RuntimeV1;
import io.harness.beans.yaml.extended.runtime.V1.VMRuntimeV1;
import io.harness.beans.yaml.extended.runtime.V1.VMRuntimeV1.VMRuntimeSpec;
import io.harness.beans.yaml.extended.volumes.CIVolume;
import io.harness.beans.yaml.extended.volumes.EmptyDirYaml;
import io.harness.beans.yaml.extended.volumes.HostPathYaml;
import io.harness.beans.yaml.extended.volumes.PersistentVolumeClaimYaml;
import io.harness.cimanager.stages.V1.IntegrationStageConfigImplV1;
import io.harness.cimanager.stages.V1.IntegrationStageNodeV1;
import io.harness.serializer.KryoRegistrar;
import io.harness.when.beans.StageWhenCondition;

import com.esotericsoftware.kryo.Kryo;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

public class CIContractsKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(StageInfraDetails.class, 100099);
    kryo.register(CleanupStepInfo.class, 100005);
    kryo.register(RunStepInfo.class, 100011);
    kryo.register(TestStepInfo.class, 100013);
    kryo.register(CustomVariable.class, 100023);
    kryo.register(K8sDirectInfraYaml.class, 100040);
    kryo.register(CIStepOutcome.class, 100057);
    kryo.register(PluginStepInfo.class, 100058);
    kryo.register(SecurityStepInfo.class, 110105);
    kryo.register(GitCloneStepInfo.class, 110115);
    kryo.register(CustomSecretVariable.class, 100061);
    kryo.register(CustomTextVariable.class, 100062);
    kryo.register(CustomVariable.Type.class, 100063);
    kryo.register(DependencyElement.class, 100064);
    kryo.register(CIServiceInfo.class, 100065);
    kryo.register(UseFromStageInfraYaml.class, 100066);
    kryo.register(Infrastructure.Type.class, 100069);
    kryo.register(RunTestsStepInfo.class, 100075);
    kryo.register(GCRStepInfo.class, 100076);
    kryo.register(ECRStepInfo.class, 100077);
    kryo.register(DockerStepInfo.class, 100078);
    kryo.register(SaveCacheGCSStepInfo.class, 100079);
    kryo.register(RestoreCacheGCSStepInfo.class, 100080);
    kryo.register(SaveCacheS3StepInfo.class, 100081);
    kryo.register(RestoreCacheS3StepInfo.class, 100082);
    kryo.register(UploadToS3StepInfo.class, 100083);
    kryo.register(UploadToGCSStepInfo.class, 100084);
    kryo.register(UploadToArtifactoryStepInfo.class, 100086);
    kryo.register(StepArtifacts.class, 100091);
    kryo.register(PublishedFileArtifact.class, 100092);
    kryo.register(PublishedImageArtifact.class, 100093);
    kryo.register(VmInfraYaml.class, 100096);
    kryo.register(VmInfraSpec.Type.class, 110102);
    kryo.register(VmPoolYaml.class, 110103);
    kryo.register(VmPoolYamlSpec.class, 110104);
    kryo.register(K8sHostedInfraYaml.class, 110106);
    kryo.register(K8sHostedInfraYamlSpec.class, 110107);
    kryo.register(HostedVmInfraYaml.class, 110112);
    kryo.register(Runtime.Type.class, 110113);
    kryo.register(CloudRuntime.class, 110114);
    kryo.register(DockerRuntime.class, 110116);
    kryo.register(DockerInfraYaml.class, 110117);

    kryo.register(K8sDirectInfraYamlSpec.class, 100041);

    kryo.register(CIVolume.class, 390005);
    kryo.register(EmptyDirYaml.class, 390006);
    kryo.register(HostPathYaml.class, 390007);
    kryo.register(PersistentVolumeClaimYaml.class, 390008);

    kryo.register(IntegrationStageNodeV1.class, 110118);
    kryo.register(IntegrationStageConfigImplV1.class, 110119);
    kryo.register(StageWhenCondition.class, 110120);
    kryo.register(ObjectNode.class, 110121);
    kryo.register(JsonNodeFactory.class, 110122);
    kryo.register(TextNode.class, 110123);
    kryo.register(IntegrationStageNodeV1.StepType.class, 110124);
    kryo.register(ArrayNode.class, 110125);
    kryo.register(IntNode.class, 110126);
    kryo.register(DecimalNode.class, 110127);

    kryo.register(PlatformV1.class, 110129);
    kryo.register(OS.class, 110130);
    kryo.register(Arch.class, 110131);
    kryo.register(RuntimeV1.class, 110132);
    kryo.register(CloudRuntimeV1.class, 110133);
    kryo.register(CloudRuntimeSpec.class, 110134);
    kryo.register(MachineRuntimeV1.class, 110135);
    kryo.register(MachineRuntimeSpec.class, 110136);
    kryo.register(RuntimeV1.Type.class, 110137);
    kryo.register(HostedVmInfraSpec.class, 110138);
    kryo.register(Platform.class, 110139);
    kryo.register(ArchType.class, 110140);
    kryo.register(OSType.class, 110141);
    kryo.register(BooleanNode.class, 110142);
    kryo.register(NullNode.class, 110145);
    kryo.register(DockerInfraSpec.class, 110146);
    kryo.register(VMRuntimeV1.class, 110147);
    kryo.register(VMRuntimeSpec.class, 110148);
  }
}