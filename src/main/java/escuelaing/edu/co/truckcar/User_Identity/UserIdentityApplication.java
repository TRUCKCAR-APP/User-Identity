package escuelaing.edu.co.truckcar.User_Identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.data.web.config.EnableSpringDataWebSupport;

@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class UserIdentityApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserIdentityApplication.class, args);
    }

}
