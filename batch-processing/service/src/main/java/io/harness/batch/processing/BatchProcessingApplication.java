/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.timescaledb.TimeScaleDBConfig;
import io.harness.timescaledb.TimeScaleDBConfig.TimeScaleDBConfigFields;

import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.dbcp.BasicDataSource;
import org.jetbrains.annotations.NotNull;
import org.springframework.batch.core.configuration.annotation.BatchConfigurer;
import org.springframework.batch.core.configuration.annotation.DefaultBatchConfigurer;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.embedded.EmbeddedMongoAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootApplication(
    exclude = {DataSourceAutoConfiguration.class, MongoAutoConfiguration.class, MongoDataAutoConfiguration.class,
        EmbeddedMongoAutoConfiguration.class, HazelcastAutoConfiguration.class})
@EnableBatchProcessing(modular = true)
@PropertySource("classpath:batch.properties")
@Slf4j
public class BatchProcessingApplication {
  @Autowired private BatchMainConfig batchMainConfig;

  public static void main(String[] args) {
    new SpringApplicationBuilder(BatchProcessingApplication.class).web(WebApplicationType.NONE).run(args);
  }

  @Bean
  @ConditionalOnProperty(value = "batchJobRepository.timescaleEnabled", havingValue = "true")
  public BatchConfigurer batchConfigurer(DataSource dataSource) throws SQLException {
    BasicDataSource basicDataSource = getBasicDataSource();
    return new DefaultBatchConfigurer(basicDataSource) {
      // TODO: Add transactionManager
      @Override
      public PlatformTransactionManager getTransactionManager() {
        return new ResourcelessTransactionManager();
      }
    };
  }

  @NotNull
  private BasicDataSource getBasicDataSource() {
    TimeScaleDBConfig timeScaleDBConfig = batchMainConfig.getTimeScaleDBConfig();

    BasicDataSource basicDataSource = new BasicDataSource();
    basicDataSource.setUrl(timeScaleDBConfig.getTimescaledbUrl());
    if (!isEmpty(timeScaleDBConfig.getTimescaledbUsername())) {
      basicDataSource.setUsername(timeScaleDBConfig.getTimescaledbUsername());
    }
    if (!isEmpty(timeScaleDBConfig.getTimescaledbPassword())) {
      basicDataSource.setPassword(timeScaleDBConfig.getTimescaledbPassword());
    }

    basicDataSource.setMinIdle(0);
    basicDataSource.setMaxIdle(10);

    basicDataSource.addConnectionProperty(
        TimeScaleDBConfigFields.connectTimeout, String.valueOf(timeScaleDBConfig.getSocketTimeout()));
    basicDataSource.addConnectionProperty(
        TimeScaleDBConfigFields.socketTimeout, String.valueOf(timeScaleDBConfig.getSocketTimeout()));
    basicDataSource.addConnectionProperty(
        TimeScaleDBConfigFields.logUnclosedConnections, String.valueOf(timeScaleDBConfig.isLogUnclosedConnections()));
    return basicDataSource;
  }
}
