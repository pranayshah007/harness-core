/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.artifactsource.ArtifactSource;
import io.harness.cdng.artifact.bean.artifactsource.DockerArtifactSource;
import io.harness.cdng.artifact.steps.ArtifactStepParameters;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.infra.beans.InfraMapping;
import io.harness.cdng.infra.beans.InfraUseFromStage;
import io.harness.cdng.infra.beans.K8sAzureInfraMapping;
import io.harness.cdng.infra.beans.K8sDirectInfraMapping;
import io.harness.cdng.infra.beans.K8sGcpInfraMapping;
import io.harness.cdng.infra.beans.PdcInfraMapping;
import io.harness.cdng.infra.beans.ServerlessAwsLambdaInfraMapping;
import io.harness.cdng.infra.beans.SshWinRmAzureInfraMapping;
import io.harness.cdng.infra.steps.InfraStepParameters;
import io.harness.cdng.pipeline.executions.CDAccountExecutionMetadata;
import io.harness.cdng.provision.cloudformation.beans.CloudformationConfig;
import io.harness.cdng.provision.terraform.TerraformConfig;
import io.harness.cdng.service.steps.ServiceStepParameters;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.polling.bean.PollingDocument;

import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class NGMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(InfraMapping.class);
    set.add(K8sDirectInfraMapping.class);
    set.add(K8sGcpInfraMapping.class);
    set.add(K8sAzureInfraMapping.class);
    set.add(PdcInfraMapping.class);
    set.add(SshWinRmAzureInfraMapping.class);
    set.add(DockerArtifactSource.class);
    set.add(ArtifactSource.class);
    set.add(TerraformConfig.class);
    set.add(CloudformationConfig.class);
    set.add(PollingDocument.class);
    set.add(CDAccountExecutionMetadata.class);
    set.add(EnvironmentGroupEntity.class);
    set.add(ServerlessAwsLambdaInfraMapping.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    h.put("cdng.artifact.steps.ArtifactStepParameters", ArtifactStepParameters.class);
    h.put("cdng.service.steps.ServiceStepParameters", ServiceStepParameters.class);
    h.put("cdng.infra.beans.InfraUseFromStage$Overrides", InfraUseFromStage.Overrides.class);
    h.put("cdng.infra.beans.InfraUseFromStage", InfraUseFromStage.class);
    h.put("cdng.infra.steps.InfraStepParameters", InfraStepParameters.class);
    h.put("io.harness.cdng.provision.terraform.TerraformConfig", TerraformConfig.class);
    h.put("io.harness.cdng.provision.cloudformation.beans.CloudformationConfig", CloudformationConfig.class);
    h.put("io.harness.polling.bean.PollingDocument", PollingDocument.class);
  }
}
