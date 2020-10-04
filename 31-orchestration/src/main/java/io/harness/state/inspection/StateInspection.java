package io.harness.state.inspection;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.persistence.PersistentEntity;
import io.harness.state.StateInspectionUtils;
import lombok.Builder;
import lombok.Value;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Map;

@OwnedBy(CDC)
@Value
@Builder
@Entity(value = "stateInspections", noClassnameStored = true)
@HarnessEntity(exportable = false)
public final class StateInspection implements PersistentEntity {
  @Id private final String stateExecutionInstanceId;
  private final Map<String, StateInspectionData> data;

  @FdTtlIndex
  private final Date validUntil = Date.from(OffsetDateTime.now().plus(StateInspectionUtils.TTL).toInstant());

  public static final class StateInspectionKeys {
    public static final String stateExecutionInstanceId = "stateExecutionInstanceId";
    public static final String data = "data";
    public static final String validUntil = "validUntil";
  }
}
