/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.container;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.yaml.YamlHelper.trimYaml;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.persistence.AccountAccess;

import software.wings.beans.DeploymentSpecification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;

/**
 * Created by anubhaw on 2/6/17.
 */
@OwnedBy(CDP)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "deploymentType")
@Entity("containerTasks")
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "ContainerTaskKeys")
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public abstract class ContainerTask extends DeploymentSpecification implements AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("service")
                 .unique(true)
                 .field(ContainerTaskKeys.serviceId)
                 .field(ContainerTaskKeys.deploymentType)
                 .build())
        .build();
  }

  @NotEmpty private String deploymentType;
  @SchemaIgnore @NotEmpty private String serviceId;

  private String advancedConfig;

  private List<ContainerDefinition> containerDefinitions;

  public ContainerTask(String deploymentType) {
    this.deploymentType = deploymentType;
  }

  public List<ContainerDefinition> getContainerDefinitions() {
    return containerDefinitions;
  }

  public void setContainerDefinitions(List<ContainerDefinition> containerDefinitions) {
    this.containerDefinitions = containerDefinitions;
  }

  public String getDeploymentType() {
    return deploymentType;
  }

  public String getServiceId() {
    return serviceId;
  }

  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  public String getAdvancedConfig() {
    return advancedConfig;
  }

  public void setAdvancedConfig(String advancedConfig) {
    this.advancedConfig = trimYaml(advancedConfig);
  }

  @SchemaIgnore
  @Override
  public String getAppId() {
    return super.getAppId();
  }

  @SchemaIgnore
  @Override
  public EmbeddedUser getCreatedBy() {
    return super.getCreatedBy();
  }

  @SchemaIgnore
  @Override
  public EmbeddedUser getLastUpdatedBy() {
    return super.getLastUpdatedBy();
  }

  @SchemaIgnore
  @Override
  public long getCreatedAt() {
    return super.getCreatedAt();
  }

  @SchemaIgnore
  @Override
  public long getLastUpdatedAt() {
    return super.getLastUpdatedAt();
  }

  @SchemaIgnore
  @Override
  public String getUuid() {
    return super.getUuid();
  }

  public abstract ContainerTask convertToAdvanced();

  public abstract ContainerTask convertFromAdvanced();

  public abstract void validateAdvanced();

  protected void copyConfigToContainerTask(ContainerTask newContainerTask) {
    newContainerTask.setAdvancedConfig(this.getAdvancedConfig());
    newContainerTask.setContainerDefinitions(this.getContainerDefinitions());
    newContainerTask.setServiceId(this.getServiceId());
    newContainerTask.setAccountId(this.getAccountId());
    newContainerTask.setAppId(this.getAppId());
  }

  public ContainerTask cloneInternal() {
    throw new UnsupportedOperationException();
  }

  public abstract void validate();

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public abstract static class Yaml extends DeploymentSpecification.Yaml {
    private String advancedConfig;
    private ContainerDefinition.Yaml containerDefinition;

    protected Yaml(
        String type, String harnessApiVersion, String advancedConfig, ContainerDefinition.Yaml containerDefinition) {
      super(type, harnessApiVersion);
      this.advancedConfig = advancedConfig;
      this.containerDefinition = containerDefinition;
    }
  }
}
