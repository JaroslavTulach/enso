package org.enso.base.enso_cloud;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.security.PrivateKey;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import org.enso.base.cache.ReloadDetector;
import org.enso.base.cache.ResponseTooLargeException;
import org.enso.base.net.URISchematic;
import org.enso.base.net.URIWithSecrets;

/** Makes HTTP requests with secrets in either header or query string. */
public final class EnsoSecretHelper extends SecretValueResolver {
  private static EnsoHTTPResponseCache cache;

  /**
   * Gets a JDBC connection resolving EnsoKeyValuePair into the properties.
   *
   * @param properties properties in the form of {@code List<Pair<String, HideableValue>>}
   */
  public static Connection getJDBCConnection(String url, Map<String, HideableValue> properties)
      throws SQLException {
    var javaProperties = new Properties();
    for (var pair : properties.entrySet()) {
      HideableValue value = pair.getValue();
      // Special handling for PrivateKey parameter.
      if (value instanceof HideableImpl.InterpretAsPrivateKey(HideableValue innerValue)) {
        String rawKey = resolveValue(innerValue);
        PrivateKey key = HideableImpl.InterpretAsPrivateKey.decodePrivateKey(rawKey);
        javaProperties.put(pair.getKey(), key);
      } else {
        javaProperties.setProperty(pair.getKey(), resolveValue(pair.getValue()));
      }
    }

    return DriverManager.getConnection(url, javaProperties);
  }

  /**
   * Gets the actual URI with all secrets resolved, so that it can be used to create a request. This
   * value should never be returned to Enso.
   */
  private static URI resolveURI(URIWithSecrets uri) {
    try {
      Map<String, String> resolvedQueryParameters =
          uri.queryParameters().entrySet().stream()
              .map(p -> new AbstractMap.SimpleEntry<>(p.getKey(), resolveValue(p.getValue())))
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
      URISchematic resolvedSchematic = new URISchematic(uri.baseUri(), resolvedQueryParameters);
      return resolvedSchematic.build();
    } catch (URISyntaxException e) {
      // Here we don't display the message of the exception to avoid risking it may leak any
      // secrets.
      // This should never happen in practice.
      throw new IllegalStateException(
          "Unexpectedly unable to build a valid URI from the base URI: "
              + uri
              + ": "
              + e.getClass().getCanonicalName());
    }
  }

  /** Makes a request with secrets in the query string or headers. * */
  public static EnsoHttpResponse makeRequest(
      HttpClient client,
      Builder origBuilder,
      URIWithSecrets uri,
      Map<String, HideableValue> headers,
      boolean useCache)
      throws IllegalArgumentException,
          IOException,
          InterruptedException,
          ResponseTooLargeException {
    // Clone incoming builder so we can't leak secrets through it
    var builder = origBuilder.copy();

    // Build a new URI with the query arguments.
    URI resolvedURI = resolveURI(uri);

    Map<String, String> resolvedHeaders =
        headers.entrySet().stream()
            .map(
                pair -> {
                  return new AbstractMap.SimpleEntry<>(
                      pair.getKey(), resolveValue(pair.getValue()));
                })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    var requestMaker =
        new RequestMaker(client, builder, uri, resolvedURI, headers, resolvedHeaders);

    if (!useCache) {
      return requestMaker.makeRequest();
    } else {
      return getOrCreateCache().makeRequest(requestMaker);
    }
  }

  public static void deleteSecretFromCache(String secretId) {
    EnsoSecretReader.INSTANCE.removeFromCache(secretId);
  }

  private static class RequestMaker implements EnsoHTTPResponseCache.RequestMaker {
    private final HttpClient client;
    private final Builder builder;
    private final URIWithSecrets uri;
    private final URI resolvedURI;
    private final Map<String, HideableValue> headers;
    private final Map<String, String> resolvedHeaders;

    RequestMaker(
        HttpClient client,
        Builder builder,
        URIWithSecrets uri,
        URI resolvedURI,
        Map<String, HideableValue> headers,
        Map<String, String> resolvedHeaders) {
      this.client = client;
      this.builder = builder;
      this.uri = uri;
      this.resolvedURI = resolvedURI;
      this.headers = headers;
      this.resolvedHeaders = resolvedHeaders;
    }

    @Override
    public EnsoHttpResponse makeRequest() throws IOException, InterruptedException {
      boolean hasSecrets =
          uri.containsSecrets()
              || headers.values().stream().anyMatch(HideableValue::containsSecrets);
      if (hasSecrets) {
        if (resolvedURI.getScheme() == null) {
          throw new IllegalArgumentException("The URI must have a scheme.");
        }

        if (!resolvedURI.getScheme().equalsIgnoreCase("https")) {
          throw new IllegalArgumentException(
              "Secrets are not allowed in HTTP connections, use HTTPS instead.");
        }
      }

      builder.uri(resolvedURI);

      var resolvedHeadersWithDefaults = withDefaultHeaders(resolvedHeaders);
      for (var resolvedHeader : resolvedHeadersWithDefaults.entrySet()) {
        builder.header(resolvedHeader.getKey(), resolvedHeader.getValue());
      }

      // Build and Send the request.
      var httpRequest = builder.build();
      var bodyHandler = HttpResponse.BodyHandlers.ofInputStream();
      var javaResponse = client.send(httpRequest, bodyHandler);

      URI renderedURI = uri.render();

      var decodedBody = decodeContentEncoding(javaResponse.body(), javaResponse.headers());

      return new EnsoHttpResponse(
          renderedURI, javaResponse.headers(), decodedBody, javaResponse.statusCode());
    }

    /** Sorts the header by header name and value. */
    @Override
    public String hashKey() {
      // Include default headers in cache key to reflect actual request.
      var sortedHeaders = withDefaultHeaders(resolvedHeaders);
      List<String> keyStrings = new ArrayList<>(sortedHeaders.size() + 1);
      keyStrings.add(resolvedURI.toString());

      for (var resolvedHeader : sortedHeaders.entrySet()) {
        keyStrings.add(resolvedHeader.getKey());
        keyStrings.add(resolvedHeader.getValue());
      }

      return Integer.toHexString(Arrays.deepHashCode(keyStrings.toArray()));
    }

    @Override
    public EnsoHttpResponse reconstructResponseFromCachedStream(
        InputStream inputStream, EnsoHTTPResponseCache.Metadata metadata) {
      URI renderedURI = uri.render();

      return new EnsoHttpResponse(
          renderedURI, metadata.headers(), inputStream, metadata.statusCode());
    }
  }

  public static EnsoHTTPResponseCache getOrCreateCache() {
    if (cache == null) {
      cache = new EnsoHTTPResponseCache();
    }
    return cache;
  }

  /** Visible for testing */
  public static int getEnsoSecretReaderCacheSize() {
    return EnsoSecretReader.INSTANCE.getCacheSize();
  }

  /** Visible for testing */
  public static void simulateEnsoSecretReaderReload() {
    ReloadDetector.simulateReloadTestOnly(EnsoSecretReader.INSTANCE);
  }

  private static InputStream decodeContentEncoding(InputStream stream, HttpHeaders headers)
      throws IOException {
    String encoding = headers.firstValue("content-encoding").map(String::toLowerCase).orElse("");
    if ("gzip".equals(encoding)) {
      return new GZIPInputStream(stream);
    }
    return stream;
  }

  private static Map<String, String> withDefaultHeaders(Map<String, String> headers) {
    boolean hasAccept = false;
    boolean hasAcceptEncoding = false;

    for (var h : headers.entrySet()) {
      String name = h.getKey();
      if ("accept".equalsIgnoreCase(name)) {
        hasAccept = true;
      } else if ("accept-encoding".equalsIgnoreCase(name)) {
        hasAcceptEncoding = true;
      }
      if (hasAccept && hasAcceptEncoding) {
        return headers;
      }
    }

    var augmented = new TreeMap<String, String>();
    augmented.putAll(headers);
    if (!hasAccept) {
      augmented.put("Accept", "*/*");
    }
    if (!hasAcceptEncoding) {
      augmented.put("Accept-Encoding", "gzip");
    }
    return augmented;
  }
}
