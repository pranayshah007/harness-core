/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.interrupts.Interrupt.InterruptKeys;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CV)
@Slf4j
public class VerifyStepInterruptCDCHandler extends AbstractChangeDataHandler {
  @Override
  public Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    if (changeEvent == null) {
      return null;
    }
    Map<String, String> columnValueMapping = new HashMap<>();
    DBObject dbObject = changeEvent.getFullDocument();
    String interruptId = dbObject.get("_id").toString();
    log.info("Handling change event: {} for Interrupt _id: {}", changeEvent.getUuid(), interruptId);
    columnValueMapping.put("id", interruptId);
    if (dbObject.get(InterruptKeys.planExecutionId) != null) {
      columnValueMapping.put("planExecutionId", dbObject.get(InterruptKeys.planExecutionId).toString());
    }
    if (dbObject.get(InterruptKeys.nodeExecutionId) != null) {
      columnValueMapping.put("nodeExecutionId", dbObject.get(InterruptKeys.nodeExecutionId).toString());
    }
    if (dbObject.get(InterruptKeys.type) != null) {
      columnValueMapping.put("type", dbObject.get(InterruptKeys.type).toString());
    }
    if (dbObject.get(InterruptKeys.interruptConfig) != null) {
      BasicDBObject interruptConfig = (BasicDBObject) dbObject.get(InterruptKeys.interruptConfig);
      if (interruptConfig.get("issuedBy") != null) {
        BasicDBObject issuedBy = (BasicDBObject) interruptConfig.get("issuedBy");
        if (issuedBy.get("timeoutIssuer") != null) {
          columnValueMapping.put("issuerType", "timeoutIssuer");
        } else if (issuedBy.get("adviserIssuer") != null) {
          columnValueMapping.put("issuerType", "adviserIssuer");
        } else if (issuedBy.get("triggerIssuer") != null) {
          columnValueMapping.put("issuerType", "triggerIssuer");
        } else if (issuedBy.get("manualIssuer") != null) {
          columnValueMapping.put("issuerType", "manualIssuer");
        }
      }
      columnValueMapping.put("type", dbObject.get(InterruptKeys.type).toString());
    }
    if (dbObject.get(InterruptKeys.createdAt) != null) {
      columnValueMapping.put("createdAtTimestamp", String.valueOf(dbObject.get(InterruptKeys.createdAt)));
    }
    log.info("Handled change event: {} for Interrupt _id: {}", changeEvent.getUuid(), interruptId);
    return columnValueMapping;
  }

  @Override
  public List<String> getPrimaryKeys() {
    return List.of("id");
  }
}
