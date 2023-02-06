/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraformcloud;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.LogCallback;
import io.harness.terraformcloud.TerraformCloudApiTokenCredentials;
import io.harness.terraformcloud.TerraformCloudClient;
import io.harness.terraformcloud.TerraformCloudConfig;
import io.harness.terraformcloud.model.OrganizationData;
import io.harness.terraformcloud.model.TerraformCloudResponse;
import io.harness.terraformcloud.model.WorkspaceData;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
@Singleton
public class TerraformCloudTaskHelper {
  private static final int CHUNK_SIZE = 100000;
  @Inject TerraformCloudClient terraformCloudClient;

  public Map<String, String> getOrganizationsMap(TerraformCloudConfig terraformCloudConfig) throws IOException {
    TerraformCloudApiTokenCredentials credentials =
        (TerraformCloudApiTokenCredentials) terraformCloudConfig.getTerraformCloudCredentials();

    List<OrganizationData> organizationsData = getAllOrganizations(credentials);
    Map<String, String> organizations = new HashMap<>();
    if (isNotEmpty(organizationsData)) {
      organizationsData.forEach(
          organizationData -> organizations.put(organizationData.getId(), organizationData.getAttributes().getName()));
    }
    return organizations;
  }

  public Map<String, String> getWorkspacesMap(TerraformCloudConfig terraformCloudConfig, String organization)
      throws IOException {
    TerraformCloudApiTokenCredentials credentials =
        (TerraformCloudApiTokenCredentials) terraformCloudConfig.getTerraformCloudCredentials();
    List<WorkspaceData> workspacesData = getAllWorkspaces(credentials, organization);

    Map<String, String> workspaces = new HashMap<>();
    if (isNotEmpty(workspacesData)) {
      workspacesData.forEach(
          workspaceData -> workspaces.put(workspaceData.getId(), workspaceData.getAttributes().getName()));
    }
    return workspaces;
  }

  public List<WorkspaceData> getAllWorkspaces(TerraformCloudApiTokenCredentials credentials, String organization)
      throws IOException {
    int pageNumber = 1;
    TerraformCloudResponse<List<WorkspaceData>> response;
    List<WorkspaceData> workspacesData = new ArrayList<>();
    do {
      response =
          terraformCloudClient.listWorkspaces(credentials.getUrl(), credentials.getToken(), organization, pageNumber);
      workspacesData.addAll(response.getData());
      pageNumber++;
    } while (response.getLinks().hasNonNull("next"));
    return workspacesData;
  }

  public List<OrganizationData> getAllOrganizations(TerraformCloudApiTokenCredentials credentials) throws IOException {
    int pageNumber = 1;
    List<OrganizationData> organizationsData = new ArrayList<>();
    TerraformCloudResponse<List<OrganizationData>> response;
    do {
      response = terraformCloudClient.listOrganizations(credentials.getUrl(), credentials.getToken(), pageNumber);
      organizationsData.addAll(response.getData());
      pageNumber++;
    } while (response.getLinks().hasNonNull("next"));
    return organizationsData;
  }

  public void streamLogs(LogCallback logCallback, String logReadUrl) throws IOException {
    int lastIndex = 0;
    boolean isEndOfText = false;
    String incompleteLine = "";

    while (!isEndOfText) {
      int finalLastIndex = lastIndex;
      String logs = Failsafe.with(getRetryPolicy())
                        .get(() -> terraformCloudClient.getLogs(logReadUrl, finalLastIndex, CHUNK_SIZE));
      if (isNotEmpty(logs)) {
        lastIndex = lastIndex + logs.length();
        String[] logLines = (incompleteLine + logs).split("\n");
        for (int i = 0; i < logLines.length - 1; i++) {
          logCallback.saveExecutionLog(logLines[i]);
        }
        if (isEndOfText(logs)) {
          isEndOfText = true;
          logCallback.saveExecutionLog(logLines[logLines.length - 1]);
        } else {
          incompleteLine = logLines[logLines.length - 1];
        }
      }
      if (logs != null && logs.length() < CHUNK_SIZE) {
        sleep(ofSeconds(2));
      }
    }
  }

  private RetryPolicy<Object> getRetryPolicy() {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withDelay(Duration.ofSeconds(1))
        .withMaxAttempts(5)
        .onFailedAttempt(event -> log.info("Failed to get logs: {}", event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event
            -> log.error("Failed to get logs after retrying {} times", event.getAttemptCount(), event.getFailure()));
  }

  private boolean isEndOfText(String string) {
    return string.endsWith(String.valueOf((char) 3));
  }
}
