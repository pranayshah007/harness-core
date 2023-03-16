/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resources;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;

import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.VerificationIntegrationBase;
import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.rule.Owner;

import software.wings.beans.dto.CVActivityLog.LogLevel;
import software.wings.service.intfc.verification.CVActivityLogService;
import software.wings.verification.CVActivityLog;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DelegateDataCollectionResourceTest extends VerificationIntegrationBase {
  @Inject CVActivityLogService cvActivityLogService;
  @Override
  @Before
  public void setUp() {
    loginAdminUser();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: provide proper description why this test is disabled")
  public void testPostCVActivityLog() {
    WebTarget target =
        client.target(VERIFICATION_API_BASE + "/delegate-data-collection/save-cv-activity-logs?accountId=" + accountId);
    List<CVActivityLog> logs = IntStream.range(0, 10).mapToObj(i -> getActivityLog()).collect(Collectors.toList());
    Response response = getDelegateRequestBuilderWithAuthHeader(target).post(entity(logs, APPLICATION_JSON));
    assertThat(response.getStatus()).isEqualTo(200);
    logs.forEach(logObject -> {
      CVActivityLog cvActivityLog = cvActivityLogService.findByStateExecutionId(logObject.getStateExecutionId()).get(0);
      assertThat(cvActivityLog.getStateExecutionId()).isEqualTo(logObject.getStateExecutionId());
      assertThat(cvActivityLog.getLog()).isEqualTo(logObject.getLog());
      assertThat(cvActivityLog.getLogLevel()).isEqualTo(logObject.getLogLevel());
    });
  }

  private CVActivityLog getActivityLog() {
    return CVActivityLog.builder()
        .stateExecutionId(generateUuid())
        .logLevel(LogLevel.INFO)
        .log("test log: " + generateUuid())
        .build();
  }
}
