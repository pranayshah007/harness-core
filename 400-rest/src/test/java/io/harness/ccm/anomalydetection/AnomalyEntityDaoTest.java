/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.anomalydetection;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.SANDESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.anomaly.AnomalyDataStub;
import io.harness.ccm.anomaly.dao.AnomalyEntityDao;
import io.harness.ccm.anomaly.entities.AnomalyEntity;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class AnomalyEntityDaoTest extends CategoryTest {
  @Mock TimeScaleDBService dbService;
  @Mock Connection connection;
  @Mock Statement statement;
  @InjectMocks AnomalyEntityDao anomalyEntityDao;

  String updateQuery =
      "UPDATE anomalies SET note = 'K8S_Anomaly',slackInstantNotification = 'false',slackDailyNotification = 'false',slackWeeklyNotification = 'false' WHERE ((id = 'ANOMALY_ID1') AND (accountid = 'ACCOUNT_ID'))";
  String insertQuery =
      "INSERT INTO anomalies (id,accountid,actualcost,expectedcost,anomalytime,timegranularity,clusterid,clustername,namespace,workloadtype,workloadname,region,gcpproduct,gcpproject,gcpskuid,gcpskudescription,azuresubscriptionguid,azureresourcegroup,azuremetercategory,awsaccount,awsinstancetype,awsservice,awsusagetype,anomalyscore,reportedby,newentity) VALUES ('ANOMALY_ID1','ACCOUNT_ID',10.1,12.3,'1970-01-01T00:00:00Z','DAILY','CLUSTER_ID','CLUSTER_NAME',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,12.34,NULL,'false') ON CONFLICT (id,anomalytime) DO UPDATE SET actualcost = 10.100000 , expectedcost = 12.300000 ";
  String deleteQuery =
      "DELETE FROM anomalies WHERE ((id IN ('TEMP_ID_1') ) AND (anomalytime = '1970-01-01T00:00:00Z'))";
  @Before
  public void setUp() throws SQLException {
    MockitoAnnotations.initMocks(this);
    when(dbService.getDBConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeUpdate(updateQuery)).thenReturn(1);
    when(dbService.isValid()).thenReturn(true);
    when(statement.executeBatch()).thenReturn(new int[] {1});
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldUpdate() throws SQLException {
    AnomalyEntity clusterAnomaly = AnomalyDataStub.getClusterAnomaly();
    ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
    anomalyEntityDao.update(clusterAnomaly);
    verify(statement, times(1)).executeUpdate(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue()).isEqualTo(updateQuery);
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldDelete() throws SQLException {
    List<String> ids = new ArrayList<>();
    ids.add("TEMP_ID_1");
    ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
    anomalyEntityDao.delete(ids, AnomalyDataStub.anomalyTime);
    verify(statement, times(1)).execute(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue()).isEqualTo(deleteQuery);
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldInsert() throws SQLException {
    AnomalyEntity clusterAnomaly = AnomalyDataStub.getClusterAnomaly();
    ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
    anomalyEntityDao.insert(Arrays.asList(clusterAnomaly));
    verify(statement, times(1)).addBatch(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue()).isEqualTo(insertQuery);
  }
}
