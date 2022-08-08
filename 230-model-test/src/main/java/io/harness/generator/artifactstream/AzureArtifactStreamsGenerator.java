/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.generator.artifactstream;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import io.harness.annotations.dev.OwnedBy;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.SettingGenerator;
import io.harness.generator.SettingGenerator.Settings;

import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.AzureArtifactsArtifactStream;
import software.wings.beans.artifact.AzureArtifactsArtifactStreamProtocolType;

import com.google.inject.Inject;

@OwnedBy(CDP)
public class AzureArtifactStreamsGenerator implements ArtifactStreamsGenerator {
  @Inject private SettingGenerator settingGenerator;
  @Inject private ArtifactStreamGeneratorHelper artifactStreamGeneratorHelper;

  private static final String FEED_ID_MAVEN = "garvit-test";
  private static final String PACKAGE_ID_MAVEN = "610b1a30-9b03-4380-b869-a098b3794396";
  private static final String PACKAGE_NAME_MAVEN = "com.mycompany.app:my-app";

  @Override
  public ArtifactStream ensureArtifactStream(Seed seed, Owners owners) {
    return ensureArtifactStream(seed, owners, false);
  }

  @Override
  public ArtifactStream ensureArtifactStream(Seed seed, Owners owners, String serviceName, boolean atConnector) {
    return null;
  }

  @Override
  public ArtifactStream ensureArtifactStream(Seed seed, Owners owners, boolean atConnector) {
    Service service = owners.obtainService();
    Application application = owners.obtainApplication();
    final SettingAttribute settingAttribute =
        settingGenerator.ensurePredefined(seed, owners, Settings.AZURE_TEST_CLOUD_PROVIDER);
    String serviceId = service != null ? service.getUuid() : null;
    ArtifactStream artifactStream = AzureArtifactsArtifactStream.builder()
                                        .appId(atConnector ? GLOBAL_APP_ID : application.getUuid())
                                        .serviceId(atConnector ? settingAttribute.getUuid() : serviceId)
                                        .feed(FEED_ID_MAVEN)
                                        .packageId(PACKAGE_ID_MAVEN)
                                        .name("azure-hello-world")
                                        .packageName(PACKAGE_NAME_MAVEN)
                                        .protocolType(AzureArtifactsArtifactStreamProtocolType.maven.name())
                                        .autoPopulate(true)
                                        .settingId(settingAttribute.getUuid())
                                        .build();
    return ensureArtifactStream(seed, artifactStream, owners);
  }

  @Override
  public ArtifactStream ensureArtifactStream(Seed seed, Owners owners, boolean atConnector, boolean metadataOnly) {
    return null;
  }

  @Override
  public ArtifactStream ensureArtifactStream(Seed seed, ArtifactStream artifactStream, Owners owners) {
    AzureArtifactsArtifactStream azureArtifactStream = (AzureArtifactsArtifactStream) artifactStream;
    ArtifactStream existing = artifactStreamGeneratorHelper.exists(azureArtifactStream);
    if (existing != null) {
      return existing;
    }
    return artifactStreamGeneratorHelper.saveArtifactStream(AzureArtifactsArtifactStream.builder()
                                                                .appId(azureArtifactStream.getAppId())
                                                                .serviceId(azureArtifactStream.getServiceId())
                                                                .name(azureArtifactStream.getName())
                                                                .project(azureArtifactStream.getProject())
                                                                .feed(azureArtifactStream.getFeed())
                                                                .packageName(azureArtifactStream.getPackageName())
                                                                .packageId(azureArtifactStream.getPackageId())
                                                                .protocolType(azureArtifactStream.getProtocolType())
                                                                .autoPopulate(azureArtifactStream.isAutoPopulate())
                                                                .settingId(azureArtifactStream.getSettingId())
                                                                .build(),
        owners);
  }
}
