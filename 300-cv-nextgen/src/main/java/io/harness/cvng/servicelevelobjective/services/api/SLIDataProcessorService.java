package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseRequest;
import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseResponse;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.SLIMetricSpec;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface SLIDataProcessorService {
  List<SLIAnalyseResponse> process(Map<String, List<SLIAnalyseRequest>> sliAnalyseRequest, SLIMetricSpec sliSpec,
      Instant startTime, Instant endTime);
}
