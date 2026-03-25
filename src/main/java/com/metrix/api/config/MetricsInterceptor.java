package com.metrix.api.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Interceptor AOP para captura automática de métricas en METRIX.
 * <p>
 * Solo loguea métricas en formato structured (service, method, duration, status).
 * El analytics-service consulta MongoDB directamente — no necesita Redis Pub/Sub.
 * <p>
 * Emite WARNING para queries lentas en KpiService (>500ms).
 */
@Slf4j
@Aspect
@Component
public class MetricsInterceptor {

    @Around("execution(* com.metrix.api.service.*ServiceImpl.create*(..)) || " +
            "execution(* com.metrix.api.service.*ServiceImpl.update*(..)) || " +
            "execution(* com.metrix.api.service.*ServiceImpl.rate*(..)) || " +
            "execution(* com.metrix.api.service.*ServiceImpl.add*(..)) || " +
            "execution(* com.metrix.api.service.*ServiceImpl.delete*(..))")
    public Object captureMetric(ProceedingJoinPoint jp) throws Throwable {
        String service = jp.getTarget().getClass().getSimpleName();
        String method = jp.getSignature().getName();
        long start = System.currentTimeMillis();

        try {
            Object result = jp.proceed();
            long duration = System.currentTimeMillis() - start;
            log.info("[METRIC] service={} method={} duration={}ms status=OK",
                    service, method, duration);
            return result;
        } catch (Throwable ex) {
            long duration = System.currentTimeMillis() - start;
            log.info("[METRIC] service={} method={} duration={}ms status=ERROR error={}",
                    service, method, duration, ex.getMessage());
            throw ex;
        }
    }

    @Around("execution(* com.metrix.api.service.KpiServiceImpl.get*(..))")
    public Object captureKpiMetric(ProceedingJoinPoint jp) throws Throwable {
        String method = jp.getSignature().getName();
        long start = System.currentTimeMillis();

        try {
            Object result = jp.proceed();
            long duration = System.currentTimeMillis() - start;

            if (duration > 500) {
                log.warn("[METRIC][SLOW] KpiService.{} took {}ms", method, duration);
            } else {
                log.info("[METRIC] service=KpiServiceImpl method={} duration={}ms status=OK",
                        method, duration);
            }
            return result;
        } catch (Throwable ex) {
            long duration = System.currentTimeMillis() - start;
            log.info("[METRIC] service=KpiServiceImpl method={} duration={}ms status=ERROR",
                    method, duration);
            throw ex;
        }
    }
}
