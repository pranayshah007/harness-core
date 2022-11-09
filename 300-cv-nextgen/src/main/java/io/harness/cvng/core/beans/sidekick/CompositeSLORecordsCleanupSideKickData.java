package io.harness.cvng.core.beans.sidekick;

import io.harness.cvng.core.entities.SideKick.SideKickData;
import io.harness.cvng.core.entities.SideKick.Type;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CompositeSLORecordsCleanupSideKickData implements SideKickData {
  String sloId;
  int sloVersion;
  long afterStartTime;
  @Override
  public Type getType() {
    return Type.COMPOSITE_SLO_RECORDS_CLEANUP;
  }
}