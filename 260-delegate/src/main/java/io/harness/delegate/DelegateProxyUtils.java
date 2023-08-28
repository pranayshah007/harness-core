package io.harness.delegate;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

public class DelegateProxyUtils {
  public static void setupProxyConfig() {
    System.setProperty("proxyScheme", "http");
    System.setProperty("http.proxyHost", "34.71.168.0");
    System.setProperty("http.proxyPort", "3128");
    System.setProperty("https.proxyHost", "34.71.168.0");
    System.setProperty("https.proxyPort", "3128");

    final String proxyUser = System.getenv("PROXY_USER");
    if (isNotBlank(proxyUser)) {
      System.setProperty("http.proxyUser", proxyUser);
      System.setProperty("https.proxyUser", proxyUser);

      // How will anonymous proxy: proxy with only username, will work ?
      final String proxyPassword = System.getenv("PROXY_PASSWORD");
      if (isNotBlank(proxyPassword)) {
        System.setProperty("http.proxyPassword", proxyPassword);
        System.setProperty("https.proxyPassword", proxyPassword);
        Authenticator.setDefault(new Authenticator() {
          @Override
          protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(proxyUser, proxyPassword.toCharArray());
          }
        });
      }
    }
  }
}
