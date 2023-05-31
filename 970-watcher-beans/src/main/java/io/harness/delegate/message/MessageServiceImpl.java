/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.message;

import static io.harness.filesystem.FileIo.acquireLock;
import static io.harness.filesystem.FileIo.releaseLock;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.filefilter.FileFileFilter.FILE;

import io.harness.concurrent.HTimeLimiter;
import io.harness.exception.GeneralException;
import io.harness.serializer.JsonUtils;
import io.harness.threading.Schedulable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.io.filefilter.AndFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.apache.commons.io.filefilter.PrefixFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.lang3.StringUtils;

/**
 * Message service for inter-process communication via the filesystem
 *
 * Created by brett on 10/26/17
 */
@Slf4j
public class MessageServiceImpl implements MessageService {
  private static final String FOUNDATION = "msg/";
  private static final String IO = "io/";
  private static final String DATA = "data/";
  private static final String NO_SPACE_LEFT_ON_DEVICE_ERROR = "No space left on device";
  @VisibleForTesting static final String IN = "IN";
  @VisibleForTesting static final String OUT = "OUT";
  @VisibleForTesting static final String PRIMARY_DELIMITER = "|-|";
  @VisibleForTesting static final String SECONDARY_DELIMITER = "::";

  private final String root;
  private final Clock clock;
  private final MessengerType messengerType;
  private final String processId;

  private final TimeLimiter timeLimiter = HTimeLimiter.create();
  private final Map<File, Long> messageTimestamps = new HashMap<>();
  private final Map<File, BlockingQueue<Message>> messageQueues = new HashMap<>();
  private final AtomicBoolean running = new AtomicBoolean(true);

  public MessageServiceImpl(String root, Clock clock, MessengerType messengerType, String processId) {
    this.root = root;
    this.clock = clock;
    this.messengerType = messengerType;
    this.processId = processId;
  }

  @Override
  public void writeMessage(String message, String... params) {
    writeMessageToChannel(messengerType, processId, message, params);
  }

  @Override
  public void writeMessageToChannel(
      MessengerType targetType, String targetProcessId, String message, String... params) {
    boolean isOutput = messengerType == targetType && processId.equals(targetProcessId);
    if (log.isDebugEnabled()) {
      String output = isOutput ? "Writing message" : "Sending message to " + targetType + " " + targetProcessId;
      String paramStr = params != null ? join(", ", params) : "";
      log.info("{}: {}({})", output, message, paramStr);
    }
    try {
      File channel = getMessageChannel(targetType, targetProcessId);
      List<String> messageContent = new ArrayList<>();
      messageContent.add(isOutput ? OUT : IN);
      messageContent.add("" + clock.millis());
      messageContent.add(messengerType.name());
      messageContent.add(processId);
      messageContent.add(message);
      if (params != null) {
        messageContent.add(join(SECONDARY_DELIMITER, params));
      }
      if (acquireLock(channel, ofSeconds(5))) {
        try {
          FileUtils.touch(channel);
          FileUtils.writeLines(channel, singletonList(join(PRIMARY_DELIMITER, messageContent)), true);
        } finally {
          if (!releaseLock(channel)) {
            log.error("Failed to release lock {}", channel.getPath());
          }
        }
      } else {
        log.error("Failed to acquire lock {}", channel.getPath());
      }
    } catch (Exception e) {
      log.error("Error writing message to channel: {}({})", message, params, e);
    }
  }

  @Override
  public Message readMessage(long timeout) {
    return readMessageFromChannel(messengerType, processId, timeout);
  }

  @Override
  public Message readMessageFromChannel(MessengerType sourceType, String sourceProcessId, long timeout) {
    boolean isInput = messengerType == sourceType && processId.equals(sourceProcessId);
    try {
      File channel = getMessageChannel(sourceType, sourceProcessId);
      long lastReadTimestamp = Optional.ofNullable(messageTimestamps.get(channel)).orElse(0L);
      if (!channel.exists()) {
        FileUtils.touch(channel);
      }
      return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMillis(timeout), () -> {
        while (true) {
          LineIterator reader = FileUtils.lineIterator(channel);
          while (reader.hasNext()) {
            String line = reader.nextLine();
            if (StringUtils.startsWith(line, (isInput ? IN : OUT) + PRIMARY_DELIMITER)) {
              List<String> components = Arrays.asList(line.split(Pattern.quote(PRIMARY_DELIMITER)));
              long timestamp = Long.parseLong(components.get(1));
              if (timestamp > lastReadTimestamp) {
                try {
                  MessengerType fromType = MessengerType.valueOf(components.get(2));
                  String fromProcess = components.get(3);
                  String messageName = components.get(4);
                  List<String> msgParams = new ArrayList<>();
                  if (components.size() == 6) {
                    String params = components.get(5);
                    if (!params.contains(SECONDARY_DELIMITER)) {
                      msgParams.add(params);
                    } else {
                      msgParams.addAll(Arrays.asList(params.split(Pattern.quote(SECONDARY_DELIMITER))));
                    }
                  }
                  Message message = Message.builder()
                                        .message(messageName)
                                        .params(msgParams)
                                        .timestamp(timestamp)
                                        .fromType(fromType)
                                        .fromProcess(fromProcess)
                                        .build();
                  if (log.isDebugEnabled()) {
                    String input =
                        isInput ? "Read message" : "Retrieved message from " + sourceType + " " + sourceProcessId;
                    log.debug("{}: {}", input, message);
                  }
                  messageTimestamps.put(channel, timestamp);
                  reader.close();
                  return message;
                } catch (IllegalArgumentException e) {
                  log.error(
                      "Error parsing message from channel {} {}. Original line {}", sourceType, sourceProcessId, line);
                  return null; // So we keep the old behavior and don't skip the message even if invalid.
                }
              }
            }
          }
          reader.close();
          Thread.sleep(200L);
        }
      });
    } catch (UncheckedTimeoutException e) {
      log.debug("Timed out reading message from channel {} {}", sourceType, sourceProcessId);
    } catch (Exception e) {
      log.error("Error reading message from channel {} {}", sourceType, sourceProcessId, e);
    }
    return null;
  }

  @Override
  public Runnable getMessageCheckingRunnable(long readTimeout, Consumer<Message> messageHandler) {
    return getMessageCheckingRunnableForChannel(messengerType, processId, readTimeout, messageHandler);
  }

  @Override
  public void shutdown() {
    running.set(false);
  }

  @Override
  public Runnable getMessageCheckingRunnableForChannel(
      MessengerType sourceType, String sourceProcessId, long readTimeout, Consumer<Message> messageHandler) {
    try {
      messageQueues.putIfAbsent(getMessageChannel(sourceType, sourceProcessId), new LinkedBlockingQueue<>());
    } catch (IOException e) {
      log.error("Couldn't get message channel for {} {}", sourceType, sourceProcessId);
    }
    return new Schedulable(
        format("Error while checking for message from channel %s %s", sourceType, sourceProcessId), () -> {
          if (running.get()) {
            Message message = readMessageFromChannel(sourceType, sourceProcessId, readTimeout);
            if (message != null) {
              if (messageHandler != null) {
                messageHandler.accept(message);
              }
              try {
                if (messageQueues.get(getMessageChannel(sourceType, sourceProcessId)) != null) {
                  messageQueues.get(getMessageChannel(sourceType, sourceProcessId)).put(message);
                } else {
                  log.warn("Failed attempt to read from closed channel");
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } catch (IOException e) {
                throw new GeneralException(e.getMessage(), e);
              }
            }
          }
        });
  }

  @Override
  public Message waitForMessage(String messageName, long timeout, boolean printIntermediateMessages) {
    return waitForMessageOnChannel(messengerType, processId, messageName, timeout, printIntermediateMessages);
  }

  @Override
  public List<Message> waitForMessages(String messageName, long timeout, long minWaitTime) {
    return waitForMessagesOnChannel(messengerType, processId, messageName, timeout, minWaitTime);
  }

  @Override
  public Message waitForMessageOnChannel(MessengerType sourceType, String sourceProcessId, String messageName,
      long timeout, boolean printIntermediateMessages) {
    try {
      BlockingQueue<Message> queue = messageQueues.get(getMessageChannel(sourceType, sourceProcessId));
      if (queue == null) {
        RuntimeException ex = new RuntimeException(
            "To wait for a message you must first schedule the runnable returned by getMessageCheckingRunnable[ForChannel] at regular intervals.");
        log.error(ex.getMessage(), ex);
        throw ex;
      }
      return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMillis(timeout), () -> {
        Message message = null;
        while (message == null || !messageName.equals(message.getMessage())) {
          try {
            message = queue.take();
            if (printIntermediateMessages && message != null) {
              log.info("Message received: {}", message.getMessage());
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          Thread.sleep(200L);
        }
        return message;
      });
    } catch (UncheckedTimeoutException e) {
      log.debug("Timed out waiting for message {} from channel {} {}", messageName, sourceType, sourceProcessId);
    } catch (Exception e) {
      log.error("Error while waiting for message {} from channel {} {}", messageName, sourceType, sourceProcessId, e);
    }
    return null;
  }

  @Override
  public List<Message> waitForMessagesOnChannel(
      MessengerType sourceType, String sourceProcessId, String messageName, long timeout, long minWaitTime) {
    try {
      BlockingQueue<Message> queue = messageQueues.get(getMessageChannel(sourceType, sourceProcessId));
      if (queue == null) {
        RuntimeException ex = new RuntimeException(
            "To wait for a message you must first schedule the runnable returned by getMessageCheckingRunnable[ForChannel] at regular intervals.");
        log.error(ex.getMessage(), ex);
        throw ex;
      }
      return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMillis(timeout), () -> {
        List<Message> messages = new ArrayList<>();
        while (messages.isEmpty()) {
          try {
            HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMillis(minWaitTime), () -> {
              while (true) {
                try {
                  Message message = queue.take();
                  if (messageName.equals(message.getMessage())) {
                    messages.add(message);
                  }
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                Thread.sleep(200L);
              }
            });
          } catch (UncheckedTimeoutException e) {
            // Do nothing
          }
          Thread.sleep(200L);
        }
        return messages;
      });
    } catch (UncheckedTimeoutException e) {
      log.debug("Timed out waiting for message {} from channel {} {}", messageName, sourceType, sourceProcessId);
    } catch (Exception e) {
      log.error("Error while waiting for message {} from channel {} {}", messageName, sourceType, sourceProcessId, e);
    }
    return null;
  }

  @Override
  public List<String> listChannels(MessengerType type) {
    File channelDirectory = new File(root + FOUNDATION + IO + type.name().toLowerCase() + "/");
    try {
      FileUtils.forceMkdir(channelDirectory);
    } catch (Exception e) {
      log.error("Error creating channel directory: {}", channelDirectory.getAbsolutePath(), e);
      return null;
    }
    return FileUtils.listFiles(channelDirectory, FILE, null).stream().map(File::getName).collect(toList());
  }

  @Override
  public void closeChannel(MessengerType type, String id) {
    log.debug("Closing channel {} {}", type, id);
    try {
      File channel = getMessageChannel(type, id);
      messageTimestamps.remove(channel);
      messageQueues.remove(channel);
      if (channel.exists()) {
        FileUtils.forceDelete(channel);
      }
    } catch (Exception e) {
      log.error("Error closing channel {} {}", type, id, e);
    }
  }

  @Override
  public void clearChannel(MessengerType type, String id) {
    log.debug("Clearing channel {} {}", type, id);
    try {
      File channel = getMessageChannel(type, id);
      if (channel.exists()) {
        FileUtils.write(channel, "", UTF_8);
      }
      if (messageQueues.get(getMessageChannel(type, id)) != null) {
        log.info("Clearing {} messages from channel.", messageQueues.get(getMessageChannel(type, id)).size());
        messageQueues.get(getMessageChannel(type, id)).clear();
      }
    } catch (Exception e) {
      log.error("Error clearing channel {} {}", type, id, e);
    }
  }

  @Override
  public void putData(String name, String key, Object value) {
    Map<String, Object> data = new HashMap<>();
    data.put(key, value);
    putAllData(name, data);
  }

  @Override
  public void putAllData(String name, Map<String, Object> dataToWrite) {
    log.debug("Writing data to {}: {}", name, dataToWrite);
    try {
      File file = getDataFile(name);
      if (acquireLock(file, ofSeconds(5))) {
        try {
          Map<String, Object> data = getDataMap(file);
          data.putAll(dataToWrite);
          if (file.exists()) {
            FileUtils.forceDelete(file);
          }
          FileUtils.touch(file);
          FileUtils.write(file, JsonUtils.asPrettyJson(data), UTF_8);
        } finally {
          if (!releaseLock(file)) {
            log.error("Failed to release lock {}", file.getPath());
          }
        }
      } else {
        log.error("Failed to acquire lock {}", file.getPath());
      }
    } catch (Exception e) {
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE_ERROR)) {
        log.error("Disk space is full.");
      }
      log.error("Error while writing data to {}. Couldn't store {}", name, dataToWrite, e);
    }
  }

  @Override
  public <T> T getData(String name, String key, Class<T> valueClass) {
    Map<String, Object> allData = getAllData(name);
    if (allData == null) {
      log.error("Error reading data from {}. Couldn't get {}", name, key);
      return null;
    }
    Object value = allData.get(key);
    if (value == null) {
      return null;
    }
    if (!valueClass.isAssignableFrom(value.getClass())) {
      log.error("Value is not an instance of {}: {}", valueClass.getName(), value);
      return null;
    }
    log.debug("Value read from {}: {} = {}", name, key, value);
    return (T) value;
  }

  @Override
  public Map<String, Object> getAllData(String name) {
    try {
      File file = getDataFile(name);
      if (acquireLock(file, ofSeconds(5))) {
        try {
          return getDataMap(file);
        } finally {
          if (!releaseLock(file)) {
            log.error("Failed to release lock {}", file.getPath());
          }
        }
      } else {
        log.error("Failed to acquire lock {}", file.getPath());
      }
    } catch (UncheckedTimeoutException e) {
      log.error("Timed out reading data from {}", name);
    } catch (Exception e) {
      log.error("Error reading data from {}", name, e);
    }
    return null;
  }

  @Override
  public List<String> listDataNames(@Nullable String prefix) {
    File dataDirectory = new File(root + FOUNDATION + DATA);
    try {
      FileUtils.forceMkdir(dataDirectory);
    } catch (Exception e) {
      log.error("Error creating data directory: {}", dataDirectory.getAbsolutePath(), e);
      return null;
    }
    return FileUtils
        .listFiles(dataDirectory,
            new AndFileFilter(asList(FILE, new PrefixFileFilter(prefix == null ? "" : prefix),
                new NotFileFilter(new SuffixFileFilter(".lock")))),
            null)
        .stream()
        .map(File::getName)
        .collect(toList());
  }

  @Override
  public void removeData(String name, String key) {
    log.debug("Removing data from {}: {}", name, key);
    try {
      File file = getDataFile(name);
      if (acquireLock(file, ofSeconds(5))) {
        try {
          Map<String, Object> data = getDataMap(file);
          data.remove(key);
          if (file.exists()) {
            FileUtils.forceDelete(file);
          }
          FileUtils.touch(file);
          FileUtils.write(file, JsonUtils.asPrettyJson(data), UTF_8);
        } finally {
          if (!releaseLock(file)) {
            log.error("Failed to release lock {}", file.getPath());
          }
        }
      } else {
        log.error("Failed to acquire lock {}", file.getPath());
      }
    } catch (UncheckedTimeoutException e) {
      log.error("Timed out removing data from {}. Couldn't remove {}", name, key);
    } catch (Exception e) {
      log.error("Error removing data from {}. Couldn't remove {}", name, key, e);
    }
  }

  @Override
  public void closeData(String name) {
    log.debug("Closing data: {}", name);
    try {
      File file = getDataFile(name);
      if (file.exists()) {
        FileUtils.forceDelete(file);
      }
    } catch (Exception e) {
      log.error("Error closing data: {}", name, e);
    }
  }

  @Override
  public void logAllMessages(MessengerType sourceType, String sourceProcessId) {
    try {
      File channel = getMessageChannel(sourceType, sourceProcessId);
      if (channel.exists()) {
        LineIterator reader = FileUtils.lineIterator(channel);
        while (reader.hasNext()) {
          log.error(reader.nextLine());
        }
      }
    } catch (IOException e) {
      log.error("Couldn't read channel for {} {}.", sourceType, sourceProcessId, e);
    }
  }

  private File getMessageChannel(MessengerType type, String id) throws IOException {
    File channel = new File(root + FOUNDATION + IO + type.name().toLowerCase() + "/" + id);
    FileUtils.forceMkdirParent(channel);
    return channel;
  }

  private File getDataFile(String name) throws IOException {
    File file = new File(root + FOUNDATION + DATA + name);
    FileUtils.forceMkdirParent(file);
    return file;
  }

  private Map<String, Object> getDataMap(File file) throws Exception {
    return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(1), () -> {
      while (true) {
        Map<String, Object> data = null;
        if (file.exists()) {
          String fileContent = "";
          try {
            fileContent = FileUtils.readFileToString(file, UTF_8);
            data = JsonUtils.asObject(fileContent, HashMap.class);
          } catch (Exception e) {
            log.error("Couldn't read map from {}. File content: \n{}", file.getName(), fileContent, e);
            FileUtils.deleteQuietly(file);
          }
        } else {
          data = new HashMap<>();
        }

        if (data != null) {
          return data;
        }
        Thread.sleep(200L);
      }
    });
  }
}
