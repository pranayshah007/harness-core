/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helpers.generator;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.polling.contracts.Nexus3RegistryPayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.Type;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(CDP)
public class Nexus3PollingItemGenerator implements PollingItemGenerator {
  @Inject BuildTriggerHelper buildTriggerHelper;

  @Override
  public PollingItem generatePollingItem(BuildTriggerOpsData buildTriggerOpsData) {
    NGTriggerEntity ngTriggerEntity = buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity();
    PollingItem.Builder builder = getBaseInitializedPollingItem(ngTriggerEntity, buildTriggerOpsData);
    String connectorRef = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.connectorRef");
    String repository = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.repository");
    String artifactPath = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.artifactPath");
    String repositoryUrl = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.repositoryUrl");
    String artifactId = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.artifactId");
    String repositoryFormat =
        buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.repositoryFormat");
    String groupId = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.groupId");
    String packageName = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.packageName");
    String classifier = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.classifier");
    String extension = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.extension");
    String repositoryPort = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.repositoryPort");
    String group = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.group");

    Nexus3RegistryPayload.Builder nexus3RegistryPayload = Nexus3RegistryPayload.newBuilder();
    nexus3RegistryPayload.setRepositoryFormat(repositoryFormat).setRepository(repository);
    if ("maven".equalsIgnoreCase(repositoryFormat)) {
      nexus3RegistryPayload.setArtifactId(artifactId)
          .setGroupId(groupId)
          .setClassifier(classifier)
          .setExtension(extension);
    } else if ("docker".equalsIgnoreCase(repositoryFormat)) {
      nexus3RegistryPayload.setRepositoryUrl(repositoryUrl)
          .setRepositoryPort(repositoryPort)
          .setArtifactPath(artifactPath);
    } else if ("nuget".equalsIgnoreCase(repositoryFormat)) {
      nexus3RegistryPayload.setPackageName(packageName);
    } else if ("npm".equalsIgnoreCase(repositoryFormat)) {
      nexus3RegistryPayload.setPackageName(packageName);
    } else if ("raw".equalsIgnoreCase(repositoryFormat)) {
      nexus3RegistryPayload.setGroup(group);
    } else {
      throw new RuntimeException(String.format("Repository format %s is not supported", repositoryFormat));
    }

    return builder
        .setPollingPayloadData(PollingPayloadData.newBuilder()
                                   .setConnectorRef(connectorRef)
                                   .setType(Type.NEXUS3)
                                   .setNexus3RegistryPayload(nexus3RegistryPayload.build())
                                   .build())
        .build();
  }
}
