/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.NextGenConfiguration;
import io.harness.ng.core.invites.api.InviteService;
import io.harness.ng.core.invites.api.impl.InviteServiceImpl;
import io.harness.persistence.HPersistence;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import lombok.AllArgsConstructor;

@OwnedBy(PL)
@AllArgsConstructor
public class InviteModule extends AbstractModule {
  public static final String NG_INVITE_THREAD_EXECUTOR = "ngInviteExecutorService";
  private final boolean isNGAuthUIEnabled;

  @Override
  protected void configure() {
    bind(InviteService.class).to(InviteServiceImpl.class);
    registerRequiredBindings();
  }

  @Provides
  @Named("userVerificationSecret")
  @Singleton
  protected String getUserVerificationSecret(NextGenConfiguration nextGenConfiguration) {
    return nextGenConfiguration.getNextGenConfig().getUserVerificationSecret();
  }

  @Provides
  @Named("isNgAuthUIEnabled")
  public boolean isNGAuthUIEnabled() {
    return isNGAuthUIEnabled;
  }

  private void registerRequiredBindings() {
    requireBinding(HPersistence.class);
    requireBinding(AccessControlAdminClient.class);

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named(NG_INVITE_THREAD_EXECUTOR))
        .toInstance(new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder()
                .setNameFormat("ng-invite-executor-thread-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()));
  }
}
