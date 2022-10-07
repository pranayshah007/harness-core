/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.rule.OwnerRule.SHALINI;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.pms.resume.EngineResumeCallback;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializerWrapper;

import com.esotericsoftware.kryo.KryoException;
import com.google.inject.Provider;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationComponentTesterTest extends OrchestrationTestBase {
  @Mock Provider<KryoSerializerWrapper> kryoSerializerProvider;
  @Mock KryoSerializerWrapper kryoSerializerWrapper;

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testTestKryoRegistration() {
    doReturn(kryoSerializerWrapper).when(kryoSerializerProvider).get();
    doReturn(true).when(kryoSerializerWrapper).isRegistered(any());
    assertThatCode(() -> OrchestrationComponentTester.testKryoRegistration(kryoSerializerProvider))
        .doesNotThrowAnyException();
    doReturn(false).when(kryoSerializerWrapper).isRegistered(EngineResumeCallback.class);
    assertThatThrownBy(() -> {
      OrchestrationComponentTester.testKryoRegistration(kryoSerializerProvider);
    }).isInstanceOf(KryoException.class);
  }
}
