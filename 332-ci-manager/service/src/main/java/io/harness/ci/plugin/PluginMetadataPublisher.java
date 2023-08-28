/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plugin;

import static java.lang.String.format;

import io.harness.app.beans.entities.PluginMetadataConfig;
import io.harness.app.beans.entities.PluginMetadataStatus;
import io.harness.beans.PluginMetadata;
import io.harness.exception.InvalidRequestException;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.repositories.PluginMetadataRepository;
import io.harness.repositories.PluginMetadataStatusRepository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

@Slf4j
@Singleton
public class PluginMetadataPublisher {
  private static final long DAY_MILLISECS = 86400 * (long) 1000;
  private static final int HTTP_CONNECT_TIMEOUT = 1000;
  private static final String LOCK_NAME = "PLUGIN_METADATA_PUBLISHER";
  private static final String PLUGIN_SCHEMA_URL =
      "https://storage.googleapis.com/harness-plugins/schema/latest/schema.json";

  @Inject PluginMetadataRepository pluginMetadataRepository;
  @Inject PluginMetadataStatusRepository pluginMetadataStatusRepository;
  @Inject PersistentLocker persistentLocker;

  public void publish() {
    // Since there can be parallel executions, update after taking a lock
    try (AcquiredLock<?> lock = persistentLocker.tryToAcquireLock(LOCK_NAME, Duration.ofMinutes(2))) {
      if (lock == null) {
        throw new InvalidRequestException("Could not acquire lock");
      }

      int version = 1;
      PluginMetadataStatus pluginMetadataStatus = pluginMetadataStatusRepository.find();
      if (pluginMetadataStatus != null) {
        if (pluginMetadataStatus.getLastUpdated() > System.currentTimeMillis() - DAY_MILLISECS) {
          return;
        }

        version = pluginMetadataStatus.getVersion() + 1;
      }

      log.info("Publishing plugin metadata to db");
      int numPlugins = 0;
      for (PluginMetadata metadata : parse()) {
        writeToDb(metadata, version);
        numPlugins += 1;
      }

      if (pluginMetadataStatus == null) {
        pluginMetadataStatus = PluginMetadataStatus.builder().build();
      }
      pluginMetadataStatus.setVersion(version);
      pluginMetadataStatus.setLastUpdated(System.currentTimeMillis());
      pluginMetadataStatusRepository.save(pluginMetadataStatus);

      // delete all the version entries except latest version from db
      pluginMetadataRepository.deleteAllExcept(version);
      log.info("Published {} plugins with version {}", numPlugins, version);
    } catch (Exception e) {
      log.error("Plugin metadata publishing failed.", e);
    } finally {
      log.info("Plugin metadata publishing finished.");
    }
  }

  private void writeToDb(PluginMetadata pluginMetadata, int version) {
    int priority = 0;
    if (!pluginMetadata.getKind().equals("harness")) {
      priority = 1;
    }
    PluginMetadataConfig pluginMetadataConfig =
        PluginMetadataConfig.builder().metadata(pluginMetadata).version(version).priority(priority).build();
    pluginMetadataRepository.save(pluginMetadataConfig);
  }

  public List<PluginMetadata> parse() throws IOException {
    String schema = downloadSchema();
    ObjectMapper mapper = new ObjectMapper();
    return mapper.readValue(schema, new TypeReference<>() {});
  }

  public String downloadSchema() throws IOException {
    URL url = new URL(PLUGIN_SCHEMA_URL);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.setConnectTimeout(HTTP_CONNECT_TIMEOUT);
    conn.connect();
    if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
      throw new InternalError(format("Failed to download schema from: %s", PLUGIN_SCHEMA_URL));
    }

    String encoding = conn.getContentEncoding();
    encoding = encoding == null ? "UTF-8" : encoding;
    return IOUtils.toString(conn.getInputStream(), encoding);
  }
}
