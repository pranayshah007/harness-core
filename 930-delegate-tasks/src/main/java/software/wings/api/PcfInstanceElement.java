/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.context.ContextElementType;

import software.wings.sm.ContextElement;
import software.wings.sm.VerificationElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

/**
 * This class holds information for verification step.
 * any instance has unique application_Guid + InstanceIndex combination, by which it can be accessed.
 * e.g. curl myapp.private-domain.example.com -H "X-Cf-App-Instance: 5cdc7595-2e9b-4f62-8d5a-a86b92f2df0e:9"
 * where 5cdc7595-2e9b-4f62-8d5a-a86b92f2df0e is appGuid and 9 is instanceIndex
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "PcfInstanceElementKeys")
@OwnedBy(CDP)
@TargetModule(_957_CG_BEANS)
public class PcfInstanceElement implements ContextElement, VerificationElement {
  private String uuid;
  private String applicationId;
  private String instanceIndex;
  private String displayName;
  private boolean isUpsize;

  @Override
  public String getName() {
    return displayName;
  }

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.PCF_INSTANCE;
  }

  @Override
  public String getUuid() {
    return uuid;
  }

  @Override
  public ContextElement cloneMin() {
    return null;
  }

  @Override
  public boolean isNewInstance() {
    return isUpsize;
  }
}
