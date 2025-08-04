package com.maxx_global.dto.order;

import java.util.List;

public record OrderRequest(
        Long dealerId,
        List<OrderProductRequest> products
) {}

