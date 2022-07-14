/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.beans.YamlDTO;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@Getter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(DEL)
@Slf4j
public class DelegateDTO implements YamlDTO {
  private String accountId;
  private String delegateId;
  private String delegateName;
  // will this break the existing automations??
  private String delegateProfileId;

  List<String> tags;

  public static DelegateDTO convertToDTO(Delegate delegate, List<String> implicitTags) {
    List<String> delegateTags = new ArrayList<>();
    if (isNotEmpty(delegate.getTags())) {
      delegateTags.addAll(delegate.getTags());
    }
    if (isNotEmpty(implicitTags)) {
      delegateTags.addAll(implicitTags);
    }

    return DelegateDTO.builder()
        .accountId(delegate.getAccountId())
        .delegateId(delegate.getUuid())
        .delegateName(delegate.getDelegateName())
        .delegateProfileId(delegate.getDelegateProfileId())
        .tags(delegateTags)
        .build();
  }
}
