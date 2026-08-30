package com.metrix.api.platform.repository;

import com.metrix.api.platform.model.ProductOrder;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductOrderRepository extends MongoRepository<ProductOrder, String> {
}
