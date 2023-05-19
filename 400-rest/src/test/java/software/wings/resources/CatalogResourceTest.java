/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.rule.OwnerRule.UNKNOWN;

import static software.wings.beans.CatalogNames.EXECUTION_TYPE;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.CatalogNames;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.service.intfc.CatalogService;
import software.wings.service.intfc.SettingsService;
import software.wings.utils.ResourceTestRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import javax.ws.rs.core.GenericType;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.Verifier;
import org.junit.runner.RunWith;

/**
 * The Class CatalogResourceTest.
 *
 * @author Rishi.
 */
@RunWith(JUnitParamsRunner.class)
public class CatalogResourceTest extends WingsBaseTest {
  private static final CatalogService catalogService = mock(CatalogService.class);
  private static final SettingsService settingsService = mock(SettingsService.class);

  /**
   * The constant resources.
   */
  @ClassRule
  public static final ResourceTestRule resources =
      ResourceTestRule.builder().instance(new CatalogResource(catalogService, settingsService)).build();

  /**
   * The Verifier.
   */
  @Rule
  public Verifier verifier = new Verifier() {
    @Override
    protected void verify() throws Throwable {
      verifyNoMoreInteractions(catalogService, settingsService);
    }
  };

  /**
   * Setup mocks.
   *
   * @throws IOException Signals that an I/O exception has occurred.
   */
  @Before
  public void setupMocks() throws IOException {
    when(settingsService.get(anyString())).thenReturn(aSettingAttribute().withValue(new JenkinsConfig()).build());
    when(settingsService.getSettingAttributesByType(anyString(), anyString())).thenReturn(newArrayList());
  }

  /**
   * Tear down.
   */
  @After
  public void tearDown() {
    reset(catalogService, settingsService);
  }

  /**
   * Should list catalogs.
   */
  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  public void shouldListCatalogs() {
    when(catalogService.getCatalogItems(anyString())).thenReturn(new ArrayList<>());

    RestResponse<Map<String, Object>> actual =
        resources.client()
            .target("/catalogs?catalogType=EXECUTION_TYPE&catalogType=CARD_VIEW_SORT_BY")
            .request()
            .get(new GenericType<RestResponse<Map<String, Object>>>() {});

    assertThat(actual).isNotNull();
    assertThat(actual.getResource()).isNotNull().hasSize(2).containsKeys(EXECUTION_TYPE, "CARD_VIEW_SORT_BY");
  }

  private Object[][] catalogNames() {
    return new Object[][] {{UPPER_UNDERSCORE.to(UPPER_CAMEL, CatalogNames.CONNECTION_ATTRIBUTES), null},
        {UPPER_UNDERSCORE.to(UPPER_CAMEL, CatalogNames.BASTION_HOST_ATTRIBUTES), null},
        {UPPER_UNDERSCORE.to(UPPER_CAMEL, CatalogNames.EXECUTION_TYPE), null},
        {UPPER_UNDERSCORE.to(UPPER_CAMEL, CatalogNames.ENVIRONMENT_TYPE), null}};
  }

  /**
   * Should list catalogs for.
   *
   * @param catalogNameForDisplay the catalog name for display
   * @param settingAttribute      the setting attribute
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: this test is not working after upgrade of assertJ")
  @Parameters(method = "catalogNames")
  public void shouldListCatalogsFor(String catalogNameForDisplay, SettingAttribute settingAttribute) {
    if (settingAttribute != null) {
      when(settingsService.get(any())).thenReturn(settingAttribute);
    }
    String catalogName = UPPER_CAMEL.to(UPPER_UNDERSCORE, catalogNameForDisplay);
    RestResponse<Map<String, Object>> actual = resources.client()
                                                   .target("/catalogs?catalogType=" + catalogName + "&appId=" + APP_ID)
                                                   .request()
                                                   .get(new GenericType<RestResponse<Map<String, Object>>>() {});

    assertThat(actual)
        .isNotNull()
        .extracting(RestResponse::getResource)
        .isNotNull()
        .extracting(o -> o.get(catalogName))
        .isNotNull();
  }
}
