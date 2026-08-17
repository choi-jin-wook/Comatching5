package com.comatching.common.dto.item;

public record AdminInventoryCounts(
	long matchingTicketCount,
	long optionTicketCount
) {
	public static AdminInventoryCounts empty() {
		return new AdminInventoryCounts(0, 0);
	}
}
