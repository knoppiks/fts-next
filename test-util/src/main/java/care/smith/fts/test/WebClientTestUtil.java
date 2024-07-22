package care.smith.fts.test;

import static java.util.Optional.empty;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpStatus.OK;

import org.mockito.Mockito;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;

public interface WebClientTestUtil {
  static MockExchangeFunction matchRequest(HttpMethod method) {
    return new MockExchangeFunction(
        method, ClientResponse.create(OK).build(), new IllegalArgumentException(""));
  }

  class MockExchangeFunction implements ExchangeFunction {
    private final HttpMethod method;
    private final ClientResponse resp;
    private final Throwable err;

    public MockExchangeFunction(HttpMethod method, ClientResponse resp, Throwable err) {
      this.method = method;
      this.resp = resp;
      this.err = err;
    }

    public MockExchangeFunction willRespond(ClientResponse resp) {
      return new MockExchangeFunction(method, resp, err);
    }

    public MockExchangeFunction willError(Throwable err) {
      return new MockExchangeFunction(method, resp, err);
    }

    @Override
    public Mono<ClientResponse> exchange(ClientRequest req) {
      return req.method().equals(method) ? Mono.just(resp) : Mono.error(wrapException(req));
    }

    private static WebClientRequestException wrapException(ClientRequest req) {
      return new WebClientRequestException(
          new IllegalArgumentException(), req.method(), req.url(), req.headers());
    }
  }

  static MockClientResponseBuilder mockResponse(HttpStatus status) {
    return new MockClientResponseBuilder(status);
  }

  class MockClientResponseBuilder {
    private final ClientResponse mock;

    public MockClientResponseBuilder(HttpStatus status) {
      mock = Mockito.spy(ClientResponse.create(status).build());
    }

    public MockClientResponseBuilder toBodilessEntity() {
      given(mock.toBodilessEntity()).willReturn(Mono.just(ResponseEntity.of(empty())));
      return this;
    }

    public ClientResponse build() {
      return mock;
    }
  }

  static ExchangeFunction requestsInOrder(MockExchangeFunction... mockExchangeFunction) {
    return new ExchangeFunction() {
      private int i = 0;

      @Override
      public Mono<ClientResponse> exchange(ClientRequest request) {
        return mockExchangeFunction[i++].exchange(request);
      }
    };
  }
}
