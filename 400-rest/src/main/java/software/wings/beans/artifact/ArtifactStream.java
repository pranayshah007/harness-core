/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static java.util.Arrays.asList;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.EntityName;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.NameAccess;

import software.wings.beans.Base;
import software.wings.beans.NameValuePair;
import software.wings.beans.Service;
import software.wings.beans.Variable;
import software.wings.beans.config.ArtifactSourceable;
import software.wings.beans.entityinterface.KeywordsAware;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.utils.Utils;
import software.wings.yaml.BaseEntityYaml;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Transient;

@OwnedBy(CDC)
@TargetModule(HarnessModule._957_CG_BEANS)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "artifactStreamType")

// TODO: ASR: add compound index with setting_id + name
// TODO: ASR: change all apis to work with Service.artifactStreamIds instead of serviceId - including UI
// TODO: ASR: migrate the index and name of existing artifact streams (name + service name + app name + setting name)
// TODO: ASR: removing serviceId from ArtifactStream
// TODO: ASR: add feature flag: should not have serviceId in ArtifactStream
@Data
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "ArtifactStreamKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "artifactStream")
@BreakDependencyOn("io.harness.ff.FeatureFlagService")
@HarnessEntity(exportable = true)
public abstract class ArtifactStream
    extends Base implements AccountAccess, ArtifactSourceable, PersistentRegularIterable, NameAccess, KeywordsAware,
                            NGMigrationEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("yaml")
                 .unique(true)
                 .field(ArtifactStreamKeys.appId)
                 .field(ArtifactStreamKeys.serviceId)
                 .field(ArtifactStreamKeys.name)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("artifactStream_cleanup")
                 .field(ArtifactStreamKeys.artifactStreamType)
                 .field(ArtifactStreamKeys.collectionEnabled)
                 .field(ArtifactStreamKeys.nextCleanupIteration)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("artifactStream_collection")
                 .field(ArtifactStreamKeys.collectionEnabled)
                 .field(ArtifactStreamKeys.nextIteration)
                 .build())
        .build();
  }

  protected static final String dateFormat = "HHMMSS";
  protected static final Pattern wingsVariablePattern = Pattern.compile("\\$\\{[^.{}\\s-]*}");
  protected static final Pattern extractParametersPattern = Pattern.compile("\\$\\{(.*?)}");

  @Transient private String artifactStreamId;
  private String artifactStreamType;
  private String sourceName;
  private String settingId;
  @Transient private String artifactServerName;
  @EntityName private String name;
  private boolean autoPopulate;
  @FdIndex private String serviceId;
  @Transient private Service service;
  @Deprecated private transient boolean autoDownload;
  @Deprecated private transient boolean autoApproveForProduction;
  private boolean metadataOnly;
  private int failedCronAttempts;
  @FdIndex private long nextIteration;
  @FdIndex private long nextCleanupIteration;
  @FdIndex private String templateUuid;
  private String templateVersion;
  private List<Variable> templateVariables = new ArrayList<>();
  @FdIndex private String accountId;
  @SchemaIgnore private Set<String> keywords;
  @Transient private long artifactCount;
  @Transient private List<ArtifactSummary> artifacts;
  private boolean sample;

  // perpetualTaskId is the reference to the perpetual task. If no task has yet been created for this artifact stream,
  // this field will not exist.
  @FdIndex String perpetualTaskId;

  // Collection status denotes whether the first-time collection of the artifact stream is completed. If it's completed
  // we mark the status as STABLE, otherwise it is by default UNSTABLE when the stream is created or the artifact source
  // is updated. For backwards compatibility, null is treated as stable and we make sure to update the artifact stream
  // to UNSTABLE if the artifact source is changed.
  private String collectionStatus;

  private boolean artifactStreamParameterized;

  private Boolean collectionEnabled;
  private long lastIteration;
  private long lastSuccessfulIteration;
  private long maxAttempts;

  public ArtifactStream(String artifactStreamType) {
    this.artifactStreamType = artifactStreamType;
    this.autoPopulate = true;
  }

  public ArtifactStream(String uuid, String appId, EmbeddedUser createdBy, long createdAt, EmbeddedUser lastUpdatedBy,
      long lastUpdatedAt, String entityYamlPath, String artifactStreamType, String sourceName, String settingId,
      String name, boolean autoPopulate, String serviceId, boolean metadataOnly, String accountId, Set<String> keywords,
      boolean sample) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath);
    this.artifactStreamType = artifactStreamType;
    this.sourceName = sourceName;
    this.settingId = settingId;
    this.name = name;
    this.autoPopulate = autoPopulate;
    this.serviceId = serviceId;
    this.metadataOnly = metadataOnly;
    this.accountId = accountId;
    this.keywords = keywords;
    this.sample = sample;
  }

  public String fetchAppId() {
    return appId;
  }

  public String generateName() {
    return Utils.normalize(generateSourceName());
  }

  public abstract String generateSourceName();

  public abstract ArtifactStreamAttributes fetchArtifactStreamAttributes(FeatureFlagService featureFlagService);

  public void inferProperties(ArtifactStreamAttributes attributes) {}

  public abstract String fetchArtifactDisplayName(String buildNo);

  public void validateRequiredFields() {}

  public abstract ArtifactStream cloneInternal();

  public boolean artifactSourceChanged(ArtifactStream artifactStream) {
    return !this.sourceName.equals(artifactStream.getSourceName());
  }

  public boolean artifactCollectionEnabledFromDisabled(ArtifactStream artifactStream) {
    return Boolean.FALSE.equals(collectionEnabled) && !Boolean.FALSE.equals(artifactStream.getCollectionEnabled());
  }

  public boolean artifactServerChanged(ArtifactStream artifactStream) {
    if (settingId == null) {
      if (artifactStream.getSettingId() == null) {
        return false;
      }
      return true;
    }
    return !this.settingId.equals(artifactStream.getSettingId());
  }

  public boolean shouldValidate() {
    return false;
  }

  public boolean checkIfStreamParameterized() {
    return false;
  }

  protected boolean validateParameters(String... parameters) {
    boolean found = false;
    for (String parameter : parameters) {
      if (isNotEmpty(parameter) && parameter.startsWith("${")) {
        if (!wingsVariablePattern.matcher(parameter).find()) {
          throw new InvalidRequestException(
              format("Parameterized fields should match regex: [%s]", wingsVariablePattern.toString()));
        }
        found = true;
      }
    }
    return found;
  }

  public List<String> fetchArtifactStreamParameters() {
    return new ArrayList<>();
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return ArtifactStreamKeys.nextCleanupIteration.equals(fieldName) ? nextCleanupIteration : nextIteration;
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (ArtifactStreamKeys.nextCleanupIteration.equals(fieldName)) {
      this.nextCleanupIteration = nextIteration;
      return;
    }

    this.nextIteration = nextIteration;
  }

  @Override
  public Set<String> generateKeywords() {
    Set<String> keywords = KeywordsAware.super.generateKeywords();
    keywords.addAll(asList(name, sourceName, artifactStreamType));
    return keywords;
  }

  @JsonIgnore
  @Override
  public String getMigrationEntityName() {
    return getName();
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public abstract static class Yaml extends BaseEntityYaml {
    private String serverName;
    private String templateUri;
    private Boolean collectionEnabled;
    private List<NameValuePair> templateVariables;

    public Yaml(String type, String harnessApiVersion, String serverName) {
      super(type, harnessApiVersion);
      this.serverName = serverName;
    }

    public Yaml(String type, String harnessApiVersion) {
      super(type, harnessApiVersion);
    }

    public Yaml(String type, String harnessApiVersion, String serverName, boolean metadataOnly, String templateUri,
        List<NameValuePair> templateVariables, Boolean collectionEnabled) {
      super(type, harnessApiVersion);
      this.serverName = serverName;
      this.templateUri = templateUri;
      this.templateVariables = templateVariables;
      this.collectionEnabled = collectionEnabled;
    }
  }

  @UtilityClass
  public static final class ArtifactStreamKeys {
    // Temporary
    public static final String appId = "appId";
    public static final String accountId = "accountId";
    public static final String uuid = "uuid";
    public static final String settingId = "settingId";
    public static final String repositoryFormat = "repositoryFormat";
    public static final String repositoryType = "repositoryType";
  }
}
