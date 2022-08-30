/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.modules.platform;

import io.harness.delegate.app.modules.common.DelegateHealthModule;
import io.harness.delegate.app.modules.common.DelegateManagerGrpcClientModule;
import io.harness.delegate.app.modules.common.DelegateTokensModule;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.logstreaming.LogStreamingModule;
import io.harness.delegate.app.modules.common.DelegateManagerClientModule;
import io.harness.metrics.MetricRegistryModule;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import io.harness.serializer.KryoModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@RequiredArgsConstructor
@Slf4j
public class DelegatePlatformModule extends AbstractModule {
  private final DelegateConfiguration configuration;

  @Override
  protected void configure() {
    super.configure();

    if (StringUtils.isNotEmpty(configuration.getClientCertificateFilePath())
        && StringUtils.isNotEmpty(configuration.getClientCertificateKeyFilePath())) {
      log.info("Delegate is running with mTLS enabled.");
    }

    install(new DelegateTokensModule(configuration));
    install(new DelegateHealthModule());
    install(KryoModule.getInstance());
    install(new DelegatePlatformKryoModule());
    install(new MetricRegistryModule(new MetricRegistry()));

    install(new DelegateManagerClientModule());

    install(new LogStreamingModule(configuration.getLogStreamingServiceBaseUrl(),
        configuration.getClientCertificateFilePath(), configuration.getClientCertificateKeyFilePath(),
        configuration.isTrustAllCertificates()));
    install(new DelegateManagerGrpcClientModule(configuration));

    //        configureCcmEventPublishing();
    //        install(new PerpetualTaskWorkerModule());

    //        install(KubernetesClientFactoryModule.getInstance());
    //        install(KubernetesApiClientFactoryModule.getInstance());
    //        install(new CITaskFactoryModule());

    //========== Replace ==========
    //    install(new DelegateModule(configuration));
    install(new DelegateExecutorsModule(configuration.isDynamicHandlingOfRequestEnabled())); // Check if some can be removed
    install(new DelegateCommonModule(configuration));
    install(new DelegateTaskModule());

    //========== Maybe ============
    /*        if (configuration.isGrpcServiceEnabled()) {
                install(DelegateServiceGrpcAgentClientModule.getInstance());
                install(
                        new DelegateGrpcServiceModule(configuration.getGrpcServiceConnectorPort(),
       configuration.getDelegateToken()));
            }*/
    //==============================
  }

  //    private void configureCcmEventPublishing() {
  //        final String deployMode = System.getenv(DEPLOY_MODE);
  //        if (!isOnPrem(deployMode)) {
  //            final String managerHostAndPort = System.getenv("MANAGER_HOST_AND_PORT");
  //            if (isNotBlank(managerHostAndPort)) {
  //                log.info("Running immutable delegate, starting CCM event tailer");
  //                final DelegateTailerModule.Config tailerConfig =
  //                        DelegateTailerModule.Config.builder()
  //                                .queueFilePath(configuration.getQueueFilePath())
  //                                .publishTarget(extractTarget(managerHostAndPort))
  //                                .publishAuthority(extractAndPrepareAuthority(
  //                                        managerHostAndPort, "events",
  //                                        configuration.isGrpcAuthorityModificationDisabled()))
  //                                .clientCertificateFilePath(configuration.getClientCertificateFilePath())
  //                                .clientCertificateKeyFilePath(configuration.getClientCertificateKeyFilePath())
  //                                .trustAllCertificates(configuration.isTrustAllCertificates())
  //                                .build();
  //                install(new DelegateTailerModule(tailerConfig));
  //            } else {
  //                log.warn("Unable to configure event publisher configs. Event publisher will be disabled");
  //            }
  //        } else {
  //            log.info("Skip running tailer by delegate. For mutable it runs in watcher, for on prem we never run
  //            it.");
  //        }
  //        final AppenderModule.Config appenderConfig =
  //        AppenderModule.Config.builder().queueFilePath(configuration.getQueueFilePath()).build(); install(new
  //        AppenderModule(appenderConfig, () -> getDelegateId().orElse("UNREGISTERED")));
  //    }
}
