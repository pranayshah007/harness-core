/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.springdata;

import static io.harness.mongo.MongoConfig.DOT_REPLACEMENT;
import static io.harness.springdata.PersistenceStoreUtils.getMatchingEntities;

import static com.google.inject.Key.get;
import static com.google.inject.name.Names.named;

import com.mongodb.client.MongoClient;
import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.MongoConfig;
import io.harness.persistence.Store;
import io.harness.reflection.HarnessReflections;

import com.google.inject.Injector;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.mongodb.morphia.AdvancedDatastore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.guice.annotation.GuiceModule;

@Configuration
@GuiceModule
@EnableMongoRepositories(basePackages = {"io.harness.repositories"},
    includeFilters = @ComponentScan.Filter(HarnessRepo.class), mongoTemplateRef = "primary")
@EnableMongoAuditing
@OwnedBy(HarnessTeam.PL)
public class SpringPersistenceConfig extends AbstractMongoClientConfiguration {
  protected final Injector injector;
  protected final AdvancedDatastore advancedDatastore;
  protected final List<Class<? extends Converter<?, ?>>> springConverters;
  protected final MongoConfig mongoConfig;

  public SpringPersistenceConfig(Injector injector, List<Class<? extends Converter<?, ?>>> springConverters) {
    this.injector = injector;
    this.advancedDatastore = injector.getProvider(get(AdvancedDatastore.class, named("primaryDatastore"))).get();
    this.springConverters = springConverters;
    this.mongoConfig = injector.getInstance(MongoConfig.class);
  }

  @Override
  public MongoClient mongoClient() {
    return null;
  }

  @Override
  protected String getDatabaseName() {
    return advancedDatastore.getDB().getName();
  }

  @Bean(name = "primary")
  @Primary
  public MongoTemplate mongoTemplate() throws Exception {
    MongoConfig config = injector.getInstance(MongoConfig.class);
    final MongoCustomConversions mongoCustomConversions = customConversions();
    final MongoDatabaseFactory mongoDatabaseFactory = mongoDbFactory();
    MappingMongoConverter mappingMongoConverter = mappingMongoConverter(
            mongoDatabaseFactory,
            mongoCustomConversions,
            mongoMappingContext(mongoCustomConversions));
    mappingMongoConverter.setMapKeyDotReplacement(DOT_REPLACEMENT);
    return new HMongoTemplate(mongoDatabaseFactory, mappingMongoConverter, config.getTraceMode());
  }

  @Bean
  MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
    return new MongoTransactionManager(dbFactory);
  }

  @Override
  protected Set<Class<?>> getInitialEntitySet() {
    Set<Class<?>> classes = HarnessReflections.get().getTypesAnnotatedWith(TypeAlias.class);
    Store store = null;
    if (Objects.nonNull(mongoConfig.getAliasDBName())) {
      store = Store.builder().name(mongoConfig.getAliasDBName()).build();
    }
    return getMatchingEntities(classes, store);
  }

  @Bean
  public MongoCustomConversions customConversions() {
    List<?> converterInstances = springConverters.stream().map(injector::getInstance).collect(Collectors.toList());
    return new MongoCustomConversions(converterInstances);
  }

  @Bean
  public AuditorAware<EmbeddedUser> auditorAware() {
    return injector.getInstance(SpringSecurityAuditorAware.class);
  }

  @Override
  protected boolean autoIndexCreation() {
    return false;
  }
}
