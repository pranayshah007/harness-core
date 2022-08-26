/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service;

import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.batch.processing.BatchProcessingTestBase;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.billing.CECluster;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CEClusterDaoTest extends BatchProcessingTestBase {
  private String accountId = "ACCOUNT_ID";
  private String infraAccountId = "123123112";
  private String infraMasterAccountId = "3243223122";
  private String masterAccountSettingId = "MASTER_SETTING_ID";
  private String clusterName = "EKS_CLUSTER";
  private String region = "us-east-1";
  @Inject private CEClusterDao ceClusterDao;

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnClusterForInfraAccountId() {
    boolean savedAccount = ceClusterDao.create(getCECluster());
    assertThat(savedAccount).isTrue();
    List<CECluster> ceClusters = ceClusterDao.getByInfraAccountId(accountId, infraAccountId);
    CECluster savedCECluster = ceClusters.get(0);
    assertThat(savedCECluster.getAccountId()).isEqualTo(accountId);
    assertThat(savedCECluster.getClusterName()).isEqualTo(clusterName);
    assertThat(savedCECluster.getRegion()).isEqualTo(region);
    assertThat(savedCECluster.getInfraAccountId()).isEqualTo(infraAccountId);
    assertThat(savedCECluster.getInfraMasterAccountId()).isEqualTo(infraMasterAccountId);
    assertThat(savedCECluster.getParentAccountSettingId()).isEqualTo(masterAccountSettingId);
    ceClusterDao.deleteCluster(savedCECluster.getUuid());
    List<CECluster> ceClusterList = ceClusterDao.getByInfraAccountId(accountId, infraAccountId);
    assertThat(ceClusterList).hasSize(0);
  }

  private CECluster getCECluster() {
    return CECluster.builder()
        .accountId(accountId)
        .infraAccountId(infraAccountId)
        .infraMasterAccountId(infraMasterAccountId)
        .parentAccountSettingId(masterAccountSettingId)
        .clusterName(clusterName)
        .region(region)
        .build();
  }
}
