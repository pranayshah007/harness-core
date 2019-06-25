package software.wings.beans;

import static java.util.Arrays.asList;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import io.harness.annotation.HarnessExportableEntity;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.EntityName;
import io.harness.data.validator.Trimmed;
import io.harness.persistence.NameAccess;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Field;
import org.mongodb.morphia.annotations.Index;
import org.mongodb.morphia.annotations.IndexOptions;
import org.mongodb.morphia.annotations.Indexed;
import org.mongodb.morphia.annotations.Indexes;
import org.mongodb.morphia.annotations.Reference;
import org.mongodb.morphia.annotations.Transient;
import org.mongodb.morphia.annotations.Version;
import software.wings.api.DeploymentType;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamBinding;
import software.wings.beans.command.ServiceCommand;
import software.wings.utils.ArtifactType;
import software.wings.yaml.BaseEntityYaml;

import java.util.ArrayList;
import java.util.List;

/**
 * Component bean class.
 *
 * @author Rishi
 */
@Entity(value = "services", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexes(@Index(options = @IndexOptions(name = "yaml", unique = true), fields = { @Field("appId")
                                                                                  , @Field("name") }))
@HarnessExportableEntity
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "ServiceKeys")
public class Service extends Base implements KeywordsAware, NameAccess {
  @Trimmed @EntityName @NotEmpty private String name;
  private String description;
  private ArtifactType artifactType;
  private DeploymentType deploymentType;
  private String configMapYaml;
  private String helmValueYaml;

  @Version private long version;

  @Reference(idOnly = true, ignoreMissing = true) private AppContainer appContainer;

  @Transient private List<ConfigFile> configFiles = new ArrayList<>();
  @Transient private List<ServiceVariable> serviceVariables = new ArrayList<>();
  @Transient private List<ArtifactStream> artifactStreams = new ArrayList<>();
  @Transient private List<ServiceCommand> serviceCommands = new ArrayList<>();

  @Transient private Activity lastDeploymentActivity;
  @Transient private Activity lastProdDeploymentActivity;
  @Transient private Setup setup;

  @SchemaIgnore private List<String> keywords;

  private boolean isK8sV2;
  @Indexed private String accountId;
  @Indexed private List<String> artifactStreamIds;
  @Transient private List<ArtifactStreamBinding> artifactStreamBindings;

  @Builder
  public Service(String uuid, String appId, EmbeddedUser createdBy, long createdAt, EmbeddedUser lastUpdatedBy,
      long lastUpdatedAt, List<String> keywords, String entityYamlPath, String name, String description,
      ArtifactType artifactType, DeploymentType deploymentType, String configMapYaml, String helmValueYaml,
      long version, AppContainer appContainer, List<ConfigFile> configFiles, List<ServiceVariable> serviceVariables,
      List<ArtifactStream> artifactStreams, List<ServiceCommand> serviceCommands, Activity lastDeploymentActivity,
      Activity lastProdDeploymentActivity, Setup setup, boolean isK8sV2, String accountId,
      List<String> artifactStreamIds) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath);
    this.name = name;
    this.description = description;
    this.artifactType = artifactType;
    this.deploymentType = deploymentType;
    this.configMapYaml = configMapYaml;
    this.helmValueYaml = helmValueYaml;
    this.version = version;
    this.appContainer = appContainer;
    this.configFiles = configFiles == null ? new ArrayList<>() : configFiles;
    this.serviceVariables = serviceVariables == null ? new ArrayList<>() : serviceVariables;
    this.artifactStreams = artifactStreams == null ? new ArrayList<>() : artifactStreams;
    this.serviceCommands = serviceCommands == null ? new ArrayList<>() : serviceCommands;
    this.lastDeploymentActivity = lastDeploymentActivity;
    this.lastProdDeploymentActivity = lastProdDeploymentActivity;
    this.setup = setup;
    this.keywords = keywords;
    this.isK8sV2 = isK8sV2;
    this.accountId = accountId;
    this.artifactStreamIds = artifactStreamIds;
  }

  // TODO: check what to do with artifactStreamIds and artifactStreamBindings
  public Service cloneInternal() {
    return Service.builder()
        .appId(getAppId())
        .accountId(getAccountId())
        .name(name)
        .description(description)
        .artifactType(artifactType)
        .deploymentType(deploymentType)
        .configMapYaml(configMapYaml)
        .helmValueYaml(helmValueYaml)
        .appContainer(appContainer)
        .isK8sV2(isK8sV2)
        .build();
  }

  @Override
  public List<String> generateKeywords() {
    List<String> keywords = KeywordsAware.super.generateKeywords();
    keywords.addAll(asList(name, description));
    if (artifactType != null) {
      keywords.add(artifactType.name());
    }
    return keywords;
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends BaseEntityYaml {
    private String description;
    private String artifactType;
    private String deploymentType;
    private String configMapYaml;
    private String applicationStack;
    private List<NameValuePair.Yaml> configVariables = new ArrayList<>();

    @lombok.Builder
    public Yaml(String harnessApiVersion, String description, String artifactType, String deploymentType,
        String configMapYaml, String applicationStack, List<NameValuePair.Yaml> configVariables) {
      super(EntityType.SERVICE.name(), harnessApiVersion);
      this.description = description;
      this.artifactType = artifactType;
      this.deploymentType = deploymentType;
      this.configMapYaml = configMapYaml;
      this.applicationStack = applicationStack;
      this.configVariables = configVariables;
    }
  }

  public static final class ServiceKeys {
    // Temporary
    public static final String appId = "appId";
    public static final String createdAt = "createdAt";
    public static final String uuid = "uuid";
  }
}
