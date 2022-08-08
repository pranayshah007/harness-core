/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.springdata;

import static com.google.inject.Key.get;
import static com.google.inject.name.Names.named;

import com.mongodb.client.MongoClient;
import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Injector;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongodbClientCreates;
import org.mongodb.morphia.AdvancedDatastore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.guice.annotation.GuiceModule;

@OwnedBy(HarnessTeam.PL)
@Configuration
@GuiceModule
@EnableMongoRepositories(basePackages = {"io.harness.repositories"},
    includeFilters = @ComponentScan.Filter(HarnessRepo.class), mongoTemplateRef = "primary")
public class SpringPersistenceTestConfig extends AbstractMongoClientConfiguration {
  protected final Injector injector;
  protected final AdvancedDatastore advancedDatastore;
  protected final List<Class<? extends Converter<?, ?>>> springConverters;
  protected final MongoConfig mongoConfig;

  public SpringPersistenceTestConfig(Injector injector, List<Class<? extends Converter<?, ?>>> springConverters) {
    this.injector = injector;
    this.advancedDatastore = injector.getProvider(get(AdvancedDatastore.class, named("primaryDatastore"))).get();
    this.springConverters = springConverters;
    this.mongoConfig = injector.getInstance(MongoConfig.class);
  }

  @Override
  public MongoClient mongoClient() {
    // TODO (xingchi): upgrade morphia and take the mongo client from morphia.
    return null;
  }

  @Override
  protected String getDatabaseName() {
    return advancedDatastore.getDB().getName();
  }

  @Bean(name = "primary")
  @Primary
  public MongoTemplate mongoTemplate() throws Exception {
    final MongoCustomConversions mongoCustomConversions = customConversions();
    final MongoDatabaseFactory mongoDatabaseFactory = mongoDbFactory();
    return new HMongoTemplate(mongoDatabaseFactory, mappingMongoConverter(
            mongoDatabaseFactory,
            mongoCustomConversions,
            mongoMappingContext(mongoCustomConversions)));
  }

  @Override
  protected Collection<String> getMappingBasePackages() {
    return Collections.emptyList();
  }

  @Bean
  public MongoCustomConversions customConversions() {
    List<?> converterInstances = springConverters.stream().map(injector::getInstance).collect(Collectors.toList());
    return new MongoCustomConversions(converterInstances);
  }

  @Override
  protected boolean autoIndexCreation() {
    return false;
  }

  @Bean
  MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
    return new MongoTransactionManager(dbFactory);
  }
}
