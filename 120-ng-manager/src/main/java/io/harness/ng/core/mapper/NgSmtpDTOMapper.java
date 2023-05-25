/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.mapper;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.SettingAttribute.SettingCategory.CONNECTOR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.NgSmtpDTO;
import io.harness.ng.core.dto.SmtpConfigDTO;

import software.wings.beans.SettingAttribute;
import software.wings.helpers.ext.mail.SmtpConfig;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Singleton;

@OwnedBy(PL)
@Singleton
public class NgSmtpDTOMapper {
  static final String NG_SMTP_SETTINGS_PREFIX = "ngSmtpConfig-";
  public static SettingAttribute getSettingAttributeFromNgSmtpDTO(NgSmtpDTO dto) {
    SmtpConfig smtpConfig = new SmtpConfig();
    smtpConfig.setHost(dto.getValue().getHost());
    smtpConfig.setPort(dto.getValue().getPort());
    smtpConfig.setType(SettingVariableTypes.SMTP.name());
    smtpConfig.setFromAddress(dto.getValue().getFromAddress());
    smtpConfig.setUseSSL(dto.getValue().isUseSSL());
    smtpConfig.setStartTLS(dto.getValue().isStartTLS());
    smtpConfig.setUsername(dto.getValue().getUsername());
    smtpConfig.setPassword(dto.getValue().getPassword());
    if (isNotEmpty(dto.getValue().getDelegateSelectors())) {
      smtpConfig.setDelegateSelectors(dto.getValue().getDelegateSelectors());
    }
    SettingAttribute settingAttribute = new SettingAttribute();
    settingAttribute.setName(NG_SMTP_SETTINGS_PREFIX + dto.getName());
    settingAttribute.setUuid(dto.getUuid());
    settingAttribute.setAccountId(dto.getAccountId());
    settingAttribute.setValue(smtpConfig);
    settingAttribute.setCategory(CONNECTOR);
    return settingAttribute;
  }
  public static NgSmtpDTO getDTOFromSettingAttribute(SettingAttribute settingAttribute) {
    SmtpConfig smtpConfig = (SmtpConfig) settingAttribute.getValue();
    SmtpConfigDTO smtpConfigDTO = new SmtpConfigDTO();
    smtpConfigDTO.setHost(smtpConfig.getHost());
    smtpConfigDTO.setPort(smtpConfig.getPort());
    smtpConfigDTO.setFromAddress(smtpConfig.getFromAddress());
    smtpConfigDTO.setUseSSL(smtpConfig.isUseSSL());
    smtpConfigDTO.setStartTLS(smtpConfig.isStartTLS());
    smtpConfigDTO.setUsername(smtpConfig.getUsername());
    smtpConfigDTO.setPassword(smtpConfig.getPassword());
    if (isNotEmpty(smtpConfig.getDelegateSelectors())) {
      smtpConfigDTO.setDelegateSelectors(smtpConfig.getDelegateSelectors());
    }
    return NgSmtpDTO.builder()
        .uuid(settingAttribute.getUuid())
        .accountId(settingAttribute.getAccountId())
        .name(settingAttribute.getName().substring(13))
        .value(smtpConfigDTO)
        .build();
  }
}
