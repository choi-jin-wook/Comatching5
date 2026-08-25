package com.comatching.user.infra.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import com.comatching.common.aop.RoleCheckAspect;
import com.comatching.common.exception.BusinessException;
import com.comatching.common.exception.code.GeneralErrorCode;
import com.comatching.common.exception.handler.GlobalExceptionHandler;
import com.comatching.common.resolver.MemberInfoArgumentResolver;
import com.comatching.user.domain.admin.notice.dto.ActiveNoticeResponse;
import com.comatching.user.domain.admin.notice.dto.AdminNoticeResponse;
import com.comatching.user.domain.admin.notice.dto.NoticeCreateRequest;
import com.comatching.user.domain.admin.notice.dto.NoticeUpdateRequest;
import com.comatching.user.domain.admin.notice.service.AdminNoticeService;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AdminNoticeControllerTest {

	private static final long ADMIN_ID = 999L;
	private static final String ADMIN_HEADERS_ROLE = "ROLE_ADMIN";
	private static final String SUCCESS_CODE = "GEN-000";

	private MockMvc mockMvc;

	@Mock
	private AdminNoticeService adminNoticeService;

	@InjectMocks
	private AdminNoticeController adminNoticeController;

	@BeforeEach
	void setUp() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		AspectJProxyFactory proxyFactory = new AspectJProxyFactory(adminNoticeController);
		proxyFactory.setProxyTargetClass(true);
		proxyFactory.addAspect(new RoleCheckAspect());

		mockMvc = MockMvcBuilders.standaloneSetup((Object)proxyFactory.getProxy())
			.setCustomArgumentResolvers(new MemberInfoArgumentResolver())
			.setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
			.setValidator(validator)
			.build();
	}

	@Test
	void getAdminNotices_usesV1Path() throws Exception {
		given(adminNoticeService.getAdminNotices()).willReturn(List.of(
			new AdminNoticeResponse(1L, "점검 안내", "내용", LocalDateTime.of(2026, 8, 1, 9, 0),
				LocalDateTime.of(2026, 8, 2, 9, 0), true)
		));

		mockMvc.perform(get("/api/v1/admin/notices").header("X-Member-Id", ADMIN_ID).header("X-Member-Role", ADMIN_HEADERS_ROLE))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(SUCCESS_CODE))
			.andExpect(jsonPath("$.data[0].noticeId").value(1));

		then(adminNoticeService).should().getAdminNotices();
	}

	@Test
	void createNotice_forwardsRequest() throws Exception {
		String body = """
			{"title":"점검 안내","content":"내용","startTime":"2026-08-01T09:00:00","endTime":"2026-08-02T09:00:00"}
			""";
		NoticeCreateRequest expected = new NoticeCreateRequest("점검 안내", "내용",
			LocalDateTime.of(2026, 8, 1, 9, 0), LocalDateTime.of(2026, 8, 2, 9, 0));

		mockMvc.perform(post("/api/v1/admin/notices")
				.header("X-Member-Id", ADMIN_ID).header("X-Member-Role", ADMIN_HEADERS_ROLE)
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(SUCCESS_CODE));

		then(adminNoticeService).should().createNotice(expected);
	}

	@Test
	void updateNotice_forwardsPathAndBody() throws Exception {
		String body = """
			{"title":"수정","content":"수정 내용","startTime":"2026-08-03T09:00:00","endTime":"2026-08-04T09:00:00"}
			""";
		NoticeUpdateRequest expected = new NoticeUpdateRequest("수정", "수정 내용",
			LocalDateTime.of(2026, 8, 3, 9, 0), LocalDateTime.of(2026, 8, 4, 9, 0));

		mockMvc.perform(patch("/api/v1/admin/notices/{noticeId}", 7L)
				.header("X-Member-Id", ADMIN_ID).header("X-Member-Role", ADMIN_HEADERS_ROLE)
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk());

		then(adminNoticeService).should().updateNotice(7L, expected);
	}

	@Test
	void deleteNotice_forwardsNoticeId() throws Exception {
		mockMvc.perform(delete("/api/v1/admin/notices/{noticeId}", 7L)
				.header("X-Member-Id", ADMIN_ID).header("X-Member-Role", ADMIN_HEADERS_ROLE))
			.andExpect(status().isOk());

		then(adminNoticeService).should().deleteNotice(7L);
	}

	@Test
	void getActiveNotices_usesV1Path() throws Exception {
		given(adminNoticeService.getActiveNotices()).willReturn(List.of(
			new ActiveNoticeResponse(1L, "활성 공지", "내용")
		));

		mockMvc.perform(get("/api/v1/notices/active").header("X-Member-Id", ADMIN_ID).header("X-Member-Role", "ROLE_USER"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(SUCCESS_CODE))
			.andExpect(jsonPath("$.data[0].noticeId").value(1));

		then(adminNoticeService).should().getActiveNotices();
	}

	@ParameterizedTest
	@MethodSource("invalidNoticeRequestBodies")
	void createNotice_rejectsInvalidRequestBody(String requestBody) throws Exception {
		mockMvc.perform(post("/api/v1/admin/notices")
				.header("X-Member-Id", ADMIN_ID).header("X-Member-Role", ADMIN_HEADERS_ROLE)
				.contentType(MediaType.APPLICATION_JSON).content(requestBody))
			.andExpect(status().isBadRequest());

		then(adminNoticeService).shouldHaveNoInteractions();
	}

	@Test
	void createNotice_rejectsReversedDisplayPeriod() throws Exception {
		String body = """
			{"title":"점검 안내","content":"내용","startTime":"2026-08-02T09:00:00","endTime":"2026-08-01T09:00:00"}
			""";
		NoticeCreateRequest request = new NoticeCreateRequest("점검 안내", "내용",
			LocalDateTime.of(2026, 8, 2, 9, 0), LocalDateTime.of(2026, 8, 1, 9, 0));
		willThrow(new BusinessException(GeneralErrorCode.INVALID_INPUT_VALUE))
			.given(adminNoticeService).createNotice(request);

		mockMvc.perform(post("/api/v1/admin/notices")
				.header("X-Member-Id", ADMIN_ID).header("X-Member-Role", ADMIN_HEADERS_ROLE)
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest());
	}

	@Test
	void userRole_isForbiddenFromAdminNoticeApi() throws Exception {
		mockMvc.perform(get("/api/v1/admin/notices")
				.header("X-Member-Id", ADMIN_ID).header("X-Member-Role", "ROLE_USER"))
			.andExpect(status().isForbidden());

		then(adminNoticeService).shouldHaveNoInteractions();
	}

	private static Stream<String> invalidNoticeRequestBodies() {
		return Stream.of(
			"{\"title\":\"\",\"content\":\"내용\",\"startTime\":\"2026-08-01T09:00:00\",\"endTime\":\"2026-08-02T09:00:00\"}",
			"{\"title\":\"제목\",\"content\":\"\",\"startTime\":\"2026-08-01T09:00:00\",\"endTime\":\"2026-08-02T09:00:00\"}",
			"{\"title\":\"제목\",\"content\":\"내용\",\"endTime\":\"2026-08-02T09:00:00\"}",
			"{\"title\":\"제목\",\"content\":\"내용\",\"startTime\":\"2026-08-01T09:00:00\"}"
		);
	}
}
