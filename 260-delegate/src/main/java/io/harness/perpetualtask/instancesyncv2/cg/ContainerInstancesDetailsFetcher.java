package io.harness.perpetualtask.instancesyncv2.cg;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.network.SafeHttpCall.execute;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams;
import io.harness.serializer.KryoSerializer;

import software.wings.helpers.ext.k8s.request.K8sClusterConfig;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class ContainerInstancesDetailsFetcher implements InstanceDetailsFetcher {
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;

  private final KryoSerializer kryoSerializer;
  @Override
  public PerpetualTaskResponse fetchRunningInstanceDetails(PerpetualTaskId taskId, CgInstanceSyncTaskParams params) {
    K8sClusterConfig config =
        (K8sClusterConfig) kryoSerializer.asObject(params.getCloudProviderDetails().toByteArray());
    /*
            publishInstanceSyncResult(
                    taskId, settingAttribute.getAccountId(), containerServicePerpetualTaskParams.getNamespace(),
       responseData);*/

    return null;
  }

  private void publishInstanceSyncResult(
      PerpetualTaskId taskId, String accountId, String namespace, DelegateResponseData responseData) {
    try {
      execute(delegateAgentManagerClient.publishInstanceSyncResult(taskId.getId(), accountId, responseData));
    } catch (Exception e) {
      log.error(
          String.format("Failed to publish container instance sync result. namespace [%s] and PerpetualTaskId [%s]",
              namespace, taskId.getId()),
          e);
    }
  }
}
