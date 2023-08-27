/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.events.base;

import static io.harness.pms.events.PmsEventFrameworkConstants.SERVICE_NAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.consumer.Message;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.sdk.execution.events.PmsCommonsBaseEventHandler;
import io.harness.serializer.ProtoUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public abstract class PmsAbstractMessageListener<T extends com.google.protobuf.Message, H
                                                     extends PmsCommonsBaseEventHandler<T>>
    implements PmsMessageListener {
  public final String serviceName;
  public final Class<T> entityClass;
  public final H handler;

  public PmsAbstractMessageListener(String serviceName, Class<T> entityClass, H handler) {
    this.serviceName = serviceName;
    this.entityClass = entityClass;
    this.handler = handler;
  }

  /**
   * We are always returning true from this method even if exception occurred. If we return false that means we are do
   * not ack the message and it would be delivered to the same consumer group again. This can lead to double
   * notifications
   */

  @Override
  public boolean handleMessage(Message message, Long readTs) {
    T entity = extractEntity(message);
    Long issueTimestamp = ProtoUtils.timestampToUnixMillis(message.getTimestamp());
    processMessage(entity, message.getMessage().getMetadataMap(), issueTimestamp, readTs);
    return true;
  }

  @VisibleForTesting
  T extractEntity(@NonNull Message message) {
    try {
      return (T) entityClass.getMethod("parseFrom", ByteString.class).invoke(null, message.getMessage().getData());
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking %s for key %s", entityClass.getSimpleName(), message.getId()), e);
    }
  }

  public boolean isProcessable(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      return metadataMap.get(SERVICE_NAME) != null && serviceName.equals(metadataMap.get(SERVICE_NAME));
    }
    return false;
  }

  /**
   * The boolean that we are returning here is just for logging purposes we should infer nothing from the responses
   */
  public void processMessage(T event, Map<String, String> metadataMap, Long messageTimeStamp, Long readTs) {
    handler.handleEvent(event, metadataMap, messageTimeStamp, readTs);
  }
}
