/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.expression;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.data.structure.EmptyPredicate;
import io.harness.expression.DockerConfigJsonSecretFunctor;
import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.ImageSecretFunctor;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.ff.FeatureFlagService;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SimpleEncryption;

import software.wings.expression.NgSecretManagerFunctor.NgSecretManagerFunctorBuilder;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;

import com.github.benmanes.caffeine.cache.Cache;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import lombok.Value;

@OwnedBy(CDC)
@Value
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ManagerPreExecutionExpressionEvaluator extends ExpressionEvaluator {
  private final ExpressionFunctor secretManagerFunctor;
  private final ExpressionFunctor ngSecretManagerFunctor;
  private final SweepingOutputSecretFunctor sweepingOutputSecretFunctor;

  public ManagerPreExecutionExpressionEvaluator(SecretManagerMode mode, ServiceTemplateService serviceTemplateService,
      ConfigService configService, ArtifactCollectionUtils artifactCollectionUtils,
      FeatureFlagService featureFlagService, ManagerDecryptionService managerDecryptionService,
      SecretManager secretManager, String accountId, String workflowExecutionId, int expressionFunctorToken,
      SecretManagerClientService ngSecretService, Map<String, String> taskSetupAbstractions,
      Cache<String, EncryptedDataDetails> secretsCache, DelegateMetricsService delegateMetricsService,
      ExecutorService expressionEvaluatorExecutor) {
    setEvaluationMode(mode);
    String appId = taskSetupAbstractions == null ? null : taskSetupAbstractions.get(Cd1SetupFields.APP_ID_FIELD);
    String envId = taskSetupAbstractions == null ? null : taskSetupAbstractions.get(Cd1SetupFields.ENV_ID_FIELD);
    String serviceTemplateId =
        taskSetupAbstractions == null ? null : taskSetupAbstractions.get(Cd1SetupFields.SERVICE_TEMPLATE_ID_FIELD);
    String artifactStreamId =
        taskSetupAbstractions == null ? null : taskSetupAbstractions.get(Cd1SetupFields.ARTIFACT_STREAM_ID_FIELD);

    addFunctor("configFile",
        ConfigFileFunctor.builder()
            .appId(appId)
            .envId(envId)
            .serviceTemplateId(serviceTemplateId)
            .configService(configService)
            .serviceTemplateService(serviceTemplateService)
            .expressionFunctorToken(expressionFunctorToken)
            .build());

    addFunctor("dockerconfig",
        DockerConfigFunctor.builder()
            .appId(appId)
            .artifactStreamId(artifactStreamId)
            .artifactCollectionUtils(artifactCollectionUtils)
            .build());

    secretManagerFunctor = SecretManagerFunctor.builder()
                               .mode(mode)
                               .featureFlagService(featureFlagService)
                               .managerDecryptionService(managerDecryptionService)
                               .secretManager(secretManager)
                               .secretsCache(secretsCache)
                               .accountId(accountId)
                               .appId(appId)
                               .envId(envId)
                               .workflowExecutionId(workflowExecutionId)
                               .expressionFunctorToken(expressionFunctorToken)
                               .delegateMetricsService(delegateMetricsService)
                               .expressionEvaluatorExecutor(expressionEvaluatorExecutor)
                               .build();
    addFunctor(SecretManagerFunctorInterface.FUNCTOR_NAME, secretManagerFunctor);

    NgSecretManagerFunctorBuilder ngSecretManagerFunctorBuilder =
        NgSecretManagerFunctor.builder()
            .mode(mode)
            .accountId(accountId)
            .expressionFunctorToken(expressionFunctorToken)
            .secretManager(secretManager)
            .secretsCache(secretsCache)
            .delegateMetricsService(delegateMetricsService)
            .ngSecretService(ngSecretService)
            .expressionEvaluatorExecutor(expressionEvaluatorExecutor);

    if (EmptyPredicate.isNotEmpty(taskSetupAbstractions)) {
      ngSecretManagerFunctorBuilder.orgId(taskSetupAbstractions.get("orgIdentifier"))
          .projectId(taskSetupAbstractions.get("projectIdentifier"));
    }

    ngSecretManagerFunctor = ngSecretManagerFunctorBuilder.build();
    addFunctor(NgSecretManagerFunctorInterface.FUNCTOR_NAME, ngSecretManagerFunctor);

    addFunctor(ImageSecretFunctor.FUNCTOR_NAME, new ImageSecretFunctor());
    addFunctor(DockerConfigJsonSecretFunctor.FUNCTOR_NAME, new DockerConfigJsonSecretFunctor());

    sweepingOutputSecretFunctor =
        SweepingOutputSecretFunctor.builder().mode(mode).simpleEncryption(new SimpleEncryption()).build();

    addFunctor("sweepingOutputSecrets", sweepingOutputSecretFunctor);

    Base64ManagerFunctor base64ManagerFunctor =
        Base64ManagerFunctor.builder().mode(mode).expressionFunctors(super.getExpressionFunctorMap().keySet()).build();
    addFunctor("ngBase64Manager", base64ManagerFunctor);
  }
}
