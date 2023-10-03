/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapointsdata.service;

import static io.harness.idp.common.Constants.KUBERNETES_IDENTIFIER;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.factory.DataSourceDslFactory;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.factory.KubernetesDslFactory;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl.KubernetesDsl;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.ClusterConfig;
import io.harness.spec.server.idp.v1.model.DataPointInputValues;
import io.harness.spec.server.idp.v1.model.DataSourceLocationInfo;
import io.harness.spec.server.idp.v1.model.KubernetesConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class KubernetesDataPointsServiceImplTest extends CategoryTest {
  private static final String TEST_DATAPOINT_IDENTIFIER = "dp1";
  private static final String TEST_DSL_IDENTIFIER = "dsl1";
  private static final String TEST_CLUSTER = "cluster1";
  private static final String TEST_LABEL_SELECTOR = "app=myapp";
  private static final String TEST_URL = "http://192.168.0.1";
  private static final String TEST_ACCOUNT_IDENTIFIER = "testAccount";
  AutoCloseable openMocks;
  @InjectMocks KubernetesDataPointsServiceImpl kubernetesDataPointsService;
  @Mock DataSourceDslFactory dataSourceDataProviderFactory;
  @Mock DataPointService dataPointService;
  @Mock KubernetesDslFactory factory;
  @Mock KubernetesDsl kubernetesDsl;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetDataPointDataValues() {
    List<ClusterConfig> clusters = new ArrayList<>();
    ClusterConfig clusterConfig = new ClusterConfig();
    clusterConfig.setName(TEST_CLUSTER);
    clusterConfig.setUrl(TEST_URL);
    clusters.add(clusterConfig);

    DataSourceLocationInfo dslInfo = new DataSourceLocationInfo();
    DataPointInputValues dpInputValues = new DataPointInputValues();
    dpInputValues.setDataPointIdentifier(TEST_DATAPOINT_IDENTIFIER);
    dslInfo.setDataPoints(Collections.singletonList(dpInputValues));

    KubernetesConfig kubernetesConfig = new KubernetesConfig();
    kubernetesConfig.setLabelSelector(TEST_LABEL_SELECTOR);
    kubernetesConfig.setClusters(clusters);
    kubernetesConfig.setDataSourceLocation(dslInfo);

    Map<String, List<DataPointEntity>> dataToFetch = new HashMap<>();
    DataPointEntity datapoint = DataPointEntity.builder()
                                    .dataSourceIdentifier(KUBERNETES_IDENTIFIER)
                                    .identifier(TEST_DATAPOINT_IDENTIFIER)
                                    .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                    .build();
    dataToFetch.put(TEST_DSL_IDENTIFIER, Collections.singletonList(datapoint));

    when(dataSourceDataProviderFactory.getDataSourceDataProvider(KUBERNETES_IDENTIFIER)).thenReturn(factory);
    when(dataPointService.getDslDataPointsInfo(
             TEST_ACCOUNT_IDENTIFIER, Collections.singletonList(TEST_DATAPOINT_IDENTIFIER), KUBERNETES_IDENTIFIER))
        .thenReturn(dataToFetch);
    when(factory.getDslDataProvider(TEST_DSL_IDENTIFIER)).thenReturn(kubernetesDsl);

    kubernetesDataPointsService.getDataPointDataValues(TEST_ACCOUNT_IDENTIFIER, kubernetesConfig);

    verify(kubernetesDsl).getDslData(TEST_ACCOUNT_IDENTIFIER, kubernetesConfig);
  }
}
