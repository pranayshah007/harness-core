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

public class HarnessApprovalStepExecutionHandlerTest extends CategoryTest {
  @Mock private TimeScaleDBService timeScaleDBService;
  private HarnessApprovalStepExecutionHandler handler = new HarnessApprovalStepExecutionHandler();
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

    handler.handleChange(changeEvent, "harness_approval_step_execution", new String[] {});

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockConnection, times(2)).prepareStatement(queryCaptor.capture());

    final List<String> sqls = queryCaptor.getAllValues();
    assertThat(sqls.get(0))
        .isEqualTo(
            "INSERT INTO harness_approval_step_execution (comments,approved_at,approval_action,approved_by_email,id,approved_by_name) VALUES('approving','1689668962473','APPROVE','admin@harness.io','o8LMZ7EwScieAc0Pb1mj5w','Admin') ON CONFLICT (id,approved_by_email) Do UPDATE  SET comments='approving',approved_at='1689668962473',approval_action='APPROVE',approved_by_email='admin@harness.io',id='o8LMZ7EwScieAc0Pb1mj5w',approved_by_name='Admin'");
    assertThat(sqls.get(1))
        .isEqualTo(
            "INSERT INTO harness_approval_step_execution (comments,approved_at,approval_action,approved_by_email,id,approved_by_name) VALUES('approved!','1689668962473','APPROVE','rbac1@harness.io','o8LMZ7EwScieAc0Pb1mj5w','Rbac') ON CONFLICT (id,approved_by_email) Do UPDATE  SET comments='approved!',approved_at='1689668962473',approval_action='APPROVE',approved_by_email='rbac1@harness.io',id='o8LMZ7EwScieAc0Pb1mj5w',approved_by_name='Rbac'");
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void testInsertFail() throws IOException, SQLException {
    // setup mocks to read final sql query
    Connection mockConnection = mock(Connection.class);
    doReturn(mockConnection).when(timeScaleDBService).getDBConnection();
    doReturn(mock(PreparedStatement.class)).when(mockConnection).prepareStatement(anyString());

    ChangeEvent<PersistentEntity> changeEvent =
        generateInsertChangeEvent("harness_approval_step_execution_entity_failed.json");

    handler.handleChange(changeEvent, "harness_approval_step_execution", new String[] {});

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockConnection, times(2)).prepareStatement(queryCaptor.capture());

    final List<String> sqls = queryCaptor.getAllValues();
    assertThat(sqls.get(0))
        .isEqualTo(
            "INSERT INTO harness_approval_step_execution (comments,approved_at,approval_action,approved_by_email,id,approved_by_name) VALUES('approve','1688033958501','APPROVE','admin@harness.io','yAa9u1uGQm6CM8KjtcVxcA','Admin') ON CONFLICT (id,approved_by_email) Do UPDATE  SET comments='approve',approved_at='1688033958501',approval_action='APPROVE',approved_by_email='admin@harness.io',id='yAa9u1uGQm6CM8KjtcVxcA',approved_by_name='Admin'");
    assertThat(sqls.get(1))
        .isEqualTo(
            "INSERT INTO harness_approval_step_execution (comments,approved_at,approval_action,approved_by_email,id,approved_by_name) VALUES('rejecting!','1688033958501','REJECT','rbac1@harness.io','yAa9u1uGQm6CM8KjtcVxcA','Rbac') ON CONFLICT (id,approved_by_email) Do UPDATE  SET comments='rejecting!',approved_at='1688033958501',approval_action='REJECT',approved_by_email='rbac1@harness.io',id='yAa9u1uGQm6CM8KjtcVxcA',approved_by_name='Rbac'");
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void testUpdate() throws IOException, SQLException {
    // setup mocks to read final sql query
    Connection mockConnection = mock(Connection.class);
    doReturn(mockConnection).when(timeScaleDBService).getDBConnection();
    doReturn(mock(PreparedStatement.class)).when(mockConnection).prepareStatement(anyString());

    ChangeEvent<PersistentEntity> changeEvent =
        generateUpdateChangeEvent("harness_approval_step_execution_entity.json");

    handler.handleChange(changeEvent, "harness_approval_step_execution", new String[] {});

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockConnection, times(2)).prepareStatement(queryCaptor.capture());

    final List<String> sqls = queryCaptor.getAllValues();
    assertThat(sqls.get(0))
        .isEqualTo(
            "INSERT INTO harness_approval_step_execution (comments,approved_at,approval_action,approved_by_email,id,approved_by_name) VALUES('approving','1689668962473','APPROVE','admin@harness.io','o8LMZ7EwScieAc0Pb1mj5w','Admin') ON CONFLICT (id,approved_by_email) Do UPDATE  SET comments='approving',approved_at='1689668962473',approval_action='APPROVE',approved_by_email='admin@harness.io',id='o8LMZ7EwScieAc0Pb1mj5w',approved_by_name='Admin'");
    assertThat(sqls.get(1))
        .isEqualTo(
            "INSERT INTO harness_approval_step_execution (comments,approved_at,approval_action,approved_by_email,id,approved_by_name) VALUES('approved!','1689668962473','APPROVE','rbac1@harness.io','o8LMZ7EwScieAc0Pb1mj5w','Rbac') ON CONFLICT (id,approved_by_email) Do UPDATE  SET comments='approved!',approved_at='1689668962473',approval_action='APPROVE',approved_by_email='rbac1@harness.io',id='o8LMZ7EwScieAc0Pb1mj5w',approved_by_name='Rbac'");
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
