/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.CET;

import io.harness.annotations.dev.OwnedBy;
import io.harness.errortracking.client.beans.ErrorTrackingDefaultAgentConfig;
import io.harness.errortracking.client.remote.ErrorTrackingClient;
import io.harness.exception.EngineFunctorException;
import io.harness.expression.LateBindingValue;
import io.harness.network.SafeHttpCall;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.expression.SdkFunctor;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@OwnedBy(CET)
public class ErrorTrackingFunctor implements SdkFunctor {
  public static final String CET = "cet";
  @Inject private ErrorTrackingClient errorTrackingClient;

  @Override
  public Object get(Ambiance ambiance, String... args) {
    Map<String, Object> jsonObject = new HashMap<>();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);

    String value = "";
    if (args.length > 0) {
      try {
        ErrorTrackingDefaultAgentConfig config =
            SafeHttpCall.execute(errorTrackingClient.getDefaultAgentConfig(accountId, orgId, projectId));
        switch (args[0]) {
          case "defaultAgentToken":
            value = config.getDefaultAgentToken();
            break;
          case "collectorUrl":
            value = config.getCollectorUrl();
            break;
        }
      } catch (Exception ex) {
        throw new EngineFunctorException(
            String.format("Unable to retrieve CET agent configuration: account=%s, org=%s, project=%s", accountId,
                orgId, projectId),
            ex);
      }
    }

    return value;
  }
}
