/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.artifactstream;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.beans.artifact.DockerArtifactStream.DockerArtifactStreamBuilder;
import software.wings.beans.artifact.DockerArtifactStream.Yaml;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.YamlType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

/**
 * @author rktummala on 10/09/17
 */
@OwnedBy(CDC)
@Singleton
public class DockerArtifactStreamYamlHandler extends ArtifactStreamYamlHandler<Yaml, DockerArtifactStream> {
  @Inject private FeatureFlagService featureFlagService;

  @Override
  public Yaml toYaml(DockerArtifactStream bean, String appId) {
    Yaml yaml = Yaml.builder().build();
    super.toYaml(yaml, bean);
    yaml.setImageName(bean.getImageName());
    return yaml;
  }

  @Override
  public DockerArtifactStream upsertFromYaml(ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext) {
    String accountId = changeContext.getChange().getAccountId();
    String yamlFilePath = changeContext.getChange().getFilePath();
    DockerArtifactStream previous = get(accountId, yamlFilePath);

    if (!featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, accountId)) {
      String appId =
          yamlHelper.getAppId(changeContext.getChange().getAccountId(), changeContext.getChange().getFilePath());
      String serviceId = yamlHelper.getServiceId(appId, changeContext.getChange().getFilePath());
      DockerArtifactStreamBuilder builder = DockerArtifactStream.builder().serviceId(serviceId).appId(appId);
      DockerArtifactStream dockerArtifactStream = toBean(accountId, builder, changeContext.getYaml(), appId);
      dockerArtifactStream.setName(yamlHelper.getArtifactStreamName(yamlFilePath));
      dockerArtifactStream.setSyncFromGit(changeContext.getChange().isSyncFromGit());

      if (previous != null) {
        dockerArtifactStream.setUuid(previous.getUuid());
        return (DockerArtifactStream) artifactStreamService.update(
            dockerArtifactStream, !dockerArtifactStream.isSyncFromGit());
      } else {
        return (DockerArtifactStream) artifactStreamService.createWithBinding(
            appId, dockerArtifactStream, !dockerArtifactStream.isSyncFromGit());
      }
    } else {
      if (changeContext.getYamlType() == YamlType.ARTIFACT_STREAM) {
        String appId =
            yamlHelper.getAppId(changeContext.getChange().getAccountId(), changeContext.getChange().getFilePath());
        String serviceId = yamlHelper.getServiceId(appId, changeContext.getChange().getFilePath());
        DockerArtifactStreamBuilder builder = DockerArtifactStream.builder().serviceId(serviceId).appId(appId);
        DockerArtifactStream dockerArtifactStream = toBean(accountId, builder, changeContext.getYaml(), appId);
        dockerArtifactStream.setName(yamlHelper.getArtifactStreamName(yamlFilePath));
        dockerArtifactStream.setSyncFromGit(changeContext.getChange().isSyncFromGit());

        if (previous != null) {
          dockerArtifactStream.setUuid(previous.getUuid());
          return (DockerArtifactStream) artifactStreamService.update(
              dockerArtifactStream, !dockerArtifactStream.isSyncFromGit());
        } else {
          return (DockerArtifactStream) artifactStreamService.createWithBinding(
              appId, dockerArtifactStream, !dockerArtifactStream.isSyncFromGit());
        }
      } else {
        DockerArtifactStreamBuilder builder = DockerArtifactStream.builder().appId(GLOBAL_APP_ID);
        DockerArtifactStream dockerArtifactStream = toBean(accountId, builder, changeContext.getYaml(), GLOBAL_APP_ID);
        dockerArtifactStream.setName(yamlHelper.getArtifactStreamName(yamlFilePath));
        dockerArtifactStream.setSyncFromGit(changeContext.getChange().isSyncFromGit());
        if (previous != null) {
          dockerArtifactStream.setUuid(previous.getUuid());
          return (DockerArtifactStream) artifactStreamService.update(
              dockerArtifactStream, !dockerArtifactStream.isSyncFromGit());
        } else {
          return (DockerArtifactStream) artifactStreamService.createWithBinding(
              GLOBAL_APP_ID, dockerArtifactStream, !dockerArtifactStream.isSyncFromGit());
        }
      }
    }
  }

  @Override
  protected DockerArtifactStream getNewArtifactStreamObject() {
    return new DockerArtifactStream();
  }

  private DockerArtifactStream toBean(
      String accountId, DockerArtifactStreamBuilder builder, Yaml artifactStreamYaml, String appId) {
    DockerArtifactStream dockerArtifactStream =
        builder.settingId(getSettingId(accountId, appId, artifactStreamYaml.getServerName()))
            .imageName(artifactStreamYaml.getImageName())
            .build();
    dockerArtifactStream.setCollectionEnabled(artifactStreamYaml.getCollectionEnabled());
    return dockerArtifactStream;
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }
}
