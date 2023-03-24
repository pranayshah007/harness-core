/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.ecs;

import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class EcsPerpetualTaskServiceClientTest extends WingsBaseTest {
  private String accountId = "ACCOUNT_ID";
  private static final String REGION = "region";
  private static final String SETTING_ID = "settingId";
  private static final String CLUSTER_NAME = "clusterName";
  private static final String CLUSTER_ID = "clusterId";
  private Map<String, String> clientParamsMap = new HashMap<>();

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private SettingsService settingsService;
  @Mock private SecretManager secretManager;
  @InjectMocks @Inject EcsPerpetualTaskServiceClient ecsPerpetualTaskServiceClient;

  @Before
  public void setUp() {
    when(settingsService.get(SETTING_ID))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute()
                        .withValue(AwsConfig.builder()
                                       .accessKey("accessKey".toCharArray())
                                       .secretKey("secretKey".toCharArray())
                                       .build())
                        .build());

    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(null);

    clientParamsMap.put(REGION, REGION);
    clientParamsMap.put(SETTING_ID, SETTING_ID);
    clientParamsMap.put(CLUSTER_NAME, CLUSTER_NAME);
    clientParamsMap.put(CLUSTER_ID, CLUSTER_ID);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testGet() {
    PerpetualTaskClientContext perpetualTaskClientContext =
        PerpetualTaskClientContext.builder().clientParams(clientParamsMap).build();
    EcsPerpetualTaskParams ecsPerpetualTaskParams =
        ecsPerpetualTaskServiceClient.getTaskParams(perpetualTaskClientContext, true);
    assertThat(ecsPerpetualTaskParams.getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(ecsPerpetualTaskParams.getClusterName()).isEqualTo(CLUSTER_NAME);
    assertThat(ecsPerpetualTaskParams.getRegion()).isEqualTo(REGION);
    assertThat(ecsPerpetualTaskParams.getSettingId()).isEqualTo(SETTING_ID);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testGetValidationTask() {
    PerpetualTaskClientContext perpetualTaskClientContext =
        PerpetualTaskClientContext.builder().clientParams(clientParamsMap).build();
    DelegateTask delegateTask = ecsPerpetualTaskServiceClient.getValidationTask(perpetualTaskClientContext, accountId);
    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getAccountId()).isEqualTo(accountId);
  }
}
