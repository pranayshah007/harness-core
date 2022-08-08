/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.generator.artifactstream;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.azure.AzureMachineImageArtifactDTO.ImageType.IMAGE_GALLERY;
import static io.harness.generator.constants.ArtifactStreamsGeneratorConstants.AZURE_MACHINE_IMAGE_ARTIFACT_STREAM_NAME;
import static io.harness.generator.constants.ArtifactStreamsGeneratorConstants.IMAGE_GALLERY_NAME;
import static io.harness.generator.constants.ArtifactStreamsGeneratorConstants.LINUX_IMAGE_DEFINITION_NAME;
import static io.harness.generator.constants.InfraDefinitionGeneratorConstants.AZURE_FUNCTIONAL_TEST_RESOURCE_GROUP;
import static io.harness.generator.constants.InfraDefinitionGeneratorConstants.AZURE_SUBSCRIPTION_ID;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.artifact.AzureMachineImageArtifactStream.OSType.LINUX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.SettingGenerator;

import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.AzureMachineImageArtifactStream;
import software.wings.beans.artifact.AzureMachineImageArtifactStream.ImageDefinition;

import com.google.common.base.Preconditions;

@OwnedBy(CDP)
public class AzureMachineImageLinuxGalleryArtifactStreamGenerator extends AzureMachineImageArtifactStreamGenerator {
  @Override
  public ArtifactStream ensureArtifactStream(Seed seed, Owners owners, String serviceName, boolean atConnector) {
    return null;
  }

  @Override
  public ArtifactStream ensureArtifactStream(Seed seed, Owners owners, boolean atConnector, boolean metadataOnly) {
    Service service = owners.obtainService();
    Application application = owners.obtainApplication();
    final SettingAttribute settingAttribute =
        settingGenerator.ensurePredefined(seed, owners, SettingGenerator.Settings.AZURE_TEST_CLOUD_PROVIDER);
    String serviceId = service != null ? service.getUuid() : null;

    return ensureArtifactStream(seed,
        AzureMachineImageArtifactStream.builder()
            .name(AZURE_MACHINE_IMAGE_ARTIFACT_STREAM_NAME)
            .appId(atConnector ? GLOBAL_APP_ID : application.getUuid())
            .subscriptionId(AZURE_SUBSCRIPTION_ID)
            .serviceId(atConnector ? settingAttribute.getUuid() : serviceId)
            .settingId(settingAttribute.getUuid())
            .imageType(IMAGE_GALLERY)
            .osType(LINUX)
            .imageDefinition(ImageDefinition.builder()
                                 .resourceGroup(AZURE_FUNCTIONAL_TEST_RESOURCE_GROUP)
                                 .imageGalleryName(IMAGE_GALLERY_NAME)
                                 .imageDefinitionName(LINUX_IMAGE_DEFINITION_NAME)
                                 .build())
            .autoPopulate(atConnector)
            .build(),
        owners);
  }

  @Override
  public ArtifactStream ensureArtifactStream(Seed seed, ArtifactStream artifactStream, Owners owners) {
    AzureMachineImageArtifactStream azureMachineImageArtifactStream = (AzureMachineImageArtifactStream) artifactStream;
    Preconditions.checkNotNull(azureMachineImageArtifactStream);

    ImageDefinition imageDefinition = azureMachineImageArtifactStream.getImageDefinition();
    Preconditions.checkNotNull(imageDefinition);

    AzureMachineImageArtifactStream.builder()
        .appId(azureMachineImageArtifactStream.getAppId())
        .subscriptionId(azureMachineImageArtifactStream.getSubscriptionId())
        .serviceId(azureMachineImageArtifactStream.getServiceId())
        .name(azureMachineImageArtifactStream.getName())
        .osType(azureMachineImageArtifactStream.getOsType())
        .imageType(azureMachineImageArtifactStream.getImageType())
        .imageDefinition(imageDefinition)
        .autoPopulate(azureMachineImageArtifactStream.isAutoPopulate())
        .settingId(azureMachineImageArtifactStream.getSettingId())
        .build();

    return saveArtifactStream(azureMachineImageArtifactStream, owners);
  }
}
