/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.releaseradar;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.harness.mongo.MongoConfig;
import io.harness.releaseradar.services.JiraTrackerService;
import io.harness.releaseradar.services.JiraTrackerServiceImpl;
import io.harness.springdata.SpringPersistenceModule;
import org.springframework.core.convert.converter.Converter;

import java.util.List;

public class ReleaseRadarAppModule extends AbstractModule {
  private static ReleaseRadarAppModule instance;
  private final AppConfig appConfig;

  private ReleaseRadarAppModule(AppConfig config) {
    this.appConfig = config;
  }

  public static synchronized ReleaseRadarAppModule getInstance(AppConfig config) {
    if (instance == null) {
      instance = new ReleaseRadarAppModule(config);
    }
    return instance;
  }

  @Override
  protected void configure() {
    bind(JiraTrackerService.class).to(JiraTrackerServiceImpl.class);
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
