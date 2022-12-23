/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.service.intfc.cvng;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.change.HarnessCDCurrentGenEventMetadata;

import java.time.Instant;
import java.util.List;

@OwnedBy(CV)
public interface CDChangeSourceIntegrationService {
  List<HarnessCDCurrentGenEventMetadata> getCurrentGenEventsBetween(
      String accountId, String appId, String serviceId, String environmentId, Instant startTime, Instant endTime);
}
