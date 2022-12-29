package io.harness.batch.processing.config;

import io.harness.clickhouse.ClickHouseConfig;
import io.harness.clickhouse.ClickHouseService;
import io.harness.clickhouse.ClickHouseServiceImpl;
import io.harness.timescaledb.JooqModule;
import io.harness.timescaledb.TimeScaleDBConfig;
import io.harness.timescaledb.TimeScaleDBService;
import io.harness.timescaledb.TimeScaleDBServiceImpl;
import io.harness.timescaledb.metrics.HExecuteListener;
import io.harness.timescaledb.metrics.QueryStatsPrinter;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@Slf4j

public class BatchProcessingClickHouseModule extends AbstractModule {
  private ClickHouseConfig clickHouseConfig;

  public BatchProcessingClickHouseModule(ClickHouseConfig clickHouseConfig) {
    this.clickHouseConfig = clickHouseConfig;
  }

  @Provides
  @Singleton
  @Named("ClickHouseConfig")
  ClickHouseConfig clickHouseConfig() {
    return clickHouseConfig;
  }

  @Override
  protected void configure() {
    bind(ClickHouseService.class).toInstance(new ClickHouseServiceImpl(clickHouseConfig));
    install(JooqModule.getInstance());
  }
}
