/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.request;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.WorkflowExecution;

import com.mongodb.BasicDBObject;
import dev.morphia.query.Query;
import dev.morphia.query.QueryImpl;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
public class ExportExecutionsRequestQuery {
  String dbObjectJson;

  @SuppressWarnings("deprecation")
  public static ExportExecutionsRequestQuery fromQuery(Query<WorkflowExecution> query) {
    if (query == null) {
      return null;
    }

    BasicDBObject dbObject = (BasicDBObject) query.getQueryObject();
    return ExportExecutionsRequestQuery.builder().dbObjectJson(dbObject == null ? null : dbObject.toJson()).build();
  }

  public static void updateQuery(Query<WorkflowExecution> query, ExportExecutionsRequestQuery requestQuery) {
    if (requestQuery == null || requestQuery.getDbObjectJson() == null || query == null) {
      return;
    }

    if (query instanceof QueryImpl) {
      BasicDBObject dbObject = BasicDBObject.parse(requestQuery.getDbObjectJson());
      ((QueryImpl<WorkflowExecution>) query).setQueryObject(dbObject);
    }
  }
}
