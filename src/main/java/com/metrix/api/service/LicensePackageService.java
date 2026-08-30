package com.metrix.api.service;

import com.metrix.api.dto.LicensePackageResponse;
import com.metrix.api.dto.UpdateLicensePackageRequest;

import java.util.List;

public interface LicensePackageService {

    List<LicensePackageResponse> getAll();

    LicensePackageResponse getById(String id);

    LicensePackageResponse update(String id, UpdateLicensePackageRequest request);

    LicensePackageResponse toggleActivo(String id);

    LicensePackageResponse toggleDestacado(String id);

    List<LicensePackageResponse> resetDefaults();
}
