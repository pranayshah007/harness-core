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

public class RuntimeInputsInfoCDChangeDataHandlerTest extends CategoryTest {
  @Mock private TimeScaleDBService timeScaleDBService;
  private RuntimeInputsInfoCDChangeDataHandler handler = new RuntimeInputsInfoCDChangeDataHandler();
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

    handler.handleChange(changeEvent, "runtime_inputs_info", new String[] {});

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockConnection, times(2)).prepareStatement(queryCaptor.capture());

    final List<String> sqls = queryCaptor.getAllValues();
    assertThat(sqls.get(0))
        .isEqualTo(
            "INSERT INTO runtime_inputs_info (fqn_hash,fqn,account_id,plan_execution_id,org_identifier,id,display_name,project_identifier,input_value) VALUES('c218a679320bbc8fdaafa91b35b0865e','pipeline.stages.Canary_zip_deployment_linux.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.artifactPath','OgiB4-xETamKNVAz-wQRjw','iTDQmlTWRUGVlHiDIB5RTw','Ng_Pipelines_K8s_Organisations','uuid','artifactPath','CdpNgGoldenPipelinesProject','spring-boot-hello.jar.zip') ON CONFLICT (id,fqn_hash) Do UPDATE  SET fqn_hash='c218a679320bbc8fdaafa91b35b0865e',fqn='pipeline.stages.Canary_zip_deployment_linux.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.artifactPath',account_id='OgiB4-xETamKNVAz-wQRjw',plan_execution_id='iTDQmlTWRUGVlHiDIB5RTw',org_identifier='Ng_Pipelines_K8s_Organisations',id='uuid',display_name='artifactPath',project_identifier='CdpNgGoldenPipelinesProject',input_value='spring-boot-hello.jar.zip'");
    assertThat(sqls.get(1))
        .isEqualTo(
            "INSERT INTO runtime_inputs_info (fqn_hash,fqn,account_id,plan_execution_id,org_identifier,id,display_name,project_identifier,input_value) VALUES('e11bb07885b4e8bf8fa47b9c4dcd5418','pipeline.stages.Canary_zip_deployment_linux.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.tag','OgiB4-xETamKNVAz-wQRjw','iTDQmlTWRUGVlHiDIB5RTw','Ng_Pipelines_K8s_Organisations','uuid','tag','CdpNgGoldenPipelinesProject','v1') ON CONFLICT (id,fqn_hash) Do UPDATE  SET fqn_hash='e11bb07885b4e8bf8fa47b9c4dcd5418',fqn='pipeline.stages.Canary_zip_deployment_linux.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.tag',account_id='OgiB4-xETamKNVAz-wQRjw',plan_execution_id='iTDQmlTWRUGVlHiDIB5RTw',org_identifier='Ng_Pipelines_K8s_Organisations',id='uuid',display_name='tag',project_identifier='CdpNgGoldenPipelinesProject',input_value='v1'");
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void testUpdate() throws IOException, SQLException {
    // setup mocks to read final sql query
    Connection mockConnection = mock(Connection.class);
    doReturn(mockConnection).when(timeScaleDBService).getDBConnection();
    doReturn(mock(PreparedStatement.class)).when(mockConnection).prepareStatement(anyString());

    ChangeEvent<PersistentEntity> changeEvent = generateUpdateChangeEvent("cdpipeline_execution_summary.json");

    handler.handleChange(changeEvent, "runtime_inputs_info", new String[] {});

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockConnection, times(2)).prepareStatement(queryCaptor.capture());

    final List<String> sqls = queryCaptor.getAllValues();
    assertThat(sqls.get(0))
        .isEqualTo(
            "INSERT INTO runtime_inputs_info (fqn_hash,fqn,account_id,plan_execution_id,org_identifier,id,display_name,project_identifier,input_value) VALUES('c218a679320bbc8fdaafa91b35b0865e','pipeline.stages.Canary_zip_deployment_linux.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.artifactPath','OgiB4-xETamKNVAz-wQRjw','iTDQmlTWRUGVlHiDIB5RTw','Ng_Pipelines_K8s_Organisations','uuid','artifactPath','CdpNgGoldenPipelinesProject','spring-boot-hello.jar.zip') ON CONFLICT (id,fqn_hash) Do UPDATE  SET fqn_hash='c218a679320bbc8fdaafa91b35b0865e',fqn='pipeline.stages.Canary_zip_deployment_linux.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.artifactPath',account_id='OgiB4-xETamKNVAz-wQRjw',plan_execution_id='iTDQmlTWRUGVlHiDIB5RTw',org_identifier='Ng_Pipelines_K8s_Organisations',id='uuid',display_name='artifactPath',project_identifier='CdpNgGoldenPipelinesProject',input_value='spring-boot-hello.jar.zip'");
    assertThat(sqls.get(1))
        .isEqualTo(
            "INSERT INTO runtime_inputs_info (fqn_hash,fqn,account_id,plan_execution_id,org_identifier,id,display_name,project_identifier,input_value) VALUES('e11bb07885b4e8bf8fa47b9c4dcd5418','pipeline.stages.Canary_zip_deployment_linux.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.tag','OgiB4-xETamKNVAz-wQRjw','iTDQmlTWRUGVlHiDIB5RTw','Ng_Pipelines_K8s_Organisations','uuid','tag','CdpNgGoldenPipelinesProject','v1') ON CONFLICT (id,fqn_hash) Do UPDATE  SET fqn_hash='e11bb07885b4e8bf8fa47b9c4dcd5418',fqn='pipeline.stages.Canary_zip_deployment_linux.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.tag',account_id='OgiB4-xETamKNVAz-wQRjw',plan_execution_id='iTDQmlTWRUGVlHiDIB5RTw',org_identifier='Ng_Pipelines_K8s_Organisations',id='uuid',display_name='tag',project_identifier='CdpNgGoldenPipelinesProject',input_value='v1'");
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
