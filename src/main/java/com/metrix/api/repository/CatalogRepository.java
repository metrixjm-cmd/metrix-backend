package com.metrix.api.repository;

import com.metrix.api.model.Catalog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CatalogRepository extends MongoRepository<Catalog, String> {

    List<Catalog> findByTypeAndActivoTrue(String type);

    Optional<Catalog> findByTypeAndValue(String type, String value);

    boolean existsByTypeAndValue(String type, String value);
}
