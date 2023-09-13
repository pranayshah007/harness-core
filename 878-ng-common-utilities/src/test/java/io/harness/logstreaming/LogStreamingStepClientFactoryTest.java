/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logstreaming;

import static io.harness.beans.FeatureName.*;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.rule.Owner;
import io.harness.steps.StepUtils;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.PIPELINE)
public class LogStreamingStepClientFactoryTest extends CategoryTest {
  private static final String SERVICE_TOKEN = "token";
  private static final String ACCOUNT_ID = "accountId";
  private static final String TOKEN = "Token";

  LogStreamingStepClientFactory logStreamingStepClientFactory = spy(new LogStreamingStepClientFactory());

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void getLogStreamingStepClient() throws Exception {
    logStreamingStepClientFactory.logStreamingClientThreadPool = Mockito.mock(ThreadPoolExecutor.class);
    Ambiance ambiance =
        Ambiance.newBuilder()
            .putSetupAbstractions("accountId", ACCOUNT_ID)
            .setMetadata(
                ExecutionMetadata.newBuilder().putFeatureFlagToValueMap("PIE_SIMPLIFY_LOG_BASE_KEY", false).build())
            .build();

    logStreamingStepClientFactory.accountIdToTokenCache.put(ACCOUNT_ID, TOKEN);

    assertThat(logStreamingStepClientFactory.getLogStreamingStepClient(ambiance)).isNotNull();
    LogStreamingStepClientImpl logStreamingStepClient =
        (LogStreamingStepClientImpl) logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    assertThat(logStreamingStepClient.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(logStreamingStepClient.getBaseLogKey())
        .isEqualTo(LogStreamingHelper.generateLogBaseKey(StepUtils.generateLogAbstractions(ambiance)));
    assertThat(logStreamingStepClient.getToken()).isEqualTo(TOKEN);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void getLogStreamingStepClientWithSimplifiedLogKey() throws Exception {
    logStreamingStepClientFactory.logStreamingClientThreadPool = Mockito.mock(ThreadPoolExecutor.class);
    Ambiance ambiance =
        Ambiance.newBuilder()
            .putSetupAbstractions("accountId", ACCOUNT_ID)
            .setMetadata(
                ExecutionMetadata.newBuilder().putFeatureFlagToValueMap("PIE_SIMPLIFY_LOG_BASE_KEY", true).build())
            .build();

    logStreamingStepClientFactory.accountIdToTokenCache.put(ACCOUNT_ID, TOKEN);

    assertThat(logStreamingStepClientFactory.getLogStreamingStepClient(ambiance)).isNotNull();
    LogStreamingStepClientImpl logStreamingStepClient =
        (LogStreamingStepClientImpl) logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    assertThat(logStreamingStepClient.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(logStreamingStepClient.getBaseLogKey())
        .isEqualTo(LogStreamingHelper.generateSimplifiedLogBaseKey(
            StepUtils.generateSimplifiedLogAbstractions(ambiance, null)));
    assertThat(logStreamingStepClient.getToken()).isEqualTo(TOKEN);
  }
}
