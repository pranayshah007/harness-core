/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.platform.notification;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.MongoConfig;
import io.harness.platform.PlatformConfiguration;
import io.harness.reflection.HarnessReflections;
import io.harness.springdata.HMongoTemplate;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoClientURI;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@OwnedBy(PL)
@Configuration
@EnableMongoRepositories(
    basePackages = {"io.harness.notification.repositories"}, includeFilters = @ComponentScan.Filter(HarnessRepo.class))
public class NotificationPersistenceConfig extends AbstractMongoClientConfiguration {
  private final MongoConfig mongoBackendConfiguration;

  @Inject
  public NotificationPersistenceConfig(Injector injector) {
    mongoBackendConfiguration =
        injector.getInstance(Key.get(PlatformConfiguration.class)).getNotificationServiceConfig().getMongoConfig();
  }

  @Override
  public MongoClient mongoClient() {
    MongoClientSettings mongoClientSettings =
        MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(mongoBackendConfiguration.getUri()))
            .retryWrites(true)
            .applyToSocketSettings(
                builder -> builder.connectTimeout(mongoBackendConfiguration.getConnectTimeout(), TimeUnit.MILLISECONDS))
            .applyToClusterSettings(builder
                -> builder.serverSelectionTimeout(
                    mongoBackendConfiguration.getServerSelectionTimeout(), TimeUnit.MILLISECONDS))
            .applyToSocketSettings(
                builder -> builder.readTimeout(mongoBackendConfiguration.getSocketTimeout(), TimeUnit.MILLISECONDS))
            .applyToConnectionPoolSettings(builder
                -> builder.maxConnectionIdleTime(
                    mongoBackendConfiguration.getMaxConnectionIdleTime(), TimeUnit.MILLISECONDS))
            .applyToConnectionPoolSettings(
                builder -> builder.maxSize(mongoBackendConfiguration.getConnectionsPerHost()))
            .readPreference(ReadPreference.primary())
            .build();

    return MongoClients.create(mongoClientSettings);
  }

  @Override
  protected String getDatabaseName() {
    return new MongoClientURI(mongoBackendConfiguration.getUri()).getDatabase();
  }

  @Bean
  MongoTransactionManager transactionManager(MongoDbFactory dbFactory) {
    return new MongoTransactionManager(dbFactory);
  }

  @Override
  protected Set<Class<?>> getInitialEntitySet() {
    return HarnessReflections.get().getTypesAnnotatedWith(TypeAlias.class);
  }

  @Bean
  public MongoTemplate mongoTemplate() throws Exception {
    MongoDbFactory mongoDbFactory = mongoDbFactory();
    DbRefResolver dbRefResolver = new DefaultDbRefResolver(mongoDbFactory);
    MongoMappingContext mappingContext = mongoMappingContext();
    mappingContext.setAutoIndexCreation(false);
    MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, mappingContext);
    converter.setCodecRegistryProvider(mongoDbFactory);
    converter.afterPropertiesSet();
    return new HMongoTemplate(mongoDbFactory, mappingMongoConverter(), mongoBackendConfiguration);
  }
}
