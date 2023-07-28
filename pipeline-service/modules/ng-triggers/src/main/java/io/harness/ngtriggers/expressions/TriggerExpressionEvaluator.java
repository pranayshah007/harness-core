/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.expressions;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.HeaderConfig;
import io.harness.exception.CriticalExpressionEvaluationException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.EngineJexlContext;
import io.harness.expression.common.ExpressionMode;
import io.harness.ngtriggers.expressions.functors.PayloadFunctor;
import io.harness.ngtriggers.expressions.functors.TriggerPayloadFunctor;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.triggers.ArtifactData;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;

import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class TriggerExpressionEvaluator extends EngineExpressionEvaluator {
  private final String payload;
  private final TriggerPayload triggerPayload;

  public TriggerExpressionEvaluator(ParseWebhookResponse parseWebhookResponse, ArtifactData artifactData,
      List<HeaderConfig> headerConfigs, String payload) {
    super(null);
    TriggerPayload.Builder builder = TriggerPayload.newBuilder();
    if (parseWebhookResponse != null) {
      if (parseWebhookResponse.hasPr()) {
        builder.setParsedPayload(ParsedPayload.newBuilder().setPr(parseWebhookResponse.getPr()).build()).build();
      } else if (parseWebhookResponse.hasRelease()) {
        builder.setParsedPayload(ParsedPayload.newBuilder().setRelease(parseWebhookResponse.getRelease()).build())
            .build();
      } else {
        builder.setParsedPayload(ParsedPayload.newBuilder().setPush(parseWebhookResponse.getPush()).build()).build();
      }
    }
    if (artifactData != null) {
      builder.setArtifactData(artifactData);
    }
    if (headerConfigs != null) {
      for (HeaderConfig config : headerConfigs) {
        if (config != null) {
          builder.putHeaders(config.getKey().toLowerCase(), config.getValues().get(0));
        }
      }
    }
    this.triggerPayload = builder.build();
    Ambiance.newBuilder().setMetadata(ExecutionMetadata.newBuilder().build()).build();
    this.payload = payload;
  }

  @Override
  protected void initialize() {
    addToContext(SetupAbstractionKeys.trigger, new TriggerPayloadFunctor(payload, triggerPayload));
    addToContext(SetupAbstractionKeys.eventPayload, new PayloadFunctor(payload));
  }

  @Override
  protected Object evaluateInternal(@NotNull String expression, @NotNull EngineJexlContext ctx) {
    return evaluateByCreatingScript(expression, ctx);
  }

  @Override
  public Object evaluateExpression(String expression) {
    return evaluateExpression(expression, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
  }

  @Override
  public Object evaluateExpression(String expression, ExpressionMode expressionMode) {
    try {
      Object result = evaluateExpression(expression, (Map<String, Object>) null);
      return result == null ? "null" : result;
    } catch (Exception e) {
      log.warn("Failed to evaluated Trigger expression", e);
      return "null";
    }
  }

  public Object evaluateExpressionWithExpressionMode(String expression, ExpressionMode expressionMode) {
    try {
      Object result = evaluateExpression(expression, (Map<String, Object>) null);
      if (result == null && ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED.equals(expressionMode)) {
        throw new CriticalExpressionEvaluationException(
            String.format("Failed to evaluate trigger expression %s", expression), expression);
      }
      return result == null ? "null" : result;
    } catch (Exception e) {
      log.warn("Failed to evaluated Trigger expression", e);
      if (ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED.equals(expressionMode)) {
        throw new CriticalExpressionEvaluationException(
            String.format("Failed to evaluate trigger expression %s", expression), expression, e);
      }
      return "null";
    }
  }
}
