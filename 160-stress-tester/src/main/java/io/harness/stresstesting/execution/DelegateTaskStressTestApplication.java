/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.stresstesting.execution;

import static io.harness.logging.LoggingInitializer.initializeLogging;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.DelegateServiceGrpc.DelegateServiceBlockingStub;
import io.harness.govern.ProviderModule;
import io.harness.maintenance.MaintenanceController;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.DelegateServiceDriverRegistrars;
import io.harness.serializer.KryoRegistrar;
import io.harness.service.DelegateServiceDriverModule;
import io.harness.testing.DelegateTaskStressTest;
import io.harness.testing.DelegateTaskStressTestStage;
import io.harness.waiter.AbstractWaiterModule;
import io.harness.waiter.WaiterConfiguration;
import io.harness.waiter.WaiterModule;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.protobuf.TextFormat;
import io.dropwizard.Application;
import io.dropwizard.setup.Environment;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.mongodb.morphia.converters.TypeConverter;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateTaskStressTestApplication extends Application<DelegateTaskStressTestConfiguration> {
  DelegateServiceBlockingStub delegateServiceBlockingStub;

  public static void main(String... args) throws Exception {
    initializeLogging();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));
    new DelegateTaskStressTestApplication().run(args);
  }

  @Override
  public void run(DelegateTaskStressTestConfiguration configuration, Environment environment) throws Exception {
    List<Module> modules = new ArrayList<>();
    modules.add(new io.harness.grpc.DelegateServiceDriverGrpcClientModule(
        configuration.getServiceSecret(), configuration.getTarget(), configuration.getAuthority(), false));
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(DelegateServiceDriverRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(DelegateServiceDriverRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      @Named("dbAliases")
      public List<String> getDbAliases() {
        return Collections.EMPTY_LIST;
      }
    });
    modules.add(new io.harness.mongo.AbstractMongoModule() {
      @Override
      public io.harness.persistence.UserProvider userProvider() {
        return new io.harness.persistence.NoopUserProvider();
      }

      @Provides
      @Singleton
      io.harness.mongo.MongoConfig mongoConfig() {
        return io.harness.mongo.MongoConfig.builder().build();
      }
    });
    modules.add(new AbstractWaiterModule() {
      @Override
      public WaiterConfiguration waiterConfiguration() {
        return WaiterConfiguration.builder().persistenceLayer(WaiterConfiguration.PersistenceLayer.MORPHIA).build();
      }
    });
    modules.add(WaiterModule.getInstance());
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        install(DelegateServiceDriverModule.getInstance(false, false));
        bind(io.harness.persistence.HPersistence.class).to(io.harness.mongo.MongoPersistence.class);
        MapBinder<Class, String> morphiaClasses = MapBinder.newMapBinder(
            binder(), new TypeLiteral<Class>() {}, new TypeLiteral<String>() {}, Names.named("morphiaClasses"));
        Multibinder<Class<? extends TypeConverter>> morphiaConverters =
            Multibinder.newSetBinder(binder(), new TypeLiteral<Class<? extends TypeConverter>>() {});
      }
    });

    Injector injector = Guice.createInjector(modules);
    delegateServiceBlockingStub = injector.getInstance(DelegateServiceBlockingStub.class);

    DelegateTaskStressTest.Builder stressTestBuilder = DelegateTaskStressTest.newBuilder();
    TextFormat.getParser().merge(
        IOUtils.toString(new FileInputStream(new File(configuration.getSetupLocation())), StandardCharsets.UTF_8),
        stressTestBuilder);
    DelegateTaskStressTest stressTest = stressTestBuilder.build();
    int stageId = 0;
    for (DelegateTaskStressTestStage stage : stressTest.getStageList()) {
      DelegateTaskStressTestThread thread =
          new DelegateTaskStressTestThread(delegateServiceBlockingStub, stage, "stage " + stageId++);
      thread.start();
    }
  }
}
