/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cf.CfClientConfig;
import io.harness.ff.FeatureFlagConfig;
import io.harness.mongo.MongoConfig;
import io.harness.secret.ConfigSecret;
import io.harness.timescaledb.TimeScaleDBConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Singleton;
import io.dropwizard.Configuration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.ws.rs.Path;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.reflections.Reflections;

@Data
@EqualsAndHashCode(callSuper = false)
@Singleton
@OwnedBy(HarnessTeam.CE)
public class ChangeDataCaptureServiceConfig extends Configuration {
  public static final String RESOURCE_PACKAGE = "io.harness.resources";

  @JsonProperty("harness-mongo") private MongoConfig harnessMongo = MongoConfig.builder().build();
  @JsonProperty("events-mongo") private MongoConfig eventsMongo = MongoConfig.builder().build();
  @JsonProperty("cdc-mongo") private MongoConfig cdcMongo = MongoConfig.builder().build();
  @JsonProperty("pms-harness") private MongoConfig pmsMongo = MongoConfig.builder().build();
  @JsonProperty("ng-harness") private MongoConfig ngMongo = MongoConfig.builder().build();
  @JsonProperty("timescaledb") private TimeScaleDBConfig timeScaleDBConfig;
  @JsonProperty("mongotags") private MongoTagsConfig mongoTagsConfig = MongoTagsConfig.builder().build();
  @JsonProperty("gcp-project-id") private String gcpProjectId;
  @JsonProperty("debeziumEnabled") private boolean debeziumEnabled;
  @JsonProperty("cfClientConfig") @ConfigSecret private CfClientConfig cfClientConfig;
  @JsonProperty("featureFlagConfig") private FeatureFlagConfig featureFlagConfig;
  @JsonProperty("changeStreamBatchSize") private int changeStreamBatchSize;
  @JsonProperty("cvng") private MongoConfig cvngMongo = MongoConfig.builder().build();

  public static Collection<Class<?>> getResourceClasses() {
    Reflections reflections = new Reflections(RESOURCE_PACKAGE);
    return reflections.getTypesAnnotatedWith(Path.class);
  }

  public List<String> getDbAliases() {
    List<String> dbAliases = new ArrayList<>();
    if (harnessMongo != null) {
      dbAliases.add(harnessMongo.getAliasDBName());
    }
    if (eventsMongo != null) {
      dbAliases.add(eventsMongo.getAliasDBName());
    }
    if (cdcMongo != null) {
      dbAliases.add(cdcMongo.getAliasDBName());
    }
    if (pmsMongo != null) {
      dbAliases.add(pmsMongo.getAliasDBName());
    }
    if (ngMongo != null) {
      dbAliases.add(ngMongo.getAliasDBName());
    }
    if (cvngMongo != null) {
      dbAliases.add(cvngMongo.getAliasDBName());
    }
    return dbAliases;
  }
}
