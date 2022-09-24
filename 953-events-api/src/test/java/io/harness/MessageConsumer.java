/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.eventsframework.api.AbstractConsumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.eventsframework.impl.redis.RedisConsumer;
import io.harness.eventsframework.impl.redis.RedisSerialConsumer;
import io.harness.lock.AcquiredLock;
import io.harness.lock.redis.RedisPersistentLocker;
import io.harness.redis.RedisConfig;

import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageConsumer implements Runnable {
  private final String color;
  private final String readVia;
  private final String groupName;
  private final Integer processingTime;
  private final AbstractConsumer client;
  private RedisPersistentLocker redisLocker;

  public MessageConsumer(
      String readVia, RedisConfig redisConfig, String channel, String groupName, Integer processingTime, String color) {
    this.readVia = readVia;
    this.color = color;
    if (readVia.equals("serialConsumerGroups")) {
      this.client =
          RedisSerialConsumer.of(channel, groupName, "hardcodedconsumer", redisConfig, Duration.ofSeconds(10));
    } else {
      this.client = RedisConsumer.of(channel, groupName, redisConfig, Duration.ofSeconds(10), 5);
    }
    this.groupName = groupName;
    this.processingTime = processingTime;
  }

  public MessageConsumer(RedisPersistentLocker redisLocker, RedisConfig redisConfig, String readVia, String channel,
      String groupName, Integer processingTime, String color) {
    this(readVia, redisConfig, channel, groupName, processingTime, color);
    this.redisLocker = redisLocker;
  }

  @SneakyThrows
  @Override
  public void run() {
    if (this.readVia.equals("consumerGroups")) {
      readViaConsumerGroups();
    } else if (this.readVia.equals("serialConsumerGroups")) {
      readViaSerialConsumerGroups();
    }
  }

  private void readViaConsumerGroups() throws InterruptedException, InvalidProtocolBufferException {
    List<Message> messages;
    while (true) {
      messages = client.read(Duration.ofSeconds(2));
      for (Message message : messages) {
        try {
          processMessage(message);
        } catch (Exception e) {
          //        throw e;
          e.printStackTrace();
          log.error("{}Color{} - {}Something is wrong " + e.toString() + "{}", color, ColorConstants.TEXT_RESET,
              ColorConstants.TEXT_RED, ColorConstants.TEXT_RESET);
        }
      }
    }
  }

  private void readViaSerialConsumerGroups() throws InterruptedException, InvalidProtocolBufferException {
    AcquiredLock lock;
    List<Message> messages;
    while (true) {
      lock = this.redisLocker.tryToAcquireLock(groupName, Duration.ofMinutes(1));
      if (lock == null) {
        Thread.sleep(1000);
        continue;
      }

      try {
        messages = client.read(Duration.ofSeconds(2));
        for (Message message : messages) {
          try {
            processMessage(message);
          } catch (Exception e) {
            log.error("{}Color{} - {}Something is wrong " + e.toString() + "{}", color, ColorConstants.TEXT_RESET,
                ColorConstants.TEXT_RED, ColorConstants.TEXT_RESET, e);
          }
        }
        Thread.sleep(1000);
      } catch (Exception e) {
        log.error("{}Color{} - {}Something is wrong " + e.toString() + "{}", color, ColorConstants.TEXT_RESET,
            ColorConstants.TEXT_RED, ColorConstants.TEXT_RESET);
      } finally {
        redisLocker.destroy(lock);
        Thread.sleep(200);
      }
    }
  }

  private void processMessage(Message message) throws InterruptedException, InvalidProtocolBufferException {
    ProjectEntityChangeDTO p = ProjectEntityChangeDTO.parseFrom(message.getMessage().getData());
    if (p.getIdentifier().isEmpty()) {
      throw new IllegalStateException("Bad data sent - " + message.getId());
    }
    log.info("{}Reading messageId: {} for Consumer - {} - pid: {}{}", color, message.getId(), this.client.getName(),
        p.getIdentifier(), ColorConstants.TEXT_RESET);
    Thread.sleep(processingTime);
    log.info("{}Done processing for Consumer - {} - pid: {}{}", color, this.client.getName(), p.getIdentifier(),
        ColorConstants.TEXT_RESET);
    client.acknowledge(message.getId());
  }
}
