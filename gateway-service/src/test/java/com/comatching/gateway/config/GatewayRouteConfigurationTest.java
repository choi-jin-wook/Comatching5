package com.comatching.gateway.config;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

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
	void shouldRouteAdminUsersToUserServiceAndKeepV1OnItemService() throws Exception {
		// application-docker.yml은 gitignore된 로컬 전용 파일이라 있을 때만 검증한다.
		for (String resourceName : List.of("application.yml", "application-aws.yml", "application-docker.yml")) {
			ClassPathResource resource = new ClassPathResource(resourceName);
			if (!resource.exists()) {
				continue;
			}

			String config = read(resourceName);

			assertThat(config)
				.as("%s: user-service는 v1 없는 /api/admin/users를 서빙한다", resourceName)
				.contains("- id: user-service-admin")
				.contains("Path=/api/admin/users, /api/admin/users/**");
			assertThat(config)
				.as("%s: /api/v1/**는 item-service가 그대로 가져간다", resourceName)
				.contains("Path=/api/items/**, /api/v1/**")
				.doesNotContain("/api/v1/admin/users");
		}
	}

	private String read(String resourceName) throws Exception {
		return new String(new ClassPathResource(resourceName).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
	}
}
