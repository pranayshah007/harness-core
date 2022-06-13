/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.cloudProvider;

import static io.harness.rule.OwnerRule.SHUBHAM_MAHESHWARI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.utils.RequestField;

import software.wings.WingsBaseTest;
import software.wings.beans.RancherConfig;
import software.wings.beans.SettingAttribute;
import software.wings.graphql.schema.mutation.cloudProvider.QLRancherCloudProviderInput;
import software.wings.graphql.schema.mutation.cloudProvider.QLUpdateRancherCloudProviderInput;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class RancherDataFetcherHelperTest extends WingsBaseTest {
  private static final String NAME = "NAME";
  private static final String URL = "URL";
  private static final String TOKEN_SECRET_ID = "Token Secret ID";
  private static final String ACCOUNT_ID = "123";

  @InjectMocks private final RancherDataFetcherHelper helper = new RancherDataFetcherHelper();

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testToSettingAttribute() {
    QLRancherCloudProviderInput input = QLRancherCloudProviderInput.builder()
                                            .name(RequestField.ofNullable(NAME))
                                            .rancherUrl(RequestField.ofNullable(URL))
                                            .bearerTokenSecretId(RequestField.ofNullable(TOKEN_SECRET_ID))
                                            .build();

    SettingAttribute setting = helper.toSettingAttribute(input, ACCOUNT_ID);
    assertThat(setting.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(setting.getValue()).isInstanceOf(RancherConfig.class);
    assertThat(setting.getName()).isEqualTo(NAME);

    RancherConfig config = (RancherConfig) setting.getValue();
    assertThat(config.getRancherUrl()).isEqualTo(URL);
    assertThat(config.getEncryptedBearerToken()).isEqualTo(TOKEN_SECRET_ID);
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testUpdateSettingAttribute() {
    SettingAttribute setting =
        SettingAttribute.Builder.aSettingAttribute().withValue(RancherConfig.builder().build()).build();
    QLUpdateRancherCloudProviderInput input = QLUpdateRancherCloudProviderInput.builder()
                                                  .name(RequestField.ofNullable(NAME))
                                                  .rancherUrl(RequestField.ofNullable(URL))
                                                  .bearerTokenSecretId(RequestField.ofNullable(TOKEN_SECRET_ID))
                                                  .build();

    helper.updateSettingAttribute(setting, input, ACCOUNT_ID);
    assertThat(setting.getValue()).isInstanceOf(RancherConfig.class);
    assertThat(setting.getName()).isEqualTo(NAME);

    RancherConfig config = (RancherConfig) setting.getValue();
    assertThat(config.getRancherUrl()).isEqualTo(URL);
    assertThat(config.getEncryptedBearerToken()).isEqualTo(TOKEN_SECRET_ID);
  }
}
