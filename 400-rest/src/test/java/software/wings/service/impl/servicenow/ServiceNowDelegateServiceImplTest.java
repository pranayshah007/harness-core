/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.AGORODETKI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.ServiceNowException;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Collections;
import okhttp3.OkHttpClient;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDC)
@RunWith(MockitoJUnitRunner.class)
public class ServiceNowDelegateServiceImplTest extends CategoryTest {
  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnHttpClientWithIncreasedTimeout() {
    OkHttpClient httpClientWithIncreasedTimeout =
        ServiceNowDelegateServiceImpl.getHttpClientWithIncreasedTimeout("url.com", false);
    assertThat(httpClientWithIncreasedTimeout.connectTimeoutMillis())
        .isEqualTo(ServiceNowDelegateServiceImpl.TIME_OUT * 1000);
    assertThat(httpClientWithIncreasedTimeout.readTimeoutMillis())
        .isEqualTo(ServiceNowDelegateServiceImpl.TIME_OUT * 1000);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenCanNotReadElementValue() {
    ServiceNowDelegateServiceImpl serviceNowDelegateService = new ServiceNowDelegateServiceImpl();
    assertThatThrownBy(()
                           -> serviceNowDelegateService.getTextValue(
                               new ObjectNode(JsonNodeFactory.instance, Collections.singletonMap("value", null)),
                               "value", "fieldName"))
        .isInstanceOf(ServiceNowException.class)
        .hasMessage("Can not read field: fieldName. User might not have explicit read access to sys_choice table");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnElementValueAsText() {
    ServiceNowDelegateServiceImpl serviceNowDelegateService = new ServiceNowDelegateServiceImpl();
    String textValue = serviceNowDelegateService.getTextValue(
        new ObjectNode(JsonNodeFactory.instance, Collections.singletonMap("value", new TextNode("123"))), "value",
        "fieldName");

    assertThat(textValue).isEqualTo("123");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenCanNotReadElementLabel() {
    ServiceNowDelegateServiceImpl serviceNowDelegateService = new ServiceNowDelegateServiceImpl();
    assertThatThrownBy(
        ()
            -> serviceNowDelegateService.getTextValue(
                new ObjectNode(JsonNodeFactory.instance, Collections.singletonMap("label", null)), "label", "priority"))
        .isInstanceOf(ServiceNowException.class)
        .hasMessage("Can not read field: priority. User might not have explicit read access to sys_choice table");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnElementLabelAsText() {
    ServiceNowDelegateServiceImpl serviceNowDelegateService = new ServiceNowDelegateServiceImpl();
    String textValue = serviceNowDelegateService.getTextValue(
        new ObjectNode(JsonNodeFactory.instance, Collections.singletonMap("label", new TextNode("1 - High"))), "label",
        "priority");

    assertThat(textValue).isEqualTo("1 - High");
  }
}
