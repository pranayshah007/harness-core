/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changestreams.eventhandlers;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventHandler.DebeziumAbstractRedisEventHandler;
import io.harness.timescaledb.Tables;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;

@Slf4j
@OwnedBy(GTM)
public class ModuleLicensesChangeEventHandler extends DebeziumAbstractRedisEventHandler {
  @Inject private DSLContext dsl;

  @SneakyThrows
  public Record createRecord(String value, String id) {
    JsonNode node = objectMapper.readTree(value);

    Record record = dsl.newRecord(Tables.MODULE_LICENSES);
    record.set(Tables.MODULE_LICENSES.ID, id);
    populateCommonFields(node, record);
    populateChaosFields(node, record);
    populateCdFields(node, record);
    populateCeFields(node, record);
    populateCfFields(node, record);
    populateCiFields(node, record);
    populateSrmFields(node, record);
    populateStoFields(node, record);

    return record;
  }

  @Override
  public boolean handleCreateEvent(String id, String value) {
    Record record = createRecord(value, id);
    if (record == null) {
      return true;
    }
    try {
      dsl.insertInto(Tables.MODULE_LICENSES)
          .set(record)
          .onConflict(Tables.MODULE_LICENSES.ID)
          .doUpdate()
          .set(record)
          .execute();
      log.debug("Successfully inserted data for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught exception while inserting data ", ex);
      return false;
    }
    return false;
  }

  @Override
  public boolean handleDeleteEvent(String id) {
    try {
      dsl.delete(Tables.MODULE_LICENSES).where(Tables.MODULE_LICENSES.ID.eq(id)).execute();
      log.debug("Successfully deleted data for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught exception while deleting data", ex);
      return false;
    }
    return true;
  }

  @Override
  public boolean handleUpdateEvent(String id, String value) {
    Record record = createRecord(value, id);
    if (record == null) {
      return true;
    }
    try {
      dsl.insertInto(Tables.MODULE_LICENSES)
          .set(record)
          .onConflict(Tables.MODULE_LICENSES.ID)
          .doUpdate()
          .set(record)
          .execute();
      log.debug("Successfully updated data for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught Exception while updating data", ex);
      return false;
    }
    return true;
  }

  private void populateCommonFields(JsonNode node, Record record) {
    if (node.get("accountIdentifier") != null) {
      record.set(Tables.MODULE_LICENSES.ACCOUNT_IDENTIFIER, node.get("accountIdentifier").asText());
    }
    if (node.get("_id") != null) {
      record.set(Tables.MODULE_LICENSES.ID, node.get("_id").asText());
    }
    if (node.get("moduleType") != null) {
      record.set(Tables.MODULE_LICENSES.MODULE_TYPE, node.get("moduleType").asText());
    }
    if (node.get("edition") != null) {
      record.set(Tables.MODULE_LICENSES.EDITION, node.get("edition").asText());
    }
    if (node.get("licenseType") != null) {
      record.set(Tables.MODULE_LICENSES.LICENSE_TYPE, node.get("licenseType").asText());
    }
    if (node.get("startTime") != null) {
      record.set(Tables.MODULE_LICENSES.START_TIME, node.get("startTime").asLong());
    }
    if (node.get("expiryTime") != null) {
      record.set(Tables.MODULE_LICENSES.EXPIRY_TIME, node.get("expiryTime").asLong());
    }
    if (node.get("premiumSupport") != null) {
      record.set(Tables.MODULE_LICENSES.PREMIUM_SUPPORT, node.get("premiumSupport").asBoolean());
    }
    if (node.get("trialExtended") != null) {
      record.set(Tables.MODULE_LICENSES.TRIAL_EXTENDED, node.get("trialExtended").asBoolean());
    }
    if (node.get("selfService") != null) {
      record.set(Tables.MODULE_LICENSES.SELF_SERVICE, node.get("selfService").asBoolean());
    }
    if (node.get("createdAt") != null) {
      record.set(Tables.MODULE_LICENSES.CREATED_AT, node.get("createdAt").asLong());
    }
    if (node.get("lastUpdatedAt") != null) {
      record.set(Tables.MODULE_LICENSES.LAST_UPDATED_AT, node.get("lastUpdatedAt").asLong());
    }
    if (node.get("status") != null) {
      record.set(Tables.MODULE_LICENSES.STATUS, node.get("status").asText());
    }
    if (node.get("createdBy") != null) {
      populateCreatedByFields(node, record);
    }
    if (node.get("lastUpdatedBy") != null) {
      populateLastUpdatedByFields(node, record);
    }
  }

  private void populateCreatedByFields(JsonNode node, Record record) {
    JsonNode createdBy = node.get("createdBy");
    if (createdBy.get("uuid") != null) {
      record.set(Tables.MODULE_LICENSES.CREATED_BY_UUID, createdBy.get("uuid").asText());
    }
    if (createdBy.get("name") != null) {
      record.set(Tables.MODULE_LICENSES.CREATED_BY_NAME, createdBy.get("name").asText());
    }
    if (createdBy.get("email") != null) {
      record.set(Tables.MODULE_LICENSES.CREATED_BY_EMAIL, createdBy.get("email").asText());
    }
    if (createdBy.get("externalUserId") != null) {
      record.set(Tables.MODULE_LICENSES.CREATED_BY_EXTERNAL_USER_ID, createdBy.get("externalUserId").asText());
    }
  }

  private void populateLastUpdatedByFields(JsonNode node, Record record) {
    JsonNode lastUpdatedBy = node.get("lastUpdatedBy");
    if (lastUpdatedBy.get("uuid") != null) {
      record.set(Tables.MODULE_LICENSES.LAST_UPDATED_BY_UUID, lastUpdatedBy.get("uuid").asText());
    }
    if (lastUpdatedBy.get("name") != null) {
      record.set(Tables.MODULE_LICENSES.LAST_UPDATED_BY_NAME, lastUpdatedBy.get("name").asText());
    }
    if (lastUpdatedBy.get("email") != null) {
      record.set(Tables.MODULE_LICENSES.LAST_UPDATED_BY_EMAIL, lastUpdatedBy.get("email").asText());
    }
    if (lastUpdatedBy.get("externalUserId") != null) {
      record.set(Tables.MODULE_LICENSES.LAST_UPDATED_BY_EXTERNAL_USER_ID, lastUpdatedBy.get("externalUserId").asText());
    }
  }

  private void populateStoFields(JsonNode node, Record record) {
    if (node.get("numberOfDevelopers") != null) {
      record.set(Tables.MODULE_LICENSES.STO_NUMBER_OF_DEVELOPERS, node.get("numberOfDevelopers").asLong());
    }
  }

  private void populateCeFields(JsonNode node, Record record) {
    if (node.get("spendLimit") != null) {
      record.set(Tables.MODULE_LICENSES.CE_SPEND_LIMIT, node.get("spendLimit").asLong());
    }
  }

  private void populateChaosFields(JsonNode node, Record record) {
    if (node.get("totalChaosExperimentRuns") != null) {
      record.set(Tables.MODULE_LICENSES.CHAOS_TOTAL_EXPERIMENT_RUNS, node.get("totalChaosExperimentRuns").asLong());
    }
    if (node.get("totalChaosInfrastructures") != null) {
      record.set(Tables.MODULE_LICENSES.CHAOS_TOTAL_INFRASTRUCTURES, node.get("totalChaosInfrastructures").asLong());
    }
  }

  private void populateCdFields(JsonNode node, Record record) {
    if (node.get("cdLicenseType") != null) {
      record.set(Tables.MODULE_LICENSES.CD_LICENSE_TYPE, node.get("cdLicenseType").asText());
    }
    if (node.get("workloads") != null) {
      record.set(Tables.MODULE_LICENSES.CD_WORKLOADS, node.get("workloads").asText());
    }
    if (node.get("serviceInstances") != null) {
      record.set(Tables.MODULE_LICENSES.CD_SERVICE_INSTANCES, node.get("serviceInstances").asText());
    }
  }

  private void populateSrmFields(JsonNode node, Record record) {
    if (node.get("numberOfServices") != null) {
      record.set(Tables.MODULE_LICENSES.SRM_NUMBER_OF_SERVICES, node.get("numberOfServices").asLong());
    }
  }

  private void populateCfFields(JsonNode node, Record record) {
    if (node.get("numberOfUsers") != null) {
      record.set(Tables.MODULE_LICENSES.CF_NUMBER_OF_USERS, node.get("numberOfUsers").asLong());
    }
    if (node.get("numberOfClientMAUs") != null) {
      record.set(Tables.MODULE_LICENSES.CF_NUMBER_OF_CLIENT_MAUS, node.get("numberOfClientMAUs").asLong());
    }
  }

  private void populateCiFields(JsonNode node, Record record) {
    if (node.get("numberOfCommitters") != null) {
      record.set(Tables.MODULE_LICENSES.CI_NUMBER_OF_COMMITTERS, node.get("numberOfCommitters").asLong());
    }
    if (node.get("cacheAllowance") != null) {
      record.set(Tables.MODULE_LICENSES.CI_CACHE_ALLOWANCE, node.get("cacheAllowance").asLong());
    }
    if (node.get("hostingCredits") != null) {
      record.set(Tables.MODULE_LICENSES.CI_HOSTING_CREDITS, node.get("hostingCredits").asLong());
    }
  }
}
