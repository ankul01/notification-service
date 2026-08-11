package notification.boot;

import notification.api.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves the composition root actually wires: every @Bean in BeanConfiguration resolves. */
@SpringBootTest
class NotificationServiceApplicationTests {

    @Autowired
    private NotificationService notificationService;

    @Test
    void contextLoads_andExposesTheNotificationServicePort() {
        assertThat(notificationService).isNotNull();
    }
}
