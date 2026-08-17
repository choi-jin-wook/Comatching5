package com.comatching.item.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.comatching.common.domain.enums.ItemType;
import com.comatching.common.dto.item.AdminInventoryAction;
import com.comatching.common.dto.item.AdminInventoryUpdateRequest;
import com.comatching.common.exception.BusinessException;
import com.comatching.item.global.exception.ItemErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminItemCommandService 테스트")
class AdminItemCommandServiceTest {

	@InjectMocks
	private AdminItemCommandService adminItemCommandService;

	@Mock
	private AdminInventoryDedupeService adminInventoryDedupeService;

	@Mock
	private AdminInventoryAdjustmentService adminInventoryAdjustmentService;

	private static final Long ADMIN_ID = 999L;
	private static final Long MEMBER_ID = 11L;

	@Test
	@DisplayName("중복 예약 이후에 인벤토리를 조정한다")
	void shouldReserveBeforeAdjust() {
		// given
		AdminInventoryUpdateRequest request = new AdminInventoryUpdateRequest(
			ItemType.MATCHING_TICKET, 2, AdminInventoryAction.ADD, "보상 누락"
		);

		// when
		adminItemCommandService.adjustInventory(ADMIN_ID, MEMBER_ID, request);

		// then
		InOrder inOrder = Mockito.inOrder(adminInventoryDedupeService, adminInventoryAdjustmentService);
		inOrder.verify(adminInventoryDedupeService).reserveOrThrow(MEMBER_ID, request);
		inOrder.verify(adminInventoryAdjustmentService).adjust(ADMIN_ID, MEMBER_ID, request);
	}

	@Test
	@DisplayName("중복 요청이면 인벤토리를 조정하지 않는다")
	void shouldNotAdjustWhenDuplicated() {
		// given
		AdminInventoryUpdateRequest request = new AdminInventoryUpdateRequest(
			ItemType.MATCHING_TICKET, 2, AdminInventoryAction.ADD, "보상 누락"
		);
		willThrow(new BusinessException(ItemErrorCode.DUPLICATE_ADMIN_INVENTORY_ADJUSTMENT))
			.given(adminInventoryDedupeService).reserveOrThrow(MEMBER_ID, request);

		// when & then
		assertThatThrownBy(() -> adminItemCommandService.adjustInventory(ADMIN_ID, MEMBER_ID, request))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ItemErrorCode.DUPLICATE_ADMIN_INVENTORY_ADJUSTMENT);

		Mockito.verifyNoInteractions(adminInventoryAdjustmentService);
	}
}
