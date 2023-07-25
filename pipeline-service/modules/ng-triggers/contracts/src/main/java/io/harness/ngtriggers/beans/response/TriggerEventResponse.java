/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.response;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NEW_ARTIFACT_EVENT_PROCESSED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NEW_MANIFEST_EVENT_PROCESSED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TARGET_EXECUTION_REQUESTED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TRIGGER_CONFIRMATION_SUCCESSFUL;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ngtriggers.beans.source.NGTriggerType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Data
@Builder
@OwnedBy(PIPELINE)
public class TriggerEventResponse {
  public enum FinalStatus {
    SCM_SERVICE_CONNECTION_FAILED("Scm service connection failed"),
    INVALID_PAYLOAD("Invalid payload"),
    TRIGGER_DID_NOT_MATCH_EVENT_CONDITION("Trigger did not match event condition"),
    TRIGGER_DID_NOT_MATCH_METADATA_CONDITION("Trigger did not match metadata condition"),
    TRIGGER_DID_NOT_MATCH_ARTIFACT_JEXL_CONDITION("Trigger did not match artifact jexl condition"),
    NO_MATCHING_TRIGGER_FOR_REPO("No matching trigger for repo"),
    NO_MATCHING_TRIGGER_FOR_EVENT_ACTION("No matching trigger for event action"),
    NO_MATCHING_TRIGGER_FOR_METADATA_CONDITIONS("No matching trigger for metadata conditions"),
    NO_MATCHING_TRIGGER_FOR_PAYLOAD_CONDITIONS("No matching trigger for payload conditions"),
    NO_MATCHING_TRIGGER_FOR_JEXL_CONDITIONS("No matching trigger for jexl conditions"),
    NO_MATCHING_TRIGGER_FOR_HEADER_CONDITIONS("No matching trigger for header conditions"),
    INVALID_RUNTIME_INPUT_YAML("Invalid runtime input yaml"),
    TARGET_DID_NOT_EXECUTE("Target did not execute"),
    TARGET_EXECUTION_REQUESTED("Target execution requested"),
    NO_ENABLED_CUSTOM_TRIGGER_FOUND("No enabled custom trigger found"),
    NO_ENABLED_CUSTOM_TRIGGER_FOUND_FOR_ACCOUNT("No enabled custom trigger found for account"),
    NO_ENABLED_TRIGGER_FOR_PROJECT("No enabled trigger for project"),
    NO_ENABLED_TRIGGER_FOR_ACCOUNT("No enabled trigger for account"),
    NO_ENABLED_TRIGGER_FOR_SOURCEREPO_TYPE("No enabled trigger for source repo type"),
    NO_ENABLED_TRIGGER_FOR_ACCOUNT_SOURCE_REPO("No enabled trigger for account source repo"),
    NO_MATCHING_TRIGGER_FOR_FILEPATH_CONDITIONS("No matching trigger for filepath conditions"),
    FAILED_TO_FETCH_PR_DETAILS("Failed to fetch pr details"),
    EXCEPTION_WHILE_PROCESSING("Exception while processing"),
    TRIGGER_CONFIRMATION_SUCCESSFUL("Trigger confirmation successful"),
    TRIGGER_CONFIRMATION_FAILED("Trigger confirmation failed"),
    TRIGGER_AUTHENTICATION_FAILED("Trigger authentication failed"),

    VALIDATION_FAILED_FOR_TRIGGER("Validation failed for trigger"),
    ALL_MAPPED_TRIGGER_FAILED_VALIDATION_FOR_POLLING_EVENT("All mapped trigger failed validation for polling event"),
    NO_MATCHING_TRIGGER_FOR_FOR_EVENT_SIGNATURES("No matching trigger for event signatures"),
    NO_MATCHING_TRIGGER_FOR_FOR_EVENT_CONDITION("No matching trigger for event condition"),
    POLLING_EVENT_WITH_NO_VERSIONS("Polling event with no versions"),
    // Build Trigger events
    NEW_ARTIFACT_EVENT_PROCESSED("New artifact event processed"),
    NEW_MANIFEST_EVENT_PROCESSED("New manifest event processed"),
    ;
    String message;
    FinalStatus(String message) {
      this.message = message;
    }

    public String getMessage() {
      return message;
    }
  }

  private String accountId;
  private String orgIdentifier;
  private String projectIdentifier;
  private String targetIdentifier;
  private String eventCorrelationId;
  private String pollingDocId;
  private String payload;
  private long createdAt;
  private FinalStatus finalStatus;
  private String message;
  private String planExecutionId;
  private boolean exceptionOccurred;
  private String triggerIdentifier;
  private TargetExecutionSummary targetExecutionSummary;
  private boolean enabled;
  NGTriggerType ngTriggerType;

  public static boolean isSuccessResponse(FinalStatus status) {
    Set<FinalStatus> finalStatuses = new HashSet<>(Arrays.asList(NEW_ARTIFACT_EVENT_PROCESSED,
        NEW_MANIFEST_EVENT_PROCESSED, TRIGGER_CONFIRMATION_SUCCESSFUL, TARGET_EXECUTION_REQUESTED));
    return finalStatuses.contains(status);
  }
}
