/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo;

import io.harness.morphia.MorphiaModule;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.ObjectFactory;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ObjectFactoryModule extends AbstractModule {
  private static volatile ObjectFactoryModule instance;

  public static ObjectFactoryModule getInstance() {
    if (instance == null) {
      instance = new ObjectFactoryModule();
    }
    return instance;
  }

  private ObjectFactoryModule() {}

  @Provides
  @Singleton
  public ObjectFactory objectFactory(
      @Named("morphiaInterfaceImplementersClasses") Map<String, Class> morphiaInterfaceImplementersClasses) {
    return new HObjectFactory(morphiaInterfaceImplementersClasses);
  }

  @Override
  protected void configure() {
    install(MorphiaModule.getInstance());
  }
}
