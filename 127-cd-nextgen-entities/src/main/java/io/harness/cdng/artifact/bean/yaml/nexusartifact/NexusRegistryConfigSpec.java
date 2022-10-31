/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.yaml.nexusartifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.v3.oas.annotations.media.Schema;

@OwnedBy(CDC)
@Schema(name = "NexusRegistryConfigSpec", description = "This contains details of repository for Nexus Registry")
@JsonSubTypes({
  @JsonSubTypes.Type(value = NexusRegistryMavenConfig.class, name = NexusConstant.MAVEN)
  , @JsonSubTypes.Type(value = NexusRegistryNpmConfig.class, name = NexusConstant.NPM),
      @JsonSubTypes.Type(value = NexusRegistryNugetConfig.class, name = NexusConstant.NUGET),
      @JsonSubTypes.Type(value = NexusRegistryDockerConfig.class, name = NexusConstant.DOCKER),
      @JsonSubTypes.Type(value = NexusRegistryRawConfig.class, name = NexusConstant.RAW)
})
public interface NexusRegistryConfigSpec extends DecryptableEntity {}
