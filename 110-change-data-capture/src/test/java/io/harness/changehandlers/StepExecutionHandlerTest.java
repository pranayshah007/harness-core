/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.changehandlers.helper.ChangeHandlerHelper;
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
import java.util.List;
import org.joor.Reflect;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class StepExecutionHandlerTest extends CategoryTest {
  @Mock private TimeScaleDBService timeScaleDBService;
  private StepExecutionHandler handler = new StepExecutionHandler();
  private ChangeHandlerHelper helper = new ChangeHandlerHelper();
  private AutoCloseable mocks;
  private final ClassLoader classLoader = this.getClass().getClassLoader();
  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    Reflect.on(handler).set("timeScaleDBService", timeScaleDBService);
    Reflect.on(handler).set("changeHandlerHelper", helper);
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

    ChangeEvent<PersistentEntity> changeEvent =
        generateInsertChangeEvent("harness_approval_step_execution_entity.json");

    handler.handleChange(changeEvent, "step_execution", new String[] {});

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockConnection, times(1)).prepareStatement(queryCaptor.capture());

    final List<String> sqls = queryCaptor.getAllValues();
    assertThat(sqls.get(0))
        .isEqualTo(
            "INSERT INTO step_execution (stage_execution_id,plan_execution_id,step_execution_id,org_identifier,account_identifier,end_time,type,project_identifier,duration,start_time,pipeline_identifier,name,id,step_identifier,status) VALUES('i1irq3HVT_i1vkJKUq3jKA','tYn28B4nRqiq1Ys-W4AoWw','o8LMZ7EwScieAc0Pb1mj5w','default','kmpySmUISimoRrJL6NL73w','1689668962723','HarnessApproval','TAS','7927','1689668954796','testApproval','HarnessApproval_1','uuid','HarnessApproval_1','SUCCEEDED')");
  }
  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void testInsertEmptyExecutionSummary() throws IOException, SQLException {
    // setup mocks to read final sql query
    Connection mockConnection = mock(Connection.class);
    doReturn(mockConnection).when(timeScaleDBService).getDBConnection();
    doReturn(mock(PreparedStatement.class)).when(mockConnection).prepareStatement(anyString());

    ChangeEvent<PersistentEntity> changeEvent = generateInsertChangeEvent("step_execution_entity.json");

    handler.handleChange(changeEvent, "step_execution", new String[] {});

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockConnection, times(1)).prepareStatement(queryCaptor.capture());

    final List<String> sqls = queryCaptor.getAllValues();
    assertThat(sqls.get(0))
        .isEqualTo(
            "INSERT INTO step_execution (stage_execution_id,plan_execution_id,step_execution_id,org_identifier,account_identifier,end_time,type,project_identifier,duration,start_time,pipeline_identifier,name,id,step_identifier,status) VALUES('i1irq3HVT_i1vkJKUq3jKA','tYn28B4nRqiq1Ys-W4AoWw','o8LMZ7EwScieAc0Pb1mj5w','default','kmpySmUISimoRrJL6NL73w','1689668962723','HarnessApproval','TAS','7927','1689668954796','testApproval','HarnessApproval_1','uuid','HarnessApproval_1','SUCCEEDED')");
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void testUpdate() throws IOException, SQLException {
    // setup mocks to read final sql query
    Connection mockConnection = mock(Connection.class);
    doReturn(mockConnection).when(timeScaleDBService).getDBConnection();
    doReturn(mock(PreparedStatement.class)).when(mockConnection).prepareStatement(anyString());

    ChangeEvent<PersistentEntity> changeEvent = generateUpdateChangeEvent("jira_approval_step_execution_entity.json");

    handler.handleChange(changeEvent, "step_execution", new String[] {});

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockConnection, times(1)).prepareStatement(queryCaptor.capture());

    final List<String> sqls = queryCaptor.getAllValues();
    assertThat(sqls.get(0))
        .isEqualTo(
            "INSERT INTO step_execution (stage_execution_id,plan_execution_id,step_execution_id,org_identifier,account_identifier,end_time,type,project_identifier,duration,start_time,pipeline_identifier,name,id,step_identifier,status) VALUES('i1irq3HVT_i1vkJKUq3jKA','tYn28B4nRqiq1Ys-W4AoWw','TnTzF1TmTZ-J9TTuEaFX4w','default','kmpySmUISimoRrJL6NL73w','1689669038401','JiraApproval','TAS','62514','1689668975887','testApproval','JiraApproval_1','uuid','JiraApproval_1','SUCCEEDED') ON CONFLICT (id) Do UPDATE  SET stage_execution_id='i1irq3HVT_i1vkJKUq3jKA',plan_execution_id='tYn28B4nRqiq1Ys-W4AoWw',step_execution_id='TnTzF1TmTZ-J9TTuEaFX4w',org_identifier='default',account_identifier='kmpySmUISimoRrJL6NL73w',end_time='1689669038401',type='JiraApproval',project_identifier='TAS',duration='62514',start_time='1689668975887',pipeline_identifier='testApproval',name='JiraApproval_1',id='uuid',step_identifier='JiraApproval_1',status='SUCCEEDED'");
  }

  private ChangeEvent<PersistentEntity> generateInsertChangeEvent(String fileName) throws IOException {
    String s = readFile(fileName);
    return ChangeEvent.builder()
        .uuid("uuid")
        .changeType(ChangeType.INSERT)
        .fullDocument(BasicDBObject.parse(s))
        .build();
  }

  private ChangeEvent<PersistentEntity> generateUpdateChangeEvent(String fileName) throws IOException {
    String s = readFile(fileName);
    return ChangeEvent.builder()
        .uuid("uuid")
        .changeType(ChangeType.UPDATE)
        .fullDocument(BasicDBObject.parse(s))
        .build();
  }

  private String readFile(String fileName) throws IOException {
    final URL testFile = classLoader.getResource(fileName);
    return Resources.toString(testFile, Charsets.UTF_8);
  }
}
