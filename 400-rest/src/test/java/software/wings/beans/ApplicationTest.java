/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.rule.OwnerRule.RAMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import software.wings.WingsBaseTest;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Test case.
 *
 * @author Rishi
 */
@Slf4j
public class ApplicationTest extends WingsBaseTest {
  /**
   * Test serialize deserialize.
   */
  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testSerializeDeserialize() {
    Application app = new Application();
    final String appName = "TestApp-" + System.currentTimeMillis();
    final String desc = "TestAppDesc-" + System.currentTimeMillis();
    app.setName(appName);
    app.setDescription(desc);
    app.onSave();

    // resetting createdBy and lastUpdatedBy since those fields are marked with @jsonIgnore
    app.setCreatedBy(null);
    app.setLastUpdatedBy(null);

    if (log.isDebugEnabled()) {
      log.debug("TestApp : " + app);
    }

    String json = JsonUtils.asJson(app);
    if (log.isDebugEnabled()) {
      log.debug("json : " + json);
    }

    Application app2 = JsonUtils.asObject(json, Application.class);
    assertThat(app2).isEqualToComparingFieldByField(app);
  }
}
