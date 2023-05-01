/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.response;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.source.NGTriggerType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(PIPELINE)
public class TriggerEventResponse {
  public enum FinalStatus {
    SCM_SERVICE_CONNECTION_FAILED,
    INVALID_PAYLOAD,
    NO_MATCHING_TRIGGER_FOR_REPO,
    NO_MATCHING_TRIGGER_FOR_EVENT_ACTION,
    NO_MATCHING_TRIGGER_FOR_METADATA_CONDITIONS,
    NO_MATCHING_TRIGGER_FOR_PAYLOAD_CONDITIONS,
    NO_MATCHING_TRIGGER_FOR_JEXL_CONDITIONS,
    NO_MATCHING_TRIGGER_FOR_HEADER_CONDITIONS,
    INVALID_RUNTIME_INPUT_YAML,
    TARGET_DID_NOT_EXECUTE,
    TARGET_EXECUTION_REQUESTED,
    NO_ENABLED_CUSTOM_TRIGGER_FOUND,
    NO_ENABLED_CUSTOM_TRIGGER_FOUND_FOR_ACCOUNT,
    NO_ENABLED_TRIGGER_FOR_PROJECT,
    NO_ENABLED_TRIGGER_FOR_ACCOUNT,
    NO_ENABLED_TRIGGER_FOR_SOURCEREPO_TYPE,
    NO_ENABLED_TRIGGER_FOR_ACCOUNT_SOURCE_REPO,
    NO_MATCHING_TRIGGER_FOR_FILEPATH_CONDITIONS,
    FAILED_TO_FETCH_PR_DETAILS,
    EXCEPTION_WHILE_PROCESSING,
    TRIGGER_CONFIRMATION_SUCCESSFUL,
    TRIGGER_CONFIRMATION_FAILED,
    TRIGGER_AUTHENTICATION_FAILED,

    VALIDATION_FAILED_FOR_TRIGGER,
    ALL_MAPPED_TRIGGER_FAILED_VALIDATION_FOR_POLLING_EVENT,
    NO_MATCHING_TRIGGER_FOR_FOR_EVENT_SIGNATURES,
    NO_MATCHING_TRIGGER_FOR_FOR_EVENT_CONDITION,
    POLLING_EVENT_WITH_NO_VERSIONS,
    // Build Trigger events
    NEW_ARTIFACT_EVENT_PROCESSED,
    NEW_MANIFEST_EVENT_PROCESSED,
  }

  private String accountId;
  private String orgIdentifier;
  private String projectIdentifier;
  private String targetIdentifier;
  private String eventCorrelationId;
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
}
