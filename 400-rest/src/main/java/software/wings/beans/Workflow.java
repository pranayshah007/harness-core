/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/**
 *
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.ngmigration.NGMigrationEntityType.WORKFLOW;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.WorkflowType;
import io.harness.data.validator.EntityName;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.NameAccess;

import software.wings.api.DeploymentType;
import software.wings.beans.entityinterface.ApplicationAccess;
import software.wings.beans.entityinterface.KeywordsAware;
import software.wings.beans.entityinterface.TagAware;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.service.impl.workflow.WorkflowServiceTemplateHelper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Transient;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;

/**
 * The Class Workflow.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "workflows", noClassnameStored = true)
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "WorkflowKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
@TargetModule(HarnessModule._957_CG_BEANS)
public class Workflow
    extends Base implements KeywordsAware, NameAccess, TagAware, AccountAccess, ApplicationAccess, NGMigrationEntity {
  public static String RBAC_INDEX_2 = "rbac_index_2";
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("accountIdCreatedAt")
                 .field(WorkflowKeys.accountId)
                 .descSortField(WorkflowKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("account_orchestrationWorkflowTypeIndex")
                 .field(WorkflowKeys.accountId)
                 .field(WorkflowKeys.orchestrationWorkflowType)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_name")
                 .field(WorkflowKeys.accountId)
                 .ascSortField(WorkflowKeys.name)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("appId_name")
                 .field(WorkflowKeys.appId)
                 .ascSortField(WorkflowKeys.name)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name(RBAC_INDEX_2)
                 .field(WorkflowKeys.accountId)
                 .field("_id")
                 .field(WorkflowKeys.appId)
                 .field(WorkflowKeys.envId)
                 .field(WorkflowKeys.templatized)
                 .field(WorkflowKeys.templateExpressions)
                 .build())
        .build();
  }

  public static final String NAME_KEY = "name";
  public static final String ORCHESTRATION_KEY = "orchestration";

  @NotNull @EntityName private String name;

  private String description;

  private WorkflowType workflowType;

  private String envId;

  private Integer defaultVersion;

  private boolean templatized;

  private List<TemplateExpression> templateExpressions;

  @Getter @Setter private Set<String> keywords;

  @Transient private String notes;

  @Getter @Setter private OrchestrationWorkflow orchestration;

  @Transient private OrchestrationWorkflow orchestrationWorkflow;

  @Transient private List<Service> services = new ArrayList<>();
  @Transient private List<WorkflowExecution> workflowExecutions = new ArrayList<>();

  // Only for UI payload to support BasicOrchestration workflow {{
  @Transient private String serviceId;
  @Transient private String infraMappingId;
  @Transient private String infraDefinitionId;
  @Transient @Getter @Setter private WorkflowCreationFlags creationFlags;
  // }}

  @Transient @Getter @Setter private boolean envTemplatized;

  @Getter @Setter private transient List<HarnessTagLink> tagLinks;

  private transient List<String> templatizedServiceIds = new ArrayList<>();

  @FdIndex private List<String> linkedTemplateUuids = new ArrayList<>();

  @FdIndex private List<String> linkedArtifactStreamIds = new ArrayList<>();

  @Getter @Setter private transient List<DeploymentType> deploymentTypes = new ArrayList<>();
  private String accountId;
  private boolean sample;

  @Override
  public boolean equals(Object o) {
    return super.equals(o) && true;
  }

  public List<String> getLinkedTemplateUuids() {
    return linkedTemplateUuids;
  }

  public void setLinkedTemplateUuids(List<String> linkedTemplateUuids) {
    this.linkedTemplateUuids = linkedTemplateUuids;
  }

  public List<String> getLinkedArtifactStreamIds() {
    return linkedArtifactStreamIds;
  }

  public void setLinkedArtifactStreamIds(List<String> linkedArtifactStreamIds) {
    this.linkedArtifactStreamIds = linkedArtifactStreamIds;
  }

  /**
   * Get Templatized ServiceIds
   * @return
   */
  public List<String> getTemplatizedServiceIds() {
    return templatizedServiceIds;
  }

  /**
   * Set templatized serviceids
   * @param templatizedServiceIds
   */
  public void setTemplatizedServiceIds(List<String> templatizedServiceIds) {
    this.templatizedServiceIds = templatizedServiceIds;
  }

  /**
   * Gets name.
   *
   * @return the name
   */
  @Override
  public String getName() {
    return name;
  }

  /**
   * Sets name.
   *
   * @param name the name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets description.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  public WorkflowType getWorkflowType() {
    return workflowType;
  }

  public void setWorkflowType(WorkflowType workflowType) {
    this.workflowType = workflowType;
  }

  /**
   * Sets description.
   *
   * @param description the description
   */
  public void setDescription(String description) {
    this.description = description;
  }

  public OrchestrationWorkflow getOrchestrationWorkflow() {
    return orchestrationWorkflow;
  }

  public void setOrchestrationWorkflow(OrchestrationWorkflow orchestrationWorkflow) {
    this.orchestrationWorkflow = orchestrationWorkflow;
  }

  public String getEnvId() {
    return envId;
  }

  public void setEnvId(String envId) {
    this.envId = envId;
  }

  public Integer getDefaultVersion() {
    return defaultVersion;
  }

  public void setDefaultVersion(Integer defaultVersion) {
    this.defaultVersion = defaultVersion;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public List<Service> getServices() {
    return services;
  }

  public void setServices(List<Service> services) {
    this.services = services;
  }

  public List<WorkflowExecution> getWorkflowExecutions() {
    return workflowExecutions;
  }

  public void setWorkflowExecutions(List<WorkflowExecution> workflowExecutions) {
    this.workflowExecutions = workflowExecutions;
  }

  public String getServiceId() {
    return serviceId;
  }

  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  public String getInfraMappingId() {
    return infraMappingId;
  }

  public void setInfraMappingId(String infraMappingId) {
    this.infraMappingId = infraMappingId;
  }

  public String getInfraDefinitionId() {
    return infraDefinitionId;
  }

  public void setInfraDefinitionId(String infraDefinitionId) {
    this.infraDefinitionId = infraDefinitionId;
  }

  public boolean isTemplatized() {
    return templatized;
  }

  public void setTemplatized(boolean templatized) {
    this.templatized = templatized;
  }

  public List<TemplateExpression> getTemplateExpressions() {
    return templateExpressions;
  }
  public void setTemplateExpressions(List<TemplateExpression> templateExpressions) {
    this.templateExpressions = templateExpressions;
  }

  @Override
  public String getAccountId() {
    return accountId;
  }

  public void setAccountId(String accountId) {
    this.accountId = accountId;
  }

  public boolean isSample() {
    return sample;
  }

  public void setSample(boolean sample) {
    this.sample = sample;
  }

  public boolean checkEnvironmentTemplatized() {
    if (templateExpressions == null) {
      return false;
    }
    return templateExpressions.stream().anyMatch(
        templateExpression -> templateExpression.getFieldName().equals("envId"));
  }

  public String fetchEnvTemplatizedName() {
    TemplateExpression envTemplateExpression = fetchEnvTemplateExpression();
    return envTemplateExpression == null ? null
                                         : WorkflowServiceTemplateHelper.getName(envTemplateExpression.getExpression());
  }

  private TemplateExpression fetchEnvTemplateExpression() {
    if (isEmpty(templateExpressions)) {
      return null;
    }
    return templateExpressions.stream()
        .filter(templateExpression -> templateExpression.getFieldName().equals("envId"))
        .findFirst()
        .orElse(null);
  }

  public TemplateExpression fetchServiceTemplateExpression() {
    if (isEmpty(templateExpressions)) {
      return null;
    }
    return templateExpressions.stream()
        .filter(templateExpression -> templateExpression.getFieldName().equals(WorkflowKeys.serviceId))
        .findFirst()
        .orElse(null);
  }
  public boolean checkServiceTemplatized() {
    return checkFieldTemplatized(WorkflowKeys.serviceId);
  }

  public boolean checkInfraTemplatized() {
    return checkFieldTemplatized(WorkflowKeys.infraMappingId);
  }

  public boolean checkInfraDefinitionTemplatized() {
    return checkFieldTemplatized(WorkflowKeys.infraDefinitionId);
  }

  private boolean checkFieldTemplatized(String fieldName) {
    return templateExpressions != null
        && templateExpressions.stream().anyMatch(
            templateExpression -> templateExpression.getFieldName().equals(fieldName));
  }

  public boolean envValid() {
    return isNotBlank(envId) || checkEnvironmentTemplatized();
  }

  public Workflow cloneInternal() {
    return aWorkflow()
        .appId(getAppId())
        .accountId(getAccountId())
        .envId(getEnvId())
        .workflowType(getWorkflowType())
        .name(getName())
        .templatized(isTemplatized())
        .templateExpressions(getTemplateExpressions())
        .build();
  }

  @Override
  public Set<String> generateKeywords() {
    Set<String> keywords = KeywordsAware.super.generateKeywords();
    keywords.addAll(asList(name, description, notes));
    if (workflowType != null) {
      keywords.add(workflowType.name());
    }

    if (orchestrationWorkflow != null) {
      keywords.add(orchestrationWorkflow.getOrchestrationWorkflowType().name());
      if (isNotEmpty(orchestrationWorkflow.getLinkedTemplateUuids())) {
        keywords.addAll(orchestrationWorkflow.getLinkedTemplateUuids());
      }
    }
    if (templatized) {
      keywords.add("template");
    }
    if (services != null) {
      keywords.addAll(services.stream().map(Service::getName).distinct().collect(toList()));
    }
    return keywords;
  }

  @JsonIgnore
  @Override
  public String getMigrationEntityName() {
    return getName();
  }

  @JsonIgnore
  @Override
  public CgBasicInfo getCgBasicInfo() {
    return CgBasicInfo.builder()
        .id(getUuid())
        .name(getName())
        .type(WORKFLOW)
        .appId(getAppId())
        .accountId(getAccountId())
        .build();
  }

  public static final class WorkflowBuilder {
    private String name;
    private String description;
    private WorkflowType workflowType;
    private String envId;
    private Integer defaultVersion;
    private String notes;
    private OrchestrationWorkflow orchestrationWorkflow;
    private List<Service> services;
    private List<WorkflowExecution> workflowExecutions = new ArrayList<>();
    private String uuid;
    private String appId;
    private String accountId;
    private EmbeddedUser createdBy;
    private long createdAt;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;
    private String serviceId;
    private String infraMappingId;
    private String infraDefinitionId;
    private boolean templatized;
    private List<TemplateExpression> templateExpressions;
    private List<String> linkedTemplateUuids;
    private List<String> linkedArtifactStreamIds;
    private boolean syncFromGit;
    private String ecsBGType;
    private boolean sample;

    private WorkflowBuilder() {}

    public static WorkflowBuilder aWorkflow() {
      return new WorkflowBuilder();
    }

    public WorkflowBuilder ecsBGType(String ecsBGType) {
      this.ecsBGType = ecsBGType;
      return this;
    }

    public WorkflowBuilder name(String name) {
      this.name = name;
      return this;
    }

    public WorkflowBuilder description(String description) {
      this.description = description;
      return this;
    }

    public WorkflowBuilder workflowType(WorkflowType workflowType) {
      this.workflowType = workflowType;
      return this;
    }

    public WorkflowBuilder envId(String envId) {
      this.envId = envId;
      return this;
    }

    public WorkflowBuilder defaultVersion(Integer defaultVersion) {
      this.defaultVersion = defaultVersion;
      return this;
    }

    public WorkflowBuilder orchestrationWorkflow(OrchestrationWorkflow orchestrationWorkflow) {
      this.orchestrationWorkflow = orchestrationWorkflow;
      return this;
    }

    public WorkflowBuilder services(List<Service> services) {
      this.services = services;
      return this;
    }

    public WorkflowBuilder uuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    public WorkflowBuilder appId(String appId) {
      this.appId = appId;
      return this;
    }

    public WorkflowBuilder accountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    public WorkflowBuilder createdBy(EmbeddedUser createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public WorkflowBuilder createdAt(long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public WorkflowBuilder lastUpdatedBy(EmbeddedUser lastUpdatedBy) {
      this.lastUpdatedBy = lastUpdatedBy;
      return this;
    }

    public WorkflowBuilder lastUpdatedAt(long lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    public WorkflowBuilder serviceId(String serviceId) {
      this.serviceId = serviceId;
      return this;
    }

    public WorkflowBuilder infraMappingId(String infraMappingId) {
      this.infraMappingId = infraMappingId;
      return this;
    }

    public WorkflowBuilder infraDefinitionId(String infraDefinitionId) {
      this.infraDefinitionId = infraDefinitionId;
      return this;
    }

    public WorkflowBuilder templatized(boolean templatized) {
      this.templatized = templatized;
      return this;
    }

    public WorkflowBuilder templateExpressions(List<TemplateExpression> templateExpressions) {
      this.templateExpressions = templateExpressions;
      return this;
    }

    public WorkflowBuilder linkedTemplateUuids(List<String> templateUuids) {
      this.linkedTemplateUuids = templateUuids;
      return this;
    }

    public WorkflowBuilder linkedArtifactStreamIds(List<String> linkedArtifactStreamIds) {
      this.linkedArtifactStreamIds = linkedArtifactStreamIds;
      return this;
    }

    public WorkflowBuilder notes(String notes) {
      this.notes = notes;
      return this;
    }

    public WorkflowBuilder syncFromGit(boolean syncFromGit) {
      this.syncFromGit = syncFromGit;
      return this;
    }

    public WorkflowBuilder sample(boolean sample) {
      this.sample = sample;
      return this;
    }

    public Workflow build() {
      Workflow workflow = new Workflow();
      workflow.setName(name);
      workflow.setDescription(description);
      workflow.setWorkflowType(workflowType);
      workflow.setEnvId(envId);
      workflow.setDefaultVersion(defaultVersion);
      workflow.setNotes(notes);
      workflow.setOrchestrationWorkflow(orchestrationWorkflow);
      workflow.setServices(services);
      workflow.setWorkflowExecutions(workflowExecutions);
      workflow.setUuid(uuid);
      workflow.setAppId(appId);
      workflow.setAccountId(accountId);
      workflow.setCreatedBy(createdBy);
      workflow.setCreatedAt(createdAt);
      workflow.setLastUpdatedBy(lastUpdatedBy);
      workflow.setLastUpdatedAt(lastUpdatedAt);
      workflow.setServiceId(serviceId);
      workflow.setInfraMappingId(infraMappingId);
      workflow.setInfraDefinitionId(infraDefinitionId);
      workflow.setTemplatized(templatized);
      workflow.setTemplateExpressions(templateExpressions);
      workflow.setNotes(notes);
      workflow.setLinkedTemplateUuids(linkedTemplateUuids);
      workflow.setLinkedArtifactStreamIds(linkedArtifactStreamIds);
      workflow.setSyncFromGit(syncFromGit);
      workflow.setSample(sample);
      return workflow;
    }
  }

  @UtilityClass
  public static final class WorkflowKeys {
    // Temporary
    public static final String appId = "appId";
    public static final String createdAt = "createdAt";
    public static final String uuid = "uuid";
    public static final String accountId = "accountId";
    public static final String orchestrationWorkflowType = "orchestration.orchestrationWorkflowType";
  }
}
