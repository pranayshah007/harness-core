/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;

@Configuration
public class AuditEventBatchPersistenceConfig extends AbstractMongoClientConfiguration {
  @Autowired private AuditEventDbMongoConfig auditDbConfig;

  @Override
  public MongoClient mongoClient() {
    return MongoClients.create(
        MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(auditDbConfig.getUri()))
            .applyToSocketSettings(
                builder -> builder.connectTimeout(auditDbConfig.getConnectTimeout(), TimeUnit.MILLISECONDS))
            .applyToConnectionPoolSettings(builder
                -> builder.maxConnectionIdleTime(auditDbConfig.getMaxConnectionIdleTime(), TimeUnit.MILLISECONDS))
            .build());
  }

  @Override
  protected String getDatabaseName() {
    return "ng-audits";
  }
}
