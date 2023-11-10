/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.version;

import static io.harness.annotations.dev.HarnessTeam.PL;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/version")
@Produces(MediaType.APPLICATION_JSON)
public class VersionInfoManagerV2 {
  private static final String VERSION_YAML_FILE = "/opt/harness/version.yaml";

  @GET
  public String getVersionInfo() {
    try {
      // Read the content of the version.yaml file
      FileInputStream fileInputStream = new FileInputStream(VERSION_YAML_FILE);
      String yamlContent = IOUtils.toString(fileInputStream, StandardCharsets.UTF_8);

      // Parse the YAML content and create a JSON response
      JSONObject json = new JSONObject();
      for (String line : yamlContent.split("\n")) {
        String[] parts = line.split(":");
        if (parts.length == 2) {
          String key = parts[0].trim();
          String value = parts[1].trim();
          json.put(key, value);
        }
      }

      return json.toString();
    } catch (IOException e) {
      e.printStackTrace();
      // Handle the error and return an appropriate JSON response.
      JSONObject errorJson = new JSONObject();
      errorJson.put("error", "Failed to read version.yaml");
      return errorJson.toString();
    }
  }
}