/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.changestreamsframework.ChangeType;
import io.harness.persistence.PersistentEntity;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.mongodb.BasicDBObject;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.joor.Reflect;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PlanExecutionSummaryChangeDataHandlerAllStagesTest extends CategoryTest {
  @Mock private TimeScaleDBService timeScaleDBService;
  @InjectMocks private PlanExecutionSummaryChangeDataHandlerAllStages handler;

  private AutoCloseable mocks;
  private final ClassLoader classLoader = this.getClass().getClassLoader();
  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    Reflect.on(handler).set("timeScaleDBService", timeScaleDBService);
    doReturn(true).when(timeScaleDBService).isValid();
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void testInsert() throws IOException, SQLException {
    // setup mocks to read final sql query
    Connection mockConnection = mock(Connection.class);
    doReturn(mockConnection).when(timeScaleDBService).getDBConnection();
    doReturn(mock(PreparedStatement.class)).when(mockConnection).prepareStatement(anyString());

    ChangeEvent<PersistentEntity> changeEvent = generateInsertChangeEvent("cdpipeline_execution_summary.json");

    handler.handleChange(changeEvent, "pipeline_execution_summary", new String[] {});

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockConnection).prepareStatement(queryCaptor.capture());

    final String sql = queryCaptor.getValue();
    assertThat(sql).isEqualTo(
        "INSERT INTO pipeline_execution_summary (accountId,pipelineIdentifier,orgIdentifier,trigger_type,name,endTs,startTs,id,projectIdentifier,planExecutionId,author_id,status) VALUES('OgiB4-xETamKNVAz-wQRjw','Azure_Web_Apps','Ng_Pipelines_K8s_Organisations','MANUAL','Azure Web Apps ','1670404155674','1670403351259','uuid','CdpNgGoldenPipelinesProject','iTDQmlTWRUGVlHiDIB5RTw','automationpipelinesng@mailinator.com','SUCCESS')");
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void testInsertWithAuthor() throws IOException, SQLException {
    // setup mocks to read final sql query
    Connection mockConnection = mock(Connection.class);
    doReturn(mockConnection).when(timeScaleDBService).getDBConnection();
    doReturn(mock(PreparedStatement.class)).when(mockConnection).prepareStatement(anyString());

    ChangeEvent<PersistentEntity> changeEvent = generateInsertChangeEvent("cdpipeline_execution_summary_with_ci.json");

    handler.handleChange(changeEvent, "pipeline_execution_summary", new String[] {});

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockConnection).prepareStatement(queryCaptor.capture());

    final String sql = queryCaptor.getValue();
    assertThat(sql).isEqualTo(
        "INSERT INTO pipeline_execution_summary (author_name,orgIdentifier,trigger_type,startTs,author_avatar,accountId,pipelineIdentifier,name,endTs,id,projectIdentifier,planExecutionId,author_id,status) VALUES('test author','Ng_Pipelines_K8s_Organisations','MANUAL','1670403351259','testavatar','OgiB4-xETamKNVAz-wQRjw','Azure_Web_Apps','Azure Web Apps ','1670404155674','uuid','CdpNgGoldenPipelinesProject','iTDQmlTWRUGVlHiDIB5RTw','automationpipelinesng@mailinator.com','SUCCESS')");
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void testInsertWithEmptyCI() throws IOException, SQLException {
    // setup mocks to read final sql query
    Connection mockConnection = mock(Connection.class);
    doReturn(mockConnection).when(timeScaleDBService).getDBConnection();
    doReturn(mock(PreparedStatement.class)).when(mockConnection).prepareStatement(anyString());

    ChangeEvent<PersistentEntity> changeEvent =
        generateInsertChangeEvent("cdpipeline_execution_summary_with_empty_ci.json");

    handler.handleChange(changeEvent, "pipeline_execution_summary", new String[] {});

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockConnection).prepareStatement(queryCaptor.capture());

    final String sql = queryCaptor.getValue();
    assertThat(sql).isEqualTo(
        "INSERT INTO pipeline_execution_summary (accountId,pipelineIdentifier,orgIdentifier,trigger_type,name,endTs,startTs,id,projectIdentifier,planExecutionId,author_id,status) VALUES('OgiB4-xETamKNVAz-wQRjw','Azure_Web_Apps','Ng_Pipelines_K8s_Organisations','MANUAL','Azure Web Apps ','1670404155674','1670403351259','uuid','CdpNgGoldenPipelinesProject','iTDQmlTWRUGVlHiDIB5RTw','automationpipelinesng@mailinator.com','SUCCESS')");
  }

  private ChangeEvent<PersistentEntity> generateInsertChangeEvent(String fileName) throws IOException {
    String s = readFile(fileName);
    return ChangeEvent.builder()
        .uuid("uuid")
        .changeType(ChangeType.INSERT)
        .fullDocument(BasicDBObject.parse(s))
        .build();
  }

  private String readFile(String fileName) throws IOException {
    final URL testFile = classLoader.getResource(fileName);
    return Resources.toString(testFile, Charsets.UTF_8);
  }
}