package io.harness.clickhouse;

import io.harness.secret.ConfigSecret;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;

@Value
@Builder
@FieldNameConstants(innerTypeName = "ClickHouseConfigFields")
@FieldDefaults(makeFinal = false)
public class ClickHouseConfig {
  @JsonProperty(defaultValue = "jdbc:ch://localhost:8123") @NotEmpty String clickhouseUrl;
  @ConfigSecret String clickhouseUsername;
  @ConfigSecret String clickhousePassword;
  int connectTimeout;
  int socketTimeout;
}
