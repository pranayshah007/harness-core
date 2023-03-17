/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.dto;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import software.wings.beans.entityinterface.ApplicationAccess;

import com.amazonaws.services.ec2.model.Instance;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

/**
 * The Class Host.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "HostKeys")
@OwnedBy(CDP)
@Data
@Builder
public class Host implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, ApplicationAccess {
  // Pulled out of Base
  private String uuid;
  protected String appId;
  private long createdAt;
  private long lastUpdatedAt;

  private String envId;
  private String serviceTemplateId;
  private String infraMappingId;
  private String infraDefinitionId;
  private String computeProviderId;
  private String hostName;
  // In the case of EC2, publicDns could be either the public or private DNS name, depending on the setting in AWS_SSH
  // infrastructure mapping.
  private String publicDns;
  private String hostConnAttr;
  private String bastionConnAttr;
  private String winrmConnAttr;
  private Map<String, Object> properties;
  private Instance ec2Instance;
}
