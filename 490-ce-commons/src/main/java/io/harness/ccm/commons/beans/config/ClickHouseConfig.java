package io.harness.ccm.commons.beans.config;

import io.harness.secret.ConfigSecret;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClickHouseConfig {
  @JsonProperty(defaultValue = "jdbc:ch:http://localhost:8123") String url;
  @ConfigSecret String username;
  @ConfigSecret String password;
}
