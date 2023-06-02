/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.EngineFunctorException;
import io.harness.network.SafeHttpCall;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;

@OwnedBy(HarnessTeam.PIPELINE)
@PrepareForTest({SafeHttpCall.class})
public class AccountFunctorTest extends CategoryTest {
  @Mock private AccountClient accountClient;
  @InjectMocks private AccountFunctor accountFunctor;
  private Ambiance ambiance = Ambiance.newBuilder().build();
  private Ambiance ambiance1 = Ambiance.newBuilder().putSetupAbstractions("accountId", "accountId").build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testBind() throws IOException {
    MockedStatic<SafeHttpCall> aStatic = Mockito.mockStatic(SafeHttpCall.class);

    String resourceObject = "resource";
    RestResponse<String> restRequest = new RestResponse<>();
    restRequest.setResource(resourceObject);
    on(accountFunctor).set("ambiance", ambiance);
    assertNull(accountFunctor.bind());
    on(accountFunctor).set("ambiance", ambiance1);
    when(accountClient.getAccountDTO(anyString())).thenReturn(null);
    // Should throw exception due to NPE
    assertThatThrownBy(() -> accountFunctor.bind()).isInstanceOf(EngineFunctorException.class);
    aStatic.when(() -> SafeHttpCall.execute(any())).thenReturn(restRequest);
    assertEquals(accountFunctor.bind(), resourceObject);
  }
}
