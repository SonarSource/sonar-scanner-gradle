package com.gradle.publish;

import com.gradle.protocols.ServerResponseBase;
import com.gradle.publish.plugin.dep.com.google.gson.Gson;
import com.gradle.publish.plugin.dep.com.google.gson.GsonBuilder;
import com.gradle.publish.plugin.dep.com.google.gson.JsonElement;
import com.gradle.publish.protocols.v1.models.ClientPostRequest;
import com.gradle.publish.protocols.v1.models.publish.PublishActivateRequest;
import com.gradle.publish.protocols.v1.models.publish.PublishActivateResponse;
import com.gradle.publish.protocols.v1.models.publish.PublishArtifact;
import com.gradle.publish.protocols.v1.models.publish.PublishNewVersionRequest;
import com.gradle.publish.protocols.v1.models.publish.PublishNewVersionResponse;
import com.gradle.publish.protocols.v1.models.publish.ValidateNewVersionRequest;
import com.gradle.publish.protocols.v1.models.publish.ValidateNewVersionResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import static java.nio.charset.StandardCharsets.UTF_8;

public class SimpleGradlePluginPortalServer implements AutoCloseable {

  private static final Logger LOGGER = Logging.getLogger(SimpleGradlePluginPortalServer.class);

  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

  private HttpServer server;

  private final List<ClientPostRequest<?>> postRequestHistory = new ArrayList<>();

  public SimpleGradlePluginPortalServer() {
    try {
      server = HttpServer.create(new InetSocketAddress(0), 0);
      server.createContext("/api/v1/publish/versions/validate/", handler("POST", this::handleValidate));
      server.createContext("/api/v1/publish/versions/new/", handler("POST", this::handleNew));
      server.createContext("/api/v1/publish/versions/activate/", handler("POST", this::handleActivate));
      server.createContext("/api/v1/publish/versions/upload/", handler("PUT", this::handleUpload));
      server.createContext("/", this::handleNotFound);
      server.start();
    } catch (IOException e) {
      throw new GradleException("Failed to start SimpleGradlePluginPortalServer", e);
    }
  }

  public String getServerUrl() {
    return "http://localhost:" + server.getAddress().getPort();
  }

  @FunctionalInterface
  public interface IOConsumer<T> {
    void accept(T t) throws IOException;
  }

  public HttpHandler handler(String method, IOConsumer<HttpExchange> postHandler) {
    return exchange -> {
      logRequestHeader(exchange);
      if (method.equals(exchange.getRequestMethod())) {
        postHandler.accept(exchange);
      } else {
        LOGGER.info("Response 405 Method Not Allowed");
        exchange.sendResponseHeaders(405, -1);
      }
    };
  }

  public void handleNotFound(HttpExchange exchange) throws IOException {
    logRequestHeader(exchange);
    LOGGER.info("Response 404 Not Found");
    exchange.sendResponseHeaders(404, -1);
  }

  public void handleValidate(HttpExchange exchange) throws IOException {
    var request = readJsonRequestBody(exchange, ValidateNewVersionRequest.class);
    postRequestHistory.add(request);
    var response = new ValidateNewVersionResponse();
    sendResponse(exchange, 200, response);
  }

  public void handleNew(HttpExchange exchange) throws IOException {
    var request = readJsonRequestBody(exchange, PublishNewVersionRequest.class);
    postRequestHistory.add(request);
    Map<String, String> publishTo = request.getArtifacts().stream()
      .collect(Collectors.toMap(
        PublishArtifact::encode,
        e -> getServerUrl() + "/api/v1/publish/versions/upload/" + e.getHash() + "/" + e.getType()));
    var response = new PublishNewVersionResponse(request.getPluginId(), request.getPluginVersion(), publishTo, null);
    sendResponse(exchange, 200, response);
  }

  public void handleActivate(HttpExchange exchange) throws IOException {
    var request = readJsonRequestBody(exchange, PublishActivateRequest.class);
    postRequestHistory.add(request);
    var response = new PublishActivateResponse("Successfully activated");
    sendResponse(exchange, 200, response);
  }

  public void handleUpload(HttpExchange exchange) throws IOException {
    try (InputStream in = exchange.getRequestBody()) {
      String hash = Hasher.hash(in);
      LOGGER.info("Received file hash: {}\n", hash);
    }
    exchange.sendResponseHeaders(200, -1);
  }

  private static void logRequestHeader(HttpExchange exchange) {
    LOGGER.info("{} {}", exchange.getRequestMethod(), exchange.getRequestURI());
    exchange.getRequestHeaders().getOrDefault("Content-type", List.of())
      .forEach(contentType -> LOGGER.info("Content-type: {}", contentType));
  }

  @Override
  public void close() {
    if (server != null) {
      server.stop(1);
      server = null;
    }
  }

  public <T> T readJsonRequestBody(HttpExchange exchange, Class<T> classOfT) throws IOException {
    try (var in = exchange.getRequestBody()) {
      String jsonString = new String(in.readAllBytes(), UTF_8);
      LOGGER.info("Received JSON:");
      String prettyPrintedJson = gson.toJson(gson.fromJson(jsonString, JsonElement.class)) + "\n";
      LOGGER.info(prettyPrintedJson);
      return gson.fromJson(jsonString, classOfT);
    }
  }

  public void sendResponse(HttpExchange exchange, int rCode, ServerResponseBase response) throws IOException {
    exchange.getResponseHeaders().add("Content-Type", response.getContentType());
    String responseString = response.convertToJsonString();
    var responseBytes = responseString.getBytes(UTF_8);
    LOGGER.info("Response {}, Content-Type: {}", rCode, response.getContentType());
    // log reformated json instead of "responseString"
    String prettyPrintedJson = gson.toJson(gson.fromJson(responseString, JsonElement.class)) + "\n";
    LOGGER.info(prettyPrintedJson);
    exchange.sendResponseHeaders(rCode, responseBytes.length);
    try (var out = exchange.getResponseBody()) {
      out.write(responseBytes);
    }
  }

}
