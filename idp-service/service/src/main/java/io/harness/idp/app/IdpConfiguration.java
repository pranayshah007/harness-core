/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.app;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.logstreaming.LogStreamingServiceConfiguration;
import io.harness.mongo.MongoConfig;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.secret.ConfigSecret;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.Path;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Getter
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class IdpConfiguration extends Configuration {
  @Setter @JsonProperty("mongo") private MongoConfig mongoConfig;
  @JsonProperty("eventsFramework") private EventsFrameworkConfiguration eventsFrameworkConfiguration;
  @JsonProperty("logStreamingServiceConfig")
  @ConfigSecret
  private LogStreamingServiceConfiguration logStreamingServiceConfig;
  @JsonProperty("ngManagerServiceHttpClientConfig") private ServiceHttpClientConfig ngManagerServiceHttpClientConfig;
  @JsonProperty("ngManagerServiceSecret") private String ngManagerServiceSecret;
  @JsonProperty("backstageSaToken") private String backstageSaToken;
  @JsonProperty("backstageSaCaCrt") private String backstageSaCaCrt;
  @JsonProperty("backstageMasterUrl") private String backstageMasterUrl;
  public static final Collection<Class<?>> HARNESS_RESOURCE_CLASSES = getResourceClasses();
  public static final String IDP_SPEC_PACKAGE = "io.harness.spec.server.idp.v1";

  public List<String> getDbAliases() {
    List<String> dbAliases = new ArrayList<>();
    if (mongoConfig != null) {
      dbAliases.add(mongoConfig.getAliasDBName());
    }
    return dbAliases;
  }

  public static Collection<Class<?>> getResourceClasses() {
    return HarnessReflections.get()
        .getTypesAnnotatedWith(Path.class)
        .stream()
        .filter(klazz -> StringUtils.startsWithAny(klazz.getPackage().getName(), IDP_SPEC_PACKAGE))
        .collect(Collectors.toSet());
  }
}
