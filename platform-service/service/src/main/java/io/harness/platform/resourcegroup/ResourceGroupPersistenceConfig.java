/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.platform.resourcegroup;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongodbClientCreates;
import io.harness.platform.PlatformConfiguration;
import io.harness.reflection.HarnessReflections;
import io.harness.springdata.HMongoTemplate;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoClient;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@OwnedBy(PL)
@Configuration
@EnableMongoRepositories(basePackages = {"io.harness.resourcegroup", "io.harness.repositories"},
    includeFilters = @ComponentScan.Filter(HarnessRepo.class))
@EnableMongoAuditing
public class ResourceGroupPersistenceConfig extends AbstractMongoClientConfiguration {
  private final MongoConfig mongoBackendConfiguration;

  @Inject
  public ResourceGroupPersistenceConfig(Injector injector) {
    this.mongoBackendConfiguration =
        injector.getInstance(Key.get(PlatformConfiguration.class)).getResoureGroupServiceConfig().getMongoConfig();
  }

  @Override
  public MongoClient mongoClient() {
    return MongodbClientCreates.createMongoClient(mongoBackendConfiguration);
  }

  @Override
  protected String getDatabaseName() {
    return new MongoClientURI(mongoBackendConfiguration.getUri()).getDatabase();
  }

  @Bean
  MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
    return new MongoTransactionManager(dbFactory);
  }

  @Override
  protected Set<Class<?>> getInitialEntitySet() {
    return HarnessReflections.get().getTypesAnnotatedWith(TypeAlias.class);
  }

  @Bean
  public MongoTemplate mongoTemplate() throws Exception {
    final MongoDatabaseFactory mongoDbFactory = mongoDbFactory();
    final MongoCustomConversions mongoCustomConversions = customConversions();
    MongoMappingContext mappingContext = mongoMappingContext(mongoCustomConversions);
    mappingContext.setAutoIndexCreation(false);
    MappingMongoConverter converter = mappingMongoConverter(mongoDbFactory, mongoCustomConversions, mappingContext);
    converter.setCodecRegistryProvider(mongoDbFactory);
    converter.afterPropertiesSet();
    return new HMongoTemplate(mongoDbFactory, converter);
  }
}
