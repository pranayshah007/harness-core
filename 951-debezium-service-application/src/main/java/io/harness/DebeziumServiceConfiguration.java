package io.harness;

import io.harness.debezium.DebeziumConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Singleton;
import io.dropwizard.Configuration;
import lombok.Data;

@Data
@Singleton
public class DebeziumServiceConfiguration extends Configuration {
  @JsonProperty("debeziumConfig") private DebeziumConfig debeziumConfig;
}