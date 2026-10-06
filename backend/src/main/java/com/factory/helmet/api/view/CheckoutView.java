package com.factory.helmet.api.view;

import java.time.LocalDateTime;

import com.factory.helmet.domain.CheckoutRecord;

public record CheckoutView(Long id,
                           String workerName,
                           String team,
                           LocalDateTime checkedOutAt,
                           LocalDateTime returnedAt,
                           boolean open) {

    public static CheckoutView of(CheckoutRecord r) {
        return new CheckoutView(r.getId(), r.getWorkerName(), r.getTeam(),
                r.getCheckedOutAt(), r.getReturnedAt(), r.isOpen());
    }
}
