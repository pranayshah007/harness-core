/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.servicenow;

import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.rule.Owner;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
// ---
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.imageio.IIOException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

// ---

public class ServiceNowTaskNGTest extends CategoryTest {
  @Mock private ServiceNowTaskNgHelper serviceNowTaskNgHelper;
  @InjectMocks
  private final ServiceNowTaskNG serviceNowTaskNG =
      new ServiceNowTaskNG(DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null, null, null);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  //  @Test
  //  @Owner(developers = PRABU)
  //  @Category(UnitTests.class)
  //  public void testRunObjectParamsShouldThrowMotImplementedException() {
  //    assertThatThrownBy(() -> serviceNowTaskNG.run(new Object[1]))
  //        .hasMessage("not implemented")
  //        .isInstanceOf(NotImplementedException.class);
  //  }

  //  @Test
  //  @Owner(developers = PRABU)
  //  @Category(UnitTests.class)
  //  public void testRun() {
  //    ServiceNowTaskNGResponse taskResponse = ServiceNowTaskNGResponse.builder().build();
  //    when(serviceNowTaskNgHelper.getServiceNowResponse(any())).thenReturn(taskResponse);
  //    assertThatCode(() ->
  //    serviceNowTaskNG.run(ServiceNowTaskNGParameters.builder().build())).doesNotThrowAnyException();
  //    verify(serviceNowTaskNgHelper).getServiceNowResponse(ServiceNowTaskNGParameters.builder().build());
  //  }

  //  @Test
  //  @Owner(developers = PRABU)
  //  @Category(UnitTests.class)
  //  public void testRunFailure() {
  //    when(serviceNowTaskNgHelper.getServiceNowResponse(any())).thenThrow(new HintException("Exception"));
  //    assertThatThrownBy(() -> serviceNowTaskNG.run(ServiceNowTaskNGParameters.builder().build()))
  //        .isInstanceOf(HintException.class);
  //    verify(serviceNowTaskNgHelper).getServiceNowResponse(ServiceNowTaskNGParameters.builder().build());
  //  }

  //  @Test
  //  @Owner(developers = PRABU)
  //  @Category(UnitTests.class)
  //  public void testGetServiceNowTaskNGDelegateSelectors() {
  //    ServiceNowTaskNGParameters serviceNowTaskNGParameters =
  //        ServiceNowTaskNGParameters.builder()
  //            .delegateSelectors(Arrays.asList("selector1"))
  //            .serviceNowConnectorDTO(ServiceNowConnectorDTO.builder().build())
  //            .build();
  //    assertThat(serviceNowTaskNGParameters.getDelegateSelectors()).containsExactlyInAnyOrder("selector1");
  //    serviceNowTaskNGParameters.getServiceNowConnectorDTO().setDelegateSelectors(ImmutableSet.of("selector2"));
  //    assertThat(serviceNowTaskNGParameters.getDelegateSelectors()).containsExactlyInAnyOrder("selector1",
  //    "selector2");
  //  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testisSupportingErrorFramework() {
    assertThat(serviceNowTaskNG.isSupportingErrorFramework()).isTrue();
  }

  class HarnessAdfsResource {
    private static final String PEM_PRIVATE_KEY_START = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_PRIVATE_KEY_END = "-----END PRIVATE KEY-----";
    private static final String KEY_ALGORITHM_RSA = "RSA";
    private static final int NO_OF_DAYS = 2;
    private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();

    public Map<String, Object> get_ssl_context_from_x509_cert(String crt, String key)
        throws IIOException, NoSuchAlgorithmException, InvalidKeySpecException {
      Map<String, Object> context = new HashMap<>();
      PKCS8EncodedKeySpec PKCS8keySpec = loadPKCS8EncodedKeySpecFromPem(key);
      KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM_RSA);

      // generate private key
      PrivateKey privateKey = keyFactory.generatePrivate(PKCS8keySpec);
      RSAPrivateCrtKey rsaPrivateCrtKey = (RSAPrivateCrtKey) privateKey;
      // getting public key spec from private key
      RSAPublicKeySpec publicKeySpec =
          new RSAPublicKeySpec(rsaPrivateCrtKey.getModulus(), rsaPrivateCrtKey.getPublicExponent());
      // generating public key
      context.put("certificate", loadCertificatesFromPem(crt)[0]);
      context.put("privateKey", privateKey);
      context.put("publicKey", keyFactory.generatePublic(publicKeySpec));

      return context;
    }

    public String get_ida_token_url(String env) {
      env = env.toLowerCase();
      String token_url;
      if (env.equals("dev")) {
        token_url = "https://idadg2.jpmorganchase.com/adfs/oauth2/token/";
      } else if (env.equals("uat")) {
        token_url = "https://idauatg2.jpmorganchase.com/adfs/oauth2/token/";
      } else {
        token_url = "https://harnessadfs.qa.harness.io/adfs/oauth2/token";
      }

      return token_url;
    }

    public Map<String, Object> createJWTHeader(Certificate certificate)
        throws NoSuchAlgorithmException, UnsupportedEncodingException, CertificateEncodingException {
      Map<String, Object> headers = new HashMap<>();
      String kid = DigestUtils.sha1Hex(certificate.getEncoded()).toUpperCase();

      //              Backup : new String(
      //              MessageDigest.getInstance("SHA-1").digest(certificate.toString().getBytes(StandardCharsets.UTF_8)),
      //              ); expected KID : "39A99DE112F09E9158BA49B7D60400867EB2F4ED";
      headers.put("alg", "RS256");
      headers.put("typ", "JWT");
      headers.put("kid", kid);
      return headers;
    }

    public Map<String, String> createJwtClaims(String client_id, String token_url) {
      Map<String, String> claims = new HashMap<>();
      claims.put("iss", client_id);
      claims.put("sub", client_id);
      claims.put("aud", token_url);
      claims.put("jti", generateUuid());
      // headers.put("exp", exp);
      // headers.pus("iat", iat);
      return claims;
    }

    public String createJwtSignedRequest(
        Map<String, String> jwt_claims, Map<String, Object> jwt_header, RSAKey privateKey) {
      Algorithm algorithm = Algorithm.RSA256(privateKey);

      Date exp = new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(NO_OF_DAYS));
      Date iat = new Date(System.currentTimeMillis());

      JWTCreator.Builder jwtBuilder = JWT.create().withIssuedAt(iat).withExpiresAt(exp).withHeader(jwt_header);

      if (jwt_claims != null && !jwt_claims.isEmpty()) {
        jwt_claims.forEach(jwtBuilder::withClaim);
      }
      return jwtBuilder.sign(algorithm);
    }

    public Map<String, Object> verifyJWTToken(String jwtSignedRequest, RSAKey publicKey) {
      Algorithm algorithm = Algorithm.RSA256(publicKey);
      JWTVerifier verifier = JWT.require(algorithm).build();
      verifier.verify(jwtSignedRequest);
      Map<String, Claim> decodedClaims = JWT.decode(jwtSignedRequest).getClaims();
      try {
        return decodedClaims.entrySet().stream().collect(Collectors.toMap(
            k -> k.getKey(), k -> (k.getValue().asString() == null ? k.getValue().asInt() : k.getValue().asString())));
      } catch (Exception ex) {
        System.out.println("Error while converting decoded claims into readable format: ignoring it....");
        return new HashMap<>();
      }
    }

    public String createJwtPayload(String jwtSignedRequest, String client_id, String resource_id) {
      String grant_type = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
      String payload = String.format(
          "client_id=%s&client_assertion_type=%s&client_assertion=%s&grant_type=client_credentials&resource=%s",
          client_id, grant_type, jwtSignedRequest, resource_id);
      return payload;
    }

    public String getIdaToken(String jwtPayload, String token_url) throws IOException, InterruptedException {
      HttpRequest request = HttpRequest.newBuilder()
                                .POST(HttpRequest.BodyPublishers.ofString(jwtPayload))
                                .uri(URI.create(token_url))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      // print status code
      System.out.println("HTTP status code for token request is: " + response.statusCode());
      return response.body();
    }

    protected String generateUuid() {
      UUID uuid = UUID.randomUUID();
      return convertToBase64String(uuid);
    }

    private String convertToBase64String(UUID uuid) {
      byte[] bytes = new byte[16];
      ByteBuffer uuidBytes = ByteBuffer.wrap(bytes);
      uuidBytes.putLong(uuid.getMostSignificantBits());
      uuidBytes.putLong(uuid.getLeastSignificantBits());
      return Base64.encodeBase64URLSafeString(bytes);
    }

    private Certificate[] loadCertificatesFromPem(String filePath) throws IIOException {
      try (InputStream certificateChainAsInputStream =
               Files.newInputStream(Paths.get(filePath), StandardOpenOption.READ)) {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        return certificateFactory.generateCertificates(certificateChainAsInputStream).toArray(new Certificate[0]);
      } catch (Exception ex) {
        throw new IIOException(
            String.format("Failed to load certificate(s) from '%s': %s", filePath, ex.getMessage()), ex);
      }
    }

    private PKCS8EncodedKeySpec loadPKCS8EncodedKeySpecFromPem(String filePath) throws IIOException {
      String fileContent = null;
      try {
        byte[] rawFileContent = Files.readAllBytes(Paths.get(filePath));
        fileContent = new String(rawFileContent, StandardCharsets.UTF_8);
      } catch (Exception ex) {
        throw new IIOException(String.format("Failed to read file '%s': %s", filePath, ex.getMessage()), ex);
      }

      if (fileContent == null) {
        throw new IIOException(String.format("Received NULL content for file '%s'.", filePath));
      }

      // prepare string - some sanitation to avoid too many complications.
      fileContent = fileContent.trim().replace("\n", "").replace("\r", "");

      // ensure file is in PEM format and has only one key
      int lastKeyStartTag = fileContent.lastIndexOf(PEM_PRIVATE_KEY_START);
      int firstKeyEndTag = fileContent.indexOf(PEM_PRIVATE_KEY_END);
      if (lastKeyStartTag != 0 || firstKeyEndTag == -1
          || firstKeyEndTag != fileContent.length() - PEM_PRIVATE_KEY_END.length()) {
        throw new IIOException(String.format(
            "Invalid format for key (expected PEM format) - Only one key per file is allowed and it has to start with %s and end with %s.",
            PEM_PRIVATE_KEY_START, PEM_PRIVATE_KEY_END));
      }

      // remove start and end tag
      String base64DerEncodedPrivateKey =
          fileContent.replace(PEM_PRIVATE_KEY_START, "").replace(PEM_PRIVATE_KEY_END, "");

      // remove any remaining spaces (base64 is expected to be a continuous string, safe to remove spaces)
      base64DerEncodedPrivateKey = base64DerEncodedPrivateKey.replace(" ", "");

      try {
        // decode to raw DER encoded key
        byte[] derEncodedPrivateKey = Base64.decodeBase64(base64DerEncodedPrivateKey);
        // check this changes
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(derEncodedPrivateKey);
        return keySpec;
      } catch (Exception ex) {
        throw new IIOException(
            String.format("Failed to generate key (expected DER encoded key in PKCS8 format): %s", ex.getMessage()),
            ex);
      }
    }
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testisADFS() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, InterruptedException,
                                  CertificateEncodingException {
    assertThat(serviceNowTaskNG.isSupportingErrorFramework()).isTrue();
    // 5 arguments for the script:
    String crt =
        "/Users/namangoenka/harness-core/930-delegate-tasks/src/test/java/io/harness/delegate/task/servicenow/certSupport.pem";
    String key =
        "/Users/namangoenka/harness-core/930-delegate-tasks/src/test/java/io/harness/delegate/task/servicenow/keySupport.pem";
    String client_id = "e840f3b6-a165-40ba-9c6a-a2dd323a7b86";
    String resource_id = "https://ven03172.service-now.com/";
    String env = "harness";

    HarnessAdfsResource harnessAdfsResource = new HarnessAdfsResource();

    System.out.println("Getting certs....");
    Map<String, Object> context = harnessAdfsResource.get_ssl_context_from_x509_cert(crt, key);

    String token_url = harnessAdfsResource.get_ida_token_url(env);

    System.out.println("Creating JWT Header...");
    Map<String, Object> jwt_header = harnessAdfsResource.createJWTHeader((Certificate) context.get("certificate"));
    System.out.println("jwt_header: " + jwt_header);

    System.out.println("Creating JWT Claims...");
    Map<String, String> jwt_claims = harnessAdfsResource.createJwtClaims(client_id, token_url);
    System.out.println("jwt_claims: " + jwt_claims);

    System.out.println("Creating Signed JWT Request...");
    String jwtSignedRequest =
        harnessAdfsResource.createJwtSignedRequest(jwt_claims, jwt_header, (RSAPrivateKey) context.get("privateKey"));
    System.out.println("jwt_signed_request: " + jwtSignedRequest);

    System.out.println("Verifying the Signed JWT Request...");
    Map<String, Object> decodedJwtSignedRequest =
        harnessAdfsResource.verifyJWTToken(jwtSignedRequest, (RSAPublicKey) context.get("publicKey"));
    System.out.println("decoded_jwt_signed_request: " + decodedJwtSignedRequest);

    System.out.println("Creating JWT Payload...");
    String jwtPayload = harnessAdfsResource.createJwtPayload(jwtSignedRequest, client_id, resource_id);
    System.out.println("jwt_payload: " + jwtPayload);

    System.out.println("Getting IDA Token...");
    String idaToken = harnessAdfsResource.getIdaToken(jwtPayload, token_url);
    System.out.println("Getting IDA Token..." + idaToken);
  }
}
