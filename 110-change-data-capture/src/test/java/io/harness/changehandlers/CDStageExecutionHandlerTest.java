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
public class CDStageExecutionHandlerTest extends CategoryTest {
  @Mock private TimeScaleDBService timeScaleDBService;
  private ChangeHandlerHelper helper = new ChangeHandlerHelper();
  private CDStageExecutionHandler handler = new CDStageExecutionHandler();
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
  @Owner(developers = OwnerRule.ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testInsert() throws IOException, SQLException {
    // setup mocks to read final sql query
    Connection mockConnection = mock(Connection.class);
    doReturn(mockConnection).when(timeScaleDBService).getDBConnection();
    doReturn(mock(PreparedStatement.class)).when(mockConnection).prepareStatement(anyString());

    ChangeEvent<PersistentEntity> changeEvent = generateInsertChangeEvent("stage_execution_info.json");

    handler.handleChange(changeEvent, "cd_stage_execution", new String[] {});

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockConnection, times(1)).prepareStatement(queryCaptor.capture());

    final List<String> sqls = queryCaptor.getAllValues();
    assertThat(sqls.get(0))
        .isEqualTo(
            "INSERT INTO cd_stage_execution (deployment_type,infra_id,env_id,env_name,infra_name,artifact_display_name,rollback_duration,service_name,service_id,id,failure_message,env_type) VALUES('Kubernetes','infraName','envRedesign','envRedesign','infraName','library/nginx:stable-perl','25728','serNewDesign','serNewDesign','WGorBC7tTFStlQshsrAgYA','Shell Script execution failed. Please check execution logs.','Production') ON CONFLICT (id) Do UPDATE  SET deployment_type='Kubernetes',infra_id='infraName',env_id='envRedesign',env_name='envRedesign',infra_name='infraName',artifact_display_name='library/nginx:stable-perl',rollback_duration='25728',service_name='serNewDesign',service_id='serNewDesign',id='WGorBC7tTFStlQshsrAgYA',failure_message='Shell Script execution failed. Please check execution logs.',env_type='Production'");
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testUpdate() throws IOException, SQLException {
    // setup mocks to read final sql query
    Connection mockConnection = mock(Connection.class);
    doReturn(mockConnection).when(timeScaleDBService).getDBConnection();
    doReturn(mock(PreparedStatement.class)).when(mockConnection).prepareStatement(anyString());

    ChangeEvent<PersistentEntity> changeEvent = generateInsertChangeEvent("stage_execution_info.json");

    handler.handleChange(changeEvent, "cd_stage_execution", new String[] {});

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockConnection, times(1)).prepareStatement(queryCaptor.capture());

    final List<String> sqls = queryCaptor.getAllValues();
    assertThat(sqls.get(0))
        .isEqualTo(
            "INSERT INTO cd_stage_execution (deployment_type,infra_id,env_id,env_name,infra_name,artifact_display_name,rollback_duration,service_name,service_id,id,failure_message,env_type) VALUES('Kubernetes','infraName','envRedesign','envRedesign','infraName','library/nginx:stable-perl','25728','serNewDesign','serNewDesign','WGorBC7tTFStlQshsrAgYA','Shell Script execution failed. Please check execution logs.','Production') ON CONFLICT (id) Do UPDATE  SET deployment_type='Kubernetes',infra_id='infraName',env_id='envRedesign',env_name='envRedesign',infra_name='infraName',artifact_display_name='library/nginx:stable-perl',rollback_duration='25728',service_name='serNewDesign',service_id='serNewDesign',id='WGorBC7tTFStlQshsrAgYA',failure_message='Shell Script execution failed. Please check execution logs.',env_type='Production'");
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
