/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.rule;

import static io.harness.network.LocalhostUtils.findFreePort;

import io.harness.event.app.EventServiceConfig;
import io.harness.event.app.EventServiceModule;
import io.harness.event.client.impl.appender.AppenderModule;
import io.harness.event.client.impl.tailer.TailerModule;
import io.harness.factory.ClosingFactory;
import io.harness.factory.ClosingFactoryModule;
import io.harness.govern.ProviderModule;
import io.harness.serializer.PersistenceRegistrars;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.lang.annotation.Annotation;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.mongodb.morphia.converters.TypeConverter;

@Slf4j
public class EventServiceRule implements MethodRule, InjectorRuleMixin, MongoRuleMixin {
  public static final String DEFAULT_ACCOUNT_ID = "kmpySmUISimoRrJL6NL73w";
  public static final String DEFAULT_ACCOUNT_SECRET = "2f6b0988b6fb3370073c3d0505baee59";
  public static final String DEFAULT_DELEGATE_ID = "G0yG0f9gQhKsr1xBErpUFg";

  public static final String QUEUE_FILE_PATH =
      Paths.get(FileUtils.getTempDirectoryPath(), UUID.randomUUID().toString()).toString();

  @Getter private final ClosingFactory closingFactory;

  public EventServiceRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory == null ? new ClosingFactory() : closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) throws Exception {
    List<Module> modules = new ArrayList<>();
    modules.add(new ClosingFactoryModule(closingFactory));
    modules.add(mongoTypeModule(annotations));

    modules.add(new AppenderModule(
        AppenderModule.Config.builder().queueFilePath(QUEUE_FILE_PATH).build(), () -> DEFAULT_DELEGATE_ID));

    int port = findFreePort();

    modules.add(new TailerModule(TailerModule.Config.builder()
                                     .accountId(DEFAULT_ACCOUNT_ID)
                                     .accountSecret(DEFAULT_ACCOUNT_SECRET)
                                     .queueFilePath(QUEUE_FILE_PATH)
                                     .publishTarget("localhost:" + port)
                                     .publishAuthority("localhost")
                                     .minDelay(Duration.ofMillis(10))
                                     .build()));

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(PersistenceRegistrars.morphiaConverters)
            .build();
      }
    });

    modules.add(new EventServiceModule(new EventServiceConfig()));

    modules.add(TestMongoModule.getInstance());
    return modules;
  }

  @Override
  public Statement apply(Statement base, FrameworkMethod method, Object target) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        try {
          applyInjector(log, base, method, target).evaluate();
        } finally {
          closingFactory.stopServers();
        }
      }
    };
  }
}
