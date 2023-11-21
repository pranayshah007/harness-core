/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.template;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TemplateMetadata {
  @NotNull String templateIdentifier;
  String versionLabel;
  int templateVersionNumber;
  String templateInputs;
  boolean isTemplateByReference;
  long lastReconciliationTime;

  public static TemplateMetadataBuilder fromTemplateDTO(TemplateDTO templateDTO) {
    return TemplateMetadata.builder()
        .templateIdentifier(templateDTO.getTemplateRef())
        .versionLabel(templateDTO.getVersionLabel())
        .templateVersionNumber(templateDTO.getTemplateVersionNumber())
        .templateInputs(templateDTO.getTemplateInputs())
        .isTemplateByReference(templateDTO.isTemplateByReference());
  }
}
