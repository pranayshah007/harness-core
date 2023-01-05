/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.request;

import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.WorkflowExecution;

import com.google.inject.Inject;
import com.mongodb.DBObject;
import dev.morphia.query.Query;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ExportExecutionsRequestQueryTest extends WingsBaseTest {
  @Inject private HPersistence persistence;

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  @SuppressWarnings("deprecation")
  public void testConversion() {
    assertThat(ExportExecutionsRequestQuery.fromQuery(null)).isNull();

    Query<WorkflowExecution> query = persistence.createQuery(WorkflowExecution.class).filter("accountId", "aid");
    ExportExecutionsRequestQuery requestQuery = ExportExecutionsRequestQuery.fromQuery(query);
    assertThat(requestQuery).isNotNull();
    assertThat(requestQuery.getDbObjectJson()).isNotNull();

    Query<WorkflowExecution> newQuery = persistence.createQuery(WorkflowExecution.class);
    assertThatCode(() -> ExportExecutionsRequestQuery.updateQuery(null, requestQuery)).doesNotThrowAnyException();
    assertThatCode(() -> ExportExecutionsRequestQuery.updateQuery(query, null)).doesNotThrowAnyException();

    ExportExecutionsRequestQuery.updateQuery(newQuery, requestQuery);
    DBObject dbObject = newQuery.getQueryObject();
    assertThat(dbObject).isNotNull();
  }
}
