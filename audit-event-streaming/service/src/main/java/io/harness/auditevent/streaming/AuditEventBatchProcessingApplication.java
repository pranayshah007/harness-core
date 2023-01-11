/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming;

import static io.harness.reflection.HarnessReflections.CLASSPATH_METADATA_FILE_NAME;

import io.harness.packages.HarnessPackages;

import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.reflections.serializers.JsonSerializer;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.embedded.EmbeddedMongoAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, MongoAutoConfiguration.class,
                           EmbeddedMongoAutoConfiguration.class, HazelcastAutoConfiguration.class})
@EnableBatchProcessing(modular = true)
@Slf4j
public class AuditEventBatchProcessingApplication {
  public static final String SCAN_CLASSPATH_METADATA_COMMAND = "scan-classpath-metadata";

  public static void main(String[] args) {
    boolean onlyGenerateClassPathMetadata = generateClassPathMetadata(args);
    if (!onlyGenerateClassPathMetadata) {
      log.info("Starting Audit Event Streaming Service");
      SpringApplication.run(AuditEventBatchProcessingApplication.class, args);
    }
  }

  private static boolean generateClassPathMetadata(String[] args) {
    if (args != null && args.length > 0 && SCAN_CLASSPATH_METADATA_COMMAND.equals(args[0])) {
      String savePath = Paths.get(System.getProperty("user.dir"), CLASSPATH_METADATA_FILE_NAME).toString();
      new Reflections(HarnessPackages.IO_HARNESS, HarnessPackages.SOFTWARE_WINGS).save(savePath, new JsonSerializer());
      return true;
    }
    return false;
  }
}
