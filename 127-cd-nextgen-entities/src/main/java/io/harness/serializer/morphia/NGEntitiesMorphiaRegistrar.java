/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import io.harness.cdng.artifact.bean.yaml.AMIArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AmazonS3ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.AzureArtifactsConfig;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GithubPackagesArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleArtifactRegistryConfig;
import io.harness.cdng.artifact.bean.yaml.JenkinsArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifact;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.BambooArtifactConfig;
import io.harness.cdng.artifact.outcome.AMIArtifactOutcome;
import io.harness.cdng.artifact.outcome.AcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.AzureArtifactsOutcome;
import io.harness.cdng.artifact.outcome.BambooArtifactOutcome;
import io.harness.cdng.artifact.outcome.DockerArtifactOutcome;
import io.harness.cdng.artifact.outcome.GarArtifactOutcome;
import io.harness.cdng.artifact.outcome.GcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.GithubPackagesArtifactOutcome;
import io.harness.cdng.artifact.outcome.JenkinsArtifactOutcome;
import io.harness.cdng.artifact.outcome.S3ArtifactOutcome;
import io.harness.cdng.gitops.entity.Cluster;
import io.harness.cdng.infra.beans.AsgInfraMapping;
import io.harness.cdng.infra.beans.AwsLambdaInfrastructureMapping;
import io.harness.cdng.infra.beans.AwsSamInfraMapping;
import io.harness.cdng.infra.beans.AzureWebAppInfraMapping;
import io.harness.cdng.infra.beans.CustomDeploymentInfraMapping;
import io.harness.cdng.infra.beans.EcsInfraMapping;
import io.harness.cdng.infra.beans.ElastigroupInfraMapping;
import io.harness.cdng.infra.beans.GoogleFunctionsInfraMapping;
import io.harness.cdng.infra.beans.InfraMapping;
import io.harness.cdng.infra.beans.K8sAwsInfraMapping;
import io.harness.cdng.infra.beans.K8sAzureInfraMapping;
import io.harness.cdng.infra.beans.K8sDirectInfraMapping;
import io.harness.cdng.infra.beans.K8sGcpInfraMapping;
import io.harness.cdng.infra.beans.K8sRancherInfraMapping;
import io.harness.cdng.infra.beans.PdcInfraMapping;
import io.harness.cdng.infra.beans.ServerlessAwsLambdaInfraMapping;
import io.harness.cdng.infra.beans.SshWinRmAwsInfraMapping;
import io.harness.cdng.infra.beans.SshWinRmAzureInfraMapping;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfraMapping;
import io.harness.cdng.manifest.yaml.ManifestsOutcome;
import io.harness.cdng.moduleversioninfo.entity.ModuleVersionInfo;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceConfigOutcome;
import io.harness.cdng.service.beans.ServiceOutcome;
import io.harness.cdng.service.beans.ServiceUseFromStage;
import io.harness.cdng.service.beans.StageOverridesConfig;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.ng.core.ScopeAware;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;

import java.util.Set;

public class NGEntitiesMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(ServiceEntity.class);
    set.add(Cluster.class);
    set.add(Environment.class);
    set.add(InfrastructureEntity.class);
    set.add(NGServiceOverridesEntity.class);
    set.add(ServerlessAwsLambdaInfraMapping.class);
    set.add(SshWinRmAwsInfraMapping.class);
    set.add(AzureWebAppInfraMapping.class);
    set.add(ElastigroupInfraMapping.class);
    set.add(AsgInfraMapping.class);
    set.add(CustomDeploymentInfraMapping.class);
    set.add(EcsInfraMapping.class);
    set.add(InfraMapping.class);
    set.add(K8sDirectInfraMapping.class);
    set.add(K8sGcpInfraMapping.class);
    set.add(K8sAzureInfraMapping.class);
    set.add(PdcInfraMapping.class);
    set.add(SshWinRmAzureInfraMapping.class);
    set.add(TanzuApplicationServiceInfraMapping.class);
    set.add(GoogleFunctionsInfraMapping.class);
    set.add(AwsSamInfraMapping.class);
    set.add(ModuleVersionInfo.class);
    set.add(ScopeAware.class);
    set.add(AwsLambdaInfrastructureMapping.class);
    set.add(K8sAwsInfraMapping.class);
    set.add(K8sRancherInfraMapping.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    h.put("cdng.service.beans.ServiceOutcome", ServiceOutcome.class);
    h.put("cdng.service.beans.ServiceOutcome$ArtifactsOutcome", ServiceOutcome.ArtifactsOutcome.class);
    h.put("ngpipeline.artifact.bean.DockerArtifactOutcome", DockerArtifactOutcome.class);
    h.put("ngpipeline.artifact.bean.S3ArtifactOutcome", S3ArtifactOutcome.class);
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
    h.put("cdng.artifact.bean.yaml.AmazonS3ArtifactConfig", AmazonS3ArtifactConfig.class);
    h.put("cdng.service.ServiceConfig", ServiceConfig.class);
    h.put("cdng.artifact.bean.yaml.JenkinsArtifactConfig", JenkinsArtifactConfig.class);
    h.put("ngpipeline.artifact.bean.JenkinsArtifactOutcome", JenkinsArtifactOutcome.class);
    h.put("cdng.artifact.bean.yaml.GithubPackagesArtifactConfig", GithubPackagesArtifactConfig.class);
    h.put("ngpipeline.artifact.bean.GithubPackagesArtifactOutcome", GithubPackagesArtifactOutcome.class);
    h.put("cdng.artifact.bean.yaml.GoogleArtifactRegistryConfig", GoogleArtifactRegistryConfig.class);
    h.put("ngpipeline.artifact.bean.GarArtifactOutcome", GarArtifactOutcome.class);
    h.put("cdng.artifact.bean.yaml.AzureArtifactsConfig", AzureArtifactsConfig.class);
    h.put("ngpipeline.artifact.bean.AzureArtifactsOutcome", AzureArtifactsOutcome.class);
    h.put("cdng.artifact.bean.yaml.AMIArtifactConfig", AMIArtifactConfig.class);
    h.put("ngpipeline.artifact.bean.AMIArtifactOutcome", AMIArtifactOutcome.class);
    h.put("cdng.artifact.bean.yaml.BambooArtifactConfig", BambooArtifactConfig.class);
    h.put("ngpipeline.artifact.bean.BambooArtifactOutcome", BambooArtifactOutcome.class);
  }
}
