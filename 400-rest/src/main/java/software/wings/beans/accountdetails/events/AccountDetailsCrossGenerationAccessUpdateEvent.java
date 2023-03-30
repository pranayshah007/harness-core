/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.accountdetails.events;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import static software.wings.beans.accountdetails.AccountDetailsConstants.CROSS_GENERATION_ACCESS_UPDATED;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@OwnedBy(GTM)
@Getter
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountDetailsCrossGenerationAccessUpdateEvent extends AccountDetailsAbstractEvent {
  private CrossGenerationAccessYamlDTO oldCrossGenerationAccessYamlDTO;
  private CrossGenerationAccessYamlDTO newCrossGenerationAccessYamlDTO;

  @Override
  public String getEventType() {
    return CROSS_GENERATION_ACCESS_UPDATED;
  }
}
