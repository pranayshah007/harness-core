/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.config;

import static io.harness.mongo.MongoConfig.DOT_REPLACEMENT;

import io.harness.mongo.MongoConfig;
import io.harness.reflection.HarnessReflections;
import io.harness.springdata.HMongoTemplate;

import com.google.inject.Injector;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoClientURI;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = {"io.harness.auditevent.streaming.repositories",
                             "io.harness.auditevent.streaming", "io.harness.repositories"})
@EnableMongoAuditing
public class AuditEventStreamingPersistenceConfig extends AbstractMongoClientConfiguration {
  protected final MongoConfig mongoConfig;

  @Autowired
  public AuditEventStreamingPersistenceConfig(Injector injector) {
    this.mongoConfig = injector.getInstance(MongoConfig.class);
  }

  @Override
  public MongoClient mongoClient() {
    return MongoClients.create(
        MongoClientSettings.builder()
            .retryWrites(true)
            .applyConnectionString(new ConnectionString(mongoConfig.getUri()))
            .applyToSocketSettings(
                builder -> builder.connectTimeout(mongoConfig.getConnectTimeout(), TimeUnit.MILLISECONDS))
            .applyToClusterSettings(builder
                -> builder.serverSelectionTimeout(mongoConfig.getServerSelectionTimeout(), TimeUnit.MILLISECONDS))
            .applyToConnectionPoolSettings(
                builder -> builder.maxConnectionIdleTime(mongoConfig.getMaxConnectionIdleTime(), TimeUnit.MILLISECONDS))
            .readPreference(ReadPreference.primary())
            .build());
  }

  @Override
  protected String getDatabaseName() {
    return new MongoClientURI(mongoConfig.getUri()).getDatabase();
  }

  @Bean(name = "primary")
  @Primary
  public MongoTemplate mongoTemplate(MongoDatabaseFactory databaseFactory, MappingMongoConverter converter) {
    return new HMongoTemplate(databaseFactory, converter, mongoConfig);
  }

  @Bean
  public MappingMongoConverter mappingMongoConverter(MongoDatabaseFactory databaseFactory,
      MongoCustomConversions customConversions, MongoMappingContext mappingContext) {
    DbRefResolver dbRefResolver = new DefaultDbRefResolver(databaseFactory);
    MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, mappingContext);
    converter.setCustomConversions(customConversions);
    converter.setCodecRegistryProvider(databaseFactory);
    converter.setMapKeyDotReplacement(DOT_REPLACEMENT);

    return converter;
  }

  @Override
  protected Set<Class<?>> getInitialEntitySet() {
    return HarnessReflections.get().getTypesAnnotatedWith(TypeAlias.class);
  }
}
