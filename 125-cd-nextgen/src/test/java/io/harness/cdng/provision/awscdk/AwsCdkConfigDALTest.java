/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.awscdk;

import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.provision.awscdk.beans.AwsCdkConfig;
import io.harness.cdng.provision.awscdk.beans.AwsCdkConfig.AwsCdkConfigKeys;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;

import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AwsCdkConfigDALTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private HPersistence persistence;
  @Mock private CDExpressionResolver cdExpressionResolver;
  @InjectMocks private final AwsCdkConfigDAL awsCdkConfigDAL = new AwsCdkConfigDAL();

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testSaveAwsCdkConfig() {
    AwsCdkConfig awsCdkConfig = AwsCdkConfig.builder().build();
    awsCdkConfigDAL.saveAwsCdkConfig(awsCdkConfig);
    verify(persistence, times(1)).save(awsCdkConfig);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetAwsCdkConfig() {
    AwsCdkConfig awsCdkConfig = AwsCdkConfig.builder().build();
    String provisionerIdentifier = "provisionerIdentifier";
    Query mockQuery = mock(Query.class);
    doReturn(mockQuery).when(persistence).createQuery(any());
    doReturn(mockQuery).when(mockQuery).filter(any(), any());
    doReturn(mockQuery).when(mockQuery).order(any(Sort.class));
    doReturn(awsCdkConfig).when(mockQuery).get();
    doReturn(mock(FieldEnd.class)).when(mockQuery).criteria(anyString());

    AwsCdkConfig config = awsCdkConfigDAL.getRollbackAwsCdkConfig(getAmbiance(), provisionerIdentifier);
    assertThat(config).isEqualTo(awsCdkConfig);
    verify(persistence, times(1)).createQuery(AwsCdkConfig.class);
    verify(mockQuery, times(1)).get();
    verify(mockQuery, times(1)).filter(eq(AwsCdkConfigKeys.accountId), eq("account"));
    verify(mockQuery, times(1)).filter(eq(AwsCdkConfigKeys.orgId), eq("org"));
    verify(mockQuery, times(1)).filter(eq(AwsCdkConfigKeys.projectId), eq("project"));
    verify(mockQuery, times(1)).filter(eq(AwsCdkConfigKeys.provisionerIdentifier), eq(provisionerIdentifier));
    verify(cdExpressionResolver).updateExpressions(any(), eq(awsCdkConfig));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testDeleteAwsCdkConfig() {
    String provisionerIdentifier = "provisionerIdentifier";
    Query mockQuery = mock(Query.class);
    doReturn(mockQuery).when(persistence).createQuery(any());
    doReturn(mockQuery).when(mockQuery).filter(any(), any());
    doReturn(true).when(persistence).delete(eq(mockQuery));

    awsCdkConfigDAL.deleteAwsCdkConfig(getAmbiance(), provisionerIdentifier);
    verify(persistence, times(1)).createQuery(AwsCdkConfig.class);
    verify(persistence, times(1)).delete(mockQuery);

    verify(mockQuery, times(1)).filter(eq(AwsCdkConfigKeys.accountId), eq("account"));
    verify(mockQuery, times(1)).filter(eq(AwsCdkConfigKeys.orgId), eq("org"));
    verify(mockQuery, times(1)).filter(eq(AwsCdkConfigKeys.projectId), eq("project"));
    verify(mockQuery, times(1)).filter(eq(AwsCdkConfigKeys.provisionerIdentifier), eq(provisionerIdentifier));
    verify(mockQuery, times(1)).filter(eq(AwsCdkConfigKeys.stageExecutionId), eq("stageExecutionId"));
    verify(persistence, times(1)).createQuery(AwsCdkConfig.class);
    verify(persistence, times(1)).delete(mockQuery);
  }

  private Ambiance getAmbiance() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "account");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project");
    return Ambiance.newBuilder()
        .putAllSetupAbstractions(setupAbstractions)
        .setStageExecutionId("stageExecutionId")
        .build();
  }
}
