/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifact;
import io.harness.cdng.artifact.outcome.AcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.DockerArtifactOutcome;
import io.harness.cdng.artifact.outcome.GcrArtifactOutcome;
import io.harness.cdng.gitops.entity.Cluster;
import io.harness.cdng.manifest.yaml.ManifestsOutcome;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceConfigOutcome;
import io.harness.cdng.service.beans.ServiceOutcome;
import io.harness.cdng.service.beans.ServiceUseFromStage;
import io.harness.cdng.service.beans.StageOverridesConfig;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.service.entity.ServiceEntity;

import java.util.Set;

public class NGEntitiesMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(ServiceEntity.class);
    set.add(Cluster.class);
    set.add(Environment.class);
    set.add(InfrastructureEntity.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    h.put("cdng.service.beans.ServiceOutcome", ServiceOutcome.class);
    h.put("cdng.service.beans.ServiceOutcome$ArtifactsOutcome", ServiceOutcome.ArtifactsOutcome.class);
    h.put("ngpipeline.artifact.bean.DockerArtifactOutcome", DockerArtifactOutcome.class);
    h.put("ngpipeline.artifact.bean.GcrArtifactOutcome", GcrArtifactOutcome.class);
    h.put("ngpipeline.artifact.bean.AcrArtifactOutcome", AcrArtifactOutcome.class);
    h.put("cdng.service.beans.ServiceConfigOutcome", ServiceConfigOutcome.class);
    h.put("cdng.service.beans.StageOverridesConfig", StageOverridesConfig.class);
    h.put("cdng.service.beans.ServiceUseFromStage", ServiceUseFromStage.class);
    h.put("cdng.service.beans.ServiceUseFromStage$Overrides", ServiceUseFromStage.Overrides.class);
    h.put("io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig", CustomArtifactConfig.class);
    h.put("cdng.artifact.bean.yaml.GcrArtifactConfig", GcrArtifactConfig.class);
    h.put("cdng.artifact.bean.yaml.AcrArtifactConfig", AcrArtifactConfig.class);
    h.put("cdng.artifact.bean.yaml.SidecarArtifact", SidecarArtifact.class);
    h.put("cdng.manifest.yaml.ManifestsOutcome", ManifestsOutcome.class);
    h.put("cdng.artifact.bean.yaml.ArtifactListConfig", ArtifactListConfig.class);
    h.put("cdng.artifact.bean.yaml.DockerHubArtifactConfig", DockerHubArtifactConfig.class);
    h.put("cdng.service.ServiceConfig", ServiceConfig.class);
  }
}
