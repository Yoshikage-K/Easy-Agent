package com.eaharness.transfer.source;

import com.eaharness.transfer.config.TransferProperties;
import com.eaharness.transfer.upload.domain.UploadPart;
import com.eaharness.transfer.upload.exception.UploadException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class HttpRangeFileSource implements RangeFileSource {
    private final HttpClient client;
    private final TransferProperties properties;

    public HttpRangeFileSource(TransferProperties properties) {
        this.properties = properties;
        this.client = HttpClient.newBuilder()
                .connectTimeout(properties.requestTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public RemoteFileMetadata head(String sourceUrl, Map<String, String> headers)
            throws IOException, InterruptedException {
        URI uri = validateUri(sourceUrl);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(properties.requestTimeout())
                .method("HEAD", HttpRequest.BodyPublishers.noBody());
        applyHeaders(builder, headers);
        HttpResponse<Void> response = client.send(builder.build(), HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new UploadException("Remote HEAD failed with status " + response.statusCode());
        }
        long length = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        if (length <= 0) {
            throw new UploadException("Remote file must provide a positive Content-Length");
        }
        return new RemoteFileMetadata(
                length,
                response.headers().firstValue("ETag").orElse(null),
                response.headers().firstValue("Content-Type").orElse(null),
                response.headers().firstValue("Accept-Ranges").orElse(null));
    }

    @Override
    public InputStream openRange(String sourceUrl, Map<String, String> headers, UploadPart part)
            throws IOException, InterruptedException {
        URI uri = validateUri(sourceUrl);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(properties.requestTimeout())
                .header("Range", "bytes=" + part.start() + "-" + part.end())
                .GET();
        applyHeaders(builder, headers);
        HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 206) {
            response.body().close();
            throw new UploadException("Remote server did not honor Range request; status " + response.statusCode());
        }
        return response.body();
    }

    private URI validateUri(String sourceUrl) {
        URI uri = URI.create(sourceUrl);
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new UploadException("Only HTTP and HTTPS source URLs are supported");
        }
        return uri;
    }

    private void applyHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        headers.forEach((name, value) -> {
            if (name.equalsIgnoreCase("Host") || name.equalsIgnoreCase("Content-Length")) {
                throw new UploadException("Forbidden source header: " + name);
            }
            builder.header(name, value);
        });
    }
}
