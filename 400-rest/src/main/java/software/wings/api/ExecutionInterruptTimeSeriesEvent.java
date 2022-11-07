package software.wings.api;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.ng.DbAliases;
import io.harness.queue.Queuable;

import software.wings.service.impl.event.timeseries.TimeSeriesEventInfo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.mongodb.morphia.annotations.Entity;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "executionInterruptTimeSeriesEventQueue", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class ExecutionInterruptTimeSeriesEvent extends Queuable {
  private TimeSeriesEventInfo timeSeriesEventInfo;
}
