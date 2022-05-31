package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.beans.StoreType;
import io.harness.pms.contracts.plan.PipelineStoreType;

import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class StoreTypeMapper {
  public StoreType fromPipelineStoreType(PipelineStoreType pipelineStoreType) {
    if (pipelineStoreType == null) {
      return null;
    }
    switch (pipelineStoreType) {
      case INLINE:
        return StoreType.INLINE;
      case REMOTE:
        return StoreType.REMOTE;
      default:
        return null;
    }
  }

  public PipelineStoreType fromStoreType(StoreType storeType) {
    if (storeType == null) {
      return null;
    }
    switch (storeType) {
      case INLINE:
        return PipelineStoreType.INLINE;
      case REMOTE:
        return PipelineStoreType.REMOTE;
      default:
        return null;
    }
  }
}
