package io.jenkins.update_center.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.Objects;

public class HttpHelper {

    /**
     * @param response HTTP response
     * @return response body
     */
    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE",
        justification = "body is never null as per Javadoc")
    public static @NonNull ResponseBody body(Response response) {
        ResponseBody body = response.body();
        return Objects.requireNonNull(body);
    }

    public static String getResponseBody(OkHttpClient client, Request request) throws IOException {
        try (final ResponseBody body = body(client.newCall(request).execute())) {
            return body.string();
        }
    }
}
