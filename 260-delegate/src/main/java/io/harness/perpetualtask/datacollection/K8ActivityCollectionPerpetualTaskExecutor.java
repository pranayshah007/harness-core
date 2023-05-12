/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.datacollection;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.cvng.beans.CVDataCollectionInfo;
import io.harness.cvng.beans.K8ActivityDataCollectionInfo;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.grpc.utils.AnyUtils;
import io.harness.k8s.apiclient.ApiClientFactory;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.AutoLogContext;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskExecutor;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskLogContext;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.datacollection.k8s.ChangeIntelSharedInformerFactory;
import io.harness.perpetualtask.k8s.watch.K8sWatchServiceDelegate.WatcherGroup;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;

import software.wings.delegatetasks.cvng.K8InfoDataService;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class K8ActivityCollectionPerpetualTaskExecutor implements PerpetualTaskExecutor {
  private final Map<String, WatcherGroup> watchMap = new ConcurrentHashMap<>();
  @Inject private K8InfoDataService k8InfoDataService;

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private ChangeIntelSharedInformerFactory changeIntelSharedInformerFactory;
  @Inject private ApiClientFactory apiClientFactory;
  @Inject private Injector injector;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    try (AutoLogContext ignore1 = new PerpetualTaskLogContext(taskId.getId(), OVERRIDE_ERROR)) {
      K8ActivityCollectionPerpetualTaskParams taskParams =
          AnyUtils.unpack(params.getCustomizedParams(), K8ActivityCollectionPerpetualTaskParams.class);
      log.info("Executing for !! changeSourceId: {}", taskParams.getDataCollectionWorkerId());
      watchMap.computeIfAbsent(taskId.getId(), id -> {
        CVDataCollectionInfo dataCollectionInfo = (CVDataCollectionInfo) referenceFalseKryoSerializer.asObject(
            taskParams.getDataCollectionInfo().toByteArray());
        log.info("for {} DataCollectionInfo {} ", taskParams.getDataCollectionWorkerId(), dataCollectionInfo);

        KubernetesClusterConfigDTO kubernetesClusterConfig =
            (KubernetesClusterConfigDTO) dataCollectionInfo.getConnectorConfigDTO();

        List<List<EncryptedDataDetail>> encryptedDataDetailList = dataCollectionInfo.getEncryptedDataDetails();
        KubernetesConfig kubernetesConfig = k8InfoDataService.getDecryptedKubernetesConfig(kubernetesClusterConfig,
            isNotEmpty(encryptedDataDetailList) ? encryptedDataDetailList.get(0) : new ArrayList<>());

        ApiClient apiClient = apiClientFactory.getClient(kubernetesConfig).setVerifyingSsl(false);

        SharedInformerFactory factory = changeIntelSharedInformerFactory.createInformerFactoryWithHandlers(
            apiClient, taskParams.getAccountId(), (K8ActivityDataCollectionInfo) dataCollectionInfo, injector);
        factory.startAllRegisteredInformers();
        return WatcherGroup.builder().watchId(id).sharedInformerFactory(factory).build();
      });

      return PerpetualTaskResponse.builder().responseCode(200).responseMessage("success").build();
    }
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    try (AutoLogContext ignore1 = new PerpetualTaskLogContext(taskId.getId(), OVERRIDE_ERROR)) {
      String watchId = taskId.getId();
      if (watchMap.get(watchId) == null) {
        return false;
      }
      log.info("Stopping the watch with id {}", watchId);
      watchMap.computeIfPresent(watchId, (id, eventWatcher) -> {
        eventWatcher.close();
        return null;
      });
      watchMap.remove(watchId);
      return true;
    }
  }
}
