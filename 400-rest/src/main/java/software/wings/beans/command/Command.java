/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import static software.wings.beans.command.Command.Builder.aCommand;

import static java.util.Arrays.asList;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.logging.CommandExecutionStatus;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.serializer.MapperUtils;

import software.wings.beans.Base;
import software.wings.beans.Graph;
import software.wings.beans.GraphNode;
import software.wings.beans.Variable;
import software.wings.beans.template.TemplateMetadata;
import software.wings.beans.template.TemplateReference;
import software.wings.beans.template.dto.ImportedTemplateDetails;
import software.wings.service.impl.ServiceResourceServiceImpl;
import software.wings.stencils.EnumData;
import software.wings.stencils.Expand;
import software.wings.utils.ArtifactType;
import software.wings.utils.ContainerFamily;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;

/**
 * Created by peeyushaggarwal on 5/31/16.
 */
@JsonTypeName("COMMAND")
@Attributes(title = "Command")
@Entity(value = "commands")
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "CommandKeys")
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class Command extends Base implements CommandUnit, NestedAnnotationResolver {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("yaml")
                 .unique(true)
                 .field(BaseKeys.appId)
                 .field(CommandKeys.originEntityId)
                 .field(CommandKeys.version)
                 .build())
        .build();
  }

  @NotEmpty @SchemaIgnore private String name;
  @SchemaIgnore private CommandUnitType commandUnitType;
  @SchemaIgnore private CommandExecutionStatus commandExecutionStatus = CommandExecutionStatus.QUEUED;

  @SchemaIgnore private boolean artifactNeeded;
  @SchemaIgnore @Deprecated private String deploymentType;

  @NotEmpty @SchemaIgnore private String originEntityId;

  @SchemaIgnore private ContainerFamily containerFamily;

  @SchemaIgnore private ArtifactType artifactType;

  @Expand(dataProvider = ServiceResourceServiceImpl.class)
  @EnumData(enumDataProvider = ServiceResourceServiceImpl.class)
  @Attributes(title = "Name")
  private String referenceId;
  private String referenceUuid;
  private TemplateReference templateReference;
  @SchemaIgnore private transient Graph graph;

  @SchemaIgnore private Long version;

  @Expression(ALLOW_SECRETS) @SchemaIgnore private List<CommandUnit> commandUnits = Lists.newArrayList();

  @SchemaIgnore private CommandType commandType = CommandType.OTHER;

  @SchemaIgnore private List<Variable> templateVariables = new ArrayList<>();

  @JsonIgnore private transient String templateId;
  @JsonIgnore private transient String templateVersion;
  @JsonIgnore private transient ImportedTemplateDetails importedTemplateDetails;
  @SchemaIgnore private TemplateMetadata templateMetadata;
  @FdIndex @Getter @Setter private String accountId;

  private List<Variable> variables = Lists.newArrayList();

  public Command() {
    this.commandUnitType = CommandUnitType.COMMAND;
  }

  /**
   * Instantiates a new command.
   */
  @Override
  public CommandExecutionStatus execute(CommandExecutionContext context) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CommandUnitType getCommandUnitType() {
    return commandUnitType;
  }

  @Override
  public void setCommandUnitType(CommandUnitType commandUnitType) {
    this.commandUnitType = commandUnitType;
  }

  @Override
  public CommandExecutionStatus getCommandExecutionStatus() {
    return commandExecutionStatus;
  }

  @Override
  public void setCommandExecutionStatus(CommandExecutionStatus commandExecutionStatus) {
    this.commandExecutionStatus = commandExecutionStatus;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets reference id.
   *
   * @return the reference id
   */
  public String getReferenceId() {
    return referenceId;
  }

  /**
   * Sets reference id.
   *
   * @param referenceId the reference id
   */
  public void setReferenceId(String referenceId) {
    this.referenceId = referenceId;
  }

  /**
   * Gets graph.
   *
   * @return the graph
   */
  @SchemaIgnore
  public Graph getGraph() {
    return graph;
  }

  /**
   * Sets graph.
   *
   * @param graph the graph
   */
  @SchemaIgnore
  public void setGraph(Graph graph) {
    this.graph = graph;
  }

  /**
   * Gets command units.
   *
   * @return the command units
   */
  @SchemaIgnore
  public List<CommandUnit> getCommandUnits() {
    return commandUnits;
  }

  /**
   * Sets command units.
   *
   * @param commandUnits the command units
   */
  @SchemaIgnore
  public void setCommandUnits(List<CommandUnit> commandUnits) {
    this.commandUnits = commandUnits;
  }

  /**
   * Getter for property 'version'.
   *
   * @return Value for property 'version'.
   */
  @SchemaIgnore
  public Long getVersion() {
    return Optional.ofNullable(version).orElse(1L);
  }

  /**
   * Setter for property 'version'.
   *
   * @param version Value to set for property 'version'.
   */
  @SchemaIgnore
  public void setVersion(Long version) {
    this.version = version;
  }

  /**
   * Getter for property 'originEntityId'.
   *
   * @return Value for property 'originEntityId'.
   */
  public String getOriginEntityId() {
    return originEntityId;
  }

  /**
   * Setter for property 'originEntityId'.
   *
   * @param originEntityId Value to set for property 'originEntityId'.
   */
  public void setOriginEntityId(String originEntityId) {
    this.originEntityId = originEntityId;
  }

  /**
   * Getter for property 'containerFamily'.
   *
   * @return Value for property 'containerFamily'.
   */
  public ContainerFamily getContainerFamily() {
    return containerFamily;
  }

  /**
   * Setter for property 'containerFamily'.
   *
   * @param containerFamily Value to set for property 'containerFamily'.
   */
  public void setContainerFamily(ContainerFamily containerFamily) {
    this.containerFamily = containerFamily;
  }

  /**
   * Getter for property 'artifactType'.
   *
   * @return Value for property 'artifactType'.
   */
  public ArtifactType getArtifactType() {
    return artifactType;
  }

  /**
   * Setter for property 'artifactType'.
   *
   * @param artifactType Value to set for property 'artifactType'.
   */
  public void setArtifactType(ArtifactType artifactType) {
    this.artifactType = artifactType;
  }

  @SchemaIgnore
  public CommandType getCommandType() {
    return commandType;
  }

  public void setCommandType(CommandType commandType) {
    this.commandType = commandType;
  }

  @SchemaIgnore
  public String getReferenceUuid() {
    return referenceUuid;
  }

  public void setReferenceUuid(String referenceUuid) {
    this.referenceUuid = referenceUuid;
  }

  public TemplateReference getTemplateReference() {
    return templateReference;
  }

  public void setTemplateReference(TemplateReference templateReference) {
    this.templateReference = templateReference;
  }

  @SchemaIgnore
  public List<Variable> getTemplateVariables() {
    return templateVariables;
  }

  public void setTemplateVariables(List<Variable> templateVariables) {
    this.templateVariables = templateVariables;
  }

  @SchemaIgnore
  public String getTemplateId() {
    return templateId;
  }

  public void setTemplateId(String templateId) {
    this.templateId = templateId;
  }

  @SchemaIgnore
  public String getTemplateVersion() {
    return templateVersion;
  }

  @SchemaIgnore
  public ImportedTemplateDetails getImportedTemplateDetails() {
    return importedTemplateDetails;
  }

  @SchemaIgnore
  public TemplateMetadata getTemplateMetadata() {
    return templateMetadata;
  }

  public void setTemplateVersion(String templateVersion) {
    this.templateVersion = templateVersion;
  }

  public void setImportedTemplateDetails(ImportedTemplateDetails importedTemplateDetails) {
    this.importedTemplateDetails = importedTemplateDetails;
  }

  public void setTemplateMetadata(TemplateMetadata templateMetadata) {
    this.templateMetadata = templateMetadata;
  }

  @Override
  public List<Variable> getVariables() {
    return variables;
  }

  @Override
  public void setVariables(List<Variable> variables) {
    this.variables = variables;
  }

  /**
   * Transform graph.
   */
  public void transformGraph() {
    setName(graph.getGraphName());
    Iterator<GraphNode> pipelineIterator = graph.getLinearGraphIterator();
    while (pipelineIterator.hasNext()) {
      GraphNode node = pipelineIterator.next();
      CommandUnitType type = CommandUnitType.valueOf(node.getType().toUpperCase());

      CommandUnitDescriptor descriptor = CommandUnitDescriptor.forType(type);
      CommandUnit commandUnit = descriptor.newInstance("");
      MapperUtils.mapObject(node.getProperties(), descriptor.getTypeClass().cast(commandUnit));
      commandUnit.setName(node.getName());
      commandUnits.add(commandUnit);
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(referenceId, graph, commandUnits);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final Command other = (Command) obj;
    return Objects.equals(this.referenceId, other.referenceId) && Objects.equals(this.graph, other.graph)
        && Objects.equals(this.commandUnits, other.commandUnits);
  }

  @SchemaIgnore
  @Override
  public boolean isArtifactNeeded() {
    Set<String> serviceArtifactVariableNames = new HashSet<>();
    updateServiceArtifactVariableNames(serviceArtifactVariableNames);
    return isNotEmpty(serviceArtifactVariableNames);
  }

  @Override
  public void setArtifactNeeded(boolean artifactNeeded) {
    this.artifactNeeded = artifactNeeded;
  }

  @SchemaIgnore
  @Override
  public void updateServiceArtifactVariableNames(Set<String> serviceArtifactVariableNames) {
    if (isNotEmpty(templateVariables)) { // when command linked to service
      for (Variable variable : templateVariables) {
        ExpressionEvaluator.updateServiceArtifactVariableNames(variable.getValue(), serviceArtifactVariableNames);
      }
    }
    if (isNotEmpty(commandUnits)) {
      commandUnits.forEach(commandUnit -> commandUnit.updateServiceArtifactVariableNames(serviceArtifactVariableNames));
    }
  }

  @Override
  @SchemaIgnore
  @Deprecated
  public String getDeploymentType() {
    return deploymentType;
  }

  @Override
  @Deprecated
  public void setDeploymentType(String deploymentType) {
    this.deploymentType = deploymentType;
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

  public Command cloneInternal() {
    Command clonedCommand = aCommand()
                                .withCommandType(getCommandType())
                                .withName(getName())
                                .withTemplateVariables(templateVariables)
                                .withGraph(getGraph())
                                .build();
    if (getGraph() == null) {
      clonedCommand.setCommandUnits(getCommandUnits());
    }
    clonedCommand.setCommandUnitType(getCommandUnitType());
    return clonedCommand;
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private String referenceId;
    private String originEntityId;
    private Graph graph;
    private List<CommandUnit> commandUnits = Lists.newArrayList();
    private String name;
    private CommandExecutionStatus commandExecutionStatus;
    private boolean artifactNeeded;
    private CommandType commandType = CommandType.OTHER;
    private List<Variable> templateVariables = new ArrayList<>();
    private String templateId;
    private String templateVersion;
    private ImportedTemplateDetails importedTemplateVersion;
    private TemplateMetadata templateMetadata;
    private TemplateReference templateReference;
    private String referenceUuid;
    private String accountId;

    private Builder() {}

    /**
     * A command builder.
     *
     * @return the builder
     */
    public static Builder aCommand() {
      return new Builder();
    }

    /**
     * With reference id builder.
     *
     * @param referenceId the reference id
     * @return the builder
     */
    public Builder withReferenceId(String referenceId) {
      this.referenceId = referenceId;
      return this;
    }

    public Builder withOriginEntityId(String originEntityId) {
      this.originEntityId = originEntityId;
      return this;
    }

    /**
     * With graph builder.
     *
     * @param graph the graph
     * @return the builder
     */
    public Builder withGraph(Graph graph) {
      this.graph = graph;
      return this;
    }

    /**
     * Adds the command units.
     *
     * @param commandUnits the command units
     * @return the builder
     */
    public Builder addCommandUnits(CommandUnit... commandUnits) {
      this.commandUnits.addAll(asList(commandUnits));
      return this;
    }

    /**
     * With command units builder.
     *
     * @param commandUnits the command units
     * @return the builder
     */
    public Builder withCommandUnits(List<CommandUnit> commandUnits) {
      this.commandUnits = commandUnits;
      return this;
    }

    /**
     * With name builder.
     *
     * @param name the name
     * @return the builder
     */
    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    /**
     * With execution result builder.
     *
     * @param commandExecutionStatus the execution result
     * @return the builder
     */
    public Builder withExecutionResult(CommandExecutionStatus commandExecutionStatus) {
      this.commandExecutionStatus = commandExecutionStatus;
      return this;
    }

    /**
     * With artifact needed builder.
     *
     * @param artifactNeeded the artifact needed
     * @return the builder
     */
    public Builder withArtifactNeeded(boolean artifactNeeded) {
      this.artifactNeeded = artifactNeeded;
      return this;
    }

    /**
     * With command type
     *
     * @param commandType the command type
     * @return the builder
     */
    public Builder withCommandType(CommandType commandType) {
      this.commandType = commandType;
      return this;
    }

    /**
     * With Variables
     */
    public Builder withTemplateVariables(List<Variable> templateVariables) {
      this.templateVariables = templateVariables;
      return this;
    }

    public Builder withTemplateId(String templateId) {
      this.templateId = templateId;
      return this;
    }

    public Builder withTemplateVersion(String templateVersion) {
      this.templateVersion = templateVersion;
      return this;
    }

    public Builder withImportedTemplateVersion(ImportedTemplateDetails importedTemplateVersion) {
      this.importedTemplateVersion = importedTemplateVersion;
      return this;
    }

    public Builder withTemplateMetadata(TemplateMetadata templateMetadata) {
      this.templateMetadata = templateMetadata;
      return this;
    }

    public Builder withTemplateReference(TemplateReference templateReference) {
      this.templateReference = templateReference;
      return this;
    }

    public Builder withReferenceUuid(String referenceUuid) {
      this.referenceUuid = referenceUuid;
      return this;
    }

    /**
     * With accountId
     *
     * @param accountId the command type
     * @return the builder
     */
    public Builder withAccountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    /**
     * But builder.
     *
     * @return the builder
     */
    public Builder but() {
      return aCommand()
          .withReferenceId(referenceId)
          .withGraph(graph)
          .withCommandUnits(commandUnits)
          .withName(name)
          .withExecutionResult(commandExecutionStatus)
          .withArtifactNeeded(artifactNeeded)
          .withCommandType(commandType)
          .withTemplateId(templateId)
          .withTemplateVersion(templateVersion)
          .withImportedTemplateVersion(importedTemplateVersion)
          .withTemplateReference(templateReference)
          .withTemplateMetadata(templateMetadata)
          .withReferenceUuid(referenceUuid)
          .withAccountId(accountId);
    }

    /**
     * Build command.
     *
     * @return the command
     */
    public Command build() {
      Command command = new Command();
      command.setReferenceId(referenceId);
      command.setOriginEntityId(originEntityId);
      command.setGraph(graph);
      command.setCommandUnits(commandUnits);
      command.setName(name);
      command.setCommandExecutionStatus(commandExecutionStatus);
      command.setArtifactNeeded(artifactNeeded);
      command.setCommandType(commandType);
      command.setTemplateVariables(templateVariables);
      command.setTemplateId(templateId);
      command.setTemplateReference(templateReference);
      command.setTemplateVersion(templateVersion);
      command.setImportedTemplateDetails(importedTemplateVersion);
      command.setTemplateMetadata(templateMetadata);
      command.setAccountId(accountId);
      return command;
    }
  }
}
