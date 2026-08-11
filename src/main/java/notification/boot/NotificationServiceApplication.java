package notification.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Entry point. Explicitly imports BeanConfiguration rather than relying on component scanning —
 * domain/api/spi/core/infra classes are plain POJOs with no @Component annotations by design (see
 * BeanConfiguration's javadoc), so there's nothing under notification.* for a scan to find besides
 * this package itself.
 */
@SpringBootApplication
@Import(BeanConfiguration.class)
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
