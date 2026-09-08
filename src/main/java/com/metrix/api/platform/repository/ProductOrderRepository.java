package com.metrix.api.platform.repository;

import com.metrix.api.platform.model.ProductOrder;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ProductOrderRepository extends MongoRepository<ProductOrder, String> {

    Optional<ProductOrder> findByMpPaymentId(String mpPaymentId);
}
