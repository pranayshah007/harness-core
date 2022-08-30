/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.NameAccess;

import software.wings.beans.entityinterface.ApplicationAccess;
import software.wings.beans.entityinterface.TagAware;
import software.wings.beans.shellscript.provisioner.ShellScriptInfrastructureProvisioner;
import software.wings.yaml.BaseEntityYaml;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.Valid;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;

@OwnedBy(CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TargetModule(HarnessModule._957_CG_BEANS)
@JsonTypeInfo(use = Id.NAME, property = "infrastructureProvisionerType")
@JsonSubTypes({
  @Type(value = TerraformInfrastructureProvisioner.class, name = "TERRAFORM")
  , @Type(value = ShellScriptInfrastructureProvisioner.class, name = "SHELL_SCRIPT"),
      @Type(value = CloudFormationInfrastructureProvisioner.class, name = "CLOUD_FORMATION"),
      @Type(value = ARMInfrastructureProvisioner.class, name = "ARM"),
      @Type(value = TerragruntInfrastructureProvisioner.class, name = "TERRAGRUNT")
})
@StoreIn(DbAliases.HARNESS)
@Entity(value = "infrastructureProvisioner")
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "InfrastructureProvisionerKeys")
public abstract class InfrastructureProvisioner
    extends Base implements NameAccess, TagAware, AccountAccess, ApplicationAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("accountIdCreatedAtIdx")
                 .field(InfrastructureProvisionerKeys.accountId)
                 .descSortField(InfrastructureProvisionerKeys.createdAt)
                 .build())
        .build();
  }

  public static final String INFRASTRUCTURE_PROVISIONER_TYPE_KEY = "infrastructureProvisionerType";
  public static final String MAPPING_BLUEPRINTS_KEY = "mappingBlueprints";
  public static final String NAME_KEY = "name";
  public static final String ID = "_id";
  public static final String APP_ID = "appId";

  @NotEmpty @Trimmed private String name;
  private String description;
  @NotEmpty private String infrastructureProvisionerType;
  private List<NameValuePair> variables;
  @Valid List<InfrastructureMappingBlueprint> mappingBlueprints;
  @FdIndex private String accountId;
  private transient List<HarnessTagLink> tagLinks;

  public InfrastructureProvisioner(String name, String description, String infrastructureProvisionerType,
      List<NameValuePair> variables, List<InfrastructureMappingBlueprint> mappingBlueprints, String accountId,
      String uuid, String appId, EmbeddedUser createdBy, long createdAt, EmbeddedUser lastUpdatedBy, long lastUpdatedAt,
      String entityYamlPath) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath);
    this.name = name;
    this.description = description;
    this.infrastructureProvisionerType = infrastructureProvisionerType;
    this.variables = variables;
    this.mappingBlueprints = mappingBlueprints;
    this.accountId = accountId;
  }
  public abstract String variableKey();

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public abstract static class InfraProvisionerYaml extends BaseEntityYaml {
    private String description;
    private String infrastructureProvisionerType;
    private List<NameValuePair.Yaml> variables;
    private List<InfrastructureMappingBlueprint.Yaml> mappingBlueprints;

    public InfraProvisionerYaml(String type, String harnessApiVersion, String description,
        String infrastructureProvisionerType, List<NameValuePair.Yaml> variables,
        List<InfrastructureMappingBlueprint.Yaml> mappingBlueprints) {
      super(type, harnessApiVersion);
      this.description = description;
      this.infrastructureProvisionerType = infrastructureProvisionerType;
      this.mappingBlueprints = mappingBlueprints;
    }
  }

  @UtilityClass
  public static final class InfrastructureProvisionerKeys {
    // Temporary
    public static final String createdAt = "createdAt";
    public static final String uuid = "uuid";
    public static final String name = "name";
    public static final String appId = "appId";
    public static final String accountId = "accountId";
  }
}
