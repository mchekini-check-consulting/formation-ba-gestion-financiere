package com.example.gestionfinanciere.logging;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class SlowQueryLoggingBeanPostProcessor implements BeanPostProcessor {

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (bean instanceof DataSource dataSource && !(bean instanceof SlowQueryLoggingDataSource)) {
			return new SlowQueryLoggingDataSource(dataSource);
		}
		return bean;
	}

}
