package com.comatching.user.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.comatching.common.domain.enums.Gender;
import com.comatching.common.domain.enums.ItemType;
import com.comatching.common.dto.item.AdminInventoryAction;
import com.comatching.common.dto.item.AdminInventoryCounts;
import com.comatching.common.dto.item.AdminInventoryUpdateRequest;
import com.comatching.common.dto.member.AdminUserProfileDto;
import com.comatching.common.dto.response.PagingResponse;
import com.comatching.common.exception.BusinessException;
import com.comatching.common.exception.code.GeneralErrorCode;
import com.comatching.user.domain.admin.dto.AdminUserDetailResponse;
import com.comatching.user.domain.admin.dto.AdminUserSummaryResponse;
import com.comatching.user.domain.member.service.AdminMemberQueryService;
import com.comatching.user.global.exception.UserErrorCode;
import com.comatching.user.infra.client.ItemAdminClient;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserServiceImpl 테스트")
class AdminUserServiceTest {

	@InjectMocks
	private AdminUserServiceImpl adminUserService;

	@Mock
	private AdminMemberQueryService adminMemberQueryService;

	@Mock
	private ItemAdminClient itemAdminClient;

	private static final Long ADMIN_ID = 999L;

	@Test
	@DisplayName("사용자 목록과 인벤토리 수량을 함께 조회한다")
	void shouldReturnUsersWithInventoryCounts() {
		// given
		PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));
		given(adminMemberQueryService.getUsers(null, pageable)).willReturn(new PagingResponse<>(
			List.of(profile(1L, "user1@test.com"), profile(2L, "user2@test.com")),
			0, 20, 2, 1, false, false
		));
		given(itemAdminClient.getInventoryCounts(anyList()))
			.willReturn(Map.of(1L, new AdminInventoryCounts(3L, 1L)));

		// when
		PagingResponse<AdminUserSummaryResponse> result = adminUserService.getUsers(null, pageable);

		// then
		assertThat(result.content()).hasSize(2);
		assertThat(result.totalElements()).isEqualTo(2);

		AdminUserSummaryResponse first = result.content().get(0);
		assertThat(first.id()).isEqualTo(1L);
		assertThat(first.email()).isEqualTo("user1@test.com");
		assertThat(first.matchingTicketCount()).isEqualTo(3L);
		assertThat(first.optionTicketCount()).isEqualTo(1L);

		AdminUserSummaryResponse second = result.content().get(1);
		assertThat(second.id()).isEqualTo(2L);
		assertThat(second.matchingTicketCount()).isZero();
		assertThat(second.optionTicketCount()).isZero();

		then(itemAdminClient).should().getInventoryCounts(List.of(1L, 2L));
	}

	@Test
	@DisplayName("조회된 사용자가 없으면 아이템 서비스를 호출하지 않는다")
	void shouldNotCallItemServiceWhenNoUsers() {
		// given
		PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));
		given(adminMemberQueryService.getUsers(null, pageable))
			.willReturn(new PagingResponse<>(List.of(), 0, 20, 0, 0, false, false));

		// when
		PagingResponse<AdminUserSummaryResponse> result = adminUserService.getUsers(null, pageable);

		// then
		assertThat(result.content()).isEmpty();
		then(itemAdminClient).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("아이템 서비스 조회가 실패하면 ITEM_QUERY_FAILED로 변환한다")
	void shouldTranslateInventoryQueryFailure() {
		// given
		PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));
		given(adminMemberQueryService.getUsers(null, pageable))
			.willReturn(new PagingResponse<>(List.of(profile(1L, "user1@test.com")), 0, 20, 1, 1, false, false));
		willThrow(feignException(500)).given(itemAdminClient).getInventoryCounts(anyList());

		// when & then
		assertThatThrownBy(() -> adminUserService.getUsers(null, pageable))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(UserErrorCode.ITEM_QUERY_FAILED);
	}

	@Test
	@DisplayName("사용자 상세를 인벤토리 수량과 함께 조회한다")
	void shouldReturnUserDetailWithInventoryCounts() {
		// given
		given(adminMemberQueryService.getUserDetail(1L)).willReturn(profile(1L, "user1@test.com"));
		given(itemAdminClient.getInventoryCounts(List.of(1L)))
			.willReturn(Map.of(1L, new AdminInventoryCounts(2L, 5L)));

		// when
		AdminUserDetailResponse result = adminUserService.getUserDetail(1L);

		// then
		assertThat(result.id()).isEqualTo(1L);
		assertThat(result.email()).isEqualTo("user1@test.com");
		assertThat(result.nickname()).isEqualTo("닉네임1");
		assertThat(result.gender()).isEqualTo(Gender.FEMALE);
		assertThat(result.matchingTicketCount()).isEqualTo(2L);
		assertThat(result.optionTicketCount()).isEqualTo(5L);
	}

	@Test
	@DisplayName("아이템 수량이 없는 사용자는 상세 조회 시 0으로 채운다")
	void shouldFallbackToEmptyInventoryOnDetail() {
		// given
		given(adminMemberQueryService.getUserDetail(1L)).willReturn(profile(1L, "user1@test.com"));
		given(itemAdminClient.getInventoryCounts(List.of(1L))).willReturn(Map.of());

		// when
		AdminUserDetailResponse result = adminUserService.getUserDetail(1L);

		// then
		assertThat(result.matchingTicketCount()).isZero();
		assertThat(result.optionTicketCount()).isZero();
	}

	@Test
	@DisplayName("memberId가 1 미만이면 잘못된 입력값 예외가 발생한다")
	void shouldRejectNonPositiveMemberId() {
		// when & then
		assertThatThrownBy(() -> adminUserService.getUserDetail(0L))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(GeneralErrorCode.INVALID_INPUT_VALUE);

		then(adminMemberQueryService).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("인벤토리 수정은 대상 사용자 선조회 후 아이템 서비스에 위임한다")
	void shouldDelegateInventoryUpdateAfterUserLookup() {
		// given
		AdminInventoryUpdateRequest request = new AdminInventoryUpdateRequest(
			ItemType.MATCHING_TICKET, 2, AdminInventoryAction.ADD, "보상 누락"
		);
		given(adminMemberQueryService.getUserDetail(1L)).willReturn(profile(1L, "user1@test.com"));

		// when
		adminUserService.updateUserInventory(ADMIN_ID, 1L, request);

		// then
		InOrder inOrder = Mockito.inOrder(adminMemberQueryService, itemAdminClient);
		inOrder.verify(adminMemberQueryService).getUserDetail(1L);
		inOrder.verify(itemAdminClient).adjustInventory(1L, ADMIN_ID, request);
	}

	@Test
	@DisplayName("존재하지 않는 사용자면 아이템 서비스를 호출하지 않는다")
	void shouldNotAdjustInventoryWhenUserMissing() {
		// given
		AdminInventoryUpdateRequest request = new AdminInventoryUpdateRequest(
			ItemType.MATCHING_TICKET, 2, AdminInventoryAction.ADD, "보상 누락"
		);
		given(adminMemberQueryService.getUserDetail(1L))
			.willThrow(new BusinessException(UserErrorCode.TARGET_USER_NOT_FOUND));

		// when & then
		assertThatThrownBy(() -> adminUserService.updateUserInventory(ADMIN_ID, 1L, request))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(UserErrorCode.TARGET_USER_NOT_FOUND);

		then(itemAdminClient).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("아이템 서비스가 409를 반환하면 중복 조정 요청으로 변환한다")
	void shouldTranslateConflictToDuplicateAdjustment() {
		assertInventoryAdjustmentFailure(409, UserErrorCode.DUPLICATE_INVENTORY_ADJUSTMENT);
	}

	@Test
	@DisplayName("아이템 서비스가 400을 반환하면 아이템 부족으로 변환한다")
	void shouldTranslateBadRequestToNotEnoughItem() {
		assertInventoryAdjustmentFailure(400, UserErrorCode.NOT_ENOUGH_ITEM);
	}

	@Test
	@DisplayName("아이템 서비스가 그 외 오류를 반환하면 조정 실패로 변환한다")
	void shouldTranslateOtherFailuresToAdjustmentFailed() {
		assertInventoryAdjustmentFailure(503, UserErrorCode.ITEM_QUERY_FAILED);
	}

	private void assertInventoryAdjustmentFailure(int status, UserErrorCode expected) {
		// given
		AdminInventoryUpdateRequest request = new AdminInventoryUpdateRequest(
			ItemType.MATCHING_TICKET, 2, AdminInventoryAction.ADD, "보상 누락"
		);
		given(adminMemberQueryService.getUserDetail(1L)).willReturn(profile(1L, "user1@test.com"));
		willThrow(feignException(status)).given(itemAdminClient).adjustInventory(any(), any(), any());

		// when & then
		assertThatThrownBy(() -> adminUserService.updateUserInventory(ADMIN_ID, 1L, request))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(expected);
	}

	private static AdminUserProfileDto profile(Long id, String email) {
		return new AdminUserProfileDto(id, email, "홍길동", "닉네임" + id, Gender.FEMALE, "https://img" + id);
	}

	private static FeignException feignException(int status) {
		Request request = Request.create(
			Request.HttpMethod.PATCH,
			"/api/internal/admin/items/1",
			Map.of(),
			null,
			new RequestTemplate()
		);
		Response response = Response.builder()
			.status(status)
			.reason("error")
			.request(request)
			.headers(Map.of())
			.build();

		return FeignException.errorStatus("ItemAdminClient#adjustInventory", response);
	}
}
