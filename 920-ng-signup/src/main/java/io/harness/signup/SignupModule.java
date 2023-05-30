/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.signup;

import static io.harness.annotations.dev.HarnessTeam.GTM;
import static io.harness.configuration.DeployMode.DEPLOY_MODE;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.SIGNUP_TOKEN;

import io.harness.AccessControlClientConfiguration;
import io.harness.AccessControlClientModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.configuration.DeployMode;
import io.harness.ff.FeatureFlagModule;
import io.harness.ng.core.event.MessageListener;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.signup.event.SignUpTokenEventListener;
import io.harness.signup.notification.OnPremSignupNotificationHelper;
import io.harness.signup.notification.SaasSignupNotificationHelper;
import io.harness.signup.notification.SignupNotificationHelper;
import io.harness.signup.notification.SignupNotificationTemplateLoader;
import io.harness.signup.services.SignupService;
import io.harness.signup.services.impl.SignupServiceImpl;
import io.harness.templates.google.GoogleCloudFileModule;
import io.harness.user.UserClientModule;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@OwnedBy(GTM)
public class SignupModule extends AbstractModule {
  public static final String NG_SIGNUP_EXECUTOR_SERVICE = "ngSignupExecutorService";
  private final ServiceHttpClientConfig serviceHttpClientConfig;
  private final String managerServiceSecret;
  private final String clientId;
  private final SignupNotificationConfiguration notificationConfiguration;
  private final AccessControlClientConfiguration accessControlClientConfiguration;
  private final SignupDomainDenylistConfiguration signupDomainDenylistConfiguration;
  private static final int NUMBER_OF_NOTIFICATION_THREADS = 10;

  public SignupModule(ServiceHttpClientConfig serviceHttpClientConfig, String managerServiceSecret, String clientId,
      SignupNotificationConfiguration notificationConfiguration,
      AccessControlClientConfiguration accessControlClientConfiguration,
      SignupDomainDenylistConfiguration signupDomainDenylistConfiguration) {
    this.serviceHttpClientConfig = serviceHttpClientConfig;
    this.managerServiceSecret = managerServiceSecret;
    this.clientId = clientId;
    this.notificationConfiguration = notificationConfiguration;
    this.accessControlClientConfiguration = accessControlClientConfiguration;
    this.signupDomainDenylistConfiguration = signupDomainDenylistConfiguration;
  }

  @Override
  protected void configure() {
    install(FeatureFlagModule.getInstance());
    install(UserClientModule.getInstance(serviceHttpClientConfig, managerServiceSecret, clientId));
    install(AccessControlClientModule.getInstance(accessControlClientConfiguration, clientId));
    install(GoogleCloudFileModule.getInstance());
    bind(SignupService.class).to(SignupServiceImpl.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(SIGNUP_TOKEN + ENTITY_CRUD))
        .to(SignUpTokenEventListener.class);
    String deployMode = System.getenv().get(DEPLOY_MODE);

    if (DeployMode.isOnPrem(deployMode)) {
      bind(SignupNotificationHelper.class).to(OnPremSignupNotificationHelper.class);
    } else {
      bind(SignupNotificationHelper.class).to(SaasSignupNotificationHelper.class);
      bind(SignupNotificationTemplateLoader.class);
    }

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named(NG_SIGNUP_EXECUTOR_SERVICE))
        .toInstance(new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder()
                .setNameFormat("ng-signup-executor-thread-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()));
  }

  @Provides
  @Singleton
  @Named("NGSignupNotification")
  public ExecutorService NGSignupNotificationExecutor() {
    return Executors.newFixedThreadPool(
        NUMBER_OF_NOTIFICATION_THREADS, new ThreadFactoryBuilder().setNameFormat("ng-signup-notification-%d").build());
  }

  @Provides
  @Singleton
  public SignupNotificationConfiguration notificationConfiguration() {
    return notificationConfiguration;
  }

  @Provides
  @Singleton
  public SignupDomainDenylistConfiguration signupDomainDenylistConfiguration() {
    return signupDomainDenylistConfiguration;
  }
}
