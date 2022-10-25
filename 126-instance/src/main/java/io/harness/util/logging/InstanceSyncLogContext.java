/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.util.logging;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.NullSafeImmutableMap;
import io.harness.data.structure.NullSafeImmutableMap.NullSafeBuilder;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.models.constants.InstanceSyncConstants;

import com.google.common.collect.ImmutableMap;

@OwnedBy(HarnessTeam.DX)
public class InstanceSyncLogContext extends AutoLogContext {
  public InstanceSyncLogContext(ImmutableMap<String, String> context, OverrideBehavior behavior) {
    super(context, behavior);
  }

  public static InstanceSyncLogContext.Builder builder() {
    return new InstanceSyncLogContext.Builder();
  }

  public static class Builder {
    private final NullSafeBuilder<String, String> nullSafeBuilder = NullSafeImmutableMap.builder();

    public Builder instanceSyncFlow(String instanceSyncFlow) {
      nullSafeBuilder.putIfNotNull(InstanceSyncConstants.INSTANCE_SYNC_FLOW_KEY, instanceSyncFlow);
      return this;
    }

    public Builder perpetualTaskId(String perpetualTaskId) {
      nullSafeBuilder.putIfNotNull(InstanceSyncConstants.PERPETUAL_TASK_ID_KEY, perpetualTaskId);
      return this;
    }

    public Builder infrastructureMappingId(String infrastructureMappingId) {
      nullSafeBuilder.putIfNotNull(InstanceSyncConstants.INFRASTRUCTURE_MAPPING_KEY, infrastructureMappingId);
      return this;
    }

    public Builder eventId(String eventId) {
      nullSafeBuilder.putIfNotNull(InstanceSyncConstants.DEPLOYMENT_EVENT_ID, eventId);
      return this;
    }

    public InstanceSyncLogContext build(OverrideBehavior behavior) {
      nullSafeBuilder.put("TAG", InstanceSyncConstants.INSTANCE_SYNC_FLOW_KEY);
      return new InstanceSyncLogContext(nullSafeBuilder.build(), behavior);
    }
  }
}
