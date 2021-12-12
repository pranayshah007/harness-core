package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget.SLOGraphData;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordParam;

import java.time.Instant;
import java.util.List;

public interface SLIRecordService {
  void create(List<SLIRecordParam> sliRecordList, String sliId, String verificationTaskId);
  SLOGraphData getGraphData(String sliId, Instant startTime, Instant endTime, int totalErrorBudgetMinutes,
      SLIMissingDataType sliMissingDataType);
}
