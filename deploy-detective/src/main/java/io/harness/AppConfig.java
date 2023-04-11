/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.mongo.MongoConfig;
import io.harness.secret.ConfigSecret;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import lombok.Setter;

public class AppConfig extends Configuration {
  @Setter @JsonProperty("mongo") @ConfigSecret private MongoConfig mongoConfig;
}
