package com.example.gestionfinanciere.logging;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

class SlowQueryLoggingBeanPostProcessorTest {

	private final SlowQueryLoggingBeanPostProcessor processor = new SlowQueryLoggingBeanPostProcessor();

	@Test
	void enveloppeUnBeanDataSource() {
		DataSource original = mock(DataSource.class);
		Object result = processor.postProcessAfterInitialization(original, "dataSource");
		assertTrue(result instanceof SlowQueryLoggingDataSource);
	}

	@Test
	void laisseUnBeanNonDataSourceInchange() {
		Object bean = new Object();
		assertSame(bean, processor.postProcessAfterInitialization(bean, "autreBean"));
	}

	@Test
	void neReenvelopePasUneDataSourceDejaEnveloppee() {
		SlowQueryLoggingDataSource wrapped = new SlowQueryLoggingDataSource(mock(DataSource.class));
		assertSame(wrapped, processor.postProcessAfterInitialization(wrapped, "dataSource"));
	}

}
