/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.version;

import static io.harness.annotations.dev.HarnessTeam.PL;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import io.dropwizard.Application;
import io.dropwizard.setup.Environment;
import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class VersionInfoManagerV2 extends Application<Configuration> {
  public static void main(String[] args) throws Exception {
    new VersionInfoManagerV2().run(args);
  }

  @Override
  public void run(Configuration configuration, Environment environment) {
    // Read the YAML file from the Docker container
    String filePath = "/opt/harness/version.yaml";
    Map<String, String> versionInfo = readYamlFile(filePath);

    // Register a resource that returns the JSON response
    environment.jersey().register(new VersionInfoResource(versionInfo));
  }

  // Helper method to read the YAML file and parse its content
  private Map<String, String> readYamlFile(String filePath) {
    Map<String, String> versionInfo = new HashMap<>();
    Yaml yaml = new Yaml();

    try (FileReader fileReader = new FileReader(filePath)) {
      versionInfo = yaml.load(fileReader);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return versionInfo;
  }

  // Configure Jackson to format the JSON response nicely
  @Override
  public void configure(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    objectMapper.setDateFormat(new StdDateFormat());
  }
}
