/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

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
