/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/**
 *
 */

package software.wings.api;

import software.wings.beans.ExecutionStrategy;

import java.util.List;

/**
 * The type Simple workflow param.
 *
 * @author Rishi
 */
public class SimpleWorkflowParam extends ServiceInstanceIdsParam {
  private ExecutionStrategy executionStrategy;
  private String commandName;

  /**
   * Gets execution strategy.
   *
   * @return the execution strategy
   */
  public ExecutionStrategy getExecutionStrategy() {
    return executionStrategy;
  }

  /**
   * Sets execution strategy.
   *
   * @param executionStrategy the execution strategy
   */
  public void setExecutionStrategy(ExecutionStrategy executionStrategy) {
    this.executionStrategy = executionStrategy;
  }

  /**
   * Gets command name.
   *
   * @return the command name
   */
  public String getCommandName() {
    return commandName;
  }

  /**
   * Sets command name.
   *
   * @param commandName the command name
   */
  public void setCommandName(String commandName) {
    this.commandName = commandName;
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private ExecutionStrategy executionStrategy;
    private String serviceId;
    private String commandName;
    private List<String> instanceIds;

    private Builder() {}

    /**
     * A simple workflow param builder.
     *
     * @return the builder
     */
    public static Builder aSimpleWorkflowParam() {
      return new Builder();
    }

    /**
     * With execution strategy builder.
     *
     * @param executionStrategy the execution strategy
     * @return the builder
     */
    public Builder withExecutionStrategy(ExecutionStrategy executionStrategy) {
      this.executionStrategy = executionStrategy;
      return this;
    }

    /**
     * With service id builder.
     *
     * @param serviceId the service id
     * @return the builder
     */
    public Builder withServiceId(String serviceId) {
      this.serviceId = serviceId;
      return this;
    }

    /**
     * With command name builder.
     *
     * @param commandName the command name
     * @return the builder
     */
    public Builder withCommandName(String commandName) {
      this.commandName = commandName;
      return this;
    }

    /**
     * With instance ids builder.
     *
     * @param instanceIds the instance ids
     * @return the builder
     */
    public Builder withInstanceIds(List<String> instanceIds) {
      this.instanceIds = instanceIds;
      return this;
    }

    /**
     * But builder.
     *
     * @return the builder
     */
    public Builder but() {
      return aSimpleWorkflowParam()
          .withExecutionStrategy(executionStrategy)
          .withServiceId(serviceId)
          .withCommandName(commandName)
          .withInstanceIds(instanceIds);
    }

    /**
     * Build simple workflow param.
     *
     * @return the simple workflow param
     */
    public SimpleWorkflowParam build() {
      SimpleWorkflowParam simpleWorkflowParam = new SimpleWorkflowParam();
      simpleWorkflowParam.setExecutionStrategy(executionStrategy);
      simpleWorkflowParam.setServiceId(serviceId);
      simpleWorkflowParam.setCommandName(commandName);
      simpleWorkflowParam.setInstanceIds(instanceIds);
      return simpleWorkflowParam;
    }
  }
}
