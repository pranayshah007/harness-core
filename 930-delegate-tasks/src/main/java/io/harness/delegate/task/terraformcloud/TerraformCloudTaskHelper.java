/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraformcloud;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.DelegateFile.Builder.aDelegateFile;
import static io.harness.delegate.task.terraformcloud.Relationship.APPLY;
import static io.harness.delegate.task.terraformcloud.Relationship.PLAN;
import static io.harness.delegate.task.terraformcloud.Relationship.STATE_VERSION;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Explanation.APPLY_UNREACHABLE_ERROR;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Explanation.COULD_NOT_APPLY;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Explanation.COULD_NOT_CREATE_RUN;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Explanation.COULD_NOT_FIND_RELATIONSHIP;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Explanation.COULD_NOT_GET_APPLY_LOGS;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Explanation.COULD_NOT_GET_APPLY_OUTPUT;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Explanation.COULD_NOT_GET_LAST_APPLIED;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Explanation.COULD_NOT_GET_ORG;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Explanation.COULD_NOT_GET_PLAN;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Explanation.COULD_NOT_GET_PLAN_JSON;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Explanation.COULD_NOT_GET_POLICY_DATA;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Explanation.COULD_NOT_GET_POLICY_OUTPUT;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Explanation.COULD_NOT_GET_RUN;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Explanation.COULD_NOT_GET_WORKSPACE;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Explanation.COULD_NOT_OVERRIDE_POLICY;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Explanation.COULD_NOT_UPLOAD_FILE;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Explanation.ERROR_PLAN;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Explanation.RELATIONSHIP_DATA_EMPTY;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Hints.PLEASE_CHECK_PLAN;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Hints.PLEASE_CHECK_POLICY;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Hints.PLEASE_CHECK_RUN;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Hints.PLEASE_CHECK_TFC_CONFIG;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Hints.PLEASE_CONTACT_HARNESS;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Message.CANT_PROCESS_TFC_RESPONSE;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Message.ERROR_APPLY;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Message.ERROR_APPLYING;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Message.ERROR_CREATING_RUN;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Message.ERROR_GETTING_APPLIED_POLICIES;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Message.ERROR_GETTING_APPLY_OUTPUT;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Message.ERROR_GETTING_JSON_PLAN;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Message.ERROR_GETTING_ORG;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Message.ERROR_GETTING_POLICY_DATA;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Message.ERROR_GETTING_POLICY_OUTPUT;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Message.ERROR_GETTING_RUN;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Message.ERROR_GETTING_WORKSPACE;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Message.ERROR_OVERRIDE_POLICY;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Message.ERROR_STREAMING_APPLY_LOGS;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Message.ERROR_STREAMING_PLAN_LOGS;
import static io.harness.delegate.task.terraformcloud.TerraformCloudExceptionConstants.Message.FAILED_TO_PLAN;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;
import static io.harness.terraformcloud.model.ApplyData.Attributes.Status.CANCELED;
import static io.harness.terraformcloud.model.ApplyData.Attributes.Status.ERRORED;
import static io.harness.terraformcloud.model.ApplyData.Attributes.Status.FINISHED;
import static io.harness.terraformcloud.model.ApplyData.Attributes.Status.UNREACHABLE;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.delegate.beans.FileBucket;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.TerraformCloudException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.terraformcloud.TerraformCloudApiTokenCredentials;
import io.harness.terraformcloud.TerraformCloudClient;
import io.harness.terraformcloud.TerraformCloudConfig;
import io.harness.terraformcloud.model.ApplyData;
import io.harness.terraformcloud.model.Data;
import io.harness.terraformcloud.model.OrganizationData;
import io.harness.terraformcloud.model.PlanData;
import io.harness.terraformcloud.model.PolicyCheckData;
import io.harness.terraformcloud.model.RunActionRequest;
import io.harness.terraformcloud.model.RunData;
import io.harness.terraformcloud.model.RunRequest;
import io.harness.terraformcloud.model.RunStatus;
import io.harness.terraformcloud.model.StateVersionOutputData;
import io.harness.terraformcloud.model.TerraformCloudResponse;
import io.harness.terraformcloud.model.WorkspaceData;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.jsonwebtoken.lang.Collections;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.io.FileUtils;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
@Singleton
public class TerraformCloudTaskHelper {
  private static final String OUTPUT_FORMAT = "\"%s\" : { \"value\" : %s, \"sensitive\" : %s }";
  private static final String HARD_FAILED = "hard_failed";
  private static final String SOFT_FAILED = "soft_failed";
  private static final int CHUNK_SIZE = 100000;
  @Inject TerraformCloudClient terraformCloudClient;
  @Inject DelegateFileManagerBase delegateFileManager;

  public Map<String, String> getOrganizationsMap(TerraformCloudConfig terraformCloudConfig) {
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

  public Map<String, String> getWorkspacesMap(TerraformCloudConfig terraformCloudConfig, String organization) {
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

  public List<WorkspaceData> getAllWorkspaces(TerraformCloudApiTokenCredentials credentials, String organization) {
    int pageNumber = 1;
    List<WorkspaceData> workspacesData = new ArrayList<>();
    try {
      TerraformCloudResponse<List<WorkspaceData>> response;
      do {
        response =
            terraformCloudClient.listWorkspaces(credentials.getUrl(), credentials.getToken(), organization, pageNumber);
        workspacesData.addAll(response.getData());
        pageNumber++;
      } while (response.getLinks().hasNonNull("next"));
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException(PLEASE_CHECK_TFC_CONFIG,
          format(COULD_NOT_GET_WORKSPACE, credentials.getUrl(), organization),
          new TerraformCloudException(ERROR_GETTING_WORKSPACE, e));
    }
    return workspacesData;
  }

  public List<OrganizationData> getAllOrganizations(TerraformCloudApiTokenCredentials credentials) {
    int pageNumber = 1;
    List<OrganizationData> organizationsData = new ArrayList<>();
    try {
      TerraformCloudResponse<List<OrganizationData>> response;
      do {
        response = terraformCloudClient.listOrganizations(credentials.getUrl(), credentials.getToken(), pageNumber);
        organizationsData.addAll(response.getData());
        pageNumber++;
      } while (response.getLinks().hasNonNull("next"));
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException(PLEASE_CHECK_TFC_CONFIG,
          format(COULD_NOT_GET_ORG, credentials.getUrl()), new TerraformCloudException(ERROR_GETTING_ORG, e));
    }
    return organizationsData;
  }

  public void streamLogs(LogCallback logCallback, String logReadUrl) {
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

  RunData createRun(String url, String token, RunRequest runRequest, boolean forceExecute, LogCallback logCallback) {
    String runId;
    RunData runData;
    try {
      logCallback.saveExecutionLog("Creating run...", INFO, CommandExecutionStatus.RUNNING);
      runData = terraformCloudClient.createRun(url, token, runRequest).getData();
      runId = runData.getId();
      logCallback.saveExecutionLog(format("Run: %s has been created", runId), INFO, CommandExecutionStatus.RUNNING);
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          PLEASE_CHECK_TFC_CONFIG, COULD_NOT_CREATE_RUN, new TerraformCloudException(ERROR_CREATING_RUN, e));
    }

    if (forceExecute && getRunStatus(url, token, runId) == RunStatus.PENDING) {
      try {
        terraformCloudClient.forceExecuteRun(url, token, runId);
      } catch (Exception e) {
        logCallback.saveExecutionLog("Failed to discard pending runs. Run might already moved from pending state", WARN,
            CommandExecutionStatus.RUNNING);
      }
    }
    String planId = getRelationshipId(runData, PLAN);
    try {
      PlanData plan = terraformCloudClient.getPlan(url, token, planId).getData();
      // stream plan logs
      streamLogs(logCallback, plan.getAttributes().getLogReadUrl());
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException(format(PLEASE_CHECK_RUN, runId),
          format(COULD_NOT_GET_PLAN, planId), new TerraformCloudException(ERROR_STREAMING_PLAN_LOGS, e));
    }

    runData = getRun(url, token, runId);
    if (runData.getAttributes().getStatus() == RunStatus.ERRORED) {
      logCallback.saveExecutionLog(FAILED_TO_PLAN, ERROR, CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(
          format(PLEASE_CHECK_RUN, runId), ERROR_PLAN, new TerraformCloudException(FAILED_TO_PLAN));
    }
    logCallback.saveExecutionLog("Plan finished", INFO, CommandExecutionStatus.SUCCESS);
    return runData;
  }

  public void streamApplyLogs(String url, String token, RunData runData, LogCallback logCallback) {
    String applyId = getRelationshipId(runData, APPLY);
    try {
      ApplyData apply = terraformCloudClient.getApply(url, token, applyId).getData();
      ApplyData.Attributes.Status status = apply.getAttributes().getStatus();
      if (status != UNREACHABLE && status != CANCELED && status != ERRORED) {
        logCallback.saveExecutionLog(
            format("Apply %s execution...", runData.getId()), INFO, CommandExecutionStatus.RUNNING);
        streamLogs(logCallback, apply.getAttributes().getLogReadUrl());
        logCallback.saveExecutionLog("Apply finished", INFO, CommandExecutionStatus.SUCCESS);
      } else if (!runData.getAttributes().isHasChanges()) {
        logCallback.saveExecutionLog("Apply will not run. No changes.", INFO, CommandExecutionStatus.SUCCESS);
      } else {
        logCallback.saveExecutionLog(format(APPLY_UNREACHABLE_ERROR, runData.getId(), status.name()), LogLevel.ERROR,
            CommandExecutionStatus.FAILURE);
        throw NestedExceptionUtils.hintWithExplanationException(PLEASE_CHECK_PLAN,
            format(APPLY_UNREACHABLE_ERROR, runData.getId(), status.name()),
            new TerraformCloudException(ERROR_APPLYING));
      }
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException(format(PLEASE_CHECK_RUN, runData.getId()),
          format(COULD_NOT_GET_APPLY_LOGS, applyId), new TerraformCloudException(ERROR_STREAMING_APPLY_LOGS, e));
    }
  }

  public String uploadJsonFile(String accountId, String delegateId, String taskId, String entityId, String fileName,
      String content, FileBucket fileBucket) {
    try {
      final DelegateFile delegateFile = aDelegateFile()
                                            .withAccountId(accountId)
                                            .withDelegateId(delegateId)
                                            .withTaskId(taskId)
                                            .withEntityId(entityId)
                                            .withBucket(fileBucket)
                                            .withFileName(fileName)
                                            .build();

      File file = Files.createTempFile("compressedTfPlan", ".gz").toFile();
      try (FileOutputStream output = new FileOutputStream(file);
           Writer writer = new OutputStreamWriter(new GZIPOutputStream(output), StandardCharsets.UTF_8)) {
        writer.write(content);
      }
      try (InputStream fileStream = new FileInputStream(file)) {
        delegateFileManager.upload(delegateFile, fileStream);
      } finally {
        FileUtils.deleteQuietly(file);
      }
      return delegateFile.getFileId();
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          PLEASE_CONTACT_HARNESS, format(COULD_NOT_UPLOAD_FILE, fileName, fileBucket.name()), e);
    }
  }

  String getRelationshipId(Data data, Relationship relationship) {
    return data.getRelationships()
        .entrySet()
        .stream()
        .filter(entry -> entry.getKey().equals(relationship.getRelationshipName()))
        .findFirst()
        .orElseThrow(()
                         -> NestedExceptionUtils.hintWithExplanationException(PLEASE_CONTACT_HARNESS,
                             format(COULD_NOT_FIND_RELATIONSHIP, relationship.getRelationshipName()),
                             new TerraformCloudException(CANT_PROCESS_TFC_RESPONSE)))
        .getValue()
        .getData()
        .stream()
        .findFirst()
        .orElseThrow(()
                         -> NestedExceptionUtils.hintWithExplanationException(PLEASE_CONTACT_HARNESS,
                             format(RELATIONSHIP_DATA_EMPTY, relationship.getRelationshipName()),
                             new TerraformCloudException(CANT_PROCESS_TFC_RESPONSE)))
        .getId();
  }

  String getApplyOutput(String url, String token, RunData runData) {
    try {
      ApplyData applyData = terraformCloudClient.getApply(url, token, getRelationshipId(runData, APPLY)).getData();
      if (applyData.getAttributes().getStatus() == FINISHED) {
        String svId = getRelationshipId(applyData, STATE_VERSION);

        int pageNumber = 1;
        TerraformCloudResponse<List<StateVersionOutputData>> response;
        List<StateVersionOutputData> stateVersionOutputData = new ArrayList<>();
        do {
          response = terraformCloudClient.getStateVersionOutputs(url, token, svId, pageNumber);
          stateVersionOutputData.addAll(response.getData());
          pageNumber++;
        } while (response.getLinks().hasNonNull("next"));

        return format("{ %s }",
            stateVersionOutputData.stream()
                .map(out
                    -> format(OUTPUT_FORMAT, out.getAttributes().getName(), out.getAttributes().getValue(),
                        out.getAttributes().isSensitive()))
                .collect(Collectors.joining(",")));
      }
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException(format(PLEASE_CHECK_RUN, runData.getId()),
          COULD_NOT_GET_APPLY_OUTPUT, new TerraformCloudException(ERROR_GETTING_APPLY_OUTPUT, e));
    }
    return null;
  }

  public String getJsonPlan(String url, String token, RunData runData) {
    try {
      return terraformCloudClient.getPlanJsonOutput(url, token, getRelationshipId(runData, PLAN));
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException(format(PLEASE_CHECK_RUN, runData.getId()),
          COULD_NOT_GET_PLAN_JSON, new TerraformCloudException(ERROR_GETTING_JSON_PLAN, e));
    }
  }

  public RunStatus getRunStatus(String url, String token, String runId) {
    return getRun(url, token, runId).getAttributes().getStatus();
  }

  public String applyRun(String url, String token, String runId, String message, LogCallback logCallback) {
    try {
      terraformCloudClient.applyRun(url, token, runId, RunActionRequest.builder().comment(message).build());
      RunData runData = getRun(url, token, runId);
      streamApplyLogs(url, token, runData, logCallback);
      return getApplyOutput(url, token, runData);
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          format(PLEASE_CHECK_RUN, runId), COULD_NOT_APPLY, new TerraformCloudException(ERROR_APPLY, e));
    }
  }

  public RunData getRun(String url, String token, String runId) {
    try {
      return terraformCloudClient.getRun(url, token, runId).getData();
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          format(PLEASE_CHECK_RUN, runId), COULD_NOT_GET_RUN, new TerraformCloudException(ERROR_GETTING_RUN, e));
    }
  }

  void discardRun(String url, String token, String runId, String message) throws IOException {
    terraformCloudClient.discardRun(url, token, runId, RunActionRequest.builder().comment(message).build());
  }

  public void streamSentinelPolicies(String url, String token, String runId, LogCallback logCallback) {
    logCallback.saveExecutionLog("Policy checks...", INFO, CommandExecutionStatus.RUNNING);
    Set<String> completedPolicies = new HashSet<>();
    List<PolicyCheckData> policyCheckData = getPolicyCheckData(url, token, runId);
    while (true) {
      for (PolicyCheckData policyCheck : policyCheckData) {
        if (policyCheck.getLinks() != null && policyCheck.getLinks().hasNonNull("output")
            && !completedPolicies.contains(policyCheck.getId())) {
          String policyCheckOutput;
          try {
            policyCheckOutput = terraformCloudClient.getPolicyCheckOutput(url, token, policyCheck.getId());
          } catch (Exception e) {
            throw NestedExceptionUtils.hintWithExplanationException(format(PLEASE_CHECK_POLICY, policyCheck.getId()),
                COULD_NOT_GET_POLICY_OUTPUT, new TerraformCloudException(ERROR_GETTING_POLICY_OUTPUT, e));
          }
          Arrays.stream(policyCheckOutput.split("\n"))
              .forEach(raw -> logCallback.saveExecutionLog(raw, INFO, CommandExecutionStatus.RUNNING));
          printPolicyChecksSummary(policyCheck, logCallback);
          completedPolicies.add(policyCheck.getId());
        }
      }
      if (policyCheckData.size() == completedPolicies.size()) {
        break;
      } else {
        sleep(ofSeconds(2));
        policyCheckData = getPolicyCheckData(url, token, runId);
      }
    }
    logCallback.saveExecutionLog("Policy check finished", INFO, CommandExecutionStatus.SUCCESS);
  }

  public List<PolicyCheckData> getPolicyCheckData(String url, String token, String runId) {
    int pageNumber = 1;
    List<PolicyCheckData> policyCheckData = new ArrayList<>();
    TerraformCloudResponse<List<PolicyCheckData>> response;
    do {
      try {
        response = terraformCloudClient.listPolicyChecks(url, token, runId, pageNumber);
      } catch (Exception e) {
        throw NestedExceptionUtils.hintWithExplanationException(format(PLEASE_CHECK_RUN, runId),
            COULD_NOT_GET_POLICY_DATA, new TerraformCloudException(ERROR_GETTING_POLICY_DATA, e));
      }
      policyCheckData.addAll(response.getData());
      pageNumber++;
    } while (response.getLinks().hasNonNull("next"));
    return policyCheckData;
  }

  public void overridePolicy(String url, String token, List<PolicyCheckData> policyCheckData, LogCallback logCallback) {
    for (PolicyCheckData policyCheck : policyCheckData) {
      if (policyCheck.getAttributes().getActions().isOverridable()) {
        String policyCheckId = policyCheck.getId();
        logCallback.saveExecutionLog(
            format("Overriding policy check: %s", policyCheckId), INFO, CommandExecutionStatus.RUNNING);
        try {
          terraformCloudClient.overridePolicyChecks(url, token, policyCheckId);
        } catch (Exception e) {
          throw NestedExceptionUtils.hintWithExplanationException(format(PLEASE_CHECK_POLICY, policyCheckId),
              COULD_NOT_OVERRIDE_POLICY, new TerraformCloudException(ERROR_OVERRIDE_POLICY, e));
        }
        logCallback.saveExecutionLog(
            format("Policy check: %s is overridden", policyCheckId), INFO, CommandExecutionStatus.RUNNING);
      }
    }
  }

  public String getLastAppliedRunId(String url, String token, String workspace) {
    try {
      List<RunData> appliedRuns = terraformCloudClient.getAppliedRuns(url, token, workspace).getData();
      return Collections.isEmpty(appliedRuns) ? null : appliedRuns.get(0).getId();
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException(PLEASE_CHECK_TFC_CONFIG,
          format(COULD_NOT_GET_LAST_APPLIED, workspace),
          new TerraformCloudException(ERROR_GETTING_APPLIED_POLICIES, e));
    }
  }

  private void printPolicyChecksSummary(PolicyCheckData policyCheckData, LogCallback logCallback) {
    PolicyCheckData.Attributes attributes = policyCheckData.getAttributes();
    if (attributes != null) {
      String status = attributes.getStatus();
      if (status != null) {
        LogColor logColor = (status.equals(HARD_FAILED) || status.equals(SOFT_FAILED)) ? LogColor.Red : LogColor.Green;
        logCallback.saveExecutionLog(
            color(format("Policy check [%s]", status), logColor, LogWeight.Bold), INFO, CommandExecutionStatus.RUNNING);
        PolicyCheckData.Attributes.Result result = attributes.getResult();
        if (result != null) {
          PolicyCheckData.Attributes.Result.Sentinel sentinel = result.getSentinel();
          if (sentinel != null) {
            Map<String, PolicyCheckData.Attributes.Result.Sentinel.PolicyData> data = sentinel.getData();
            if (isNotEmpty(data) && isNotEmpty(data.values())) {
              data.values().forEach(policyData
                  -> policyData.getPolicies().forEach(policySummary
                      -> logCallback.saveExecutionLog(
                          format("%s : %s",
                              policySummary.isResult() ? color("passed", LogColor.Green, LogWeight.Bold)
                                                       : color("failed", LogColor.Red, LogWeight.Bold),
                              policySummary.getPolicy() != null ? policySummary.getPolicy().getName() : "unknown name"),
                          INFO, CommandExecutionStatus.RUNNING)));
            }
          }
        }
      }
    }
  }
}
