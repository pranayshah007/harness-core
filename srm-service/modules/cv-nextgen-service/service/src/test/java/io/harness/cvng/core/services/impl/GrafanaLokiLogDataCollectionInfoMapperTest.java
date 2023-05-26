/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.GrafanaLokiLogDataCollectionInfo;
import io.harness.cvng.core.entities.NextGenLogCVConfig;
import io.harness.cvng.core.entities.QueryParams;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GrafanaLokiLogDataCollectionInfoMapperTest extends CvNextGenTestBase {
  private static final String MOCKED_QUERY = "{job=~\".+\"}";
  private static final String MOCKED_QUERY_NAME = "mockedQueryName";
  private static final String MOCKED_INSTANCE_IDENTIFIER = "host";
  @Inject private GrafanaLokiLogDataCollectionInfoMapper grafanaLokiLogDataCollectionInfoMapper;

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testToGrafanaLokiLogDataCollectionInfo() {
    NextGenLogCVConfig nextGenLogCVConfig =
        NextGenLogCVConfig.builder()
            .query(MOCKED_QUERY)
            .queryName(MOCKED_QUERY_NAME)
            .queryParams(QueryParams.builder().serviceInstanceField(MOCKED_INSTANCE_IDENTIFIER).build())
            .build();

    GrafanaLokiLogDataCollectionInfo grafanaLokiLogDataCollectionInfo =
        grafanaLokiLogDataCollectionInfoMapper.toDataCollectionInfo(
            nextGenLogCVConfig, VerificationTask.TaskType.DEPLOYMENT);
    assertThat(grafanaLokiLogDataCollectionInfo).isNotNull();
    assertThat(grafanaLokiLogDataCollectionInfo.getUrlEncodedQuery())
        .isEqualTo(GrafanaLokiLogNextGenHealthSourceHelper.encodeValue(MOCKED_QUERY));
    assertThat(grafanaLokiLogDataCollectionInfo.getServiceInstanceIdentifier()).isEqualTo(MOCKED_INSTANCE_IDENTIFIER);
    assertThat(grafanaLokiLogDataCollectionInfo.getDataCollectionDsl())
        .isEqualTo(DataCollectionDSLFactory.readLogDSL(DataSourceType.GRAFANA_LOKI_LOGS));
  }
}