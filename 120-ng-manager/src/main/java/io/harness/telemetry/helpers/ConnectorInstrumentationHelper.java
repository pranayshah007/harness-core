/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.data.structure.EmptyPredicate;
import io.harness.telemetry.Category;
import io.harness.telemetry.Destination;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
public class ConnectorInstrumentationHelper extends InstrumentationHelper {
  @Inject TelemetryReporter telemetryReporter;
  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
  String ACCOUNT_ID = "account_id";
  String CONNECTOR_ID = "connector_id";
  String CONNECTOR_PROJECT = "connector_project";
  String CONNECTOR_ORG = "connector_org";
  String CONNECTOR_NAME = "connector_name";
  String CONNECTOR_TYPE = "connector_type";
  String CONNECTIVITY_STATUS = "connectivity_status";
  String ERROR_SUMMARY = "error_summary";
  String ERROR_DETAILS = "error_details";
  String DELEGATE_ID = "delegate_id";
  String TEST_CONNECTION_SUCCESS = "test_connection_success";
  String TEST_CONNECTION_FAILURE = "test_connection_failure";

  public CompletableFuture sendConnectorCreateEvent(ConnectorInfoDTO connector, String accountId) {
    try {
      if (EmptyPredicate.isNotEmpty(accountId) || !accountId.equals(GLOBAL_ACCOUNT_ID)) {
        HashMap<String, Object> eventPropertiesMap = new HashMap<>();
        String projectIdentifier = connector.getProjectIdentifier();
        String orgIdentifier = connector.getOrgIdentifier();
        if (projectIdentifier != null) {
          eventPropertiesMap.put(CONNECTOR_PROJECT, projectIdentifier);
        }
        if (orgIdentifier != null) {
          eventPropertiesMap.put(CONNECTOR_ORG, orgIdentifier);
        }
        eventPropertiesMap.put(ACCOUNT_ID, accountId);
        eventPropertiesMap.put(CONNECTOR_ID, connector.getIdentifier());
        eventPropertiesMap.put(CONNECTOR_TYPE, connector.getConnectorType());
        eventPropertiesMap.put(CONNECTOR_NAME, connector.getName());
        String userId = getUserId();
        return CompletableFuture.runAsync(
            ()
                -> telemetryReporter.sendTrackEvent("Connector Created", userId, accountId, eventPropertiesMap,
                    ImmutableMap.<Destination, Boolean>builder()
                        .put(Destination.AMPLITUDE, true)
                        .put(Destination.ALL, false)
                        .build(),
                    Category.PLATFORM, TelemetryOption.builder().sendForCommunity(true).build()));
      } else {
        log.info("There is no account found for account ID = " + accountId
            + "!. Cannot send Connector Creation Finished event.");
      }
    } catch (Exception e) {
      log.error("Connector creation event failed for accountID= " + accountId, e);
    }
    return null;
  }

  public CompletableFuture sendConnectorDeleteEvent(
      String orgIdentifier, String projectIdentifier, String connectorIdentifier, String accountId) {
    try {
      if (EmptyPredicate.isNotEmpty(accountId) || !accountId.equals(GLOBAL_ACCOUNT_ID)) {
        HashMap<String, Object> eventPropertiesMap = new HashMap<>();
        if (projectIdentifier != null) {
          eventPropertiesMap.put(CONNECTOR_PROJECT, projectIdentifier);
        }
        if (orgIdentifier != null) {
          eventPropertiesMap.put(CONNECTOR_ORG, orgIdentifier);
        }
        eventPropertiesMap.put(ACCOUNT_ID, accountId);
        eventPropertiesMap.put(CONNECTOR_ID, connectorIdentifier);
        String userId = getUserId();
        return CompletableFuture.runAsync(
            ()
                -> telemetryReporter.sendTrackEvent("connector_deletion", userId, accountId, eventPropertiesMap,
                    ImmutableMap.<Destination, Boolean>builder()
                        .put(Destination.AMPLITUDE, true)
                        .put(Destination.ALL, false)
                        .build(),
                    Category.PLATFORM, TelemetryOption.builder().sendForCommunity(true).build()));
      } else {
        log.info(
            "There is no account found for account ID = " + accountId + "!. Cannot send Connector Deletion event.");
      }
    } catch (Exception e) {
      log.error("Connector deletion event failed for accountID= " + accountId, e);
    }
    return null;
  }

  public CompletableFuture sendTestConnectionEvent(
      ConnectorValidationResult connectorValidationResult, ConnectorInfoDTO connector, String accountId) {
    try {
      String eventMessage;
      if (EmptyPredicate.isNotEmpty(accountId) || !accountId.equals(GLOBAL_ACCOUNT_ID)) {
        HashMap<String, Object> eventPropertiesMap = new HashMap<>();
        String projectIdentifier = connector.getProjectIdentifier();
        String orgIdentifier = connector.getOrgIdentifier();
        if (projectIdentifier != null) {
          eventPropertiesMap.put(CONNECTOR_PROJECT, projectIdentifier);
        }
        if (orgIdentifier != null) {
          eventPropertiesMap.put(CONNECTOR_ORG, orgIdentifier);
        }
        String delegateId = connectorValidationResult.getDelegateId();
        if (delegateId != null) {
          eventPropertiesMap.put(DELEGATE_ID, delegateId);
        }
        eventPropertiesMap.put(ACCOUNT_ID, accountId);
        eventPropertiesMap.put(CONNECTOR_ID, connector.getIdentifier());
        eventPropertiesMap.put(CONNECTOR_TYPE, connector.getConnectorType());
        eventPropertiesMap.put(CONNECTOR_NAME, connector.getName());
        eventPropertiesMap.put(CONNECTIVITY_STATUS, connectorValidationResult.getStatus());
        if (connectorValidationResult.getStatus() == ConnectivityStatus.SUCCESS) {
          eventMessage = TEST_CONNECTION_SUCCESS;
        } else {
          eventMessage = TEST_CONNECTION_FAILURE;
          eventPropertiesMap.put(ERROR_SUMMARY, connectorValidationResult.getErrorSummary());
          eventPropertiesMap.put(ERROR_DETAILS, connectorValidationResult.getErrors());
        }
        String userId = getUserId();
        return CompletableFuture.runAsync(
            ()
                -> telemetryReporter.sendTrackEvent(eventMessage, userId, accountId, eventPropertiesMap,
                    ImmutableMap.<Destination, Boolean>builder()
                        .put(Destination.AMPLITUDE, true)
                        .put(Destination.ALL, false)
                        .build(),
                    Category.PLATFORM, TelemetryOption.builder().sendForCommunity(true).build()));
      } else {
        log.info("There is no account found for account ID = " + accountId + "!. Cannot send Test Connection event.");
      }
    } catch (Exception e) {
      log.error("Test connection event failed for accountID= " + accountId, e);
    }
    return null;
  }
}
