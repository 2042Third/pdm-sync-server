package pw.pdm.pdmsyncserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {"pw.pdm"})
@Import(JacksonAutoConfiguration.class)
public class PdmSyncServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(PdmSyncServerApplication.class, args);
	}
}