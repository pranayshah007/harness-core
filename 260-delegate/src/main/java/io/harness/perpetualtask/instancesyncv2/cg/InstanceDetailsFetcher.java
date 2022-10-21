package io.harness.perpetualtask.instancesyncv2.cg;

import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams;

import software.wings.beans.infrastructure.instance.info.InstanceInfo;

import java.util.List;

// instance sync v2
public interface InstanceDetailsFetcher {
  List<InstanceInfo> fetchRunningInstanceDetails(
      PerpetualTaskId taskId, CgInstanceSyncTaskParams params, String releaseDtails);
}
