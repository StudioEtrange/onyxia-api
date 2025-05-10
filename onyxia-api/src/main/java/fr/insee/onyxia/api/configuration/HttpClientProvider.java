package fr.insee.onyxia.api.configuration;

import fr.insee.onyxia.model.region.Region;
import java.io.IOException;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.security.SecureRandom;

@Configuration
public class HttpClientProvider {

    @Bean
    public OkHttpClient httpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        enableDebugFeatureIfEnabled(builder);
        return builder.build();
    }

    @Bean
    public OkHttpClient unsafeHttpClient() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
            };

            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            return new OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                .hostnameVerifier((hostname, session) -> true)
                .build();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public OkHttpClient getClientForRegion(Region region) {
        OkHttpClient.Builder builder =
                new OkHttpClient.Builder()
                        .addInterceptor(
                                new Interceptor() {
                                    @NotNull
                                    @Override
                                    public Response intercept(@NotNull Chain chain)
                                            throws IOException {
                                        Request request = chain.request();
                                        Request newRequest = request;

                                        Region.Auth auth =
                                                region.getServices().getServer().getAuth();

                                        if (auth != null && auth.getToken() != null) {
                                            newRequest =
                                                    newRequest
                                                            .newBuilder()
                                                            .addHeader(
                                                                    "Authorization",
                                                                    "token=" + auth.getToken())
                                                            .build();
                                        }

                                        if (auth != null && auth.getUsername() != null) {
                                            String credentials =
                                                    Credentials.basic(
                                                            auth.getUsername(), auth.getPassword());
                                            newRequest =
                                                    newRequest
                                                            .newBuilder()
                                                            .addHeader("Authorization", credentials)
                                                            .build();
                                        }

                                        return chain.proceed(newRequest);
                                    }
                                });

        enableDebugFeatureIfEnabled(builder);

        return builder.build();
    }

    private void enableDebugFeatureIfEnabled(OkHttpClient.Builder builder) {
        // Currently disabled
        if (false) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.level(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(logging);
        }
    }
}
