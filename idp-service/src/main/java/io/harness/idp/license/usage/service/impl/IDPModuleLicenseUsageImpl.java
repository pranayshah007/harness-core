/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.service.impl;

import static io.harness.NGDateUtils.YEAR_MONTH_DAY_DATE_PATTERN;
import static io.harness.NGDateUtils.getLocalDateOrThrow;
import static io.harness.idp.common.DateUtils.dateToLocateDate;
import static io.harness.idp.common.DateUtils.getPreviousDay24HourTimeFrame;
import static io.harness.idp.common.DateUtils.localDateToDate;
import static io.harness.idp.common.DateUtils.yesterdayDateInStringAndDateFormat;
import static io.harness.remote.client.CGRestUtils.getResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.client.NgConnectorManagerClient;
import io.harness.idp.events.producers.IdpServiceMiscRedisProducer;
import io.harness.idp.license.usage.dto.ActiveDevelopersTrendCountDTO;
import io.harness.idp.license.usage.dto.IDPLicenseUsageUserCaptureDTO;
import io.harness.idp.license.usage.entities.ActiveDevelopersDailyCountEntity;
import io.harness.idp.license.usage.entities.ActiveDevelopersEntity;
import io.harness.idp.license.usage.mappers.ActiveDevelopersDailyCountEntityMapper;
import io.harness.idp.license.usage.mappers.ActiveDevelopersEntityMapper;
import io.harness.idp.license.usage.repositories.ActiveDevelopersDailyCountRepository;
import io.harness.idp.license.usage.repositories.ActiveDevelopersRepository;
import io.harness.idp.license.usage.service.IDPModuleLicenseUsage;
import io.harness.licensing.usage.params.filter.IDPLicenseDateUsageParams;
import io.harness.licensing.usage.params.filter.LicenseDateUsageReportType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IDPModuleLicenseUsageImpl implements IDPModuleLicenseUsage {
  @Inject IdpServiceMiscRedisProducer idpServiceMiscRedisProducer;
  @Inject NgConnectorManagerClient ngConnectorManagerClient;
  @Inject @Named("internalAccounts") private List<String> internalAccounts;
  @Inject ActiveDevelopersRepository activeDevelopersRepository;
  @Inject ActiveDevelopersDailyCountRepository activeDevelopersDailyCountRepository;

  private static final List<Pattern> URL_PATHS_PATTERN_FOR_LICENSE_USAGE_CAPTURE = List.of(
      Pattern.compile("v1/status-info*"), Pattern.compile("v1/onboarding*"), Pattern.compile("v1/plugins-info*"),
      Pattern.compile("v1/plugin-toggle*"), Pattern.compile("v1/app-config*"), Pattern.compile("v1/plugin/request*"),
      Pattern.compile("v1/merged-plugins-config*"), Pattern.compile("v1/configuration-entities*"),
      Pattern.compile("v1/auth-info*"), Pattern.compile("v1/scorecards*"), Pattern.compile("v1/scores*"),
      Pattern.compile("v1/checks*"), Pattern.compile("v1/data-sources*"), Pattern.compile("v1/layout*"),
      Pattern.compile("v1/backstage-permissions*"), Pattern.compile("v1/connector-info*"),
      Pattern.compile("v1/allow-list*"), Pattern.compile("v1/entity-facets*"),
      Pattern.compile("v1/backstage-env-variables/batch*"));

  @Override
  public boolean checkIfUrlPathCapturesLicenseUsage(String urlPath) {
    for (Pattern urlPattern : URL_PATHS_PATTERN_FOR_LICENSE_USAGE_CAPTURE) {
      if (urlPattern.matcher(urlPath).matches()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void captureLicenseUsageInRedis(IDPLicenseUsageUserCaptureDTO idpLicenseUsageUserCapture) {
    idpServiceMiscRedisProducer.publishIDPLicenseUsageUserCaptureDTOToRedis(
        idpLicenseUsageUserCapture.getAccountIdentifier(), idpLicenseUsageUserCapture.getUserIdentifier(),
        idpLicenseUsageUserCapture.getEmail(), idpLicenseUsageUserCapture.getUserName(),
        idpLicenseUsageUserCapture.getAccessedAt());
  }

  @Override
  public void saveLicenseUsageInDB(IDPLicenseUsageUserCaptureDTO idpLicenseUsageUserCapture) {
    boolean isHarnessSupportUser =
        getResponse(ngConnectorManagerClient.isHarnessSupportUser(idpLicenseUsageUserCapture.getUserIdentifier()));
    if (!isHarnessSupportUser || internalAccounts.contains(idpLicenseUsageUserCapture.getAccountIdentifier())) {
      ActiveDevelopersEntity activeDevelopersEntity = ActiveDevelopersEntityMapper.fromDto(idpLicenseUsageUserCapture);
      Optional<ActiveDevelopersEntity> optionalActiveDevelopersEntity =
          activeDevelopersRepository.findByAccountIdentifierAndUserIdentifier(
              idpLicenseUsageUserCapture.getAccountIdentifier(), idpLicenseUsageUserCapture.getUserIdentifier());
      optionalActiveDevelopersEntity.ifPresent(activeDevelopersEntityExisting -> {
        activeDevelopersEntity.setId(activeDevelopersEntityExisting.getId());
        activeDevelopersEntity.setCreatedAt(activeDevelopersEntityExisting.getCreatedAt());
      });
      activeDevelopersRepository.save(activeDevelopersEntity);
    }
  }

  @Override
  public void licenseUsageDailyCountAggregationPerAccount() {
    Pair<Long, Long> previousDay24HourTimeFrame = getPreviousDay24HourTimeFrame();
    log.info("Fetching data between {} {} for license usage daily count aggregation per account",
        previousDay24HourTimeFrame.getLeft(), previousDay24HourTimeFrame.getRight());
    List<ActiveDevelopersEntity> activeDevelopersEntities = activeDevelopersRepository.findByLastAccessedAtBetween(
        previousDay24HourTimeFrame.getLeft(), previousDay24HourTimeFrame.getRight());
    log.info("Found {} active developers for all accounts between {} {}", activeDevelopersEntities.size(),
        previousDay24HourTimeFrame.getLeft(), previousDay24HourTimeFrame.getRight());
    Map<String, Long> accountsPreviousDayDevelopersCount = activeDevelopersEntities.stream().collect(
        Collectors.groupingBy(ActiveDevelopersEntity::getAccountIdentifier, Collectors.counting()));
    List<ActiveDevelopersDailyCountEntity> activeDevelopersDailyCountEntities =
        prepareActiveDevelopersDailyCountEntitiesForSave(accountsPreviousDayDevelopersCount);
    activeDevelopersDailyCountRepository.saveAll(activeDevelopersDailyCountEntities);
  }

  @Override
  public List<ActiveDevelopersTrendCountDTO> getHistoryTrend(
      String accountIdentifier, IDPLicenseDateUsageParams idpLicenseDateUsageParams) {
    LocalDate fromDate = getLocalDateOrThrow(YEAR_MONTH_DAY_DATE_PATTERN, idpLicenseDateUsageParams.getFromDate());
    LocalDate toDate = getLocalDateOrThrow(YEAR_MONTH_DAY_DATE_PATTERN, idpLicenseDateUsageParams.getToDate());
    List<ActiveDevelopersDailyCountEntity> activeDevelopersDailyCountEntities =
        activeDevelopersDailyCountRepository.findByAccountIdentifierAndDateInDateFormatBetween(
            accountIdentifier, localDateToDate(fromDate), localDateToDate(toDate));
    if (idpLicenseDateUsageParams.getReportType().equals(LicenseDateUsageReportType.MONTHLY)) {
      return monthlyData(activeDevelopersDailyCountEntities);
    } else {
      return dailyData(activeDevelopersDailyCountEntities);
    }
  }

  private List<ActiveDevelopersDailyCountEntity> prepareActiveDevelopersDailyCountEntitiesForSave(
      Map<String, Long> accountsPreviousDayDevelopersCount) {
    Pair<String, Date> yesterdayDateInStringAndDateFormat = yesterdayDateInStringAndDateFormat();
    List<ActiveDevelopersDailyCountEntity> activeDevelopersDailyCountEntities = new ArrayList<>();
    accountsPreviousDayDevelopersCount.forEach((k, v) -> {
      ActiveDevelopersDailyCountEntity activeDevelopersDailyCountEntity =
          ActiveDevelopersDailyCountEntity.builder()
              .accountIdentifier(k)
              .dateInStringFormat(yesterdayDateInStringAndDateFormat.getLeft())
              .dateInDateFormat(yesterdayDateInStringAndDateFormat.getRight())
              .count(v)
              .build();
      activeDevelopersDailyCountEntities.add(activeDevelopersDailyCountEntity);
    });
    return activeDevelopersDailyCountEntities;
  }

  private List<ActiveDevelopersTrendCountDTO> monthlyData(
      List<ActiveDevelopersDailyCountEntity> activeDevelopersDailyCountEntities) {
    List<ActiveDevelopersTrendCountDTO> activeDevelopersTrendCountDTOS = new ArrayList<>();

    Map<String, Integer> monthlyTrend = new HashMap<>();

    activeDevelopersDailyCountEntities.forEach(activeDevelopersDailyCountEntity -> {
      LocalDate localDate = dateToLocateDate(activeDevelopersDailyCountEntity.getDateInDateFormat());
      int year = localDate.getYear();
      int month = localDate.getMonthValue();

      monthlyTrend.put(year + "-" + month, monthlyTrend.getOrDefault(year + "-" + month, 0) + 1);
    });

    monthlyTrend.forEach(
        (k, v) -> activeDevelopersTrendCountDTOS.add(ActiveDevelopersTrendCountDTO.builder().date(k).count(v).build()));

    return activeDevelopersTrendCountDTOS;
  }

  private List<ActiveDevelopersTrendCountDTO> dailyData(
      List<ActiveDevelopersDailyCountEntity> activeDevelopersDailyCountEntities) {
    List<ActiveDevelopersTrendCountDTO> activeDevelopersTrendCountDTOS = new ArrayList<>();

    activeDevelopersDailyCountEntities.forEach(activeDevelopersDailyCountEntity
        -> activeDevelopersTrendCountDTOS.add(
            ActiveDevelopersDailyCountEntityMapper.toDto(activeDevelopersDailyCountEntity)));

    return activeDevelopersTrendCountDTOS;
  }
}
