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
import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.api.InstanceElement.Builder.anInstanceElement;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.context.ContextElementType;
import io.harness.ecs.EcsContainerDetails;

import software.wings.sm.ContextElement;
import software.wings.sm.VerificationElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * The Class InstanceElement.
 *
 * @author Rishi
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CDP)
@TargetModule(_957_CG_BEANS)
public class InstanceElement implements ContextElement, VerificationElement {
  private String uuid;
  private String displayName;
  private String hostName;
  private String dockerId;
  private HostElement host;
  private ServiceTemplateElement serviceTemplateElement;
  private String podName;
  @Getter @Setter private String namespace;
  private String workloadName;
  private EcsContainerDetails ecsContainerDetails;
  private boolean newInstance;

  @Override
  public String getName() {
    return displayName;
  }

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.INSTANCE;
  }

  public EcsContainerDetails getEcsContainerDetails() {
    return ecsContainerDetails;
  }

  public void setEcsContainerDetails(EcsContainerDetails ecsContainerDetails) {
    this.ecsContainerDetails = ecsContainerDetails;
  }

  @Override
  public String getUuid() {
    return uuid;
  }

  /**
   * Sets uuid.
   *
   * @param uuid the uuid
   */
  public void setUuid(String uuid) {
    this.uuid = uuid;
  }

  /**
   * Gets display name.
   *
   * @return the display name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Sets display name.
   *
   * @param displayName the display name
   */
  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getHostName() {
    return hostName;
  }

  public void setHostName(String hostName) {
    this.hostName = hostName;
  }

  public String getDockerId() {
    return dockerId;
  }

  public void setDockerId(String dockerId) {
    this.dockerId = dockerId;
  }

  public String getPodName() {
    return podName;
  }

  public void setPodName(String podName) {
    this.podName = podName;
  }

  public String getWorkloadName() {
    return workloadName;
  }

  public void setWorkloadName(String workloadName) {
    this.workloadName = workloadName;
  }

  @Override
  public boolean isNewInstance() {
    return newInstance;
  }

  public void setNewInstance(boolean newInstance) {
    this.newInstance = newInstance;
  }

  /**
   * Gets host element.
   *
   * @return the host element
   */
  public HostElement getHost() {
    return host;
  }

  /**
   * Sets host element.
   *
   * @param hostElement the host element
   */
  public void setHost(HostElement hostElement) {
    this.host = hostElement;
  }

  /**
   * Gets service template element.
   *
   * @return the service template element
   */
  public ServiceTemplateElement getServiceTemplateElement() {
    return serviceTemplateElement;
  }

  /**
   * Sets service template element.
   *
   * @param serviceTemplateElement the service template element
   */
  public void setServiceTemplateElement(ServiceTemplateElement serviceTemplateElement) {
    this.serviceTemplateElement = serviceTemplateElement;
  }

  @Override
  public ContextElement cloneMin() {
    InstanceElement instanceElement =
        anInstanceElement().uuid(uuid).displayName(displayName).hostName(hostName).build();
    if (host != null) {
      instanceElement.setHost((HostElement) host.cloneMin());
    }
    return instanceElement;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    InstanceElement that = (InstanceElement) o;
    return Objects.equal(getUuid(), that.getUuid()) && Objects.equal(getDisplayName(), that.getDisplayName())
        && Objects.equal(getHostName(), that.getHostName()) && Objects.equal(getDockerId(), that.getDockerId())
        && Objects.equal(getHost(), that.getHost())
        && Objects.equal(getServiceTemplateElement(), that.getServiceTemplateElement())
        && Objects.equal(getPodName(), that.getPodName()) && Objects.equal(getWorkloadName(), that.getWorkloadName());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getUuid(), getDisplayName(), getHostName(), getDockerId(), getHost(),
        getServiceTemplateElement(), getPodName(), getWorkloadName());
  }

  @Override
  public String toString() {
    return "InstanceElement{"
        + "uuid='" + uuid + '\'' + ", displayName='" + displayName + '\'' + ", hostName='" + hostName + '\''
        + ", hostElement=" + host + ", serviceTemplateElement=" + serviceTemplateElement + ", podName=" + podName
        + ", workloadName=" + workloadName + '}';
  }

  public static final class Builder {
    private String uuid;
    private String displayName;
    private String hostName;
    private String dockerId;
    private HostElement host;
    private ServiceTemplateElement serviceTemplateElement;
    private String podName;
    private String namespace;
    private String workloadName;
    private EcsContainerDetails ecsContainerDetails;
    private boolean newInstance;

    private Builder() {}

    public static Builder anInstanceElement() {
      return new Builder();
    }

    public Builder ecsContainerDetails(EcsContainerDetails ecsContainerDetails) {
      this.ecsContainerDetails = ecsContainerDetails;
      return this;
    }

    public Builder uuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    public Builder displayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    public Builder hostName(String hostName) {
      this.hostName = hostName;
      return this;
    }

    public Builder dockerId(String dockerId) {
      this.dockerId = dockerId;
      return this;
    }

    public Builder host(HostElement host) {
      this.host = host;
      return this;
    }

    public Builder serviceTemplateElement(ServiceTemplateElement serviceTemplateElement) {
      this.serviceTemplateElement = serviceTemplateElement;
      return this;
    }

    public Builder podName(String podName) {
      this.podName = podName;
      return this;
    }

    public Builder namespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    public Builder workloadName(String workloadName) {
      this.workloadName = workloadName;
      return this;
    }

    public Builder newInstance(boolean newInstance) {
      this.newInstance = newInstance;
      return this;
    }

    public Builder but() {
      return anInstanceElement()
          .uuid(uuid)
          .displayName(displayName)
          .hostName(hostName)
          .dockerId(dockerId)
          .host(host)
          .serviceTemplateElement(serviceTemplateElement)
          .podName(podName)
          .namespace(namespace)
          .workloadName(workloadName)
          .ecsContainerDetails(ecsContainerDetails)
          .newInstance(newInstance);
    }

    public InstanceElement build() {
      InstanceElement instanceElement = new InstanceElement();
      instanceElement.setUuid(uuid);
      instanceElement.setDisplayName(displayName);
      instanceElement.setHostName(hostName);
      instanceElement.setDockerId(dockerId);
      instanceElement.setHost(host);
      instanceElement.setServiceTemplateElement(serviceTemplateElement);
      instanceElement.setPodName(podName);
      instanceElement.setWorkloadName(workloadName);
      instanceElement.setEcsContainerDetails(ecsContainerDetails);
      instanceElement.setNewInstance(newInstance);
      instanceElement.setNamespace(namespace);
      return instanceElement;
    }
  }
}
