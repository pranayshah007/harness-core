/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkConstants.DEFAULT_MAX_PROCESSING_TIME;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_ACTIVITY;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_ACTIVITY_MAX_PROCESSING_TIME;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD_MAX_PROCESSING_TIME;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_CONFIG_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_FULL_SYNC_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.INSTANCE_STATS;
import static io.harness.eventsframework.EventsFrameworkConstants.LDAP_GROUP_SYNC;
import static io.harness.eventsframework.EventsFrameworkConstants.MODULE_LICENSE;
import static io.harness.eventsframework.EventsFrameworkConstants.MODULE_LICENSE_MAX_PROCESSING_TIME;
import static io.harness.eventsframework.EventsFrameworkConstants.SAML_AUTHORIZATION_ASSERTION;
import static io.harness.eventsframework.EventsFrameworkConstants.SETUP_USAGE;
import static io.harness.eventsframework.EventsFrameworkConstants.SETUP_USAGE_MAX_PROCESSING_TIME;
import static io.harness.eventsframework.EventsFrameworkConstants.USERMEMBERSHIP;
import static io.harness.eventsframework.EventsFrameworkConstants.USERMEMBERSHIP_MAX_PROCESSING_TIME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.impl.FullSyncMessageConsumer;
import io.harness.ldap.scheduler.LdapGroupSyncStreamConsumer;
import io.harness.ng.authenticationsettings.SamlAuthorizationStreamConsumer;
import io.harness.ng.core.event.modulelicense.ModuleLicenseStreamConsumer;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class NGEventConsumerService implements Managed {
  @Inject private NGAccountSetupConsumer ngAccountSetupConsumer;
  @Inject private EntityCRUDStreamConsumer entityCRUDStreamConsumer;
  @Inject private UserMembershipStreamConsumer userMembershipStreamConsumer;
  @Inject private ModuleLicenseStreamConsumer moduleLicenseStreamConsumer;
  @Inject private SetupUsageStreamConsumer setupUsageStreamConsumer;
  @Inject private EntityActivityStreamConsumer entityActivityStreamConsumer;
  @Inject private SamlAuthorizationStreamConsumer samlAuthorizationStreamConsumer;
  @Inject private LdapGroupSyncStreamConsumer ldapGroupSyncStreamConsumer;
  @Inject private FullSyncMessageConsumer fullSyncMessageConsumer;
  private ExecutorService ngAccountSetupConsumerService;

  @Inject private InstanceStatsStreamConsumer instanceStatsStreamConsumer;
  private ExecutorService entityCRUDConsumerService;
  private ExecutorService setupUsageConsumerService;
  private ExecutorService entityActivityConsumerService;
  private ExecutorService userMembershipConsumerService;
  private ExecutorService moduleLicenseConsumerService;
  private ExecutorService samlAuthorizationConsumerService;
  private ExecutorService ldapGroupSyncConsumerService;
  private ExecutorService instanceStatsConsumerService;
  private ExecutorService gitSyncConfigStreamConsumerService;
  private ExecutorService fullSyncStreamConsumerService;

  @Override
  public void start() {
    ngAccountSetupConsumerService = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setNameFormat("ng_account_setup_consumer").build());
    entityCRUDConsumerService =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(ENTITY_CRUD).build());
    setupUsageConsumerService =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(SETUP_USAGE).build());
    entityActivityConsumerService =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(ENTITY_ACTIVITY).build());
    userMembershipConsumerService =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(USERMEMBERSHIP).build());
    moduleLicenseConsumerService =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(MODULE_LICENSE).build());
    samlAuthorizationConsumerService = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setNameFormat(SAML_AUTHORIZATION_ASSERTION).build());
    ldapGroupSyncConsumerService =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(LDAP_GROUP_SYNC).build());
    instanceStatsConsumerService =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(INSTANCE_STATS).build());
    gitSyncConfigStreamConsumerService =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(GIT_CONFIG_STREAM).build());
    fullSyncStreamConsumerService =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(GIT_FULL_SYNC_STREAM).build());

    entityCRUDConsumerService.execute(entityCRUDStreamConsumer);
    ngAccountSetupConsumerService.execute(ngAccountSetupConsumer);
    setupUsageConsumerService.execute(setupUsageStreamConsumer);
    entityActivityConsumerService.execute(entityActivityStreamConsumer);
    userMembershipConsumerService.execute(userMembershipStreamConsumer);
    moduleLicenseConsumerService.execute(moduleLicenseStreamConsumer);
    samlAuthorizationConsumerService.execute(samlAuthorizationStreamConsumer);
    ldapGroupSyncConsumerService.execute(ldapGroupSyncStreamConsumer);
    instanceStatsConsumerService.execute(instanceStatsStreamConsumer);
    fullSyncStreamConsumerService.execute(fullSyncMessageConsumer);
  }

  @Override
  public void stop() throws InterruptedException {
    ngAccountSetupConsumerService.shutdownNow();
    entityCRUDConsumerService.shutdownNow();
    setupUsageConsumerService.shutdownNow();
    entityActivityConsumerService.shutdownNow();
    userMembershipConsumerService.shutdownNow();
    moduleLicenseConsumerService.shutdownNow();
    samlAuthorizationConsumerService.shutdownNow();
    ldapGroupSyncConsumerService.shutdown();
    gitSyncConfigStreamConsumerService.shutdownNow();
    instanceStatsConsumerService.shutdownNow();
    ngAccountSetupConsumerService.awaitTermination(ENTITY_CRUD_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    entityCRUDConsumerService.awaitTermination(ENTITY_CRUD_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    setupUsageConsumerService.awaitTermination(SETUP_USAGE_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    entityActivityConsumerService.awaitTermination(ENTITY_ACTIVITY_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    userMembershipConsumerService.awaitTermination(USERMEMBERSHIP_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    moduleLicenseConsumerService.awaitTermination(MODULE_LICENSE_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    samlAuthorizationConsumerService.awaitTermination(DEFAULT_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    ldapGroupSyncConsumerService.awaitTermination(DEFAULT_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    instanceStatsConsumerService.awaitTermination(DEFAULT_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    gitSyncConfigStreamConsumerService.awaitTermination(DEFAULT_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
  }
}
