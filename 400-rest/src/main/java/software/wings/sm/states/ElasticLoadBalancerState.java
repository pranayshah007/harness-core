/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import io.harness.beans.ExecutionStatus;
import io.harness.context.ContextElementType;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;

import software.wings.api.ElbStateExecutionData;
import software.wings.api.InstanceElement;
import software.wings.beans.AwsConfig;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.ElasticLoadBalancerConfig;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.PhysicalInfrastructureMappingBase;
import software.wings.beans.SettingAttribute;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.stencils.DefaultValue;

import com.amazonaws.regions.Regions;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import dev.morphia.annotations.Transient;

/**
 * Created by peeyushaggarwal on 10/3/16.
 */
public class ElasticLoadBalancerState extends State {
  @Transient @Inject private transient InfrastructureMappingService infrastructureMappingService;

  @Transient @Inject private transient SettingsService settingsService;

  @Transient @Inject private transient AwsHelperService awsHelperService;

  @Attributes(title = "Operation") private Operation operation;

  @DefaultValue("Instance") @Attributes(title = "Entity") @SchemaIgnore private Entity entity = Entity.Instance;

  @Attributes(title = "Custom Entity") @SchemaIgnore private String custom;

  @Transient @Inject private transient SecretManager secretManager;

  @Transient @Inject private transient ManagerDecryptionService managerDecryptionService;

  public ElasticLoadBalancerState(String name) {
    super(name, StateType.ELASTIC_LOAD_BALANCER.name());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    InfrastructureMapping infrastructureMapping =
        infrastructureMappingService.get(context.getAppId(), context.fetchInfraMappingId());

    String loadBalancerName;
    String region;

    if (infrastructureMapping instanceof AwsInfrastructureMapping) {
      loadBalancerName = ((AwsInfrastructureMapping) infrastructureMapping).getLoadBalancerId();
      region = ((AwsInfrastructureMapping) infrastructureMapping).getRegion();
      SettingAttribute settingAttribute = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
      AwsConfig awsConfig = (AwsConfig) settingAttribute.getValue();
      managerDecryptionService.decrypt(awsConfig,
          secretManager.getEncryptionDetails(awsConfig, context.getAppId(), context.getWorkflowExecutionId()));
      return execute(context, loadBalancerName, Regions.fromName(region), awsConfig);
    } else if (infrastructureMapping instanceof PhysicalInfrastructureMappingBase) {
      SettingAttribute elbSetting =
          settingsService.get(((PhysicalInfrastructureMappingBase) infrastructureMapping).getLoadBalancerId());
      ElasticLoadBalancerConfig loadBalancerConfig = (ElasticLoadBalancerConfig) elbSetting.getValue();
      managerDecryptionService.decrypt(loadBalancerConfig,
          secretManager.getEncryptionDetails(loadBalancerConfig, context.getAppId(), context.getWorkflowExecutionId()));
      loadBalancerName = loadBalancerConfig.getLoadBalancerName();
      region = loadBalancerConfig.getRegion().name();
      AwsConfig awsConfigDerived = AwsConfig.builder()
                                       .accessKey(loadBalancerConfig.getAccessKey().toCharArray())
                                       .secretKey(loadBalancerConfig.getSecretKey())
                                       .useEc2IamCredentials(loadBalancerConfig.isUseEc2IamCredentials())
                                       .crossAccountAttributes(null)
                                       .build();
      return execute(context, loadBalancerName, Regions.valueOf(region), awsConfigDerived);
    } else {
      throw new InvalidRequestException("ELB operations not supported");
    }
  }

  public ExecutionResponse execute(
      ExecutionContext context, String loadBalancerName, Regions region, AwsConfig awsConfig) {
    ExecutionStatus status;

    InstanceElement instance = context.getContextElement(ContextElementType.INSTANCE);
    final String instanceId = instance.getHost().getInstanceId() != null
        ? instance.getHost().getInstanceId()
        : awsHelperService.getInstanceId(region, instance.getHost().getHostName(), awsConfig);

    String errorMessage = "";

    try {
      boolean result = operation == Operation.Enable
          ? awsHelperService.registerInstancesWithLoadBalancer(region, loadBalancerName, instanceId, awsConfig)
          : awsHelperService.deregisterInstancesFromLoadBalancer(region, loadBalancerName, instanceId, awsConfig);
      status = result ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED;
    } catch (Exception e) {
      status = ExecutionStatus.ERROR;
      errorMessage = ExceptionUtils.getMessage(e);
    }

    return ExecutionResponse.builder()
        .stateExecutionData(ElbStateExecutionData.builder().hostName(instance.getHost().getHostName()).build())
        .executionStatus(status)
        .errorMessage(errorMessage)
        .build();
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  /**
   * Getter for property 'operation'.
   *
   * @return Value for property 'operation'.
   */
  public Operation getOperation() {
    return operation;
  }

  /**
   * Setter for property 'operation'.
   *
   * @param operation Value to set for property 'operation'.
   */
  public void setOperation(Operation operation) {
    this.operation = operation;
  }

  public String getCustom() {
    return custom;
  }

  public void setCustom(String custom) {
    this.custom = custom;
  }

  /**
   * Getter for property 'entity'.
   *
   * @return Value for property 'entity'.
   */
  public Entity getEntity() {
    return entity;
  }

  /**
   * Setter for property 'entity'.
   *
   * @param entity Value to set for property 'entity'.
   */
  public void setEntity(Entity entity) {
    this.entity = entity;
  }

  public enum Operation { Enable, Disable }

  @Override
  @SchemaIgnore
  public ContextElementType getRequiredContextElementType() {
    return ContextElementType.INSTANCE;
  }

  private enum Entity {
    Instance("${instance}"),
    Custom("");

    private String expression;

    Entity(String expression) {
      this.expression = expression;
    }

    public String getExpression() {
      return expression;
    }
  }
}
