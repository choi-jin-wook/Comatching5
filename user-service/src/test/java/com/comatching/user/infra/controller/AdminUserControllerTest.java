package com.comatching.user.infra.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.comatching.common.domain.enums.Gender;
import com.comatching.common.domain.enums.ItemType;
import com.comatching.common.dto.item.AdminInventoryAction;
import com.comatching.common.dto.item.AdminInventoryUpdateRequest;
import com.comatching.common.dto.response.PagingResponse;
import com.comatching.common.exception.handler.GlobalExceptionHandler;
import com.comatching.common.resolver.MemberInfoArgumentResolver;
import com.comatching.user.domain.admin.dto.AdminUserDetailResponse;
import com.comatching.user.domain.admin.dto.AdminUserSummaryResponse;
import com.comatching.user.domain.admin.service.AdminUserService;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Mock
	private AdminUserService adminUserService;

	@InjectMocks
	private AdminUserController adminUserController;

	private static final Long ADMIN_ID = 999L;
	private static final String SUCCESS_CODE = "GEN-000";

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(adminUserController)
			.setCustomArgumentResolvers(new MemberInfoArgumentResolver(), new PageableHandlerMethodArgumentResolver())
			.setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
			.build();
	}

	@Test
	@DisplayName("GET /api/admin/users - 사용자 목록을 조회한다")
	void getUsers_success() throws Exception {
		// given
		AdminUserSummaryResponse summary = new AdminUserSummaryResponse(
			1L, "user@test.com", "홍길동", "닉네임", Gender.FEMALE, "https://img", 3L, 1L
		);
		PagingResponse<AdminUserSummaryResponse> response =
			new PagingResponse<>(List.of(summary), 0, 20, 1, 1, false, false);

		given(adminUserService.getUsers(eq(null), any(Pageable.class))).willReturn(response);

		// when & then
		mockMvc.perform(get("/api/admin/users")
				.header("X-Member-Id", ADMIN_ID)
				.header("X-Member-Role", "ROLE_ADMIN"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(SUCCESS_CODE))
			.andExpect(jsonPath("$.data.content.length()").value(1))
			.andExpect(jsonPath("$.data.content[0].id").value(1))
			.andExpect(jsonPath("$.data.content[0].email").value("user@test.com"))
			.andExpect(jsonPath("$.data.content[0].matchingTicketCount").value(3))
			.andExpect(jsonPath("$.data.content[0].optionTicketCount").value(1))
			.andExpect(jsonPath("$.data.totalElements").value(1));

		then(adminUserService).should().getUsers(eq(null), any(Pageable.class));
	}

	@Test
	@DisplayName("GET /api/admin/users?keyword= - 키워드를 서비스로 그대로 전달한다")
	void getUsers_withKeyword() throws Exception {
		// given
		PagingResponse<AdminUserSummaryResponse> response =
			new PagingResponse<>(List.of(), 0, 20, 0, 0, false, false);

		given(adminUserService.getUsers(eq("nickname"), any(Pageable.class))).willReturn(response);

		// when & then
		mockMvc.perform(get("/api/admin/users")
				.param("keyword", "nickname")
				.header("X-Member-Id", ADMIN_ID)
				.header("X-Member-Role", "ROLE_ADMIN"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(SUCCESS_CODE))
			.andExpect(jsonPath("$.data.content.length()").value(0));

		then(adminUserService).should().getUsers(eq("nickname"), any(Pageable.class));
	}

	@Test
	@DisplayName("GET /api/admin/users - 인증 헤더가 없으면 예외가 발생한다")
	void getUsers_missingMemberIdHeader() throws Exception {
		mockMvc.perform(get("/api/admin/users"))
			.andExpect(status().isInternalServerError());

		then(adminUserService).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("GET /api/admin/users/{memberId} - 사용자 상세를 조회한다")
	void getUserDetail_success() throws Exception {
		// given
		AdminUserDetailResponse detail = new AdminUserDetailResponse(
			1L, "user@test.com", "홍길동", "닉네임", Gender.FEMALE, "https://img", 3L, 1L
		);
		given(adminUserService.getUserDetail(1L)).willReturn(detail);

		// when & then
		mockMvc.perform(get("/api/admin/users/1")
				.header("X-Member-Id", ADMIN_ID)
				.header("X-Member-Role", "ROLE_ADMIN"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(SUCCESS_CODE))
			.andExpect(jsonPath("$.data.id").value(1))
			.andExpect(jsonPath("$.data.email").value("user@test.com"))
			.andExpect(jsonPath("$.data.realName").value("홍길동"))
			.andExpect(jsonPath("$.data.nickname").value("닉네임"))
			.andExpect(jsonPath("$.data.gender").value("FEMALE"))
			.andExpect(jsonPath("$.data.profileImageUrl").value("https://img"))
			.andExpect(jsonPath("$.data.matchingTicketCount").value(3))
			.andExpect(jsonPath("$.data.optionTicketCount").value(1));
	}

	@Test
	@DisplayName("PATCH /api/admin/users/{memberId}/items - 인벤토리 수정을 위임한다")
	void updateUserInventory_success() throws Exception {
		// given
		AdminInventoryUpdateRequest request = new AdminInventoryUpdateRequest(
			ItemType.MATCHING_TICKET, 2, AdminInventoryAction.ADD, "보상 누락"
		);

		// when & then
		mockMvc.perform(patch("/api/admin/users/1/items")
				.header("X-Member-Id", ADMIN_ID)
				.header("X-Member-Role", "ROLE_ADMIN")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(SUCCESS_CODE));

		then(adminUserService).should().updateUserInventory(ADMIN_ID, 1L, request);
	}

	@Test
	@DisplayName("PATCH /api/admin/users/{memberId}/items - 수정 사유가 없으면 400을 반환한다")
	void updateUserInventory_blankReason() throws Exception {
		// given
		AdminInventoryUpdateRequest request = new AdminInventoryUpdateRequest(
			ItemType.MATCHING_TICKET, 2, AdminInventoryAction.ADD, " "
		);

		// when & then
		mockMvc.perform(patch("/api/admin/users/1/items")
				.header("X-Member-Id", ADMIN_ID)
				.header("X-Member-Role", "ROLE_ADMIN")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest());

		then(adminUserService).shouldHaveNoInteractions();
	}
}
