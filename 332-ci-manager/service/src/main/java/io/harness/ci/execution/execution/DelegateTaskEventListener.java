/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution;

import static software.wings.beans.TaskType.DLITE_CI_VM_INITIALIZE_TASK;

import io.harness.app.beans.dto.CITaskDetails;
import io.harness.eventsframework.consumer.Message;
import io.harness.ng.core.event.MessageListener;
import io.harness.observer.Informant;
import io.harness.observer.Informant5;
import io.harness.repositories.CIExecutionRepository;
import io.harness.repositories.CITaskDetailsRepository;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import io.fabric8.utils.Strings;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class DelegateTaskEventListener implements MessageListener {
  private KryoSerializer kryoSerializer;
  private static final String OBSERVER_CLASS_NAME_KEY = "observer_class_name";
  private static final String OBSERVER_CLASS_NAME_VALUE = "software.wings.service.impl.CIDelegateTaskObserver";
  @Inject CITaskDetailsRepository ciTaskDetailsRepository;
  @Inject CIExecutionRepository ciExecutionRepository;
  @Inject
  public DelegateTaskEventListener(KryoSerializer kryoSerializer) {
    this.kryoSerializer = kryoSerializer;
  }

  @Override
  public boolean handleMessage(@NonNull final Message message) {
    if (message.hasMessage()) {
      final Map<String, String> metadata = message.getMessage().getMetadataMap();
      if (metadata.containsKey(OBSERVER_CLASS_NAME_KEY)
          && metadata.get(OBSERVER_CLASS_NAME_KEY).equals(OBSERVER_CLASS_NAME_VALUE)) {
        try {
          log.info("Received dlite initialization task message");
          Informant informant = Informant.parseFrom(message.getMessage().getData());
          Informant5 informant5 = informant.getInformant5();
          ByteString accountIdEncrypted = informant5.getParam1();
          ByteString taskIdEncrypted = informant5.getParam2();
          ByteString delegateIdEncrypted = informant5.getParam3();
          ByteString stageIdEncrypted = informant5.getParam4();
          ByteString taskTypeEncrypted = informant5.getParam5();

          String accountId = (String) kryoSerializer.asObject(accountIdEncrypted.toByteArray());
          String taskId = (String) kryoSerializer.asObject(taskIdEncrypted.toByteArray());
          String delegateId = (String) kryoSerializer.asObject(delegateIdEncrypted.toByteArray());
          String stageId = (String) kryoSerializer.asObject(stageIdEncrypted.toByteArray());
          String taskType = (String) kryoSerializer.asObject(taskTypeEncrypted.toByteArray());

          log.info(String.format(
              "Parsed dlite initialization task message, accountId %s, taskId %s, delegateId %s, stageId %s, taskType %s",
              accountId, taskId, delegateId, stageId, taskType));

          if (Strings.isNotBlank(stageId) && DLITE_CI_VM_INITIALIZE_TASK.toString().equals(taskType)) {
            if (ciExecutionRepository.findByStageExecutionId(stageId) == null) {
              log.info("Skipping creation of ciTaskDetails for unknown stageId: {}", stageId);
              return true;
            }
            CITaskDetails ciTaskDetails = CITaskDetails.builder()
                                              .stageExecutionId(stageId)
                                              .delegateId(delegateId)
                                              .taskId(taskId)
                                              .taskType(taskType)
                                              .accountId(accountId)
                                              .build();
            ciTaskDetailsRepository.save(ciTaskDetails);
            log.info(String.format("Successfully saved dlite initialization task data with taskId %s", taskId));
            return true;
          }
        } catch (Exception e) {
          log.error("Error while handling the message: " + e);
          return false;
        }
      }
    }
    return false;
  }
}
