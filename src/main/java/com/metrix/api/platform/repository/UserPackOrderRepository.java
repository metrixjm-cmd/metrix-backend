package com.metrix.api.platform.repository;

import com.metrix.api.platform.model.UserPackOrder;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserPackOrderRepository extends MongoRepository<UserPackOrder, String> {

    Optional<UserPackOrder> findByMpPaymentId(String mpPaymentId);
}
