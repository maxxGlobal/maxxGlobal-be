package com.maxx_global.dto.order;

import com.maxx_global.entity.OrderItem;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OrderMapperTest {
    private final OrderMapper mapper = Mappers.getMapper(OrderMapper.class);

    @Test
    void subtotalIsNullWhenAnyItemHasNoPrice() {
        OrderItem priced = new OrderItem();
        priced.setTotalPrice(new BigDecimal("25.00"));
        OrderItem unpriced = new OrderItem();
        unpriced.setTotalPrice(null);

        assertNull(mapper.calculateSubtotal(new LinkedHashSet<>(Set.of(priced, unpriced))));
    }

    @Test
    void subtotalIsCalculatedWhenEveryItemHasPrice() {
        OrderItem first = new OrderItem();
        first.setTotalPrice(new BigDecimal("25.00"));
        OrderItem second = new OrderItem();
        second.setTotalPrice(new BigDecimal("15.00"));

        assertEquals(new BigDecimal("40.00"), mapper.calculateSubtotal(new LinkedHashSet<>(Set.of(first, second))));
    }
}
