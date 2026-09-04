package dev.sellerkit.reconkit.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// The entities live in recon-domain, outside this module's package, so the default
// scan from the application package does not reach them.
@EntityScan("dev.sellerkit.reconkit.domain.model")
@EnableScheduling
public class ReconkitApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReconkitApplication.class, args);
    }
}
