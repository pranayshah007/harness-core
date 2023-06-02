/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.RAGHU;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import software.wings.WingsBaseTest;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.beans.dto.ThirdPartyApiCallLog.FieldType;
import software.wings.beans.dto.ThirdPartyApiCallLog.ThirdPartyApiCallField;
import software.wings.resources.ActivityResource;
import software.wings.resources.DelegateAgentResource;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Created by rsingh on 9/13/18.
 */
public class ThirdPartyApiCallServiceTest extends WingsBaseTest {
  private String accountId;
  private String stateExecutionId;
  private String delegateId;
  private String appId;
  @Inject private DelegateAgentResource delegateResource;
  @Inject private ActivityResource activityResource;
  @Inject private KryoSerializer kryoSerializer;

  @Before
  public void setup() throws IOException, NoSuchFieldException, IllegalAccessException {
    accountId = generateUuid();
    appId = generateUuid();
    stateExecutionId = generateUuid();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSaveAndGetApiCallLogs() {
    int numOfApiCallLogs = 10;
    List<ThirdPartyApiCallLog> apiCallLogs = new ArrayList<>();
    for (int i = 0; i < numOfApiCallLogs; i++) {
      apiCallLogs.add(ThirdPartyApiCallLog.builder()
                          .stateExecutionId(stateExecutionId)
                          .accountId(accountId)
                          .requestTimeStamp(System.currentTimeMillis())
                          .responseTimeStamp(System.currentTimeMillis())
                          .request(Lists.newArrayList(ThirdPartyApiCallField.builder()
                                                          .type(FieldType.TEXT)
                                                          .value(generateUuid())
                                                          .name(generateUuid())
                                                          .build(),
                              ThirdPartyApiCallField.builder()
                                  .type(FieldType.TEXT)
                                  .value(generateUuid())
                                  .name(generateUuid())
                                  .build()))
                          .response(Lists.newArrayList(ThirdPartyApiCallField.builder()
                                                           .type(FieldType.TEXT)
                                                           .value(generateUuid())
                                                           .name(generateUuid())
                                                           .build(),
                              ThirdPartyApiCallField.builder()
                                  .type(FieldType.TEXT)
                                  .value(generateUuid())
                                  .name(generateUuid())
                                  .build()))
                          .createdAt(i + 1)
                          .build());
    }
    delegateResource.saveApiCallLogs(delegateId, accountId, kryoSerializer.asBytes(apiCallLogs));

    RestResponse<List<software.wings.service.impl.ThirdPartyApiCallLog>> restResponse =
        activityResource.listLogs(appId, stateExecutionId, 0, 0, aPageRequest().build());
    List<software.wings.service.impl.ThirdPartyApiCallLog> savedApiCallLogs = restResponse.getResource();
    assertThat(savedApiCallLogs).hasSize(numOfApiCallLogs);
    for (int i = 0; i < numOfApiCallLogs; i++) {
      assertThat(savedApiCallLogs.get(i).getCreatedAt()).isEqualTo(numOfApiCallLogs - i);
      assertThat(savedApiCallLogs.get(i).getAccountId()).isEqualTo(accountId);
      assertThat(savedApiCallLogs.get(i).getStateExecutionId()).isEqualTo(stateExecutionId);
      assertThat(savedApiCallLogs.get(i).getRequestTimeStamp())
          .isEqualTo(apiCallLogs.get(numOfApiCallLogs - i - 1).getRequestTimeStamp());
      assertThat(savedApiCallLogs.get(i).getResponseTimeStamp())
          .isEqualTo(apiCallLogs.get(numOfApiCallLogs - i - 1).getResponseTimeStamp());
      assertThat(savedApiCallLogs.get(i).getRequest())
          .isEqualTo(apiCallLogs.get(numOfApiCallLogs - i - 1).getRequest());
      assertThat(savedApiCallLogs.get(i).getResponse())
          .isEqualTo(apiCallLogs.get(numOfApiCallLogs - i - 1).getResponse());
    }
  }
}
