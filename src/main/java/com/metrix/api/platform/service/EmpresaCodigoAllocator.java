package com.metrix.api.platform.service;

import com.metrix.api.platform.EmpresaCodigos;
import com.metrix.api.platform.model.MetrixInstance;
import com.metrix.api.platform.model.TenantAdminIndex;
import com.metrix.api.platform.repository.MetrixInstanceRepository;
import com.metrix.api.platform.repository.TenantAdminIndexRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmpresaCodigoAllocator {

    private final MetrixInstanceRepository instanceRepository;
    private final TenantAdminIndexRepository tenantAdminIndexRepository;

    public String allocate(String empresaNombre, String instanceId) {
        String code = EmpresaCodigos.generate(empresaNombre, instanceId);
        int attempt = 0;
        while (instanceRepository.existsByCodigoEmpresa(code) && attempt < 8) {
            code = EmpresaCodigos.withExtraSuffix(code, UUID.randomUUID().toString());
            attempt++;
        }
        if (instanceRepository.existsByCodigoEmpresa(code)) {
            throw new IllegalStateException("No se pudo generar un código de empresa único.");
        }
        return code;
    }

    public String ensure(MetrixInstance instance) {
        String code;
        if (instance.getCodigoEmpresa() != null && !instance.getCodigoEmpresa().isBlank()) {
            code = EmpresaCodigos.normalize(instance.getCodigoEmpresa());
        } else {
            code = allocate(instance.getEmpresaNombre(), instance.getId());
            instance.setCodigoEmpresa(code);
            instanceRepository.save(instance);
        }
        stampIndexRows(instance.getId(), code);
        return code;
    }

    private void stampIndexRows(String instanceId, String code) {
        if (instanceId == null || instanceId.isBlank()) {
            return;
        }
        List<TenantAdminIndex> rows = tenantAdminIndexRepository.findByInstanceId(instanceId);
        for (TenantAdminIndex row : rows) {
            if (row.getCodigoEmpresa() == null || row.getCodigoEmpresa().isBlank()) {
                row.setCodigoEmpresa(code);
                tenantAdminIndexRepository.save(row);
            }
        }
    }

    public int backfillMissing() {
        int count = 0;
        for (MetrixInstance instance : instanceRepository.findAll()) {
            if (instance.getCodigoEmpresa() == null || instance.getCodigoEmpresa().isBlank()) {
                ensure(instance);
                count++;
            }
        }
        return count;
    }
}
