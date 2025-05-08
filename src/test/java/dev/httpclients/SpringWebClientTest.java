package dev.httpclients;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;

class SpringWebClientTest {

    @RegisterExtension
    static WireMockExtension wm =
            WireMockExtension.newInstance()
                             .options(WireMockConfiguration.wireMockConfig()
                                                           .dynamicPort())
                             .build();

    private static final WebClient WEB_CLIENT = WebClient.builder()
                                                         .clientConnector(ClientConnector.CLIENT_HTTP_CONNECTOR)
                                                         .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                                         .build();

    @Test
    void sendRequest_ShouldExecutedSuccessfullyWithStatus200() throws InterruptedException {
        final String httpBaseUrl = wm.getRuntimeInfo().getHttpBaseUrl();
        final String url = "/test";

        wm.stubFor(WireMock.get(url).willReturn(WireMock.aResponse()
                                                        .withStatus(200)
                                                        .withBody("Hello, world!")));

        WEB_CLIENT.method(HttpMethod.GET)
                  .uri(httpBaseUrl + url)
                  .exchangeToMono(resp -> {
                      System.out.println("The response status code: " + resp.statusCode());

                      return resp.bodyToMono(String.class);
                  })
                  .doOnNext(body -> System.out.println("DoOnNext the response body: " + body)) // executed
                  .doOnError(ex -> {
                      System.out.println("DoOnError the response error: " + ex.getMessage()); // not executed
                      ex.printStackTrace();
                  })
                  .subscribe();

        TimeUnit.SECONDS.sleep(2);
    }

    @Test
    void sendRequest_ShouldEmptyResponse() throws InterruptedException {
        final String httpBaseUrl = wm.getRuntimeInfo().getHttpBaseUrl();
        final String url = "/test";

        wm.stubFor(WireMock.get(url).willReturn(WireMock.aResponse()
                                                        .withStatus(400)));

        final Mono<ResponseEntity<String>> stringMono = WEB_CLIENT.method(HttpMethod.GET)
                                                                  .uri(httpBaseUrl + url)
                                                                  .exchangeToMono(resp -> {
                                                                      System.out.println("The response status code: " + resp.statusCode());

                                                                      return resp.statusCode().isError()
                                                                             ? resp.bodyToMono(String.class)
                                                                                       .map(body -> {
                                                                                           throw new IllegalArgumentException(body);})
                                                                             : resp.toEntity(String.class);
                                                                  })
                                                                  .doOnNext(body -> System.out.println("DoOnNext the response body: " + body)) // executed
                                                                  .doOnError(ex -> {
                                                                      System.out.println("DoOnError the response error: " + ex.getMessage()); // not executed
                                                                      ex.printStackTrace();
                                                                  });
        //                                                                  .switchIfEmpty(Mono.empty());

        final ResponseEntity<String> block = stringMono.block();

        System.out.println("END test. the response body: " + block.getBody());

        TimeUnit.SECONDS.sleep(2);
    }

    @Test
    void sendRequest_ShouldFailedWithExchangeToMono() throws InterruptedException {
        final String httpBaseUrl = wm.getRuntimeInfo().getHttpBaseUrl();
        final String url = "/test";

        wm.stubFor(WireMock.get(url).willReturn(WireMock.aResponse()
                                                        .withStatus(400)
                                                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                                        .withBody("""
                                                                  {
                                                                  "status": 400,
                                                                  "errorMsg": "Bad request"
                                                                  }""")));

        WEB_CLIENT.method(HttpMethod.GET)
                  .uri(httpBaseUrl + url)
                  .bodyValue(new Response(200, "Hello"))
                  .exchangeToMono(resp -> {
                      System.out.println("The response status code: " + resp.statusCode());

                      if (resp.statusCode().isError()) {
                          return resp.createError();
                      }

                      return resp.bodyToMono(Response.class);
                  })
                  .doOnNext(body -> System.out.println("DoOnNext the response body: " + body)) // not executed
                  .doOnError(WebClientResponseException.class, ex -> {
                      System.out.println("DoOnError WebClientResponseException the response error: " + ex.getMessage()); // executed
                      System.out.println("DoOnError the response body: " + ex.getResponseBodyAsString());
                      System.out.println("DoOnError the response body mapped to object: " + ex.getResponseBodyAs(ErrorResponse.class));
                      ex.printStackTrace();
                  })
                  .doOnError(e -> !(e instanceof WebClientResponseException), ex -> {
                      System.out.println("DoOnError the response error: " + ex.getMessage()); // not executed
                      ex.printStackTrace();
                  })
                  .subscribe();

        TimeUnit.SECONDS.sleep(2);
    }

    @Test
    void sendRequest_ShouldFailedWithRetrieveMethod() throws InterruptedException {
        final String httpBaseUrl = wm.getRuntimeInfo().getHttpBaseUrl();
        final String url = "/test";

        wm.stubFor(WireMock.get(url).willReturn(WireMock.aResponse()
                                                        .withStatus(200)
                                                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                                        .withBody("""
                                                                  {
                                                                  "status": 400,
                                                                  "errorMsg": "Bad request"
                                                                  }""")));

        WEB_CLIENT.method(HttpMethod.GET)
                  .uri(httpBaseUrl + url)
                  .bodyValue(new Response(200, "Hello"))
                  .retrieve()
                  .toEntity(byte[].class)
                  .doOnEach(resp -> System.out.println("Response onEach: " + new String(resp.get().getBody())))
                  .doOnNext(body -> System.out.println("DoOnNext the response body: " + body)) // not executed
                  .doOnError(WebClientResponseException.class, ex -> {
                      System.out.println("DoOnError WebClientResponseException the response error: " + ex.getMessage()); // executed
                      System.out.println("DoOnError the response body: " + ex.getResponseBodyAsString());
                      System.out.println("DoOnError the response body mapped to object: " + ex.getResponseBodyAs(ErrorResponse.class));
                      ex.printStackTrace();
                  })
                  .doOnError(ex -> {
                      System.out.println("DoOnError the response error: " + ex.getMessage()); // not executed
                      ex.printStackTrace();
                  })
                  .subscribe();

        TimeUnit.SECONDS.sleep(2);
    }

    @Test
    void sendRequest_ShouldSuccessWithRetrieveMethod() throws InterruptedException {
        final String httpBaseUrl = wm.getRuntimeInfo().getHttpBaseUrl();
        final String url = "/test";

        wm.stubFor(WireMock.get(url).willReturn(WireMock.aResponse()
                                                        .withStatus(400)
                                                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                                        .withBody("""
                                                                  {
                                                                  "status": 400,
                                                                  "errorMsg": "Bad request"
                                                                  }""")));

        WEB_CLIENT.method(HttpMethod.GET)
                  .uri(httpBaseUrl + url)
                  .bodyValue(new Response(200, "Hello"))
                  .exchangeToMono(resp -> {
                      System.out.println("The response status code: " + resp.statusCode());

                      if (resp.statusCode().isError()) {
                          return resp.createError();
                      }

                      return resp.bodyToMono(Response.class);
                  })
                  .doOnNext(body -> System.out.println("DoOnNext the response body: " + body)) // executed
                  .doOnError(WebClientResponseException.class, ex -> {
                      System.out.println("DoOnError WebClientResponseException the response error: " + ex.getMessage()); // executed
                      System.out.println("DoOnError the response body: " + ex.getResponseBodyAsString());
                      System.out.println("DoOnError the response body mapped to object: " + ex.getResponseBodyAs(ErrorResponse.class));
                      ex.printStackTrace();
                  })
                  .doOnError(ex -> {
                      System.out.println("DoOnError the response error: " + ex.getMessage()); // not executed
                      ex.printStackTrace();
                  })
                  .subscribe();

        TimeUnit.SECONDS.sleep(2);
    }

    @Test
    void sendRequest_ShouldFailExecutionStatus400WithException() throws InterruptedException {
        final String httpBaseUrl = wm.getRuntimeInfo().getHttpBaseUrl();
        final String url = "/test";

        wm.stubFor(WireMock.get(url).willReturn(WireMock.aResponse()
                                                        .withStatus(500)
                                                        .withBody("Hello, world!")));

        WEB_CLIENT.method(HttpMethod.GET)
                  .uri(httpBaseUrl + url)
                  .retrieve()
                  .bodyToMono(String.class)
                  .doOnNext(body -> System.out.println("DoOnNext the response body: " + body)) // not executed
                  .doOnError(WebClientResponseException.class, ex -> {
                      System.out.println("DoOnError WebClientResponseException the response error: " + ex.getMessage()); // executed
                      System.out.println("DoOnError the response body: " + ex.getResponseBodyAsString());
                      System.out.println("DoOnError the response status code: " + ex.getStatusCode());
                      ex.printStackTrace();
                  })
                  .subscribe();

        System.out.println("Test finished");
        TimeUnit.SECONDS.sleep(2);
    }

    @Test
    void test() throws InterruptedException {
        final String httpBaseUrl = wm.getRuntimeInfo().getHttpBaseUrl();
        final String url = "/test";

        wm.stubFor(WireMock.get(url).willReturn(WireMock.aResponse()
                                                        .withStatus(300)));

        WEB_CLIENT.method(HttpMethod.GET)
                  .uri(httpBaseUrl + url)
                  .retrieve()
                  .toEntity(String.class)
                  .doOnNext(resp -> System.out.println("DoOnNext -> Response: " + resp.getBody()))
                  .doOnError(ex -> {
                      System.out.println("DoOnError the response error: " + ex.toString()); // not executed
                  })
                  .subscribe();

        TimeUnit.SECONDS.sleep(2);
    }

    // retry with new auth header
    //    public ExchangeFilterFunction retryOn401() {
    //        return (request, next) -> next.exchange(request)
    //                                      .flatMap((Function<ClientResponse, Mono<ClientResponse>>) clientResponse -> {
    //                                          if (clientResponse.statusCode() == HttpStatus.UNAUTHORIZED) {
    //                                              return authClient.getUpdatedToken() //here you get a Mono with a new & valid token
    //                                                               .map(token -> ClientRequest
    //                                                                       .from(request)
    //                                                                       .headers(headers -> headers.replace("Authorization", Collections.singletonList("Bearer " + token)))
    //                                                                       .build())
    //                                                               .flatMap(next::exchange);
    //                                          } else {
    //                                              return Mono.just(clientResponse);
    //                                          }
    //                                      });
    //    }

    @Test
    void sendRequest_ShouldRetryResendOnError() throws InterruptedException {
        final String httpBaseUrl = wm.getRuntimeInfo().getHttpBaseUrl();
        final String url = "/test";

        final AtomicInteger counter = new AtomicInteger(1);
        wm.stubFor(WireMock.get(url).willReturn(WireMock.aResponse()
                                                        .withStatus(401)
                                                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                                        .withBody("""
                                                                  {
                                                                  "status": 401,
                                                                  "errorMsg": "Bad request"
                                                                  }"""))
                           .willReturn(WireMock.aResponse()
                                               .withStatus(200)
                                               .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                               .withBody("""
                                                         {
                                                         "status": 200,
                                                         "message": "Success"
                                                         }""")));

        final WebClient wc = WebClient.builder()
                                      .clientConnector(ClientConnector.CLIENT_HTTP_CONNECTOR)
                                      .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                      .filter((request, next) ->
                                                      next.exchange(request)
                                                          .elapsed()
                                                          .flatMap(result -> {
                                                              final ClientResponse clientResponse = result.getT2();
                                                              System.out.println("Logging requests took " + result.getT1() + "ms and responses: " + request + clientResponse);
                                                              if (clientResponse.statusCode().equals(HttpStatus.UNAUTHORIZED)) {
                                                                  return next.exchange(ClientRequest.from(request)
                                                                                                    .headers(headers -> headers.put("Authorization", List.of("Bearer token" + counter.getAndIncrement())))
                                                                                                    .build());
                                                              }

                                                              return Mono.just(clientResponse);
                                                          })
                                             )
                                      .build();

        wc.method(HttpMethod.GET)
          .uri(httpBaseUrl + url)
          .bodyValue(new Response(200, "Hello"))
          .exchangeToMono(resp -> {
              System.out.println("The response status code: " + resp.statusCode());

              if (resp.statusCode().isError()) {
                  return resp.createError();
              }

              return resp.bodyToMono(Response.class);
          })
          .doOnNext(body -> System.out.println("DoOnNext the response body: " + body))
          .doOnError(WebClientResponseException.class, ex -> {
              System.out.println("DoOnError the response error: " + ex.getClass());
          })
          .doOnError(WebClientResponseException.class, ex -> {
              System.out.println("DoOnError WebClientResponseException the response body: " + ex.getResponseBodyAsString());
          })
          .block();

        TimeUnit.SECONDS.sleep(3);

        wm.getAllServeEvents();
    }

    TestClient testClient1 = null;

    @Test
    void sendRequest_ShouldRetryResendOnError_AvoidCycling() throws InterruptedException {
        final String httpBaseUrl = wm.getRuntimeInfo().getHttpBaseUrl();
        final String url = "/image";
        final String auth = "/auth";

        final UUID mockId = UUID.randomUUID();

        final AtomicInteger counter = new AtomicInteger(1);
        wm.stubFor(WireMock.post(url)
                           .withId(mockId)
                           .willReturn(WireMock.aResponse()
                                               .withStatus(401)
                                               .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                               .withBody("""
                                                         {
                                                         "status": 401,
                                                         "errorMsg": "Bad request"
                                                         }""")));
        wm.stubFor(WireMock.post(auth).willReturn(WireMock.aResponse()
                                                          .withStatus(402)
                                                          .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                                          .withBody("""
                                                                    {
                                                                    "status": 200,
                                                                    "errorMsg": "Bad request"
                                                                    }""")));

        final WebClient wc1 = WebClient.builder()
                                       .baseUrl(httpBaseUrl)
                                       .build();

        final WebClient wc = WebClient.builder()
                                      .baseUrl(httpBaseUrl)
                                      .clientConnector(ClientConnector.CLIENT_HTTP_CONNECTOR)
                                      .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                      .filter((request, next) ->
                                                      next.exchange(request)
                                                          .elapsed()
                                                          .flatMap(result -> {
                                                              final ClientResponse clientResponse = result.getT2();
                                                              System.out.println("Logging requests took " + result.getT1() + "ms and responses: " + clientResponse.request().getURI() + " status: " + clientResponse.statusCode());
                                                              if (clientResponse.statusCode().equals(HttpStatus.UNAUTHORIZED)
                                                                  && !clientResponse.request().getURI().getRawPath().contains(auth)) {
                                                                  System.out.println("Logging error requests took " + result.getT1() + "ms and responses: " + result.getT2());

                                                                  wm.editStub(WireMock.post(url)
                                                                                      .withId(mockId)
                                                                                      .willReturn(WireMock.aResponse()
                                                                                                          .withStatus(402)
                                                                                                          .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                                                                                          .withBody("""
                                                                                                                    {
                                                                                                                    "status": 402,
                                                                                                                    "errorMsg": "Bad request"
                                                                                                                    }""")));

                                                                  return testClient1.authenticate()
                                                                                    .doOnError(ex -> System.out.println("Fail to retrieve token"))
                                                                                    .elapsed()
                                                                                    .flatMap(result1 -> {
                                                                                        final ResponseEntity<String> responseEntity = result1.getT2();
                                                                                        System.out.println("Logging Inside Retry requests took " + result1.getT1() + "ms and responses: " + responseEntity);

                                                                                        return next.exchange(ClientRequest.from(request)
                                                                                                                          .headers(headers -> headers.put("Authorization", List.of("Bearer token" + counter.getAndIncrement())))
                                                                                                                          .build())
                                                                                                   .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1))
                                                                                                                   .filter(throwable -> {
                                                                                                                       if (throwable instanceof WebClientResponseException ex) {
                                                                                                                           return !ex.getStatusCode().equals(HttpStatus.UNAUTHORIZED)
                                                                                                                                  && ex.getStatusCode().isError();
                                                                                                                       }

                                                                                                                       return false;
                                                                                                                   }));
                                                                                    })
                                                                                    .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1))
                                                                                                    .filter(throwable -> {
                                                                                                        if (throwable instanceof WebClientResponseException ex) {
                                                                                                            return !ex.getStatusCode().equals(HttpStatus.UNAUTHORIZED)
                                                                                                                   && ex.getStatusCode().isError();
                                                                                                        }

                                                                                                        return false;
                                                                                                    }));
                                                              }

                                                              return Mono.just(clientResponse);
                                                          })
                                                          .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1))
                                                                          .filter(throwable -> {
                                                                              if (throwable instanceof WebClientResponseException ex) {
                                                                                  return !ex.getStatusCode().equals(HttpStatus.UNAUTHORIZED)
                                                                                         && ex.getStatusCode().isError();
                                                                              }

                                                                              return false;
                                                                          }))
                                             )
                                      .build();

        testClient1 = HttpServiceProxyFactory.builderFor(WebClientAdapter.create(wc))
                                             .build()
                                             .createClient(TestClient.class);

        try {
            final LinkedMultiValueMap<String, Object> req = new LinkedMultiValueMap<>();
            final URL resource = getClass().getClassLoader().getResource("test.csv");
            final FileSystemResource file = new FileSystemResource(Path.of(resource.toURI()));
            req.put("file", List.of(file));
            req.put("status", List.of("some status"));
            testClient1.test(req)
                       .block();
        } catch (Exception e) {
            e.printStackTrace();
        }

        wm.getAllServeEvents();
    }

    @Test
    void sendMultipartFormData() throws InterruptedException, URISyntaxException {
        final String url = "https://teg-local.requestcatcher.com";

        final WebClient wc = WebClient.builder()
                                      .baseUrl(url)
                                      .clientConnector(ClientConnector.CLIENT_HTTP_CONNECTOR)
                                      .build();

        final TestClient client = HttpServiceProxyFactory.builderFor(WebClientAdapter.create(wc))
                                                         .build().createClient(TestClient.class);
        final LinkedMultiValueMap<String, Object> req = new LinkedMultiValueMap<>();
        final URL resource = getClass().getClassLoader().getResource("test.csv");
        final FileSystemResource file = new FileSystemResource(Path.of(resource.toURI()));
        req.put("file", List.of(file));
        req.put("status", List.of("some status"));
        client.test(req).block();
    }

    @Test
    void testMono() {
        String s1 = Mono.just("Hello").block();
        String s2 = Mono.<String>justOrEmpty(null).block();

        System.out.println("s1: " + s1);
        System.out.println("s2: " + s2);
    }

    private interface TestClient {

        @PostExchange(contentType = MediaType.MULTIPART_FORM_DATA_VALUE, url = "/image")
        Mono<ResponseEntity<String>> test(@RequestBody MultiValueMap<String, ?> req);

        @PostExchange(contentType = MediaType.APPLICATION_JSON_VALUE, url = "/auth")
        Mono<ResponseEntity<String>> authenticate();
    }

    private record Response(
            int status,
            String message
    ) { }

    private record ErrorResponse(
            int status,
            String errorMsg
    ) { }

    private static final class ClientConnector {

        private static final ClientHttpConnector CLIENT_HTTP_CONNECTOR;

        static {
            final ConnectionProvider provider = ConnectionProvider.builder("test-connection-provider")
                                                                  .maxConnections(500)
                                                                  .maxIdleTime(Duration.ofSeconds(30))
                                                                  .maxLifeTime(Duration.ofSeconds(60))
                                                                  .pendingAcquireTimeout(Duration.ofSeconds(60))
                                                                  .evictInBackground(Duration.ofSeconds(120))
                                                                  .build();

            CLIENT_HTTP_CONNECTOR = new ReactorClientHttpConnector(HttpClient.create(provider));
        }
    }
}
