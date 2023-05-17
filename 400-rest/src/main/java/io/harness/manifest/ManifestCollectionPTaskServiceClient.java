/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.manifest;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.delegate.task.manifests.request.ManifestCollectionPTaskClientParams.ManifestCollectionPTaskClientParamsKeys;
import io.harness.delegate.task.manifests.request.ManifestCollectionParams;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskServiceClient;
import io.harness.perpetualtask.manifest.ManifestCollectionTaskParams;
import io.harness.serializer.KryoSerializer;

import software.wings.service.impl.applicationmanifest.ManifestCollectionUtils;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Map;

@OwnedBy(CDC)
public class ManifestCollectionPTaskServiceClient implements PerpetualTaskServiceClient {
  private static final String APP_MANIFEST_ID = ManifestCollectionPTaskClientParamsKeys.appManifestId;
  private static final String APP_ID = ManifestCollectionPTaskClientParamsKeys.appId;
  @Inject private ManifestCollectionUtils manifestCollectionUtils;
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public ManifestCollectionTaskParams getTaskParams(PerpetualTaskClientContext clientContext) {
    Map<String, String> clientParams = clientContext.getClientParams();
    String appManifestId = clientParams.get(APP_MANIFEST_ID);
    String appId = clientParams.get(APP_ID);
    ManifestCollectionParams manifestCollectionParams =
        manifestCollectionUtils.prepareCollectTaskParams(appManifestId, appId);
    ByteString bytes = ByteString.copyFrom(kryoSerializer.asBytes(manifestCollectionParams));
    return ManifestCollectionTaskParams.newBuilder()
        .setAppManifestId(appManifestId)
        .setManifestCollectionParams(bytes)
        .build();
  }

  @Override
  public DelegateTask getValidationTask(PerpetualTaskClientContext clientContext, String accountId) {
    Map<String, String> clientParams = clientContext.getClientParams();
    return manifestCollectionUtils.buildValidationTask(
        clientParams.get(APP_MANIFEST_ID), clientParams.get(ManifestCollectionPTaskClientParamsKeys.appId));
  }
}
