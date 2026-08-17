package com.comatching.user.domain.admin.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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
import feign.codec.DecodeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {

	private final AdminMemberQueryService adminMemberQueryService;
	private final ItemAdminClient itemAdminClient;

	@Override
	public PagingResponse<AdminUserSummaryResponse> getUsers(String keyword, Pageable pageable) {
		PagingResponse<AdminUserProfileDto> userPage = adminMemberQueryService.getUsers(keyword, pageable);
		List<AdminUserProfileDto> users = userPage.content();
		Map<Long, AdminInventoryCounts> inventoryCountsByMemberId = getInventoryCounts(
			users.stream()
				.map(AdminUserProfileDto::id)
				.toList()
		);

		List<AdminUserSummaryResponse> summaries = users.stream()
			.map(user -> AdminUserSummaryResponse.from(
				user,
				inventoryCountsByMemberId.getOrDefault(user.id(), AdminInventoryCounts.empty())
			))
			.toList();

		return new PagingResponse<>(
			summaries,
			userPage.currentPage(),
			userPage.size(),
			userPage.totalElements(),
			userPage.totalPages(),
			userPage.hasNext(),
			userPage.hasPrevious()
		);
	}

	@Override
	public AdminUserDetailResponse getUserDetail(Long memberId) {
		AdminUserProfileDto user = getUserOrThrow(memberId);
		AdminInventoryCounts inventoryCounts = getInventoryCounts(List.of(memberId))
			.getOrDefault(memberId, AdminInventoryCounts.empty());

		return new AdminUserDetailResponse(
			user.id(),
			user.email(),
			user.realName(),
			user.nickname(),
			user.gender(),
			user.profileImageUrl(),
			inventoryCounts.matchingTicketCount(),
			inventoryCounts.optionTicketCount()
		);
	}

	@Override
	public void updateUserInventory(Long adminId, Long memberId, AdminInventoryUpdateRequest request) {
		// 존재하지 않는 대상 사용자의 인벤토리 수정 요청을 막기 위해 선조회
		getUserOrThrow(memberId);

		try {
			itemAdminClient.adjustInventory(memberId, adminId, request);
		} catch (FeignException e) {
			throw toInventoryAdjustmentException(memberId, e);
		}
	}

	private Map<Long, AdminInventoryCounts> getInventoryCounts(List<Long> memberIds) {
		if (memberIds.isEmpty()) {
			return Map.of();
		}

		try {
			return itemAdminClient.getInventoryCounts(memberIds);
		} catch (DecodeException e) {
			log.warn("Admin inventory query decode failed. memberIds={}", memberIds, e);
			throw new BusinessException(UserErrorCode.ITEM_QUERY_FAILED);
		} catch (FeignException e) {
			log.warn("Admin inventory query failed. memberIds={}, status={}, body={}",
				memberIds, e.status(), e.contentUTF8(), e);
			throw new BusinessException(UserErrorCode.ITEM_QUERY_FAILED);
		}
	}

	private AdminUserProfileDto getUserOrThrow(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new BusinessException(GeneralErrorCode.INVALID_INPUT_VALUE, "memberId는 1 이상의 값이어야 합니다.");
		}

		return adminMemberQueryService.getUserDetail(memberId);
	}

	private BusinessException toInventoryAdjustmentException(Long memberId, FeignException e) {
		if (e.status() == HttpStatus.CONFLICT.value()) {
			return new BusinessException(UserErrorCode.DUPLICATE_INVENTORY_ADJUSTMENT);
		}
		if (e.status() == HttpStatus.BAD_REQUEST.value()) {
			return new BusinessException(UserErrorCode.NOT_ENOUGH_ITEM);
		}

		log.warn("Admin inventory adjustment failed. memberId={}, status={}, body={}",
			memberId, e.status(), e.contentUTF8(), e);
		return new BusinessException(UserErrorCode.ITEM_QUERY_FAILED);
	}
}
