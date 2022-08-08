/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.instana;

import static io.harness.annotations.dev.HarnessModule._960_API_SERVICES;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.service.impl.instana.InstanaAnalyzeMetricRequest;
import software.wings.service.impl.instana.InstanaAnalyzeMetrics;
import software.wings.service.impl.instana.InstanaInfraMetricRequest;
import software.wings.service.impl.instana.InstanaInfraMetrics;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
@OwnedBy(HarnessTeam.CV)
@TargetModule(_960_API_SERVICES)
public interface InstanaRestClient {
  @POST("api/infrastructure-monitoring/metrics/")
  Call<InstanaInfraMetrics> getInfrastructureMetrics(
      @Header("Authorization") String authorization, @Body InstanaInfraMetricRequest request);
  @POST("api/application-monitoring/analyze/trace-groups")
  Call<InstanaAnalyzeMetrics> getGroupedTraceMetrics(
      @Header("Authorization") String authorization, @Body InstanaAnalyzeMetricRequest request);
}
