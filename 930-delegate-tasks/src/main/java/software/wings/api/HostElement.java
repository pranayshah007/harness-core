/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/**
 *
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;

import io.harness.annotations.dev.TargetModule;
import io.harness.context.ContextElementType;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.response.AzureVMInstanceData;

import software.wings.sm.ContextElement;

import com.amazonaws.services.ec2.model.Instance;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * The Class HostElement.
 *
 * @author Rishi
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@TargetModule(_957_CG_BEANS)
public class HostElement implements ContextElement {
  private String uuid;
  private String hostName;
  private String ip;
  private String instanceId;
  private String publicDns;
  private Map<String, Object> properties;
  private Instance ec2Instance;
  private PcfInstanceElement pcfElement;
  private AzureVMInstanceData azureVMInstance;
  private AzureAppDeploymentData webAppInstance;

  @Override
  public String getName() {
    return hostName;
  }

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.HOST;
  }

  @Override
  public ContextElement cloneMin() {
    return HostElement.builder()
        .uuid(uuid)
        .hostName(hostName)
        .ip(ip)
        .publicDns(publicDns)
        .instanceId(instanceId)
        //        .withEc2Instance(ec2Instance)
        .build();
  }
}
