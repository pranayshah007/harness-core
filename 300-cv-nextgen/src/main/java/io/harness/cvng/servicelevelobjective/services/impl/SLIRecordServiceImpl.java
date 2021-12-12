package io.harness.cvng.servicelevelobjective.services.impl;

import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget.Point;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget.SLOGraphData;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordKeys;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.persistence.HPersistence;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.mongodb.morphia.query.Sort;

public class SLIRecordServiceImpl implements SLIRecordService {
  @VisibleForTesting static int MAX_NUMBER_OF_POINTS = 2000;
  @Inject private HPersistence hPersistence;

  @Override
  public void create(List<SLIRecordParam> sliRecordParamList, String sliId, String verificationTaskId) {
    List<SLIRecord> sliRecordList = new ArrayList<>();
    SLIRecord lastSLIRecord = getLastSLIRecord(sliId, sliRecordParamList.get(0).getTimeStamp());
    long runningGoodCount = 0L;
    long runningBadCount = 0L;
    if (Objects.nonNull(lastSLIRecord)) {
      runningGoodCount = lastSLIRecord.getRunningGoodCount();
      runningBadCount = lastSLIRecord.getRunningBadCount();
    }
    for (SLIRecordParam sliRecordParam : sliRecordParamList) {
      if (SLIState.GOOD.equals(sliRecordParam.getSliState())) {
        runningGoodCount++;
      } else if (SLIState.BAD.equals(sliRecordParam.getSliState())) {
        runningBadCount++;
      }
      SLIRecord sliRecord = SLIRecord.builder()
                                .runningBadCount(runningBadCount)
                                .runningGoodCount(runningGoodCount)
                                .sliId(sliId)
                                .verificationTaskId(verificationTaskId)
                                .timestamp(sliRecordParam.getTimeStamp())
                                .sliState(sliRecordParam.getSliState())
                                .build();
      sliRecordList.add(sliRecord);
    }
    hPersistence.save(sliRecordList);
  }
  @Override
  public SLOGraphData getGraphData(String sliId, Instant startTime, Instant endTime, int totalErrorBudgetMinutes,
      SLIMissingDataType sliMissingDataType) {
    List<SLIRecord> sliRecords = sliRecords(sliId, startTime, endTime);
    List<Point> sliTread = new ArrayList<>();
    List<Point> errorBudgetBurndown = new ArrayList<>();
    double errorBudgetRemainingPercentage = 100;
    int errorBudgetRemaining = totalErrorBudgetMinutes;
    if (!sliRecords.isEmpty()) {
      long beginningMinute = sliRecords.get(0).getEpochMinute();
      for (int i = 0; i < sliRecords.size(); i++) {
        long goodCountFromStart = sliRecords.get(i).getRunningGoodCount() - sliRecords.get(0).getRunningGoodCount();
        long badCountFromStart = sliRecords.get(i).getRunningBadCount() - sliRecords.get(0).getRunningBadCount();
        long minutesFromStart = sliRecords.get(i).getEpochMinute() - beginningMinute + 1;
        double percentageSLIValue =
            sliMissingDataType.calculateSLIValue(goodCountFromStart, badCountFromStart, minutesFromStart);
        sliTread.add(Point.builder()
                         .timestamp(sliRecords.get(i).getTimestamp().toEpochMilli())
                         .value(percentageSLIValue)
                         .build());
        errorBudgetBurndown.add(
            Point.builder()
                .timestamp(sliRecords.get(i).getTimestamp().toEpochMilli())
                .value(((totalErrorBudgetMinutes - badCountFromStart) * 100.0) / totalErrorBudgetMinutes)
                .build());
      }
      errorBudgetRemainingPercentage = errorBudgetBurndown.get(errorBudgetBurndown.size() - 1).getValue();
      errorBudgetRemaining = totalErrorBudgetMinutes
          - ((int) (sliRecords.get(sliRecords.size() - 1).getRunningBadCount()
              - sliRecords.get(0).getRunningBadCount()));
    }
    return SLOGraphData.builder()
        .errorBudgetBurndown(errorBudgetBurndown)
        .errorBudgetRemaining(errorBudgetRemaining)
        .sloPerformanceTrend(sliTread)
        .errorBudgetRemainingPercentage(errorBudgetRemainingPercentage)
        .build();
  }

  private List<SLIRecord> sliRecords(String sliId, Instant startTime, Instant endTime) {
    List<Instant> minutes = new ArrayList<>();
    long totalMinutes = Duration.between(startTime, endTime).toMinutes();
    long diff = totalMinutes / MAX_NUMBER_OF_POINTS;
    if (diff == 0) {
      diff = 1L;
    }
    // long reminder = totalMinutes % maxNumberOfPoints;
    minutes.add(startTime);
    Duration diffDuration = Duration.ofMinutes(diff);
    for (Instant current = startTime.plus(Duration.ofMinutes(diff)); current.isBefore(endTime);
         current = current.plus(diffDuration)) {
      minutes.add(current);
    }
    minutes.add(endTime.minus(Duration.ofMinutes(1))); // always include start and end minute.
    return hPersistence.createQuery(SLIRecord.class)
        .filter(SLIRecordKeys.sliId, sliId)
        .field(SLIRecordKeys.timestamp)
        .in(minutes)
        .order(Sort.ascending(SLIRecordKeys.timestamp))
        .asList();
  }

  private SLIRecord getLastSLIRecord(String sliId, Instant startTimeStamp) {
    return hPersistence.createQuery(SLIRecord.class)
        .filter(SLIRecordKeys.sliId, sliId)
        .field(SLIRecordKeys.timestamp)
        .lessThan(startTimeStamp)
        .order(Sort.descending(SLIRecordKeys.timestamp))
        .get();
  }
}
