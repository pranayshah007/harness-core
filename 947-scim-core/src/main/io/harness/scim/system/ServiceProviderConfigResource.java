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
public class ServiceProviderConfigResource {
  public static final String SCHEMA_SERVICE_PROVIDER_CONFIG =
      "urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig";
  public static Set<String> schemas = new HashSet<>(Arrays.asList(SCHEMA_SERVICE_PROVIDER_CONFIG));
  private String documentationUri;
  private ScimPatchConfig patch;
  private ScimBulkConfigResource bulk;
  private ScimFilterConfig filter;
  private ScimChangePasswordConfig changePassword;
  private ScimSortConfig sort;
  private ScimETagConfig etag;
  private List<ScimAuthenticationScheme> scimAuthenticationSchemes;

  @JsonCreator
  public ServiceProviderConfigResource(@JsonProperty(value = "documentationUri") final String documentationUri,
      @JsonProperty(value = "patch", required = true) final ScimPatchConfig patch,
      @JsonProperty(value = "bulk", required = true) final ScimBulkConfigResource bulk,
      @JsonProperty(value = "filter", required = true) final ScimFilterConfig filter,
      @JsonProperty(value = "changePassword", required = true) final ScimChangePasswordConfig changePassword,
      @JsonProperty(value = "sort", required = true) final ScimSortConfig sort,
      @JsonProperty(value = "etag", required = true) final ScimETagConfig etag,
      @JsonProperty(value = "authenticationSchemes",
          required = true) final List<ScimAuthenticationScheme> scimAuthenticationSchemes) {
    this.documentationUri = documentationUri;
    this.patch = patch;
    this.bulk = bulk;
    this.filter = filter;
    this.changePassword = changePassword;
    this.sort = sort;
    this.etag = etag;
    this.scimAuthenticationSchemes =
        scimAuthenticationSchemes == null ? null : Collections.unmodifiableList(scimAuthenticationSchemes);
  }
}
