/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.mongo.MongoConfig;
import io.harness.springdata.SpringPersistenceModule;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.util.List;
import org.springframework.core.convert.converter.Converter;

public class DetectiveAppModule extends AbstractModule {
  private static DetectiveAppModule instance;
  private final AppConfig appConfig;

  private DetectiveAppModule(AppConfig config) {
    this.appConfig = config;
  }

  public static synchronized DetectiveAppModule getInstance(AppConfig config) {
    if (instance == null) {
      instance = new DetectiveAppModule(config);
    }
    return instance;
  }

  @Override
  protected void configure() {
    install(new SpringPersistenceModule());
  }

  @Provides
  @Singleton
  public MongoConfig mongoConfig() {
    return appConfig.getMongoConfig();
  }

  @Provides
  @Singleton
  @Named("primaryMongoClient")
  public MongoClient mongoClient() {
    return MongoClients.create(appConfig.getMongoConfig().getUri());
  }

  @Provides
  @Singleton
  public List<Class<? extends Converter<?, ?>>> springConverters() {
    return ImmutableList.<Class<? extends Converter<?, ?>>>builder().build();
  }
}
