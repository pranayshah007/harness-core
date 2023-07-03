/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ldap.scheduler;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.eventsframework.EventsFrameworkConstants.LDAP_GROUP_SYNC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.impl.redis.RedisTraceConsumer;
import io.harness.eventsframework.ldapgroupsync.ldapgroupsyncdata.LdapGroupSyncDTO;
import io.harness.ldap.service.NGLdapService;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@Singleton
public class LdapGroupSyncStreamConsumer extends RedisTraceConsumer {
  private static final int WAIT_TIME_IN_SECONDS = 10;
  private final Consumer redisConsumer;
  @Inject NGLdapService ngLdapService;
  @Inject
  public LdapGroupSyncStreamConsumer(@Named(LDAP_GROUP_SYNC) Consumer redisConsumer) {
    this.redisConsumer = redisConsumer;
  }

  @Override
  public void run() {
    log.info("EVENT_LDAP_GROUP_SYNC: Started the consumer for ldap group sync stream");
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));
      while (!Thread.currentThread().isInterrupted()) {
        readEventsFrameworkMessages();
      }
    } catch (InterruptedException ex) {
      SecurityContextBuilder.unsetCompleteContext();
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      log.error("EVENT_LDAP_GROUP_SYNC: ldap group sync stream consumer unexpectedly stopped", ex);
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
  }

  private void readEventsFrameworkMessages() throws InterruptedException {
    try {
      pollAndProcessMessages();
    } catch (EventsFrameworkDownException e) {
      log.error(
          "EVENT_LDAP_GROUP_SYNC: Events framework is down for ldap group sync stream consumer. Retrying again...", e);
      TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
    }
  }

  private void pollAndProcessMessages() {
    List<Message> messages;
    String messageId;
    messages = redisConsumer.read(Duration.ofSeconds(10));
    for (Message message : messages) {
      messageId = message.getId();
      redisConsumer.acknowledge(messageId);
      log.info("EVENT_LDAP_GROUP_SYNC: acknowledged and processing message with id: {}", messageId);
      handleMessage(message);
    }
  }

  @Override
  protected boolean processMessage(Message message) {
    if (message.hasMessage()) {
      LdapGroupSyncDTO ldapGroupSyncDTO;
      try {
        ldapGroupSyncDTO = LdapGroupSyncDTO.parseFrom(message.getMessage().getData());
        ngLdapService.syncUserGroupsJob(ldapGroupSyncDTO.getAccountIdentifier(), null, null);
      } catch (InvalidProtocolBufferException e) {
        log.error("EVENT_LDAP_GROUP_SYNC: Exception in unpacking ldapGroupSyncDTO for key {}", message.getId(), e);
        throw new IllegalStateException(e);
      }
    }

    return true;
  }
}
