package io.harness.scim.system;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

/**
 * All fields in this class are defined as per below doc.
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7643#section-5">SCIM ServiceProviderConfig Definition</a>
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "ServiceProviderConfigKeys")
@OwnedBy(PL)
public class ServiceProviderConfig {
  public static final String SCHEMA_SERVICE_PROVIDER_CONFIG =
      "urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig";
  public static Set<String> schemas = new HashSet<>(Arrays.asList(SCHEMA_SERVICE_PROVIDER_CONFIG));
  private String documentationUri;
  private PatchConfig patch;
  private BulkConfig bulk;
  private FilterConfig filter;
  private ChangePasswordConfig changePassword;
  private SortConfig sort;
  private ETagConfig etag;
  private List<AuthenticationScheme> authenticationSchemes;

  @JsonCreator
  public ServiceProviderConfig(@JsonProperty(value = "documentationUri") final String documentationUri,
      @JsonProperty(value = "patch", required = true) final PatchConfig patch,
      @JsonProperty(value = "bulk", required = true) final BulkConfig bulk,
      @JsonProperty(value = "filter", required = true) final FilterConfig filter,
      @JsonProperty(value = "changePassword", required = true) final ChangePasswordConfig changePassword,
      @JsonProperty(value = "sort", required = true) final SortConfig sort,
      @JsonProperty(value = "etag", required = true) final ETagConfig etag,
      @JsonProperty(
          value = "authenticationSchemes", required = true) final List<AuthenticationScheme> authenticationSchemes) {
    this.documentationUri = documentationUri;
    this.patch = patch;
    this.bulk = bulk;
    this.filter = filter;
    this.changePassword = changePassword;
    this.sort = sort;
    this.etag = etag;
    this.authenticationSchemes =
        authenticationSchemes == null ? null : Collections.unmodifiableList(authenticationSchemes);
  }
}
