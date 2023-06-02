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

public class PlanExecutionSummaryCdChangeServiceInfraChangeDataHandlerNewTest extends CategoryTest {
  @Mock private TimeScaleDBService timeScaleDBService;
  private PlanExecutionSummaryCdChangeServiceInfraChangeDataHandlerNew handler =
      new PlanExecutionSummaryCdChangeServiceInfraChangeDataHandlerNew();
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
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testInsert() throws IOException, SQLException {
    // setup mocks to read final sql query
    Connection mockConnection = mock(Connection.class);
    doReturn(mockConnection).when(timeScaleDBService).getDBConnection();
    doReturn(mock(PreparedStatement.class)).when(mockConnection).prepareStatement(anyString());

    ChangeEvent<PersistentEntity> changeEvent = generateInsertChangeEvent("cdpipeline_execution_summary.json");

    handler.handleChange(changeEvent, "service_infra_info", new String[] {});

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockConnection, times(4)).prepareStatement(queryCaptor.capture());

    final List<String> sqls = queryCaptor.getAllValues();
    assertThat(sqls.get(0))
        .isEqualTo(
            "INSERT INTO service_infra_info (orgIdentifier,rollback_duration,pipeline_execution_summary_cd_id,service_name,infrastructureName,infrastructureIdentifier,service_startts,service_endts,accountId,deployment_type,env_name,env_id,artifact_image,service_id,service_status,id,tag,projectIdentifier,env_type,gitOpsEnabled) VALUES('Ng_Pipelines_K8s_Organisations','0','uuid','azure_webapp_docker_windows_service','azure_webapp_infrastructure','azure_webapp_infrastructure','1670403430625','1670403575743','OgiB4-xETamKNVAz-wQRjw','AzureWebApp','azure_webapp_environment','azure_webapp_environment','library/hello-world','azure_webapp_docker_windows_service','SUCCESS','k-8s_imtQGSlDGbt7NDKbA','nanoserver-ltsc2022','CdpNgGoldenPipelinesProject','PreProduction','false')");
    assertThat(sqls.get(1))
        .isEqualTo(
            "INSERT INTO service_infra_info (orgIdentifier,rollback_duration,pipeline_execution_summary_cd_id,service_name,infrastructureName,infrastructureIdentifier,service_startts,service_endts,accountId,deployment_type,env_name,env_id,artifact_image,service_id,service_status,id,tag,projectIdentifier,env_type,gitOpsEnabled) VALUES('Ng_Pipelines_K8s_Organisations','0','uuid','azure_webapp_zip_artifactory_service','azure_webapp_infrastructure','azure_webapp_infrastructure','1670403858517','1670404155590','OgiB4-xETamKNVAz-wQRjw','AzureWebApp','azure_webapp_environment','azure_webapp_environment','spring-boot-hello.jar.zip','azure_webapp_ja_artifactory_service','SUCCESS','gYY54NQJRKaTwlvWwTXsxw','spring-boot-hello.jar.zip','CdpNgGoldenPipelinesProject','PreProduction','false')");
    assertThat(sqls.get(2))
        .isEqualTo(
            "INSERT INTO service_infra_info (orgIdentifier,rollback_duration,pipeline_execution_summary_cd_id,service_name,infrastructureName,infrastructureIdentifier,service_startts,service_endts,accountId,deployment_type,env_name,env_id,artifact_image,service_id,service_status,id,tag,projectIdentifier,env_type,gitOpsEnabled) VALUES('Ng_Pipelines_K8s_Organisations','0','uuid','azure_webapp_docker_service','azure_webapp_infrastructure','azure_webapp_infrastructure','1670403575815','1670403858442','OgiB4-xETamKNVAz-wQRjw','AzureWebApp','azure_webapp_environment','azure_webapp_environment','crccheck/hello-world','azure_webapp_docker_service','SUCCESS','hAQOeXA4T_WYX2-QEn6wCA','v1.0.0','CdpNgGoldenPipelinesProject','PreProduction','false')");
    assertThat(sqls.get(3))
        .isEqualTo(
            "INSERT INTO service_infra_info (orgIdentifier,rollback_duration,pipeline_execution_summary_cd_id,service_name,infrastructureName,infrastructureIdentifier,service_startts,service_endts,accountId,deployment_type,env_name,env_id,artifact_image,service_id,service_status,id,tag,projectIdentifier,env_type,gitOpsEnabled) VALUES('Ng_Pipelines_K8s_Organisations','0','uuid','azure_webapp_artifactory_service','azure_webapp_infrastructure','azure_webapp_infrastructure','1670403351661','1670403430495','OgiB4-xETamKNVAz-wQRjw','AzureWebApp','azure_webapp_environment','azure_webapp_environment','spring-boot-hello.jar.zip','azure_webapp_artifactory_service','SUCCESS','hSqsfY1oR_aFLqEpShyb-w','spring-boot-hello.jar.zip','CdpNgGoldenPipelinesProject','PreProduction','false')");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testInsertGitOps() throws IOException, SQLException {
    // setup mocks to read final sql query
    Connection mockConnection = mock(Connection.class);
    doReturn(mockConnection).when(timeScaleDBService).getDBConnection();
    doReturn(mock(PreparedStatement.class)).when(mockConnection).prepareStatement(anyString());

    ChangeEvent<PersistentEntity> changeEvent = generateInsertChangeEvent("cdpipeline_execution_summary_gitops.json");

    handler.handleChange(changeEvent, "service_infra_info", new String[] {});

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockConnection, times(8)).prepareStatement(queryCaptor.capture());

    final List<String> sqls = queryCaptor.getAllValues();

    assertThat(sqls.get(0))
        .isEqualTo(
            "INSERT INTO service_infra_info (env_group_name,pipeline_execution_summary_cd_id,service_name,service_startts,service_endts,deployment_type,env_name,env_id,env_group_ref,service_id,service_status,id,env_type,gitOpsEnabled) VALUES('EnvGroup-1Env','uuid','service2','1671346275772','1671346296079','Kubernetes','EnvProjectLevelAgents','env2','EnvGroup1','service2','Success','gPfCWZ9WQxWOw5miYocUfA','Production','true')");
    assertThat(sqls.get(1))
        .isEqualTo(
            "INSERT INTO service_infra_info (env_group_name,pipeline_execution_summary_cd_id,service_name,service_startts,service_endts,deployment_type,env_name,env_id,env_group_ref,service_id,service_status,id,env_type,gitOpsEnabled) VALUES('EnvGroup-2Env','uuid','service5','1671346298085','1671346316016','Kubernetes','EnvProjectLevelAgents','env2','envGroup','service5','Success','vRVRcCMURs-fUu2oRxjzMA','PreProduction','true')");
    assertThat(sqls.get(2))
        .isEqualTo(
            "INSERT INTO service_infra_info (rollback_duration,pipeline_execution_summary_cd_id,service_name,service_startts,service_endts,deployment_type,service_id,service_status,id,execution_failure_details,gitOpsEnabled) VALUES('46','uuid','service3','1671346316187','1671346326076','Kubernetes','service3','Failed','fkNFsY9YToqnSL4wUfqg8w','No files were committed. Hence not creating a pull request.','true')");
    assertThat(sqls.get(3))
        .isEqualTo(
            "INSERT INTO service_infra_info (env_group_name,pipeline_execution_summary_cd_id,service_name,service_startts,service_endts,deployment_type,env_name,env_id,env_group_ref,service_id,service_status,id,gitOpsEnabled) VALUES('EnvGroup-1Env','uuid','service4','1671346298094','1671346313953','Kubernetes','EnvProjectLevelAgents','env2','EnvGroup1','service4','Success','ev9_xNp0T96yPcfbFe7Mnw','true')");
    assertThat(sqls.get(4))
        .isEqualTo(
            "INSERT INTO service_infra_info (env_group_name,pipeline_execution_summary_cd_id,service_name,service_startts,service_endts,deployment_type,env_name,env_id,env_group_ref,service_id,service_status,id,gitOpsEnabled) VALUES('EnvGroup-1Env','uuid','service1','1671346275754','1671346296111','Kubernetes','EnvProjectLevelAgents','env2','EnvGroup1','service1','Success','FNra8NwISJ2qUWFyz9vPiA','true')");
    assertThat(sqls.get(5))
        .isEqualTo(
            "INSERT INTO service_infra_info (rollback_duration,pipeline_execution_summary_cd_id,service_name,service_startts,service_endts,deployment_type,service_id,service_status,id,execution_failure_details,gitOpsEnabled) VALUES('0','uuid','service2','1671346316182','1671346318850','Kubernetes','service2','Failed','M7v0YIX9QsCD1n0S_FuFXA','Invalid request: No GitOps Cluster is selected with the current environment configuration','true')");
    assertThat(sqls.get(6))
        .isEqualTo(
            "INSERT INTO service_infra_info (env_group_name,pipeline_execution_summary_cd_id,service_name,service_startts,service_endts,deployment_type,env_name,env_id,env_group_ref,service_id,service_status,id,gitOpsEnabled) VALUES('EnvGroup-1Env','uuid','service3','1671346275760','1671346297916','Kubernetes','EnvProjectLevelAgents','env2','EnvGroup1','service3','Success','rA7k-ziARyO4aTxKl4THvA','true')");
    assertThat(sqls.get(7))
        .isEqualTo(
            "INSERT INTO service_infra_info (env_group_name,pipeline_execution_summary_cd_id,service_name,service_startts,service_endts,deployment_type,env_name,env_id,env_group_ref,service_id,service_status,id,gitOpsEnabled) VALUES('EnvGroup-2Env','uuid','service1','1671346316182','1671346334009','Kubernetes','EnvProjectLevelAgents','env2','envGroup','service1','Success','B9u3mW0yQwCIovStIGzGkg','true')");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testUpdate() throws IOException, SQLException {
    // setup mocks to read final sql query
    Connection mockConnection = mock(Connection.class);
    doReturn(mockConnection).when(timeScaleDBService).getDBConnection();
    doReturn(mock(PreparedStatement.class)).when(mockConnection).prepareStatement(anyString());

    ChangeEvent<PersistentEntity> changeEvent = generateUpdateChangeEvent("cdpipeline_execution_summary.json");

    handler.handleChange(changeEvent, "service_infra_info", new String[] {});

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockConnection, times(4)).prepareStatement(queryCaptor.capture());

    final List<String> sqls = queryCaptor.getAllValues();
    assertThat(sqls.get(0))
        .isEqualTo("INSERT INTO service_infra_info (orgIdentifier,rollback_duration,pipeline_execution_summary_cd_id,service_name,infrastructureName,infrastructureIdentifier,service_startts,service_endts,accountId,deployment_type,env_name,env_id,artifact_image,service_id,service_status,id,tag,projectIdentifier,env_type,gitOpsEnabled) VALUES('Ng_Pipelines_K8s_Organisations','0','uuid','azure_webapp_docker_windows_service','azure_webapp_infrastructure','azure_webapp_infrastructure','1670403430625','1670403575743','OgiB4-xETamKNVAz-wQRjw','AzureWebApp','azure_webapp_environment','azure_webapp_environment','library/hello-world','azure_webapp_docker_windows_service','SUCCESS','k-8s_imtQGSlDGbt7NDKbA','nanoserver-ltsc2022','CdpNgGoldenPipelinesProject','PreProduction','false') ON CONFLICT (id,service_startts) Do UPDATE  SET orgIdentifier='Ng_Pipelines_K8s_Organisations',rollback_duration='0',pipeline_execution_summary_cd_id='uuid',service_name='azure_webapp_docker_windows_service',infrastructureName='azure_webapp_infrastructure',infrastructureIdentifier='azure_webapp_infrastructure',service_startts='1670403430625',service_endts='1670403575743',accountId='OgiB4-xETamKNVAz-wQRjw',deployment_type='AzureWebApp',env_name='azure_webapp_environment',env_id='azure_webapp_environment',artifact_image='library/hello-world',service_id='azure_webapp_docker_windows_service',service_status='SUCCESS',id='k-8s_imtQGSlDGbt7NDKbA',tag='nanoserver-ltsc2022',projectIdentifier='CdpNgGoldenPipelinesProject',env_type='PreProduction',gitOpsEnabled='false'");
    assertThat(sqls.get(1))
        .isEqualTo(
            "INSERT INTO service_infra_info (orgIdentifier,rollback_duration,pipeline_execution_summary_cd_id,service_name,infrastructureName,infrastructureIdentifier,service_startts,service_endts,accountId,deployment_type,env_name,env_id,artifact_image,service_id,service_status,id,tag,projectIdentifier,env_type,gitOpsEnabled) VALUES('Ng_Pipelines_K8s_Organisations','0','uuid','azure_webapp_zip_artifactory_service','azure_webapp_infrastructure','azure_webapp_infrastructure','1670403858517','1670404155590','OgiB4-xETamKNVAz-wQRjw','AzureWebApp','azure_webapp_environment','azure_webapp_environment','spring-boot-hello.jar.zip','azure_webapp_ja_artifactory_service','SUCCESS','gYY54NQJRKaTwlvWwTXsxw','spring-boot-hello.jar.zip','CdpNgGoldenPipelinesProject','PreProduction','false') ON CONFLICT (id,service_startts) Do UPDATE  SET orgIdentifier='Ng_Pipelines_K8s_Organisations',rollback_duration='0',pipeline_execution_summary_cd_id='uuid',service_name='azure_webapp_zip_artifactory_service',infrastructureName='azure_webapp_infrastructure',infrastructureIdentifier='azure_webapp_infrastructure',service_startts='1670403858517',service_endts='1670404155590',accountId='OgiB4-xETamKNVAz-wQRjw',deployment_type='AzureWebApp',env_name='azure_webapp_environment',env_id='azure_webapp_environment',artifact_image='spring-boot-hello.jar.zip',service_id='azure_webapp_ja_artifactory_service',service_status='SUCCESS',id='gYY54NQJRKaTwlvWwTXsxw',tag='spring-boot-hello.jar.zip',projectIdentifier='CdpNgGoldenPipelinesProject',env_type='PreProduction',gitOpsEnabled='false'");
    assertThat(sqls.get(2))
        .isEqualTo(
            "INSERT INTO service_infra_info (orgIdentifier,rollback_duration,pipeline_execution_summary_cd_id,service_name,infrastructureName,infrastructureIdentifier,service_startts,service_endts,accountId,deployment_type,env_name,env_id,artifact_image,service_id,service_status,id,tag,projectIdentifier,env_type,gitOpsEnabled) VALUES('Ng_Pipelines_K8s_Organisations','0','uuid','azure_webapp_docker_service','azure_webapp_infrastructure','azure_webapp_infrastructure','1670403575815','1670403858442','OgiB4-xETamKNVAz-wQRjw','AzureWebApp','azure_webapp_environment','azure_webapp_environment','crccheck/hello-world','azure_webapp_docker_service','SUCCESS','hAQOeXA4T_WYX2-QEn6wCA','v1.0.0','CdpNgGoldenPipelinesProject','PreProduction','false') ON CONFLICT (id,service_startts) Do UPDATE  SET orgIdentifier='Ng_Pipelines_K8s_Organisations',rollback_duration='0',pipeline_execution_summary_cd_id='uuid',service_name='azure_webapp_docker_service',infrastructureName='azure_webapp_infrastructure',infrastructureIdentifier='azure_webapp_infrastructure',service_startts='1670403575815',service_endts='1670403858442',accountId='OgiB4-xETamKNVAz-wQRjw',deployment_type='AzureWebApp',env_name='azure_webapp_environment',env_id='azure_webapp_environment',artifact_image='crccheck/hello-world',service_id='azure_webapp_docker_service',service_status='SUCCESS',id='hAQOeXA4T_WYX2-QEn6wCA',tag='v1.0.0',projectIdentifier='CdpNgGoldenPipelinesProject',env_type='PreProduction',gitOpsEnabled='false'");
    assertThat(sqls.get(3))
        .isEqualTo(
            "INSERT INTO service_infra_info (orgIdentifier,rollback_duration,pipeline_execution_summary_cd_id,service_name,infrastructureName,infrastructureIdentifier,service_startts,service_endts,accountId,deployment_type,env_name,env_id,artifact_image,service_id,service_status,id,tag,projectIdentifier,env_type,gitOpsEnabled) VALUES('Ng_Pipelines_K8s_Organisations','0','uuid','azure_webapp_artifactory_service','azure_webapp_infrastructure','azure_webapp_infrastructure','1670403351661','1670403430495','OgiB4-xETamKNVAz-wQRjw','AzureWebApp','azure_webapp_environment','azure_webapp_environment','spring-boot-hello.jar.zip','azure_webapp_artifactory_service','SUCCESS','hSqsfY1oR_aFLqEpShyb-w','spring-boot-hello.jar.zip','CdpNgGoldenPipelinesProject','PreProduction','false') ON CONFLICT (id,service_startts) Do UPDATE  SET orgIdentifier='Ng_Pipelines_K8s_Organisations',rollback_duration='0',pipeline_execution_summary_cd_id='uuid',service_name='azure_webapp_artifactory_service',infrastructureName='azure_webapp_infrastructure',infrastructureIdentifier='azure_webapp_infrastructure',service_startts='1670403351661',service_endts='1670403430495',accountId='OgiB4-xETamKNVAz-wQRjw',deployment_type='AzureWebApp',env_name='azure_webapp_environment',env_id='azure_webapp_environment',artifact_image='spring-boot-hello.jar.zip',service_id='azure_webapp_artifactory_service',service_status='SUCCESS',id='hSqsfY1oR_aFLqEpShyb-w',tag='spring-boot-hello.jar.zip',projectIdentifier='CdpNgGoldenPipelinesProject',env_type='PreProduction',gitOpsEnabled='false'");
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