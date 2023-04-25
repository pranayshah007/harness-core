/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.service.impl.gitpolling;

import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import static software.wings.beans.TaskType.PT_SERIALIZATION_SUPPORT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.gitpolling.bean.GitPollingConfig;
import io.harness.cdng.gitpolling.utils.GitPollingStepHelper;
import io.harness.delegate.AccountId;
import io.harness.delegate.Capability;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.gitpolling.GitPollingSourceDelegateRequest;
import io.harness.delegate.task.gitpolling.GitPollingTaskType;
import io.harness.delegate.task.gitpolling.request.GitPollingTaskParameters;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.polling.GitPollingTaskParamsNg;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.polling.bean.GitPollingInfo;
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
public class GitPollingPerpetualTaskHelperNg {
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject protected KryoSerializer kryoSerializer;
  @Inject private DelegateServiceGrpcClient delegateServiceGrpcClient;

  GitPollingStepHelper gitPollingStepHelper;

  public PerpetualTaskExecutionBundle createPerpetualTaskExecutionBundle(PollingDocument pollingDocument) {
    String accountId = pollingDocument.getAccountId();

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

    GitPollingInfo gitPollingInfo = (GitPollingInfo) pollingDocument.getPollingInfo();
    GitPollingConfig pollingConfig = gitPollingInfo.toGitPollingConfig();

    GitPollingSourceDelegateRequest gitPollingSourceDelegateRequest =
        gitPollingStepHelper.toSourceDelegateRequest(pollingConfig, ambiance);

    GitPollingTaskParameters taskParameters = GitPollingTaskParameters.builder()
                                                  .accountId(pollingDocument.getAccountId())
                                                  .gitPollingTaskType(GitPollingTaskType.GET_WEBHOOK_EVENTS)
                                                  .attributes(gitPollingSourceDelegateRequest)
                                                  .build();

    GitPollingTaskParamsNg gitPollingTaskParamsNg =
        GitPollingTaskParamsNg.newBuilder()
            .setPollingDocId(pollingDocument.getUuid())
            .setGitpollingWebhookParams(
                ByteString.copyFrom(getKryoSerializer(pollingDocument.getAccountId()).asBytes(taskParameters)))
            .build();

    Any perpetualTaskParams = Any.pack(gitPollingTaskParamsNg);
    List<ExecutionCapability> executionCapabilities = taskParameters.fetchRequiredExecutionCapabilities(null);

    PerpetualTaskExecutionBundle.Builder builder = PerpetualTaskExecutionBundle.newBuilder();
    executionCapabilities.forEach(executionCapability
        -> builder
               .addCapabilities(
                   Capability.newBuilder()
                       .setKryoCapability(ByteString.copyFrom(
                           getKryoSerializer(pollingDocument.getAccountId()).asDeflatedBytes(executionCapability)))
                       .build())
               .build());
    return builder.setTaskParams(perpetualTaskParams).putAllSetupAbstractions(ngTaskSetupAbstractionsWithOwner).build();
  }

  private KryoSerializer getKryoSerializer(String accountIdentifier) {
    io.harness.delegate.TaskType taskType =
        io.harness.delegate.TaskType.newBuilder().setType(PT_SERIALIZATION_SUPPORT.name()).build();
    AccountId accountId = AccountId.newBuilder().setId(accountIdentifier).build();
    return delegateServiceGrpcClient.isTaskTypeSupported(accountId, taskType) ? referenceFalseKryoSerializer
                                                                              : kryoSerializer;
  }
}
