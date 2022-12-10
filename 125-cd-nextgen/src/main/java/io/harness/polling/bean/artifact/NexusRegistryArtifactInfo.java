/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.bean.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryConfigSpec;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryDockerConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryMavenConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryNpmConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryNugetConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryRawConfig;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.pms.yaml.ParameterField;

import software.wings.utils.RepositoryFormat;

import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
public class NexusRegistryArtifactInfo implements ArtifactInfo {
  String connectorRef;
  String repository;
  String artifactPath;
  String repositoryFormat;
  String artifactId;
  String groupId;
  String packageName;
  String repositoryUrl;
  String classifier;
  String extension;
  String group;
  String repositoryPort;

  @Override
  public ArtifactSourceType getType() {
    return ArtifactSourceType.NEXUS3_REGISTRY;
  }

  @Override
  public ArtifactConfig toArtifactConfig() {
    NexusRegistryConfigSpec nexusRegistryConfigSpec;
    if (repositoryFormat.equalsIgnoreCase(RepositoryFormat.docker.name())) {
      nexusRegistryConfigSpec = NexusRegistryDockerConfig.builder()
                                    .artifactPath(ParameterField.<String>builder().value(artifactPath).build())
                                    .repositoryPort(ParameterField.<String>builder().value(repositoryPort).build())
                                    .repositoryUrl(ParameterField.<String>builder().value(repositoryUrl).build())
                                    .build();
    } else if (repositoryFormat.equalsIgnoreCase(RepositoryFormat.maven.name())) {
      nexusRegistryConfigSpec = NexusRegistryMavenConfig.builder()
                                    .artifactId(ParameterField.<String>builder().value(artifactId).build())
                                    .groupId(ParameterField.<String>builder().value(groupId).build())
                                    .classifier(ParameterField.<String>builder().value(classifier).build())
                                    .extension(ParameterField.<String>builder().value(extension).build())
                                    .build();
    } else if (repositoryFormat.equalsIgnoreCase(RepositoryFormat.npm.name())) {
      nexusRegistryConfigSpec = NexusRegistryNpmConfig.builder()
                                    .packageName(ParameterField.<String>builder().value(packageName).build())
                                    .build();
    } else if (repositoryFormat.equalsIgnoreCase(RepositoryFormat.nuget.name())) {
      nexusRegistryConfigSpec = NexusRegistryNugetConfig.builder()
                                    .packageName(ParameterField.<String>builder().value(packageName).build())
                                    .build();
    } else if (repositoryFormat.equalsIgnoreCase(RepositoryFormat.raw.name())) {
      nexusRegistryConfigSpec =
          NexusRegistryRawConfig.builder().group(ParameterField.<String>builder().value(group).build()).build();
    } else {
      throw new RuntimeException(String.format("Repository format %s is not supported", repositoryFormat));
    }
    return NexusRegistryArtifactConfig.builder()
        .connectorRef(ParameterField.<String>builder().value(connectorRef).build())
        .repository(ParameterField.<String>builder().value(repository).build())
        .repositoryFormat(ParameterField.<String>builder().value(repositoryFormat).build())
        .nexusRegistryConfigSpec(nexusRegistryConfigSpec)
        .build();
  }
}
