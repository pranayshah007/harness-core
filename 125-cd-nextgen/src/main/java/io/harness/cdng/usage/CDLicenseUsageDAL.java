/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage;

import static io.harness.cd.CDLicenseType.SERVICES;
import static io.harness.cd.CDLicenseType.SERVICE_INSTANCES;
import static io.harness.cdng.usage.pojos.ActiveService.ActiveServiceField.name;
import static io.harness.cdng.usage.pojos.ActiveService.ActiveServiceField.orgName;
import static io.harness.cdng.usage.pojos.ActiveService.ActiveServiceField.projectName;
import static io.harness.cdng.usage.pojos.ActiveServiceBase.ActiveServiceBaseField.identifier;
import static io.harness.cdng.usage.pojos.ActiveServiceBase.ActiveServiceBaseField.instanceCount;
import static io.harness.cdng.usage.pojos.ActiveServiceBase.ActiveServiceBaseField.lastDeployed;
import static io.harness.cdng.usage.pojos.ActiveServiceBase.ActiveServiceBaseField.orgIdentifier;
import static io.harness.cdng.usage.pojos.ActiveServiceBase.ActiveServiceBaseField.projectIdentifier;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.LAST_DEPLOYED_SERVICE_PROPERTY;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.LICENSES_CONSUMED_QUERY_PROPERTY;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.LICENSES_CONSUMED_SORT_PROPERTY;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.SERVICE_INSTANCES_QUERY_PROPERTY;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.SERVICE_INSTANCES_SORT_PROPERTY;

import static java.lang.String.format;
import static java.sql.Date.valueOf;
import static org.apache.commons.lang3.StringUtils.SPACE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cd.CDLicenseType;
import io.harness.cdlicense.exception.CgLicenseUsageException;
import io.harness.cdng.usage.impl.AggregateServiceUsageInfo;
import io.harness.cdng.usage.pojos.ActiveService;
import io.harness.cdng.usage.pojos.ActiveServiceBase;
import io.harness.cdng.usage.pojos.ActiveServiceFetchData;
import io.harness.cdng.usage.pojos.ActiveServiceResponse;
import io.harness.cdng.usage.pojos.LicenseDailyUsage;
import io.harness.cdng.usage.pojos.LicenseDateUsageFetchData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.licensing.usage.params.filter.LicenseDateUsageReportType;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Sort;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
@Singleton
public class CDLicenseUsageDAL {
  private static final double INSTANCE_COUNT_PERCENTILE_DISC = 0.95;
  private static final int MAX_RETRY = 3;
  private static final String MAX_RETRY_MSG = format("%s retries", MAX_RETRY);
  private static final String QUERY_FETCH_SERVICE_INSTANCE_USAGE = ""
      + "select percentile_disc(?) within group (order by instanceCountsPerReportedAt.instancecount) as percentileInstanceCount\n"
      + "from (\n"
      + "    select  date_trunc('minute', reportedat) as reportedat, accountid, sum(instancecount) as instancecount\n"
      + "    from \n"
      + "        ng_instance_stats \n"
      + "    where accountid = ?\n"
      + "        and reportedat > now() - INTERVAL '30 day' \n"
      + "    group by accountid, date_trunc('minute', reportedat)\n"
      + ") as instanceCountsPerReportedAt";
  private static final String QUERY_FETCH_INSTANCES_PER_SERVICE = ""
      + "select percentile_disc(?) within group (order by instancesPerServicePerReportedat.instancecount) as instanceCount,\n"
      + "    orgid, projectid, serviceid\n"
      + "from (\n"
      + "    select date_trunc('minute', reportedat) as reportedat, orgid, projectid, serviceid, sum(instancecount) as instancecount\n"
      + "    from \n"
      + "        ng_instance_stats \n"
      + "    where accountid = ?\n"
      + "        and reportedat > now() - INTERVAL '30 day' \n"
      + "    group by orgid, projectid, serviceid, date_trunc('minute', reportedat)\n"
      + "    order by reportedat desc\n"
      + ") instancesPerServicePerReportedat\n"
      + "group by orgid, projectid, serviceid";
  private static final String FETCH_ACTIVE_SERVICES_WITH_INSTANCES_COUNT_QUERY = ""
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
      + "    WHERE (accountid = ? AND service_startts >= ? AND service_startts <= ? :filterOnServiceInfraInfo)\n"
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
      + "                WHERE accountid = ? AND reportedat > NOW() - INTERVAL '30 day' :filterOnNgInstanceStats\n"
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
      + "ORDER BY :sortCriteria\n"
      + "LIMIT ?\n"
      + "OFFSET (? * ?)";
  private static final String FETCH_ACTIVE_SERVICES_NAME_ORG_AND_PROJECT_NAME_QUERY = ""
      + "SELECT DISTINCT\n"
      + "    t.orgIdentifier, t.projectIdentifier, t.serviceIdentifier AS identifier, t.lastDeployed, t.instanceCount,\n"
      + "    COALESCE(organizations.name, 'Deleted') AS orgName,\n"
      + "    COALESCE(projects.name, 'Deleted') AS projectName,\n"
      + "    COALESCE(services.name, 'Deleted') AS name\n"
      + "FROM \n"
      + "    (\n"
      + "        VALUES :constantTable\n"
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
      + "ORDER BY :sortCriteria";

  @Inject TimeScaleDBService timeScaleDBService;

  public long fetchServiceInstancesOver30Days(String accountId) {
    if (isEmpty(accountId)) {
      log.error("AccountId: [{}] cannot be empty for finding service instances", accountId);
      return 0L;
    }

    int retry = 0;
    boolean successfulOperation = false;
    long percentileInstanceCount = 0L;
    while (!successfulOperation && retry <= MAX_RETRY) {
      try (Connection dbConnection = timeScaleDBService.getDBConnection();
           PreparedStatement fetchStatement = dbConnection.prepareStatement(QUERY_FETCH_SERVICE_INSTANCE_USAGE)) {
        fetchStatement.setDouble(1, INSTANCE_COUNT_PERCENTILE_DISC);
        fetchStatement.setString(2, accountId);

        ResultSet resultSet = fetchStatement.executeQuery();
        if (Objects.nonNull(resultSet) && resultSet.next()) {
          percentileInstanceCount = resultSet.getLong(1);
        } else {
          log.warn("No service instances count found for accountId: [{}]", accountId);
        }

        successfulOperation = true;
      } catch (SQLException exception) {
        if (retry >= MAX_RETRY) {
          String errorLog = "MAX RETRY FAILURE: Failed to fetch service instance usage after " + MAX_RETRY_MSG;
          throw new CgLicenseUsageException(errorLog, exception);
        }
        log.error(
            "Failed to fetch service instance usage for accountId : [{}] , retry : [{}]", accountId, retry, exception);
        retry++;
      }
    }

    return percentileInstanceCount;
  }

  public List<AggregateServiceUsageInfo> fetchInstancesPerServiceOver30Days(String accountId) {
    if (isEmpty(accountId)) {
      log.error("AccountId: [{}] cannot be empty for finding service instances", accountId);
      return Collections.emptyList();
    }

    int retry = 0;
    boolean successfulOperation = false;
    List<AggregateServiceUsageInfo> instanceCountPerService = new ArrayList<>();
    while (!successfulOperation && retry <= MAX_RETRY) {
      try (Connection dbConnection = timeScaleDBService.getDBConnection();
           PreparedStatement fetchStatement = dbConnection.prepareStatement(QUERY_FETCH_INSTANCES_PER_SERVICE)) {
        fetchStatement.setDouble(1, INSTANCE_COUNT_PERCENTILE_DISC);
        fetchStatement.setString(2, accountId);

        ResultSet resultSet = fetchStatement.executeQuery();
        instanceCountPerService = processResultSet(resultSet);
        successfulOperation = true;
      } catch (SQLException exception) {
        if (retry >= MAX_RETRY) {
          String errorLog = "MAX RETRY FAILURE: Failed to fetch service instance usage after " + MAX_RETRY_MSG;
          throw new CgLicenseUsageException(errorLog, exception);
        }
        log.error(
            "Failed to fetch service instance usage for accountId : [{}] , retry : [{}]", accountId, retry, exception);
        retry++;
      }
    }

    return instanceCountPerService;
  }

  public ActiveServiceResponse<List<ActiveServiceBase>> fetchActiveServices(ActiveServiceFetchData fetchData) {
    ActiveServiceResponse<List<ActiveServiceBase>> activeServiceResponse = getEmptyActiveServiceResponse();
    String accountIdentifier = fetchData.getAccountIdentifier();
    if (isEmpty(accountIdentifier)) {
      throw new InvalidArgumentsException("AccountIdentifier cannot be null or empty for fetching active services");
    }

    final String fetchActiveServicesFinalQuery =
        FETCH_ACTIVE_SERVICES_WITH_INSTANCES_COUNT_QUERY
            .replace(":filterOnServiceInfraInfo",
                buildFilterOnServiceInfraInfoTable(
                    fetchData.getOrgIdentifier(), fetchData.getProjectIdentifier(), fetchData.getServiceIdentifier()))
            .replace(":filterOnNgInstanceStats",
                buildFilterOnNGInstanceStatsTable(
                    fetchData.getOrgIdentifier(), fetchData.getProjectIdentifier(), fetchData.getServiceIdentifier()))
            .replace(":sortCriteria", buildSortCriteria(fetchData.getSort()));

    int retry = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && retry <= MAX_RETRY) {
      try (Connection dbConnection = timeScaleDBService.getDBConnection();
           PreparedStatement fetchStatement = dbConnection.prepareStatement(fetchActiveServicesFinalQuery)) {
        int cursor = 0;
        fetchStatement.setString(++cursor, accountIdentifier);
        fetchStatement.setLong(++cursor, fetchData.getStartTSInMs());
        fetchStatement.setLong(++cursor, fetchData.getEndTSInMs());
        cursor = setStatementByOrgProjectServiceIdentifiers(fetchData, fetchStatement, cursor);
        fetchStatement.setDouble(++cursor, INSTANCE_COUNT_PERCENTILE_DISC);
        fetchStatement.setString(++cursor, accountIdentifier);
        cursor = setStatementByOrgProjectServiceIdentifiers(fetchData, fetchStatement, cursor);
        fetchStatement.setInt(++cursor, fetchData.getPageSize());
        fetchStatement.setInt(++cursor, fetchData.getPageNumber());
        fetchStatement.setInt(++cursor, fetchData.getPageSize());

        ResultSet resultSet = fetchStatement.executeQuery();
        activeServiceResponse = processActiveServiceBaseResultSet(resultSet);
        successfulOperation = true;
      } catch (SQLException exception) {
        if (retry >= MAX_RETRY) {
          String errorLog = "MAX RETRY FAILURE: Failed to fetch active services after " + MAX_RETRY_MSG;
          throw new CgLicenseUsageException(errorLog, exception);
        }
        log.error("Failed to fetch active services, accountIdentifier : [{}] , retry : [{}]", accountIdentifier, retry,
            exception);
        retry++;
      }
    }

    return activeServiceResponse;
  }

  public List<ActiveService> fetchActiveServicesNameOrgAndProjectName(
      final String accountIdentifier, List<ActiveServiceBase> activeServiceBaseItems, Sort sort) {
    if (isEmpty(accountIdentifier)) {
      throw new InvalidArgumentsException(
          "AccountIdentifier cannot be null or empty for fetching active services names, org and project names");
    }

    if (isEmpty(activeServiceBaseItems)) {
      return Collections.emptyList();
    }

    final String fetchActiveServicesNameOrgAndProjectNameFinalQuery =
        FETCH_ACTIVE_SERVICES_NAME_ORG_AND_PROJECT_NAME_QUERY
            .replace(":constantTable", buildConstantTable(activeServiceBaseItems))
            .replace(":sortCriteria", buildSortCriteria(sort));
    int retry = 0;
    boolean successfulOperation = false;
    List<ActiveService> activeServices = new ArrayList<>();
    while (!successfulOperation && retry <= MAX_RETRY) {
      try (Connection dbConnection = timeScaleDBService.getDBConnection();
           PreparedStatement fetchStatement =
               dbConnection.prepareStatement(fetchActiveServicesNameOrgAndProjectNameFinalQuery)) {
        fetchStatement.setString(1, accountIdentifier);
        fetchStatement.setString(2, accountIdentifier);
        fetchStatement.setString(3, accountIdentifier);

        ResultSet resultSet = fetchStatement.executeQuery();
        activeServices = processUpdateActiveServiceResultSet(resultSet);
        successfulOperation = true;
      } catch (SQLException exception) {
        if (retry >= MAX_RETRY) {
          String errorLog =
              "MAX RETRY FAILURE: Failed to fetch active services names, org and project names after " + MAX_RETRY_MSG;
          throw new CgLicenseUsageException(errorLog, exception);
        }
        log.error(
            "Failed to fetch active services names, org and project names, accountIdentifier : [{}] , retry : [{}]",
            accountIdentifier, retry, exception);
        retry++;
      }
    }

    return activeServices;
  }

  /**
   * Fetch license usage by dates.
   *
   * @param licenseUsageFetchDate license usage fetch data needed for creating request to DB
   * @return license usage per dates
   */
  public List<LicenseDailyUsage> fetchLicenseDateUsage(LicenseDateUsageFetchData licenseUsageFetchDate) {
    List<LicenseDailyUsage> licenseUsage = new LinkedList<>();
    String accountIdentifier = licenseUsageFetchDate.getAccountIdentifier();
    CDLicenseType licenseType = licenseUsageFetchDate.getLicenseType();
    if (isEmpty(accountIdentifier)) {
      throw new InvalidArgumentsException("AccountIdentifier cannot be null or empty for fetching license date usage");
    }
    if (licenseType == null) {
      throw new InvalidArgumentsException("CD license type cannot be null for fetching license date usage");
    }

    int retry = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && retry <= MAX_RETRY) {
      try (Connection dbConnection = timeScaleDBService.getDBConnection();
           CallableStatement callableStatement = getLicenseDataUsageCallableStatement(licenseType, dbConnection)) {
        callableStatement.setString(1, accountIdentifier);
        callableStatement.setDate(2, valueOf(licenseUsageFetchDate.getFromDate()));
        callableStatement.setDate(3, valueOf(licenseUsageFetchDate.getToDate()));
        callableStatement.setBoolean(4, isMonthlyLicenseUsageReportType(licenseUsageFetchDate));
        callableStatement.setBoolean(5, false);

        callableStatement.execute();
        ResultSet results = callableStatement.getResultSet();
        while (results.next()) {
          Date usageDate = results.getDate(1);
          int licenseCount = results.getInt(2);
          licenseUsage.add(LicenseDailyUsage.builder()
                               .accountId(accountIdentifier)
                               .reportedDay(usageDate.toLocalDate())
                               .licenseCount(licenseCount)
                               .build());
        }

        successfulOperation = true;
      } catch (SQLException exception) {
        if (retry >= MAX_RETRY) {
          String errorLog = "MAX RETRY FAILURE: Failed to fetch license date usage after " + MAX_RETRY_MSG;
          throw new CgLicenseUsageException(errorLog, exception);
        }
        log.error("Failed to fetch license date usage, accountIdentifier : [{}] , retry : [{}]", accountIdentifier,
            retry, exception);
        retry++;
      }
    }

    return licenseUsage;
  }

  private List<AggregateServiceUsageInfo> processResultSet(ResultSet resultSet) throws SQLException {
    if (Objects.isNull(resultSet)) {
      return new ArrayList<>();
    }

    List<AggregateServiceUsageInfo> instanceCountPerService = new ArrayList<>();
    while (resultSet.next()) {
      long instanceCount = resultSet.getLong(1);
      String orgId = resultSet.getString(2);
      String projectId = resultSet.getString(3);
      String serviceId = resultSet.getString(4);
      instanceCountPerService.add(new AggregateServiceUsageInfo(orgId, projectId, serviceId, instanceCount));
    }

    return instanceCountPerService;
  }

  @VisibleForTesting
  String buildFilterOnServiceInfraInfoTable(String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    StringJoiner filterOnServiceInfraInfoTable = new StringJoiner(SPACE);

    if (isNotEmpty(orgIdentifier)) {
      filterOnServiceInfraInfoTable.add("AND orgidentifier = ?");
    }
    if (isNotEmpty(projectIdentifier)) {
      filterOnServiceInfraInfoTable.add("AND projectidentifier = ?");
    }
    if (isNotEmpty(serviceIdentifier)) {
      filterOnServiceInfraInfoTable.add("AND service_id = ?");
    }

    return filterOnServiceInfraInfoTable.toString();
  }

  @VisibleForTesting
  String buildFilterOnNGInstanceStatsTable(String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    StringJoiner filterOnNGInstanceStatsTable = new StringJoiner(SPACE);

    if (isNotEmpty(orgIdentifier)) {
      filterOnNGInstanceStatsTable.add("AND orgid = ?");
    }
    if (isNotEmpty(projectIdentifier)) {
      filterOnNGInstanceStatsTable.add("AND projectid = ?");
    }
    if (isNotEmpty(serviceIdentifier)) {
      filterOnNGInstanceStatsTable.add("AND serviceid = ?");
    }

    return filterOnNGInstanceStatsTable.toString();
  }

  @VisibleForTesting
  @NotNull
  String buildSortCriteria(Sort sort) {
    String defaultSortCriteria = format("%s DESC NULLS LAST", SERVICE_INSTANCES_SORT_PROPERTY);

    Sort.Order serviceInstances = sort.getOrderFor(SERVICE_INSTANCES_QUERY_PROPERTY);
    if (serviceInstances != null) {
      return serviceInstances.isAscending() ? format("%s ASC", SERVICE_INSTANCES_SORT_PROPERTY) : defaultSortCriteria;
    }

    Sort.Order licensesConsumed = sort.getOrderFor(LICENSES_CONSUMED_QUERY_PROPERTY);
    if (licensesConsumed != null) {
      return licensesConsumed.isAscending() ? format("%s ASC", LICENSES_CONSUMED_SORT_PROPERTY) : defaultSortCriteria;
    }

    Sort.Order lastDeployed = sort.getOrderFor(LAST_DEPLOYED_SERVICE_PROPERTY);
    if (lastDeployed != null) {
      return lastDeployed.isAscending() ? format("%s ASC", LAST_DEPLOYED_SERVICE_PROPERTY)
                                        : format("%s DESC NULLS LAST", LAST_DEPLOYED_SERVICE_PROPERTY);
    }

    return defaultSortCriteria;
  }

  private int setStatementByOrgProjectServiceIdentifiers(
      ActiveServiceFetchData fetchData, PreparedStatement fetchStatement, int cursor) throws SQLException {
    if (isNotEmpty(fetchData.getOrgIdentifier())) {
      fetchStatement.setString(++cursor, fetchData.getOrgIdentifier());
    }
    if (isNotEmpty(fetchData.getProjectIdentifier())) {
      fetchStatement.setString(++cursor, fetchData.getProjectIdentifier());
    }
    if (isNotEmpty(fetchData.getServiceIdentifier())) {
      fetchStatement.setString(++cursor, fetchData.getServiceIdentifier());
    }
    return cursor;
  }

  private ActiveServiceResponse<List<ActiveServiceBase>> processActiveServiceBaseResultSet(ResultSet resultSet)
      throws SQLException {
    ActiveServiceResponse<List<ActiveServiceBase>> activeServiceResponse = getEmptyActiveServiceResponse();
    if (Objects.isNull(resultSet)) {
      return activeServiceResponse;
    }

    List<ActiveServiceBase> activeServiceBaseList = new ArrayList<>();
    boolean isCountSet = false;
    while (resultSet.next()) {
      activeServiceBaseList.add(ActiveServiceBase.builder()
                                    .orgIdentifier(resultSet.getString(orgIdentifier))
                                    .projectIdentifier(resultSet.getString(projectIdentifier))
                                    .identifier(resultSet.getString(identifier))
                                    .lastDeployed(resultSet.getLong(lastDeployed))
                                    .instanceCount(resultSet.getLong(instanceCount))
                                    .build());
      if (!isCountSet) {
        activeServiceResponse.setTotalCountOfItems(resultSet.getLong("totalCount"));
        isCountSet = true;
      }
    }
    activeServiceResponse.setActiveServiceItems(activeServiceBaseList);

    return activeServiceResponse;
  }

  @NotNull
  private ActiveServiceResponse<List<ActiveServiceBase>> getEmptyActiveServiceResponse() {
    ActiveServiceResponse<List<ActiveServiceBase>> activeServiceResponse = new ActiveServiceResponse<>();
    activeServiceResponse.setTotalCountOfItems(0);
    activeServiceResponse.setActiveServiceItems(Collections.emptyList());
    return activeServiceResponse;
  }

  private String buildConstantTable(List<ActiveServiceBase> activeServiceBaseItems) {
    return Joiner.on(',').join(Lists.transform(activeServiceBaseItems, ActiveServiceBase::toSQlRow));
  }

  private List<ActiveService> processUpdateActiveServiceResultSet(ResultSet resultSet) throws SQLException {
    if (Objects.isNull(resultSet)) {
      return Collections.emptyList();
    }

    List<ActiveService> activeServices = new ArrayList<>();
    while (resultSet.next()) {
      activeServices.add(ActiveService.builder()
                             .orgIdentifier(resultSet.getString(orgIdentifier))
                             .projectIdentifier(resultSet.getString(projectIdentifier))
                             .identifier(resultSet.getString(identifier))
                             .lastDeployed(resultSet.getLong(lastDeployed))
                             .instanceCount(resultSet.getLong(instanceCount))
                             .orgName(resultSet.getString(orgName))
                             .projectName(resultSet.getString(projectName))
                             .name(resultSet.getString(name))
                             .build());
    }

    return activeServices;
  }

  private CallableStatement getLicenseDataUsageCallableStatement(CDLicenseType licenseType, Connection dbConnection)
      throws SQLException {
    if (licenseType != SERVICE_INSTANCES && licenseType != SERVICES) {
      throw new InvalidArgumentsException("Not supported CD license type for fetching license date usage");
    }
    return SERVICE_INSTANCES == licenseType
        ? dbConnection.prepareCall("{ call get_service_instances_by_date(?,?,?,?,?)}")
        : dbConnection.prepareCall("{ call get_active_services_by_date(?,?,?,?,?)}");
  }

  private boolean isMonthlyLicenseUsageReportType(LicenseDateUsageFetchData fetchData) {
    return LicenseDateUsageReportType.MONTHLY == fetchData.getReportType();
  }
}
