/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.mongo.IndexManager.Mode.MANUAL;

import static lombok.AccessLevel.NONE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.tracing.TraceMode;
import io.harness.secret.ConfigSecret;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.mongodb.ReadPreference;
import com.mongodb.Tag;
import com.mongodb.TagSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * MongoConfig is used to store the MongoDB connection related configuration.
 * For URI connection string format, see: https://docs.mongodb.com/manual/reference/connection-string
 */

@Value
@Builder(toBuilder = true)
@ToString(onlyExplicitlyIncluded = true)
@OwnedBy(HarnessTeam.PL)
@FieldDefaults(makeFinal = false)
public class MongoConfig {
  public static final String DOT_REPLACEMENT = "__dot__";
  public static final String DEFAULT_URI = "mongodb://localhost:27017/wings";
  public static final String MONGODB_SCHEMA = "mongodb";

  @Value
  public static class ReadPref {
    String name;
    ImmutableMap<String, String> tagSet;
  }

  @Value
  public static class HostAndPort {
    public static HostAndPort of(String host, int port) {
      return new HostAndPort(host, port);
    }

    public static HostAndPort of(String host) {
      return new HostAndPort(host, -1);
    }

    @JsonProperty String host;
    @JsonProperty(defaultValue = "-1") int port;
  }

  @JsonProperty(defaultValue = DEFAULT_URI) @Default @NotEmpty private String uri = DEFAULT_URI;
  @JsonProperty @Getter(NONE) private String schema;
  @JsonProperty @Getter(NONE) private List<HostAndPort> hosts;
  @JsonProperty @Getter(NONE) private String database;
  @JsonProperty @Getter(NONE) @ConfigSecret private String username;
  @JsonProperty @Getter(NONE) @ConfigSecret private String password;
  @JsonProperty @Getter(NONE) private Map<String, String> params;

  @ToString.Include @Getter(NONE) private ReadPref readPref;

  private String locksUri;

  private byte[] encryptedUri;
  private byte[] encryptedLocksUri;

  @NonNull private String aliasDBName;

  @JsonProperty(defaultValue = "30000") @Default @NotEmpty private int connectTimeout = 30000;

  @JsonProperty(defaultValue = "90000") @Default @NotEmpty private int serverSelectionTimeout = 90000;

  @JsonProperty(defaultValue = "600000") @Default @NotEmpty private int maxConnectionIdleTime = 600000;

  @JsonProperty(defaultValue = "300") @Default @NotEmpty private int connectionsPerHost = 300;

  private MongoSSLConfig mongoSSLConfig = MongoSSLConfig.builder().build();

  private boolean transactionsEnabled;

  private TraceMode traceMode = TraceMode.DISABLED;

  @JsonProperty(defaultValue = "MANUAL") @Default @NotEmpty private IndexManager.Mode indexManagerMode = MANUAL;

  private AnalyticNodeMongoConfig analyticNodeConfig = AnalyticNodeMongoConfig.builder().build();

  @JsonIgnore
  public ReadPreference getReadPreference() {
    if (readPref == null || readPref.getName() == null || readPref.getName().equals("primary")) {
      return ReadPreference.primary();
    } else {
      return ReadPreference.valueOf(readPref.getName(),
          Collections.singletonList(new TagSet(Optional.ofNullable(readPref.getTagSet())
                                                   .orElse(ImmutableMap.of())
                                                   .entrySet()
                                                   .stream()
                                                   .map(entry -> new Tag(entry.getKey(), entry.getValue()))
                                                   .collect(Collectors.toList()))));
    }
  }

  public String getUri() {
    if (uriPartsAreNotUsed()) {
      return uri;
    }

    final URIBuilder uriBuilder =
        new URIBuilder().setScheme(schema()).setHost(hosts()).setPath(forceSlashAfterHostWhenThereAreParams());

    if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
      uriBuilder.setUserInfo(username, password);
    } else if (StringUtils.isNotBlank(username)) {
      uriBuilder.setUserInfo(username);
    }

    if (params != null) {
      params.forEach(uriBuilder::setParameter);
    }

    return uriBuilder.toString();
  }

  private String schema() {
    if (StringUtils.isNotBlank(schema)) {
      return schema;
    } else {
      return MONGODB_SCHEMA;
    }
  }

  private String hosts() {
    return hosts.stream()
        .map(host -> {
          if (host.port > 0) {
            return host.host + ":" + host.port;
          } else {
            return host.host;
          }
        })
        .collect(Collectors.joining(","));
  }

  private String forceSlashAfterHostWhenThereAreParams() {
    if (isEmpty(params)) {
      return database;
    } else {
      return StringUtils.defaultIfBlank(database, "/");
    }
  }

  private boolean uriPartsAreNotUsed() {
    return isEmpty(hosts);
  }
}
