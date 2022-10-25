/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANUBHAW;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.kryo.KryoSerializer;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.WorkflowExecution;
import software.wings.rules.Integration;

import com.google.inject.Inject;
import java.security.SecureRandom;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Created by rishi on 3/2/17.
 * This is meant to generate workflowExecutions for last 30 days
 */
@Integration
@Slf4j
public class ExecutionGenTest extends WingsBaseTest {
  private static final SecureRandom random = new SecureRandom();

  @Inject private HPersistence persistence;
  @Inject KryoSerializer kryoSerializer;

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void generateData() {
    PageRequest<WorkflowExecution> pageRequest = aPageRequest().withLimit("500").build();

    PageResponse<WorkflowExecution> response = persistence.query(WorkflowExecution.class, pageRequest);

    for (int i = 0; i < 50; i++) {
      WorkflowExecution workflowExecution = kryoSerializer.clone(response.get(random.nextInt(response.size())));
      if (workflowExecution.getStatus() != ExecutionStatus.SUCCESS
          && workflowExecution.getStatus() != ExecutionStatus.FAILED) {
        continue;
      }
      workflowExecution.setUuid(generateUuid());
      int day = random.nextInt(30);
      long interval = day * 24 * 3600 * 1000;
      workflowExecution.setCreatedAt(workflowExecution.getCreatedAt() - interval);

      persistence.save(workflowExecution);
    }
    log.info("response size: {}", response.size());
  }
}
