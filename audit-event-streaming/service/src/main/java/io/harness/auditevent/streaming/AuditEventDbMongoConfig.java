/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

@Data
@Configuration
@PropertySources({
  @PropertySource(value = "classpath:application.yml", factory = YamlPropertyLoaderFactory.class)
  ,
      @PropertySource(
          value = "file:./application.yml", ignoreResourceNotFound = true, factory = YamlPropertyLoaderFactory.class)
})
public class AuditEventDbMongoConfig {
  @Value("${auditDbConfig.uri}") private String uri;

  @Value("${auditDbConfig.connectTimeout}") private int connectTimeout = 30000;

  @Value("${auditDbConfig.serverSelectionTimeout}") private int serverSelectionTimeout = 90000;

  @Value("${auditDbConfig.socketTimeout}") private int socketTimeout = 360000;

  @Value("${auditDbConfig.maxConnectionIdleTime}") private int maxConnectionIdleTime = 600000;

  @Value("${auditDbConfig.connectionsPerHost}") private int connectionsPerHost = 300;
}
