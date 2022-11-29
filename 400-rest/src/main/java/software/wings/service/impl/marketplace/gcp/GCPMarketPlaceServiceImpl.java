/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.marketplace.gcp;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.marketplace.gcp.GcpMarketPlaceConstants;
import io.harness.marketplace.gcp.procurement.GcpProcurementService;
import io.harness.marketplace.gcp.servicecontrol.GCPServiceControlService;

import software.wings.beans.marketplace.gcp.GCPMarketplaceCustomer;
import software.wings.beans.marketplace.gcp.GCPMarketplaceCustomer.GCPMarketplaceCustomerKeys;
import software.wings.beans.marketplace.gcp.GCPUsageReport;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.instance.stats.InstanceStatService;
import software.wings.service.intfc.marketplace.gcp.GCPMarketPlaceService;
import software.wings.service.intfc.marketplace.gcp.GCPUsageReportService;

import com.google.cloudcommerceprocurement.v1.model.Entitlement;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@Singleton
public class GCPMarketPlaceServiceImpl implements GCPMarketPlaceService {
  public static final double DEFAULT_PERCENTILE = 95.0D;
  private static final ChronoUnit SYNC_CHRONO_UNIT = ChronoUnit.HOURS;
  private static final long SYNC_INTERVAL = TimeUnit.HOURS.toHours(1);

  @Inject private InstanceStatService statService;
  @Inject private GCPUsageReportService gcpUsageReportService;
  @Inject private GCPServiceControlService gcpServiceControlService;
  @Inject private GcpProcurementService gcpProcurementService;
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void createUsageReport(String accountId) {
    Optional<Entitlement> entitlementMaybe = fetchActiveEntitlement(accountId);
    if (entitlementMaybe.isPresent()) {
      Entitlement entitlement = entitlementMaybe.get();
      Instant usageReportStartTime = this.usageReportStartTime(accountId, entitlement);
      Instant usageReportEndTime = this.usageReportEndTime(accountId);

      if (null != usageReportStartTime && null != usageReportEndTime) {
        log.info("GCP_MKT_PLACE Usage report time {} {}", usageReportStartTime, usageReportEndTime);
        createGCPUsageReport(accountId, entitlement, usageReportStartTime, usageReportEndTime);
      } else {
        log.warn("GCP_MKT_PLACE start or end time is null : {} {}", usageReportStartTime, usageReportEndTime);
      }

    } else {
      log.error("GCP_MKT_PLACE No active entitlement present for account {} ", accountId);
    }
  }

  private Instant usageReportStartTime(String accountId, Entitlement entitlement) {
    Instant usageReportStartTime = this.gcpUsageReportService.fetchLastGCPUsageReportTime(accountId);
    Instant entitlementUpdatedTime = fetchEntitlementUpdatedTime(entitlement);

    if (null == usageReportStartTime || entitlementUpdatedTime.isAfter(usageReportStartTime)) {
      log.info("GCP_MKT_PLACE Entitlement update time {} and usage report time {}", entitlementUpdatedTime,
          usageReportStartTime);
      return entitlementUpdatedTime;
    }
    return usageReportStartTime;
  }

  private Instant fetchEntitlementUpdatedTime(Entitlement entitlement) {
    try {
      return Instant.parse(entitlement.getUpdateTime());
    } catch (DateTimeParseException ex) {
      log.error("GCP_MKT_PLACE DateTimeParseException ", ex);
    }
    return null;
  }

  private Instant usageReportEndTime(String accountId) {
    return this.statService.getLastSnapshotTime(accountId);
  }

  private void createGCPUsageReport(String accountId, Entitlement entitlement, Instant startTime, Instant endTime) {
    Preconditions.checkArgument(endTime.isAfter(startTime), "'endTime' timestamp should be after 'startTime'");

    log.info("GCP_MKT_PLACE start time {} and end time {} for execution", startTime, endTime);
    GCPUsageReportTimeProvider gcpUsageReportTimeProvider =
        new GCPUsageReportTimeProvider(startTime, endTime, SYNC_INTERVAL, SYNC_CHRONO_UNIT);

    Instant reportStartTime = startTime;
    while (gcpUsageReportTimeProvider.hasNext()) {
      Instant reportEndTime = gcpUsageReportTimeProvider.next();
      if (null == reportEndTime) {
        throw new IllegalStateException(
            "reportEndTime is null even though hasNext() returned true. Shouldn't be possible");
      }

      long instanceUsage = getGCPInstanceUsage(accountId, reportStartTime, reportEndTime);
      String consumerId = entitlement.getUsageReportingId();
      String entitlementName = entitlement.getName();
      String operationId = String.format("%s-%s", consumerId, reportStartTime.toEpochMilli());
      GCPUsageReport gcpUsageReport = new GCPUsageReport(
          accountId, consumerId, operationId, entitlementName, reportStartTime, reportEndTime, instanceUsage);

      log.info("GCP_MKT_PLACE Gcp usage report data {} ", gcpUsageReport.toString());

      if (this.gcpServiceControlService.reportUsageDataToGCP(gcpUsageReport)) {
        this.gcpUsageReportService.create(gcpUsageReport);
      } else {
        log.error("GCP_MKT_PLACE Exception while sending data to GCP, accountId {} start time {} end time {} ",
            accountId, reportStartTime, reportEndTime);
        break;
      }
      reportStartTime = reportEndTime;
    }
  }

  private Optional<Entitlement> fetchActiveEntitlement(String accountId) {
    Optional<GCPMarketplaceCustomer> gcpMarketplaceCustomer =
        Optional.ofNullable(wingsPersistence.createQuery(GCPMarketplaceCustomer.class)
                                .filter(GCPMarketplaceCustomerKeys.harnessAccountId, accountId)
                                .get());
    if (!gcpMarketplaceCustomer.isPresent()) {
      log.error("GCP_MKT_PLACE Error marketplace entity not present {} ", accountId);
      return Optional.empty();
    } else {
      GCPMarketplaceCustomer marketPlace = gcpMarketplaceCustomer.get();
      List<Entitlement> entitlements =
          this.gcpProcurementService.listEntitlementsForGcpAccountId(marketPlace.getGcpAccountId());
      List<Entitlement> activeEntitlement =
          entitlements.stream()
              .filter(entitlement -> entitlement.getState().equals(GcpMarketPlaceConstants.ENTITLEMENT_ACTIVATED))
              .collect(Collectors.toList());
      if (activeEntitlement.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(activeEntitlement.get(0));
    }
  }

  private long getGCPInstanceUsage(String accountId, Instant startTime, Instant endTime) {
    double percentile = this.statService.percentile(accountId, startTime, endTime, DEFAULT_PERCENTILE);
    if (percentile < 0) {
      log.info("GCP_MKT_PLACE percentile value {}", percentile);
      percentile = 0;
    }
    log.info("GCP_MKT_PLACE Usage start time {} end time {} instance count {} ", startTime, endTime, percentile);
    long instanceTime = (endTime.toEpochMilli() - startTime.toEpochMilli()) / 60000;
    return (long) (percentile * instanceTime);
  }

  @Override
  public void deleteByAccountId(String accountId) {
    wingsPersistence.delete(wingsPersistence.createQuery(GCPMarketplaceCustomer.class)
                                .filter(GCPMarketplaceCustomerKeys.harnessAccountId, accountId));
  }
}
