package io.harness.perpetualtask.instancesyncv2.cg;

import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams;

// instance sync v2
public interface InstanceDetailsFetcher {
  PerpetualTaskResponse fetchRunningInstanceDetails(PerpetualTaskId taskId, CgInstanceSyncTaskParams params);
}
