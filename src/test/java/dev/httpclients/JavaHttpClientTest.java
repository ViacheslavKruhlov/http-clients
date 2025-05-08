package dev.httpclients;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaHttpClientTest {

    @RegisterExtension
    static WireMockExtension wm =
            WireMockExtension.newInstance()
                             .options(WireMockConfiguration.wireMockConfig()
                                                           .dynamicPort())
                             .build();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
                                                            .version(HttpClient.Version.HTTP_1_1)
                                                            .connectTimeout(Duration.ofSeconds(5L))
                                                            .build();

    @Test
    void sendRequest_ShouldExecutedSuccessfullyWithStatus200() {
        final String httpBaseUrl = wm.getRuntimeInfo().getHttpBaseUrl();
        final String url = "/test";

        wm.stubFor(WireMock.get(url).willReturn(WireMock.aResponse()
                                                        .withStatus(200)
                                                        .withBody("Hello, world!")));

        final HttpResponse<byte[]> result = HTTP_CLIENT.sendAsync(HttpRequest.newBuilder(URI.create(httpBaseUrl + url))
                                                                             .GET()
                                                                             .build(),
                                                                  HttpResponse.BodyHandlers.ofByteArray())
                                                       .thenApply(response -> {
                                                           System.out.println(Thread.currentThread().getName());

                                                           return response;
                                                       })
                                                       .join();
        assertEquals(200, result.statusCode());

        System.out.println(new String(result.body()));
    }

    @Test
    void sendRequest_ShouldThrowCompletionExceptionWithConnectExceptionCause_WhenConnectionToResourceFailed() {
        final CompletableFuture<HttpResponse<byte[]>> result =
                HTTP_CLIENT.sendAsync(HttpRequest.newBuilder(URI.create("http://qwerty123.com.net.ua/test"))
                                                 .GET()
                                                 .build(),
                                      HttpResponse.BodyHandlers.ofByteArray());

        final CompletionException ex = Assertions.assertThrows(CompletionException.class, result::join);

        assertEquals(ConnectException.class, ex.getCause().getClass());
    }

    @Test
    void sendRequest_ShouldThrowCompletionExceptionWithHttpConnectTimeoutExceptionCause_WhenConnectionTimeout() {
        final String httpBaseUrl = wm.getRuntimeInfo().getHttpBaseUrl();

        final HttpClient client = HttpClient.newBuilder()
                                            .version(HttpClient.Version.HTTP_1_1)
                                            .connectTimeout(Duration.ofMillis(1L))
                                            .build();

        final CompletableFuture<HttpResponse<byte[]>> result =
                client.sendAsync(HttpRequest.newBuilder(URI.create(httpBaseUrl))
                                            .GET()
                                            .build(),
                                 HttpResponse.BodyHandlers.ofByteArray())
                      .thenApply(response -> {
                          System.out.println(Thread.currentThread().getName());

                          return response;
                      });

        final CompletionException ex = Assertions.assertThrows(CompletionException.class, result::join);

        final Throwable cause = ex.getCause();

        assertEquals(HttpConnectTimeoutException.class, cause.getClass());
        assertEquals("HTTP connect timed out", cause.getMessage());
    }

    @Test
    void sendRequest_ShouldThrowCompletionExceptionWithHttpTimeoutExceptionCause_WhenResponseTimeout() {
        final String httpBaseUrl = wm.getRuntimeInfo().getHttpBaseUrl();
        final String url = "/test";

        wm.stubFor(WireMock.get(url).willReturn(WireMock.aResponse()
                                                        .withFixedDelay((int) TimeUnit.SECONDS.toMillis(5L))
                                                        .withStatus(200)
                                                        .withBody("Hello, world!")));

        final CompletableFuture<HttpResponse<byte[]>> result =
                HTTP_CLIENT.sendAsync(HttpRequest.newBuilder(URI.create(httpBaseUrl + url))
                                                 .GET()
                                                 .timeout(Duration.ofSeconds(3L))
                                                 .build(),

                                      HttpResponse.BodyHandlers.ofByteArray()
                                     )
                           .thenApply(response -> {
                               System.out.println(Thread.currentThread().getName());

                               return response;
                           });

        final CompletionException ex = Assertions.assertThrows(CompletionException.class, result::join);

        final Throwable cause = ex.getCause();

        assertEquals(HttpTimeoutException.class, cause.getClass());
        assertEquals("request timed out", cause.getMessage());
    }

    @Test
    void name() {
        HTTP_CLIENT.sendAsync(HttpRequest.newBuilder(URI.create("https://uat-certificial-documents.s3-us-west-2.amazonaws.com/document/certificial6@mailinator.com/policyshare/COI-02-09-2024-ACORD25-ZS2V.pdf"))
                                         .GET()
                                         .timeout(Duration.ofSeconds(30L))
                                         .build(),

                              HttpResponse.BodyHandlers.ofInputStream()
                             )
                   .thenAccept(response -> {
                       try {
                           final Path tempFile = Files.createTempFile("COI-02-09-2024-ACORD25-ZS2V", ".pdf");
                           System.out.println("File path: " + tempFile);
                           try (final FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
                               response.body().transferTo(fos);
                           }
                       } catch (IOException e) {
                           throw new RuntimeException(e);
                       }
                   })
                   .join();
    }

    @Test
    void sendRequest_ShouldExecutedSuccessfullyWithStatus2001() throws InterruptedException {
        final String httpBaseUrl = wm.getRuntimeInfo().getHttpBaseUrl();
        final String url = "/test";

        wm.stubFor(WireMock.get(url).willReturn(WireMock.aResponse()
                                                        .withStatus(200)
                                                        .withFixedDelay(3000)
                                                        .withBody("Hello, world!")));

        System.setProperty("jdk.httpclient.connectionPoolSize", "40");
        final HttpClient client = HttpClient.newBuilder()
                                            .version(HttpClient.Version.HTTP_1_1)
                                            .connectTimeout(Duration.ofSeconds(5L))
                                            .build();

        Runnable send = () -> {
            final long start = System.currentTimeMillis();
            final String name = Thread.currentThread().getName();

            try {
                final HttpResponse<byte[]> result = client.send(HttpRequest.newBuilder(URI.create(httpBaseUrl + url))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofByteArray());

                assertEquals(200, result.statusCode());

                System.out.println("'%s' finished execution. Took %d ms".formatted(name, System.currentTimeMillis() - start));
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        for (int i = 0; i < 40; i++) {
            new Thread(send, "thread-" + i).start();
        }

        Thread.currentThread().join();
    }

    @Test
    void name1() {
        final ByteArrayInputStream inputStream = new ByteArrayInputStream("String test".getBytes());

        System.out.println(inputStream.available());
        System.out.println(new String(inputStream.readAllBytes()));
        System.out.println(inputStream.available());
        inputStream.reset();
        System.out.println(inputStream.available());
        System.out.println(new String(inputStream.readAllBytes()));
        inputStream.reset();
        System.out.println(new String(inputStream.readAllBytes()));
    }

    @Test
    void name2() throws JsonProcessingException {
        final ObjectMapper objectMapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                                            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                                                            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                                            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                                                            .registerModule(new JavaTimeModule());

        Map<String, String> map = objectMapper.readValue("{\"time\":1710499506458}", new TypeReference<>() {});
    }

    @Test
    void name3() {
        final String s = "[test, test1]";
        System.out.println(s.substring(1, s.length()));
    }
}
