package com.comatching.gateway.config;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class GatewayRouteConfigurationTest {

	@Test
	void shouldNotExposeInternalApiRoutes() throws Exception {
		assertThat(read("application.yml")).doesNotContain("/api/internal/");
		assertThat(read("application-aws.yml")).doesNotContain("/api/internal/");
	}

	@Test
	void shouldRouteReissueAsPublicAndAllowPatchCors() throws Exception {
		assertThat(read("application.yml"))
			.contains("/api/auth/reissue")
			.contains("allowedMethods: [GET, POST, PUT, PATCH, DELETE, OPTIONS]");
		assertThat(read("application-aws.yml"))
			.contains("/api/auth/reissue")
			.contains("allowedMethods: [GET, POST, PUT, PATCH, DELETE, OPTIONS]");
	}

	@Test
	void shouldRouteRefactoredV1UserAndNoticeApisToUserService() throws Exception {
		for (String resourceName : new String[] {"application.yml", "application-aws.yml", "application-docker.yml"}) {
			assertThat(read(resourceName))
				.contains("/api/v1/admin/users/**")
				.contains("/api/v1/admin/notices/**")
				.contains("/api/v1/notices/active");
		}
	}

	@Test
	void shouldUseExplicitV1RoutesInsteadOfAServiceWideCatchAll() throws Exception {
		for (String resourceName : new String[] {"application.yml", "application-aws.yml", "application-docker.yml"}) {
			assertThat(read(resourceName))
				.contains("/api/v1/shop/**")
				.contains("/api/v1/admin/shop/**")
				.contains("/api/v1/admin/payment/**")
				.doesNotContain("Path=/api/items/**, /api/v1/**");
		}
	}

	@Test
	void shouldDeclareUserV1RoutesBeforeItemV1Routes() throws Exception {
		for (String resourceName : new String[] {"application.yml", "application-aws.yml", "application-docker.yml"}) {
			String routes = read(resourceName);
			assertThat(routes.indexOf("id: user-service-admin"))
				.isLessThan(routes.indexOf("id: item-service-admin-shop"));
		}
	}

	private String read(String resourceName) throws Exception {
		return new String(new ClassPathResource(resourceName).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
	}
}
