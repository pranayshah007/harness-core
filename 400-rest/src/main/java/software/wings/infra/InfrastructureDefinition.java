/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.infra;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.EntityName;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.NameAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;

import software.wings.api.CloudProviderType;
import software.wings.api.DeploymentType;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.entityinterface.ApplicationAccess;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.service.intfc.customdeployment.CustomDeploymentTypeAware;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "InfrastructureDefinitionKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "infrastructureDefinitions", noClassnameStored = true)
@HarnessEntity(exportable = true)
@TargetModule(_957_CG_BEANS)
@OwnedBy(CDP)
public class InfrastructureDefinition
    implements PersistentEntity, UuidAware, NameAccess, CreatedAtAware, CreatedByAware, UpdatedAtAware, UpdatedByAware,
               ApplicationAccess, CustomDeploymentTypeAware, AccountAccess, NGMigrationEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("infraDefinitionIdx")
                 .unique(true)
                 .field(InfrastructureDefinitionKeys.appId)
                 .field(InfrastructureDefinitionKeys.envId)
                 .field(InfrastructureDefinitionKeys.name)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("infrastructure_cloudProviderId")
                 .field(InfrastructureDefinitionKeys.infrastructure + ".cloudProviderId")
                 .build())
        .build();
  }

  @Id private String uuid;
  @SchemaIgnore private EmbeddedUser createdBy;
  @SchemaIgnore private long createdAt;
  @NotEmpty @EntityName private String name;
  @SchemaIgnore private EmbeddedUser lastUpdatedBy;
  @SchemaIgnore @NotNull private long lastUpdatedAt;
  @NotNull protected String appId;
  private String provisionerId;
  @NotNull private CloudProviderType cloudProviderType;
  @NotNull private DeploymentType deploymentType;
  @NotNull private InfraMappingInfrastructureProvider infrastructure;
  private List<String> scopedToServices;
  @NotNull private String envId;
  private boolean sample;
  @FdIndex private String accountId;

  /*
  Support for Custom Deployment
   */
  private String deploymentTypeTemplateId;
  private transient String customDeploymentName;

  @JsonIgnore
  public InfrastructureMapping getInfraMapping() {
    InfrastructureMapping infrastructureMapping = infrastructure.getInfraMapping();
    infrastructureMapping.setAccountId(accountId);
    infrastructureMapping.setAppId(appId);
    infrastructureMapping.setEnvId(envId);
    infrastructureMapping.setDeploymentType(deploymentType.name());
    infrastructureMapping.setComputeProviderType(cloudProviderType.name());
    infrastructureMapping.setProvisionerId(provisionerId);
    infrastructureMapping.setCustomDeploymentTemplateId(deploymentTypeTemplateId);
    return infrastructureMapping;
  }

  public InfrastructureDefinition cloneForUpdate() {
    return InfrastructureDefinition.builder()
        .name(getName())
        .provisionerId(getProvisionerId())
        .cloudProviderType(getCloudProviderType())
        .deploymentType(getDeploymentType())
        .infrastructure(getInfrastructure())
        .scopedToServices(getScopedToServices())
        .accountId(getAccountId())
        .deploymentTypeTemplateId(deploymentTypeTemplateId)
        .customDeploymentName(customDeploymentName)
        .build();
  }

  @Override
  public void setDeploymentTypeName(String theCustomDeploymentName) {
    customDeploymentName = theCustomDeploymentName;
  }

  @JsonIgnore
  @Override
  public String getMigrationEntityName() {
    return getName();
  }
}
