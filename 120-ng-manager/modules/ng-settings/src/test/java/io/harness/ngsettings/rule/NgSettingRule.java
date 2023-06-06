/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.rule;

import static org.mockito.Mockito.mock;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.factory.ClosingFactory;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.licensing.services.LicenseService;
import io.harness.lock.PersistentLocker;
import io.harness.mongo.MongoConfig;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ngsettings.NgSettingsPersistenceTestModule;
import io.harness.ngsettings.services.SettingEnforcementValidator;
import io.harness.ngsettings.services.SettingValidator;
import io.harness.ngsettings.services.SettingsService;
import io.harness.ngsettings.services.impl.SettingsServiceImpl;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.api.impl.OutboxServiceImpl;
import io.harness.rule.InjectorRuleMixin;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.NGSettingRegistrar;
import io.harness.springdata.HTransactionTemplate;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class NgSettingRule implements MethodRule, InjectorRuleMixin, MongoRuleMixin {
  ClosingFactory closingFactory;

  public NgSettingRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) {
    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());
    List<Module> modules = new ArrayList<>();
    modules.add(mongoTypeModule(annotations));
    modules.add(TestMongoModule.getInstance());
    modules.add(new NgSettingsPersistenceTestModule());
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(SettingsService.class).to(SettingsServiceImpl.class);
        bind(OutboxService.class).toInstance(mock(OutboxServiceImpl.class));
        bind(TransactionTemplate.class)
            .annotatedWith(Names.named("OUTBOX_TRANSACTION_TEMPLATE"))
            .toInstance(mock(TransactionTemplate.class));
        bind(LicenseService.class).toInstance(mock(LicenseService.class));
        bind(PersistentLocker.class).toInstance(mock(PersistentLocker.class));
        MapBinder.newMapBinder(binder(), String.class, SettingValidator.class);
        MapBinder.newMapBinder(binder(), String.class, SettingEnforcementValidator.class);
      }
    });

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return getKryoRegistrars();
      }
      @Provides
      @Singleton
      TransactionTemplate getTransactionTemplate(MongoTransactionManager mongoTransactionManager) {
        return new HTransactionTemplate(mongoTransactionManager, false);
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder().build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return NGSettingRegistrar.morphiaRegistrars;
      }

      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return MongoConfig.builder().build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(NGSettingRegistrar.morphiaConverters)
            .build();
      }
    });
    return modules;
  }

  @Override
  public void initialize(Injector injector, List<Module> modules) {
    for (Module module : modules) {
      if (module instanceof ServersModule) {
        for (Closeable server : ((ServersModule) module).servers(injector)) {
          closingFactory.addServer(server);
        }
      }
    }
  }

  @Override
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object target) {
    return applyInjector(log, statement, frameworkMethod, target);
  }

  protected Set<Class<? extends KryoRegistrar>> getKryoRegistrars() {
    return ImmutableSet.<Class<? extends KryoRegistrar>>builder().build();
  }
}
