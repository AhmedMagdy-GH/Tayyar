import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;

public final class HealthProbe {
    private HealthProbe() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.exit(2);
        }
        var connection = (HttpURLConnection) URI.create(args[0]).toURL().openConnection();
        int timeout = Math.toIntExact(Duration.ofSeconds(2).toMillis());
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(false);
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                System.exit(1);
            }
        } finally {
            connection.disconnect();
        }
    }
}
