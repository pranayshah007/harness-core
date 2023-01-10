/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.authenticationsettings;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.eventsframework.EventsFrameworkConstants.SAML_AUTHORIZATION_ASSERTION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.impl.redis.RedisTraceConsumer;
import io.harness.eventsframework.samlauthorization.samlauthorizationdata.SamlAuthorizationDTO;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@Singleton
public class SamlAuthorizationStreamConsumer extends RedisTraceConsumer {
  private static final int WAIT_TIME_IN_SECONDS = 10;
  private final Consumer redisConsumer;
  @Inject private NGSamlUserGroupSync ngSamlUserGroupSync;
  @Inject
  public SamlAuthorizationStreamConsumer(@Named(SAML_AUTHORIZATION_ASSERTION) Consumer redisConsumer) {
    this.redisConsumer = redisConsumer;
  }

  @Override
  public void run() {
    log.info("Started the consumer for saml assertion stream");
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));
      while (!Thread.currentThread().isInterrupted()) {
        readEventsFrameworkMessages();
      }
    } catch (InterruptedException ex) {
      SecurityContextBuilder.unsetCompleteContext();
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      log.error("saml assertion stream consumer unexpectedly stopped", ex);
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
  }

  private void readEventsFrameworkMessages() throws InterruptedException {
    try {
      pollAndProcessMessages();
    } catch (EventsFrameworkDownException e) {
      log.error("Events framework is down for saml assertion stream consumer. Retrying again...", e);
      TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
    }
  }

  private void pollAndProcessMessages() {
    List<Message> messages;
    String messageId;
    boolean messageProcessed;
    messages = redisConsumer.read(Duration.ofSeconds(10));
    for (Message message : messages) {
      messageId = message.getId();
      messageProcessed = handleMessage(message);
      if (messageProcessed) {
        redisConsumer.acknowledge(messageId);
      }
    }
  }

  @Override
  protected boolean processMessage(Message message) {
    if (message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      SamlAuthorizationDTO samlAuthorizationDTO;
      try {
        samlAuthorizationDTO = SamlAuthorizationDTO.parseFrom(message.getMessage().getData());
        ngSamlUserGroupSync.syncUserGroup(samlAuthorizationDTO.getAccountIdentifier(), samlAuthorizationDTO.getSsoId(),
            samlAuthorizationDTO.getEmail(), samlAuthorizationDTO.getUserGroupsList());
      } catch (InvalidProtocolBufferException e) {
        log.error("Exception in unpacking samlAuthorizationDTO for key {}", message.getId(), e);
        throw new IllegalStateException(e);
      }
    }

    return true;
  }
}
