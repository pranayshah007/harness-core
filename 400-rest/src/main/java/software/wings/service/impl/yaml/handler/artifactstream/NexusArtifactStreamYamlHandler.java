/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.artifactstream;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.service.impl.artifact.ArtifactServiceImpl.metadataOnlyBehindFlag;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.beans.artifact.NexusArtifactStream.Yaml;
import software.wings.beans.yaml.ChangeContext;
import software.wings.utils.RepositoryFormat;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * @author rktummala on 10/09/17
 */
@OwnedBy(CDC)
@Singleton
public class NexusArtifactStreamYamlHandler
    extends ArtifactStreamYamlHandler<NexusArtifactStream.Yaml, NexusArtifactStream> {
  @Inject FeatureFlagService featureFlagService;

  @Override
  public Yaml toYaml(NexusArtifactStream bean, String appId) {
    Yaml yaml = Yaml.builder().build();
    super.toYaml(yaml, bean);
    yaml.setRepositoryName(bean.getJobname());
    yaml.setArtifactPaths(bean.getArtifactPaths());
    if (isNotEmpty(bean.getArtifactPaths())) {
      yaml.setGroupId(bean.getGroupId());
    } else {
      yaml.setImageName(bean.getImageName());
      yaml.setDockerRegistryUrl(bean.getDockerRegistryUrl());
    }
    yaml.setPackageName(bean.getPackageName());
    yaml.setRepositoryFormat(bean.getRepositoryFormat());
    if (!bean.getRepositoryFormat().equals(RepositoryFormat.docker.name())) {
      yaml.setMetadataOnly(metadataOnlyBehindFlag(featureFlagService, bean.getAccountId(), bean.isMetadataOnly()));
    } else {
      yaml.setMetadataOnly(true);
    }
    if (isNotEmpty(bean.getExtension())) {
      yaml.setExtension(bean.getExtension());
    }
    if (isNotEmpty(bean.getClassifier())) {
      yaml.setClassifier(bean.getClassifier());
    }
    return yaml;
  }

  @Override
  protected void toBean(NexusArtifactStream bean, ChangeContext<Yaml> changeContext, String appId) {
    super.toBean(bean, changeContext, appId);
    Yaml yaml = changeContext.getYaml();
    if (isEmpty(yaml.getRepositoryFormat())) {
      throw new InvalidRequestException("Repository Format is mandatory");
    }
    if (isEmpty(yaml.getRepositoryName())) {
      throw new InvalidRequestException("Repository Name is mandatory");
    }
    if (yaml.getRepositoryFormat().equals(RepositoryFormat.raw.name()) && isEmpty(yaml.getPackageName())) {
      throw new InvalidRequestException("Package Name is mandatory");
    }
    if (isNotEmpty(yaml.getArtifactPaths())) {
      bean.setArtifactPaths(yaml.getArtifactPaths());
      bean.setGroupId(yaml.getGroupId());
    } else {
      bean.setImageName(yaml.getImageName());
      bean.setDockerRegistryUrl(yaml.getDockerRegistryUrl());
    }
    bean.setJobname(yaml.getRepositoryName());
    bean.setPackageName(yaml.getPackageName());
    bean.setRepositoryFormat(yaml.getRepositoryFormat());
    if (!yaml.getRepositoryFormat().equals(RepositoryFormat.docker.name())) {
      bean.setMetadataOnly(yaml.isMetadataOnly());
    } else {
      bean.setMetadataOnly(true);
    }
    bean.setExtension(yaml.getExtension());
    bean.setClassifier(yaml.getClassifier());
  }

  @Override
  protected NexusArtifactStream getNewArtifactStreamObject() {
    return new NexusArtifactStream();
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }
}
