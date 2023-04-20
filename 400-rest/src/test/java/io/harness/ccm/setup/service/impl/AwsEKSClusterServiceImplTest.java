/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.setup.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.billing.CECluster;
import io.harness.ccm.setup.service.support.intfc.AwsEKSHelperService;
import io.harness.rule.Owner;

import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.NameValuePair;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.service.intfc.AwsHelperResourceService;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class AwsEKSClusterServiceImplTest extends CategoryTest {
  private AwsEKSClusterServiceImpl awsEKSClusterService;
  @Mock private AwsHelperResourceService awsHelperResourceService;
  @Mock private AwsEKSHelperService awsEKSHelperService;

  private final String ACCOUNT_ID = "accountId";
  private final String SETTING_ID = "settingId";
  private final String AWS_ACCOUNT_ID = "awsAccountId";
  private final String AWS_MASTER_ACCOUNT_ID = "awsMasterAccountId";

  @Before
  public void setUp() throws Exception {
    awsEKSClusterService = new AwsEKSClusterServiceImpl(awsHelperResourceService, awsEKSHelperService);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testListEKSClusters() {
    CEAwsConfig ceAwsConfig =
        CEAwsConfig.builder()
            .awsAccountId(AWS_ACCOUNT_ID)
            .awsMasterAccountId(AWS_MASTER_ACCOUNT_ID)
            .awsCrossAccountAttributes(
                AwsCrossAccountAttributes.builder().crossAccountRoleArn("roleArn").externalId("externalId").build())
            .build();
    doReturn(ImmutableList.of(NameValuePair.builder().name("East 1").value("us-east-1").build(),
                 NameValuePair.builder().name("East 2").value("us-east-2").build()))
        .when(awsHelperResourceService)
        .getAwsRegions();
    doReturn(ImmutableList.of("clusterName1", "clusterName2"))
        .when(awsEKSHelperService)
        .listEKSClusters("us-east-1", ceAwsConfig.getAwsCrossAccountAttributes());
    doReturn(ImmutableList.of("clusterName3"))
        .when(awsEKSHelperService)
        .listEKSClusters("us-east-2", ceAwsConfig.getAwsCrossAccountAttributes());

    List<CECluster> eksClusters = awsEKSClusterService.getEKSCluster(ACCOUNT_ID, SETTING_ID, ceAwsConfig);
    assertThat(eksClusters).hasSize(3);
    assertThat(eksClusters.get(0))
        .isEqualTo(CECluster.builder()
                       .accountId(ACCOUNT_ID)
                       .clusterName("clusterName1")
                       .region("us-east-1")
                       .infraAccountId(AWS_ACCOUNT_ID)
                       .infraMasterAccountId(AWS_MASTER_ACCOUNT_ID)
                       .parentAccountSettingId(SETTING_ID)
                       .build());
    assertThat(eksClusters.get(2))
        .isEqualTo(CECluster.builder()
                       .accountId(ACCOUNT_ID)
                       .clusterName("clusterName3")
                       .region("us-east-2")
                       .infraAccountId(AWS_ACCOUNT_ID)
                       .infraMasterAccountId(AWS_MASTER_ACCOUNT_ID)
                       .parentAccountSettingId(SETTING_ID)
                       .build());
  }
}
