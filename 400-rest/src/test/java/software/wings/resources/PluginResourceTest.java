/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.rule.OwnerRule.UNKNOWN;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.violation.ConstraintViolationExceptionMapper;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.beans.AccountPlugin;
import software.wings.exception.WingsExceptionMapper;
import software.wings.service.intfc.PluginService;
import software.wings.utils.ResourceTestRule;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.GenericType;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Created by peeyushaggarwal on 10/21/16.
 */
public class PluginResourceTest extends CategoryTest {
  public static final PluginService PLUGIN_SERVICE = mock(PluginService.class);

  @ClassRule
  public static final ResourceTestRule RESOURCES = ResourceTestRule.builder()
                                                       .instance(new PluginResource(PLUGIN_SERVICE))
                                                       .type(ConstraintViolationExceptionMapper.class)
                                                       .type(WingsExceptionMapper.class)
                                                       .build();

  @Before
  public void setUp() throws IOException {
    reset(PLUGIN_SERVICE);
  }

  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  public void shouldGetInstalledPlugins() throws Exception {
    RestResponse<List<AccountPlugin>> restResponse = RESOURCES.client()
                                                         .target("/plugins/ACCOUNT_ID/installed")
                                                         .request()
                                                         .get(new GenericType<RestResponse<List<AccountPlugin>>>() {});
    verify(PLUGIN_SERVICE).getInstalledPlugins("ACCOUNT_ID");
  }

  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  public void shouldGetInstalledPluginSettingSchema() throws Exception {
    RestResponse<Map<String, JsonNode>> restResponse =
        RESOURCES.client()
            .target("/plugins/ACCOUNT_ID/installed/settingschema")
            .request()
            .get(new GenericType<RestResponse<Map<String, JsonNode>>>() {});
    verify(PLUGIN_SERVICE).getPluginSettingSchema("ACCOUNT_ID");
  }
}
