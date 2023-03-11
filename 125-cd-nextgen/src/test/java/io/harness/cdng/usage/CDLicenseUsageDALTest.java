/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage;

import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.LAST_DEPLOYED_SERVICE_PROPERTY;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.LICENSES_CONSUMED_QUERY_PROPERTY;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.SERVICE_INSTANCES_QUERY_PROPERTY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cd.CDLicenseType;
import io.harness.cdng.usage.pojos.ActiveServiceBase;
import io.harness.cdng.usage.pojos.ActiveServiceFetchData;
import io.harness.cdng.usage.pojos.LicenseDateUsageFetchData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.licensing.usage.params.filter.LicenseDateUsageReportType;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.timescaledb.TimeScaleDBService;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.domain.Sort;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class CDLicenseUsageDALTest extends CategoryTest {
  private static final String accountIdentifier = "accountIdentifier";
  private static final String orgIdentifier = "orgIdentifier";
  private static final String projectIdentifier = "projectIdentifier";
  private static final String serviceIdentifier = "serviceIdentifier";

  @Mock TimeScaleDBService timeScaleDBService;
  @InjectMocks private CDLicenseUsageDAL cdLicenseUsageDAL;

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testBuildFilterOnServiceInfraInfoTable() {
    String filterOnServiceInfraInfoTable = cdLicenseUsageDAL.buildFilterOnServiceInfraInfoTable(null, null, null);

    assertThat(filterOnServiceInfraInfoTable).isBlank();
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testBuildFilterOnServiceInfraInfoTableWithOrgIdentifier() {
    String filterOnServiceInfraInfoTable =
        cdLicenseUsageDAL.buildFilterOnServiceInfraInfoTable(orgIdentifier, null, null);

    assertThat(filterOnServiceInfraInfoTable).isNotBlank();
    assertThat(filterOnServiceInfraInfoTable).isEqualTo("AND orgidentifier = ?");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testBuildFilterOnServiceInfraInfoTableWithProjectIdentifier() {
    String filterOnServiceInfraInfoTable =
        cdLicenseUsageDAL.buildFilterOnServiceInfraInfoTable(orgIdentifier, projectIdentifier, null);

    assertThat(filterOnServiceInfraInfoTable).isNotBlank();
    assertThat(filterOnServiceInfraInfoTable).isEqualTo("AND orgidentifier = ? AND projectidentifier = ?");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testBuildFilterOnServiceInfraInfoTableWithServiceIdentifier() {
    String filterOnServiceInfraInfoTable =
        cdLicenseUsageDAL.buildFilterOnServiceInfraInfoTable(orgIdentifier, projectIdentifier, serviceIdentifier);

    assertThat(filterOnServiceInfraInfoTable).isNotBlank();
    assertThat(filterOnServiceInfraInfoTable)
        .isEqualTo("AND orgidentifier = ? AND projectidentifier = ? AND service_id = ?");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testBuildFilterOnNGInstanceStatsTable() {
    String filterOnServiceInfraInfoTable = cdLicenseUsageDAL.buildFilterOnNGInstanceStatsTable(null, null, null);

    assertThat(filterOnServiceInfraInfoTable).isBlank();
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testBuildFilterOnNGInstanceStatsTableWithOrgIdentifier() {
    String filterOnServiceInfraInfoTable =
        cdLicenseUsageDAL.buildFilterOnNGInstanceStatsTable(orgIdentifier, null, null);

    assertThat(filterOnServiceInfraInfoTable).isNotBlank();
    assertThat(filterOnServiceInfraInfoTable).isEqualTo("AND orgid = ?");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testBuildFilterOnNGInstanceStatsTableWithProjectIdentifier() {
    String filterOnServiceInfraInfoTable =
        cdLicenseUsageDAL.buildFilterOnNGInstanceStatsTable(orgIdentifier, projectIdentifier, null);

    assertThat(filterOnServiceInfraInfoTable).isNotBlank();
    assertThat(filterOnServiceInfraInfoTable).isEqualTo("AND orgid = ? AND projectid = ?");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testBuildFilterOnNGInstanceStatsTableWithServiceIdentifier() {
    String filterOnServiceInfraInfoTable =
        cdLicenseUsageDAL.buildFilterOnNGInstanceStatsTable(orgIdentifier, projectIdentifier, serviceIdentifier);

    assertThat(filterOnServiceInfraInfoTable).isNotBlank();
    assertThat(filterOnServiceInfraInfoTable).isEqualTo("AND orgid = ? AND projectid = ? AND serviceid = ?");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testBuildSortCriteriaByServiceInstancesDesc() {
    String sortCriteria =
        cdLicenseUsageDAL.buildSortCriteria(Sort.by(Sort.Direction.DESC, SERVICE_INSTANCES_QUERY_PROPERTY));

    assertThat(sortCriteria).isNotBlank();
    assertThat(sortCriteria).isEqualTo("instanceCount DESC NULLS LAST");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testBuildSortCriteriaByLicencesConsumedDesc() {
    String sortCriteria =
        cdLicenseUsageDAL.buildSortCriteria(Sort.by(Sort.Direction.DESC, LICENSES_CONSUMED_QUERY_PROPERTY));

    assertThat(sortCriteria).isNotBlank();
    assertThat(sortCriteria).isEqualTo("instanceCount DESC NULLS LAST");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testBuildSortCriteriaByServiceByLastDeployedDesc() {
    String sortCriteria =
        cdLicenseUsageDAL.buildSortCriteria(Sort.by(Sort.Direction.DESC, LAST_DEPLOYED_SERVICE_PROPERTY));

    assertThat(sortCriteria).isNotBlank();
    assertThat(sortCriteria).isEqualTo("lastDeployed DESC NULLS LAST");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testFetchActiveServicesNameOrgAndProjectName() throws SQLException {
    Connection dbConnection = mock(Connection.class);
    PreparedStatement fetchStatement = mock(PreparedStatement.class);
    ResultSet resultSet = mock(ResultSet.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(dbConnection);
    when(dbConnection.prepareStatement(any())).thenReturn(fetchStatement);
    when(fetchStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(false);

    List<ActiveServiceBase> activeServiceBaseItems = List.of(ActiveServiceBase.builder()
                                                                 .orgIdentifier(orgIdentifier)
                                                                 .projectIdentifier(projectIdentifier)
                                                                 .identifier(serviceIdentifier)
                                                                 .instanceCount(2)
                                                                 .lastDeployed(1L)
                                                                 .build());
    cdLicenseUsageDAL.fetchActiveServicesNameOrgAndProjectName(
        accountIdentifier, activeServiceBaseItems, Sort.by(Sort.Direction.DESC, LAST_DEPLOYED_SERVICE_PROPERTY));

    ArgumentCaptor<String> queryArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(dbConnection, times(1)).prepareStatement(queryArgumentCaptor.capture());

    String sqlQuery = queryArgumentCaptor.getValue();
    assertThat(sqlQuery).isNotBlank();
    assertThat(sqlQuery).isEqualTo(""
        + "SELECT DISTINCT\n"
        + "    t.orgIdentifier, t.projectIdentifier, t.serviceIdentifier AS identifier, t.lastDeployed, t.instanceCount,\n"
        + "    COALESCE(organizations.name, 'Deleted') AS orgName,\n"
        + "    COALESCE(projects.name, 'Deleted') AS projectName,\n"
        + "    COALESCE(services.name, 'Deleted') AS name\n"
        + "FROM \n"
        + "    (\n"
        + "        VALUES ('orgIdentifier','projectIdentifier','serviceIdentifier',1,2)\n"
        + "    )\n"
        + "    AS t (orgIdentifier, projectIdentifier, serviceIdentifier, lastDeployed, instanceCount)\n"
        + "LEFT JOIN services ON\n"
        + "    services.account_id = ?\n"
        + "    AND t.orgidentifier = services.org_identifier\n"
        + "    AND t.projectidentifier = services.project_identifier\n"
        + "    AND t.serviceIdentifier = services.identifier\n"
        + " LEFT JOIN projects ON\n"
        + "    projects.account_identifier = ?\n"
        + "    AND t.orgidentifier = projects.org_identifier\n"
        + "    AND t.projectidentifier = projects.identifier\n"
        + " LEFT JOIN organizations ON\n"
        + "    organizations.account_identifier = ?\n"
        + "    AND t.orgidentifier = organizations.identifier\n"
        + "ORDER BY lastDeployed DESC NULLS LAST");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testFetchActiveServices() throws SQLException {
    Connection dbConnection = mock(Connection.class);
    PreparedStatement fetchStatement = mock(PreparedStatement.class);
    ResultSet resultSet = mock(ResultSet.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(dbConnection);
    when(dbConnection.prepareStatement(any())).thenReturn(fetchStatement);
    when(fetchStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(false);
    ActiveServiceFetchData fetchData = ActiveServiceFetchData.builder()
                                           .accountIdentifier(accountIdentifier)
                                           .orgIdentifier(orgIdentifier)
                                           .projectIdentifier(projectIdentifier)
                                           .serviceIdentifier(serviceIdentifier)
                                           .sort(Sort.by(Sort.Direction.DESC, LAST_DEPLOYED_SERVICE_PROPERTY))
                                           .pageNumber(0)
                                           .pageSize(1)
                                           .build();

    cdLicenseUsageDAL.fetchActiveServices(fetchData);

    ArgumentCaptor<String> queryArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(dbConnection, times(1)).prepareStatement(queryArgumentCaptor.capture());

    String sqlQuery = queryArgumentCaptor.getValue();
    assertThat(sqlQuery).isNotBlank();
    assertThat(sqlQuery).isEqualTo(""
        + "SELECT activeServices.orgIdentifier,\n"
        + "       activeServices.projectIdentifier,\n"
        + "       activeServices.serviceIdentifier AS identifier,\n"
        + "       activeServices.lastDeployedServiceTime AS lastDeployed,\n"
        + "       activeServices.totalCount,\n"
        + "       COALESCE(percentileInstancesPerServices.instanceCount, 0) AS instanceCount\n"
        + "FROM\n"
        + "-- List services deployed in last 30 days from service_infra_info table. 'Group by' is needed for lastDeployedServiceTime calculation\n"
        + "(\n"
        + "    SELECT orgidentifier AS orgIdentifier,\n"
        + "           projectidentifier AS projectIdentifier,\n"
        + "           service_id AS serviceIdentifier,\n"
        + "           MAX(service_startts) as lastDeployedServiceTime,\n"
        + "           COUNT(*) OVER () AS totalCount\n"
        + "    FROM service_infra_info\n"
        + "    WHERE (accountid = ? AND service_startts >= ? AND service_startts <= ? AND orgidentifier = ? AND projectidentifier = ? AND service_id = ?)\n"
        + "    GROUP BY orgidentifier, projectidentifier, service_id\n"
        + ") activeServices\n"
        + "    LEFT JOIN\n"
        + "-- List services percentile instances count from ng_instance_stats table\n"
        + "    (\n"
        + "        SELECT PERCENTILE_DISC(?) WITHIN GROUP (ORDER BY instancesPerService.instanceCount) AS instanceCount,\n"
        + "               orgid,\n"
        + "               projectid,\n"
        + "               serviceid\n"
        + "        FROM\n"
        + "            (\n"
        + "                SELECT DATE_TRUNC('minute', reportedat) AS reportedat,\n"
        + "                       orgid,\n"
        + "                       projectid,\n"
        + "                       serviceid,\n"
        + "                       SUM(instancecount) AS instanceCount\n"
        + "                FROM ng_instance_stats\n"
        + "                WHERE accountid = ? AND reportedat > NOW() - INTERVAL '30 day' AND orgid = ? AND projectid = ? AND serviceid = ?\n"
        + "                GROUP BY orgid,\n"
        + "                         projectid,\n"
        + "                         serviceid,\n"
        + "                         DATE_TRUNC('minute', reportedat)\n"
        + "            ) instancesPerService\n"
        + "        GROUP BY orgid,projectid,serviceid\n"
        + "    ) percentileInstancesPerServices\n"
        + "ON activeServices.orgIdentifier = percentileInstancesPerServices.orgid\n"
        + "    AND activeServices.projectIdentifier = percentileInstancesPerServices.projectid\n"
        + "    AND activeServices.serviceIdentifier = percentileInstancesPerServices.serviceid\n"
        + "ORDER BY lastDeployed DESC NULLS LAST\n"
        + "LIMIT ?\n"
        + "OFFSET (? * ?)");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testFetchActiveServicesWithServiceIdentifier() throws SQLException {
    Connection dbConnection = mock(Connection.class);
    PreparedStatement fetchStatement = mock(PreparedStatement.class);
    ResultSet resultSet = mock(ResultSet.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(dbConnection);
    when(dbConnection.prepareStatement(any())).thenReturn(fetchStatement);
    when(fetchStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(false);
    ActiveServiceFetchData fetchData = ActiveServiceFetchData.builder()
                                           .accountIdentifier(accountIdentifier)
                                           .orgIdentifier(null)
                                           .projectIdentifier(null)
                                           .serviceIdentifier(serviceIdentifier)
                                           .sort(Sort.by(Sort.Direction.DESC, LAST_DEPLOYED_SERVICE_PROPERTY))
                                           .pageNumber(0)
                                           .pageSize(1)
                                           .build();

    cdLicenseUsageDAL.fetchActiveServices(fetchData);

    ArgumentCaptor<String> queryArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(dbConnection, times(1)).prepareStatement(queryArgumentCaptor.capture());

    String sqlQuery = queryArgumentCaptor.getValue();
    assertThat(sqlQuery).isNotBlank();
    assertThat(sqlQuery).isEqualTo(""
        + "SELECT activeServices.orgIdentifier,\n"
        + "       activeServices.projectIdentifier,\n"
        + "       activeServices.serviceIdentifier AS identifier,\n"
        + "       activeServices.lastDeployedServiceTime AS lastDeployed,\n"
        + "       activeServices.totalCount,\n"
        + "       COALESCE(percentileInstancesPerServices.instanceCount, 0) AS instanceCount\n"
        + "FROM\n"
        + "-- List services deployed in last 30 days from service_infra_info table. 'Group by' is needed for lastDeployedServiceTime calculation\n"
        + "(\n"
        + "    SELECT orgidentifier AS orgIdentifier,\n"
        + "           projectidentifier AS projectIdentifier,\n"
        + "           service_id AS serviceIdentifier,\n"
        + "           MAX(service_startts) as lastDeployedServiceTime,\n"
        + "           COUNT(*) OVER () AS totalCount\n"
        + "    FROM service_infra_info\n"
        + "    WHERE (accountid = ? AND service_startts >= ? AND service_startts <= ? AND service_id = ?)\n"
        + "    GROUP BY orgidentifier, projectidentifier, service_id\n"
        + ") activeServices\n"
        + "    LEFT JOIN\n"
        + "-- List services percentile instances count from ng_instance_stats table\n"
        + "    (\n"
        + "        SELECT PERCENTILE_DISC(?) WITHIN GROUP (ORDER BY instancesPerService.instanceCount) AS instanceCount,\n"
        + "               orgid,\n"
        + "               projectid,\n"
        + "               serviceid\n"
        + "        FROM\n"
        + "            (\n"
        + "                SELECT DATE_TRUNC('minute', reportedat) AS reportedat,\n"
        + "                       orgid,\n"
        + "                       projectid,\n"
        + "                       serviceid,\n"
        + "                       SUM(instancecount) AS instanceCount\n"
        + "                FROM ng_instance_stats\n"
        + "                WHERE accountid = ? AND reportedat > NOW() - INTERVAL '30 day' AND serviceid = ?\n"
        + "                GROUP BY orgid,\n"
        + "                         projectid,\n"
        + "                         serviceid,\n"
        + "                         DATE_TRUNC('minute', reportedat)\n"
        + "            ) instancesPerService\n"
        + "        GROUP BY orgid,projectid,serviceid\n"
        + "    ) percentileInstancesPerServices\n"
        + "ON activeServices.orgIdentifier = percentileInstancesPerServices.orgid\n"
        + "    AND activeServices.projectIdentifier = percentileInstancesPerServices.projectid\n"
        + "    AND activeServices.serviceIdentifier = percentileInstancesPerServices.serviceid\n"
        + "ORDER BY lastDeployed DESC NULLS LAST\n"
        + "LIMIT ?\n"
        + "OFFSET (? * ?)");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testFetchServiceInstancesDateUsage() throws SQLException {
    Connection dbConnection = mock(Connection.class);
    CallableStatement callableStatement = mock(CallableStatement.class);
    ResultSet resultSet = mock(ResultSet.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(dbConnection);
    when(dbConnection.prepareCall(any())).thenReturn(callableStatement);
    doReturn(true).when(callableStatement).execute();
    when(callableStatement.getResultSet()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(false);

    cdLicenseUsageDAL.fetchLicenseDateUsage(LicenseDateUsageFetchData.builder()
                                                .reportType(LicenseDateUsageReportType.DAILY)
                                                .accountIdentifier(accountIdentifier)
                                                .fromDate(LocalDate.of(2023, 1, 1))
                                                .toDate(LocalDate.of(2023, 2, 1))
                                                .licenseType(CDLicenseType.SERVICE_INSTANCES)
                                                .build());

    ArgumentCaptor<String> queryArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(dbConnection, times(1)).prepareCall(queryArgumentCaptor.capture());

    String sqlQuery = queryArgumentCaptor.getValue();
    assertThat(sqlQuery).isNotBlank();
    assertThat(sqlQuery).isEqualTo("{ call get_service_instances_by_date(?,?,?,?,?)}");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testFetchActiveServiceDateUsage() throws SQLException {
    Connection dbConnection = mock(Connection.class);
    CallableStatement callableStatement = mock(CallableStatement.class);
    ResultSet resultSet = mock(ResultSet.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(dbConnection);
    when(dbConnection.prepareCall(any())).thenReturn(callableStatement);
    doReturn(true).when(callableStatement).execute();
    when(callableStatement.getResultSet()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(false);

    cdLicenseUsageDAL.fetchLicenseDateUsage(LicenseDateUsageFetchData.builder()
                                                .reportType(LicenseDateUsageReportType.DAILY)
                                                .accountIdentifier(accountIdentifier)
                                                .fromDate(LocalDate.of(2023, 1, 1))
                                                .toDate(LocalDate.of(2023, 2, 1))
                                                .licenseType(CDLicenseType.SERVICES)
                                                .build());

    ArgumentCaptor<String> queryArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(dbConnection, times(1)).prepareCall(queryArgumentCaptor.capture());

    String sqlQuery = queryArgumentCaptor.getValue();
    assertThat(sqlQuery).isNotBlank();
    assertThat(sqlQuery).isEqualTo("{ call get_active_services_by_date(?,?,?,?,?)}");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testFetchServiceInstancesDateUsageWithInvalidAccount() {
    assertThatThrownBy(()
                           -> cdLicenseUsageDAL.fetchLicenseDateUsage(LicenseDateUsageFetchData.builder()
                                                                          .reportType(LicenseDateUsageReportType.DAILY)
                                                                          .accountIdentifier(null)
                                                                          .fromDate(LocalDate.of(2023, 1, 1))
                                                                          .toDate(LocalDate.of(2023, 2, 1))
                                                                          .build()))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("AccountIdentifier cannot be null or empty for fetching license date usage");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testFetchLicenseUsageWithInvalidLicenseType() {
    assertThatThrownBy(()
                           -> cdLicenseUsageDAL.fetchLicenseDateUsage(LicenseDateUsageFetchData.builder()
                                                                          .reportType(LicenseDateUsageReportType.DAILY)
                                                                          .accountIdentifier(accountIdentifier)
                                                                          .fromDate(LocalDate.of(2023, 1, 1))
                                                                          .toDate(LocalDate.of(2023, 2, 1))
                                                                          .build()))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("CD license type cannot be null for fetching license date usage");
  }
}
