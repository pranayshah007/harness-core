/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskServiceClient;
import io.harness.perpetualtask.artifact.ArtifactCollectionTaskParams;
import io.harness.serializer.KryoSerializer;

import software.wings.delegatetasks.buildsource.BuildSourceParameters;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
public class ArtifactCollectionPTaskServiceClient implements PerpetualTaskServiceClient {
  private static final String ARTIFACT_STREAM_ID = "artifactStreamId";

  @Inject private ArtifactCollectionUtils artifactCollectionUtils;
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public ArtifactCollectionTaskParams getTaskParams(PerpetualTaskClientContext clientContext) {
    Map<String, String> clientParams = clientContext.getClientParams();
    String artifactStreamId = clientParams.get(ARTIFACT_STREAM_ID);
    BuildSourceParameters buildSourceParameters =
        artifactCollectionUtils.prepareBuildSourceParameters(artifactStreamId);
    ByteString bytes = ByteString.copyFrom(kryoSerializer.asBytes(buildSourceParameters));
    return ArtifactCollectionTaskParams.newBuilder()
        .setArtifactStreamId(artifactStreamId)
        .setBuildSourceParams(bytes)
        .build();
  }

  @Override
  public DelegateTask getValidationTask(PerpetualTaskClientContext clientContext, String accountId) {
    Map<String, String> clientParams = clientContext.getClientParams();
    return artifactCollectionUtils.prepareValidateTask(clientParams.get(ARTIFACT_STREAM_ID), accountId);
  }
}
