/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.service.impl.artifact;

import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.utils.ArtifactStepHelper;
import io.harness.delegate.Capability;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.polling.ArtifactCollectionTaskParamsNg;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.polling.bean.ArtifactInfo;
import io.harness.polling.bean.PollingDocument;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@OwnedBy(HarnessTeam.CDC)
public class ArtifactPerpetualTaskHelperNg {
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  ArtifactStepHelper artifactStepHelper;

  public PerpetualTaskExecutionBundle createPerpetualTaskExecutionBundle(PollingDocument pollingDocument) {
    String accountId = pollingDocument.getAccountId();

    // This is a hack so that we can re-use same methods.
    // Do note that Ambiance is incomplete here. In future, if need be, populate accordingly to avoid failures.
    Map<String, String> abstractions = new HashMap<>();
    abstractions.put(SetupAbstractionKeys.accountId, accountId);
    if (pollingDocument.getOrgIdentifier() != null) {
      abstractions.put(SetupAbstractionKeys.orgIdentifier, pollingDocument.getOrgIdentifier());
    }
    if (pollingDocument.getProjectIdentifier() != null) {
      abstractions.put(SetupAbstractionKeys.projectIdentifier, pollingDocument.getProjectIdentifier());
    }
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(abstractions).build();
    final Map<String, String> ngTaskSetupAbstractionsWithOwner = getNGTaskSetupAbstractionsWithOwner(
        accountId, pollingDocument.getOrgIdentifier(), pollingDocument.getProjectIdentifier());

    ArtifactInfo artifactInfo = (ArtifactInfo) pollingDocument.getPollingInfo();
    ArtifactConfig artifactConfig = artifactInfo.toArtifactConfig();

    if (artifactConfig instanceof CustomArtifactConfig) {
      ((CustomArtifactConfig) artifactConfig).setFromTrigger(true);
    }

    ArtifactSourceDelegateRequest artifactSourceDelegateRequest =
        artifactStepHelper.toSourceDelegateRequest(artifactConfig, ambiance);

    ArtifactTaskParameters taskParameters = ArtifactTaskParameters.builder()
                                                .accountId(pollingDocument.getAccountId())
                                                .artifactTaskType(ArtifactTaskType.GET_BUILDS)
                                                .attributes(artifactSourceDelegateRequest)
                                                .build();

    ArtifactCollectionTaskParamsNg artifactCollectionTaskParamsNg =
        ArtifactCollectionTaskParamsNg.newBuilder()
            .setPollingDocId(pollingDocument.getUuid())
            .setArtifactCollectionParams(ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(taskParameters)))
            .build();

    Any perpetualTaskParams = Any.pack(artifactCollectionTaskParamsNg);
    List<ExecutionCapability> executionCapabilities = taskParameters.fetchRequiredExecutionCapabilities(null);

    PerpetualTaskExecutionBundle.Builder builder = PerpetualTaskExecutionBundle.newBuilder();
    executionCapabilities.forEach(executionCapability
        -> builder
               .addCapabilities(Capability.newBuilder()
                                    .setKryoCapability(ByteString.copyFrom(
                                        referenceFalseKryoSerializer.asDeflatedBytes(executionCapability)))
                                    .build())
               .build());
    return builder.setTaskParams(perpetualTaskParams).putAllSetupAbstractions(ngTaskSetupAbstractionsWithOwner).build();
  }
}
