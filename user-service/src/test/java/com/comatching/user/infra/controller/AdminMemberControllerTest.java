package com.comatching.user.infra.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import com.comatching.common.aop.RoleCheckAspect;
import com.comatching.common.domain.enums.Gender;
import com.comatching.common.domain.enums.ItemType;
import com.comatching.common.dto.response.PagingResponse;
import com.comatching.common.exception.BusinessException;
import com.comatching.common.exception.handler.GlobalExceptionHandler;
import com.comatching.common.resolver.MemberInfoArgumentResolver;
import com.comatching.user.domain.admin.user.dto.AdminInventoryAction;
import com.comatching.user.domain.admin.user.dto.AdminInventoryUpdateRequest;
import com.comatching.user.domain.admin.user.dto.AdminUserDetailResponse;
import com.comatching.user.domain.admin.user.dto.AdminUserSummaryResponse;
import com.comatching.user.domain.admin.user.service.AdminMemberService;
import com.comatching.user.global.exception.UserErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AdminMemberControllerTest {

	private static final long ADMIN_ID = 999L;
	private static final String SUCCESS_CODE = "GEN-000";

	private MockMvc mockMvc;

	@Mock
	private AdminMemberService adminMemberService;

	@InjectMocks
	private AdminMemberController adminMemberController;

	@BeforeEach
	void setUp() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		AspectJProxyFactory proxyFactory = new AspectJProxyFactory(adminMemberController);
		proxyFactory.setProxyTargetClass(true);
		proxyFactory.addAspect(new RoleCheckAspect());

		mockMvc = MockMvcBuilders.standaloneSetup((Object)proxyFactory.getProxy())
			.setCustomArgumentResolvers(new MemberInfoArgumentResolver(), new PageableHandlerMethodArgumentResolver())
			.setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
			.setValidator(validator)
			.build();
	}

	@Test
	void getUsers_usesV1PathAndForwardsKeyword() throws Exception {
		AdminUserSummaryResponse user = new AdminUserSummaryResponse(
			1L, "user@test.com", "홍길동", "닉네임", Gender.FEMALE, "https://img", 3L, 1L
		);
		given(adminMemberService.getUsers(eq("nickname"), any(Pageable.class)))
			.willReturn(new PagingResponse<>(List.of(user), 0, 20, 1, 1, false, false));

		mockMvc.perform(get("/api/v1/admin/users").param("keyword", "nickname")
				.header("X-Member-Id", ADMIN_ID).header("X-Member-Role", "ROLE_ADMIN"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(SUCCESS_CODE))
			.andExpect(jsonPath("$.data.content[0].id").value(1));

		then(adminMemberService).should().getUsers(eq("nickname"), any(Pageable.class));
	}

	@Test
	void getUserDetail_usesV1Path() throws Exception {
		given(adminMemberService.getUserDetail(1L)).willReturn(new AdminUserDetailResponse(
			1L, "user@test.com", "홍길동", "닉네임", Gender.FEMALE, "https://img", 3L, 1L
		));

		mockMvc.perform(get("/api/v1/admin/users/{memberId}", 1L)
				.header("X-Member-Id", ADMIN_ID).header("X-Member-Role", "ROLE_ADMIN"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(1));

		then(adminMemberService).should().getUserDetail(1L);
	}

	@Test
	void updateUserInventory_usesV1Path() throws Exception {
		AdminInventoryUpdateRequest request = new AdminInventoryUpdateRequest(
			ItemType.MATCHING_TICKET, 3, AdminInventoryAction.ADD, "보상 지급"
		);

		mockMvc.perform(patch("/api/v1/admin/users/{memberId}/items", 1L)
				.header("X-Member-Id", ADMIN_ID).header("X-Member-Role", "ROLE_ADMIN")
				.contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(SUCCESS_CODE));

		then(adminMemberService).should().updateUserInventory(ADMIN_ID, 1L, request);
	}

	@Test
	void updateUserInventory_returnsServiceError() throws Exception {
		AdminInventoryUpdateRequest request = new AdminInventoryUpdateRequest(
			ItemType.MATCHING_TICKET, 3, AdminInventoryAction.ADD, "보상 지급"
		);
		willThrow(new BusinessException(UserErrorCode.TARGET_USER_NOT_FOUND))
			.given(adminMemberService).updateUserInventory(ADMIN_ID, 1L, request);

		mockMvc.perform(patch("/api/v1/admin/users/{memberId}/items", 1L)
				.header("X-Member-Id", ADMIN_ID).header("X-Member-Role", "ROLE_ADMIN")
				.contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ITEM-004"));
	}

	@ParameterizedTest
	@MethodSource("invalidInventoryRequestBodies")
	void updateUserInventory_rejectsInvalidRequestBody(String requestBody) throws Exception {
		mockMvc.perform(patch("/api/v1/admin/users/{memberId}/items", 1L)
				.header("X-Member-Id", ADMIN_ID).header("X-Member-Role", "ROLE_ADMIN")
				.contentType(MediaType.APPLICATION_JSON).content(requestBody))
			.andExpect(status().isBadRequest());

		then(adminMemberService).shouldHaveNoInteractions();
	}

	@Test
	void userRole_isForbiddenFromAdminUserApi() throws Exception {
		mockMvc.perform(get("/api/v1/admin/users")
				.header("X-Member-Id", ADMIN_ID).header("X-Member-Role", "ROLE_USER"))
			.andExpect(status().isForbidden());

		then(adminMemberService).shouldHaveNoInteractions();
	}

	private static Stream<String> invalidInventoryRequestBodies() {
		return Stream.of(
			"{\"itemType\":\"MATCHING_TICKET\",\"quantity\":0,\"action\":\"ADD\",\"reason\":\"사유\"}",
			"{\"itemType\":\"MATCHING_TICKET\",\"quantity\":-1,\"action\":\"ADD\",\"reason\":\"사유\"}",
			"{\"itemType\":\"MATCHING_TICKET\",\"quantity\":1,\"action\":\"ADD\"}",
			"{\"quantity\":1,\"action\":\"ADD\",\"reason\":\"사유\"}",
			"{\"itemType\":\"MATCHING_TICKET\",\"quantity\":1,\"reason\":\"사유\"}"
		);
	}
}
