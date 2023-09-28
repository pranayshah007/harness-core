/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.client.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.beans.AuditEventDTO;
import io.harness.audit.beans.custom.ActiveProjectMetricsDTO;
import io.harness.ng.core.dto.ResponseDTO;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

@OwnedBy(PL)
public interface AuditClient {
  String AUDITS_API = "audits";
  String PUBLISH_METRICS_API = "audits/publish-metrics";

  @POST(AUDITS_API) Call<ResponseDTO<Boolean>> createAudit(@Body AuditEventDTO auditEventDTO);

  @POST(PUBLISH_METRICS_API)
  Call<ResponseDTO<Void>> publishMetrics(@Body ActiveProjectMetricsDTO activeProjectMetricsDTO);
}
