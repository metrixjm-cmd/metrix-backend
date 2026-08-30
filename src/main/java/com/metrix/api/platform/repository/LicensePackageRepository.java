package com.metrix.api.platform.repository;

import com.metrix.api.model.LicensePackage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface LicensePackageRepository extends MongoRepository<LicensePackage, String> {

    List<LicensePackage> findAllByOrderByIdAsc();

    List<LicensePackage> findByActivoTrueOrderByIdAsc();
}
