package net.theevilreaper;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;

@ConfigurationProperties("application.properties")
@OpenAPIDefinition(
        info = @Info(
                title = "Otis API",
                version = "0.0.1",
                description = "Simple user management system",
                license = @License(name = "Close Source"),
                contact = @Contact(url = "https://onelitefeather.net", name = "Management", email = "admin@onelitefeather.net")
        )
)
public class OtisApplication {

    public static void main(String[] args) {
        Micronaut.run(OtisApplication.class, args);
    }
}