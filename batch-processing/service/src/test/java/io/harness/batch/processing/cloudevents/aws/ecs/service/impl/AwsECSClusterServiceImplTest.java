/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service.impl;

import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.cloudevents.aws.ecs.service.CEClusterDao;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AwsECSHelperService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AwsHelperResourceService;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.ccm.commons.entities.billing.CECluster;
import io.harness.rule.Owner;

import software.wings.beans.NameValuePair;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AwsECSClusterServiceImplTest extends CategoryTest {
  @InjectMocks private AwsECSClusterServiceImpl awsECSClusterService;

  @Mock private AwsHelperResourceService awsHelperResourceService;
  @Mock private AwsECSHelperService awsECSHelperService;
  @Mock private CEClusterDao ceClusterDao;

  @Captor private ArgumentCaptor<CECluster> ceCreateClusterArgumentCaptor;
  @Captor private ArgumentCaptor<CECluster> ceDeleteClusterArgumentCaptor;

  private String accountId = "ACCOUNT_ID";
  private String deleteRecordUUID = "DELETE_RECORD_UUID";
  private String accountName = "ACCOUNT_NAME";
  private String accountNameDelete = "ACCOUNT_NAME_DELETE";
  private String infraAccountId = "123123112";
  private String infraAccountIdDelete = "4423232112";
  private String infraMasterAccountId = "3243223122";
  private String masterAccountSettingId = "MASTER_SETTING_ID";
  private String accountArn = "arn:aws:organizations::123123112:account/o-tbm3caqef8/3243223122";
  private String accountArnDelete = "arn:aws:organizations::123123112:account/o-tbm3caqef8/4423232112";

  private String clusterName = "CLUSTER_NAME";
  private String clusterNameCreate = "CLUSTER_NAME_CREATE";
  private String clusterNameDelete = "CLUSTER_NAME_DELETE";
  private String region = "US_EAST_1";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testSyncCEClusters() {
    CECloudAccount ceCloudAccount = getCECloudAccount(accountNameDelete, accountArnDelete, infraAccountIdDelete);
    CECluster ceCluster = getCECluster(clusterName, region);
    CECluster ceClusterCreate = getCECluster(clusterNameCreate, region);
    CECluster ceClusterDelete = getCECluster(clusterNameDelete, region);
    ceClusterDelete.setUuid(deleteRecordUUID);
    List<CECluster> savedCEClusters = ImmutableList.of(ceCluster, ceClusterDelete);
    List<String> infraClusters = ImmutableList.of(clusterName, clusterNameCreate);

    NameValuePair regionName = NameValuePair.builder().value(region).name(region).build();
    when(awsHelperResourceService.getAwsRegions()).thenReturn(ImmutableList.of(regionName));
    when(ceClusterDao.getByInfraAccountId(accountId, infraAccountId)).thenReturn(savedCEClusters);
    when(awsECSHelperService.listECSClusters(any(), any())).thenReturn(infraClusters);

    awsECSClusterService.syncCEClusters(getCECloudAccount(accountName, accountArn, infraAccountId));

    verify(ceClusterDao).create(ceCreateClusterArgumentCaptor.capture());
    verify(ceClusterDao).deactivateCluster(ceDeleteClusterArgumentCaptor.capture());

    CECluster createCECluster = ceCreateClusterArgumentCaptor.getValue();
    assertThat(createCECluster.getClusterName()).isEqualTo(clusterNameCreate);
    assertThat(ceDeleteClusterArgumentCaptor.getValue()).isEqualTo(ceClusterDelete);
  }

  private CECluster getCECluster(String clusterName, String region) {
    return CECluster.builder()
        .accountId(accountId)
        .infraAccountId(infraAccountId)
        .infraMasterAccountId(infraMasterAccountId)
        .clusterName(clusterName)
        .region(region)
        .parentAccountSettingId(masterAccountSettingId)
        .build();
  }

  private CECloudAccount getCECloudAccount(String accountName, String accountArn, String infraAccountId) {
    return CECloudAccount.builder()
        .accountId(accountId)
        .accountName(accountName)
        .accountArn(accountArn)
        .infraAccountId(infraAccountId)
        .infraMasterAccountId(infraMasterAccountId)
        .masterAccountSettingId(masterAccountSettingId)
        .build();
  }
}
