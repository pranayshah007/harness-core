/*

 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 */

package io.harness.cdng.provision.terragrunt;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.expression.EngineExpressionSecretUtils;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.expression.EngineExpressionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class TerragruntConfigDAL {
  @Inject private HPersistence persistence;
  @Inject private EngineExpressionService engineExpressionService;

  void saveTerragruntConfig(@Nonnull TerragruntConfig terragruntConfig) {
    // NG Secret Manager Functor is reversed to secret.getValue form so that token is not saved inside DB
    TerragruntConfig secretsRevertedConfig =
        (TerragruntConfig) EngineExpressionSecretUtils.revertSecrets(terragruntConfig);
    persistence.save(secretsRevertedConfig);
  }

  TerragruntConfig getTerragruntConfig(@Nonnull Query<TerragruntConfig> query, @Nonnull Ambiance ambiance) {
    TerragruntConfig terragruntConfig = query.get();

    if (terragruntConfig == null) {
      return null;
    }
    ExpressionEvaluatorUtils.updateExpressions(
        terragruntConfig, new CDExpressionResolveFunctor(engineExpressionService, ambiance));

    return terragruntConfig;
  }
}
