/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.mapper;

import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.NgSmtpDTO;
import io.harness.ng.core.dto.SmtpConfigDTO;
import io.harness.rule.Owner;

import software.wings.beans.SettingAttribute;
import software.wings.helpers.ext.mail.SmtpConfig;

import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NgSmtpDTOMapperTest extends CategoryTest {
  private String ACCOUNT_ID = "accountId";
  private String Host = "host";
  private String USER_NAME = "userName";

  @Before
  public void setup() {}

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testNgSmtpDtoToSettingAttribute() {
    NgSmtpDTO ngSmtpDTO =
        NgSmtpDTO.builder()
            .accountId(ACCOUNT_ID)
            .value(SmtpConfigDTO.builder().host(Host).username(USER_NAME).delegateSelectors(Set.of("delegate")).build())
            .build();
    SettingAttribute settingAttribute = NgSmtpDTOMapper.getSettingAttributeFromNgSmtpDTO(ngSmtpDTO);
    assertThat(settingAttribute.getAccountId()).isEqualTo(ACCOUNT_ID);
    SmtpConfig smtpConfig = (SmtpConfig) settingAttribute.getValue();
    assertThat(smtpConfig.getHost()).isEqualTo(Host);
    assertThat(smtpConfig.getDelegateSelectors()).isNotNull();
    assertThat(smtpConfig.getDelegateSelectors().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void test_getDTOFromSettingAttribute() {
    SettingAttribute settingAttribute = new SettingAttribute();
    settingAttribute.setAccountId(ACCOUNT_ID);
    settingAttribute.setName("settingAttribute");
    settingAttribute.setValue(
        SmtpConfig.builder().host(Host).username(USER_NAME).delegateSelectors(Set.of("delegate")).build());
    NgSmtpDTO ngSmtpDTO = NgSmtpDTOMapper.getDTOFromSettingAttribute(settingAttribute);
    assertThat(ngSmtpDTO.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(ngSmtpDTO.getValue()).isNotNull();
    assertThat(ngSmtpDTO.getValue().getHost()).isEqualTo(Host);
    assertThat(ngSmtpDTO.getValue().getDelegateSelectors()).isNotNull();
    assertThat(ngSmtpDTO.getValue().getDelegateSelectors().size()).isEqualTo(1);
  }
}
