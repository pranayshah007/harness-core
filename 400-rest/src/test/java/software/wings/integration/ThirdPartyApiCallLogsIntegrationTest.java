/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.RAGHU;

import static software.wings.beans.Application.Builder.anApplication;

import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.PageResponse;
import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.data.structure.CollectionUtils;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.beans.dto.ThirdPartyApiCallLog.FieldType;
import software.wings.beans.dto.ThirdPartyApiCallLog.ThirdPartyApiCallField;
import software.wings.service.impl.ThirdPartyApiCallLog;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Created by rsingh on 6/14/18.
 */
public class ThirdPartyApiCallLogsIntegrationTest extends IntegrationTestBase {
  private String stateExecutionId;
  private String appId;
  private String delegateId;
  private String delegateTaskId;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    loginAdminUser();
    stateExecutionId = generateUuid();
    appId = wingsPersistence.save(anApplication().name(generateUuid()).accountId(accountId).build());
    delegateId = generateUuid();
    delegateTaskId = generateUuid();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testSaveApiCallLogs() throws Exception {
    int numOfApiCallLogs = 12;
    List<ThirdPartyApiCallLog> apiCallLogs = new ArrayList<>();
    for (int i = 0; i < numOfApiCallLogs; i++) {
      apiCallLogs.add(ThirdPartyApiCallLog.builder()
                          .accountId(accountId)
                          .stateExecutionId(stateExecutionId)
                          .requestTimeStamp(i)
                          .request(Lists.newArrayList(ThirdPartyApiCallField.builder()
                                                          .name(generateUuid())
                                                          .value(generateUuid())
                                                          .type(FieldType.TEXT)
                                                          .build()))
                          .response(Lists.newArrayList(ThirdPartyApiCallField.builder()
                                                           .name(generateUuid())
                                                           .value(generateUuid())
                                                           .type(FieldType.TEXT)
                                                           .build()))
                          .delegateId(delegateId)
                          .delegateTaskId(delegateTaskId)
                          .build());
    }
    WebTarget target = client.target(API_BASE + "/delegates/" + delegateTaskId + "/state-executions"
        + "?accountId=" + accountId);
    RestResponse<Boolean> restResponse = getDelegateRequestBuilderWithAuthHeader(target).post(
        entity(apiCallLogs, APPLICATION_JSON), new GenericType<RestResponse<Boolean>>() {});
    Thread.sleep(2000);

    target = client.target(
        API_BASE + "/activities/" + stateExecutionId + "/api-call-logs?accountId=" + accountId + "&appId=" + appId);
    RestResponse<PageResponse<ThirdPartyApiCallLog>> logResponse = getRequestBuilderWithAuthHeader(target).get(
        new GenericType<RestResponse<PageResponse<ThirdPartyApiCallLog>>>() {});

    List<ThirdPartyApiCallLog> savedApiCallLogs = logResponse.getResource().getResponse();
    assertThat(savedApiCallLogs).hasSize(numOfApiCallLogs);
    savedApiCallLogs.forEach(savedApiCallLog -> {
      assertThat(savedApiCallLog.getUuid()).isNotNull();
      savedApiCallLog.setUuid(null);
      assertThat(savedApiCallLog.getCreatedAt() > 0).isTrue();
      savedApiCallLog.setCreatedAt(0);
    });

    assertThat(CollectionUtils.isEqualCollection(apiCallLogs, savedApiCallLogs)).isTrue();
  }
}
