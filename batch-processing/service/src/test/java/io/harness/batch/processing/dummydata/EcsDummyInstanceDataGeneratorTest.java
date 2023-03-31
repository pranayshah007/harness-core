/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.dummydata;

import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.dao.intfc.InstanceDataDao;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EcsDummyInstanceDataGeneratorTest extends CategoryTest {
  @Inject @InjectMocks EcsDummyInstanceDataGenerator ecsDummyInstanceDataGenerator;
  @Mock InstanceDataDao instanceDataDao;

  private static String APP_ID = "appId";
  private static String CLOUD_PROVIDER_ID = "cloudProviderId";
  private static String ENV_ID = "envId";
  private static String INFRA_MAPPING_ID = "infraMappingId";
  private static String SERVICE_ID = "serviceId";
  private static String DEPLOYMENT_SUMMARY_ID = "deploymentSummaryId";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(instanceDataDao.create(any())).thenReturn(true);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void shouldWriteDummyEcsInstanceData() {
    int numberOfCluster = 2;
    int numberOfDays = 25;
    long ONE_DAY_MILLIS = 86400000;
    List<Integer> listOfNumberOfNodesInEachCluster = Arrays.asList(2, 2);
    List<List<Integer>> listOfNumberOfPodsInEachNode = Arrays.asList(Arrays.asList(1, 2), Arrays.asList(2, 4));
    List<HarnessServiceInfo> harnessServiceInfoList = Arrays.asList(
        new HarnessServiceInfo(SERVICE_ID, APP_ID, CLOUD_PROVIDER_ID, ENV_ID, INFRA_MAPPING_ID, DEPLOYMENT_SUMMARY_ID));
    long startTime = 15400000000L;
    long endTime = startTime + (numberOfDays * ONE_DAY_MILLIS);
    boolean isCreatedAndInsertedIntoDB = ecsDummyInstanceDataGenerator.createAndInsertDummyData(numberOfCluster,
        listOfNumberOfNodesInEachCluster, listOfNumberOfPodsInEachNode, startTime, endTime, harnessServiceInfoList);
    assertThat(isCreatedAndInsertedIntoDB).isTrue();
  }
}
