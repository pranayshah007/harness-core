/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.app;

import io.harness.assessment.serializer.AssessmentServiceRegistrars;
import io.harness.assessment.settings.services.AssessmentEvaluationService;
import io.harness.assessment.settings.services.AssessmentEvaluationServiceImpl;
import io.harness.assessment.settings.services.AssessmentResultService;
import io.harness.assessment.settings.services.AssessmentResultServiceImpl;
import io.harness.assessment.settings.services.AssessmentUploadService;
import io.harness.assessment.settings.services.AssessmentUploadServiceImpl;
import io.harness.assessment.settings.services.BenchmarkService;
import io.harness.assessment.settings.services.BenchmarkServiceImpl;
import io.harness.assessment.settings.services.InvitationService;
import io.harness.assessment.settings.services.InvitationServiceImpl;
import io.harness.metrics.modules.MetricsModule;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.notification.SmtpConfig;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.queue.QueueController;
import io.harness.serializer.KryoRegistrar;
import io.harness.threading.ThreadPool;
import io.harness.version.VersionModule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.core.convert.converter.Converter;

public class AssessmentServiceModule extends AbstractModule {
  private final AssessmentServiceConfiguration appConfig;

  public AssessmentServiceModule(AssessmentServiceConfiguration appConfig) {
    this.appConfig = appConfig;
  }

  @Override
  protected void configure() {
    registerRequiredBindings();
    install(VersionModule.getInstance());
    install(new AssessmentPersistanceModule());
    install(new AbstractMongoModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(AssessmentServiceRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(AssessmentServiceRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder().build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder().build();
      }

      @Override
      public UserProvider userProvider() {
        return new NoopUserProvider();
      }

      @Provides
      @Singleton
      @Named("dbAliases")
      public List<String> getDbAliases() {
        return appConfig.getDbAliases();
      }

      @Provides
      @Singleton
      @Named("morphiaClasses")
      Map<Class, String> morphiaCustomCollectionNames() {
        return ImmutableMap.<Class, String>builder().build();
      }
    });
    install(new MetricsModule());
    install(new AbstractModule() {
      @Override
      protected void configure() {
        bind(QueueController.class).toInstance(new QueueController() {
          @Override
          public boolean isPrimary() {
            return true;
          }

          @Override
          public boolean isNotPrimary() {
            return false;
          }
        });
      }
    });

    bind(AssessmentServiceConfiguration.class).toInstance(appConfig);
    bind(AssessmentUploadService.class).to(AssessmentUploadServiceImpl.class);
    bind(AssessmentEvaluationService.class).to(AssessmentEvaluationServiceImpl.class);
    bind(InvitationService.class).to(InvitationServiceImpl.class);
    bind(AssessmentResultService.class).to(AssessmentResultServiceImpl.class);
    bind(BenchmarkService.class).to(BenchmarkServiceImpl.class);
    // Keeping it to 1 thread to start with. Assuming executor service is used only to
    // serve health checks. If it's being used for other tasks also, max pool size should be increased.
    bind(ExecutorService.class)
        .toInstance(ThreadPool.create(1, 2, 5, TimeUnit.SECONDS,
            new ThreadFactoryBuilder()
                .setNameFormat("default-assessment-service-executor-%d")
                .setPriority(Thread.MIN_PRIORITY)
                .build()));
    bind(HPersistence.class).to(MongoPersistence.class).in(Singleton.class);
    bind(String.class).annotatedWith(Names.named("baseUrl")).toInstance(appConfig.getBaseUrl());
  }

  @Provides
  @Singleton
  public MongoConfig mongoConfig() {
    return appConfig.getMongoConfig();
  }

  @Provides
  @Singleton
  public SmtpConfig smtpConfig() {
    return appConfig.getSmtpConfig();
  }

  private void registerRequiredBindings() {
    requireBinding(HPersistence.class);
  }
}
