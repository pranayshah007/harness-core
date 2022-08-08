/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.cloudprovider.aws;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.util.List;

/**
 * Created by peeyushaggarwal on 3/14/17.
 */
public class TaskMetadata {
  @JsonProperty("Tasks") private List<Task> tasks;

  /**
   * Getter for property 'tasks'.
   *
   * @return Value for property 'tasks'.
   */
  public List<Task> getTasks() {
    return tasks;
  }

  /**
   * Setter for property 'tasks'.
   *
   * @param tasks Value to set for property 'tasks'.
   */
  public void setTasks(List<Task> tasks) {
    this.tasks = tasks;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("tasks", tasks).toString();
  }

  public static class Task {
    @JsonProperty("Arn") private String arn;
    @JsonProperty("DesiredStatus") private String desiredStatus;
    @JsonProperty("Family") private String family;
    @JsonProperty("KnownStatus") private String knownStatus;
    @JsonProperty("Version") private String version;
    @JsonProperty("Containers") private List<Container> containers;

    /**
     * Getter for property 'arn'.
     *
     * @return Value for property 'arn'.
     */
    public String getArn() {
      return arn;
    }

    /**
     * Setter for property 'arn'.
     *
     * @param arn Value to set for property 'arn'.
     */
    public void setArn(String arn) {
      this.arn = arn;
    }

    /**
     * Getter for property 'desiredStatus'.
     *
     * @return Value for property 'desiredStatus'.
     */
    public String getDesiredStatus() {
      return desiredStatus;
    }

    /**
     * Setter for property 'desiredStatus'.
     *
     * @param desiredStatus Value to set for property 'desiredStatus'.
     */
    public void setDesiredStatus(String desiredStatus) {
      this.desiredStatus = desiredStatus;
    }

    /**
     * Getter for property 'family'.
     *
     * @return Value for property 'family'.
     */
    public String getFamily() {
      return family;
    }

    /**
     * Setter for property 'family'.
     *
     * @param family Value to set for property 'family'.
     */
    public void setFamily(String family) {
      this.family = family;
    }

    /**
     * Getter for property 'knownStatus'.
     *
     * @return Value for property 'knownStatus'.
     */
    public String getKnownStatus() {
      return knownStatus;
    }

    /**
     * Setter for property 'knownStatus'.
     *
     * @param knownStatus Value to set for property 'knownStatus'.
     */
    public void setKnownStatus(String knownStatus) {
      this.knownStatus = knownStatus;
    }

    /**
     * Getter for property 'version'.
     *
     * @return Value for property 'version'.
     */
    public String getVersion() {
      return version;
    }

    /**
     * Setter for property 'version'.
     *
     * @param version Value to set for property 'version'.
     */
    public void setVersion(String version) {
      this.version = version;
    }

    /**
     * Getter for property 'containers'.
     *
     * @return Value for property 'containers'.
     */
    public List<Container> getContainers() {
      return containers;
    }

    /**
     * Setter for property 'containers'.
     *
     * @param containers Value to set for property 'containers'.
     */
    public void setContainers(List<Container> containers) {
      this.containers = containers;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("arn", arn)
          .add("desiredStatus", desiredStatus)
          .add("family", family)
          .add("knownStatus", knownStatus)
          .add("version", version)
          .add("containers", containers)
          .toString();
    }
  }

  public static class Container {
    @JsonProperty("DockerId") private String dockerId;
    @JsonProperty("DockerName") private String dockerName;
    @JsonProperty("Name") private String name;
    @JsonProperty("Networks") private List<Network> networks;

    /**
     * Getter for property 'dockerId'.
     *
     * @return Value for property 'dockerId'.
     */
    public String getDockerId() {
      return dockerId;
    }

    /**
     * Setter for property 'dockerId'.
     *
     * @param dockerId Value to set for property 'dockerId'.
     */
    public void setDockerId(String dockerId) {
      this.dockerId = dockerId;
    }

    /**
     * Getter for property 'dockerName'.
     *
     * @return Value for property 'dockerName'.
     */
    public String getDockerName() {
      return dockerName;
    }

    /**
     * Setter for property 'dockerName'.
     *
     * @param dockerName Value to set for property 'dockerName'.
     */
    public void setDockerName(String dockerName) {
      this.dockerName = dockerName;
    }

    /**
     * Getter for property 'name'.
     *
     * @return Value for property 'name'.
     */
    public String getName() {
      return name;
    }

    /**
     * Setter for property 'name'.
     *
     * @param name Value to set for property 'name'.
     */
    public void setName(String name) {
      this.name = name;
    }

    public List<Network> getNetworks() {
      return networks;
    }

    public void setNetworks(List<Network> networks) {
      this.networks = networks;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("dockerId", dockerId)
          .add("dockerName", dockerName)
          .add("name", name)
          .add("netWorks", networks)
          .toString();
    }
  }

  public static class Network {
    @JsonProperty("NetworkMode") private String NetworkMode;
    @JsonProperty("IPv4Addresses") private List<String> IPv4Addresses;

    public String getNetworkMode() {
      return NetworkMode;
    }

    public void setNetworkMode(String networkMode) {
      NetworkMode = networkMode;
    }

    public List<String> getIPv4Addresses() {
      return IPv4Addresses;
    }

    public void setIPv4Addresses(List<String> IPv4Addresses) {
      this.IPv4Addresses = IPv4Addresses;
    }

    @Override
    public String toString() {
      return "Network{"
          + "NetworkMode='" + NetworkMode + '\'' + ", IPv4Addresses=" + IPv4Addresses + '}';
    }
  }
}
