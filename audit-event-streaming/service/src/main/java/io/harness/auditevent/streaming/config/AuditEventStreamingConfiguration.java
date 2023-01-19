/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.harness.serializer.YamlUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Slf4j
@Configuration
public class AuditEventStreamingConfiguration {
  @Bean
  public static AuditEventStreamingConfig auditEventStreamingConfig(Environment environment) throws IOException {
    String fileName = "application.yml";
    InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName);
    if (inputStream != null) {
      InputStreamReader inputStreamReader = new InputStreamReader(inputStream, UTF_8);
      BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
      String fileContent = bufferedReader.lines().collect(Collectors.joining(System.lineSeparator()));
      return new YamlUtils().read(fileContent, AuditEventStreamingConfig.class);
    } else {
      throw new NotFoundException(String.format("File %s not found or could not be read.", fileName));
    }
  }
}
