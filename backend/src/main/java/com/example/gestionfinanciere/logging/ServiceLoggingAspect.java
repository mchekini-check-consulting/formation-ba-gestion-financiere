package com.example.gestionfinanciere.logging;

import java.time.Duration;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ServiceLoggingAspect {

	private static final Logger log = LoggerFactory.getLogger(ServiceLoggingAspect.class);

	@Around("@within(Loggable) && execution(public * *(..))")
	public Object logServiceCall(ProceedingJoinPoint joinPoint) throws Throwable {
		String method = joinPoint.getSignature().toShortString();
		MdcContext ctx = MdcContext.open().with("layer", "service").with("method", method);
		try {
			long start = System.nanoTime();
			log.debug("service call started");
			try {
				Object result = joinPoint.proceed();
				logCompletion(start, null);
				return result;
			} catch (Throwable ex) {
				logCompletion(start, ex);
				throw ex;
			}
		} finally {
			ctx.close();
		}
	}

	private void logCompletion(long startNanos, Throwable error) {
		String durationMs = String.valueOf(Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
		MdcContext ctx = MdcContext.open().with("durationMs", durationMs);
		try {
			if (error == null) {
				log.info("service call completed");
			} else {
				log.warn("service call failed");
			}
		} finally {
			ctx.close();
		}
	}

}
