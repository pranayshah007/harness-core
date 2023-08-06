/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helpers.generator;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.polling.contracts.GoogleCloudStoragePayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.Type;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class GoogleCloudStoragePollingItemGenerator implements PollingItemGenerator {
  @Inject BuildTriggerHelper buildTriggerHelper;

  @Override
  public PollingItem generatePollingItem(BuildTriggerOpsData buildTriggerOpsData) {
    NGTriggerEntity ngTriggerEntity = buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity();

    PollingItem.Builder builder = getBaseInitializedPollingItem(ngTriggerEntity, buildTriggerOpsData);

    String connectorRef = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.connectorRef");

    String project = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.project");

    String bucket = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.bucket");

    String artifactPath = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.artifactPath");

    return builder
        .setPollingPayloadData(PollingPayloadData.newBuilder()
                                   .setConnectorRef(connectorRef)
                                   .setType(Type.GOOGLE_CLOUD_STORAGE_ARTIFACT)
                                   .setGoogleCloudStoragePayload(GoogleCloudStoragePayload.newBuilder()
                                                                     .setProject(project)
                                                                     .setBucket(bucket)
                                                                     .setArtifactPath(artifactPath)
                                                                     .build())
                                   .build())
        .build();
  }
}
