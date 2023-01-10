/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.message;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Message service for inter-process communication via the filesystem
 *
 * Created by brett on 10/26/17
 */
public interface MessageService {
  void writeMessage(String message, String... params);

  void writeMessageToChannel(MessengerType targetType, String targetProcessId, String message, String... params);

  Message readMessage(long timeout);

  Message readMessageFromChannel(MessengerType sourceType, String sourceProcessId, long timeout);

  Runnable getMessageCheckingRunnable(long readTimeout, Consumer<Message> messageHandler);

  Runnable getMessageCheckingRunnableForChannel(
      MessengerType sourceType, String sourceProcessId, long readTimeout, Consumer<Message> messageHandler);

  void shutdown();

  Message waitForMessage(String messageName, long timeout, boolean printIntermediateMessages);

  List<Message> waitForMessages(String messageName, long timeout, long minWaitTime);

  Message waitForMessageOnChannel(MessengerType sourceType, String sourceProcessId, String messageName, long timeout,
      boolean printIntermediateMessages);

  List<Message> waitForMessagesOnChannel(
      MessengerType sourceType, String sourceProcessId, String messageName, long timeout, long minWaitTime);

  List<String> listChannels(MessengerType type);

  void closeChannel(MessengerType type, String id);

  void clearChannel(MessengerType type, String id);

  void putData(String name, String key, Object value);

  void putAllData(String name, Map<String, Object> dataToWrite);

  <T> T getData(String name, String key, Class<T> valueClass);

  Map<String, Object> getAllData(String name);

  List<String> listDataNames(@Nullable String prefix);

  void removeData(String name, String key);

  void closeData(String name);

  void logAllMessages(MessengerType sourceType, String sourceProcessId);
}
