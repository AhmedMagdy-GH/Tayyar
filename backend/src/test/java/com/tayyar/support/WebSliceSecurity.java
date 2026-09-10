package com.tayyar.support;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
/** Parser/error slice tests; actual security is covered by IdentityIT over HTTP. */
@TestConfiguration(proxyBeanMethods=false)
@org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
public class WebSliceSecurity {
 @Bean SecurityFilterChain webSliceSecurity(HttpSecurity http) throws Exception {
   return http.csrf(csrf->csrf.disable()).authorizeHttpRequests(rules->rules.anyRequest().permitAll()).build();
 }
}
