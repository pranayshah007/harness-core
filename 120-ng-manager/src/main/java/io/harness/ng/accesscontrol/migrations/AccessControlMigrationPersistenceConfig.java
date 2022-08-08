/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.accesscontrol.migrations;

import com.mongodb.ConnectionString;
import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongodbClientCreates;
import io.harness.springdata.HMongoTemplate;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.mongodb.client.MongoClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = {"io.harness.ng.accesscontrol.migrations.repositories",
                             "io.harness.ng.accesscontrol.mockserver.repositories"},
    includeFilters = @ComponentScan.Filter(HarnessRepo.class))
@OwnedBy(HarnessTeam.PL)
public class AccessControlMigrationPersistenceConfig extends AbstractMongoClientConfiguration {
  private final MongoConfig mongoBackendConfiguration;

  @Inject
  public AccessControlMigrationPersistenceConfig(Injector injector) {
    this.mongoBackendConfiguration = injector.getInstance(MongoConfig.class);
  }

  @Override
  public MongoClient mongoClient() {
    return MongodbClientCreates.createMongoClient(mongoBackendConfiguration);
  }

  @Override
  protected String getDatabaseName() {
    return new ConnectionString(mongoBackendConfiguration.getUri()).getDatabase();
  }

  @Bean
  MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
    return new MongoTransactionManager(dbFactory);
  }

  @Bean
  public MongoTemplate mongoTemplate() throws Exception {
    MongoDatabaseFactory mongoDbFactory = mongoDbFactory();
    DbRefResolver dbRefResolver = new DefaultDbRefResolver(mongoDbFactory);
    MongoMappingContext mappingContext = mongoMappingContext(customConversions());
    mappingContext.setAutoIndexCreation(false);
    MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, mappingContext);
    converter.setCodecRegistryProvider(mongoDbFactory);
    converter.afterPropertiesSet();
    // TO-ASK: why created new converter while there is already a converter constructed?
    // TO-ASK: why not use the inherited mappingMongoConverter(..) instead of new?
    // (will remove this comment before merge)
    return new HMongoTemplate(mongoDbFactory, converter);
  }
}
