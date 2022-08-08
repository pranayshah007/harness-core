/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.signup;

import static io.harness.annotations.dev.HarnessTeam.GTM;
import static io.harness.configuration.DeployMode.DEPLOY_MODE;

import io.harness.AccessControlClientConfiguration;
import io.harness.AccessControlClientModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.configuration.DeployMode;
import io.harness.ff.FeatureFlagModule;
import io.harness.remote.client.ServiceHttpClientConfig;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@OwnedBy(GTM)
public class SignupModule extends AbstractModule {
  private final ServiceHttpClientConfig serviceHttpClientConfig;
  private final String managerServiceSecret;
  private final String clientId;
  private final SignupNotificationConfiguration notificationConfiguration;
  private final AccessControlClientConfiguration accessControlClientConfiguration;
  private static final int NUMBER_OF_NOTIFICATION_THREADS = 10;

  public SignupModule(ServiceHttpClientConfig serviceHttpClientConfig, String managerServiceSecret, String clientId,
      SignupNotificationConfiguration notificationConfiguration,
      AccessControlClientConfiguration accessControlClientConfiguration) {
    this.serviceHttpClientConfig = serviceHttpClientConfig;
    this.managerServiceSecret = managerServiceSecret;
    this.clientId = clientId;
    this.notificationConfiguration = notificationConfiguration;
    this.accessControlClientConfiguration = accessControlClientConfiguration;
  }

  @Override
  protected void configure() {
    install(FeatureFlagModule.getInstance());
    install(UserClientModule.getInstance(serviceHttpClientConfig, managerServiceSecret, clientId));
    install(AccessControlClientModule.getInstance(accessControlClientConfiguration, managerServiceSecret));
    install(GoogleCloudFileModule.getInstance());
    bind(SignupService.class).to(SignupServiceImpl.class);
    String deployMode = System.getenv().get(DEPLOY_MODE);

    if (DeployMode.isOnPrem(deployMode)) {
      bind(SignupNotificationHelper.class).to(OnPremSignupNotificationHelper.class);
    } else {
      bind(SignupNotificationHelper.class).to(SaasSignupNotificationHelper.class);
      bind(SignupNotificationTemplateLoader.class);
    }
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
}
