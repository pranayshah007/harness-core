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
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.changestreamsframework.ChangeType;
import io.harness.execution.stage.StageExecutionEntity;
import io.harness.persistence.PersistentEntity;
import io.harness.pms.pipeline.PipelineEntity;
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

public class TagsInfoNGCDChangeDataHandlerTest extends CategoryTest {
  @Mock private TimeScaleDBService timeScaleDBService;
  private TagsInfoNGCDChangeDataHandler handler = new TagsInfoNGCDChangeDataHandler();
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
  public void testInsertStageExecutionInfo() throws IOException, SQLException {
    // setup mocks to read final sql query
    Connection mockConnection = mock(Connection.class);
    doReturn(mockConnection).when(timeScaleDBService).getDBConnection();
    doReturn(mock(PreparedStatement.class)).when(mockConnection).prepareStatement(anyString());

    ChangeEvent<PersistentEntity> changeEvent =
        generateInsertChangeEvent("stage_execution_info.json", StageExecutionInfo.builder().build());

    handler.handleChange(changeEvent, "execution_tags_info_ng", new String[] {});

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockConnection, times(1)).prepareStatement(queryCaptor.capture());

    final List<String> sqls = queryCaptor.getAllValues();
    assertThat(sqls.get(0))
        .isEqualTo(
            "INSERT INTO execution_tags_info_ng (account_id,project_id,org_id,parent_id,id,parent_type,tags) VALUES('kmpySmUISimoRrJL6NL73w','testProject','default','WGorBC7tTFStlQshsrAgYA','uuid','STAGE_EXECUTION',ARRAY['hello\"\":ahello\"\\\"{}','hello\\\"\\\":a','simple:hello','secret:${ngSecretManager.obtain(\"account.pcf_pass\", -724166992)}','<+<+pipeline.stages.Determine_Changes.spec.execution.steps.List_Packages.output.outputVariables.packages>==null?\\\"\\\":<+pipeline.name>>']) ON CONFLICT (id,parent_type) Do UPDATE  SET account_id='kmpySmUISimoRrJL6NL73w',project_id='testProject',org_id='default',parent_id='WGorBC7tTFStlQshsrAgYA',id='uuid',parent_type='STAGE_EXECUTION',tags=ARRAY['hello\"\":ahello\"\\\"{}','hello\\\"\\\":a','simple:hello','secret:${ngSecretManager.obtain(\"account.pcf_pass\", -724166992)}','<+<+pipeline.stages.Determine_Changes.spec.execution.steps.List_Packages.output.outputVariables.packages>==null?\\\"\\\":<+pipeline.name>>']");
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void testInsertStageExecutionEntity() throws IOException, SQLException {
    // setup mocks to read final sql query
    Connection mockConnection = mock(Connection.class);
    doReturn(mockConnection).when(timeScaleDBService).getDBConnection();
    doReturn(mock(PreparedStatement.class)).when(mockConnection).prepareStatement(anyString());

    ChangeEvent<PersistentEntity> changeEvent =
        generateInsertChangeEvent("stage_execution_entity.json", StageExecutionEntity.builder().build());

    handler.handleChange(changeEvent, "execution_tags_info_ng", new String[] {});

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockConnection, times(1)).prepareStatement(queryCaptor.capture());

    final List<String> sqls = queryCaptor.getAllValues();
    assertThat(sqls.get(0))
        .isEqualTo(
            "INSERT INTO execution_tags_info_ng (account_id,project_id,org_id,parent_id,id,parent_type,tags) VALUES('kmpySmUISimoRrJL6NL73w','TAS','default','_bNe7lGaRbGTwrnlUMIiXQ','uuid','STAGE_EXECUTION',ARRAY['hello\"\":ahello\"\\\"{}','hello\\\"\\\":a','simple:hello','secret:${ngSecretManager.obtain(\"account.pcf_pass\", -724166992)}','<+<+pipeline.stages.Determine_Changes.spec.execution.steps.List_Packages.output.outputVariables.packages>==null?\\\"\\\":<+pipeline.name>>']) ON CONFLICT (id,parent_type) Do UPDATE  SET account_id='kmpySmUISimoRrJL6NL73w',project_id='TAS',org_id='default',parent_id='_bNe7lGaRbGTwrnlUMIiXQ',id='uuid',parent_type='STAGE_EXECUTION',tags=ARRAY['hello\"\":ahello\"\\\"{}','hello\\\"\\\":a','simple:hello','secret:${ngSecretManager.obtain(\"account.pcf_pass\", -724166992)}','<+<+pipeline.stages.Determine_Changes.spec.execution.steps.List_Packages.output.outputVariables.packages>==null?\\\"\\\":<+pipeline.name>>']");
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void testInsertPipelineEntity() throws IOException, SQLException {
    // setup mocks to read final sql query
    Connection mockConnection = mock(Connection.class);
    doReturn(mockConnection).when(timeScaleDBService).getDBConnection();
    doReturn(mock(PreparedStatement.class)).when(mockConnection).prepareStatement(anyString());

    ChangeEvent<PersistentEntity> changeEvent =
        generateInsertChangeEvent("pipeline.json", PipelineEntity.builder().build());

    handler.handleChange(changeEvent, "tags_info_ng", new String[] {});

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockConnection, times(1)).prepareStatement(queryCaptor.capture());

    final List<String> sqls = queryCaptor.getAllValues();
    assertThat(sqls.get(0))
        .isEqualTo(
            "INSERT INTO tags_info_ng (account_id,project_id,org_id,parent_id,id,parent_type,tags) VALUES('kmpySmUISimoRrJL6NL73w','dummyPipelines','default','dummyK8s','uuid','PIPELINE',ARRAY['secret:<+secrets.getValue(\\\"account.pcf_pass\\\")>','<+<+pipeline.stages.Determine_Changes.spec.execution.steps.List_Packages.output.outputVariables.packages>==null?\\\"\\\":<+pipeline.name>>','hello\\\"\\\":a','hello\\\"\\\":ahello\\\"\\\"{}','secretas:<+secrets.getValue(\\\"account.pcf_pass\\\")>']) ON CONFLICT (id,parent_type) Do UPDATE  SET account_id='kmpySmUISimoRrJL6NL73w',project_id='dummyPipelines',org_id='default',parent_id='dummyK8s',id='uuid',parent_type='PIPELINE',tags=ARRAY['secret:<+secrets.getValue(\\\"account.pcf_pass\\\")>','<+<+pipeline.stages.Determine_Changes.spec.execution.steps.List_Packages.output.outputVariables.packages>==null?\\\"\\\":<+pipeline.name>>','hello\\\"\\\":a','hello\\\"\\\":ahello\\\"\\\"{}','secretas:<+secrets.getValue(\\\"account.pcf_pass\\\")>']");
  }

  private ChangeEvent<PersistentEntity> generateInsertChangeEvent(String fileName, Object o) throws IOException {
    String s = readFile(fileName);
    return ChangeEvent.builder()
        .uuid("uuid")
        .changeType(ChangeType.INSERT)
        .fullDocument(BasicDBObject.parse(s))
        .entityType((Class<PersistentEntity>) o.getClass())
        .build();
  }

  private String readFile(String fileName) throws IOException {
    final URL testFile = classLoader.getResource(fileName);
    return Resources.toString(testFile, Charsets.UTF_8);
  }
}
