/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTaskParams;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTaskParams.ScmPathFilterEvaluationTaskParamsBuilder;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTaskResponse;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;

import lombok.NoArgsConstructor;

@NoArgsConstructor
@OwnedBy(HarnessTeam.CI)
public abstract class SCMFilePathEvaluator {
  public abstract ScmPathFilterEvaluationTaskResponse execute(FilterRequestData filterRequestData,
      TriggerEventDataCondition pathCondition, ConnectorDetails connectorDetails, ScmConnector scmConnector);

  public ScmPathFilterEvaluationTaskParams getScmPathFilterEvaluationTaskParams(FilterRequestData filterRequestData,
      TriggerEventDataCondition pathCondition, ConnectorDetails connectorDetails, ScmConnector scmConnector) {
    ScmPathFilterEvaluationTaskParamsBuilder paramsBuilder =
        ScmPathFilterEvaluationTaskParams.builder()
            .encryptedDataDetails(connectorDetails.getEncryptedDataDetails())
            .scmConnector(scmConnector)
            .operator(pathCondition.getOperator().getValue())
            .standard(pathCondition.getValue());

    ParseWebhookResponse parseWebhookResponse = filterRequestData.getWebhookPayloadData().getParseWebhookResponse();
    if (parseWebhookResponse.getHookCase() == ParseWebhookResponse.HookCase.PR) {
      paramsBuilder.prNumber((int) parseWebhookResponse.getPr().getPr().getNumber());
    } else {
      paramsBuilder.branch(parseWebhookResponse.getPush().getRef())
          .latestCommit(parseWebhookResponse.getPush().getAfter())
          .previousCommit(parseWebhookResponse.getPush().getBefore());
    }
    return paramsBuilder.build();
  }
}
