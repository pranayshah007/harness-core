/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cimanager.serializer;

import static io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
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
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sHostedInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.UseFromStageInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraSpec;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.beans.yaml.extended.runtime.CloudRuntime;
import io.harness.beans.yaml.extended.runtime.Runtime;
import io.harness.beans.yaml.extended.volumes.CIVolume;
import io.harness.beans.yaml.extended.volumes.EmptyDirYaml;
import io.harness.beans.yaml.extended.volumes.HostPathYaml;
import io.harness.beans.yaml.extended.volumes.PersistentVolumeClaimYaml;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;

public class CIContractsKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(StageInfraDetails.class, 100099);
    kryo.register(CleanupStepInfo.class, 100005);
    kryo.register(RunStepInfo.class, 100011);
    kryo.register(TestStepInfo.class, 100013);
    kryo.register(CustomVariable.class, 100023);
    kryo.register(K8sDirectInfraYaml.class, 100040);
    kryo.register(K8sDirectInfraYamlSpec.class, 100041);
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

    kryo.register(CIVolume.class, 390005);
    kryo.register(EmptyDirYaml.class, 390006);
    kryo.register(HostPathYaml.class, 390007);
    kryo.register(PersistentVolumeClaimYaml.class, 390008);
  }
}
