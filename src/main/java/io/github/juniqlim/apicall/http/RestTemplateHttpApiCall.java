package io.github.juniqlim.apicall.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.juniqlim.apicall.http.logging.HttpLogging;
import io.github.juniqlim.apicall.http.logging.HttpLogging.SystemOutPrintHttpLogging;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * interface
 */
@Slf4j
public class RestTemplateHttpApiCall implements HttpApiCall {
    private final RestTemplate restTemplate;
    private final ResponseBodyParser responseBodyParser;
    private final HttpLogging httpLogging;

    public RestTemplateHttpApiCall() { this(new RestTemplate(), new SystemOutPrintHttpLogging()); }

    public RestTemplateHttpApiCall(RestTemplate restTemplate) {
        this(restTemplate, new SystemOutPrintHttpLogging());
    }

    public RestTemplateHttpApiCall(RestTemplate restTemplate, HttpLogging httpLogging) {
        this(restTemplate, new OnlyStringResponseBodyParser(), httpLogging);
    }

    public RestTemplateHttpApiCall(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this(restTemplate, objectMapper, new SystemOutPrintHttpLogging());
    }

    public RestTemplateHttpApiCall(RestTemplate restTemplate, ObjectMapper objectMapper, HttpLogging httpLogging) {
        this.restTemplate = restTemplate;
        this.responseBodyParser = new ObjectMapperResponseBodyParser(objectMapper);
        this.httpLogging = httpLogging;
    }

    public RestTemplateHttpApiCall(RestTemplate restTemplate, ResponseBodyParser responseBodyParser, HttpLogging httpLogging) {
        this.restTemplate = restTemplate;
        this.responseBodyParser = responseBodyParser;
        this.httpLogging = httpLogging;
    }

    @Override
    public <Q, S> S callApi(HttpRequest<Q, S> request) {
        return run(request.httpMethod().httpMethod(), request.url(), request.header(), request.request(), request.responseType());
    }

    @Override
    public <Q> void callApi(HttpRequestWithoutResponse<Q> response) {
        runWithoutResponse(response.httpMethod().httpMethod(), response.url(), response.header(), response.request());
    }

    private <Q, S> S run(HttpMethod httpMethod, String url, Map<String, String> header, Q request, Class<S> clazz) {
        HttpResponse response = sendHttpRequest(httpMethod, url, header, request);
        log(HttpApiCallResult.of(httpMethod, url, header, request, response));
        return parseResponseBody(response.body(), clazz);
    }

    private <Q> void runWithoutResponse(HttpMethod httpMethod, String url, Map<String, String> header, Q request) {
        HttpResponse response = sendHttpRequest(httpMethod, url, header, request);
        log(HttpApiCallResult.of(httpMethod, url, header, request, response));
    }

    private void log(HttpApiCallResult httpMethod) {
        if (httpMethod.response().isError()) {
            httpLogging.errorLog(httpMethod);
            throw new HttpApiCallException(httpMethod.response(),
                "Http request call exception - status: " + httpMethod.response().httpStatus() + ", response: "
                    + httpMethod.response().body());
        }
        httpLogging.infoLog(httpMethod);
    }

    private HttpResponse sendHttpRequest(HttpMethod httpMethod, String url, Map<String, String> header, Object request) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, httpMethod, new HttpEntity<>(request, makeHeader(header)), String.class);
            return HttpResponse.of(response.getStatusCode(), response.getBody());
        } catch (HttpClientErrorException e) {
            return HttpResponse.of(e.getStatusCode(), e.getResponseBodyAsString());
        }
    }

    private MultiValueMap<String, String> makeHeader(Map<String, String> header) {
        MultiValueMap<String, String> result = new LinkedMultiValueMap<>();
        result.setAll(header);
        return result;
    }

    private <S> S parseResponseBody(String responseBody, Class<S> clazz) {
        if (clazz == String.class) {
            return (S) responseBody;
        }
        return responseBodyParser.parse(responseBody, clazz);
    }
}
