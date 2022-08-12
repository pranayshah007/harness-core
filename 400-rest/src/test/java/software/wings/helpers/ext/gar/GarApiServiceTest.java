// package software.wings.helpers.ext.gar;
//
// import io.harness.artifacts.gar.service.GARApiServiceImpl;
// import io.harness.artifacts.gcr.beans.GcrInternalConfig;
// import org.junit.Before;
// import org.junit.Rule;
// import software.wings.WingsBaseTest;
// import software.wings.service.mappers.artifact.GcrConfigToInternalMapper;
//
// import static org.mockito.ArgumentMatchers.anyString;
// import static org.mockito.Mockito.spy;
// import static org.mockito.Mockito.when;
// import com.github.tomakehurst.wiremock.client.WireMock;
// import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
// import com.github.tomakehurst.wiremock.junit.WireMockRule;
//
// public class GarApiServiceTest extends WingsBaseTest {
//     GARApiServiceImpl garApiServiceImpl = spy(new GARApiServiceImpl());
//
//     @Rule
//     public WireMockRule wireMockRule = new WireMockRule(
//             WireMockConfiguration.wireMockConfig().usingFilesUnderDirectory("400-rest/src/test/resources").port(0));
//     private String url;
//     String basicAuthHeader = "auth";
//     GcrInternalConfig gcpInternalConfig;
//
//     @Before
//     public void setUp() {
//         url = "localhost:" + wireMockRule.port();
//         gcpInternalConfig = GcrConfigToInternalMapper.toGcpInternalConfig(url, basicAuthHeader);
//         wireMockRule.stubFor(WireMock.get(WireMock.urlEqualTo("/v2/someImage/tags/list"))
//                 .withHeader("Authorization", equalTo("auth"))
//                 .willReturn(aResponse().withStatus(200).withBody(
//                         "{\"name\":\"someImage\",\"tags\":[\"v1\",\"v2\",\"latest\"]}")));
//
//         wireMockRule.stubFor(WireMock.get(WireMock.urlEqualTo("/v2/noImage/tags/list"))
//                 .withHeader("Authorization", equalTo("auth"))
//                 .willReturn(aResponse().withStatus(404)));
//
//         wireMockRule.stubFor(
//                 WireMock.get(WireMock.urlEqualTo("/v2/invalidProject/tags/list"))
//                         .withHeader("Authorization", equalTo("auth"))
//                         .willReturn(aResponse().withStatus(403).withBody(
//                                 "{\"errors\":[{\"code\":\"UNKNOWN\",\"message\":\"Project 'project:project-name' not
//                                 found or deleted.\"}]}")));
//
//         wireMockRule.stubFor(WireMock.get(WireMock.urlEqualTo("/v2/teapot/tags/list"))
//                 .withHeader("Authorization", equalTo("auth"))
//                 .willReturn(aResponse().withStatus(418).withBody("I'm a teapot")));
//
//         when(gcrService.getUrl(anyString())).thenReturn("http://" + url);
//     }
//
// }
