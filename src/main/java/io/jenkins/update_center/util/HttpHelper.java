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
     * Returns the response body for an HTTP response. Throws an IllegalStateException if the
     * response body is null, as this is considered an unexpected condition that should be handled
     * separately.
     *
     * @param response HTTP response
     * @return response body
     * @throws IllegalStateException if the response body is null
     */
    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE",
        justification = "body is never null as per Javadoc")
    public static @NonNull ResponseBody body(Response response) throws IllegalStateException {
        ResponseBody body = response.body();
        if (body == null) {
            throw new IllegalStateException("Response body is null");
        }
        return body;
    }

    /**
     * Returns the response body as a string for an HTTP request. Uses the provided OkHttpClient
     * instance to execute the request and retrieve the response. If an I/O error occurs or the
     * response body is null, an exception is thrown. Any other exception is wrapped in an IOException
     * with a more descriptive error message.
     *
     * @param client OkHttpClient instance
     * @param request HTTP request
     * @return response body as a string
     * @throws IOException if an I/O error occurs
     * @throws IllegalStateException if the response body is null
     */
    public static String getResponseBody(OkHttpClient client, Request request) throws IOException, IllegalStateException {
        try (final ResponseBody body = body(client.newCall(request).execute())) {
            return body.string();
        } catch (IOException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("An error occurred while getting the response body", e);
        }
    }
}


