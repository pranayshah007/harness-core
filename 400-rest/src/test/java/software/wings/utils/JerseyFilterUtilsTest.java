/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.annotations.dev.HarnessTeam.SPG;
import static io.harness.rule.OwnerRule.VIKAS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.resources.UserResourceNG;

import java.lang.reflect.Method;
import javax.ws.rs.container.ResourceInfo;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(SPG)
@RunWith(MockitoJUnitRunner.class)
public class JerseyFilterUtilsTest {
  @Mock private ResourceInfo resourceInfo;

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testFilter_For_NextGenRequest() {
    Class clazz = UserResourceNG.class;
    when(resourceInfo.getResourceClass()).thenReturn(clazz);
    when(resourceInfo.getResourceMethod()).thenReturn(getNgMockResourceMethod());
    boolean isNextGenRequest = JerseyFilterUtils.isNextGenManagerRequest(resourceInfo);
    assertThat(isNextGenRequest).isTrue();
  }

  private Method getNgMockResourceMethod() {
    Class mockClass = UserResourceNG.class;
    try {
      return mockClass.getMethod("getUser", String.class);
    } catch (NoSuchMethodException e) {
      return null;
    }
  }
}
