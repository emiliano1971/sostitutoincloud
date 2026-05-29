package it.gavia.sostitutoincloud;

import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@Log4j2
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class SostitutoincloudApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(SostitutoincloudApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(SostitutoincloudApplication.class, args);
    }
}
